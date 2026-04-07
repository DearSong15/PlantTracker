package com.planttracker.util

import java.util.regex.Pattern

/**
 * 智能时间解析器
 * 支持格式：
 * - "1小时15分钟" -> 1小时15分钟后的时间戳
 * - "1h15m" -> 1小时15分钟后的时间戳
 * - "75分钟" -> 1小时15分钟后的时间戳
 * - "2小时46分钟" -> 2小时46分钟后的时间戳
 * - "12:15:34" -> 直接解析为倒计时
 */
object TimeParser {

    /**
     * 解析时间字符串，返回毫秒数（从当前时间开始计算的倒计时）
     * @param input 用户输入的时间字符串
     * @return 毫秒数，解析失败返回 null
     */
    fun parseToMillis(input: String): Long? {
        val trimmed = input.trim()
        
        // 尝试解析各种格式
        return parseChineseFormat(trimmed)
            ?: parseShortFormat(trimmed)
            ?: parseColonFormat(trimmed)
            ?: parseMinutesOnly(trimmed)
            ?: parseHoursOnly(trimmed)
    }

    /**
     * 解析中文格式："1小时15分钟", "2小时46分钟"
     */
    private fun parseChineseFormat(input: String): Long? {
        // 匹配 "X小时Y分钟" 或 "X时Y分"
        val pattern = Pattern.compile("""
            (\d+)\s*[小时時hH]\s*(\d+)\s*[分钟分鐘mM]?
            |(\d+)\s*[分钟分鐘mM]
        """.trimIndent().replace("\n", ""))
        
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            val hours = matcher.group(1)?.toIntOrNull() ?: 0
            val minutes = matcher.group(2)?.toIntOrNull() 
                ?: matcher.group(3)?.toIntOrNull() 
                ?: 0
            return (hours * 60 * 60 * 1000 + minutes * 60 * 1000).toLong()
        }
        return null
    }

    /**
     * 解析简写格式："1h15m", "2h30m"
     */
    private fun parseShortFormat(input: String): Long? {
        val pattern = Pattern.compile("""
            (\d+)\s*[hH]\s*(\d+)\s*[mM]?
            |(\d+)\s*[hH]
            |(\d+)\s*[mM]
        """.trimIndent().replace("\n", ""))
        
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            val hours = matcher.group(1)?.toIntOrNull() 
                ?: matcher.group(3)?.toIntOrNull() 
                ?: 0
            val minutes = matcher.group(2)?.toIntOrNull() 
                ?: matcher.group(4)?.toIntOrNull() 
                ?: 0
            return (hours * 60 * 60 * 1000 + minutes * 60 * 1000).toLong()
        }
        return null
    }

    /**
     * 解析冒号格式："12:15:34", "2:30:00"
     */
    private fun parseColonFormat(input: String): Long? {
        val pattern = Pattern.compile("""
            (\d+):(\d+):(\d+)
            |(\d+):(\d+)
        """.trimIndent().replace("\n", ""))
        
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            val hours = matcher.group(1)?.toIntOrNull() 
                ?: matcher.group(4)?.toIntOrNull() 
                ?: 0
            val minutes = matcher.group(2)?.toIntOrNull() 
                ?: matcher.group(5)?.toIntOrNull() 
                ?: 0
            val seconds = matcher.group(3)?.toIntOrNull() ?: 0
            return (hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000).toLong()
        }
        return null
    }

    /**
     * 仅分钟："75分钟", "90分"
     */
    private fun parseMinutesOnly(input: String): Long? {
        val pattern = Pattern.compile("""
            (\d+)\s*[分钟分鐘mM]
        """.trimIndent())
        
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            val minutes = matcher.group(1)?.toIntOrNull() ?: return null
            return (minutes * 60 * 1000).toLong()
        }
        return null
    }

    /**
     * 仅小时："2小时", "3h"
     */
    private fun parseHoursOnly(input: String): Long? {
        val pattern = Pattern.compile("""
            (\d+)\s*[小时時hH]
        """.trimIndent())
        
        val matcher = pattern.matcher(input)
        if (matcher.find()) {
            val hours = matcher.group(1)?.toIntOrNull() ?: return null
            return (hours * 60 * 60 * 1000).toLong()
        }
        return null
    }

    /**
     * 从 OCR 识别的文本中提取成熟时间
     * 识别包含 "后成熟" 的时间
     */
    fun extractMatureTimeFromOcr(ocrText: String): Pair<String, Long>? {
        // 查找包含 "后成熟" 的行
        val lines = ocrText.split("\n")
        
        for (line in lines) {
            if (line.contains("后成熟") || line.contains("成熟")) {
                // 尝试提取时间
                val timeMillis = parseToMillis(line)
                if (timeMillis != null) {
                    return Pair(line.trim(), timeMillis)
                }
            }
        }
        
        // 如果没找到 "后成熟"，尝试直接解析所有时间格式
        val timePattern = Pattern.compile("""
            (\d+)\s*[小时時hH]\s*(\d+)\s*[分钟分鐘mM]
            |(\\d+):(\d+):(\d+)
        """.trimIndent().replace("\n", ""))
        
        val matcher = timePattern.matcher(ocrText)
        if (matcher.find()) {
            val matchedText = matcher.group()
            val timeMillis = parseToMillis(matchedText)
            if (timeMillis != null) {
                return Pair(matchedText, timeMillis)
            }
        }
        
        return null
    }

    /**
     * 从 OCR 文本中提取昵称（通常在左上角）
     */
    fun extractNickname(ocrText: String): String? {
        val lines = ocrText.split("\n")
        
        // 通常在第一行或前几行
        for (line in lines.take(3)) {
            // 匹配常见的昵称格式
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed.length <= 20) {
                // 过滤掉纯数字、纯符号
                if (trimmed.any { it.isLetter() || it in '\u4e00'.. '\u9fff' }) {
                    return trimmed
                }
            }
        }
        
        return null
    }

    /**
     * 格式化毫秒为可读字符串
     */
    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${minutes}分钟"
        }
    }
}
