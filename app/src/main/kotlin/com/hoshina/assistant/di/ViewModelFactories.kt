package com.hoshina.assistant.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hoshina.assistant.data.local.SettingsRepository
import com.hoshina.assistant.data.local.UserMemoryRepository
import com.hoshina.assistant.data.repository.ChatRepository
import com.hoshina.assistant.data.repository.ReminderRepository
import com.hoshina.assistant.ui.chat.ChatViewModel
import com.hoshina.assistant.ui.settings.SettingsViewModel

class ChatViewModelFactory(
    private val application: Application,
    private val chatRepository: ChatRepository,
    private val reminderRepository: ReminderRepository,
    private val userMemoryRepository: UserMemoryRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                chatRepository,
                reminderRepository,
                userMemoryRepository,
                application,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

class SettingsViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val userMemoryRepository: UserMemoryRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsRepository, userMemoryRepository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
