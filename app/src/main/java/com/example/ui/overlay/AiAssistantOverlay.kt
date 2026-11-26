package com.example.ui.overlay

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AiChatMessage
import com.example.model.AiPanelTab
import com.example.model.AutoSolveMode
import com.example.model.StealthMode
import com.example.model.TaskAssistantUiState
import com.example.model.TaskToolType
import com.example.ui.components.AiKnotOrbIcon
import com.example.ui.components.ChatGptMarkdownText
import com.example.ui.screens.decodeBitmapFromUri
import com.example.ui.theme.AiAmber
import com.example.ui.theme.AiBlue
import com.example.ui.theme.AiCardBorder
import com.example.ui.theme.AiCardDark
import com.example.ui.theme.AiEmerald
import com.example.ui.theme.AiEmeraldTint
import com.example.ui.theme.AiPanelBg
import com.example.ui.theme.AiPanelElevated
import com.example.ui.theme.AiPurple
import com.example.ui.theme.AiStopRed
import com.example.ui.theme.AiStopRedTint
import com.example.ui.theme.AiTextPrimary
import com.example.ui.theme.AiTextSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily

@Composable
fun AiAssistantOverlay(
    state: TaskAssistantUiState,
    onClose: () -> Unit,
    onSelectTab: (AiPanelTab) -> Unit,
    onToggleQuickAnswerMode: () -> Unit,
    onSelectStealthMode: (StealthMode) -> Unit,
    onToggleAntiCapture: () -> Unit,
    onOpenToolInput: (TaskToolType, String, Bitmap?) -> Unit,
    onUpdatePendingToolText: (String) -> Unit,
    onUpdatePendingToolBitmap: (Bitmap?) -> Unit,
    onRunActiveTool: () -> Unit,
    onCloseToolInput: () -> Unit,
    onStopGeneration: () -> Unit,
    onClearError: () -> Unit,
    onNewChat: () -> Unit,
    onSendChat: (String, Bitmap?) -> Unit,
    onCopyToast: (String) -> Unit,
    onScanScreen: () -> Unit,
    onRetryLastPrompt: (() -> Unit)? = null,
    onSaveApiKey: ((String) -> Unit)? = null,
    onClearApiKey: (() -> Unit)? = null,
    onToggleSilumanMode: (() -> Unit)? = null,
    onCycleAutoPace: (() -> Unit)? = null,
    onSolveSingleQuestion: (() -> Unit)? = null,
    onToggleAutoSolve: ((targetKkm: Boolean) -> Unit)? = null,
    onOpenAccessibilitySettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var pendingPhotoTargetIsTool by remember { mutableStateOf(true) }
    var chatAttachedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                if (pendingPhotoTargetIsTool) {
                    onUpdatePendingToolBitmap(bmp)
                } else {
                    chatAttachedBitmap = bmp
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            onUpdatePendingToolBitmap(bmp)
        }
    }

    AnimatedVisibility(
        visible = state.isAiPanelOpen && !state.isCapturingScreen,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AiPanelCardContent(
                state = state,
                isSystemOverlayContext = false,
                externalChatAttachedBitmap = chatAttachedBitmap,
                onClearExternalChatBitmap = { chatAttachedBitmap = null },
                onClose = onClose,
                onSelectTab = onSelectTab,
                onToggleQuickAnswerMode = onToggleQuickAnswerMode,
                onSelectStealthMode = onSelectStealthMode,
                onToggleAntiCapture = onToggleAntiCapture,
                onOpenToolInput = { tool ->
                    onOpenToolInput(tool, state.scratchpadText, null)
                },
                onUpdatePendingToolText = onUpdatePendingToolText,
                onUpdatePendingToolBitmap = onUpdatePendingToolBitmap,
                onPickPhotoFromGallery = { forTool ->
                    pendingPhotoTargetIsTool = forTool
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTakePhotoFromCamera = {
                    cameraLauncher.launch(null)
                },
                onRunActiveTool = onRunActiveTool,
                onCloseToolInput = onCloseToolInput,
                onStopGeneration = onStopGeneration,
                onClearError = onClearError,
                onNewChat = onNewChat,
                onSendChat = onSendChat,
                onCopyText = { text ->
                    clipboardManager.setText(AnnotatedString(text))
                    onCopyToast("Jawaban disalin")
                },
                onScanScreen = onScanScreen,
                onRetryLastPrompt = onRetryLastPrompt,
                onSaveApiKey = onSaveApiKey,
                onClearApiKey = onClearApiKey,
                onToggleSilumanMode = onToggleSilumanMode,
                onCycleAutoPace = onCycleAutoPace,
                onSolveSingleQuestion = onSolveSingleQuestion,
                onToggleAutoSolve = onToggleAutoSolve,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }
    }
}

