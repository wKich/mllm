package com.mllm.chat.data.remote

import com.google.gson.Gson
import com.mllm.chat.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

sealed class StreamEvent {
    data class Content(val text: String) : StreamEvent()
    data class Reasoning(val text: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data class ToolCallRequested(val id: String, val name: String, val arguments: String) : StreamEvent()
    object WebSearchStarted : StreamEvent()
    object Done : StreamEvent()
}

@Singleton
class OpenAIApiClient @Inject constructor() {
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getErrorMessage(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "Connection timed out. Please check your internet connection and try again."
            is UnknownHostException -> "Cannot resolve server address. Please check your Base URL and internet connection."
            is SSLHandshakeException -> "SSL/TLS handshake failed. The server's certificate may be invalid or untrusted."
            is SSLException -> "SSL/TLS error: ${e.message ?: "Secure connection failed"}"
            is java.net.ConnectException -> "Connection refused. Please verify the server address and port."
            is IOException -> "Network error: ${e.message ?: "Connection failed"}"
            else -> {
                val message = e.message
                if (message.isNullOrBlank()) {
                    "Unexpected error (${e.javaClass.simpleName}). Please try again."
                } else {
                    "Error: $message"
                }
            }
        }
    }

    suspend fun testConnection(config: ApiConfig): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val request = ChatCompletionRequest(
                model = config.model,
                messages = listOf(ChatMessage("user", "Say 'Connection successful!' in exactly those words.")),
                stream = false,
                temperature = 0.1f,
                maxTokens = 20
            )

