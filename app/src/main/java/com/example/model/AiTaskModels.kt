package com.example.model

import android.graphics.Bitmap

enum class AiPanelTab {
    TOOLS,
    CHAT
}

enum class StealthMode(
    val label: String,
    val badge: String,
    val description: String,
    val bubbleAlpha: Float
) {
    NORMAL(
        label = "Normal",
        badge = "100%",
        description = "Tombol AI terlihat jelas",
        bubbleAlpha = 1.0f
    ),
    DIM_16(
        label = "Samaran Tipis",
        badge = "16%",
        description = "Logo samar transparan (16%)",
        bubbleAlpha = 0.16f
    ),
    INVISIBLE(
        label = "Tak Terlihat",
        badge = "0%",
        description = "Bola gaib (0% visual) + anti-rekam layar",
        bubbleAlpha = 0.01f
    )
}

enum class TaskToolType(
    val title: String,
    val badge: String,
    val subtitle: String,
    val placeholder: String
) {
    SCAN_PHOTO(
        title = "Scan Foto Tugas",
        badge = "FOTO",
        subtitle = "Foto / screenshot soal",
        placeholder = "Tambahkan catatan (opsional), misal: Kerjakan nomor 1-5"
    ),
    MULTIPLE_CHOICE(
        title = "Jawab Pilihan Ganda",
        badge = "PG A-E",
        subtitle = "Tempel soal & opsi",
        placeholder = "Tempel soal pilihan ganda beserta opsi A, B, C, D, E di sini..."
    ),
    ESSAY_WRITER(
        title = "Kerjakan Esai",
        badge = "ESAI",
        subtitle = "Jawaban rapi siap salin",
        placeholder = "Tulis atau tempel pertanyaan esai / uraian tugas di sini..."
    ),
    SUMMARIZE(
        title = "Ringkas Materi",
        badge = "RINGKAS",
        subtitle = "Poin penting & rumus",
        placeholder = "Tempel teks materi, artikel, atau catatan pelajaran di sini..."
    ),
    PROOFREAD(
        title = "Cek & Koreksi",
        badge = "KOREKSI",
        subtitle = "Periksa jawabanmu",
        placeholder = "Tempel soal beserta jawabanmu untuk dicek dan diperbaiki oleh AI..."
    )
}

data class SolvedTaskItem(
    val id: String,
    val toolType: TaskToolType,
    val promptPreview: String,
    val answerText: String,
    val timestampLabel: String,
    val hasImage: Boolean = false
)

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val attachedBitmap: Bitmap? = null
)

data class TaskAssistantUiState(
    val isAiPanelOpen: Boolean = false,
    val isSystemOverlayExpanded: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isOverlayServiceRunning: Boolean = false,
    val isLiveScreenActive: Boolean = false,
    val isCapturingScreen: Boolean = false,
    val activeAiTab: AiPanelTab = AiPanelTab.CHAT,
    val isQuickAnswerMode: Boolean = true,
    val stealthMode: StealthMode = StealthMode.NORMAL,
    val isAntiCaptureEnabled: Boolean = false,
    val aiStatusText: String = "Siap • Tugas AI Engine",
    val activeToolInputDialog: TaskToolType? = null,
    val pendingToolText: String = "",
    val pendingToolBitmap: Bitmap? = null,
    val isGenerating: Boolean = false,
    val liveToolOutput: String = "",
    val toolError: String? = null,
    val toastMessage: String? = null,
    val solvedTasks: List<SolvedTaskItem> = emptyList(),
    val chatMessages: List<AiChatMessage> = emptyList(),
    val scratchpadText: String = "",
    val hasCustomApiKey: Boolean = false,
    val maskedApiKey: String = "",
    val apiKeyStatusMessage: String? = null,
    val isTestingApiKey: Boolean = false
)
