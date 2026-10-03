package com.hoshina.assistant.data.repository

import com.hoshina.assistant.core.time.DeviceTimeProvider
import com.hoshina.assistant.data.local.dao.ReminderDao
import com.hoshina.assistant.data.local.toEntity
import com.hoshina.assistant.domain.model.Reminder
import com.hoshina.assistant.reminder.ParsedReminder
import com.hoshina.assistant.reminder.ReminderScheduler
import java.util.UUID

class ReminderRepositoryImpl(
    private val reminderDao: ReminderDao,
    private val reminderScheduler: ReminderScheduler,
) : ReminderRepository {

    override suspend fun schedule(parsed: ParsedReminder): Reminder {
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            message = parsed.message,
            triggerAtMillis = parsed.triggerAtMillis,
            createdAtMillis = DeviceTimeProvider.nowMillis(),
        )
        reminderDao.insert(reminder.toEntity())
        reminderScheduler.schedule(reminder)
        return reminder
    }

    override suspend fun rescheduleAllUpcoming() {
        val upcoming = reminderDao.getUpcoming(DeviceTimeProvider.nowMillis())
        upcoming.forEach { entity ->
            reminderScheduler.schedule(
                Reminder(
                    id = entity.id,
                    message = entity.message,
                    triggerAtMillis = entity.triggerAtMillis,
                    createdAtMillis = entity.createdAtMillis,
                ),
            )
        }
    }
}
