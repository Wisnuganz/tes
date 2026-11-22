package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.firebase.configureAppCheck
import com.example.model.AiPanelTab
import com.example.model.StealthMode
import com.example.model.TapPulseEvent
import com.example.session.AiAssistantSession
import com.example.ui.components.AiKnotOrbIcon
import com.example.ui.overlay.AiPanelCardContent
import com.example.ui.theme.AiBlue
import com.example.ui.theme.AiEmerald
import com.example.ui.theme.AiEmeraldBright
import com.example.ui.theme.CerdasCbtTheme
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "FloatingAiOverlaySvc"
private const val CHANNEL_ID = "cerdas_ai_overlay_channel"
private const val NOTIFICATION_ID = 4102

class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        } catch (_: Exception) {}
        store.clear()
    }
}

class FloatingAiOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.overlay.ACTION_START"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_TOGGLE_PANEL = "com.example.overlay.ACTION_TOGGLE_PANEL"
        const val ACTION_RESET_VISIBLE = "com.example.overlay.ACTION_RESET_VISIBLE"
        const val ACTION_SCAN_SCREEN = "com.example.overlay.ACTION_SCAN_SCREEN"
        const val EXTRA_PICK_PHOTO_FOR_TOOL = "extra_pick_photo_for_tool"
        const val EXTRA_REQUEST_LIVE_SCREEN = "extra_request_live_screen"
        const val EXTRA_OPEN_ACCESSIBILITY_SETTINGS = "extra_open_accessibility_settings"
    }

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var tapPulseView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var tapPulseParams: WindowManager.LayoutParams? = null
    private var lastAppliedExpanded: Boolean? = null
    private var lastAppliedCapturing: Boolean? = null
    private var lastAppliedSecure: Boolean? = null
    private var lastAppliedPulseId: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground overlay service", e)
            Toast.makeText(
                this,
                "Gagal memulai layanan overlay: ${e.localizedMessage ?: "izin latar belakang ditolak"}",
                Toast.LENGTH_LONG
            ).show()
            AiAssistantSession.setOverlayServiceRunning(false)
            stopSelf()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Aktifkan izin Tampil di Atas Aplikasi Lain terlebih dahulu.",
                Toast.LENGTH_SHORT
            ).show()
            AiAssistantSession.setOverlayServiceRunning(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (FirebaseApp.getApps(this).isEmpty()) {
            configureAppCheck(this, null)
        }
        AiAssistantSession.init(this)

        lifecycleOwner.onCreate()

        val windowsAttached = setupOverlayWindows()
        if (!windowsAttached) {
            AiAssistantSession.setOverlayServiceRunning(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        AiAssistantSession.setOverlayServiceRunning(true)

        serviceScope.launch {
            AiAssistantSession.uiState.collectLatest { state ->
                syncWindowVisibilityAndFlags(
                    expanded = state.isSystemOverlayExpanded,
                    capturing = state.isCapturingScreen,
                    secure = state.isAntiCaptureEnabled || state.stealthMode == StealthMode.INVISIBLE
                )
                syncTapPulseIndicator(
                    pulse = if (state.isSilumanMode) null else state.tapPulseEvent,
                    secure = state.isAntiCaptureEnabled || state.stealthMode == StealthMode.INVISIBLE
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            AiAssistantSession.setOverlayServiceRunning(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                AiAssistantSession.setSystemOverlayExpanded(false)
                AiAssistantSession.setOverlayServiceRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PANEL -> {
                val next = !AiAssistantSession.uiState.value.isSystemOverlayExpanded
                AiAssistantSession.setSystemOverlayExpanded(next)
            }
            ACTION_RESET_VISIBLE -> {
                AiAssistantSession.setStealthMode(StealthMode.NORMAL)
                AiAssistantSession.setSystemOverlayExpanded(true)
            }
            ACTION_SCAN_SCREEN -> {
                val inChat = AiAssistantSession.uiState.value.activeAiTab == AiPanelTab.CHAT
                triggerOrRequestLiveScreen(inChatTab = inChat)
            }
        }
        return START_STICKY
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun setupOverlayWindows(): Boolean {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val bubblePx = (62 * density).roundToInt()

        val bParams = WindowManager.LayoutParams(
            bubblePx,
            bubblePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayMetrics.widthPixels - bubblePx - (16 * density).roundToInt()).coerceAtLeast(0)
            y = (displayMetrics.heightPixels * 0.65f).roundToInt()
        }
        bubbleParams = bParams

        val bView = ComposeView(themedContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner)
            )
            setContent {
                CerdasCbtTheme {
                    val state by AiAssistantSession.uiState.collectAsStateWithLifecycle()
                    val animatedAlpha by animateFloatAsState(
                        targetValue = state.stealthMode.bubbleAlpha,
                        animationSpec = spring(),
                        label = "sysBubbleAlpha"
                    )
                    val isInvisible = state.stealthMode == StealthMode.INVISIBLE

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .alpha(animatedAlpha)
                            .then(
                                if (state.stealthMode == StealthMode.NORMAL) {
                                    Modifier.shadow(8.dp, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(AiEmeraldBright, AiEmerald)
                                )
                            )
                            .then(
                                if (!isInvisible) {
                                    Modifier.border(
                                        1.5.dp,
                                        Color.White.copy(alpha = 0.28f),
                                        CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(Unit) {
                                val touchSlopPx = 10f * density
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDragX = 0f
                                    var totalDragY = 0f
                                    var dragged = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: event.changes.firstOrNull()
                                            ?: break
                                        if (!change.pressed) {
                                            if (!dragged) {
                                                AiAssistantSession.setSystemOverlayExpanded(true)
                                            }
                                            break
                                        }
                                        val delta = change.positionChange()
                                        totalDragX += abs(delta.x)
                                        totalDragY += abs(delta.y)
                                        if (!dragged && (totalDragX > touchSlopPx || totalDragY > touchSlopPx)) {
                                            dragged = true
                                        }
                                        if (dragged && (delta.x != 0f || delta.y != 0f)) {
                                            change.consume()
                                            val lp = bubbleParams ?: continue
                                            val maxX = (displayMetrics.widthPixels - bubblePx).coerceAtLeast(0)
                                            val maxY = (displayMetrics.heightPixels - bubblePx).coerceAtLeast(0)
                                            lp.x = (lp.x + delta.x.roundToInt()).coerceIn(0, maxX)
                                            lp.y = (lp.y + delta.y.roundToInt()).coerceIn(0, maxY)
                                            bubbleView?.let { v ->
                                                if (v.isAttachedToWindow) {
                                                    try {
                                                        windowManager.updateViewLayout(v, lp)
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AiKnotOrbIcon(size = 28.dp, tint = Color.White)
                    }
                }
            }
        }
        bubbleView = bView

        val panelWidth = (displayMetrics.widthPixels - (24 * density).roundToInt())
            .coerceAtMost((440 * density).roundToInt())
            .coerceAtLeast((280 * density).roundToInt())

        val pParams = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (44 * density).roundToInt()
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        panelParams = pParams

        val pView = ComposeView(themedContext).apply {
            visibility = View.GONE
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner)
            )
            setContent {
                CerdasCbtTheme {
                    val state by AiAssistantSession.uiState.collectAsStateWithLifecycle()
                    val clipboardManager = LocalClipboardManager.current

                    Box(
                        modifier = Modifier
                            .widthIn(max = 440.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        AiPanelCardContent(
                            state = state,
                            isSystemOverlayContext = true,
                            onClose = { AiAssistantSession.setSystemOverlayExpanded(false) },
                            onSelectTab = AiAssistantSession::selectAiTab,
                            onToggleQuickAnswerMode = AiAssistantSession::toggleQuickAnswerMode,
                            onSelectStealthMode = AiAssistantSession::setStealthMode,
                            onToggleAntiCapture = AiAssistantSession::toggleAntiCapture,
                            onOpenToolInput = { tool ->
                                AiAssistantSession.openToolInput(tool, state.scratchpadText, null)
                            },
                            onUpdatePendingToolText = AiAssistantSession::updatePendingToolText,
                            onUpdatePendingToolBitmap = AiAssistantSession::updatePendingToolBitmap,
                            onPickPhotoFromGallery = { forTool ->
                                launchMainActivityForPhotoPicker(forTool)
                            },
                            onTakePhotoFromCamera = {
                                launchMainActivityForPhotoPicker(true)
                            },
                            onRunActiveTool = AiAssistantSession::runActiveTool,
                            onCloseToolInput = AiAssistantSession::closeToolInput,
                            onStopGeneration = AiAssistantSession::stopGeneration,
                            onClearError = AiAssistantSession::clearToolError,
                            onNewChat = AiAssistantSession::startNewChat,
                            onSendChat = AiAssistantSession::sendChatMessage,
                            onCopyText = { text ->
                                clipboardManager.setText(AnnotatedString(text))
                                AiAssistantSession.showToast("Disalin ke clipboard")
                            },
                            onScanScreen = {
                                triggerOrRequestLiveScreen(
                                    inChatTab = state.activeAiTab == AiPanelTab.CHAT
                                )
                            },
                            onStopSystemOverlay = {
                                AiAssistantSession.setSystemOverlayExpanded(false)
                                AiAssistantSession.setOverlayServiceRunning(false)
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            },
                            onRetryLastPrompt = AiAssistantSession::retryLastUserPrompt,
                            onSaveApiKey = { key ->
                                AiAssistantSession.saveAndTestApiKey(this@FloatingAiOverlayService, key)
                            },
                            onClearApiKey = {
                                AiAssistantSession.clearApiKey(this@FloatingAiOverlayService)
                            },
                            onToggleSilumanMode = AiAssistantSession::toggleSilumanMode,
                            onCycleAutoPace = AiAssistantSession::cycleAutoPace,
                            onSolveSingleQuestion = {
                                AiAssistantSession.solveSingleScreenQuestion(fromSystemOverlay = true)
                            },
                            onToggleAutoSolve = { targetKkm ->
                                AiAssistantSession.toggleAutoSolveLoop(
                                    targetKkm = targetKkm,
                                    fromSystemOverlay = true
                                )
                            },
                            onOpenAccessibilitySettings = {
                                launchMainActivityForAccessibilitySettings()
                            }
                        )
                    }
                }
            }
        }
        panelView = pView

        val pulsePx = (64 * density).roundToInt()
        val pulseLp = WindowManager.LayoutParams(
            pulsePx,
            pulsePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        tapPulseParams = pulseLp

        val pulseView = ComposeView(themedContext).apply {
            visibility = View.GONE
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner)
            )
            setContent {
                CerdasCbtTheme {
                    val state by AiAssistantSession.uiState.collectAsStateWithLifecycle()
                    val isNext = state.tapPulseEvent?.isNextButton == true
                    val ringColor = if (isNext) AiBlue else AiEmerald
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = 0.24f))
                            .border(3.dp, ringColor, CircleShape)
                    )
                }
            }
        }
        tapPulseView = pulseView

        return try {
            windowManager.addView(bView, bParams)
            windowManager.addView(pView, pParams)
            windowManager.addView(pulseView, pulseLp)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding overlay views to WindowManager", e)
            Toast.makeText(
                this,
                "Gagal menampilkan jendela mengambang. Cek izin Pop-up / Jendela Mengambang di Setelan HP.",
                Toast.LENGTH_LONG
            ).show()
            try {
                if (bView.isAttachedToWindow) windowManager.removeViewImmediate(bView)
            } catch (_: Exception) {}
            try {
                if (pView.isAttachedToWindow) windowManager.removeViewImmediate(pView)
            } catch (_: Exception) {}
            try {
                if (pulseView.isAttachedToWindow) windowManager.removeViewImmediate(pulseView)
            } catch (_: Exception) {}
            false
        }
    }

    private fun triggerOrRequestLiveScreen(inChatTab: Boolean) {
        if (ScreenProjectionService.isActive()) {
            AiAssistantSession.captureLiveScreenAndSolve(
                inChatTab = inChatTab,
                fromSystemOverlay = true
            )
        } else {
            AiAssistantSession.setSystemOverlayExpanded(false)
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_REQUEST_LIVE_SCREEN, true)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting MainActivity for Live Screen permission", e)
                Toast.makeText(
                    this,
                    "Buka Cerdas AI lalu aktifkan Live Screen (atau izinkan pop-up latar belakang di Setelan).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun launchMainActivityForPhotoPicker(forTool: Boolean) {
        AiAssistantSession.setSystemOverlayExpanded(false)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_PICK_PHOTO_FOR_TOOL, forTool)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting MainActivity for Photo Picker", e)
            Toast.makeText(
                this,
                "Buka Cerdas AI untuk memilih foto soal (atau izinkan pop-up latar belakang).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun launchMainActivityForAccessibilitySettings() {
        AiAssistantSession.setSystemOverlayExpanded(false)
        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(settingsIntent)
            return
        } catch (_: Exception) {}

        val fallbackIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN_ACCESSIBILITY_SETTINGS, true)
        }
        try {
            startActivity(fallbackIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening Accessibility Settings", e)
            Toast.makeText(
                this,
                "Buka Setelan > Aksesibilitas > Cerdas AI Auto-Jawab untuk mengaktifkan Auto-Tap.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun syncTapPulseIndicator(pulse: TapPulseEvent?, secure: Boolean) {
        val view = tapPulseView ?: return
        val lp = tapPulseParams ?: return
        if (pulse == null) {
            if (lastAppliedPulseId != null) {
                lastAppliedPulseId = null
                view.visibility = View.GONE
            }
            return
        }
        if (lastAppliedPulseId == pulse.id) return
        lastAppliedPulseId = pulse.id

        val pulsePx = (64 * resources.displayMetrics.density).roundToInt()
        lp.x = (pulse.x - pulsePx / 2f).roundToInt()
        lp.y = (pulse.y - pulsePx / 2f).roundToInt()
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        lp.flags = if (secure) baseFlags or WindowManager.LayoutParams.FLAG_SECURE else baseFlags
        view.visibility = View.VISIBLE
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, lp)
            } catch (_: Exception) {}
        }
    }

    private fun syncWindowVisibilityAndFlags(
        expanded: Boolean,
        capturing: Boolean,
        secure: Boolean
    ) {
        if (lastAppliedExpanded == expanded &&
            lastAppliedCapturing == capturing &&
            lastAppliedSecure == secure
        ) {
            return
        }
        lastAppliedExpanded = expanded
        lastAppliedCapturing = capturing
        lastAppliedSecure = secure

        val bView = bubbleView ?: return
        val pView = panelView ?: return
        val bParams = bubbleParams ?: return
        val pParams = panelParams ?: return

        val showBubble = !capturing && !expanded
        val showPanel = !capturing && expanded

        val baseBubbleFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            if (!showBubble) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        bParams.flags = if (secure) {
            baseBubbleFlags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            baseBubbleFlags
        }

        val basePanelFlags = if (showPanel) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        pParams.flags = if (secure) {
            basePanelFlags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            basePanelFlags
        }

        bView.visibility = if (showBubble) View.VISIBLE else View.GONE
        pView.visibility = if (showPanel) View.VISIBLE else View.GONE

        if (bView.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(bView, bParams)
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating bubble layout", e)
            }
        }
        if (pView.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(pView, pParams)
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating panel layout", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cerdas AI Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga asisten AI tetap melayang di atas aplikasi lain"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePanelIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, FloatingAiOverlayService::class.java).apply { action = ACTION_TOGGLE_PANEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val scanScreenIntent = PendingIntent.getService(
            this,
            14,
            Intent(this, FloatingAiOverlayService::class.java).apply { action = ACTION_SCAN_SCREEN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            13,
            Intent(this, FloatingAiOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cerdas_ai)
            .setContentTitle("Cerdas AI Mengambang Aktif")
            .setContentText("Ketuk Scan Layar atau Panel AI di atas aplikasi lain")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, "Scan Layar", scanScreenIntent)
            .addAction(0, "Panel AI", togglePanelIntent)
            .addAction(0, "Matikan", stopIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        bubbleView?.let {
            if (it.isAttachedToWindow) {
                try { windowManager.removeViewImmediate(it) } catch (_: Exception) {}
            }
        }
        panelView?.let {
            if (it.isAttachedToWindow) {
                try { windowManager.removeViewImmediate(it) } catch (_: Exception) {}
            }
        }
        tapPulseView?.let {
            if (it.isAttachedToWindow) {
                try { windowManager.removeViewImmediate(it) } catch (_: Exception) {}
            }
        }
        lifecycleOwner.onDestroy()
        AiAssistantSession.setOverlayServiceRunning(false)
    }
}
