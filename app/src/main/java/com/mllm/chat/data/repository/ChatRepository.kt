package com.mllm.chat.data.repository

import com.mllm.chat.data.local.ConversationDao
import com.mllm.chat.data.local.MessageDao
import com.mllm.chat.data.local.SecurePreferences
import com.mllm.chat.data.model.*
import com.mllm.chat.data.remote.ApiResult
import com.mllm.chat.data.remote.OpenAIApiClient
import com.mllm.chat.data.remote.StreamEvent
import com.mllm.chat.data.remote.WebSearchClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val securePreferences: SecurePreferences,
    private val apiClient: OpenAIApiClient,
    private val webSearchClient: WebSearchClient
) {
    private val gson = Gson()

    // API Configuration
    fun getApiConfig(): ApiConfig = securePreferences.getApiConfig()

    suspend fun saveApiConfig(config: ApiConfig) =
        withContext(Dispatchers.IO) { securePreferences.saveApiConfig(config) }

    suspend fun testConnection(config: ApiConfig): ApiResult<String> =
        apiClient.testConnection(config)

    suspend fun fetchModels(config: ApiConfig): ApiResult<List<String>> =
        apiClient.fetchModels(config)

    // Web search configuration
    fun getWebSearchEnabled(): Boolean = securePreferences.getWebSearchEnabled()
    fun getWebSearchApiKey(): String = securePreferences.getWebSearchApiKey()
    fun getWebSearchProvider(): String = securePreferences.getWebSearchProvider()
    fun saveWebSearchConfig(enabled: Boolean, apiKey: String, provider: String) =
        securePreferences.saveWebSearchConfig(enabled, apiKey, provider)

    // Provider management
    fun getProviders(): List<com.mllm.chat.data.model.Provider> =
        securePreferences.getProviders()

    suspend fun saveProvider(provider: com.mllm.chat.data.model.Provider) =
        withContext(Dispatchers.IO) { securePreferences.addProvider(provider) }

    suspend fun deleteProvider(providerId: String) =
        withContext(Dispatchers.IO) { securePreferences.deleteProvider(providerId) }

    suspend fun setActiveProvider(providerId: String) =
        withContext(Dispatchers.IO) { securePreferences.setActiveProviderId(providerId) }

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

    // Build the web search tool definition for the OpenAI API
    private fun buildWebSearchTool(): Tool = Tool(
        function = FunctionDefinition(
            name = "web_search",
            description = "Search the web for current information. Use this when you need up-to-date facts, news, or information not in your training data.",
            parameters = FunctionParameters(
                properties = mapOf(
                    "query" to ParameterProperty(
                        type = "string",
                        description = "The search query to look up (max 400 characters)"
                    )
                ),
                required = listOf("query")
            )
        )
    )

    // Data class representing expected web_search tool arguments
    private data class WebSearchArguments(
        val query: String?
    )

    // Parse the web_search arguments JSON to extract the query
    private fun parseSearchQuery(arguments: String): String? {
        return try {
            val parsed = gson.fromJson(arguments, WebSearchArguments::class.java)
            parsed?.query?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    // Chat completion with streaming and tool call support
    fun streamChatCompletion(messages: List<Message>): Flow<StreamEvent> {
        val config = getApiConfig()
        val webSearchEnabled = getWebSearchEnabled()
        val webSearchApiKey = getWebSearchApiKey()
        val webSearchProvider = getWebSearchProvider()

        if (!webSearchEnabled || webSearchApiKey.isBlank()) {
            // No tool support - use plain streaming
            return apiClient.streamChatCompletion(config, messages)
        }

        val tools = listOf(buildWebSearchTool())
        val initialChatMessages = messages.map { ChatMessage(role = it.role, content = it.content) }

        return channelFlow {
            // Maintain conversation context including tool call messages
            val conversationMessages = initialChatMessages.toMutableList()

            var continueLoop = true
            var iterations = 0
            while (continueLoop && iterations < MAX_TOOL_CALL_ITERATIONS) {
                continueLoop = false
                iterations++
                var hasError = false
                val pendingToolCalls = mutableListOf<AssistantToolCall>()
                val assistantContentBuilder = StringBuilder()

                apiClient.streamChatCompletionWithMessages(config, conversationMessages, tools)
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Content -> {
                                assistantContentBuilder.append(event.text)
                                send(event)
                            }
                            is StreamEvent.Reasoning -> send(event)
                            is StreamEvent.Error -> {
                                hasError = true
                                send(event)
                            }
                            is StreamEvent.ToolCallRequested -> {
                                pendingToolCalls.add(
                                    AssistantToolCall(
                                        id = event.id,
                                        function = AssistantToolCallFunction(
                                            name = event.name,
                                            arguments = event.arguments
                                        )
                                    )
                                )
                            }
                            StreamEvent.Done, StreamEvent.WebSearchStarted -> { /* handled below */ }
                        }
                    }

                if (hasError) break

                if (pendingToolCalls.isNotEmpty()) {
                    // Add assistant message with tool calls to context
                    conversationMessages.add(
                        ChatMessage(
                            role = "assistant",
                            content = assistantContentBuilder.takeIf { it.isNotEmpty() }?.toString(),
                            toolCalls = pendingToolCalls
                        )
                    )

                    // Execute each tool call and add results
                    for (toolCall in pendingToolCalls) {
                        if (toolCall.function.name == "web_search") {
                            val query = parseSearchQuery(toolCall.function.arguments)
                                ?: toolCall.function.arguments
                            send(StreamEvent.WebSearchStarted)
                            ensureActive()
                            val result = try {
                                webSearchClient.search(query, webSearchApiKey, webSearchProvider)
                            } catch (e: Exception) {
                                val errorMessage = "Web search failed: ${e.message ?: "Unknown error"}"
                                send(StreamEvent.Error(errorMessage))
                                hasError = true
                                break
                            }
                            ensureActive()
                            conversationMessages.add(
                                ChatMessage(
                                    role = "tool",
                                    content = result,
                                    toolCallId = toolCall.id
                                )
                            )
                        }
                    }

                    if (!hasError) {
                        // Continue the loop to get the final response
                        continueLoop = true
                    }
                }
            }

            send(StreamEvent.Done)
        }
    }

    companion object {
        private const val MAX_TOOL_CALL_ITERATIONS = 5
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
