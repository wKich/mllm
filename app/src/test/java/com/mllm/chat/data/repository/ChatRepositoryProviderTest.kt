package com.mllm.chat.data.repository

import com.mllm.chat.data.local.AppPreferences
import com.mllm.chat.data.local.ConversationDao
import com.mllm.chat.data.local.MessageDao
import com.mllm.chat.data.local.ProviderDao
import com.mllm.chat.data.local.ProviderEntity
import com.mllm.chat.data.model.ApiConfig
import com.mllm.chat.data.model.Provider
import com.mllm.chat.data.remote.OpenAIApiClient
import com.mllm.chat.data.remote.WebSearchClient
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatRepositoryProviderTest {

    private lateinit var providerDao: ProviderDao
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao
    private lateinit var appPreferences: AppPreferences
    private lateinit var apiClient: OpenAIApiClient
    private lateinit var webSearchClient: WebSearchClient
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        providerDao = mockk()
        conversationDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        appPreferences = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        webSearchClient = mockk(relaxed = true)

        repository = ChatRepository(
            conversationDao = conversationDao,
            messageDao = messageDao,
            appPreferences = appPreferences,
            providerDao = providerDao,
            apiClient = apiClient,
            webSearchClient = webSearchClient
        )
    }

    // --- helpers ---

    private fun makeEntity(
        id: String = "id-1",
        name: String = "Provider",
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-key",
        selectedModel: String = "gpt-4",
        isActive: Boolean = false
    ) = ProviderEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModel = selectedModel,
        systemPrompt = "",
        temperature = null,
        maxTokens = null,
        availableModelsJson = "[]",
        lastFetchedModels = 0L,
        isActive = isActive
    )

    private fun makeProvider(
        id: String = "id-1",
        name: String = "Provider",
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-key",
        selectedModel: String = "gpt-4"
    ) = Provider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModel = selectedModel
    )

    // --- getProviders ---

    @Test
    fun `getProviders returns empty list when DAO returns no providers`() = runTest {
        coEvery { providerDao.getAllProviders() } returns emptyList()

        val result = repository.getProviders()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getProviders returns mapped list from DAO`() = runTest {
        val entities = listOf(
            makeEntity(id = "id-1", name = "Provider A"),
            makeEntity(id = "id-2", name = "Provider B")
        )
        coEvery { providerDao.getAllProviders() } returns entities

        val result = repository.getProviders()

        assertEquals(2, result.size)
        assertEquals("id-1", result[0].id)
        assertEquals("Provider A", result[0].name)
        assertEquals("id-2", result[1].id)
        assertEquals("Provider B", result[1].name)
    }

    @Test
    fun `getProviders converts each entity to Provider correctly`() = runTest {
        val entity = makeEntity(
            id = "p-id",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-abc",
            selectedModel = "gpt-4o"
        )
        coEvery { providerDao.getAllProviders() } returns listOf(entity)

        val providers = repository.getProviders()

        assertEquals(1, providers.size)
        with(providers[0]) {
            assertEquals("p-id", id)
            assertEquals("OpenAI", name)
            assertEquals("https://api.openai.com/v1", baseUrl)
            assertEquals("sk-abc", apiKey)
            assertEquals("gpt-4o", selectedModel)
        }
    }

    // --- saveProvider ---

    @Test
    fun `saveProvider preserves isActive=true when updating existing active provider`() = runTest {
        val provider = makeProvider(id = "existing-id")
        val existingEntity = makeEntity(id = "existing-id", isActive = true)

        coEvery { providerDao.getProviderById("existing-id") } returns existingEntity
        coJustRun { providerDao.upsertProvider(any()) }

        repository.saveProvider(provider)

        coVerify {
            providerDao.upsertProvider(
                match { it.id == "existing-id" && it.isActive == true }
            )
        }
    }

    @Test
    fun `saveProvider preserves isActive=false when updating existing inactive provider`() = runTest {
        val provider = makeProvider(id = "existing-id")
        val existingEntity = makeEntity(id = "existing-id", isActive = false)

        coEvery { providerDao.getProviderById("existing-id") } returns existingEntity
        coJustRun { providerDao.upsertProvider(any()) }

        repository.saveProvider(provider)

        coVerify {
            providerDao.upsertProvider(
                match { it.id == "existing-id" && it.isActive == false }
            )
        }
    }

    @Test
    fun `saveProvider inserts with isActive=false for a new provider not found in DAO`() = runTest {
        val provider = makeProvider(id = "new-id")
        coEvery { providerDao.getProviderById("new-id") } returns null
        coJustRun { providerDao.upsertProvider(any()) }

        repository.saveProvider(provider)

        coVerify {
            providerDao.upsertProvider(
                match { it.id == "new-id" && it.isActive == false }
            )
        }
    }

    @Test
    fun `saveProvider stores all provider fields in entity`() = runTest {
        val provider = Provider(
            id = "full-id",
            name = "Full Provider",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-v1-xyz",
            selectedModel = "meta-llama/llama-3.1-8b-instruct",
            systemPrompt = "Be helpful.",
            temperature = 0.6f,
            maxTokens = 2048,
            availableModels = listOf("model-a", "model-b")
        )
        coEvery { providerDao.getProviderById("full-id") } returns null
        coJustRun { providerDao.upsertProvider(any()) }

        repository.saveProvider(provider)

        coVerify {
            providerDao.upsertProvider(
                match { entity ->
                    entity.id == "full-id" &&
                    entity.name == "Full Provider" &&
                    entity.baseUrl == "https://openrouter.ai/api/v1" &&
                    entity.apiKey == "sk-or-v1-xyz" &&
                    entity.selectedModel == "meta-llama/llama-3.1-8b-instruct" &&
                    entity.systemPrompt == "Be helpful." &&
                    entity.temperature == 0.6f &&
                    entity.maxTokens == 2048
                }
            )
        }
    }

    // --- deleteProvider ---

    @Test
    fun `deleteProvider deletes the specified provider from DAO`() = runTest {
        val entity = makeEntity(id = "del-id", isActive = false)
        coEvery { providerDao.getActiveProvider() } returns makeEntity(id = "other-id", isActive = true)
        coJustRun { providerDao.deleteById("del-id") }

        repository.deleteProvider("del-id")

        coVerify { providerDao.deleteById("del-id") }
    }

    @Test
    fun `deleteProvider does not call markActive when deleted provider was not active`() = runTest {
        val activeEntity = makeEntity(id = "active-id", isActive = true)
        coEvery { providerDao.getActiveProvider() } returns activeEntity
        coJustRun { providerDao.deleteById("non-active-id") }

        repository.deleteProvider("non-active-id")

        coVerify(exactly = 0) { providerDao.markActive(any()) }
    }

    @Test
    fun `deleteProvider auto-activates first remaining provider when active provider is deleted`() = runTest {
        val activeEntity = makeEntity(id = "active-id", isActive = true)
        val remainingEntity = makeEntity(id = "remaining-id", isActive = false)

        coEvery { providerDao.getActiveProvider() } returns activeEntity
        coJustRun { providerDao.deleteById("active-id") }
        coEvery { providerDao.getAllProviders() } returns listOf(remainingEntity)
        coEvery { providerDao.markActive("remaining-id") } returns 1

        repository.deleteProvider("active-id")

        coVerify { providerDao.markActive("remaining-id") }
    }

    @Test
    fun `deleteProvider does not call markActive when active provider deleted and no remaining providers`() = runTest {
        val activeEntity = makeEntity(id = "last-id", isActive = true)

        coEvery { providerDao.getActiveProvider() } returns activeEntity
        coJustRun { providerDao.deleteById("last-id") }
        coEvery { providerDao.getAllProviders() } returns emptyList()

        repository.deleteProvider("last-id")

        coVerify(exactly = 0) { providerDao.markActive(any()) }
    }

    // --- setActiveProvider ---

    @Test
    fun `setActiveProvider delegates to providerDao setActiveProvider`() = runTest {
        coJustRun { providerDao.setActiveProvider("target-id") }

        repository.setActiveProvider("target-id")

        coVerify { providerDao.setActiveProvider("target-id") }
    }

    // --- getActiveProvider ---

    @Test
    fun `getActiveProvider returns null when DAO has no active provider`() = runTest {
        coEvery { providerDao.getActiveProvider() } returns null

        val result = repository.getActiveProvider()

        assertNull(result)
    }

    @Test
    fun `getActiveProvider returns mapped Provider when DAO has active entity`() = runTest {
        val activeEntity = makeEntity(
            id = "active-id",
            name = "Active Provider",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-active",
            selectedModel = "gpt-4o",
            isActive = true
        )
        coEvery { providerDao.getActiveProvider() } returns activeEntity

        val result = repository.getActiveProvider()

        assertNotNull(result)
        assertEquals("active-id", result!!.id)
        assertEquals("Active Provider", result.name)
        assertEquals("https://api.openai.com/v1", result.baseUrl)
        assertEquals("sk-active", result.apiKey)
        assertEquals("gpt-4o", result.selectedModel)
    }

    // --- getApiConfig ---

    @Test
    fun `getApiConfig returns default ApiConfig when no active provider`() = runTest {
        coEvery { providerDao.getActiveProvider() } returns null

        val config = repository.getApiConfig()

        assertEquals(ApiConfig(), config)
    }

    @Test
    fun `getApiConfig returns config derived from active provider`() = runTest {
        val activeEntity = makeEntity(
            id = "cfg-id",
            name = "Config Provider",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-cfg",
            selectedModel = "gpt-4",
            isActive = true
        )
        coEvery { providerDao.getActiveProvider() } returns activeEntity

        val config = repository.getApiConfig()

        assertTrue(config.isConfigured)
        assertEquals("https://api.openai.com/v1", config.baseUrl)
        assertEquals("sk-cfg", config.apiKey)
        assertEquals("gpt-4", config.model)
        assertEquals("Config Provider", config.providerName)
    }

    @Test
    fun `getApiConfig is not configured when active provider has blank apiKey`() = runTest {
        val entity = makeEntity(id = "x", apiKey = "", isActive = true)
        coEvery { providerDao.getActiveProvider() } returns entity

        val config = repository.getApiConfig()

        assertFalse(config.isConfigured)
    }
}
