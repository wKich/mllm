package com.mllm.chat.data.model

data class ApiConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    fun normalizedBaseUrl(): String {
        return baseUrl.trimEnd('/')
    }
}

val DEFAULT_MODELS = listOf(
    "gpt-4",
    "gpt-4-turbo",
    "gpt-4o",
    "gpt-4o-mini",
    "gpt-3.5-turbo",
    "llama3",
    "llama3.1",
    "qwen2.5",
    "mistral",
    "claude-3-opus",
    "claude-3-sonnet"
)
