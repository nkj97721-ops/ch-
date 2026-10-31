package com.nkj97721.framedimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
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
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.max

/** Best-effort global detector. MediaProjection frames arrive asynchronously. */
class CaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.nkj97721.framedimmer.STOP"
        private const val CHANNEL_ID = "framedimmer_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_INTERVAL_MS = 120L
        private const val BRIGHT_PIXEL_LUMA = 230
        private const val BRIGHT_RATIO_TRIGGER = 0.06
        private const val CLEAR_FRAMES_TO_RESTORE = 5
        private const val DIM_BRIGHTNESS = 1
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private var lastSampleAt = 0L
    private var clearFrames = 0
    private var dimmed = false
    private var screenOn = true
    private var receiverRegistered = false
    private var savedBrightnessMode: Int? = null
    private var savedBrightnessValue: Int? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            restoreBrightness()
            stopSelf()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    clearFrames = 0
                    restoreBrightness()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    clearFrames = 0
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "全局亮度监控", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FrameDimmer 正在运行")
            .setContentText("正在低分辨率检测全屏高亮画面")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)

        if (!Settings.System.canWrite(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null || projection != null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
            projection = mp
            worker = HandlerThread("FrameDimmerAnalyzer").also { it.start() }
            handler = Handler(worker!!.looper)
            mp.registerCallback(projectionCallback, handler)

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)

            val w = max(1, metrics.widthPixels / 4)
            val h = max(1, metrics.heightPixels / 4)
            val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ source -> analyzeLatestFrame(source) }, handler)

            virtualDisplay = mp.createVirtualDisplay(
                "FrameDimmerCapture", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )
        } catch (_: Exception) {
            restoreBrightness()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun analyzeLatestFrame(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val now = SystemClock.elapsedRealtime()
            if (!screenOn || now - lastSampleAt < SAMPLE_INTERVAL_MS) return
            lastSampleAt = now

            val plane = image.planes[0]
            val bright = countBrightPixels(
                plane.buffer, image.width, image.height, plane.pixelStride, plane.rowStride
            )
            val sampledPixels = ((image.width + 1) / 2) * ((image.height + 1) / 2)
            val ratio = bright.toDouble() / max(1, sampledPixels)

            if (ratio >= BRIGHT_RATIO_TRIGGER) {
                clearFrames = 0
                applyDim()
            } else if (dimmed) {
                clearFrames++
                if (clearFrames >= CLEAR_FRAMES_TO_RESTORE) {
                    restoreBrightness()
                    clearFrames = 0
                }
            }
        } catch (_: Exception) {
            // Frames may become unavailable during projection teardown.
        } finally {
            image?.close()
        }
    }

    private fun countBrightPixels(
        buffer: java.nio.ByteBuffer, width: Int, height: Int, pixelStride: Int, rowStride: Int
    ): Int {
        var count = 0
        var y = 0
        while (y < height) {
            val row = y * rowStride
            var x = 0
            while (x < width) {
                val i = row + x * pixelStride
                if (i + 2 < buffer.limit()) {
                    val r = buffer.get(i).toInt() and 0xff
                    val g = buffer.get(i + 1).toInt() and 0xff
                    val b = buffer.get(i + 2).toInt() and 0xff
                    val luma = (r * 2126 + g * 7152 + b * 722) / 10000
                    if (luma >= BRIGHT_PIXEL_LUMA) count++
                }
                x += 2
            }
            y += 2
        }
        return count
    }

    private fun applyDim() {
        if (dimmed) return
        try {
            if (savedBrightnessMode == null) {
                savedBrightnessMode = Settings.System.getInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                savedBrightnessValue = Settings.System.getInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
                )
                check(BrightnessRecovery.save(this, savedBrightnessMode!!, savedBrightnessValue!!)) {
                    "Unable to persist brightness recovery snapshot"
                }
            }
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, DIM_BRIGHTNESS)
            dimmed = true
        } catch (_: Exception) {
            restoreBrightness()
        }
    }

    private fun restoreBrightness() {
        if (savedBrightnessMode != null && savedBrightnessValue != null) {
            if (BrightnessRecovery.restore(this)) {
                savedBrightnessMode = null
                savedBrightnessValue = null
                dimmed = false
            }
            // On failure keep the persisted snapshot so the next app launch or boot
            // can retry restoration instead of discarding the original brightness.
        } else {
            dimmed = false
        }
    }

    override fun onDestroy() {
        restoreBrightness()
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = null
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
            projection = null
            worker?.quitSafely()
            worker = null
            handler = null
            if (receiverRegistered) {
                unregisterReceiver(screenReceiver)
                receiverRegistered = false
            }
        } catch (_: Exception) {
            // Continue teardown.
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
