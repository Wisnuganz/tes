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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.firebase.configureAppCheck
import com.example.model.StealthMode
import com.example.model.TaskToolType
import com.example.session.AiAssistantSession
import com.example.ui.components.AiKnotOrbIcon
import com.example.ui.overlay.AiPanelCardContent
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
import kotlin.math.roundToInt

private const val CHANNEL_ID = "cerdas_ai_overlay_channel"
private const val NOTIFICATION_ID = 4102

class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
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
    }

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            AiAssistantSession.setOverlayServiceRunning(false)
            stopSelf()
            return
        }

        if (FirebaseApp.getApps(this).isEmpty()) {
            configureAppCheck(this, null)
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate()
        createNotificationChannel()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        setupOverlayWindows()
        AiAssistantSession.setOverlayServiceRunning(true)

        serviceScope.launch {
            AiAssistantSession.uiState.collectLatest { state ->
                syncWindowVisibilityAndFlags(
                    expanded = state.isSystemOverlayExpanded,
                    capturing = state.isCapturingScreen,
                    secure = state.isAntiCaptureEnabled || state.stealthMode == StealthMode.INVISIBLE
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AiAssistantSession.setSystemOverlayExpanded(false)
                AiAssistantSession.setOverlayServiceRunning(false)
                stopSelf()
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
                val inChat = AiAssistantSession.uiState.value.activeAiTab == com.example.model.AiPanelTab.CHAT
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

    private fun setupOverlayWindows() {
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

        val bView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
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
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val lp = bubbleParams ?: return@detectDragGestures
                                    val maxX = (displayMetrics.widthPixels - bubblePx).coerceAtLeast(0)
                                    val maxY = (displayMetrics.heightPixels - bubblePx).coerceAtLeast(0)
                                    lp.x = (lp.x + dragAmount.x.roundToInt()).coerceIn(0, maxX)
                                    lp.y = (lp.y + dragAmount.y.roundToInt()).coerceIn(0, maxY)
                                    bubbleView?.let { v ->
                                        if (v.isAttachedToWindow) {
                                            windowManager.updateViewLayout(v, lp)
                                        }
                                    }
                                }
                            }
                            .clickable {
                                AiAssistantSession.setSystemOverlayExpanded(true)
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (44 * density).roundToInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        panelParams = pParams

        val pView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
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
                                    inChatTab = state.activeAiTab == com.example.model.AiPanelTab.CHAT
                                )
                            },
                            onStopSystemOverlay = {
                                AiAssistantSession.setSystemOverlayExpanded(false)
                                AiAssistantSession.setOverlayServiceRunning(false)
                                stopSelf()
                            }
                        )
                    }
                }
            }
        }
        panelView = pView

        // Attach bubble initially
        windowManager.addView(bView, bParams)
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
            startActivity(intent)
        }
    }

    private fun launchMainActivityForPhotoPicker(forTool: Boolean) {
        AiAssistantSession.setSystemOverlayExpanded(false)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_PICK_PHOTO_FOR_TOOL, forTool)
        }
        startActivity(intent)
    }

    private fun syncWindowVisibilityAndFlags(
        expanded: Boolean,
        capturing: Boolean,
        secure: Boolean
    ) {
        val bView = bubbleView ?: return
        val pView = panelView ?: return
        val bParams = bubbleParams ?: return
        val pParams = panelParams ?: return

        val baseBubbleFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        bParams.flags = if (secure) {
            baseBubbleFlags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            baseBubbleFlags
        }

        val basePanelFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        pParams.flags = if (secure) {
            basePanelFlags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            basePanelFlags
        }

        if (capturing) {
            bView.visibility = View.GONE
            pView.visibility = View.GONE
            return
        }

        bView.visibility = View.VISIBLE
        pView.visibility = View.VISIBLE

        if (expanded) {
            if (bView.isAttachedToWindow) {
                try { windowManager.removeView(bView) } catch (_: Exception) {}
            }
            if (!pView.isAttachedToWindow) {
                windowManager.addView(pView, pParams)
            } else {
                windowManager.updateViewLayout(pView, pParams)
            }
        } else {
            if (pView.isAttachedToWindow) {
                try { windowManager.removeView(pView) } catch (_: Exception) {}
            }
            if (!bView.isAttachedToWindow) {
                windowManager.addView(bView, bParams)
            } else {
                windowManager.updateViewLayout(bView, bParams)
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

        val resetVisibleIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, FloatingAiOverlayService::class.java).apply { action = ACTION_RESET_VISIBLE },
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        panelView?.let {
            if (it.isAttachedToWindow) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        lifecycleOwner.onDestroy()
        AiAssistantSession.setOverlayServiceRunning(false)
    }
}
