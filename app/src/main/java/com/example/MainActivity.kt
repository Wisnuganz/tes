package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.model.AiPanelTab
import com.example.model.StealthMode
import com.example.model.TaskToolType
import com.example.overlay.FloatingAiOverlayService
import com.example.overlay.ScreenProjectionService
import com.example.session.AiAssistantSession
import com.example.ui.components.AiFloatingToastBanner
import com.example.ui.components.DraggableFloatingAiBubble
import com.example.ui.overlay.AiAssistantOverlay
import com.example.ui.screens.TaskWorkspaceScreen
import com.example.ui.screens.decodeBitmapFromUri
import com.example.ui.theme.CerdasCbtTheme
import com.example.viewmodel.TaskAssistantViewModel

class MainActivity : ComponentActivity() {

    private var pendingPhotoPickerForOverlay = false
    private var pendingLiveScreenPermissionRequest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase ops.
        configureAppCheck(this, intent)

        handleOverlayIntent(intent)

        // STEP 2: Enable edge-to-edge and render Compose UI.
        enableEdgeToEdge()
        setContent {
            CerdasCbtTheme {
                TaskAssistantApp(
                    initialPickPhotoRequest = pendingPhotoPickerForOverlay,
                    initialLiveScreenRequest = pendingLiveScreenPermissionRequest,
                    onConsumePhotoRequest = { pendingPhotoPickerForOverlay = false },
                    onConsumeLiveScreenRequest = { pendingLiveScreenPermissionRequest = false },
                    onApplySecureWindow = { secure ->
                        if (secure) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOverlayIntent(intent)
    }

    private fun handleOverlayIntent(intent: Intent?) {
        if (intent?.hasExtra(FloatingAiOverlayService.EXTRA_PICK_PHOTO_FOR_TOOL) == true) {
            pendingPhotoPickerForOverlay = true
            intent.removeExtra(FloatingAiOverlayService.EXTRA_PICK_PHOTO_FOR_TOOL)
        }
        if (intent?.hasExtra(FloatingAiOverlayService.EXTRA_REQUEST_LIVE_SCREEN) == true) {
            pendingLiveScreenPermissionRequest = true
            intent.removeExtra(FloatingAiOverlayService.EXTRA_REQUEST_LIVE_SCREEN)
        }
    }
}

@Composable
fun TaskAssistantApp(
    initialPickPhotoRequest: Boolean,
    initialLiveScreenRequest: Boolean,
    onConsumePhotoRequest: () -> Unit,
    onConsumeLiveScreenRequest: () -> Unit,
    onApplySecureWindow: (Boolean) -> Unit,
    viewModel: TaskAssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.setOverlayPermission(Settings.canDrawOverlays(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        startFloatingOverlayService(context)
    }

    val overlayPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                viewModel.openToolInput(TaskToolType.SCAN_PHOTO, state.scratchpadText, bmp)
                if (state.isOverlayServiceRunning) {
                    AiAssistantSession.setSystemOverlayExpanded(true)
                }
            }
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenProjectionService::class.java).apply {
                action = ScreenProjectionService.ACTION_START
                putExtra(ScreenProjectionService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenProjectionService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            viewModel.showToast("Izin Live Screen dibatalkan")
        }
    }

    val requestLiveScreenCapture: () -> Unit = {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    LaunchedEffect(initialPickPhotoRequest) {
        if (initialPickPhotoRequest) {
            onConsumePhotoRequest()
            overlayPhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    LaunchedEffect(initialLiveScreenRequest) {
        if (initialLiveScreenRequest) {
            onConsumeLiveScreenRequest()
            requestLiveScreenCapture()
        }
    }

    LaunchedEffect(state.isAntiCaptureEnabled, state.stealthMode) {
        onApplySecureWindow(state.isAntiCaptureEnabled || state.stealthMode == StealthMode.INVISIBLE)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setOverlayPermission(Settings.canDrawOverlays(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TaskWorkspaceScreen(
            state = state,
            onScratchpadChange = viewModel::updateScratchpadText,
            onOpenAiPanel = { viewModel.toggleAiPanel(open = true) },
            onRequestOverlayPermission = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            },
            onToggleSystemOverlay = {
                if (state.isOverlayServiceRunning) {
                    val stopIntent = Intent(context, FloatingAiOverlayService::class.java).apply {
                        action = FloatingAiOverlayService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                } else if (Settings.canDrawOverlays(context)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startFloatingOverlayService(context)
                    }
                }
            },
            onToggleLiveScreen = {
                if (state.isLiveScreenActive && ScreenProjectionService.isActive()) {
                    ScreenProjectionService.stopService(context)
                } else {
                    requestLiveScreenCapture()
                }
            },
            onScanScreenNow = {
                if (ScreenProjectionService.isActive()) {
                    viewModel.captureLiveScreenAndSolve(
                        inChatTab = state.activeAiTab == AiPanelTab.CHAT,
                        fromSystemOverlay = false
                    )
                } else {
                    requestLiveScreenCapture()
                }
            },
            onSelectStealthMode = viewModel::setStealthMode,
            onToggleAntiCapture = viewModel::toggleAntiCapture,
            onOpenToolWithInput = viewModel::openToolInput,
            onSolveDirectChat = { prompt -> viewModel.sendChatMessage(prompt, null) },
            onDeleteTask = viewModel::deleteSolvedTask,
            onCopySuccess = viewModel::showToast
        )

        DraggableFloatingAiBubble(
            stealthMode = state.stealthMode,
            isPanelOpen = state.isAiPanelOpen || state.isCapturingScreen,
            onClick = { viewModel.toggleAiPanel(open = true) }
        )

        AiFloatingToastBanner(message = state.toastMessage)

        AiAssistantOverlay(
            state = state,
            onClose = { viewModel.toggleAiPanel(open = false) },
            onSelectTab = viewModel::selectAiTab,
            onToggleQuickAnswerMode = viewModel::toggleQuickAnswerMode,
            onSelectStealthMode = viewModel::setStealthMode,
            onToggleAntiCapture = viewModel::toggleAntiCapture,
            onOpenToolInput = viewModel::openToolInput,
            onUpdatePendingToolText = viewModel::updatePendingToolText,
            onUpdatePendingToolBitmap = viewModel::updatePendingToolBitmap,
            onRunActiveTool = viewModel::runActiveTool,
            onCloseToolInput = viewModel::closeToolInput,
            onStopGeneration = viewModel::stopGeneration,
            onClearError = viewModel::clearToolError,
            onNewChat = viewModel::startNewChat,
            onSendChat = viewModel::sendChatMessage,
            onCopyToast = viewModel::showToast,
            onScanScreen = {
                if (ScreenProjectionService.isActive()) {
                    viewModel.captureLiveScreenAndSolve(
                        inChatTab = state.activeAiTab == AiPanelTab.CHAT,
                        fromSystemOverlay = false
                    )
                } else {
                    requestLiveScreenCapture()
                }
            }
        )
    }
}

private fun startFloatingOverlayService(context: android.content.Context) {
    val intent = Intent(context, FloatingAiOverlayService::class.java).apply {
        action = FloatingAiOverlayService.ACTION_START
    }
    ContextCompat.startForegroundService(context, intent)
}
