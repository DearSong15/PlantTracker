package com.planttracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 植物数据实体
 * @param id 唯一标识
 * @param name 植物名称
 * @param emoji 植物图标（emoji）
 * @param plantedAt 种植时间（毫秒时间戳）
 * @param matureAt 成熟时间（毫秒时间戳）
 * @param note 备注
 * @param isHarvested 是否已收获
 */
@Entity(tableName = "plants")
data class Plant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val emoji: String = "🌱",
    val plantedAt: Long = System.currentTimeMillis(),
    val matureAt: Long,
    val note: String = "",
    val isHarvested: Boolean = false
) {
    /**
     * 距离成熟还有多少毫秒（负数表示已过期）
     */
    val remainingMs: Long get() = matureAt - System.currentTimeMillis()

    /**
     * 是否已成熟
     */
    val isMature: Boolean get() = remainingMs <= 0

    /**
     * 格式化剩余时间显示（带秒数）
     */
    fun formatRemaining(): String {
        if (isMature) return "已成熟 🎉"
        val totalSeconds = remainingMs / 1000
        val days = totalSeconds / 60 / 60 / 24
        val hours = (totalSeconds / 60 / 60) % 24
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60
        return when {
            days > 0 -> "${days}天${hours}时${minutes}分${seconds}秒"
            hours > 0 -> "${hours}时${minutes}分${seconds}秒"
            else -> "${minutes}分${seconds}秒"
        }
    }

    /**
     * 格式化预计收获时间（几点几分几秒）
     */
    fun formatMatureTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(matureAt))
    }
}
