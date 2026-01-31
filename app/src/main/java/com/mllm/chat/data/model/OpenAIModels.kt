package com.mllm.chat.data.model

import com.google.gson.annotations.SerializedName

// Request models
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

// Response models for streaming
data class ChatCompletionChunk(
    val id: String?,
    val `object`: String?,
    val created: Long?,
    val model: String?,
    val choices: List<ChunkChoice>?
)

data class ChunkChoice(
    val index: Int?,
    val delta: Delta?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Delta(
    val role: String?,
    val content: String?,
    @SerializedName("reasoning_content")
    val reasoningContent: String?
)

// Non-streaming response (for connection test)
data class ChatCompletionResponse(
    val id: String?,
    val `object`: String?,
    val created: Long?,
    val model: String?,
    val choices: List<Choice>?,
    val usage: Usage?,
    val error: ApiError?
)

data class Choice(
    val index: Int?,
    val message: ChatMessage?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int?,
    @SerializedName("completion_tokens")
    val completionTokens: Int?,
    @SerializedName("total_tokens")
    val totalTokens: Int?
)

data class ApiError(
    val message: String?,
    val type: String?,
    val code: String?
)

data class ErrorResponse(
    val error: ApiError?
)
