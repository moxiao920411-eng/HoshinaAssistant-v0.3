package com.hoshina.assistant.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hoshina.assistant.HoshinaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CompanionMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        CompanionNotificationHelper.showRandomMessage(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as HoshinaApplication
                CompanionMessageScheduler(context, app.settingsRepository).scheduleNext()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
