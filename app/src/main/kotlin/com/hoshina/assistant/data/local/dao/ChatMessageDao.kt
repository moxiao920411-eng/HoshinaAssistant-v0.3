package com.hoshina.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hoshina.assistant.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int

    @Query(
        "DELETE FROM chat_messages WHERE id IN (" +
            "SELECT id FROM chat_messages ORDER BY timestamp ASC LIMIT :count" +
            ")",
    )
    suspend fun deleteOldest(count: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
