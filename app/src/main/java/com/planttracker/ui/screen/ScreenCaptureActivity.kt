package com.planttracker.ui.screen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.planttracker.util.OcrHelper
import com.planttracker.util.OcrResult
import com.planttracker.util.ScreenCaptureHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 透明截图Activity
 * 
 * 设计目标：
 * 1. 完全透明，用户看不到界面切换
 * 2. 点击悬浮窗相机后，先返回农场界面，再自动截图识别
 * 3. 识别完成后直接关闭，不显示任何UI
 * 
 * 流程：
 * 1. 启动Activity（透明）
 * 2. 请求截图权限
 * 3. 用户点击"立即开始"后，Activity立即finish()
 * 4. 用户回到农场界面
 * 5. 延迟后自动截图识别（使用独立协程作用域，避免Activity销毁后取消）
 * 6. 通过广播发送结果
 */
@AndroidEntryPoint
class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var screenCaptureHelper: ScreenCaptureHelper
    private lateinit var ocrHelper: OcrHelper
    
    // 使用独立的协程作用域，确保Activity finish后仍能执行截图
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val TAG = "ScreenCaptureActivity"
        const val ACTION_PLANT_RECOGNIZED = "com.planttracker.PLANT_RECOGNIZED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 关键：不调用setContentView，保持完全透明
        
        screenCaptureHelper = ScreenCaptureHelper(this)
        ocrHelper = OcrHelper()

        // 请求截图权限
        val intent = screenCaptureHelper.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 获取权限成功
                screenCaptureHelper.setMediaProjection(resultCode, data)
                
                // 关键：立即finish，让用户回到农场界面
                // 然后延迟截图，确保用户已经回到农场
                finish()
                overridePendingTransition(0, 0)
                
                // 延迟2秒后截图（给用户时间回到农场界面）
                // 使用独立协程作用域，避免Activity销毁后协程被取消
                Handler(Looper.getMainLooper()).postDelayed({
                    scope.launch {
                        performScreenCapture()
                    }
                }, 2000)
                
            } else {
                // 用户取消
                Log.w(TAG, "用户取消截图权限")
                Toast.makeText(this, "需要截图权限才能识别植物", Toast.LENGTH_SHORT).show()
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    private suspend fun performScreenCapture() {
        try {
            Log.d(TAG, "开始截图...")
            
            // 截图
            val bitmap = screenCaptureHelper.captureScreen()

            if (bitmap != null) {
                Log.d(TAG, "截图成功，开始OCR识别...")
                
                // OCR识别
                val ocrResult = ocrHelper.recognizeText(bitmap)
                
                Log.d(TAG, "OCR结果: ${ocrResult.nickname}, ${ocrResult.matureTimeText}")
                
                // 发送广播通知结果
                sendResultBroadcast(ocrResult)
                
                // 显示一个短暂的Toast提示
                Handler(Looper.getMainLooper()).post {
                    val message = if (ocrResult.nickname != null) {
                        "识别成功: ${ocrResult.nickname}"
                    } else {
                        "未能识别植物信息"
                    }
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "截图失败")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "截图失败", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "截图识别失败", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "识别出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            // 清理资源
            screenCaptureHelper.release()
            ocrHelper.release()
        }
    }

    private fun sendResultBroadcast(ocrResult: OcrResult) {
        val intent = Intent(ACTION_PLANT_RECOGNIZED).apply {
            putExtra("raw_text", ocrResult.rawText)
            putExtra("nickname", ocrResult.nickname)
            putExtra("mature_time_text", ocrResult.matureTimeText)
            putExtra("mature_time_millis", ocrResult.matureTimeMillis ?: 0L)
            // 标记识别是否成功
            putExtra("success", ocrResult.nickname != null && ocrResult.matureTimeMillis != null)
        }
        sendBroadcast(intent)
        Log.d(TAG, "已发送识别结果广播")
    }

    override fun finish() {
        super.finish()
        // 禁用所有动画，实现无感知
        overridePendingTransition(0, 0)
    }
}
