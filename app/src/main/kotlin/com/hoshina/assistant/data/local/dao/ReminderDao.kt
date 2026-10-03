package com.hoshina.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hoshina.assistant.data.local.entity.ReminderEntity

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE trigger_at_millis > :now ORDER BY trigger_at_millis ASC")
    suspend fun getUpcoming(now: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReminderEntity?
}
