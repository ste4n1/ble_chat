package com.ste4n1.blechat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val isMine: Boolean,
    val type: String,
    val text: String?,
    val filePath: String?,
    val mimeType: String?,
    val fileSize: Long?,
    val status: String,
    val timestamp: Long
)
