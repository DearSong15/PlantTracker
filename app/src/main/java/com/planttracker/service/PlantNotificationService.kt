package com.planttracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.planttracker.R
import com.planttracker.alarm.AlarmReceiver
import com.planttracker.data.SettingsManager
import com.planttracker.data.repository.PlantRepository
import com.planttracker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 植物通知服务
 * - 常驻通知栏，显示最近快要成熟的5种植物
 * - 每10秒检查一次是否有植物成熟，成熟时立刻响铃/震动并推送通知
 */
@AndroidEntryPoint
class PlantNotificationService : Service() {

    @Inject
    lateinit var plantRepository: PlantRepository

    @Inject
    lateinit var settingsManager: SettingsManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager

    /** 已经发过提醒的成熟时间，避免重复提醒 */
    private val notifiedMatureTimes = mutableSetOf<Long>()

    /** 上次检测时间，用于精确捕获在两次检测之间成熟的植物 */
    private var lastCheckTime = System.currentTimeMillis()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createForegroundChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification(emptyList()))
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                try {
                    val plants = plantRepository.getActivePlants().first()
                    val now = System.currentTimeMillis()

                    // ── 1. 检测刚刚成熟的植物
                    // 凡是成熟时间在 [lastCheckTime, now] 区间内，且未通知过的植物
                    val justMatured = plants.filter { plant ->
                        !plant.isHarvested &&
                        plant.matureAt in lastCheckTime..now &&
                        !notifiedMatureTimes.contains(plant.matureAt)
                    }

                    lastCheckTime = now   // 更新检测时间

                    if (justMatured.isNotEmpty()) {
                        // 按成熟时间分组
                        val grouped = justMatured.groupBy { it.matureAt }
                        grouped.forEach { (matureAt, group) ->
                            notifiedMatureTimes.add(matureAt)
                            triggerAlert(group.map { it.name }, matureAt)
                        }
                    }

                    // ── 2. 更新常驻通知栏（显示即将成熟的植物）
                    val upcomingPlants = plants
                        .filter { !it.isHarvested && !it.isMature }
                        .sortedBy { it.matureAt }
                        .take(5)
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(upcomingPlants))

                } catch (e: Exception) {
                    Log.e(TAG, "监控异常: ${e.message}", e)
                }

                // 每10秒检查一次
                delay(10_000L)
            }
        }
    }

    /**
     * 触发成熟提醒：推送通知 + 手动播放声音/震动
     * 直接用 Ringtone / Vibrator API，完全不依赖通知渠道的声音/震动配置
     */
    private fun triggerAlert(plantNames: List<String>, matureAt: Long) {
        val soundEnabled = settingsManager.soundEnabled.value
        val vibrationEnabled = settingsManager.vibrationEnabled.value

        Log.d(TAG, "触发提醒 plantNames=$plantNames sound=$soundEnabled vibration=$vibrationEnabled")

        // ── 推送通知（静默渠道，不让系统再额外响铃）
        sendMaturityNotification(plantNames, matureAt)

        // ── 手动播放声音
        if (soundEnabled) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone: Ringtone = RingtoneManager.getRingtone(applicationContext, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.isLooping = false
                }
                ringtone.play()
            } catch (e: Exception) {
                Log.e(TAG, "播放声音失败: ${e.message}")
            }
        }

        // ── 手动震动
        if (vibrationEnabled) {
            try {
                val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                val pattern = longArrayOf(0, 500, 200, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "震动失败: ${e.message}")
            }
        }
    }

    /**
     * 发送成熟通知（使用静默渠道，声音/震动由 triggerAlert 手动控制）
     */
    private fun sendMaturityNotification(plantNames: List<String>, matureAt: Long) {
        val title = if (plantNames.size == 1) "🌱 ${plantNames[0]} 已成熟！"
                    else "🌱 ${plantNames.size} 种植物已成熟！"
        val content = if (plantNames.size == 1) "快去收获你的 ${plantNames[0]} 吧 🎉"
                      else plantNames.joinToString("、") + " 都已成熟，快去收获吧！"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, matureAt.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AlarmReceiver.CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_plant)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(matureAt.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "通知权限未授予: ${e.message}")
        }
    }

    // ── 常驻前台通知渠道（低优先级，不响铃）
    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "植物倒计时",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示最近快要成熟的植物"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(plants: List<com.planttracker.data.model.Plant>): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (plants.isEmpty()) {
            "暂无即将成熟的植物"
        } else {
            plants.joinToString("\n") { plant ->
                "${plant.emoji} ${plant.name}: ${plant.formatRemaining()}"
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_plant)
            .setContentTitle("🌱 植物倒计时")
            .setContentText(if (plants.isNotEmpty()) "${plants.size} 种植物即将成熟" else "暂无即将成熟的植物")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "PlantNotificationService"
        const val CHANNEL_ID = "plant_notification_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, PlantNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlantNotificationService::class.java))
        }
    }
}
