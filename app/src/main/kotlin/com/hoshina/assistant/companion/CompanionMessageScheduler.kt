package com.hoshina.assistant.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hoshina.assistant.data.local.SettingsRepository
import kotlin.random.Random

class CompanionMessageScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun scheduleNext() {
        val delayMillis = resolveDelayMillis()
        val triggerAt = System.currentTimeMillis() + delayMillis
        val pendingIntent = createPendingIntent()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }

    fun cancel() {
        findPendingIntent()?.let(alarmManager::cancel)
    }

    private fun createPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, CompanionMessageReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun findPendingIntent(): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, CompanionMessageReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun resolveDelayMillis(): Long {
        val debugMinMinutes = settingsRepository.getCompanionDebugMinMinutes()
        val debugMaxMinutes = settingsRepository.getCompanionDebugMaxMinutes()
        if (debugMinMinutes > 0 && debugMaxMinutes > 0) {
            val min = minOf(debugMinMinutes, debugMaxMinutes).toLong() * MINUTE_MILLIS
            val max = maxOf(debugMinMinutes, debugMaxMinutes).toLong() * MINUTE_MILLIS
            return Random.nextLong(min, max + 1)
        }
        return Random.nextLong(MIN_DELAY_MILLIS, MAX_DELAY_MILLIS + 1)
    }

    companion object {
        private const val REQUEST_CODE = 1213
        private const val MINUTE_MILLIS = 60L * 1_000L
        private const val HOUR_MILLIS = 60L * 60L * 1_000L
        private const val MIN_DELAY_MILLIS = 2L * HOUR_MILLIS
        private const val MAX_DELAY_MILLIS = 6L * HOUR_MILLIS
    }
}
