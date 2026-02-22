package com.mllm.chat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mllm.chat.data.model.Provider

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val selectedModel: String,
    val systemPrompt: String,
    val temperature: Float?,
    val maxTokens: Int?,
    val availableModelsJson: String,
    val lastFetchedModels: Long,
    val isActive: Boolean
) {
    fun toProvider(): Provider {
        val models: List<String> = try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(availableModelsJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return Provider(
            id = id,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = selectedModel,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            availableModels = models,
            lastFetchedModels = lastFetchedModels
        )
    }

    companion object {
        private val gson = Gson()

        fun fromProvider(provider: Provider, isActive: Boolean): ProviderEntity = ProviderEntity(
            id = provider.id,
            name = provider.name,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            selectedModel = provider.selectedModel,
            systemPrompt = provider.systemPrompt,
            temperature = provider.temperature,
            maxTokens = provider.maxTokens,
            availableModelsJson = gson.toJson(provider.availableModels),
            lastFetchedModels = provider.lastFetchedModels,
            isActive = isActive
        )
    }
}
