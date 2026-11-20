package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.TaskToolType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

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

    fun streamToolSolution(
        toolType: TaskToolType,
        userInput: String,
        attachedBitmap: Bitmap?,
        quickAnswerMode: Boolean
    ): Flow<String> = flow {
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
