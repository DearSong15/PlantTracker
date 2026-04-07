package com.planttracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.planttracker.R
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
 * 常驻通知栏，显示最近快要成熟的5种植物
 */
@AndroidEntryPoint
class PlantNotificationService : Service() {

    @Inject
    lateinit var plantRepository: PlantRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(emptyList()))
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                try {
                    // 获取活跃植物（未收获）
                    val plants = plantRepository.getActivePlants().first()
                    
                    // 筛选未成熟的植物，按成熟时间排序，取前5个
                    val upcomingPlants = plants
                        .filter { !it.isMature }
                        .sortedBy { it.matureAt }
                        .take(5)
                    
                    // 更新通知
                    val notification = buildNotification(upcomingPlants)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // 每分钟更新一次
                delay(60000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "植物成熟提醒",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示最近快要成熟的植物"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(plants: List<com.planttracker.data.model.Plant>): Notification {
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
        const val CHANNEL_ID = "plant_notification_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, PlantNotificationService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
