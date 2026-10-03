package com.hoshina.assistant.reminder

import com.hoshina.assistant.core.time.DeviceTimeProvider
import java.util.Calendar

data class ParsedReminder(
    val triggerAtMillis: Long,
    val message: String,
)

object ReminderParser {

    private val reminderKeywordPattern = Regex(
        """(提醒|通知|叫我|鬧鐘|remind|reminder|alarm|通知して|リマインド)""",
        RegexOption.IGNORE_CASE,
    )
    private val aiReminderPattern = Regex(
        """\[REMINDER:(\d{1,2}):(\d{2}):(.+?)]""",
        RegexOption.IGNORE_CASE,
    )
    private val colonTimePattern = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val chineseHourPattern = Regex(
        """(?:(今天|明天|早上|上午|中午|下午|晚上|今晚|凌晨)\s*)?(\d{1,2})\s*(?:點|点|時|时)(?:\s*(\d{1,2})\s*(?:分|分鐘|分钟)?)?""",
    )
    private val chineseNumberTimePattern = Regex(
        """(?:(今天|明天|早上|上午|中午|下午|晚上|今晚|凌晨)\s*)?([零〇一二兩两三四五六七八九十\d]{1,3})\s*(?:點|点|時|时)\s*(半|[零〇一二兩两三四五六七八九十\d]{1,3}\s*(?:分|分鐘|分钟)?)?""",
    )
    private val relativeMinutesPattern = Regex(
        """(\d{1,3})\s*(分鐘|分钟|分|minute|minutes|mins|m)\s*(後|后|later|之後)?""",
        RegexOption.IGNORE_CASE,
    )
    private val relativeHoursPattern = Regex(
        """(\d{1,2})\s*(小時|小时|hour|hours|h)\s*(後|后|later|之後)?""",
        RegexOption.IGNORE_CASE,
    )

    fun parseFromUserMessage(text: String): ParsedReminder? {
        val trimmed = text.trim()
        if (!reminderKeywordPattern.containsMatchIn(trimmed)) return null

        val triggerAt = extractTriggerTime(trimmed) ?: return null
        val message = extractReminderMessage(trimmed)
        return ParsedReminder(triggerAt, message)
    }

    fun stripReminderTagForDisplay(text: String): String {
        return text.replace(aiReminderPattern, "").trim()
    }

    fun parseFromAssistantReply(text: String): ParsedReminder? {
        val match = aiReminderPattern.find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val message = match.groupValues[3].trim().ifBlank { DEFAULT_MESSAGE }
        return ParsedReminder(
            triggerAtMillis = buildAbsoluteTriggerTime(
                hour = hour,
                minute = minute,
                explicitTomorrow = false,
                period = "",
                now = DeviceTimeProvider.nowCalendar(),
            ),
            message = message,
        )
    }

    private fun extractTriggerTime(text: String): Long? {
        val now = DeviceTimeProvider.nowCalendar()

        relativeMinutesPattern.find(text)?.let { match ->
            val minutes = match.groupValues[1].toIntOrNull() ?: return@let null
            return (now.clone() as Calendar).apply {
                add(Calendar.MINUTE, minutes)
            }.timeInMillis
        }

        relativeHoursPattern.find(text)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return@let null
            return (now.clone() as Calendar).apply {
                add(Calendar.HOUR_OF_DAY, hours)
            }.timeInMillis
        }

        colonTimePattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let null
            val minute = match.groupValues[2].toIntOrNull() ?: return@let null
            if (hour in 0..23 && minute in 0..59) {
                return buildAbsoluteTriggerTime(
                    hour = hour,
                    minute = minute,
                    explicitTomorrow = text.contains("明天"),
                    period = extractPeriod(text),
                    now = now,
                )
            }
        }

        chineseNumberTimePattern.find(text)?.let { match ->
            val period = match.groupValues[1]
            val rawHour = parseChineseNumber(match.groupValues[2]) ?: return@let null
            val minute = parseMinute(match.groupValues[3])
            val hour = applyPeriod(period, rawHour)
            if (hour in 0..23 && minute in 0..59) {
                return buildAbsoluteTriggerTime(
                    hour = hour,
                    minute = minute,
                    explicitTomorrow = period == "明天" || text.contains("明天"),
                    period = period,
                    now = now,
                )
            }
        }

        chineseHourPattern.find(text)?.let { match ->
            val period = match.groupValues[1]
            val rawHour = match.groupValues[2].toIntOrNull() ?: return@let null
            val minute = match.groupValues[3].toIntOrNull() ?: 0
            val hour = applyPeriod(period, rawHour)
            if (hour in 0..23 && minute in 0..59) {
                return buildAbsoluteTriggerTime(
                    hour = hour,
                    minute = minute,
                    explicitTomorrow = period == "明天" || text.contains("明天"),
                    period = period,
                    now = now,
                )
            }
        }

        return null
    }

    private fun parseMinute(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return 0
        if (trimmed == "半") return 30
        return parseChineseNumber(
            trimmed
                .replace("分鐘", "")
                .replace("分钟", "")
                .replace("分", "")
                .trim(),
        ) ?: 0
    }

    private fun parseChineseNumber(text: String): Int? {
        val trimmed = text.trim()
        trimmed.toIntOrNull()?.let { return it }
        if (trimmed.isBlank()) return null

        val normalized = trimmed
            .replace("兩", "二")
            .replace("两", "二")
            .replace("〇", "零")

        if (!normalized.contains("十")) {
            return normalized.mapNotNull { CHINESE_DIGITS[it] }
                .joinToString("")
                .toIntOrNull()
        }

        val parts = normalized.split("十", limit = 2)
        val tens = parts.getOrNull(0).orEmpty().let { part ->
            if (part.isBlank()) 1 else CHINESE_DIGITS[part.first()] ?: return null
        }
        val ones = parts.getOrNull(1).orEmpty().let { part ->
            if (part.isBlank()) 0 else CHINESE_DIGITS[part.first()] ?: return null
        }
        return tens * 10 + ones
    }

    private fun applyPeriod(period: String, hour: Int): Int {
        return when (period) {
            "下午", "晚上", "今晚" -> if (hour in 1..11) hour + 12 else hour
            "中午" -> if (hour in 1..10) hour + 12 else 12
            "凌晨", "早上", "上午", "今天", "明天", "" -> hour
            else -> hour
        }
    }

    private fun extractPeriod(text: String): String {
        return listOf("明天", "今晚", "晚上", "下午", "中午", "上午", "早上", "凌晨", "今天")
            .firstOrNull { text.contains(it) }
            .orEmpty()
    }

    private fun extractReminderMessage(text: String): String {
        val withoutTime = text
            .replace(aiReminderPattern, "")
            .replace(relativeMinutesPattern, "")
            .replace(relativeHoursPattern, "")
            .replace(colonTimePattern, "")
            .replace(chineseHourPattern, "")
            .replace(chineseNumberTimePattern, "")
            .replace(reminderKeywordPattern, "")
            .replace(Regex("""[，。！？、,.!?:：；;\s]+"""), " ")
            .trim()
        return withoutTime.ifBlank { DEFAULT_MESSAGE }
    }

    private fun buildAbsoluteTriggerTime(
        hour: Int,
        minute: Int,
        explicitTomorrow: Boolean,
        period: String,
        now: Calendar,
    ): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (explicitTomorrow) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        if (!explicitTomorrow && target.timeInMillis <= now.timeInMillis) {
            val shouldStayToday = period in setOf("今天", "今晚")
            if (!shouldStayToday) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis
    }

    private const val DEFAULT_MESSAGE = "Hoshina 提醒"

    private val CHINESE_DIGITS = mapOf(
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )
}
