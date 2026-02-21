package com.mllm.chat.data.model

import com.google.gson.annotations.SerializedName

// Tool definition models
data class Tool(
    val type: String = "function",
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: FunctionParameters
)

data class FunctionParameters(
    val type: String = "object",
    val properties: Map<String, ParameterProperty>,
    val required: List<String>
)

data class ParameterProperty(
    val type: String,
    val description: String
)

// Tool call models (for assistant messages and streaming)
data class AssistantToolCall(
    val id: String,
    val type: String = "function",
    val function: AssistantToolCallFunction
)

data class AssistantToolCallFunction(
    val name: String,
    val arguments: String
)

// Request models
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val tools: List<Tool>? = null,
    @SerializedName("tool_choice")
    val toolChoice: String? = null
)

data class ChatMessage(
    val role: String,
    val content: String? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<AssistantToolCall>? = null,
    val name: String? = null
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

// Streaming tool call delta models
data class ToolCallDelta(
    val index: Int?,
    val id: String?,
    val type: String?,
    val function: ToolCallFunctionDelta?
)

data class ToolCallFunctionDelta(
    val name: String?,
    val arguments: String?
)

data class Delta(
    val role: String?,
    val content: String?,
    @SerializedName("reasoning_content")
    val reasoningContent: String?,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCallDelta>?
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
