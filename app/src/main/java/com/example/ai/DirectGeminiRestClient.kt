package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.model.AiChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "DirectGeminiRestClient"
private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

class DirectGeminiRestClient {

    private val defaultCandidateModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash"
    )

    @Volatile
    private var discoveredFastModel: String? = null

    @Volatile
    private var discoveredPrimaryModel: String? = null

    suspend fun verifyAndSelectModel(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key tidak boleh kosong."))
        }
        try {
            val models = fetchAvailableGenerateModels(cleanKey)
            val fast = pickPreferredModel(models, preferLite = true)
            val primary = pickPreferredModel(models, preferLite = false)
            discoveredFastModel = fast
            discoveredPrimaryModel = primary
            Result.success(fast)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "API key verification failed", e)
            Result.failure(e)
        }
    }

    fun streamGenerateContent(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        attachedBitmap: Bitmap?,
        priorMessages: List<AiChatMessage>,
        preferFast: Boolean,
        verifiedHintModel: String = ""
    ): Flow<String> = flow {
        val cleanKey = apiKey.trim()
        val candidates = buildModelCandidates(preferFast, verifiedHintModel)
        val payload = buildRequestPayload(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            attachedBitmap = attachedBitmap,
            priorMessages = priorMessages
        )

        var lastError: Exception? = null
        for (modelName in candidates) {
            var emittedAny = false
            try {
                streamSingleModel(cleanKey, modelName, payload) { chunk ->
                    emittedAny = true
                    emit(chunk)
                }
                if (preferFast) {
                    discoveredFastModel = modelName
                } else {
                    discoveredPrimaryModel = modelName
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (emittedAny) throw e
                lastError = e
                val msg = e.message.orEmpty().lowercase()
                val isAuthOrQuotaInvalid = msg.contains("api key not valid") ||
                    msg.contains("api_key_invalid") ||
                    msg.contains("permission denied") ||
                    msg.contains("http 400") ||
                    msg.contains("http 401") ||
                    msg.contains("http 403")
                if (isAuthOrQuotaInvalid) {
                    throw e
                }
                Log.w(TAG, "Model $modelName failed (${e.message}), trying next fallback...")
            }
        }

        // Dynamic discovery fallback if hardcoded model names returned 404 / unavailable
        try {
            val available = fetchAvailableGenerateModels(cleanKey)
            val discovered = pickPreferredModel(available, preferLite = preferFast)
            if (discovered !in candidates) {
                streamSingleModel(cleanKey, discovered, payload) { chunk ->
                    emit(chunk)
                }
                discoveredFastModel = discovered
                return@flow
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (lastError == null) lastError = e
        }

        throw lastError ?: IllegalStateException("Gagal terhubung ke Gemini API.")
    }.flowOn(Dispatchers.IO)

    suspend fun generateSingleResponse(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        attachedBitmap: Bitmap?,
        temperature: Double = 0.1,
        jsonResponse: Boolean = true,
        verifiedHintModel: String = "",
        disableThinking: Boolean = true,
        maxOutputTokens: Int? = 768
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val candidates = buildModelCandidates(preferFast = true, verifiedHintModel = verifiedHintModel)
        val payload = buildRequestPayload(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            attachedBitmap = attachedBitmap,
            priorMessages = emptyList(),
            temperature = temperature,
            responseMimeType = if (jsonResponse) "application/json" else null,
            disableThinking = disableThinking,
            maxOutputTokens = maxOutputTokens
        )
        val payloadNoThinking = if (disableThinking) {
            buildRequestPayload(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                attachedBitmap = attachedBitmap,
                priorMessages = emptyList(),
                temperature = temperature,
                responseMimeType = if (jsonResponse) "application/json" else null,
                disableThinking = false,
                maxOutputTokens = maxOutputTokens
            )
        } else {
            payload
        }

        var lastError: Exception? = null
        for (modelName in candidates) {
            try {
                val result = try {
                    callSingleModel(cleanKey, modelName, payload)
                } catch (e: Exception) {
                    val m = e.message.orEmpty().lowercase()
                    if (disableThinking && m.contains("http 400")) {
                        callSingleModel(cleanKey, modelName, payloadNoThinking)
                    } else {
                        throw e
                    }
                }
                discoveredFastModel = modelName
                return@withContext result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val msg = e.message.orEmpty().lowercase()
                val isAuthOrInvalid = msg.contains("api key not valid") ||
                    msg.contains("api_key_invalid") ||
                    msg.contains("permission denied") ||
                    msg.contains("http 401") ||
                    msg.contains("http 403")
                if (isAuthOrInvalid) throw e
                Log.w(TAG, "Single model $modelName failed (${e.message}), trying fallback...")
            }
        }

        val available = fetchAvailableGenerateModels(cleanKey)
        val discovered = pickPreferredModel(available, preferLite = true)
        val result = try {
            callSingleModel(cleanKey, discovered, payload)
        } catch (e: Exception) {
            val m = e.message.orEmpty().lowercase()
            if (disableThinking && m.contains("http 400")) {
                callSingleModel(cleanKey, discovered, payloadNoThinking)
            } else {
                throw e
            }
        }
        discoveredFastModel = discovered
        result
    }

    private fun buildModelCandidates(preferFast: Boolean, verifiedHintModel: String): List<String> {
        val ordered = linkedSetOf<String>()
        if (preferFast) {
            discoveredFastModel?.let { ordered.add(it) }
            ordered.addAll(defaultCandidateModels)
            if (verifiedHintModel.isNotBlank()) ordered.add(verifiedHintModel)
        } else {
            discoveredPrimaryModel?.let { ordered.add(it) }
            if (verifiedHintModel.isNotBlank()) ordered.add(verifiedHintModel)
            ordered.add("gemini-3.8-flash")
            ordered.addAll(defaultCandidateModels)
        }
        return ordered.toList()
    }

    private suspend fun streamSingleModel(
        apiKey: String,
        modelName: String,
        payload: String,
        onChunk: suspend (String) -> Unit
    ) {
        val cleanModel = modelName.removePrefix("models/")
        val url = URL("$GEMINI_BASE_URL/models/$cleanModel:streamGenerateContent?alt=sse")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            conn.outputStream.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val parsedMsg = extractApiErrorMessage(errorBody) ?: "HTTP $code"
                throw IllegalStateException("HTTP $code: $parsedMsg")
            }

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) continue
                    val jsonStr = trimmed.removePrefix("data:").trim()
                    if (jsonStr.isEmpty() || jsonStr == "[DONE]") continue
                    val text = extractTextFromSseChunk(jsonStr)
                    if (!text.isNullOrEmpty()) {
                        onChunk(text)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun callSingleModel(
        apiKey: String,
        modelName: String,
        payload: String
    ): String {
        val cleanModel = modelName.removePrefix("models/")
        val url = URL("$GEMINI_BASE_URL/models/$cleanModel:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 35_000
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            conn.outputStream.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val parsedMsg = extractApiErrorMessage(errorBody) ?: "HTTP $code"
                throw IllegalStateException("HTTP $code: $parsedMsg")
            }

            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return extractTextFromSseChunk(body).orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchAvailableGenerateModels(apiKey: String): List<String> {
        val url = URL("$GEMINI_BASE_URL/models?pageSize=100")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val parsedMsg = extractApiErrorMessage(errorBody) ?: "HTTP $code"
                throw IllegalStateException("API Key ditolak ($code): $parsedMsg")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val modelsArr = json.optJSONArray("models") ?: return emptyList()
            val validNames = mutableListOf<String>()
            for (i in 0 until modelsArr.length()) {
                val obj = modelsArr.optJSONObject(i) ?: continue
                val name = obj.optString("name").removePrefix("models/")
                val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
                var supportsGenerate = false
                for (j in 0 until methods.length()) {
                    if (methods.optString(j) == "generateContent") {
                        supportsGenerate = true
                        break
                    }
                }
                if (supportsGenerate &&
                    name.startsWith("gemini-") &&
                    !name.contains("embedding") &&
                    !name.contains("tts") &&
                    !name.contains("image") &&
                    !name.contains("robotics")
                ) {
                    validNames.add(name)
                }
            }
            return validNames
        } finally {
            conn.disconnect()
        }
    }

    private fun pickPreferredModel(models: List<String>, preferLite: Boolean): String {
        if (models.isEmpty()) {
            return if (preferLite) "gemini-3.5-flash-lite" else "gemini-3.8-flash"
        }
        val preferredOrder = if (preferLite) {
            listOf(
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash"
            )
        } else {
            listOf(
                "gemini-3.8-flash",
                "gemini-3.5-flash-lite"
            )
        }
        for (candidate in preferredOrder) {
            val found = models.firstOrNull { it.equals(candidate, ignoreCase = true) }
            if (found != null) return found
        }
        val nonLegacy = models.filterNot {
            it.contains("1.0") || it.contains("1.5") || it.contains("2.0")
        }
        val pool = nonLegacy.ifEmpty { models }
        return pool.lastOrNull { it.contains("flash-lite", ignoreCase = true) }
            ?: pool.lastOrNull { it.contains("flash", ignoreCase = true) }
            ?: pool.first()
    }

    private fun buildRequestPayload(
        systemPrompt: String,
        userPrompt: String,
        attachedBitmap: Bitmap?,
        priorMessages: List<AiChatMessage>,
        temperature: Double = 0.2,
        responseMimeType: String? = null,
        disableThinking: Boolean = false,
        maxOutputTokens: Int? = null
    ): String {
        val root = JSONObject()
        root.put(
            "systemInstruction",
            JSONObject().put(
                "parts",
                JSONArray().put(JSONObject().put("text", systemPrompt))
            )
        )

        val contents = JSONArray()
        priorMessages
            .filter { !it.isError && !it.isStreaming && it.id != "welcome" && it.text.isNotBlank() }
            .takeLast(8)
            .forEach { msg ->
                contents.put(
                    JSONObject()
                        .put("role", if (msg.isUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                )
            }

        val currentParts = JSONArray()
        if (attachedBitmap != null) {
            val baos = ByteArrayOutputStream()
            attachedBitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            currentParts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", base64)
                )
            )
        }
        currentParts.put(JSONObject().put("text", userPrompt))

        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", currentParts)
        )

        root.put("contents", contents)
        val genConfig = JSONObject()
            .put("temperature", temperature)
        if (!responseMimeType.isNullOrBlank()) {
            genConfig.put("responseMimeType", responseMimeType)
        }
        if (maxOutputTokens != null) {
            genConfig.put("maxOutputTokens", maxOutputTokens)
        }
        if (disableThinking) {
            genConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }
        root.put("generationConfig", genConfig)
        return root.toString()
    }

    private fun extractTextFromSseChunk(jsonStr: String): String? {
        return try {
            val obj = JSONObject(jsonStr)
            val candidates = obj.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                val text = part.optString("text", "")
                if (text.isNotEmpty()) sb.append(text)
            }
            sb.toString().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractApiErrorMessage(errorBody: String): String? {
        if (errorBody.isBlank()) return null
        return try {
            JSONObject(errorBody).optJSONObject("error")?.optString("message")?.ifBlank { null }
        } catch (_: Exception) {
            errorBody.take(200)
        }
    }
}
