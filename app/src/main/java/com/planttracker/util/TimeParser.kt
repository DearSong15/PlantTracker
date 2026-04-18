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
     * 解析中文格式：
     * - "1时24分35秒" / "1小时24分35秒"
     * - "1小时15分钟" / "2小时46分钟"
     * - "75分钟" / "35秒"
     */
    private fun parseChineseFormat(input: String): Long? {
        // 带秒：X时X分X秒 / X小时X分X秒
        val patternHMS = Pattern.compile("""(\d+)\s*[时時小时hH]+\s*(\d+)\s*[分钟分鐘mM]+\s*(\d+)\s*[秒sS]""")
        val matcherHMS = patternHMS.matcher(input)
        if (matcherHMS.find()) {
            val hours = matcherHMS.group(1)?.toLongOrNull() ?: 0L
            val minutes = matcherHMS.group(2)?.toLongOrNull() ?: 0L
            val seconds = matcherHMS.group(3)?.toLongOrNull() ?: 0L
            return hours * 3600_000L + minutes * 60_000L + seconds * 1000L
        }

        // 不带秒：X时X分 / X小时X分钟
        val patternHM = Pattern.compile("""(\d+)\s*[时時小时hH]+\s*(\d+)\s*[分钟分鐘mM]""")
        val matcherHM = patternHM.matcher(input)
        if (matcherHM.find()) {
            val hours = matcherHM.group(1)?.toLongOrNull() ?: 0L
            val minutes = matcherHM.group(2)?.toLongOrNull() ?: 0L
            return hours * 3600_000L + minutes * 60_000L
        }

        // 仅分钟：X分钟 / X分
        val patternM = Pattern.compile("""(\d+)\s*[分钟分鐘]""")
        val matcherM = patternM.matcher(input)
        if (matcherM.find()) {
            val minutes = matcherM.group(1)?.toLongOrNull() ?: return null
            return minutes * 60_000L
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
     * 游戏界面中倒计时格式通常为：1时24分35秒 / 1小时15分钟 / 1:24:35 等
     */
    fun extractMatureTimeFromOcr(ocrText: String): Pair<String, Long>? {
        val lines = ocrText.split("\n")

        // ── 第一轮：优先找包含成熟/后成熟关键词的行
        for (line in lines) {
            if (line.contains("后成熟") || line.contains("成熟")) {
                val timeMillis = parseToMillis(line)
                if (timeMillis != null && timeMillis > 0) {
                    return Pair(line.trim(), timeMillis)
                }
            }
        }

        // ── 第二轮：遍历所有行，找第一个能解析出有效时间的
        for (line in lines) {
            val timeMillis = parseToMillis(line.trim())
            if (timeMillis != null && timeMillis > 0) {
                return Pair(line.trim(), timeMillis)
            }
        }

        // ── 第三轮：在整段文本里用正则直接搜索时间片段（容错）
        // 支持：1时24分35秒 / 1小时15分钟 / 1:24:35
        val timePattern = Pattern.compile(
            """(\d+)\s*[时時小时hH]\s*(\d+)\s*[分钟分鐘mM]\s*(\d+)?\s*[秒sS]?""" +
            """|(\d+)\s*[时時小时hH]\s*(\d+)\s*[分钟分鐘mM]""" +
            """|(\d+):(\d+):(\d+)""" +
            """|(\d+):(\d+)"""
        )
        val matcher = timePattern.matcher(ocrText)
        if (matcher.find()) {
            val matchedText = matcher.group()
            val timeMillis = parseToMillis(matchedText)
            if (timeMillis != null && timeMillis > 0) {
                return Pair(matchedText, timeMillis)
            }
        }

        return null
    }

    /**
     * 从 OCR 文本中提取昵称
     * 游戏界面昵称通常跟在 "/ka " 前缀后，或单独一行
     * 同时过滤掉游戏常见 UI 文字
     */
    fun extractNickname(ocrText: String): String? {
        // 游戏常见 UI 关键词，这些行不是昵称
        val uiKeywords = setOf(
            "商城", "仓库", "商店", "宠物", "图鉴", "好友", "等级", "活动",
            "成熟", "后成熟", "分钟", "小时", "秒", "已成熟",
            "求助", "联动", "耕纪", "Version", "ID:"
        )

        val lines = ocrText.split("\n")

        // 优先：找包含 "/ka " 或 "/ ka " 前缀的行（游戏昵称常见格式）
        for (line in lines) {
            val m = Regex("""/\s*[kK][aA]\s+(.+)""").find(line.trim())
            if (m != null) {
                val name = m.groupValues[1].trim()
                if (name.isNotEmpty()) return name
            }
        }

        // 兜底：前5行中，过滤掉 UI 关键词后，取第一个合法昵称行
        for (line in lines.take(5)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.length > 20) continue
            if (uiKeywords.any { trimmed.contains(it) }) continue
            // 必须含有字母或中文
            if (trimmed.any { it.isLetter() || it in '\u4e00'..'\u9fff' }) {
                // 过滤纯数字/纯符号/版本号
                if (!trimmed.matches(Regex("""[\d\s.,:/%-]+"""))) {
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
