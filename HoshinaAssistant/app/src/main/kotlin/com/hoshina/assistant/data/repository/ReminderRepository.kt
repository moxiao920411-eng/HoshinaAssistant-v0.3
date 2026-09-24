package com.hoshina.assistant.data.repository

import com.hoshina.assistant.domain.model.Reminder
import com.hoshina.assistant.reminder.ParsedReminder

interface ReminderRepository {
    suspend fun schedule(parsed: ParsedReminder): Reminder
    suspend fun rescheduleAllUpcoming()
}
