package com.example.test1.data.repository

import com.example.test1.data.db.dao.ChatMessageDao
import com.example.test1.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatMessageRepository(private val dao: ChatMessageDao) {
    fun getMessagesForDate(date: String): Flow<List<ChatMessageEntity>> =
        dao.getMessagesForDate(date)

    suspend fun insert(msg: ChatMessageEntity) = dao.insert(msg)

    suspend fun updateMessage(id: Long, text: String, macroResultJson: String?, foodEntryId: Long?) =
        dao.updateMessage(id, text, macroResultJson, foodEntryId)

    suspend fun deleteOlderThan(cutoffDate: String) = dao.deleteOlderThan(cutoffDate)
}
