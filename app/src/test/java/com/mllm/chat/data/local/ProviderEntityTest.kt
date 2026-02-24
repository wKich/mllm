package com.mllm.chat.data.local

import com.mllm.chat.data.model.Provider
import org.junit.Assert.*
import org.junit.Test

class ProviderEntityTest {

    private fun buildEntity(
        id: String = "entity-id",
        name: String = "My Provider",
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-key",
        selectedModel: String = "gpt-4",
        systemPrompt: String = "System prompt",
        temperature: Float? = 0.7f,
        maxTokens: Int? = 1024,
        availableModelsJson: String = """["gpt-4","gpt-3.5-turbo"]""",
        lastFetchedModels: Long = 12345L,
        isActive: Boolean = false
    ) = ProviderEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModel = selectedModel,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        availableModelsJson = availableModelsJson,
        lastFetchedModels = lastFetchedModels,
        isActive = isActive
    )

    private fun buildProvider(
        id: String = "p-id",
        name: String = "Provider",
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-key",
        selectedModel: String = "gpt-4",
        systemPrompt: String = "",
        temperature: Float? = null,
        maxTokens: Int? = null,
        availableModels: List<String> = emptyList(),
        lastFetchedModels: Long = 0L
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

    // --- toProvider field mapping ---

    @Test
    fun `toProvider maps id correctly`() {
        assertEquals("entity-id", buildEntity().toProvider().id)
    }

    @Test
    fun `toProvider maps name correctly`() {
        assertEquals("My Provider", buildEntity().toProvider().name)
    }

    @Test
    fun `toProvider maps baseUrl correctly`() {
        assertEquals("https://api.openai.com/v1", buildEntity().toProvider().baseUrl)
    }

    @Test
    fun `toProvider maps apiKey correctly`() {
        assertEquals("sk-key", buildEntity().toProvider().apiKey)
    }

    @Test
    fun `toProvider maps selectedModel correctly`() {
        assertEquals("gpt-4", buildEntity().toProvider().selectedModel)
    }

    @Test
    fun `toProvider maps systemPrompt correctly`() {
        assertEquals("System prompt", buildEntity().toProvider().systemPrompt)
    }

    @Test
    fun `toProvider maps temperature correctly`() {
        assertEquals(0.7f, buildEntity(temperature = 0.7f).toProvider().temperature)
    }

    @Test
    fun `toProvider maps null temperature correctly`() {
        assertNull(buildEntity(temperature = null).toProvider().temperature)
    }

    @Test
    fun `toProvider maps maxTokens correctly`() {
        assertEquals(1024, buildEntity(maxTokens = 1024).toProvider().maxTokens)
    }

    @Test
    fun `toProvider maps null maxTokens correctly`() {
        assertNull(buildEntity(maxTokens = null).toProvider().maxTokens)
    }

    @Test
    fun `toProvider maps lastFetchedModels correctly`() {
        assertEquals(12345L, buildEntity(lastFetchedModels = 12345L).toProvider().lastFetchedModels)
    }

    // --- toProvider availableModels JSON deserialization ---

    @Test
    fun `toProvider deserializes two-item models JSON array`() {
        val entity = buildEntity(availableModelsJson = """["gpt-4","gpt-3.5-turbo"]""")
        assertEquals(listOf("gpt-4", "gpt-3.5-turbo"), entity.toProvider().availableModels)
    }

    @Test
    fun `toProvider deserializes three-item models JSON array`() {
        val entity = buildEntity(availableModelsJson = """["gpt-4","gpt-3.5-turbo","llama3"]""")
        assertEquals(listOf("gpt-4", "gpt-3.5-turbo", "llama3"), entity.toProvider().availableModels)
    }

    @Test
    fun `toProvider deserializes empty JSON array to empty list`() {
        val entity = buildEntity(availableModelsJson = "[]")
        assertTrue(entity.toProvider().availableModels.isEmpty())
    }

    @Test
    fun `toProvider handles empty availableModelsJson string gracefully`() {
        val entity = buildEntity(availableModelsJson = "")
        assertTrue(entity.toProvider().availableModels.isEmpty())
    }

    @Test
    fun `toProvider handles invalid JSON gracefully with empty list`() {
        val entity = buildEntity(availableModelsJson = "not-valid-json{[[[")
        assertTrue(entity.toProvider().availableModels.isEmpty())
    }

    @Test
    fun `toProvider handles JSON null gracefully`() {
        val entity = buildEntity(availableModelsJson = "null")
        assertTrue(entity.toProvider().availableModels.isEmpty())
    }

    @Test
    fun `toProvider handles malformed JSON object gracefully`() {
        val entity = buildEntity(availableModelsJson = """{"key": "value"}""")
        // A JSON object is not a List<String>, Gson returns null for wrong type
        assertTrue(entity.toProvider().availableModels.isEmpty())
    }

    // --- fromProvider field mapping ---

    @Test
    fun `fromProvider maps id correctly`() {
        val provider = buildProvider(id = "my-uuid")
        assertEquals("my-uuid", ProviderEntity.fromProvider(provider, false).id)
    }

    @Test
    fun `fromProvider maps name correctly`() {
        val provider = buildProvider(name = "OpenAI")
        assertEquals("OpenAI", ProviderEntity.fromProvider(provider, false).name)
    }

    @Test
    fun `fromProvider maps baseUrl correctly`() {
        val provider = buildProvider(baseUrl = "http://localhost:11434/v1")
        assertEquals("http://localhost:11434/v1", ProviderEntity.fromProvider(provider, false).baseUrl)
    }

    @Test
    fun `fromProvider maps apiKey correctly`() {
        val provider = buildProvider(apiKey = "sk-secret")
        assertEquals("sk-secret", ProviderEntity.fromProvider(provider, false).apiKey)
    }

    @Test
    fun `fromProvider serializes non-empty available models to JSON array`() {
        val provider = buildProvider(availableModels = listOf("gpt-4", "gpt-3.5-turbo"))
        val entity = ProviderEntity.fromProvider(provider, false)
        assertTrue(entity.availableModelsJson.contains("gpt-4"))
        assertTrue(entity.availableModelsJson.contains("gpt-3.5-turbo"))
        assertTrue(entity.availableModelsJson.startsWith("["))
        assertTrue(entity.availableModelsJson.endsWith("]"))
    }

    @Test
    fun `fromProvider serializes empty available models to empty JSON array`() {
        val provider = buildProvider(availableModels = emptyList())
        val entity = ProviderEntity.fromProvider(provider, false)
        assertEquals("[]", entity.availableModelsJson)
    }

    @Test
    fun `fromProvider sets isActive true correctly`() {
        val provider = buildProvider()
        assertTrue(ProviderEntity.fromProvider(provider, isActive = true).isActive)
    }

    @Test
    fun `fromProvider sets isActive false correctly`() {
        val provider = buildProvider()
        assertFalse(ProviderEntity.fromProvider(provider, isActive = false).isActive)
    }

    @Test
    fun `fromProvider maps null temperature correctly`() {
        val provider = buildProvider(temperature = null)
        assertNull(ProviderEntity.fromProvider(provider, false).temperature)
    }

    @Test
    fun `fromProvider maps set temperature correctly`() {
        val provider = buildProvider(temperature = 0.9f)
        assertEquals(0.9f, ProviderEntity.fromProvider(provider, false).temperature)
    }

    @Test
    fun `fromProvider maps null maxTokens correctly`() {
        val provider = buildProvider(maxTokens = null)
        assertNull(ProviderEntity.fromProvider(provider, false).maxTokens)
    }

    @Test
    fun `fromProvider maps set maxTokens correctly`() {
        val provider = buildProvider(maxTokens = 2048)
        assertEquals(2048, ProviderEntity.fromProvider(provider, false).maxTokens)
    }

    // --- round-trip: fromProvider → toProvider ---

    @Test
    fun `round trip fromProvider then toProvider preserves all scalar fields`() {
        val original = Provider(
            id = "round-trip-id",
            name = "Round Trip Provider",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-v1-secretkey",
            selectedModel = "meta-llama/llama-3.1-8b-instruct",
            systemPrompt = "Be concise and direct.",
            temperature = 0.5f,
            maxTokens = 512,
            availableModels = listOf("model-a", "model-b", "model-c"),
            lastFetchedModels = 999999L
        )

        val restored = ProviderEntity.fromProvider(original, isActive = true).toProvider()

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.baseUrl, restored.baseUrl)
        assertEquals(original.apiKey, restored.apiKey)
        assertEquals(original.selectedModel, restored.selectedModel)
        assertEquals(original.systemPrompt, restored.systemPrompt)
        assertEquals(original.temperature, restored.temperature)
        assertEquals(original.maxTokens, restored.maxTokens)
        assertEquals(original.availableModels, restored.availableModels)
        assertEquals(original.lastFetchedModels, restored.lastFetchedModels)
    }

    @Test
    fun `round trip preserves provider with null optional fields`() {
        val original = Provider(
            id = "null-fields-id",
            name = "No Optionals",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-abc",
            selectedModel = "gpt-4",
            temperature = null,
            maxTokens = null,
            availableModels = emptyList()
        )

        val restored = ProviderEntity.fromProvider(original, isActive = false).toProvider()

        assertNull(restored.temperature)
        assertNull(restored.maxTokens)
        assertTrue(restored.availableModels.isEmpty())
    }
}
