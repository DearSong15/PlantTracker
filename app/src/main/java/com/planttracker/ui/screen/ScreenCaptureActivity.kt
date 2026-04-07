package com.planttracker.ui.screen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.planttracker.util.OcrHelper
import com.planttracker.util.OcrResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 透明截图Activity
 * 
 * 修复后的设计：
 * 1. 在Activity存活期间完成截图，不依赖finish后的延迟操作
 * 2. 截图和OCR在后台快速完成
 * 3. 完成后立即finish，通过广播发送结果
 */
@AndroidEntryPoint
class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var ocrHelper: OcrHelper
    private var mediaProjectionResultCode: Int = 0
    private var mediaProjectionData: Intent? = null
    
    // 使用独立的协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val TAG = "ScreenCaptureActivity"
        const val ACTION_PLANT_RECOGNIZED = "com.planttracker.PLANT_RECOGNIZED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 关键：不调用setContentView，保持完全透明
        
        ocrHelper = OcrHelper()

        // 请求截图权限
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 保存结果，立即开始截图流程
                mediaProjectionResultCode = resultCode
                mediaProjectionData = data
                
                // 立即开始截图，不等待finish
                scope.launch {
                    performCaptureAndFinish()
                }
                
            } else {
                // 用户取消
                Log.w(TAG, "用户取消截图权限")
                Toast.makeText(this, "需要截图权限才能识别植物", Toast.LENGTH_SHORT).show()
                finishWithNoAnimation()
            }
        }
    }

    private suspend fun performCaptureAndFinish() {
        try {
            Log.d(TAG, "开始截图流程...")
            
            // 使用应用上下文进行截图
            val bitmap = withContext(Dispatchers.IO) {
                captureScreenWithMediaProjection()
            }

            if (bitmap != null) {
                Log.d(TAG, "截图成功，开始OCR识别...")
                
                // OCR识别
                val ocrResult = withContext(Dispatchers.Default) {
                    ocrHelper.recognizeText(bitmap)
                }
                
                Log.d(TAG, "OCR结果: ${ocrResult.nickname}, ${ocrResult.matureTimeText}")
                
                // 发送广播通知结果
                sendResultBroadcast(ocrResult)
                
                // 显示Toast
                val message = if (ocrResult.nickname != null) {
                    "识别成功: ${ocrResult.nickname}"
                } else {
                    "未能识别植物信息"
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                
            } else {
                Log.e(TAG, "截图失败")
                Toast.makeText(applicationContext, "截图失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "截图识别失败", e)
            Toast.makeText(applicationContext, "识别出错: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            // 清理资源
            ocrHelper.release()
            // 关闭Activity
            finishWithNoAnimation()
        }
    }

    private fun captureScreenWithMediaProjection(): Bitmap? {
        val data = mediaProjectionData ?: return null
        
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = mediaProjectionManager.getMediaProjection(mediaProjectionResultCode, data)
            ?: return null

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val imageReader = android.media.ImageReader.newInstance(
            width, height, 
            android.graphics.PixelFormat.RGBA_8888, 
            1
        )

        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        // 等待图像准备好
        Thread.sleep(500)

        val bitmap = try {
            imageReader.acquireLatestImage()?.use { image ->
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val bmp = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buffer)
                bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "转换图片失败", e)
            null
        }

        // 清理资源
        virtualDisplay.release()
        imageReader.close()
        mediaProjection.stop()

        return bitmap
    }

    private fun sendResultBroadcast(ocrResult: OcrResult) {
        val intent = Intent(ACTION_PLANT_RECOGNIZED).apply {
            putExtra("raw_text", ocrResult.rawText)
            putExtra("nickname", ocrResult.nickname)
            putExtra("mature_time_text", ocrResult.matureTimeText)
            putExtra("mature_time_millis", ocrResult.matureTimeMillis ?: 0L)
            putExtra("success", ocrResult.nickname != null && ocrResult.matureTimeMillis != null)
        }
        sendBroadcast(intent)
        Log.d(TAG, "已发送识别结果广播")
    }

    private fun finishWithNoAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
