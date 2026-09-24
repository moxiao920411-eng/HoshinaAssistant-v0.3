package com.hoshina.assistant.data.repository

import com.hoshina.assistant.domain.model.ChatMessage
import com.hoshina.assistant.domain.model.ChatSendResult
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(): Flow<List<ChatMessage>>
    suspend fun loadStoredMessages(): List<ChatMessage>
    suspend fun storedMessageCount(): Int
    suspend fun sendMessage(
        userId: Int,
        message: String,
        voiceMode: Boolean = false,
    ): Result<ChatSendResult>
    suspend fun clearMessages()
}
