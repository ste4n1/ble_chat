package com.ste4n1.blechat.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ChatRepository(private val dao: ChatDao, private val context: Context) {

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

    suspend fun updateSendingStatus(messageId: String, status: String) {
        dao.updateSendingStatus(messageId, status)
    }

    suspend fun deleteMessage(messageId: String) {
        dao.deleteMessage(messageId)
    }

    suspend fun deleteMessageInConversation(
        messageId: String,
        conversationId: String,
        senderIds: List<String>
    ) {
        dao.deleteMessageInConversation(messageId, conversationId, senderIds)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        val paths = dao.getFilePaths()
        dao.clearMessages()
        dao.clearConversations()
        val roots = listOf("sent", "received").mapNotNull { name ->
            runCatching { File(context.filesDir, name).canonicalPath }.getOrNull()
        }
        paths.forEach { path ->
            val canonical = runCatching { File(path).canonicalPath }.getOrNull() ?: return@forEach
            if (roots.any { canonical.startsWith(it + File.separator) }) {
                runCatching { File(path).delete() }
            }
        }
    }
}
