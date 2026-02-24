package com.mllm.chat.data.model

import org.junit.Assert.*
import org.junit.Test

class ProviderTest {

    private fun buildProvider(
        id: String = "test-id",
        name: String = "Test Provider",
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-test-key",
        selectedModel: String = "gpt-4",
        systemPrompt: String = "You are a helpful assistant.",
        temperature: Float? = 0.8f,
        maxTokens: Int? = 2048,
        availableModels: List<String> = listOf("gpt-4", "gpt-3.5-turbo"),
        lastFetchedModels: Long = 1000L
    ) = Provider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModel = selectedModel,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        availableModels = availableModels,
        lastFetchedModels = lastFetchedModels
    )

    // --- toApiConfig field mapping ---

    @Test
    fun `toApiConfig maps baseUrl correctly`() {
        val provider = buildProvider(baseUrl = "https://openrouter.ai/api/v1")
        assertEquals("https://openrouter.ai/api/v1", provider.toApiConfig().baseUrl)
    }

    @Test
    fun `toApiConfig maps apiKey correctly`() {
        val provider = buildProvider(apiKey = "sk-or-v1-abc123")
        assertEquals("sk-or-v1-abc123", provider.toApiConfig().apiKey)
    }

    @Test
    fun `toApiConfig maps systemPrompt correctly`() {
        val provider = buildProvider(systemPrompt = "Always respond concisely.")
        assertEquals("Always respond concisely.", provider.toApiConfig().systemPrompt)
    }

    @Test
    fun `toApiConfig maps providerName from provider name`() {
        val provider = buildProvider(name = "My OpenAI")
        assertEquals("My OpenAI", provider.toApiConfig().providerName)
    }

    @Test
    fun `toApiConfig maps temperature when set`() {
        val provider = buildProvider(temperature = 0.5f)
        assertEquals(0.5f, provider.toApiConfig().temperature)
    }

    @Test
    fun `toApiConfig passes null temperature through`() {
        val provider = buildProvider(temperature = null)
        assertNull(provider.toApiConfig().temperature)
    }

    @Test
    fun `toApiConfig maps maxTokens when set`() {
        val provider = buildProvider(maxTokens = 4096)
        assertEquals(4096, provider.toApiConfig().maxTokens)
    }

    @Test
    fun `toApiConfig passes null maxTokens through`() {
        val provider = buildProvider(maxTokens = null)
        assertNull(provider.toApiConfig().maxTokens)
    }

    // --- model selection fallback logic ---

    @Test
    fun `toApiConfig uses selectedModel when it is not blank`() {
        val provider = buildProvider(
            selectedModel = "gpt-4-turbo",
            availableModels = listOf("gpt-4", "gpt-3.5-turbo")
        )
        assertEquals("gpt-4-turbo", provider.toApiConfig().model)
    }

    @Test
    fun `toApiConfig falls back to first available model when selectedModel is blank`() {
        val provider = buildProvider(
            selectedModel = "",
            availableModels = listOf("llama3", "mistral")
        )
        assertEquals("llama3", provider.toApiConfig().model)
    }

    @Test
    fun `toApiConfig falls back to gpt-4 when selectedModel is blank and availableModels is empty`() {
        val provider = buildProvider(
            selectedModel = "",
            availableModels = emptyList()
        )
        assertEquals("gpt-4", provider.toApiConfig().model)
    }

    @Test
    fun `toApiConfig uses selectedModel even when availableModels has other options`() {
        val provider = buildProvider(
            selectedModel = "claude-3-opus",
            availableModels = listOf("gpt-4", "gpt-3.5-turbo", "claude-3-opus")
        )
        assertEquals("claude-3-opus", provider.toApiConfig().model)
    }

    @Test
    fun `toApiConfig with blank selectedModel uses first of multiple available models`() {
        val provider = buildProvider(
            selectedModel = "",
            availableModels = listOf("model-a", "model-b", "model-c")
        )
        assertEquals("model-a", provider.toApiConfig().model)
    }

    // --- default field values ---

    @Test
    fun `default selectedModel is empty string`() {
        val provider = Provider(id = "id", name = "n", baseUrl = "http://x", apiKey = "k")
        assertEquals("", provider.selectedModel)
    }

    @Test
    fun `default systemPrompt is empty string`() {
        val provider = Provider(id = "id", name = "n", baseUrl = "http://x", apiKey = "k")
        assertEquals("", provider.systemPrompt)
    }

    @Test
    fun `default temperature is null`() {
        val provider = Provider(id = "id", name = "n", baseUrl = "http://x", apiKey = "k")
        assertNull(provider.temperature)
    }

    @Test
    fun `default maxTokens is null`() {
        val provider = Provider(id = "id", name = "n", baseUrl = "http://x", apiKey = "k")
        assertNull(provider.maxTokens)
    }

    @Test
    fun `default availableModels is empty list`() {
        val provider = Provider(id = "id", name = "n", baseUrl = "http://x", apiKey = "k")
        assertTrue(provider.availableModels.isEmpty())
    }

    @Test
    fun `default lastFetchedModels is zero`() {
        val provider = Provider(id = "id", name = "n", baseUrl = "http://x", apiKey = "k")
        assertEquals(0L, provider.lastFetchedModels)
    }

    // --- complete conversion scenario ---

    @Test
    fun `toApiConfig produces a fully configured ApiConfig for a complete provider`() {
        val provider = buildProvider(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-complete",
            selectedModel = "gpt-4o",
            systemPrompt = "Be brief.",
            temperature = 0.3f,
            maxTokens = 1024
        )
        val config = provider.toApiConfig()
        assertTrue(config.isConfigured)
        assertEquals("https://api.openai.com/v1", config.baseUrl)
        assertEquals("sk-complete", config.apiKey)
        assertEquals("gpt-4o", config.model)
        assertEquals("Be brief.", config.systemPrompt)
        assertEquals(0.3f, config.temperature)
        assertEquals(1024, config.maxTokens)
    }
}
