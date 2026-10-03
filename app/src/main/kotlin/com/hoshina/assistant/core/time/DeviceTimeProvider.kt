package com.hoshina.assistant.core.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DeviceTimeProvider {

    private val displayLocale: Locale = Locale.TAIWAN

    fun nowMillis(): Long = System.currentTimeMillis()

    fun nowCalendar(): Calendar = Calendar.getInstance(displayLocale)

    /** e.g. 2026/05/30 週六 14:35 */
    fun formatForDisplay(timestampMillis: Long = nowMillis()): String {
        val formatter = SimpleDateFormat("yyyy/MM/dd EEE HH:mm:ss", displayLocale)
        return formatter.format(Date(timestampMillis))
    }

    /** Sent to backend so the AI knows the user's local time. */
    fun formatForApi(timestampMillis: Long = nowMillis()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", displayLocale)
        return formatter.format(Date(timestampMillis))
    }

    /** e.g. 14:35 */
    fun formatTimeOnly(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("HH:mm", displayLocale)
        return formatter.format(Date(timestampMillis))
    }
}
