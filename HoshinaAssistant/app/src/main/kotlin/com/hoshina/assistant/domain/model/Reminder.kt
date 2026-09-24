package com.hoshina.assistant.domain.model

data class Reminder(
    val id: String,
    val message: String,
    val triggerAtMillis: Long,
    val createdAtMillis: Long,
)
