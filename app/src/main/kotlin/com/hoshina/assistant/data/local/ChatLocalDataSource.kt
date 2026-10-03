package com.hoshina.assistant.data.local

import com.hoshina.assistant.data.local.dao.ChatMessageDao
import com.hoshina.assistant.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Persists chat history on the user's device (Room).
 */
class ChatLocalDataSource(
    private val chatMessageDao: ChatMessageDao,
) {

    fun observeMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getAllMessages(): List<ChatMessage> {
        return chatMessageDao.getAll().map { it.toDomain() }
    }

    suspend fun getRecentHistory(limit: Int = MAX_API_HISTORY): List<ChatMessage> {
        return chatMessageDao.getAll()
            .takeLast(limit)
            .map { it.toDomain() }
    }

    suspend fun insertUserMessage(content: String): ChatMessage {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content.trim(),
            isFromUser = true,
        )
        chatMessageDao.insert(message.toEntity())
        pruneOldMessages()
        return message
    }

    suspend fun insertAssistantMessage(content: String): ChatMessage {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content.trim(),
            isFromUser = false,
        )
        chatMessageDao.insert(message.toEntity())
        pruneOldMessages()
        return message
    }

    suspend fun clearAll() {
        chatMessageDao.deleteAll()
    }

    suspend fun messageCount(): Int = chatMessageDao.count()

    private suspend fun pruneOldMessages() {
        val extra = chatMessageDao.count() - MAX_STORED_MESSAGES
        if (extra > 0) {
            chatMessageDao.deleteOldest(extra)
        }
    }

    companion object {
        const val MAX_API_HISTORY = 40
        private const val MAX_STORED_MESSAGES = 500
    }
}
