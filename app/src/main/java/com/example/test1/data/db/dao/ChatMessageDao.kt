package com.example.test1.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.test1.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE date = :date ORDER BY id ASC")
    fun getMessagesForDate(date: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ChatMessageEntity)

    @Query("UPDATE chat_messages SET text = :text, macroResultJson = :macroResultJson, foodEntryId = :foodEntryId WHERE id = :id")
    suspend fun updateMessage(id: Long, text: String, macroResultJson: String?, foodEntryId: Long?)

    @Query("DELETE FROM chat_messages WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
