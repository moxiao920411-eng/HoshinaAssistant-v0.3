package com.hoshina.assistant.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val message: String,
    @ColumnInfo(name = "trigger_at_millis")
    val triggerAtMillis: Long,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
)
