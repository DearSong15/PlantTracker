package com.planttracker.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * 屏幕截图助手
 * 使用 MediaProjection API 截取屏幕
 */
class ScreenCaptureHelper(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    /**
     * 获取 MediaProjectionManager
     */
    private fun getMediaProjectionManager(): MediaProjectionManager {
        return context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    /**
     * 创建截图 Intent（需要在 Activity 中启动）
     */
    fun createScreenCaptureIntent(): Intent {
        return getMediaProjectionManager().createScreenCaptureIntent()
    }

    /**
     * 设置 MediaProjection（从 onActivityResult 获取）
     */
    fun setMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection = getMediaProjectionManager().getMediaProjection(resultCode, data)
    }

    /**
     * 截取屏幕
     * @return 截图 Bitmap，失败返回 null
     */
    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        val projection = mediaProjection ?: return@withContext null

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        return@withContext suspendCancellableCoroutine { continuation ->
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            // 延迟一点确保图像准备好
            Handler(Looper.getMainLooper()).postDelayed({
                val bitmap = imageReader?.acquireLatestImage()?.use { image ->
                    imageToBitmap(image)
                }
                continuation.resume(bitmap)
            }, 500)
        }
    }

    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    /**
     * 释放资源
     */
    fun release() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    companion object {
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
    }
}

/**
 * 简单的截图回调接口（用于 Activity）
 */
interface ScreenCaptureCallback {
    fun onScreenCaptured(bitmap: Bitmap)
    fun onScreenCaptureFailed(error: String)
}
