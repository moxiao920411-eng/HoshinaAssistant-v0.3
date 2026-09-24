package com.hoshina.assistant

import android.app.Application
import com.hoshina.assistant.data.local.AppDatabase
import com.hoshina.assistant.data.local.ChatLocalDataSource
import com.hoshina.assistant.data.local.SettingsRepository
import com.hoshina.assistant.data.local.SettingsRepositoryImpl
import com.hoshina.assistant.data.local.UserMemoryRepository
import com.hoshina.assistant.data.local.UserMemoryRepositoryImpl
import com.hoshina.assistant.data.repository.ChatRepository
import com.hoshina.assistant.data.repository.ChatRepositoryImpl
import com.hoshina.assistant.data.repository.ReminderRepository
import com.hoshina.assistant.data.repository.ReminderRepositoryImpl
import com.hoshina.assistant.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HoshinaApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var userMemoryRepository: UserMemoryRepository
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var reminderRepository: ReminderRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(applicationContext)
        val chatLocalDataSource = ChatLocalDataSource(database.chatMessageDao())
        val reminderScheduler = ReminderScheduler(applicationContext)

        settingsRepository = SettingsRepositoryImpl(applicationContext)
        userMemoryRepository = UserMemoryRepositoryImpl(applicationContext)
        chatRepository = ChatRepositoryImpl(
            appContext = applicationContext,
            settingsRepository = settingsRepository,
            chatLocalDataSource = chatLocalDataSource,
            userMemoryRepository = userMemoryRepository,
        )
        reminderRepository = ReminderRepositoryImpl(
            reminderDao = database.reminderDao(),
            reminderScheduler = reminderScheduler,
        )

        applicationScope.launch {
            reminderRepository.rescheduleAllUpcoming()
        }
    }
}
