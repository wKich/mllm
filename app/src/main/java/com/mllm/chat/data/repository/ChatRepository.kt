package com.mllm.chat.data.repository

import com.mllm.chat.data.local.ConversationDao
import com.mllm.chat.data.local.MessageDao
import com.mllm.chat.data.local.SecurePreferences
import com.mllm.chat.data.model.*
import com.mllm.chat.data.remote.ApiResult
import com.mllm.chat.data.remote.OpenAIApiClient
import com.mllm.chat.data.remote.StreamEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val securePreferences: SecurePreferences,
    private val apiClient: OpenAIApiClient
) {
    // API Configuration
    fun getApiConfig(): ApiConfig = securePreferences.getApiConfig()

    fun saveApiConfig(config: ApiConfig) = securePreferences.saveApiConfig(config)

    suspend fun testConnection(config: ApiConfig): ApiResult<String> =
        apiClient.testConnection(config)

    suspend fun fetchModels(config: ApiConfig): ApiResult<List<String>> =
        apiClient.fetchModels(config)

    // Provider management
    fun getProviders(): List<com.mllm.chat.data.model.Provider> =
        securePreferences.getProviders()

    fun saveProvider(provider: com.mllm.chat.data.model.Provider) =
        securePreferences.addProvider(provider)

    fun deleteProvider(providerId: String) =
        securePreferences.deleteProvider(providerId)

    fun setActiveProvider(providerId: String) =
        securePreferences.setActiveProviderId(providerId)

    fun getActiveProvider(): com.mllm.chat.data.model.Provider? =
        securePreferences.getActiveProvider()

    // Conversations
    fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations()

    suspend fun getConversationById(id: Long): Conversation? =
        conversationDao.getConversationById(id)

    suspend fun createConversation(title: String = "New Chat"): Long {
        val conversation = Conversation(title = title)
        return conversationDao.insertConversation(conversation)
    }

    suspend fun updateConversationTitle(id: Long, title: String) {
        conversationDao.updateTitle(id, title)
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteConversationById(id)
    }

    suspend fun updateConversationTimestamp(id: Long) {
        conversationDao.updateTimestamp(id)
    }

    // Messages
    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesForConversationSync(conversationId: Long): List<Message> =
        messageDao.getMessagesForConversationSync(conversationId)

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        isStreaming: Boolean = false
    ): Long {
        val message = Message(
            conversationId = conversationId,
            role = role,
            content = content,
            isStreaming = isStreaming
        )
        val messageId = messageDao.insertMessage(message)
        conversationDao.updateTimestamp(conversationId)
        return messageId
    }

    suspend fun updateMessageContent(id: Long, content: String, isStreaming: Boolean) {
        messageDao.updateMessageContent(id, content, isStreaming)
    }

    suspend fun updateMessageContentWithReasoning(id: Long, content: String, reasoningContent: String?, isStreaming: Boolean) {
        messageDao.updateMessageContentWithReasoning(id, content, reasoningContent, isStreaming)
    }

    suspend fun updateMessageError(id: Long, isError: Boolean) {
        messageDao.updateMessageError(id, isError)
    }

    suspend fun deleteMessagesForConversation(conversationId: Long) {
        messageDao.deleteMessagesForConversation(conversationId)
    }

    // Chat completion with streaming
    fun streamChatCompletion(messages: List<Message>): Flow<StreamEvent> {
        val config = getApiConfig()
        return apiClient.streamChatCompletion(config, messages)
    }

    // Generate title from first message
    fun generateTitleFromMessage(content: String): String {
        val maxLength = 50
        val trimmed = content.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")

        return if (trimmed.length <= maxLength) {
            trimmed
        } else {
            trimmed.take(maxLength - 3) + "..."
        }
    }
}
