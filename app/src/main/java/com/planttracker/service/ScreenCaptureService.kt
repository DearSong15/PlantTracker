package com.planttracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.planttracker.R
import com.planttracker.ui.MainActivity
import com.planttracker.ui.screen.ScreenCaptureActivity
import com.planttracker.util.OcrHelper
import com.planttracker.util.OcrResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 前台服务截图
 * Android 10+ 要求 MediaProjection 必须通过前台服务使用
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var resultCode: Int = 0
    private var resultData: Intent? = null
    private lateinit var ocrHelper: OcrHelper
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val TAG = "ScreenCaptureService"
        const val ACTION_START_CAPTURE = "START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
        const val NOTIFICATION_CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ocrHelper = OcrHelper()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA)
                
                startForeground(NOTIFICATION_ID, createNotification())
                
                // 延迟执行截图，确保服务已启动
                Handler(Looper.getMainLooper()).postDelayed({
                    performCapture()
                }, 500)
            }
            ACTION_STOP_CAPTURE -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun performCapture() {
        val data = resultData ?: run {
            Log.e(TAG, "No result data")
            stopSelf()
            return
        }

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            ?: run {
                Log.e(TAG, "Failed to get MediaProjection")
                stopSelf()
                return
            }

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888,
            2
        )

        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        // 延迟等待图像
        Handler(Looper.getMainLooper()).postDelayed({
            val bitmap = imageReader.acquireLatestImage()?.use { image ->
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                ).apply {
                    copyPixelsFromBuffer(buffer)
                }
            }

            // 清理资源
            virtualDisplay?.release()
            imageReader.close()
            mediaProjection?.stop()

            // 处理识别
            if (bitmap != null) {
                scope.launch(Dispatchers.Default) {
                    val ocrResult = ocrHelper.recognizeText(bitmap)
                    ocrHelper.release()
                    
                    sendResultBroadcast(ocrResult)
                    
                    // 停止服务
                    stopSelf()
                }
            } else {
                Log.e(TAG, "Failed to capture bitmap")
                ocrHelper.release()
                stopSelf()
            }
        }, 500)
    }

    private fun sendResultBroadcast(ocrResult: OcrResult) {
        val intent = Intent(ScreenCaptureActivity.ACTION_PLANT_RECOGNIZED).apply {
            putExtra("raw_text", ocrResult.rawText)
            putExtra("nickname", ocrResult.nickname)
            putExtra("mature_time_text", ocrResult.matureTimeText)
            putExtra("mature_time_millis", ocrResult.matureTimeMillis ?: 0L)
            putExtra("success", ocrResult.nickname != null && ocrResult.matureTimeMillis != null)
        }
        sendBroadcast(intent)
        Log.d(TAG, "已发送识别结果广播")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "截图服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于识别植物信息的截图服务"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("正在识别植物")
            .setContentText("正在截图识别植物信息...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
