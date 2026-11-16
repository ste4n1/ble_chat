package com.ste4n1.blechat.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatDao) {

    fun conversations(): Flow<List<ConversationEntity>> = dao.observeConversations()

    fun messages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    suspend fun ensureConversation(id: String, name: String) {
        val existing = dao.getConversation(id)
        val now = System.currentTimeMillis()
        when {
            existing == null -> dao.upsertConversation(
                ConversationEntity(id = id, name = name.ifBlank { id }, updatedAt = now)
            )
            existing.name != name && name.isNotBlank() ->
                dao.upsertConversation(existing.copy(name = name))
        }
    }

    suspend fun insertMessage(message: MessageEntity) {
        dao.insertMessage(message)
        dao.touchConversation(message.conversationId, message.timestamp)
    }

    suspend fun updateStatus(messageId: String, status: String) {
        dao.updateStatus(messageId, status)
    }

    suspend fun deleteMessage(messageId: String) {
        dao.deleteMessage(messageId)
    }

    suspend fun clearHistory() {
        dao.clearMessages()
        dao.clearConversations()
    }
}
