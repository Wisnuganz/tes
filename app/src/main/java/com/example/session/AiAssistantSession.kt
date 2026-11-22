package com.example.session

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.ai.GeminiApiKeyStore
import com.example.ai.TaskAiRepository
import com.example.model.AiChatMessage
import com.example.model.AiPanelTab
import com.example.model.AutoAnswerDecision
import com.example.model.AutoSolveMode
import com.example.model.SolvedTaskItem
import com.example.model.StealthMode
import com.example.model.TapPulseEvent
import com.example.model.TaskAssistantUiState
import com.example.model.TaskToolType
import com.example.overlay.CerdasAccessibilityService
import com.example.overlay.ScreenProjectionService
import com.example.overlay.ScreenQuizSnapshot
import com.example.overlay.UiTargetNode
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
    private var autoSolveJob: Job? = null
    private var toastJob: Job? = null
    private var pulseJob: Job? = null
    private var lastTapPulseId: Long = 0L

    fun init(context: Context) {
        GeminiApiKeyStore.init(context)
        val savedKey = GeminiApiKeyStore.getApiKey(context)
        val hasKey = savedKey.isNotBlank()
        val accActive = CerdasAccessibilityService.isServiceEnabledInSettings(context)
        _uiState.update {
            it.copy(
                hasCustomApiKey = hasKey,
                maskedApiKey = GeminiApiKeyStore.maskKey(savedKey),
                isAccessibilityEnabled = accActive,
                apiKeyStatusMessage = if (hasKey) {
                    val modelHint = GeminiApiKeyStore.getVerifiedModel(context)
                    if (modelHint.isNotBlank()) "API Key aktif ($modelHint)" else "API Key tersimpan • Siap dipakai"
                } else {
                    null
                },
                aiStatusText = if (hasKey) "Siap • CBT & Tugas AI" else "Siap • Tugas AI Engine"
            )
        }
    }

    fun refreshAccessibilityStatus(context: Context) {
        val active = CerdasAccessibilityService.isServiceEnabledInSettings(context)
        _uiState.update { it.copy(isAccessibilityEnabled = active) }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isAccessibilityEnabled = enabled) }
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

    fun toggleSilumanMode() {
        val next = !_uiState.value.isSilumanMode
        _uiState.update {
            it.copy(
                isSilumanMode = next,
                toastMessage = if (next) null else it.toastMessage,
                tapPulseEvent = if (next) null else it.tapPulseEvent,
                aiStatusText = if (next) "AI Siluman AKTIF • Tanpa efek laser" else "Siap • Efek laser terlihat"
            )
        }
        if (!next) {
            showToast("Mode Siluman dimatikan (laser & toast aktif)")
        }
    }

    fun cycleAutoPace() {
        val paces = listOf(1, 2, 3, 5, 10, 30)
        val current = _uiState.value.autoPaceSeconds
        val idx = paces.indexOf(current)
        val next = paces[(idx + 1) % paces.size]
        _uiState.update { it.copy(autoPaceSeconds = next) }
        showToast("Tempo Auto-Jawab: $next detik/soal", force = true)
    }

    fun solveSingleScreenQuestion(fromSystemOverlay: Boolean) {
        if (autoSolveJob?.isActive == true) {
            stopGeneration()
            return
        }
        launchAutoSolveRoutine(
            mode = AutoSolveMode.SINGLE_QUESTION,
            fromSystemOverlay = fromSystemOverlay
        )
    }

    fun toggleAutoSolveLoop(targetKkm: Boolean, fromSystemOverlay: Boolean) {
        val requestedMode = if (targetKkm) AutoSolveMode.AUTO_KKM_80 else AutoSolveMode.AUTO_100
        if (_uiState.value.autoSolveMode == requestedMode && autoSolveJob?.isActive == true) {
            stopGeneration()
            return
        }
        launchAutoSolveRoutine(
            mode = requestedMode,
            fromSystemOverlay = fromSystemOverlay
        )
    }

    private fun launchAutoSolveRoutine(mode: AutoSolveMode, fromSystemOverlay: Boolean) {
        val hasAccessibility = CerdasAccessibilityService.isActive()
        val hasLiveScreen = ScreenProjectionService.isActive()
        if (!hasAccessibility && !hasLiveScreen) {
            showToast("Aktifkan Aksesibilitas Auto-Tap atau Live Screen dahulu", force = true)
            _uiState.update {
                it.copy(aiStatusText = "Perlu Aksesibilitas / Live Screen")
            }
            return
        }

        autoSolveJob?.cancel()
        activeStreamJob?.cancel()

        autoSolveJob = scope.launch {
            var solvedInRun = 0
            var previousFingerprint: String? = null
            var duplicateNoNextStreak = 0
            var waitingScreenStreak = 0

            _uiState.update {
                it.copy(
                    autoSolveMode = mode,
                    isGenerating = true,
                    aiStatusText = "${mode.label} berjalan..."
                )
            }

            try {
                while (true) {
                    val stepStartMs = System.currentTimeMillis()
                    val isKkmNaturalMiss = (mode == AutoSolveMode.AUTO_KKM_80) && ((solvedInRun + 1) % 5 == 0)

                    // Collapse panel cleanly on the first step; subsequent loops are already collapsed.
                    val wasExpanded = _uiState.value.isSystemOverlayExpanded
                    val wasInAppOpen = _uiState.value.isAiPanelOpen
                    val needCollapseDelay = wasExpanded || wasInAppOpen
                    if (needCollapseDelay) {
                        _uiState.update {
                            it.copy(
                                isCapturingScreen = true,
                                isSystemOverlayExpanded = false,
                                isAiPanelOpen = false
                            )
                        }
                        delay(140L)
                    } else {
                        _uiState.update { it.copy(isCapturingScreen = true) }
                    }

                    val snapshot: ScreenQuizSnapshot? = CerdasAccessibilityService.captureSnapshot()
                    val bitmap: Bitmap? = if (ScreenProjectionService.isActive()) {
                        withContext(Dispatchers.Default) {
                            ScreenProjectionService.captureLatestBitmap()
                        }
                    } else {
                        null
                    }

                    _uiState.update { it.copy(isCapturingScreen = false) }

                    if (snapshot == null && bitmap == null) {
                        if (fromSystemOverlay) {
                            _uiState.update { it.copy(isSystemOverlayExpanded = wasExpanded || solvedInRun > 0) }
                        } else {
                            _uiState.update { it.copy(isAiPanelOpen = wasInAppOpen || solvedInRun > 0) }
                        }
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                autoSolveMode = AutoSolveMode.IDLE,
                                aiStatusText = "Soal tidak terdeteksi di layar"
                            )
                        }
                        showToast("Aktifkan Aksesibilitas atau Live Screen agar soal terbaca.", force = true)
                        break
                    }

                    if (snapshot != null && previousFingerprint != null &&
                        snapshot.questionFingerprint == previousFingerprint &&
                        snapshot.nextButtonNode == null &&
                        bitmap == null
                    ) {
                        duplicateNoNextStreak++
                        if (duplicateNoNextStreak >= 2) {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    autoSolveMode = AutoSolveMode.IDLE,
                                    isSystemOverlayExpanded = fromSystemOverlay,
                                    aiStatusText = "Selesai • $solvedInRun soal terjawab"
                                )
                            }
                            showToast("Auto-Jawab selesai ($solvedInRun soal)", force = true)
                            break
                        }
                    } else {
                        duplicateNoNextStreak = 0
                    }

                    _uiState.update {
                        it.copy(aiStatusText = "AI menganalisis soal...")
                    }

                    val optionDescriptions = snapshot?.optionNodes?.map { node ->
                        val xr = "%.3f".format(Locale.US, node.centerX / snapshot.screenWidth.toFloat())
                        val yr = "%.3f".format(Locale.US, node.centerY / snapshot.screenHeight.toFloat())
                        "- [${node.optionLetter ?: "?"}] \"${node.label}\" @ ($xr, $yr)"
                    }.orEmpty()

                    val decision = aiRepository.solveQuestionForAutoTap(
                        screenText = snapshot?.combinedText.orEmpty(),
                        optionDescriptions = optionDescriptions,
                        attachedBitmap = bitmap,
                        preferNaturalMissForKkm = isKkmNaturalMiss
                    )

                    if (!decision.hasActiveQuestion) {
                        waitingScreenStreak++
                        if (waitingScreenStreak >= 10) {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    autoSolveMode = AutoSolveMode.IDLE,
                                    isSystemOverlayExpanded = fromSystemOverlay,
                                    aiStatusText = "Selesai • Tidak ada soal aktif ($solvedInRun terjawab)"
                                )
                            }
                            showToast("Selesai atau tidak ada soal aktif di layar ($solvedInRun soal)", force = true)
                            break
                        }
                        _uiState.update {
                            it.copy(aiStatusText = "Menunggu soal berikutnya muncul...")
                        }
                        delay(900L)
                        continue
                    }

                    waitingScreenStreak = 0
                    val preTapFingerprint = snapshot?.questionFingerprint
                    previousFingerprint = preTapFingerprint

                    solvedInRun++
                    val totalSolved = _uiState.value.autoSolvedCount + 1
                    val qLabel = snapshot?.questionNumberLabel?.ifBlank { decision.questionNumber }
                        ?: decision.questionNumber

                    logAutoSolvedResult(decision, qLabel, bitmap)

                    // Match target node or fallback ratio coordinates (real screen dimensions from snapshot or bitmap)
                    val matchedNode = findBestOptionNode(snapshot, decision)
                    val screenW = snapshot?.screenWidth ?: bitmap?.width ?: 1080
                    val screenH = snapshot?.screenHeight ?: bitmap?.height ?: 2400

                    val tapX = matchedNode?.centerX
                        ?: decision.answerTapXRatio?.let { it * screenW }
                    val tapY = matchedNode?.centerY
                        ?: decision.answerTapYRatio?.let { it * screenH }

                    var tapped = false
                    if (CerdasAccessibilityService.isActive() && tapX != null && tapY != null) {
                        emitTapPulse(tapX, tapY, isNextButton = false)
                        if (!_uiState.value.isSilumanMode) {
                            delay(80L)
                        }
                        tapped = CerdasAccessibilityService.tapOptionOrPoint(
                            optionNode = matchedNode,
                            fallbackX = tapX,
                            fallbackY = tapY
                        )
                    }

                    val statusBadge = buildString {
                        append(qLabel)
                        append(" Terjawab (")
                        append(decision.answerLetter.ifBlank { decision.answerText.take(14) })
                        append(")")
                        if (!tapped && !CerdasAccessibilityService.isActive()) {
                            append(" • Lihat Chat")
                        }
                    }

                    _uiState.update {
                        it.copy(
                            autoSolvedCount = totalSolved,
                            lastDetectedQuestionLabel = qLabel,
                            aiStatusText = statusBadge
                        )
                    }

                    showToast(
                        if (tapped) "Terjawab: ${decision.answerLetter.ifBlank { decision.answerText.take(18) }}"
                        else "Jawaban: ${decision.answerLetter.ifBlank { decision.answerText.take(24) }}"
                    )

                    if (mode == AutoSolveMode.SINGLE_QUESTION) {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                autoSolveMode = AutoSolveMode.IDLE,
                                isSystemOverlayExpanded = if (fromSystemOverlay) true else it.isSystemOverlayExpanded,
                                isAiPanelOpen = if (!fromSystemOverlay) true else it.isAiPanelOpen
                            )
                        }
                        break
                    }

                    // Subtract AI latency from the configured pace so Tempo 1s-30s stays crisp
                    val paceSec = _uiState.value.autoPaceSeconds.coerceIn(1, 60)
                    val targetCycleMs = paceSec * 1000L
                    val elapsedMs = System.currentTimeMillis() - stepStartMs
                    val remainingWaitMs = (targetCycleMs - elapsedMs).coerceAtLeast(350L)

                    if (remainingWaitMs > 1100L) {
                        var leftMs = remainingWaitMs
                        while (leftMs > 0L) {
                            val secLeft = ((leftMs + 999L) / 1000L).toInt()
                            _uiState.update {
                                it.copy(aiStatusText = "$qLabel • Lanjut dlm ${secLeft}d")
                            }
                            val chunk = leftMs.coerceAtMost(1000L)
                            delay(chunk)
                            leftMs -= chunk
                        }
                    } else {
                        _uiState.update {
                            it.copy(aiStatusText = "$qLabel • Lanjut soal berikutnya...")
                        }
                        delay(remainingWaitMs)
                    }

                    // Re-scan screen AFTER option tap & pace wait:
                    // 1) Apps like Quizizz/Kahoot often auto-advance immediately during wait!
                    // 2) Standard CBT apps often reveal/enable "Soal Selanjutnya" only after an option is tapped.
                    var postTapSnapshot = CerdasAccessibilityService.captureSnapshot() ?: snapshot
                    if (preTapFingerprint != null &&
                        postTapSnapshot != null &&
                        postTapSnapshot.questionFingerprint != preTapFingerprint
                    ) {
                        continue
                    }

                    var nextNode = postTapSnapshot?.nextButtonNode
                    var nextX = nextNode?.centerX ?: decision.nextTapXRatio?.let { it * screenW }
                    var nextY = nextNode?.centerY ?: decision.nextTapYRatio?.let { it * screenH }

                    if (nextNode == null && nextX == null && CerdasAccessibilityService.isActive()) {
                        _uiState.update {
                            it.copy(aiStatusText = "$qLabel • Cek lanjut / scroll...")
                        }
                        CerdasAccessibilityService.scrollDownSlightly()
                        delay(280L)
                        val scrolledSnapshot = CerdasAccessibilityService.captureSnapshot()
                        if (scrolledSnapshot != null) {
                            if (preTapFingerprint != null && scrolledSnapshot.questionFingerprint != preTapFingerprint) {
                                continue
                            }
                            postTapSnapshot = scrolledSnapshot
                            nextNode = scrolledSnapshot.nextButtonNode
                            nextX = nextNode?.centerX
                            nextY = nextNode?.centerY
                        }
                    }

                    if (CerdasAccessibilityService.isActive() && nextX != null && nextY != null) {
                        emitTapPulse(nextX, nextY, isNextButton = true)
                        if (!_uiState.value.isSilumanMode) {
                            delay(80L)
                        }
                        val nextTapped = CerdasAccessibilityService.tapOptionOrPoint(
                            optionNode = nextNode,
                            fallbackX = nextX,
                            fallbackY = nextY
                        )
                        if (!nextTapped) {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    autoSolveMode = AutoSolveMode.IDLE,
                                    isSystemOverlayExpanded = fromSystemOverlay,
                                    aiStatusText = "Auto berhenti • Tombol Lanjut gagal diketuk"
                                )
                            }
                            showToast("Auto berhenti: gagal mengetuk tombol Lanjut", force = true)
                            break
                        }
                        for (attempt in 0 until 5) {
                            delay(200L)
                            val updatedSnapshot = CerdasAccessibilityService.captureSnapshot()
                            if (updatedSnapshot != null && updatedSnapshot.questionFingerprint != preTapFingerprint) {
                                break
                            }
                        }
                    } else if (!CerdasAccessibilityService.isActive()) {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                autoSolveMode = AutoSolveMode.IDLE,
                                isSystemOverlayExpanded = fromSystemOverlay,
                                aiStatusText = "Jawaban siap • Aktifkan Aksesibilitas untuk Auto-Tap"
                            )
                        }
                        showToast("Aktifkan Aksesibilitas Auto-Tap agar lanjut otomatis", force = true)
                        break
                    } else {
                        // Quizizz / auto-advance mode without a Next button: poll briefly to catch delayed transition
                        var autoAdvanced = false
                        if (preTapFingerprint != null) {
                            for (poll in 0 until 4) {
                                delay(650L)
                                val polled = CerdasAccessibilityService.captureSnapshot()
                                if (polled != null && polled.questionFingerprint != preTapFingerprint) {
                                    autoAdvanced = true
                                    break
                                }
                            }
                        } else if (ScreenProjectionService.isActive()) {
                            // Live-screen-only Quizizz mode: continue loop and let hasActiveQuestion handle transitions
                            autoAdvanced = true
                        }

                        if (!autoAdvanced) {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    autoSolveMode = AutoSolveMode.IDLE,
                                    isSystemOverlayExpanded = fromSystemOverlay,
                                    aiStatusText = "Selesai • $solvedInRun soal terjawab"
                                )
                            }
                            showToast("Auto-Jawab selesai ($solvedInRun soal)", force = true)
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isCapturingScreen = false,
                        autoSolveMode = AutoSolveMode.IDLE,
                        aiStatusText = "Auto-Jawab dihentikan"
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Auto-Solve failed", e)
                val errMsg = e.localizedMessage ?: "Gagal menjalankan Auto-Jawab."
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        isCapturingScreen = false,
                        autoSolveMode = AutoSolveMode.IDLE,
                        isSystemOverlayExpanded = if (fromSystemOverlay) true else state.isSystemOverlayExpanded,
                        isAiPanelOpen = if (!fromSystemOverlay) true else state.isAiPanelOpen,
                        aiStatusText = "Error: ${errMsg.take(42)}",
                        chatMessages = state.chatMessages + AiChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            text = errMsg,
                            isError = true
                        )
                    )
                }
                showToast(errMsg, force = true)
            }
        }
    }

    private fun normalizeOptionText(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("^[a-e][\\.)\\]:\\s]+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun findBestOptionNode(
        snapshot: ScreenQuizSnapshot?,
        decision: AutoAnswerDecision
    ): UiTargetNode? {
        val nodes = snapshot?.optionNodes ?: return null
        if (nodes.isEmpty()) return null
        if (decision.answerLetter.isNotBlank()) {
            val byLetter = nodes.firstOrNull {
                it.optionLetter.equals(decision.answerLetter, ignoreCase = true)
            }
            if (byLetter != null) return byLetter
        }

        val targetRaw = decision.answerText.trim().lowercase()
        val targetNorm = normalizeOptionText(decision.answerText)
        if (targetRaw.isNotBlank()) {
            val byRawContains = nodes.firstOrNull { node ->
                val nl = node.label.lowercase()
                nl.contains(targetRaw) || targetRaw.contains(nl)
            }
            if (byRawContains != null) return byRawContains

            if (targetNorm.isNotBlank()) {
                val byNormExact = nodes.firstOrNull { normalizeOptionText(it.label) == targetNorm }
                if (byNormExact != null) return byNormExact

                val byNormContains = nodes.firstOrNull { node ->
                    val nl = normalizeOptionText(node.label)
                    nl.isNotBlank() && (nl.contains(targetNorm) || targetNorm.contains(nl))
                }
                if (byNormContains != null) return byNormContains

                val targetTokens = targetNorm.split(' ').filter { it.isNotBlank() }.toSet()
                if (targetTokens.isNotEmpty()) {
                    var bestNode: UiTargetNode? = null
                    var bestScore = 0.0
                    for (node in nodes) {
                        val nodeTokens = normalizeOptionText(node.label).split(' ').filter { it.isNotBlank() }.toSet()
                        if (nodeTokens.isEmpty()) continue
                        val overlap = targetTokens.intersect(nodeTokens).size.toDouble()
                        val score = overlap / maxOf(targetTokens.size, nodeTokens.size).toDouble()
                        if (score >= 0.6 && score > bestScore) {
                            bestScore = score
                            bestNode = node
                        }
                    }
                    if (bestNode != null) return bestNode
                }
            }
        }
        return null
    }

    private fun emitTapPulse(x: Float, y: Float, isNextButton: Boolean) {
        if (_uiState.value.isSilumanMode) return
        pulseJob?.cancel()
        val pulseId = ++lastTapPulseId
        _uiState.update {
            it.copy(tapPulseEvent = TapPulseEvent(id = pulseId, x = x, y = y, isNextButton = isNextButton))
        }
        pulseJob = scope.launch {
            delay(650L)
            if (_uiState.value.tapPulseEvent?.id == pulseId) {
                _uiState.update { it.copy(tapPulseEvent = null) }
            }
        }
    }

    private fun logAutoSolvedResult(
        decision: AutoAnswerDecision,
        qLabel: String,
        bitmap: Bitmap?
    ) {
        val formattedAnswer = buildString {
            appendLine("> **Jawaban ${qLabel}: ${decision.answerLetter.ifBlank { decision.answerText }}**")
            if (decision.answerText.isNotBlank()) {
                appendLine()
                appendLine("**Opsi Terpilih:** ${decision.answerText}")
            }
            if (decision.reason.isNotBlank()) {
                appendLine()
                appendLine("### Pembahasan Singkat")
                appendLine(decision.reason)
            }
        }.trim()

        val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val savedItem = SolvedTaskItem(
            id = UUID.randomUUID().toString(),
            toolType = TaskToolType.MULTIPLE_CHOICE,
            promptPreview = decision.questionSummary.ifBlank { qLabel }.take(80),
            answerText = formattedAnswer,
            timestampLabel = timeLabel,
            hasImage = bitmap != null
        )
        val msg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = formattedAnswer
        )
        _uiState.update {
            it.copy(
                solvedTasks = listOf(savedItem) + it.solvedTasks,
                chatMessages = it.chatMessages + msg
            )
        }
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

    fun showToast(message: String, force: Boolean = false) {
        if (!force && _uiState.value.isSilumanMode) return
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
        autoSolveJob?.cancel()
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = false,
                isCapturingScreen = false,
                autoSolveMode = AutoSolveMode.IDLE,
                tapPulseEvent = null,
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
