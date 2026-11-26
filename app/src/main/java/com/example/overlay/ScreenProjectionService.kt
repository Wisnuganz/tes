package com.example.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.session.AiAssistantSession
import java.lang.ref.WeakReference

private const val TAG = "ScreenProjectionService"
private const val CHANNEL_ID = "cerdas_ai_live_screen_channel"
private const val NOTIFICATION_ID = 4103

class ScreenProjectionService : Service() {

    companion object {
        const val ACTION_START = "com.example.overlay.ACTION_START_PROJECTION"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        @Volatile
        private var instanceRef: WeakReference<ScreenProjectionService>? = null

        fun isActive(): Boolean = instanceRef?.get()?.isProjectionReady() == true

        fun captureLatestBitmap(): Bitmap? = instanceRef?.get()?.extractLatestBitmap()

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ScreenProjectionService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(stopIntent)
            } catch (_: Exception) {
                instanceRef?.get()?.stopSelf()
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val imageLock = Any()
    private var lastHeldImage: Image? = null
    private var cachedLastBitmap: Bitmap? = null

    private var captureWidth = 720
    private var captureHeight = 1280
    private var captureDensityDpi = 320

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system or user.")
            releaseProjectionResources()
            AiAssistantSession.setLiveScreenActive(false)
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            super.onCapturedContentResize(width, height)
            if (width > 0 && height > 0) {
                resizeCaptureSurface(width, height)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseProjectionResources()
            AiAssistantSession.setLiveScreenActive(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting foreground media projection service", e)
            AiAssistantSession.setLiveScreenActive(false)
            AiAssistantSession.showToast("Gagal mengaktifkan Live Screen: izin layanan ditolak sistem")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Missing MediaProjection permission result.")
            AiAssistantSession.setLiveScreenActive(false)
            stopSelf()
            return START_NOT_STICKY
        }

        startMediaProjection(resultCode, resultData)
        return START_NOT_STICKY
    }

    private fun isProjectionReady(): Boolean = mediaProjection != null && imageReader != null

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        releaseProjectionResources()

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain MediaProjection", e)
            null
        }

        if (projection == null) {
            AiAssistantSession.setLiveScreenActive(false)
            stopSelf()
            return
        }

        mediaProjection = projection
        projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        val thread = HandlerThread("CerdasScreenCapture").apply { start() }
        captureThread = thread
        captureHandler = Handler(thread.looper)

        val (rawWidth, rawHeight, densityDpi) = resolveScreenMetrics()
        captureDensityDpi = densityDpi
        val (scaledW, scaledH) = scaleBounds(rawWidth, rawHeight, maxDimension = 1440)
        captureWidth = scaledW
        captureHeight = scaledH

        val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 3)
        reader.setOnImageAvailableListener({ r ->
            val img = try {
                r.acquireLatestImage()
            } catch (_: Exception) {
                null
            }
            if (img != null) {
                synchronized(imageLock) {
                    lastHeldImage?.close()
                    lastHeldImage = img
                }
            }
        }, captureHandler)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "CerdasAiLiveScreen",
            captureWidth,
            captureHeight,
            captureDensityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )

        AiAssistantSession.setLiveScreenActive(true)
        AiAssistantSession.showToast("Live Screen aktif! Ketuk Scan Layar kapan saja.")
    }

    private fun resizeCaptureSurface(rawWidth: Int, rawHeight: Int) {
        val projection = mediaProjection ?: return
        val handler = captureHandler ?: return
        val (scaledW, scaledH) = scaleBounds(rawWidth, rawHeight, maxDimension = 1440)
        if (scaledW == captureWidth && scaledH == captureHeight) return

        captureWidth = scaledW
        captureHeight = scaledH

        synchronized(imageLock) {
            lastHeldImage?.close()
            lastHeldImage = null
        }

        val oldReader = imageReader
        val newReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 3)
        newReader.setOnImageAvailableListener({ r ->
            val img = try {
                r.acquireLatestImage()
            } catch (_: Exception) {
                null
            }
            if (img != null) {
                synchronized(imageLock) {
                    lastHeldImage?.close()
                    lastHeldImage = img
                }
            }
        }, handler)
        imageReader = newReader

        try {
            virtualDisplay?.resize(captureWidth, captureHeight, captureDensityDpi)
            virtualDisplay?.surface = newReader.surface
        } catch (e: Exception) {
            Log.w(TAG, "VirtualDisplay resize failed, recreating", e)
            virtualDisplay?.release()
            virtualDisplay = projection.createVirtualDisplay(
                "CerdasAiLiveScreen",
                captureWidth,
                captureHeight,
                captureDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                newReader.surface,
                null,
                handler
            )
        }
        oldReader?.close()
    }

    private fun extractLatestBitmap(): Bitmap? {
        synchronized(imageLock) {
            val freshImg = try {
                imageReader?.acquireLatestImage()
            } catch (_: Exception) {
                null
            }
            if (freshImg != null) {
                lastHeldImage?.close()
                lastHeldImage = freshImg
            }

            val img = lastHeldImage
            if (img != null) {
                val bmp = imageToBitmap(img)
                if (bmp != null) {
                    cachedLastBitmap = bmp
                    return bmp
                }
            }
            return cachedLastBitmap
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            buffer.rewind()
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride
            val tempBitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            tempBitmap.copyPixelsFromBuffer(buffer)
            if (paddedWidth == image.width) {
                tempBitmap
            } else {
                val cropped = Bitmap.createBitmap(tempBitmap, 0, 0, image.width, image.height)
                tempBitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed converting screen Image to Bitmap", e)
            null
        }
    }

    private fun resolveScreenMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val densityDpi = resources.configuration.densityDpi.coerceAtLeast(160)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Triple(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1), densityDpi)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Triple(
                metrics.widthPixels.coerceAtLeast(1),
                metrics.heightPixels.coerceAtLeast(1),
                metrics.densityDpi.coerceAtLeast(160)
            )
        }
    }

    private fun scaleBounds(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxDimension) {
            return (width and 1.inv()).coerceAtLeast(2) to (height and 1.inv()).coerceAtLeast(2)
        }
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val w = (width * ratio).toInt().and(1.inv()).coerceAtLeast(2)
        val h = (height * ratio).toInt().and(1.inv()).coerceAtLeast(2)
        return w to h
    }

    private fun releaseProjectionResources() {
        synchronized(imageLock) {
            lastHeldImage?.close()
            lastHeldImage = null
            cachedLastBitmap = null
        }
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        try {
            captureThread?.quitSafely()
        } catch (_: Exception) {}
        captureThread = null
        captureHandler = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cerdas AI Live Screen",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mengizinkan Scan Layar 1-ketukan dari overlay Cerdas AI"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            20,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            21,
            Intent(this, ScreenProjectionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cerdas_ai)
            .setContentTitle("Live Screen Scan Siap")
            .setContentText("Ketuk Scan Layar di panel mengambang untuk jawab soal di layar")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, "Matikan Scan", stopIntent)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_UI_HIDDEN) {
            synchronized(imageLock) {
                lastHeldImage?.close()
                lastHeldImage = null
                cachedLastBitmap = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProjectionResources()
        AiAssistantSession.setLiveScreenActive(false)
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
    }
}
