package com.hoshina.assistant.data.repository

import android.content.Context
import com.hoshina.assistant.R
import com.hoshina.assistant.core.time.DeviceTimeProvider
import com.hoshina.assistant.data.local.ChatLocalDataSource
import com.hoshina.assistant.data.local.SettingsRepository
import com.hoshina.assistant.data.local.UserMemoryRepository
import com.hoshina.assistant.data.remote.BackendUrlResolver
import com.hoshina.assistant.data.remote.ChatHistoryDto
import com.hoshina.assistant.data.remote.ChatRequestDto
import com.hoshina.assistant.data.remote.RetrofitProvider
import com.hoshina.assistant.domain.model.ChatMessage
import com.hoshina.assistant.domain.model.ChatSendResult
import com.hoshina.assistant.reminder.ReminderParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val SENTENCES_PER_REPLY_CHUNK = 2
private const val FALLBACK_REPLY_CHUNK_SIZE = 48

class ChatRepositoryImpl(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val chatLocalDataSource: ChatLocalDataSource,
    private val userMemoryRepository: UserMemoryRepository,
) : ChatRepository {

    override fun observeMessages(): Flow<List<ChatMessage>> {
        return chatLocalDataSource.observeMessages()
    }

    override suspend fun loadStoredMessages(): List<ChatMessage> {
        return chatLocalDataSource.getAllMessages()
    }

    override suspend fun storedMessageCount(): Int {
        return chatLocalDataSource.messageCount()
    }

    override suspend fun sendMessage(
        userId: Int,
        message: String,
        voiceMode: Boolean,
    ): Result<ChatSendResult> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                IllegalArgumentException(appContext.getString(R.string.error_empty_message)),
            )
        }

        val history = chatLocalDataSource.getRecentHistory(ChatLocalDataSource.MAX_API_HISTORY)
        chatLocalDataSource.insertUserMessage(trimmed)
        val autoMemoryEnabled = settingsRepository.getIsAutoMemoryEnabled()
        if (autoMemoryEnabled) {
            userMemoryRepository.rememberFromUserMessage(trimmed)
        }

        return try {
            val baseUrl = settingsRepository.getApiBaseUrl()
            val profileMemory = if (autoMemoryEnabled) {
                userMemoryRepository.getCombinedMemory()
            } else {
                userMemoryRepository.getManualMemory()
            }
            val aiName = settingsRepository.getAiName()
            val aiModel = settingsRepository.getAiModel()
            val rolePrompt = settingsRepository.getRolePrompt()
            val request = ChatRequestDto(
                userId = userId,
                message = trimmed,
                history = history.toHistoryDto(),
                userProfile = profileMemory.ifBlank { null },
                clientTime = DeviceTimeProvider.formatForApi(),
                aiName = aiName.ifBlank { null },
                aiModel = aiModel.ifBlank { null },
                rolePrompt = rolePrompt.ifBlank { null },
                voiceMode = voiceMode,
            )
            val response = sendChatWithBackendRecovery(baseUrl, request)
            val rawReply = response.response.trim()
            val displayReply = ReminderParser.stripReminderTagForDisplay(rawReply).ifBlank { rawReply }
            if (displayReply.isEmpty()) {
                Result.failure(
                    IllegalStateException(appContext.getString(R.string.error_empty_response)),
                )
            } else {
                splitAssistantReplyForChat(displayReply).forEach { chunk ->
                    chatLocalDataSource.insertAssistantMessage(chunk)
                }
                Result.success(
                    ChatSendResult(
                        displayReply = displayReply,
                        rawReply = rawReply,
                        agentsUsed = response.agentsUsed,
                        mode = response.mode,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearMessages() {
        chatLocalDataSource.clearAll()
    }

    private fun List<ChatMessage>.toHistoryDto(): List<ChatHistoryDto> {
        return map { message ->
            ChatHistoryDto(
                role = if (message.isFromUser) ROLE_USER else ROLE_ASSISTANT,
                content = message.content,
            )
        }
    }

    companion object {
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"
    }

    private suspend fun sendChatWithBackendRecovery(
        baseUrl: String,
        request: ChatRequestDto,
    ) = try {
        RetrofitProvider.createApiService(baseUrl).sendChat(request)
    } catch (e: Exception) {
        if (!e.isRecoverableNetworkError()) {
            throw e
        }

        val resolvedUrl = BackendUrlResolver.resolve(baseUrl) ?: throw e
        settingsRepository.setApiBaseUrl(resolvedUrl)
        RetrofitProvider.createApiService(resolvedUrl).sendChat(request)
    }
}

private fun Throwable.isRecoverableNetworkError(): Boolean {
    return this is IOException ||
        this is SocketTimeoutException ||
        this is UnknownHostException ||
        this is NoRouteToHostException
}

private fun splitAssistantReplyForChat(text: String): List<String> {
    val normalized = text
        .replace("\r\n", "\n")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .trim()
    if (normalized.isEmpty()) return emptyList()

    val sentences = splitIntoSentences(normalized)
    if (sentences.size <= 1) {
        return splitLongText(normalized)
    }

    return sentences
        .chunked(SENTENCES_PER_REPLY_CHUNK)
        .map { group -> group.joinToString("").trim() }
        .filter { it.isNotEmpty() }
}

private fun splitIntoSentences(text: String): List<String> {
    val sentences = mutableListOf<String>()
    val current = StringBuilder()

    text.forEach { char ->
        current.append(char)
        if (char in SENTENCE_END_CHARS || char == '\n') {
            val sentence = current.toString().trim()
            if (sentence.isNotEmpty()) {
                sentences += sentence
            }
            current.clear()
        }
    }

    val tail = current.toString().trim()
    if (tail.isNotEmpty()) {
        sentences += tail
    }
    return sentences
}

private fun splitLongText(text: String): List<String> {
    if (text.length <= FALLBACK_REPLY_CHUNK_SIZE) return listOf(text)
    return text
        .chunked(FALLBACK_REPLY_CHUNK_SIZE)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private val SENTENCE_END_CHARS = setOf(
    '\u3002',
    '\uff01',
    '\uff1f',
    '.',
    '!',
    '?',
    '\uff1b',
    ';',
)
