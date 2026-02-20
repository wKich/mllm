package com.mllm.chat.data.model

data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val selectedModel: String = "",
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val availableModels: List<String> = emptyList(),
    val lastFetchedModels: Long = 0L
) {
    fun toApiConfig(): ApiConfig {
        return ApiConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = selectedModel.ifBlank { availableModels.firstOrNull() ?: "gpt-4" },
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            providerName = name
        )
    }
}

data class ModelInfo(
    val id: String,
    val `object`: String?,
    val created: Long?,
    val ownedBy: String?
)

data class ModelsResponse(
    val `object`: String?,
    val data: List<ModelInfo>
)
