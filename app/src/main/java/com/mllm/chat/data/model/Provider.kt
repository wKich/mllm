package com.mllm.chat.data.model

data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val availableModels: List<String> = emptyList(),
    val lastFetchedModels: Long = 0L
) {
    fun toApiConfig(
        model: String = availableModels.firstOrNull() ?: "gpt-4",
        systemPrompt: String = "",
        temperature: Float? = null,
        maxTokens: Int? = null
    ): ApiConfig {
        return ApiConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
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
