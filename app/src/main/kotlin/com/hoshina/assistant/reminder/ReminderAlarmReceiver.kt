package com.hoshina.assistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hoshina.assistant.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val message = intent.getStringExtra(EXTRA_REMINDER_MESSAGE) ?: return

        ReminderNotificationHelper.showReminderNotification(context, reminderId, message)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context).reminderDao().deleteById(reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_MESSAGE = "extra_reminder_message"
    }
}
