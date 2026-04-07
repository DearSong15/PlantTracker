package com.planttracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planttracker.util.TimeParser
import java.text.SimpleDateFormat
import java.util.*


// ── 常见植物列表 ────────────────────────────────────────────────────────────

val PLANT_PRESETS = listOf(
    PlantPreset("小麦", "🌾"),
    PlantPreset("水稻", "🌾"),
    PlantPreset("玉米", "🌽"),
    PlantPreset("番茄", "🍅"),
    PlantPreset("土豆", "🥔"),
    PlantPreset("胡萝卜", "🥕"),
    PlantPreset("辣椒", "🌶️"),
    PlantPreset("草莓", "🍓"),
    PlantPreset("西瓜", "🍉"),
    PlantPreset("玫瑰", "🌹"),
    PlantPreset("向日葵", "🌻"),
    PlantPreset("仙人掌", "🌵"),
    PlantPreset("竹子", "🎋"),
    PlantPreset("薰衣草", "💐"),
    PlantPreset("蘑菇", "🍄"),
    PlantPreset("樱花", "🌸"),
    PlantPreset("草药", "🌿"),
    PlantPreset("萝卜", "🥬"),
    PlantPreset("葡萄", "🍇"),
    PlantPreset("自定义", "🌱"),
)

data class PlantPreset(val name: String, val emoji: String)

// ── 常见成熟时间预设（小时） ───────────────────────────────────────────────

val TIME_PRESETS = listOf(
    "1 小时" to 3600000L,
    "2 小时" to 7200000L,
    "4 小时" to 14400000L,
    "8 小时" to 28800000L,
    "12 小时" to 43200000L,
    "1 天" to 86400000L,
    "2 天" to 172800000L,
    "3 天" to 259200000L,
    "7 天" to 604800000L,
    "15 天" to 1296000000L,
    "30 天" to 2592000000L,
    "自定义" to 0L,
)

@Composable
fun AddPlantDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, matureAt: Long, note: String) -> Unit
) {
    var selectedPlant by remember { mutableStateOf(PLANT_PRESETS[0]) }
    var customName by remember { mutableStateOf("") }
    var customEmoji by remember { mutableStateOf("🌱") }
    var selectedTimePresetIndex by remember { mutableStateOf(5) } // 默认 1 天
    var customMatureAt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    // 计算成熟时间
    val matureAt: Long = remember(selectedTimePresetIndex, customMatureAt) {
        if (selectedTimePresetIndex == TIME_PRESETS.lastIndex) {
            // 自定义时间：尝试解析 HH:mm 格式
            parseCustomTime(customMatureAt)
        } else {
            System.currentTimeMillis() + TIME_PRESETS[selectedTimePresetIndex].second
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🌱 添加新植物", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── 第1步：选择植物 ──
                Text("选择植物", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Emoji Grid
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val rows = PLANT_PRESETS.chunked(5)
                    items(rows.size) { rowIndex ->
                        val row = rows[rowIndex]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { preset ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (selectedPlant == preset)
                                                Modifier.weight(1f)
                                            else
                                                Modifier.clickable { selectedPlant = preset }
                                        )
                                        .background(
                                            if (selectedPlant == preset)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { selectedPlant = preset }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(preset.emoji, fontSize = 22.sp)
                                    Text(
                                        preset.name,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            // 填充空位
                            repeat(5 - row.size) {
                                Spacer(modifier = Modifier.size(64.dp))
                            }
                        }
                    }
                }

                // 自定义名称输入
                if (selectedPlant.name == "自定义") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("植物名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── 第2步：设置成熟时间 ──
                Text("成熟时间", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // 时间预设 Chips
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    val rows = TIME_PRESETS.chunked(4)
                    items(rows.size) { rowIndex ->
                        val row = rows[rowIndex]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEachIndexed { colIndex, pair ->
                                val index = rowIndex * 4 + colIndex
                                FilterChip(
                                    selected = selectedTimePresetIndex == index,
                                    onClick = { selectedTimePresetIndex = index },
                                    label = { Text(pair.first, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // 自定义时间输入
                if (selectedTimePresetIndex == TIME_PRESETS.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMatureAt,
                        onValueChange = { customMatureAt = it },
                        label = { Text("输入时间 (如: 1小时15分钟、2h30m、75分钟)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        supportingText = {
                            // 实时显示解析结果
                            val parsed = TimeParser.parseToMillis(customMatureAt)
                            if (parsed != null) {
                                Text(
                                    "✓ 识别: ${TimeParser.formatDuration(parsed)}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            } else if (customMatureAt.isNotBlank()) {
                                Text(
                                    "⚠ 无法识别，请使用格式如: 1小时15分钟",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    )
                }

                // 显示计算出的成熟时间
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⏰ 成熟时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(matureAt))}",
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── 第3步：备注（可选） ──
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Note, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    val name = if (selectedPlant.name == "自定义") customName else selectedPlant.name
                    val emoji = if (selectedPlant.name == "自定义") customEmoji else selectedPlant.emoji
                    if (name.isNotBlank() && matureAt > 0) {
                        onConfirm(name, emoji, matureAt, note)
                    }
                },
                enabled = if (selectedPlant.name == "自定义") customName.isNotBlank()
                         else true
            ) {
                Text("确认种植 🌱")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 解析用户输入的自定义时间
 * 支持格式: 
 * - 智能格式: "1小时15分钟", "2h30m", "75分钟"
 * - 时间格式: "HH:mm"（今天/明天）
 * - 完整格式: "yyyy-MM-dd HH:mm"
 */
private fun parseCustomTime(input: String): Long {
    if (input.isBlank()) return System.currentTimeMillis() + 86400000L

    // 首先尝试智能时间解析
    val smartParsed = TimeParser.parseToMillis(input)
    if (smartParsed != null) {
        return System.currentTimeMillis() + smartParsed
    }

    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()

    return try {
        // 尝试完整日期时间格式
        val fullFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        fullFmt.parse(input)?.time ?: run {
            // 尝试仅时间格式 HH:mm
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val parsed = timeFmt.parse(input) ?: return now + 86400000L
            val timeCal = Calendar.getInstance().apply { time = parsed }

            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            cal.set(Calendar.SECOND, 0)

            // 如果时间已过，自动跳到明天
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            cal.timeInMillis
        }
    } catch (e: Exception) {
        now + 86400000L // 解析失败默认1天后
    }
}
