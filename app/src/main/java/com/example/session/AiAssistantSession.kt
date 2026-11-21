package com.example.session

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.ai.GeminiApiKeyStore
import com.example.ai.TaskAiRepository
import com.example.model.AiChatMessage
import com.example.model.AiPanelTab
import com.example.model.SolvedTaskItem
import com.example.model.StealthMode
import com.example.model.TaskAssistantUiState
import com.example.model.TaskToolType
import com.example.overlay.ScreenProjectionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "AiAssistantSession"

object AiAssistantSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val aiRepository = TaskAiRepository()

    private val _uiState = MutableStateFlow(
        TaskAssistantUiState(
            chatMessages = listOf(
                AiChatMessage(
                    id = "welcome",
                    isUser = false,
                    text = "Halo! Saya Cerdas AI. Ketik soal atau ketuk Scan Layar untuk menjawab otomatis di atas aplikasi lain."
                )
            )
        )
    )
    val uiState: StateFlow<TaskAssistantUiState> = _uiState.asStateFlow()

    private var activeStreamJob: Job? = null
    private var toastJob: Job? = null

    fun init(context: Context) {
        GeminiApiKeyStore.init(context)
        val savedKey = GeminiApiKeyStore.getApiKey(context)
        val hasKey = savedKey.isNotBlank()
        _uiState.update {
            it.copy(
                hasCustomApiKey = hasKey,
                maskedApiKey = GeminiApiKeyStore.maskKey(savedKey),
                apiKeyStatusMessage = if (hasKey) {
                    val modelHint = GeminiApiKeyStore.getVerifiedModel(context)
                    if (modelHint.isNotBlank()) "API Key aktif ($modelHint)" else "API Key tersimpan • Siap dipakai"
                } else {
                    null
                },
                aiStatusText = if (hasKey) "Siap • Gemini API Key" else "Siap • Tugas AI Engine"
            )
        }
    }

    fun saveAndTestApiKey(context: Context, rawKey: String) {
        val cleanKey = rawKey.trim()
        if (cleanKey.isBlank()) {
            _uiState.update {
                it.copy(apiKeyStatusMessage = "Tempel Gemini API Key terlebih dahulu.")
            }
            return
        }
        scope.launch {
            _uiState.update {
                it.copy(
                    isTestingApiKey = true,
                    apiKeyStatusMessage = "Menguji koneksi API Key ke Gemini..."
                )
            }
            val result = aiRepository.verifyApiKey(cleanKey)
            result.onSuccess { modelName ->
                GeminiApiKeyStore.saveApiKey(context, cleanKey, modelName)
                _uiState.update {
                    it.copy(
                        isTestingApiKey = false,
                        hasCustomApiKey = true,
                        maskedApiKey = GeminiApiKeyStore.maskKey(cleanKey),
                        apiKeyStatusMessage = "Berhasil terhubung! ($modelName)",
                        aiStatusText = "Siap • Gemini API Key"
                    )
                }
                showToast("Gemini API Key aktif!")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isTestingApiKey = false,
                        apiKeyStatusMessage = "Gagal: ${err.localizedMessage ?: "Periksa API Key & internet."}"
                    )
                }
            }
        }
    }

    fun clearApiKey(context: Context) {
        GeminiApiKeyStore.clearApiKey(context)
        _uiState.update {
            it.copy(
                hasCustomApiKey = false,
                maskedApiKey = "",
                apiKeyStatusMessage = "API Key dihapus (kembali ke Firebase App Check).",
                aiStatusText = "Siap • Tugas AI Engine"
            )
        }
        showToast("API Key dihapus")
    }

    fun setOverlayPermission(granted: Boolean) {
        _uiState.update { it.copy(hasOverlayPermission = granted) }
    }

    fun setOverlayServiceRunning(running: Boolean) {
        _uiState.update {
            it.copy(
                isOverlayServiceRunning = running,
                isSystemOverlayExpanded = if (!running) false else it.isSystemOverlayExpanded
            )
        }
    }

    fun setLiveScreenActive(active: Boolean) {
        _uiState.update { it.copy(isLiveScreenActive = active) }
    }

    fun setSystemOverlayExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isSystemOverlayExpanded = expanded) }
    }

    fun toggleAiPanel(open: Boolean? = null) {
        _uiState.update { state ->
            state.copy(isAiPanelOpen = open ?: !state.isAiPanelOpen)
        }
    }

    fun captureLiveScreenAndSolve(inChatTab: Boolean, fromSystemOverlay: Boolean) {
        if (!ScreenProjectionService.isActive()) {
            _uiState.update { it.copy(isLiveScreenActive = false) }
            showToast("Aktifkan izin Live Screen terlebih dahulu")
            return
        }

        val wasSystemExpanded = _uiState.value.isSystemOverlayExpanded
        val wasInAppOpen = _uiState.value.isAiPanelOpen

        scope.launch {
            _uiState.update {
                it.copy(
                    isCapturingScreen = true,
                    isSystemOverlayExpanded = false,
                    isAiPanelOpen = false
                )
            }
            // Wait briefly so the overlay hides cleanly before grabbing the screen frame.
            delay(260L)

            val capturedBitmap = withContext(Dispatchers.Default) {
                ScreenProjectionService.captureLatestBitmap()
            }

            _uiState.update { it.copy(isCapturingScreen = false) }

            if (capturedBitmap != null) {
                if (fromSystemOverlay) {
                    _uiState.update { it.copy(isSystemOverlayExpanded = true) }
                } else {
                    _uiState.update { it.copy(isAiPanelOpen = true) }
                }

                if (inChatTab) {
                    sendChatMessage(
                        rawPrompt = "Baca soal atau materi yang tampil di layar ini, lalu berikan jawaban yang tepat dan mudah dipahami.",
                        attachedBitmap = capturedBitmap
                    )
                } else {
                    openToolInput(
                        toolType = TaskToolType.SCAN_PHOTO,
                        initialText = _uiState.value.scratchpadText,
                        initialBitmap = capturedBitmap,
                        openInAppPanel = !fromSystemOverlay
                    )
                    runActiveTool()
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSystemOverlayExpanded = wasSystemExpanded,
                        isAiPanelOpen = wasInAppOpen
                    )
                }
                showToast("Belum ada frame layar tertangkap. Coba ketuk Scan Layar lagi.")
            }
        }
    }

    fun selectAiTab(tab: AiPanelTab) {
        _uiState.update { it.copy(activeAiTab = tab) }
    }

    fun toggleQuickAnswerMode() {
        _uiState.update { it.copy(isQuickAnswerMode = !it.isQuickAnswerMode) }
    }

    fun setStealthMode(mode: StealthMode) {
        _uiState.update { state ->
            val antiCapture = if (mode == StealthMode.INVISIBLE) true else state.isAntiCaptureEnabled
            state.copy(
                stealthMode = mode,
                isAntiCaptureEnabled = antiCapture,
                aiStatusText = when (mode) {
                    StealthMode.NORMAL -> "Siap • Tugas AI Engine"
                    StealthMode.DIM_16 -> "Mode Samaran (16%)"
                    StealthMode.INVISIBLE -> "Mode Tak Terlihat (0% • Anti-Rekam)"
                }
            )
        }
    }

    fun cycleStealthMode() {
        val current = _uiState.value.stealthMode
        val next = when (current) {
            StealthMode.NORMAL -> StealthMode.DIM_16
            StealthMode.DIM_16 -> StealthMode.INVISIBLE
            StealthMode.INVISIBLE -> StealthMode.NORMAL
        }
        setStealthMode(next)
    }

    fun toggleAntiCapture() {
        _uiState.update { it.copy(isAntiCaptureEnabled = !it.isAntiCaptureEnabled) }
    }

    fun updateScratchpadText(text: String) {
        _uiState.update { it.copy(scratchpadText = text) }
    }

    fun openToolInput(
        toolType: TaskToolType,
        initialText: String = "",
        initialBitmap: Bitmap? = null,
        openInAppPanel: Boolean = true
    ) {
        _uiState.update {
            it.copy(
                isAiPanelOpen = if (openInAppPanel) true else it.isAiPanelOpen,
                activeAiTab = AiPanelTab.TOOLS,
                activeToolInputDialog = toolType,
                pendingToolText = initialText,
                pendingToolBitmap = initialBitmap,
                liveToolOutput = "",
                toolError = null
            )
        }
    }

    fun updatePendingToolText(text: String) {
        _uiState.update { it.copy(pendingToolText = text) }
    }

    fun updatePendingToolBitmap(bitmap: Bitmap?) {
        _uiState.update { it.copy(pendingToolBitmap = bitmap) }
    }

    fun closeToolInput() {
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                activeToolInputDialog = null,
                pendingToolText = "",
                pendingToolBitmap = null,
                isGenerating = false,
                aiStatusText = "Siap • Tugas AI Engine"
            )
        }
    }

    fun clearToolError() {
        _uiState.update { it.copy(toolError = null) }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        toastJob = scope.launch {
            _uiState.update { it.copy(toastMessage = message) }
            delay(2000L)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun runActiveTool() {
        val state = _uiState.value
        val tool = state.activeToolInputDialog ?: return
        val input = state.pendingToolText.trim()
        val bitmap = state.pendingToolBitmap

        if (input.isEmpty() && bitmap == null) {
            _uiState.update { it.copy(toolError = "Masukkan teks soal atau pilih foto tugas terlebih dahulu.") }
            return
        }

        activeStreamJob?.cancel()
        activeStreamJob = scope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    liveToolOutput = "",
                    toolError = null,
                    aiStatusText = "Gemini mengerjakan ${tool.title}..."
                )
            }
            val accumulated = StringBuilder()
            try {
                aiRepository.streamToolSolution(
                    toolType = tool,
                    userInput = input,
                    attachedBitmap = bitmap,
                    quickAnswerMode = state.isQuickAnswerMode
                ).collect { chunk ->
                    accumulated.append(chunk)
                    _uiState.update { it.copy(liveToolOutput = accumulated.toString()) }
                }

                val finalOutput = accumulated.toString().trim()
                if (finalOutput.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            toolError = "Model AI mengembalikan respons kosong.",
                            aiStatusText = "Error • Tugas AI Engine"
                        )
                    }
                    return@launch
                }

                val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val preview = input.ifBlank { "Foto Tugas (${tool.badge})" }.take(80)
                val savedItem = SolvedTaskItem(
                    id = UUID.randomUUID().toString(),
                    toolType = tool,
                    promptPreview = preview,
                    answerText = finalOutput,
                    timestampLabel = timeLabel,
                    hasImage = bitmap != null
                )

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        aiStatusText = "Tugas selesai dikerjakan",
                        solvedTasks = listOf(savedItem) + it.solvedTasks
                    )
                }
                showToast("Tugas Selesai Dikerjakan")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Tool generation timed out", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        toolError = "Waktu pemrosesan AI habis setelah 60 detik. Silakan coba lagi.",
                        aiStatusText = "Timeout • Tugas AI Engine"
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        aiStatusText = "Dihentikan"
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tool generation failed", e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        toolError = e.localizedMessage ?: "Gagal terhubung ke Firebase AI Logic.",
                        aiStatusText = "Error • Tugas AI Engine"
                    )
                }
            }
        }
    }

    fun sendChatMessage(rawPrompt: String, attachedBitmap: Bitmap? = null) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && attachedBitmap == null) return

        val effectivePrompt = if (prompt.isEmpty()) {
            "Tolong kerjakan soal yang ada pada foto tugas ini langkah demi langkah."
        } else {
            prompt
        }

        val historySnapshot = _uiState.value.chatMessages

        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = effectivePrompt,
            attachedBitmap = attachedBitmap
        )
        val modelMsgId = UUID.randomUUID().toString()
        val aiPlaceholder = AiChatMessage(
            id = modelMsgId,
            isUser = false,
            text = "",
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                activeAiTab = AiPanelTab.CHAT,
                isGenerating = true,
                aiStatusText = "Mengirim ke Gemini...",
                chatMessages = it.chatMessages + userMsg + aiPlaceholder
            )
        }

        activeStreamJob?.cancel()
        activeStreamJob = scope.launch {
            val accumulated = StringBuilder()
            try {
                aiRepository.streamChat(
                    prompt = effectivePrompt,
                    attachedBitmap = attachedBitmap,
                    quickAnswerMode = _uiState.value.isQuickAnswerMode,
                    priorMessages = historySnapshot
                ).collect { chunk ->
                    accumulated.append(chunk)
                    updateChatMessage(modelMsgId, accumulated.toString(), isStreaming = true, isError = false)
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isEmpty()) {
                    updateChatMessage(
                        modelMsgId,
                        "Model mengembalikan respons kosong.",
                        isStreaming = false,
                        isError = true
                    )
                    _uiState.update { it.copy(isGenerating = false, aiStatusText = "Error • Tugas AI Engine") }
                } else {
                    updateChatMessage(modelMsgId, finalText, isStreaming = false, isError = false)
                    val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val savedItem = SolvedTaskItem(
                        id = UUID.randomUUID().toString(),
                        toolType = if (attachedBitmap != null) TaskToolType.SCAN_PHOTO else TaskToolType.ESSAY_WRITER,
                        promptPreview = effectivePrompt.take(80),
                        answerText = finalText,
                        timestampLabel = timeLabel,
                        hasImage = attachedBitmap != null
                    )
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            aiStatusText = "Siap • Tugas AI Engine",
                            solvedTasks = listOf(savedItem) + it.solvedTasks
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat generation timed out", e)
                updateChatMessage(
                    modelMsgId,
                    "Waktu respons Gemini habis. Ketuk Coba Lagi atau aktifkan Mode Cepat.",
                    isStreaming = false,
                    isError = true
                )
                _uiState.update { it.copy(isGenerating = false, aiStatusText = "Timeout • Tugas AI Engine") }
            } catch (e: CancellationException) {
                val partial = accumulated.toString().ifBlank { "Generasi dihentikan oleh pengguna." }
                updateChatMessage(modelMsgId, partial, isStreaming = false, isError = false)
                _uiState.update { it.copy(isGenerating = false, aiStatusText = "Dihentikan") }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                updateChatMessage(
                    modelMsgId,
                    e.localizedMessage ?: "Gagal terhubung ke Firebase AI Logic.",
                    isStreaming = false,
                    isError = true
                )
                _uiState.update { it.copy(isGenerating = false, aiStatusText = "Error • Tugas AI Engine") }
            }
        }
    }

    fun retryLastUserPrompt() {
        val currentMessages = _uiState.value.chatMessages
        val lastUserMsg = currentMessages.lastOrNull { it.isUser } ?: return
        // Drop trailing failed AI message & duplicate user prompt before retrying cleanly
        val lastUserIdx = currentMessages.indexOfLast { it.id == lastUserMsg.id }
        if (lastUserIdx >= 0) {
            val trimmed = currentMessages.take(lastUserIdx)
            _uiState.update { it.copy(chatMessages = trimmed) }
        }
        sendChatMessage(lastUserMsg.text, lastUserMsg.attachedBitmap)
    }

    fun stopGeneration() {
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = false,
                aiStatusText = "Dihentikan oleh pengguna"
            )
        }
    }

    fun startNewChat() {
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = false,
                aiStatusText = "Siap • Tugas AI Engine",
                chatMessages = listOf(
                    AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = "Sesi baru siap. Ketik soal, lampirkan foto, atau ketuk Scan Layar untuk dijawab otomatis."
                    )
                )
            )
        }
    }

    fun deleteSolvedTask(id: String) {
        _uiState.update { state ->
            state.copy(solvedTasks = state.solvedTasks.filterNot { it.id == id })
        }
    }

    private fun updateChatMessage(
        messageId: String,
        newText: String,
        isStreaming: Boolean,
        isError: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                chatMessages = state.chatMessages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(text = newText, isStreaming = isStreaming, isError = isError)
                    } else {
                        msg
                    }
                }
            )
        }
    }
}
