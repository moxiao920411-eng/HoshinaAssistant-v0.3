package com.hoshina.assistant.data.local

import com.hoshina.assistant.data.local.entity.ReminderEntity
import com.hoshina.assistant.domain.model.Reminder

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        message = message,
        triggerAtMillis = triggerAtMillis,
        createdAtMillis = createdAtMillis,
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        message = message,
        triggerAtMillis = triggerAtMillis,
        createdAtMillis = createdAtMillis,
    )
}