@Composable
fun AiPanelCardContent(
    state: TaskAssistantUiState,
    isSystemOverlayContext: Boolean,
    onClose: () -> Unit,
    onSelectTab: (AiPanelTab) -> Unit,
    onToggleQuickAnswerMode: () -> Unit,
    onSelectStealthMode: (StealthMode) -> Unit,
    onToggleAntiCapture: () -> Unit,
    onOpenToolInput: (TaskToolType) -> Unit,
    onUpdatePendingToolText: (String) -> Unit,
    onUpdatePendingToolBitmap: (Bitmap?) -> Unit,
    onPickPhotoFromGallery: (forTool: Boolean) -> Unit,
    onTakePhotoFromCamera: () -> Unit,
    onRunActiveTool: () -> Unit,
    onCloseToolInput: () -> Unit,
    onStopGeneration: () -> Unit,
    onClearError: () -> Unit,
    onNewChat: () -> Unit,
    onSendChat: (String, Bitmap?) -> Unit,
    onCopyText: (String) -> Unit,
    externalChatAttachedBitmap: Bitmap? = null,
    onClearExternalChatBitmap: () -> Unit = {},
    onScanScreen: (() -> Unit)? = null,
    onStopSystemOverlay: (() -> Unit)? = null,
    onRetryLastPrompt: (() -> Unit)? = null,
    onSaveApiKey: ((String) -> Unit)? = null,
    onClearApiKey: (() -> Unit)? = null,
    onToggleSilumanMode: (() -> Unit)? = null,
    onCycleAutoPace: (() -> Unit)? = null,
    onSolveSingleQuestion: (() -> Unit)? = null,
    onToggleAutoSolve: ((targetKkm: Boolean) -> Unit)? = null,
    onOpenAccessibilitySettings: (() -> Unit)? = null,
    onInputFocusChange: ((Boolean) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var chatInputText by remember { mutableStateOf("") }

    val releaseInputFocus: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onInputFocusChange?.invoke(false)
    }

    DisposableEffect(onInputFocusChange) {
        onDispose {
            onInputFocusChange?.invoke(false)
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AiPanelBg,
        border = BorderStroke(1.dp, AiCardBorder),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .heightIn(max = if (isSystemOverlayContext) 560.dp else 680.dp)
            .then(
                if (!isSystemOverlayContext) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                } else {
                    Modifier
                }
            )
            .testTag("ai_overlay_panel")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            releaseInputFocus()
                            onSelectTab(
                                if (state.activeAiTab == AiPanelTab.TOOLS) AiPanelTab.CHAT else AiPanelTab.TOOLS
                            )
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Ganti tab AI",
                            tint = AiTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.ai_brand_title),
                        color = AiTextPrimary,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.isQuickAnswerMode) AiEmeraldTint else AiCardDark,
                        border = BorderStroke(
                            1.dp,
                            if (state.isQuickAnswerMode) AiEmerald.copy(alpha = 0.6f) else AiCardBorder
                        ),
                        modifier = Modifier
                            .clickable(onClick = onToggleQuickAnswerMode)
                            .testTag("header_quick_mode_badge")
                    ) {
                        Text(
                            text = if (state.isQuickAnswerMode) "Cepat • G3" else "Akurat • G3",
                            color = AiEmerald,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSystemOverlayContext && onStopSystemOverlay != null) {
                        IconButton(
                            onClick = onStopSystemOverlay,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Matikan overlay sistem",
                                tint = AiStopRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            releaseInputFocus()
                            onNewChat()
                            onSelectTab(AiPanelTab.CHAT)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("ai_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Chat baru",
                            tint = AiTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            releaseInputFocus()
                            onClose()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("ai_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Perkecil ke ikon mengambang",
                            tint = AiTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = AiCardBorder.copy(alpha = 0.7f), thickness = 1.dp)

            // Status & Tools/Chat Segmented Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            color = AiEmerald,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AiEmerald)
                        )
                    }
                    Text(
                        text = state.aiStatusText,
                        color = AiTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AiPanelElevated,
                    border = BorderStroke(1.dp, AiCardBorder)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        SegmentedTabChip(
                            label = "Tools",
                            selected = state.activeAiTab == AiPanelTab.TOOLS,
                            onClick = {
                                releaseInputFocus()
                                onSelectTab(AiPanelTab.TOOLS)
                            },
                            tag = "tab_tools"
                        )
                        SegmentedTabChip(
                            label = "Chat",
                            selected = state.activeAiTab == AiPanelTab.CHAT,
                            onClick = {
                                releaseInputFocus()
                                onSelectTab(AiPanelTab.CHAT)
                            },
                            tag = "tab_chat"
                        )
                    }
                }
            }

            HorizontalDivider(color = AiCardBorder.copy(alpha = 0.55f), thickness = 1.dp)

            if (!state.toastMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AiEmeraldTint,
                    border = BorderStroke(1.dp, AiEmerald.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = state.toastMessage,
                        color = AiTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Body Content
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
            ) {
                if (state.activeAiTab == AiPanelTab.TOOLS) {
                    val activeTool = state.activeToolInputDialog
                    if (activeTool != null) {
                        ActiveToolSolverPane(
                            tool = activeTool,
                            pendingText = state.pendingToolText,
                            pendingBitmap = state.pendingToolBitmap,
                            isGenerating = state.isGenerating,
                            liveOutput = state.liveToolOutput,
                            errorText = state.toolError,
                            onBack = {
                                releaseInputFocus()
                                onCloseToolInput()
                            },
                            onTextChange = onUpdatePendingToolText,
                            onPasteClipboard = {
                                val clip = clipboardManager.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) {
                                    onUpdatePendingToolText(clip)
                                }
                            },
                            onPickGallery = {
                                releaseInputFocus()
                                onPickPhotoFromGallery(true)
                            },
                            onTakePhoto = {
                                releaseInputFocus()
                                onTakePhotoFromCamera()
                            },
                            onRemoveBitmap = { onUpdatePendingToolBitmap(null) },
                            onSolve = {
                                releaseInputFocus()
                                onRunActiveTool()
                            },
                            onStop = onStopGeneration,
                            onClearError = onClearError,
                            onCopyOutput = { onCopyText(state.liveToolOutput) },
                            onInputFocusChange = onInputFocusChange
                        )
                    } else {
                        AiHomeworkToolsGrid(
                            state = state,
                            onToggleQuickAnswerMode = onToggleQuickAnswerMode,
                            onSelectStealthMode = onSelectStealthMode,
                            onToggleAntiCapture = onToggleAntiCapture,
                            onOpenTool = { tool ->
                                releaseInputFocus()
                                onOpenToolInput(tool)
                            },
                            onScanScreen = {
                                releaseInputFocus()
                                onScanScreen?.invoke()
                            },
                            onStopGeneration = onStopGeneration,
                            onSaveApiKey = { key ->
                                releaseInputFocus()
                                onSaveApiKey?.invoke(key)
                            },
                            onClearApiKey = onClearApiKey,
                            onToggleSilumanMode = onToggleSilumanMode,
                            onCycleAutoPace = onCycleAutoPace,
                            onSolveSingleQuestion = {
                                releaseInputFocus()
                                onSolveSingleQuestion?.invoke()
                            },
                            onToggleAutoSolve = { targetKkm ->
                                releaseInputFocus()
                                onToggleAutoSolve?.invoke(targetKkm)
                            },
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onInputFocusChange = onInputFocusChange
                        )
                    }
                } else {
                    AiChatTabContent(
                        state = state,
                        onQuickPrompt = { promptText -> onSendChat(promptText, null) },
                        onScanScreen = onScanScreen,
                        onCopyAnswer = onCopyText,
                        onRetryLastPrompt = onRetryLastPrompt,
                        onOpenApiKeyTools = { onSelectTab(AiPanelTab.TOOLS) }
                    )
                }
            }

            // Bottom Composer Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (externalChatAttachedBitmap != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AiPanelElevated)
                            .padding(6.dp)
                    ) {
                        Image(
                            bitmap = externalChatAttachedBitmap.asImageBitmap(),
                            contentDescription = "Foto tugas terlampir",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Foto soal siap dikirim",
                            color = AiEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearExternalChatBitmap,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hapus foto",
                                tint = AiTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = AiPanelElevated,
                    border = BorderStroke(1.dp, AiCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { onPickPhotoFromGallery(false) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AiCardDark)
                                .testTag("chat_attach_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Lampirkan foto tugas",
                                tint = AiTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (onScanScreen != null) {
                            IconButton(
                                onClick = onScanScreen,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.isLiveScreenActive) AiEmeraldTint else AiCardDark
                                    )
                                    .testTag("chat_scan_screen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitScreen,
                                    contentDescription = "Scan layar otomatis",
                                    tint = if (state.isLiveScreenActive) AiEmerald else AiTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        BasicTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = AiTextPrimary,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(AiEmerald),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (chatInputText.isEmpty()) {
                                        Text(
                                            text = "Tanya atau tempel tugas...",
                                            color = AiTextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .overlayTextInputFocus(onInputFocusChange)
                                .testTag("ai_chat_input")
                        )

                        IconButton(
                            onClick = {
                                if (state.isGenerating) {
                                    onStopGeneration()
                                } else if (chatInputText.isNotBlank() || externalChatAttachedBitmap != null) {
                                    val textToSend = chatInputText
                                    val bmpToSend = externalChatAttachedBitmap
                                    chatInputText = ""
                                    onClearExternalChatBitmap()
                                    releaseInputFocus()
                                    onSendChat(textToSend, bmpToSend)
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isGenerating) AiStopRed else AiTextPrimary
                                )
                                .testTag("ai_send_button")
                        ) {
                            Icon(
                                imageVector = if (state.isGenerating) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                contentDescription = if (state.isGenerating) "Hentikan" else "Kirim ke AI",
                                tint = if (state.isGenerating) Color.White else AiPanelBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) AiCardDark else Color.Transparent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = label,
            color = if (selected) AiTextPrimary else AiTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AiHomeworkToolsGrid(
    state: TaskAssistantUiState,
    onToggleQuickAnswerMode: () -> Unit,
    onSelectStealthMode: (StealthMode) -> Unit,
    onToggleAntiCapture: () -> Unit,
    onOpenTool: (TaskToolType) -> Unit,
    onScanScreen: (() -> Unit)?,
    onStopGeneration: () -> Unit,
    onSaveApiKey: ((String) -> Unit)? = null,
    onClearApiKey: (() -> Unit)? = null,
    onToggleSilumanMode: (() -> Unit)? = null,
    onCycleAutoPace: (() -> Unit)? = null,
    onSolveSingleQuestion: (() -> Unit)? = null,
    onToggleAutoSolve: ((targetKkm: Boolean) -> Unit)? = null,
    onOpenAccessibilitySettings: (() -> Unit)? = null,
    onInputFocusChange: ((Boolean) -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    var showApiKeyEditor by remember(state.hasCustomApiKey) {
        mutableStateOf(!state.hasCustomApiKey)
    }
    var overlayKeyDraft by remember { mutableStateOf("") }
    var revealOverlayKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // AI Translasi (Siluman) Toggle Banner (matching reference video)
        if (onToggleSilumanMode != null) {
            Surface(
                onClick = onToggleSilumanMode,
                shape = RoundedCornerShape(14.dp),
                color = if (state.isSilumanMode) AiEmeraldTint else AiPanelElevated,
                border = BorderStroke(
                    1.dp,
                    if (state.isSilumanMode) AiEmerald.copy(alpha = 0.7f) else AiCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("siluman_mode_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isSilumanMode) AiEmerald.copy(alpha = 0.25f) else AiCardDark
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "AI Siluman",
                                tint = if (state.isSilumanMode) AiEmerald else AiStopRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "AI Translasi (Siluman)",
                                color = AiTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.isSilumanMode) {
                                    "Aktif • Tanpa efek laser & toast"
                                } else {
                                    "Nonaktif • Animasi & laser terlihat"
                                },
                                color = AiTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.isSilumanMode) AiEmerald else AiCardDark,
                        border = BorderStroke(
                            1.dp,
                            if (state.isSilumanMode) AiEmerald else AiCardBorder
                        )
                    ) {
                        Text(
                            text = if (state.isSilumanMode) "ON" else "OFF",
                            color = if (state.isSilumanMode) Color.White else AiTextPrimary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Video-style 2x3 Auto-Jawab Action Grid
        if (onSolveSingleQuestion != null && onToggleAutoSolve != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiToolActionCard(
                        title = "Auto Nilai 100",
                        subtitle = if (state.autoSolveMode == AutoSolveMode.AUTO_100) {
                            "Aktif • Klik untuk henti"
                        } else {
                            "Selesaikan (${state.autoPaceSeconds}d/soal)"
                        },
                        badgeText = "100%",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = AiEmerald,
                        isActive = state.autoSolveMode == AutoSolveMode.AUTO_100,
                        onClick = { onToggleAutoSolve(false) },
                        tag = "tool_auto_100",
                        modifier = Modifier.weight(1f)
                    )
                    AiToolActionCard(
                        title = "Auto Aman KKM",
                        subtitle = if (state.autoSolveMode == AutoSolveMode.AUTO_KKM_80) {
                            "Aktif • Klik untuk henti"
                        } else {
                            "Skor natural ~80%"
                        },
                        badgeText = "KKM 80",
                        icon = Icons.Default.Percent,
                        accentColor = AiAmber,
                        isActive = state.autoSolveMode == AutoSolveMode.AUTO_KKM_80,
                        onClick = { onToggleAutoSolve(true) },
                        tag = "tool_auto_kkm",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiToolActionCard(
                        title = "Jawab 1 Soal",
                        subtitle = "Isi nomor aktif",
                        badgeText = "1 SOAL",
                        icon = Icons.Default.PlayArrow,
                        accentColor = AiBlue,
                        isActive = state.autoSolveMode == AutoSolveMode.SINGLE_QUESTION,
                        onClick = onSolveSingleQuestion,
                        tag = "tool_solve_single",
                        modifier = Modifier.weight(1f)
                    )
                    AiToolActionCard(
                        title = "Tempo ${state.autoPaceSeconds} Detik",
                        subtitle = "1 soal tiap ${state.autoPaceSeconds} detik",
                        badgeText = "${state.autoPaceSeconds}s",
                        icon = Icons.Default.Timer,
                        accentColor = AiPurple,
                        isActive = false,
                        onClick = { onCycleAutoPace?.invoke() },
                        tag = "tool_cycle_pace",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiToolActionCard(
                        title = "Stop Generation",
                        subtitle = "Hentikan proses AI",
                        badgeText = "STOP",
                        icon = Icons.Default.Stop,
                        accentColor = AiStopRed,
                        isActive = state.isGenerating,
                        onClick = onStopGeneration,
                        tag = "tool_stop_generation",
                        modifier = Modifier.weight(1f)
                    )
                    AiToolActionCard(
                        title = "Mode Samaran",
                        subtitle = "Tombol disamarkan (${state.stealthMode.badge})",
                        badgeText = if (state.stealthMode == StealthMode.NORMAL) "OFF" else "AKTIF",
                        icon = Icons.Default.VisibilityOff,
                        accentColor = AiEmerald,
                        isActive = state.stealthMode != StealthMode.NORMAL,
                        onClick = {
                            val next = when (state.stealthMode) {
                                StealthMode.NORMAL -> StealthMode.DIM_16
                                StealthMode.DIM_16 -> StealthMode.INVISIBLE
                                StealthMode.INVISIBLE -> StealthMode.NORMAL
                            }
                            onSelectStealthMode(next)
                        },
                        tag = "tool_cycle_stealth",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Accessibility Auto-Tap Readiness Card
        if (onOpenAccessibilitySettings != null && !state.isAccessibilityEnabled) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AiPanelElevated,
                border = BorderStroke(1.dp, AiBlue.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAccessibilitySettings)
                    .testTag("overlay_enable_accessibility_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Aktifkan Auto-Tap",
                            tint = AiBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Aktifkan Auto-Tap Aksesibilitas",
                                color = AiTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Agar AI bisa membaca soal & mengetuk jawaban A-E otomatis",
                                color = AiTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = AiBlue
                    ) {
                        Text(
                            text = "ATUR",
                            color = Color.White,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        if (onSaveApiKey != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (state.hasCustomApiKey) AiEmeraldTint else AiPanelElevated,
                border = BorderStroke(
                    1.dp,
                    if (state.hasCustomApiKey) AiEmerald.copy(alpha = 0.65f) else AiAmber.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showApiKeyEditor = !showApiKeyEditor },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Gemini API Key",
                                tint = if (state.hasCustomApiKey) AiEmerald else AiAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "Gemini API Key",
                                    color = AiTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.hasCustomApiKey) {
                                        "Aktif: ${state.maskedApiKey}"
                                    } else {
                                        "Pasang key gratis dari aistudio.google.com/apikey"
                                    },
                                    color = AiTextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (state.hasCustomApiKey) AiEmerald else AiCardDark
                        ) {
                            Text(
                                text = if (state.hasCustomApiKey) "AKTIF" else "ATUR",
                                color = if (state.hasCustomApiKey) Color.White else AiAmber,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (showApiKeyEditor) {
                        OutlinedTextField(
                            value = overlayKeyDraft,
                            onValueChange = { overlayKeyDraft = it },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = if (state.hasCustomApiKey) {
                                        "Key tersimpan (${state.maskedApiKey})"
                                    } else {
                                        "Tempel AIza... di sini"
                                    },
                                    color = AiTextSecondary,
                                    fontSize = 11.sp
                                )
                            },
                            visualTransformation = if (revealOverlayKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { revealOverlayKey = !revealOverlayKey },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (revealOverlayKey) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = "Tampilkan atau sembunyikan key",
                                            tint = AiTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                            if (clip.isNotBlank()) {
                                                overlayKeyDraft = clip
                                            }
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("overlay_paste_api_key_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Tempel API Key",
                                            tint = AiEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AiEmerald,
                                unfocusedBorderColor = AiCardBorder,
                                focusedTextColor = AiTextPrimary,
                                unfocusedTextColor = AiTextPrimary,
                                focusedContainerColor = AiCardDark,
                                unfocusedContainerColor = AiCardDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .overlayTextInputFocus(onInputFocusChange)
                                .testTag("overlay_api_key_input")
                        )

                        if (!state.apiKeyStatusMessage.isNullOrBlank()) {
                            Text(
                                text = state.apiKeyStatusMessage,
                                color = if (state.apiKeyStatusMessage.startsWith("Gagal")) {
                                    Color(0xFFFCA5A5)
                                } else {
                                    AiEmerald
                                },
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSaveApiKey(overlayKeyDraft)
                                    overlayKeyDraft = ""
                                },
                                enabled = !state.isTestingApiKey && overlayKeyDraft.isNotBlank(),
                                shape = RoundedCornerShape(9.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AiEmerald),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("overlay_save_api_key_button")
                            ) {
                                if (state.isTestingApiKey) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Tes & Simpan",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (state.hasCustomApiKey && onClearApiKey != null) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = AiStopRedTint,
                                    border = BorderStroke(1.dp, AiStopRed.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clickable(onClick = onClearApiKey)
                                        .testTag("overlay_clear_api_key_button")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        Text(
                                            text = "Hapus",
                                            color = Color(0xFFFCA5A5),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (onScanScreen != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (state.isLiveScreenActive) AiEmeraldTint else AiPanelElevated,
                border = BorderStroke(
                    1.dp,
                    if (state.isLiveScreenActive) AiEmerald else AiCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScanScreen)
                    .testTag("panel_live_screen_scan_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.isLiveScreenActive) AiEmerald.copy(alpha = 0.22f) else AiCardDark,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FitScreen,
                                    contentDescription = "Scan Layar Otomatis",
                                    tint = if (state.isLiveScreenActive) AiEmerald else AiTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Scan Layar Langsung (Live Screen)",
                                color = AiTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.isLiveScreenActive) {
                                    "1 ketuk untuk potret layar & jawab soal otomatis"
                                } else {
                                    "Ketuk untuk aktifkan izin tangkap layar"
                                },
                                color = AiTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.isLiveScreenActive) AiEmerald else AiCardDark
                    ) {
                        Text(
                            text = if (state.isLiveScreenActive) "SCAN" else "AKTIFKAN",
                            color = if (state.isLiveScreenActive) Color.White else AiEmerald,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Mode Samaran Ringkas (Normal 100% / 16% / 0%)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AiPanelElevated,
            border = BorderStroke(
                1.dp,
                if (state.stealthMode != StealthMode.NORMAL) AiAmber.copy(alpha = 0.7f) else AiCardBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Mode Samaran",
                        tint = if (state.stealthMode != StealthMode.NORMAL) AiAmber else AiTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Samaran",
                        color = AiTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StealthMode.entries.forEach { mode ->
                        val selected = state.stealthMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) AiAmber.copy(alpha = 0.22f) else AiCardDark,
                            border = BorderStroke(1.dp, if (selected) AiAmber else AiCardBorder),
                            modifier = Modifier
                                .clickable { onSelectStealthMode(mode) }
                                .testTag("stealth_level_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.badge,
                                color = if (selected) AiAmber else AiTextSecondary,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Anti-Rekam / Screenshot (FLAG_SECURE) + Jawaban Cepat row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AiPanelElevated,
                border = BorderStroke(
                    1.dp,
                    if (state.isQuickAnswerMode) AiEmerald else AiCardBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleQuickAnswerMode)
                    .testTag("quick_answer_mode_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Jawaban Cepat",
                            tint = if (state.isQuickAnswerMode) AiEmerald else AiTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Cepat",
                            color = AiTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = if (state.isQuickAnswerMode) "ON" else "OFF",
                        color = if (state.isQuickAnswerMode) AiEmerald else AiTextSecondary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AiPanelElevated,
                border = BorderStroke(
                    1.dp,
                    if (state.isAntiCaptureEnabled) AiEmerald else AiCardBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleAntiCapture)
                    .testTag("anti_capture_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Anti Rekam",
                            tint = if (state.isAntiCaptureEnabled) AiEmerald else AiTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Anti-Rekam",
                            color = AiTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = if (state.isAntiCaptureEnabled) "ON" else "OFF",
                        color = if (state.isAntiCaptureEnabled) AiEmerald else AiTextSecondary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Prompt Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AiEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    AiKnotOrbIcon(size = 14.dp, tint = Color.White)
                }
                Text(
                    text = "Mau kerjain tugas apa?",
                    color = AiTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${state.solvedTasks.size} Selesai",
                color = AiEmerald,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Tool Cards Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiToolActionCard(
                    title = TaskToolType.SCAN_PHOTO.title,
                    subtitle = TaskToolType.SCAN_PHOTO.subtitle,
                    badgeText = TaskToolType.SCAN_PHOTO.badge,
                    icon = Icons.Default.DocumentScanner,
                    accentColor = AiEmerald,
                    isActive = false,
                    onClick = { onOpenTool(TaskToolType.SCAN_PHOTO) },
                    tag = "tool_scan_photo",
                    modifier = Modifier.weight(1f)
                )
                AiToolActionCard(
                    title = TaskToolType.MULTIPLE_CHOICE.title,
                    subtitle = TaskToolType.MULTIPLE_CHOICE.subtitle,
                    badgeText = TaskToolType.MULTIPLE_CHOICE.badge,
                    icon = Icons.Default.Checklist,
                    accentColor = AiBlue,
                    isActive = false,
                    onClick = { onOpenTool(TaskToolType.MULTIPLE_CHOICE) },
                    tag = "tool_multiple_choice",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiToolActionCard(
                    title = TaskToolType.ESSAY_WRITER.title,
                    subtitle = TaskToolType.ESSAY_WRITER.subtitle,
                    badgeText = TaskToolType.ESSAY_WRITER.badge,
                    icon = Icons.Default.AutoAwesome,
                    accentColor = AiAmber,
                    isActive = false,
                    onClick = { onOpenTool(TaskToolType.ESSAY_WRITER) },
                    tag = "tool_essay",
                    modifier = Modifier.weight(1f)
                )
                AiToolActionCard(
                    title = TaskToolType.SUMMARIZE.title,
                    subtitle = TaskToolType.SUMMARIZE.subtitle,
                    badgeText = TaskToolType.SUMMARIZE.badge,
                    icon = Icons.Default.Summarize,
                    accentColor = AiPurple,
                    isActive = false,
                    onClick = { onOpenTool(TaskToolType.SUMMARIZE) },
                    tag = "tool_summarize",
                    modifier = Modifier.weight(1f)
                )
            }

            AiToolActionCard(
                title = TaskToolType.PROOFREAD.title,
                subtitle = TaskToolType.PROOFREAD.subtitle,
                badgeText = TaskToolType.PROOFREAD.badge,
                icon = Icons.AutoMirrored.Filled.FactCheck,
                accentColor = AiEmerald,
                isActive = false,
                onClick = { onOpenTool(TaskToolType.PROOFREAD) },
                tag = "tool_proofread",
                modifier = Modifier.fillMaxWidth()
            )

            if (state.isGenerating) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AiStopRedTint,
                    border = BorderStroke(1.dp, AiStopRed.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onStopGeneration)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Hentikan AI",
                            tint = AiStopRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "Stop Generation",
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveToolSolverPane(
    tool: TaskToolType,
    pendingText: String,
    pendingBitmap: Bitmap?,
    isGenerating: Boolean,
    liveOutput: String,
    errorText: String?,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onPasteClipboard: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveBitmap: () -> Unit,
    onSolve: () -> Unit,
    onStop: () -> Unit,
    onClearError: () -> Unit,
    onCopyOutput: () -> Unit,
    onInputFocusChange: ((Boolean) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali ke Tools",
                        tint = AiTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = tool.title,
                    color = AiTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AiEmeraldTint,
                modifier = Modifier.clickable(onClick = onPasteClipboard)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Tempel",
                        tint = AiEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Tempel",
                        color = AiEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AiPanelElevated,
                border = BorderStroke(1.dp, AiCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPickGallery)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Galeri",
                        tint = AiEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Pilih Foto", color = AiTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AiPanelElevated,
                border = BorderStroke(1.dp, AiCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onTakePhoto)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Kamera",
                        tint = AiBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Kamera", color = AiTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (pendingBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AiPanelElevated)
            ) {
                Image(
                    bitmap = pendingBitmap.asImageBitmap(),
                    contentDescription = "Foto tugas",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onRemoveBitmap,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hapus gambar",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = pendingText,
            onValueChange = onTextChange,
            minLines = 3,
            maxLines = 5,
            placeholder = {
                Text(tool.placeholder, color = AiTextSecondary, fontSize = 12.sp)
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AiEmerald,
                unfocusedBorderColor = AiCardBorder,
                focusedTextColor = AiTextPrimary,
                unfocusedTextColor = AiTextPrimary,
                focusedContainerColor = AiPanelElevated,
                unfocusedContainerColor = AiPanelElevated
            ),
            modifier = Modifier
                .fillMaxWidth()
                .overlayTextInputFocus(onInputFocusChange)
                .testTag("tool_solver_input")
        )

        if (!errorText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AiStopRedTint,
                border = BorderStroke(1.dp, AiStopRed.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = "Error",
                        tint = AiStopRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = errorText,
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup error",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier
                            .size(15.dp)
                            .clickable(onClick = onClearError)
                    )
                }
            }
        }

        Button(
            onClick = if (isGenerating) onStop else onSolve,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGenerating) AiStopRed else AiEmerald
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("tool_solver_submit_button")
        ) {
            Text(
                text = if (isGenerating) "Hentikan Proses" else "Kerjakan Sekarang",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        if (liveOutput.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AiPanelElevated,
                border = BorderStroke(1.dp, AiEmerald.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hasil Jawaban AI",
                            color = AiEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AiEmeraldTint,
                            modifier = Modifier.clickable(onClick = onCopyOutput)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Salin hasil",
                                    tint = AiEmerald,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Salin",
                                    color = AiEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    ChatGptMarkdownText(
                        markdown = liveOutput,
                        textColor = AiTextPrimary,
                        accentColor = AiEmerald,
                        secondaryColor = AiTextSecondary,
                        codeBackgroundColor = AiCardDark,
                        inlineCodeBackgroundColor = AiEmeraldTint,
                        calloutBackgroundColor = AiEmeraldTint,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AiToolActionCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) accentColor.copy(alpha = 0.14f) else AiPanelElevated,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) accentColor else AiCardBorder
        ),
        modifier = modifier
            .heightIn(min = 78.dp)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = accentColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = badgeText,
                    color = if (isActive) accentColor else AiTextSecondary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = AiTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = AiTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AiChatTabContent(
    state: TaskAssistantUiState,
    onQuickPrompt: (String) -> Unit,
    onScanScreen: (() -> Unit)?,
    onCopyAnswer: (String) -> Unit,
    onRetryLastPrompt: (() -> Unit)? = null,
    onOpenApiKeyTools: (() -> Unit)? = null
) {
    val chatListState = rememberLazyListState()
    LaunchedEffect(state.chatMessages.size, state.chatMessages.lastOrNull()?.text?.length) {
        if (state.chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(state.chatMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 340.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onScanScreen != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (state.isLiveScreenActive) AiEmeraldTint else AiPanelElevated,
                    border = BorderStroke(
                        1.dp,
                        if (state.isLiveScreenActive) AiEmerald else AiCardBorder
                    ),
                    modifier = Modifier.clickable(onClick = onScanScreen)
                ) {
                    Text(
                        text = if (state.isLiveScreenActive) "Scan Layar" else "Aktifkan Scan",
                        color = if (state.isLiveScreenActive) AiEmerald else AiTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            QuickPromptChip(
                text = "Bantu Matematika",
                onClick = { onQuickPrompt("Bantu selesaikan soal matematika berikut langkah demi langkah.") }
            )
            QuickPromptChip(
                text = "Jawab Pilihan Ganda",
                onClick = { onQuickPrompt("Pilih jawaban A/B/C/D/E yang paling tepat dan beri alasan singkat.") }
            )
            QuickPromptChip(
                text = "Ringkas Materi",
                onClick = { onQuickPrompt("Ringkas materi ini menjadi poin inti yang mudah dipahami.") }
            )
        }

        val lastErrorId = state.chatMessages.lastOrNull { it.isError }?.id

        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.chatMessages, key = { it.id }) { msg ->
                val isAppCheckError = msg.isError && !state.hasCustomApiKey &&
                    (msg.text.contains("App Check", ignoreCase = true) ||
                        msg.text.contains("API Key", ignoreCase = true))
                ChatBubbleItem(
                    message = msg,
                    onCopy = { onCopyAnswer(msg.text) },
                    onRetry = if (msg.id == lastErrorId) onRetryLastPrompt else null,
                    onOpenApiKeyTools = if (msg.id == lastErrorId && isAppCheckError) onOpenApiKeyTools else null
                )
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = AiPanelElevated,
        border = BorderStroke(1.dp, AiCardBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = AiTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ChatBubbleItem(
    message: AiChatMessage,
    onCopy: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onOpenApiKeyTools: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (message.isError) AiStopRed else AiEmerald),
                contentAlignment = Alignment.Center
            ) {
                AiKnotOrbIcon(size = 13.dp, tint = Color.White)
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (message.isUser) 14.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 14.dp
            ),
            color = when {
                message.isError -> AiStopRedTint
                message.isUser -> AiEmerald
                else -> AiPanelElevated
            },
            border = when {
                message.isError -> BorderStroke(1.dp, AiStopRed.copy(alpha = 0.6f))
                !message.isUser -> BorderStroke(1.dp, AiCardBorder)
                else -> null
            },
            modifier = Modifier.widthIn(max = 315.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (message.attachedBitmap != null) {
                    Image(
                        bitmap = message.attachedBitmap.asImageBitmap(),
                        contentDescription = "Lampiran soal",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                if (message.isStreaming && message.text.isEmpty()) {
                    Text(
                        text = "Sedang mengerjakan...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else if (message.isUser || message.isError) {
                    Text(
                        text = message.text,
                        color = if (message.isError) Color(0xFFFCA5A5) else Color.White,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else {
                    ChatGptMarkdownText(
                        markdown = message.text,
                        textColor = AiTextPrimary,
                        accentColor = AiEmerald,
                        secondaryColor = AiTextSecondary,
                        codeBackgroundColor = AiCardDark,
                        inlineCodeBackgroundColor = AiEmeraldTint,
                        calloutBackgroundColor = AiEmeraldTint,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }

                if (!message.isUser && !message.isStreaming && !message.isError && message.text.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onCopy)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin jawaban",
                            tint = AiTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Salin jawaban",
                            color = AiTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                if (message.isError && (onRetry != null || onOpenApiKeyTools != null)) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onRetry != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AiStopRed.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, AiStopRed.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .clickable(onClick = onRetry)
                                    .testTag("chat_retry_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Coba Lagi",
                                        tint = Color(0xFFFEE2E2),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Coba Lagi",
                                        color = Color(0xFFFEE2E2),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (onOpenApiKeyTools != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AiEmeraldTint,
                                border = BorderStroke(1.dp, AiEmerald.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .clickable(onClick = onOpenApiKeyTools)
                                    .testTag("chat_open_api_key_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "Atur API Key",
                                        tint = AiEmerald,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Atur API Key",
                                        color = AiEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.overlayTextInputFocus(
    onInputFocusChange: ((Boolean) -> Unit)?
): Modifier {
    if (onInputFocusChange == null) return this
    val keyboardController = LocalSoftwareKeyboardController.current
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    var fieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(fieldFocused, isWindowFocused) {
        if (fieldFocused && isWindowFocused) {
            keyboardController?.show()
        }
    }

    return this
        .pointerInput(onInputFocusChange) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onInputFocusChange(true)
            }
        }
        .onFocusChanged { focusState ->
            fieldFocused = focusState.isFocused
            onInputFocusChange(focusState.isFocused)
        }
}
