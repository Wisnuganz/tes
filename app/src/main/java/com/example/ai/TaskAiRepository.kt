package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiChatMessage
import com.example.model.TaskToolType
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "TaskAiRepository"
private const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
private const val FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"

private const val SYSTEM_PROMPT =
    "Anda adalah Cerdas AI, asisten pintar berbahasa Indonesia untuk membantu pelajar dan mahasiswa mengerjakan tugas sekolah, PR, latihan soal, ringkasan materi, dan esai. " +
        "Berikan jawaban yang akurat, mudah dipahami, terstruktur rapi tanpa bertele-tele."

class TaskAiRepository {

    @Volatile
    private var preferFastModelUntilMs: Long = 0L

    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            systemInstruction = content { text(SYSTEM_PROMPT) }
        )
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_MODEL_NAME,
            systemInstruction = content { text(SYSTEM_PROMPT) }
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

    private fun isHighDemandOrTransientError(e: Throwable): Boolean {
        val msg = buildString {
            var cur: Throwable? = e
            while (cur != null) {
                append(cur.message.orEmpty())
                append(' ')
                cur = cur.cause
            }
        }.lowercase()
        return msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("try again later") ||
            msg.contains("unavailable") ||
            msg.contains("503") ||
            msg.contains("429") ||
            msg.contains("resource exhausted")
    }

    private suspend fun collectModelStream(
        model: GenerativeModel,
        requestContent: Content,
        chatHistory: List<Content>,
        timeoutMs: Long,
        onChunk: suspend (String) -> Unit
    ) {
        withTimeout(timeoutMs) {
            val flow = if (chatHistory.isEmpty()) {
                model.generateContentStream(requestContent)
            } else {
                model.startChat(history = chatHistory).sendMessageStream(requestContent)
            }
            flow.collect { chunk ->
                val text = chunk.text
                if (!text.isNullOrEmpty()) {
                    onChunk(text)
                }
            }
        }
    }

    private suspend fun FlowCollector<String>.streamWithAutoFallback(
        requestContent: Content,
        chatHistory: List<Content> = emptyList(),
        preferFast: Boolean = false
    ) {
        val useFastFirst = preferFast || System.currentTimeMillis() < preferFastModelUntilMs
        val firstModel = if (useFastFirst) fallbackModel else primaryModel
        val secondModel = if (useFastFirst) primaryModel else fallbackModel
        val firstName = if (useFastFirst) FALLBACK_MODEL_NAME else PRIMARY_MODEL_NAME
        val secondName = if (useFastFirst) PRIMARY_MODEL_NAME else FALLBACK_MODEL_NAME

        var emittedFromFirst = false
        var shouldTrySecond = false

        try {
            collectModelStream(
                model = firstModel,
                requestContent = requestContent,
                chatHistory = chatHistory,
                timeoutMs = if (useFastFirst) 30_000L else 25_000L
            ) { text ->
                emittedFromFirst = true
                emit(text)
            }
            return
        } catch (e: TimeoutCancellationException) {
            if (!emittedFromFirst) {
                Log.w(TAG, "$firstName timed out before first chunk, falling back to $secondName", e)
                if (!useFastFirst) {
                    preferFastModelUntilMs = System.currentTimeMillis() + 5 * 60_000L
                }
                shouldTrySecond = true
            } else {
                throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!emittedFromFirst && isHighDemandOrTransientError(e)) {
                Log.w(TAG, "$firstName transient/high-demand error, falling back to $secondName", e)
                if (!useFastFirst) {
                    preferFastModelUntilMs = System.currentTimeMillis() + 5 * 60_000L
                }
                shouldTrySecond = true
            } else {
                throw e
            }
        }

        if (shouldTrySecond) {
            var emittedFromSecond = false
            try {
                collectModelStream(
                    model = secondModel,
                    requestContent = requestContent,
                    chatHistory = chatHistory,
                    timeoutMs = 45_000L
                ) { text ->
                    emittedFromSecond = true
                    emit(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!emittedFromSecond && isHighDemandOrTransientError(e)) {
                    delay(1000L)
                    collectModelStream(
                        model = firstModel,
                        requestContent = requestContent,
                        chatHistory = chatHistory,
                        timeoutMs = 45_000L
                    ) { text ->
                        emit(text)
                    }
                } else {
                    throw e
                }
            }
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

        val requestContent = content {
            if (scaledBitmap != null) {
                image(scaledBitmap)
            }
            text(fullPrompt)
        }
        streamWithAutoFallback(
            requestContent = requestContent,
            preferFast = quickAnswerMode
        )
    }

    fun streamChat(
        prompt: String,
        attachedBitmap: Bitmap?,
        quickAnswerMode: Boolean,
        priorMessages: List<AiChatMessage> = emptyList()
    ): Flow<String> = flow {
        verifyAppCheckTokenReady()

        val scaledBitmap = attachedBitmap?.let { downscaleBitmapIfNeeded(it) }
        val modeNote = if (quickAnswerMode) {
            "(Jawab dengan ringkas & langsung ke inti jawaban)\n"
        } else {
            ""
        }

        val historyContent = priorMessages
            .filter { !it.isError && !it.isStreaming && it.id != "welcome" && it.text.isNotBlank() }
            .takeLast(8)
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }

        val requestContent = content(role = "user") {
            if (scaledBitmap != null) {
                image(scaledBitmap)
            }
            text("$modeNote$prompt")
        }
        streamWithAutoFallback(
            requestContent = requestContent,
            chatHistory = historyContent,
            preferFast = quickAnswerMode
        )
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
