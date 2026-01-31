package com.mllm.chat.data.local

import androidx.room.*
import com.mllm.chat.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversationSync(conversationId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Update
    suspend fun updateMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("UPDATE messages SET content = :content, isStreaming = :isStreaming WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String, isStreaming: Boolean)

    @Query("UPDATE messages SET content = :content, reasoningContent = :reasoningContent, isStreaming = :isStreaming WHERE id = :id")
    suspend fun updateMessageContentWithReasoning(id: Long, content: String, reasoningContent: String?, isStreaming: Boolean)

    @Query("UPDATE messages SET isError = :isError WHERE id = :id")
    suspend fun updateMessageError(id: Long, isError: Boolean)
}
