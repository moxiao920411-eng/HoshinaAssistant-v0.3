package com.hoshina.assistant.data.local

import com.hoshina.assistant.data.local.entity.ChatMessageEntity
import com.hoshina.assistant.domain.model.ChatMessage

fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        content = content,
        isFromUser = isFromUser,
        timestamp = timestamp,
    )
}

fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        content = content,
        isFromUser = isFromUser,
        timestamp = timestamp,
    )
}
