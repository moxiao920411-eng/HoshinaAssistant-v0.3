package com.hoshina.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hoshina.assistant.data.local.dao.ChatMessageDao
import com.hoshina.assistant.data.local.dao.ReminderDao
import com.hoshina.assistant.data.local.entity.ChatMessageEntity
import com.hoshina.assistant.data.local.entity.ReminderEntity

@Database(
    entities = [ChatMessageEntity::class, ReminderEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        id TEXT NOT NULL PRIMARY KEY,
                        message TEXT NOT NULL,
                        trigger_at_millis INTEGER NOT NULL,
                        created_at_millis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hoshina_assistant.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
