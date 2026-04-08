package com.planttracker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.planttracker.R
import com.planttracker.data.SettingsManager
import com.planttracker.data.repository.PlantRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 闹钟广播接收器 — 收到闹钟触发时发送通知
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var plantRepository: PlantRepository

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onReceive(context: Context, intent: Intent) {
        val matureAt = intent.getLongExtra(EXTRA_MATURE_AT, 0L)
        if (matureAt == 0L) return

        CoroutineScope(Dispatchers.IO).launch {
            val plantNames = plantRepository.getPlantNamesByMatureTime(matureAt)
            if (plantNames.isNotEmpty()) {
                // 读取设置
                val soundEnabled = settingsManager.soundEnabled.value
                val vibrationEnabled = settingsManager.vibrationEnabled.value
                showMaturityNotification(context, plantNames, matureAt, soundEnabled, vibrationEnabled)
            }
        }
    }

    private fun showMaturityNotification(
        context: Context,
        plantNames: List<String>,
        matureAt: Long,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        val title = if (plantNames.size == 1) {
            "🌱 ${plantNames[0]} 已成熟！"
        } else {
            "🌱 ${plantNames.size} 种植物已成熟！"
        }
        val content = if (plantNames.size == 1) {
            "快去收获你的 ${plantNames[0]} 吧 🎉"
        } else {
            plantNames.joinToString("、") + " 都已成熟，快去收获吧！"
        }

        // 根据声音+震动设置选择对应渠道
        val channelId = when {
            soundEnabled && vibrationEnabled -> CHANNEL_SOUND_VIBRATE
            soundEnabled && !vibrationEnabled -> CHANNEL_SOUND_ONLY
            !soundEnabled && vibrationEnabled -> CHANNEL_VIBRATE_ONLY
            else -> CHANNEL_SILENT
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_plant)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notification = builder.build()

        try {
            NotificationManagerCompat.from(context)
                .notify(matureAt.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w("AlarmReceiver", "通知权限未授予: ${e.message}")
        }
    }

    companion object {
        // 四种渠道，对应四种声音/震动组合
        const val CHANNEL_SOUND_VIBRATE = "plant_maturity_sound_vibrate"
        const val CHANNEL_SOUND_ONLY    = "plant_maturity_sound_only"
        const val CHANNEL_VIBRATE_ONLY  = "plant_maturity_vibrate_only"
        const val CHANNEL_SILENT        = "plant_maturity_silent"

        /** 已废弃的旧渠道 ID，保留用于删除 */
        const val CHANNEL_ID_LEGACY     = "plant_maturity_channel"

        const val EXTRA_MATURE_AT = "extra_mature_at"
    }
}

/**
 * 闹钟管理器 — 负责创建、更新、取消闹钟，并自动去重
 */
class PlantAlarmManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 同步所有闹钟：
     * 1. 取消所有旧闹钟
     * 2. 获取所有不重复的未来成熟时间
     * 3. 每个唯一时间只创建一个闹钟
     */
    fun syncAlarms(distinctMatureTimes: List<Long>) {
        // 先取消所有现有闹钟（通过清空标记实现，实际项目可用数据库存储已创建的闹钟列表）
        // 这里用简单方案：直接重新设置所有去重后的闹钟
        val now = System.currentTimeMillis()
        distinctMatureTimes
            .filter { it > now }  // 只为未来时间创建闹钟
            .forEach { matureAt ->
                scheduleAlarm(matureAt)
            }

        Log.d("PlantAlarmManager", "同步完成，共创建 ${distinctMatureTimes.filter { it > now }.size} 个闹钟")
    }

    /**
     * 为指定成熟时间创建闹钟（自动去重：相同时间不重复创建）
     */
    fun scheduleAlarm(matureAt: Long) {
        val pendingIntent = buildPendingIntent(matureAt) ?: return

        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    matureAt,
                    pendingIntent
                )
                Log.d("PlantAlarmManager", "闹钟已设置: $matureAt")
            } else {
                // 降级为非精确闹钟
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    matureAt,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("PlantAlarmManager", "设置闹钟失败: ${e.message}")
        }
    }

    /**
     * 取消指定成熟时间的闹钟
     */
    fun cancelAlarm(matureAt: Long) {
        val pendingIntent = buildPendingIntent(matureAt) ?: return
        alarmManager.cancel(pendingIntent)
        Log.d("PlantAlarmManager", "闹钟已取消: $matureAt")
    }

    /**
     * 构建 PendingIntent（相同 matureAt 会复用同一个，实现去重）
     * requestCode 使用 matureAt 的 hashCode，确保同一时间唯一
     */
    private fun buildPendingIntent(matureAt: Long): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_MATURE_AT, matureAt)
        }
        return PendingIntent.getBroadcast(
            context,
            matureAt.hashCode(),  // ← 相同时间 = 相同 requestCode = 自动去重！
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
