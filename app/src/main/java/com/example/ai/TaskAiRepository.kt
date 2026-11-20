package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.TaskToolType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "TaskAiRepository"
private const val MODEL_NAME = "gemini-3.8-flash"

class TaskAiRepository {

    private val assistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    "Anda adalah Cerdas AI, asisten pintar berbahasa Indonesia untuk membantu pelajar dan mahasiswa mengerjakan tugas sekolah, PR, latihan soal, ringkasan materi, dan esai. " +
                        "Berikan jawaban yang akurat, mudah dipahami, terstruktur rapi tanpa bertele-tele."
                )
            }
        )
    }

    private suspend fun verifyAppCheckTokenReady() {
        try {
            withTimeout(15_000L) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    FirebaseAppCheck.getInstance().getAppCheckToken(false)
                        .addOnSuccessListener { result ->
                            if (continuation.isActive) {
                                Log.d(TAG, "App Check token ready (len=${result.token.length})")
                                continuation.resume(Unit)
                            }
                        }
                        .addOnFailureListener { error ->
                            if (continuation.isActive) {
                                Log.e(TAG, "App Check token request failed", error)
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        "Verifikasi App Check gagal: ${error.localizedMessage ?: error.javaClass.simpleName}",
                                        error
                                    )
                                )
                            }
                        }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "App Check token timed out after 15s", e)
            throw IllegalStateException(
                "Verifikasi App Check (reCAPTCHA) timeout setelah 15 detik. Pastikan internet & Google Play Services aktif (tidak terblokir VPN/Wi-Fi), atau uji melalui Preview AI Studio.",
                e
            )
        }
    }

    fun streamToolSolution(
        toolType: TaskToolType,
        userInput: String,
        attachedBitmap: Bitmap?,
        quickAnswerMode: Boolean
    ): Flow<String> = flow {
        verifyAppCheckTokenReady()

        val styleRule = if (quickAnswerMode) {
            "Mode Jawaban Cepat AKTIF: Langsung berikan jawaban inti secara singkat, padat, dan jelas."
        } else {
            "Mode Penjelasan Lengkap: Berikan jawaban final di awal, lalu sertakan langkah penyelesaian yang jelas dan mudah diikuti."
        }

        val taskDirective = when (toolType) {
            TaskToolType.SCAN_PHOTO ->
                "Baca semua soal atau teks tugas pada gambar ini dengan teliti. Jawab setiap soal secara berurutan. $styleRule"
            TaskToolType.MULTIPLE_CHOICE ->
                "Analisis soal pilihan ganda berikut. Sebutkan huruf jawaban yang paling benar (misal: Jawaban: B) beserta alasan ringkasnya. $styleRule"
            TaskToolType.ESSAY_WRITER ->
                "Kerjakan soal esai/uraian berikut dengan bahasa Indonesia yang baik, runtut, dan siap disalin sebagai jawaban tugas. $styleRule"
            TaskToolType.SUMMARIZE ->
                "Ringkas materi berikut menjadi poin-poin inti, definisi penting, dan rumus kunci (jika ada) agar mudah dipelajari. $styleRule"
            TaskToolType.PROOFREAD ->
                "Periksa dan koreksi jawaban tugas berikut. Tunjukkan bagian yang sudah benar, bagian yang keliru, lalu berikan versi perbaikan yang tepat. $styleRule"
        }

        val scaledBitmap = attachedBitmap?.let { downscaleBitmapIfNeeded(it) }
        val fullPrompt = buildString {
            append(taskDirective)
            if (userInput.isNotBlank()) {
                append("\n\nTugas / Soal:\n")
                append(userInput.trim())
            }
        }

        withTimeout(60_000L) {
            val requestContent = content {
                if (scaledBitmap != null) {
                    image(scaledBitmap)
                }
                text(fullPrompt)
            }
            assistantModel.generateContentStream(requestContent).collect { chunk ->
                val text = chunk.text
                if (!text.isNullOrEmpty()) {
                    emit(text)
                }
            }
        }
    }

    fun streamChat(
        prompt: String,
        attachedBitmap: Bitmap?,
        quickAnswerMode: Boolean
    ): Flow<String> = flow {
        verifyAppCheckTokenReady()

        val scaledBitmap = attachedBitmap?.let { downscaleBitmapIfNeeded(it) }
        val modeNote = if (quickAnswerMode) {
            "(Jawab dengan ringkas & langsung ke inti jawaban)\n"
        } else {
            ""
        }

        withTimeout(60_000L) {
            val requestContent = content {
                if (scaledBitmap != null) {
                    image(scaledBitmap)
                }
                text("$modeNote$prompt")
            }
            assistantModel.generateContentStream(requestContent).collect { chunk ->
                val text = chunk.text
                if (!text.isNullOrEmpty()) {
                    emit(text)
                }
            }
        }
    }

    private fun downscaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int = 1600): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to downscale bitmap, using original", e)
            bitmap
        }
    }
}
