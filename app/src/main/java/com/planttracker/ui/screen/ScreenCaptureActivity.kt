package com.planttracker.ui.screen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.planttracker.service.ScreenCaptureService
import dagger.hilt.android.AndroidEntryPoint

/**
 * 截图权限请求Activity
 * 
 * 流程：
 * 1. 启动Activity（透明）
 * 2. 请求截图权限
 * 3. 用户点击"立即开始"后，启动前台服务执行截图
 * 4. 立即finish，让用户回到农场界面
 * 5. 前台服务在后台完成截图和识别
 * 6. 通过广播发送结果
 */
@AndroidEntryPoint
class ScreenCaptureActivity : ComponentActivity() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val TAG = "ScreenCaptureActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不调用setContentView，保持完全透明

        // 请求截图权限
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 启动前台服务执行截图
                ScreenCaptureService.start(this, resultCode, data)
                
                // 立即finish，让用户回到农场界面
                finishWithNoAnimation()
                
            } else {
                // 用户取消
                Log.w(TAG, "用户取消截图权限")
                Toast.makeText(this, "需要截图权限才能识别植物", Toast.LENGTH_SHORT).show()
                finishWithNoAnimation()
            }
        }
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
