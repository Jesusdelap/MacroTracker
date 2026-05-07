package com.example.test1.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: Long,
    val date: String,
    val text: String,
    val isUser: Boolean,
    val macroResultJson: String? = null,
    val isImageMessage: Boolean = false,
    val foodEntryId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
