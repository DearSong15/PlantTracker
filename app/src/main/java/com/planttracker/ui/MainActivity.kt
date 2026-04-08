package com.planttracker.ui

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.planttracker.alarm.AlarmReceiver
import com.planttracker.data.model.Plant
import com.planttracker.service.PlantNotificationService
import com.planttracker.ui.screen.PlantListScreen
import com.planttracker.ui.screen.ScreenCaptureActivity
import com.planttracker.ui.theme.PlantTrackerTheme
import com.planttracker.ui.viewmodel.PlantViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.activity.viewModels

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: PlantViewModel by viewModels()

    private var needsNotificationPermission by mutableStateOf(false)
    private var needsOverlayPermission by mutableStateOf(false)

    // 精确闹钟权限
    private val alarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 回调后刷新状态（在 onResume 中处理）
    }

    // 截图识别结果回调
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val nickname = result.data?.getStringExtra("nickname") ?: "未知植物"
            val matureTime = result.data?.getLongExtra("matureTime", 0L) ?: 0L
            
            if (matureTime > 0) {
                addPlantFromCapture(nickname, matureTime)
            }
        }
    }
    
    // 接收来自截图识别的广播
    private val plantRecognitionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // 新的识别结果广播
                ScreenCaptureActivity.ACTION_PLANT_RECOGNIZED -> {
                    val success = intent.getBooleanExtra("success", false)
                    val nickname = intent.getStringExtra("nickname")
                    val matureTimeMillis = intent.getLongExtra("mature_time_millis", 0L)
                    
                    if (success && nickname != null && matureTimeMillis > 0) {
                        addPlantFromCapture(nickname, matureTimeMillis)
                    }
                }
                // 兼容旧的广播
                "com.planttracker.ADD_PLANT" -> {
                    val nickname = intent.getStringExtra("nickname") ?: "未知植物"
                    val matureTime = intent.getLongExtra("matureTime", 0L)
                    if (matureTime > 0) {
                        addPlantFromCapture(nickname, matureTime)
                    }
                }
            }
        }
    }
    
    private fun addPlantFromCapture(nickname: String, matureTime: Long) {
        lifecycleScope.launch {
            viewModel.addPlant(
                name = nickname,
                emoji = "🌱",
                matureAt = System.currentTimeMillis() + matureTime,
                note = "通过截图识别添加"
            )
            Toast.makeText(this@MainActivity, "已添加: $nickname", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查权限状态
        needsNotificationPermission = !NotificationManagerCompat.from(this).areNotificationsEnabled()
        needsOverlayPermission = !Settings.canDrawOverlays(this)

        // 处理从悬浮窗启动的意图
        handleIntent(intent)

        // 启动常驻通知服务
        PlantNotificationService.start(this)
        
        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction(ScreenCaptureActivity.ACTION_PLANT_RECOGNIZED)
            addAction("com.planttracker.ADD_PLANT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(plantRecognitionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(plantRecognitionReceiver, filter)
        }

        setContent {
            PlantTrackerTheme {
                MainContent(
                    needsNotificationPermission = needsNotificationPermission,
                    needsOverlayPermission = needsOverlayPermission,
                    onGrantNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                        }
                    },
                    onGrantOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    },
                    onGrantAlarm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            alarmPermissionLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.getStringExtra("action")) {
            "capture_screen" -> {
                // 启动截图识别
                val captureIntent = Intent(this, ScreenCaptureActivity::class.java)
                screenCaptureLauncher.launch(captureIntent)
            }
            "add_plant" -> {
                // 添加植物的操作在 PlantListScreen 中处理
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台刷新权限状态
        needsNotificationPermission = !NotificationManagerCompat.from(this).areNotificationsEnabled()
        needsOverlayPermission = !Settings.canDrawOverlays(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(plantRecognitionReceiver)
    }
}

@Composable
fun MainContent(
    needsNotificationPermission: Boolean,
    needsOverlayPermission: Boolean,
    onGrantNotification: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantAlarm: () -> Unit
) {
    val context = LocalContext.current
    val needsExactAlarm = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                ?.canScheduleExactAlarms() == false
        } else false
    }

    // 创建通知渠道
    LaunchedEffect(Unit) {
        createNotificationChannel(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 权限提示横幅
        if (needsNotificationPermission) {
            PermissionBanner(
                icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                text = "需要通知权限才能在植物成熟时提醒你",
                buttonText = "授权",
                onGrant = onGrantNotification
            )
        }
        if (needsOverlayPermission) {
            PermissionBanner(
                icon = { Text("🪟", fontSize = MaterialTheme.typography.bodyLarge.fontSize) },
                text = "需要悬浮窗权限才能显示常驻悬浮球",
                buttonText = "授权",
                onGrant = onGrantOverlay
            )
        }
        if (needsExactAlarm) {
            PermissionBanner(
                icon = { Text("⏰", fontSize = MaterialTheme.typography.bodyLarge.fontSize) },
                text = "需要精确闹钟权限才能准时提醒",
                buttonText = "授权",
                onGrant = onGrantAlarm
            )
        }

        // 主界面
        PlantListScreen()
    }
}

@Composable
fun PermissionBanner(
    icon: @Composable () -> Unit,
    text: String,
    buttonText: String,
    onGrant: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onGrant) {
                Text(buttonText, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 创建 Android 8.0+ 通知渠道
 * 使用四个渠道分别对应：有声+震动 / 仅声音 / 仅震动 / 静默
 * 这样用户可以通过设置灵活控制提醒方式
 */
private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

        // 删除旧渠道（避免旧渠道的固定配置干扰）
        manager.deleteNotificationChannel(AlarmReceiver.CHANNEL_ID_LEGACY)

        val channels = listOf(
            android.app.NotificationChannel(
                AlarmReceiver.CHANNEL_SOUND_VIBRATE,
                "植物成熟提醒（响铃+震动）",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当植物成熟时发出声音和震动提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            },
            android.app.NotificationChannel(
                AlarmReceiver.CHANNEL_SOUND_ONLY,
                "植物成熟提醒（仅响铃）",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当植物成熟时仅发出声音提醒"
                enableVibration(false)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            },
            android.app.NotificationChannel(
                AlarmReceiver.CHANNEL_VIBRATE_ONLY,
                "植物成熟提醒（仅震动）",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当植物成熟时仅震动提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(null, null)
            },
            android.app.NotificationChannel(
                AlarmReceiver.CHANNEL_SILENT,
                "植物成熟提醒（静默）",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "当植物成熟时静默提醒（仅通知栏）"
                enableVibration(false)
                setSound(null, null)
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }
}
