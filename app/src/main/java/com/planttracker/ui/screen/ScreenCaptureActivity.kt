package com.planttracker.ui.screen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.planttracker.data.model.Plant
import com.planttracker.ui.theme.PlantTrackerTheme
import com.planttracker.ui.viewmodel.PlantViewModel
import com.planttracker.util.OcrHelper
import com.planttracker.util.OcrResult
import com.planttracker.util.ScreenCaptureHelper
import com.planttracker.util.TimeParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 截图识别 Activity
 * 用于从悬浮窗启动，进行屏幕截图和 OCR 识别
 */
@AndroidEntryPoint
class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var screenCaptureHelper: ScreenCaptureHelper
    private lateinit var ocrHelper: OcrHelper

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            screenCaptureHelper.setMediaProjection(result.resultCode, result.data!!)
            performScreenCapture()
        } else {
            Toast.makeText(this, "需要截图权限才能识别", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        screenCaptureHelper = ScreenCaptureHelper(this)
        ocrHelper = OcrHelper()

        setContent {
            PlantTrackerTheme {
                ScreenCaptureScreen(
                    onRequestCapture = {
                        // 请求截图权限
                        val intent = screenCaptureHelper.createScreenCaptureIntent()
                        screenCaptureLauncher.launch(intent)
                    },
                    onFinish = { finish() }
                )
            }
        }
    }

    private fun performScreenCapture() {
        lifecycleScope.launch {
            try {
                // 延迟一下让用户切换到农场应用
                kotlinx.coroutines.delay(1000)
                
                // 截图
                val bitmap = screenCaptureHelper.captureScreen()
                if (bitmap == null) {
                    Toast.makeText(this@ScreenCaptureActivity, "截图失败", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // OCR 识别
                val ocrResult = ocrHelper.recognizeText(bitmap)
                
                // 显示识别结果对话框
                showRecognitionResult(ocrResult)
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ScreenCaptureActivity, "识别出错: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                screenCaptureHelper.release()
            }
        }
    }

    private fun showRecognitionResult(ocrResult: OcrResult) {
        // 这里会触发重组，显示结果对话框
        // 实际实现需要在 Compose 中处理
        val nickname = ocrResult.nickname ?: "未知植物"
        val matureTime = ocrResult.matureTimeMillis
        
        if (matureTime != null) {
            val intent = Intent().apply {
                putExtra("nickname", nickname)
                putExtra("matureTime", matureTime)
            }
            setResult(Activity.RESULT_OK, intent)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        
        // 重新设置内容显示结果
        setContent {
            PlantTrackerTheme {
                RecognitionResultScreen(
                    ocrResult = ocrResult,
                    onConfirm = { name, timeMillis ->
                        val resultIntent = Intent().apply {
                            putExtra("nickname", name)
                            putExtra("matureTime", timeMillis)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureHelper.release()
        ocrHelper.release()
    }
}

@Composable
fun ScreenCaptureScreen(
    onRequestCapture: () -> Unit,
    onFinish: () -> Unit
) {
    var showRequest by remember { mutableStateOf(true) }

    if (showRequest) {
        AlertDialog(
            onDismissRequest = onFinish,
            title = { Text("📸 截图识别") },
            text = {
                Column {
                    Text("点击开始后，请切换到农场应用界面，系统会自动截图并识别成熟时间。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("提示：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("• 确保农场应用显示植物信息")
                    Text("• 确保能看到\"后成熟\"时间")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRequest = false
                    onRequestCapture()
                }) {
                    Text("开始截图")
                }
            },
            dismissButton = {
                TextButton(onClick = onFinish) {
                    Text("取消")
                }
            }
        )
    } else {
        // 显示加载中
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在识别...")
            }
        }
    }
}

@Composable
fun RecognitionResultScreen(
    ocrResult: OcrResult,
    onConfirm: (name: String, matureTime: Long) -> Unit,
    onCancel: () -> Unit
) {
    var plantName by remember { mutableStateOf(ocrResult.nickname ?: "") }
    var timeText by remember { mutableStateOf(ocrResult.matureTimeText ?: "") }
    
    val matureTimeMillis = ocrResult.matureTimeMillis
    val isValid = plantName.isNotBlank() && matureTimeMillis != null

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("🎯 识别结果") },
        text = {
            Column {
                // 植物名称
                OutlinedTextField(
                    value = plantName,
                    onValueChange = { plantName = it },
                    label = { Text("植物名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 识别到的时间
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("识别到的时间") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    supportingText = {
                        if (matureTimeMillis != null) {
                            Text(
                                "✓ 有效时间",
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "⚠ 无法识别时间，请手动添加",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                
                // 原始识别文本（可折叠）
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "原始识别文本：",
                    fontSize = androidx.compose.ui.unit.sp(12),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    ocrResult.rawText.take(200),
                    fontSize = androidx.compose.ui.unit.sp(10),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    matureTimeMillis?.let { onConfirm(plantName, it) }
                },
                enabled = isValid
            ) {
                Text("添加到列表")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    )
}
