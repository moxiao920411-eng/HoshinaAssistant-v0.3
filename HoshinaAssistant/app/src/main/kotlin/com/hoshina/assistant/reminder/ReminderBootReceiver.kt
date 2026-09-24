package com.hoshina.assistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hoshina.assistant.data.local.AppDatabase
import com.hoshina.assistant.data.local.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val scheduler = ReminderScheduler(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).reminderDao()
                val upcoming = dao.getUpcoming(System.currentTimeMillis())
                upcoming.forEach { entity ->
                    scheduler.schedule(entity.toDomain())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
