package com.hoshina.assistant.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoshina.assistant.R
import com.hoshina.assistant.core.time.DeviceTimeProvider
import com.hoshina.assistant.data.repository.ChatRepository
import com.hoshina.assistant.data.repository.ReminderRepository
import com.hoshina.assistant.data.local.UserMemoryRepository
import com.hoshina.assistant.domain.model.ChatMessage
import com.hoshina.assistant.reminder.ReminderParser
import com.hoshina.assistant.reminder.ReminderPermissionHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isMemoryLoaded: Boolean = false,
    val storedMessageCount: Int = 0,
    val deviceTimeText: String = "",
    val userAvatarUri: String = "",
    val activeAgents: List<String> = emptyList(),
    val activeAgentMode: String? = null,
    val isAssistantSpeaking: Boolean = false,
    val reminderNotice: String? = null,
    val error: String? = null,
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val reminderRepository: ReminderRepository,
    private val userMemoryRepository: UserMemoryRepository,
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _speakEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val speakEvents: SharedFlow<String> = _speakEvents.asSharedFlow()

    init {
        refreshDeviceTime()
        startDeviceTimeTicker()
        clearPreviousMessagesOnStartup()
        observeLocalMessages()
        observeUserAvatar()
    }

    fun refreshDeviceTime() {
        _uiState.update {
            it.copy(deviceTimeText = DeviceTimeProvider.formatForDisplay())
        }
    }

    private fun startDeviceTimeTicker() {
        viewModelScope.launch {
            while (isActive) {
                refreshDeviceTime()
                delay(1_000L)
            }
        }
    }

    fun clearReminderNotice() {
        _uiState.update { it.copy(reminderNotice = null) }
    }

    private fun clearPreviousMessagesOnStartup() {
        viewModelScope.launch {
            chatRepository.clearMessages()
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    storedMessageCount = 0,
                    isMemoryLoaded = true,
                )
            }
        }
    }

    private fun observeLocalMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages().collect { messages ->
                _uiState.update {
                    it.copy(
                        messages = messages,
                        storedMessageCount = messages.size,
                        isMemoryLoaded = true,
                    )
                }
            }
        }
    }

    private fun observeUserAvatar() {
        viewModelScope.launch {
            userMemoryRepository.userAvatarUri.collect { uri ->
                _uiState.update { it.copy(userAvatarUri = uri) }
            }
        }
    }

    fun setListening(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun setAssistantSpeaking(speaking: Boolean) {
        _uiState.update { it.copy(isAssistantSpeaking = speaking) }
    }

    fun sendMessage(
        content: String,
        speakReply: Boolean = false,
        voiceMode: Boolean = false,
    ) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || _uiState.value.isLoading) return

        refreshDeviceTime()
        _uiState.update { it.copy(isLoading = true, error = null, reminderNotice = null) }

        viewModelScope.launch {
            val userReminderNotice = scheduleReminderIfPresent(trimmed)

            chatRepository.sendMessage(
                userId = DEFAULT_USER_ID,
                message = trimmed,
                voiceMode = voiceMode,
            )
                .onSuccess { result ->
                    val aiReminderNotice = scheduleReminderFromAiReply(result.rawReply)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeAgents = result.agentsUsed,
                            activeAgentMode = result.mode,
                            reminderNotice = userReminderNotice ?: aiReminderNotice,
                        )
                    }
                    if (speakReply) {
                        _speakEvents.emit(result.displayReply)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            activeAgents = emptyList(),
                            activeAgentMode = null,
                            reminderNotice = userReminderNotice,
                            error = throwable.message ?: getApplication<Application>().getString(
                                R.string.chat_send_failed,
                            ),
                        )
                    }
                }
        }
    }

    private suspend fun scheduleReminderIfPresent(text: String): String? {
        val parsed = ReminderParser.parseFromUserMessage(text) ?: return null
        return scheduleParsedReminder(parsed.message, parsed.triggerAtMillis)
    }

    private suspend fun scheduleReminderFromAiReply(reply: String): String? {
        val parsed = ReminderParser.parseFromAssistantReply(reply) ?: return null
        return scheduleParsedReminder(parsed.message, parsed.triggerAtMillis)
    }

    private suspend fun scheduleParsedReminder(message: String, triggerAtMillis: Long): String? {
        val app = getApplication<Application>()
        if (!ReminderPermissionHelper.hasNotificationPermission(app)) {
            return app.getString(R.string.reminder_need_notification_permission)
        }

        val reminder = reminderRepository.schedule(
            com.hoshina.assistant.reminder.ParsedReminder(
                triggerAtMillis = triggerAtMillis,
                message = message,
            ),
        )
        return app.getString(
            R.string.reminder_scheduled,
            DeviceTimeProvider.formatTimeOnly(reminder.triggerAtMillis),
            reminder.message,
        )
    }

    fun sendVoiceMessage(content: String) {
        sendMessage(content, speakReply = true, voiceMode = true)
    }

    fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearMessages()
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    storedMessageCount = 0,
                    error = null,
                    isLoading = false,
                    isListening = false,
                    isAssistantSpeaking = false,
                    activeAgents = emptyList(),
                    activeAgentMode = null,
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_USER_ID = 1
    }
}
