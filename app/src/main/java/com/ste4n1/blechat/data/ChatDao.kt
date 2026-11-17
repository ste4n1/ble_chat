package com.ste4n1.blechat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY timestamp ASC")
    fun observeMessages(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id AND status = 'SENDING'")
    suspend fun updateSendingStatus(id: String, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query(
        "DELETE FROM messages WHERE id = :id " +
            "AND conversationId = :conversationId AND senderId IN (:senderIds)"
    )
    suspend fun deleteMessageInConversation(
        id: String,
        conversationId: String,
        senderIds: List<String>
    )

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touchConversation(id: String, time: Long)

    @Query("SELECT filePath FROM messages WHERE filePath IS NOT NULL")
    suspend fun getFilePaths(): List<String>

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()
}