            val requestBody = gson.toJson(request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("${config.normalizedBaseUrl()}/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            response.use {
                val responseBody = it.body?.string()
                when {
                    it.isSuccessful -> {
                        val content = try {
                            responseBody?.let { body ->
                                val completionResponse = gson.fromJson(body, ChatCompletionResponse::class.java)
                                completionResponse.choices?.firstOrNull()?.message?.content
                            }
                        } catch (e: Exception) {
                            null
                        }
                        ApiResult.Success(content ?: "Connection successful!")
                    }
                    it.code == 401 -> {
                        ApiResult.Error("Authentication failed. Please check your API key.", 401)
                    }
                    it.code == 404 -> {
                        ApiResult.Error("Model not found or invalid endpoint.", 404)
                    }
                    it.code == 429 -> {
                        ApiResult.Error("Rate limit exceeded. Please try again later.", 429)
                    }
                    else -> {
                        val errorResponse = try {
                            gson.fromJson(responseBody, ErrorResponse::class.java)
                        } catch (e: Exception) { null }
                        val errorMessage = errorResponse?.error?.message ?: "API error: ${it.code}"
                        ApiResult.Error(errorMessage, it.code)
                    }
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(getErrorMessage(e))
        }
    }

    fun streamChatCompletion(
        config: ApiConfig,
        messages: List<Message>
    ): Flow<StreamEvent> = streamChatCompletionWithMessages(
        config,
        messages.map { ChatMessage(role = it.role, content = it.content) }
    )

    fun streamChatCompletionWithMessages(
        config: ApiConfig,
        chatMessages: List<ChatMessage>,
        tools: List<Tool>? = null
    ): Flow<StreamEvent> = callbackFlow {
        val allMessages = mutableListOf<ChatMessage>()

        // Add system prompt if configured
        if (config.systemPrompt.isNotBlank()) {
            allMessages.add(ChatMessage("system", config.systemPrompt))
        }

        allMessages.addAll(chatMessages)

        val request = ChatCompletionRequest(
            model = config.model,
            messages = allMessages,
            stream = true,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            tools = tools?.takeIf { it.isNotEmpty() },
            toolChoice = if (!tools.isNullOrEmpty()) "auto" else null
        )

        val requestBody = gson.toJson(request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("${config.normalizedBaseUrl()}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody)
            .build()

        val call = client.newCall(httpRequest)

        // Accumulate tool call arguments across streaming chunks
        val toolCallIds = mutableMapOf<Int, String>()
        val toolCallNames = mutableMapOf<Int, String>()
        val toolCallArgs = mutableMapOf<Int, StringBuilder>()

        try {
            val response = call.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val errorMessage = when (response.code) {
                    401 -> "Authentication failed. Please check your API key."
                    404 -> "Model not found or invalid endpoint."
                    429 -> "Rate limit exceeded. Please try again later."
                    else -> {
                        val errorResponse = try {
                            gson.fromJson(errorBody, ErrorResponse::class.java)
                        } catch (e: Exception) { null }
                        errorResponse?.error?.message ?: "API error: ${response.code}"
                    }
                }
                trySend(StreamEvent.Error(errorMessage))
                close()
                return@callbackFlow
            }

            val reader = response.body?.byteStream()?.bufferedReader()

            reader?.use { bufferedReader ->
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue

                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.removePrefix("data: ").trim()

                        if (data == "[DONE]") {
                            trySend(StreamEvent.Done)
                            break
                        }

                        try {
                            val chunk = gson.fromJson(data, ChatCompletionChunk::class.java)
                            val choice = chunk.choices?.firstOrNull()
                            val delta = choice?.delta

                            // Handle regular content
                            val content = delta?.content
                            if (!content.isNullOrEmpty()) {
                                trySend(StreamEvent.Content(content))
                            }

                            // Handle reasoning content
                            val reasoningContent = delta?.reasoningContent
                            if (!reasoningContent.isNullOrEmpty()) {
                                trySend(StreamEvent.Reasoning(reasoningContent))
                            }

                            // Accumulate tool call deltas
                            delta?.toolCalls?.forEach { toolCallDelta ->
                                val index = toolCallDelta.index ?: 0
                                toolCallDelta.id?.takeIf { it.isNotEmpty() }?.let { id ->
                                    toolCallIds[index] = id
                                }
                                toolCallDelta.function?.name?.takeIf { it.isNotEmpty() }?.let { name ->
                                    toolCallNames[index] = name
                                }
                                toolCallDelta.function?.arguments?.takeIf { it.isNotEmpty() }?.let { args ->
                                    toolCallArgs.getOrPut(index) { StringBuilder() }.append(args)
                                }
                            }

                            // Check for finish reason
                            val finishReason = choice?.finishReason
                            if (finishReason == "tool_calls") {
                                if (toolCallIds.isEmpty()) {
                                    trySend(StreamEvent.Error("Received tool_calls finish but no tool calls were accumulated"))
                                } else {
                                    var emitError = false
                                    for (index in toolCallIds.keys.sorted()) {
                                        val id = toolCallIds[index]
                                        val name = toolCallNames[index]
                                        if (id == null || name == null) {
                                            emitError = true
                                            break
                                        }
                                        val args = toolCallArgs[index]?.toString() ?: "{}"
                                        trySend(StreamEvent.ToolCallRequested(id, name, args))
                                    }
                                    if (emitError) {
                                        trySend(StreamEvent.Error("One or more tool calls in the stream were incomplete"))
                                    }
                                }
                                break
                            } else if (finishReason != null && finishReason != "null") {
                                trySend(StreamEvent.Done)
                                break
                            }
                        } catch (e: Exception) {
                            // Skip malformed JSON lines
                        }
                    }
                }
            }

            close()

        } catch (e: Exception) {
            trySend(StreamEvent.Error(getErrorMessage(e)))
            close()
        }

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchModels(config: ApiConfig): ApiResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("${config.normalizedBaseUrl()}/models")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            when {
                response.isSuccessful && responseBody != null -> {
                    try {
                        val modelsResponse = gson.fromJson(responseBody, com.mllm.chat.data.model.ModelsResponse::class.java)
                        val models = modelsResponse.data.map { it.id }.sorted()
                        ApiResult.Success(models)
                    } catch (e: Exception) {
                        ApiResult.Error("Failed to parse models response: ${e.message}")
                    }
                }
                response.code == 401 -> {
                    ApiResult.Error("Authentication failed. Please check your API key.", 401)
                }
                response.code == 404 -> {
                    ApiResult.Error("Models endpoint not found.", 404)
                }
                else -> {
                    val errorResponse = try {
                        gson.fromJson(responseBody, ErrorResponse::class.java)
                    } catch (e: Exception) { null }
                    val errorMessage = errorResponse?.error?.message ?: "API error: ${response.code}"
                    ApiResult.Error(errorMessage, response.code)
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(getErrorMessage(e))
        }
    }
}
