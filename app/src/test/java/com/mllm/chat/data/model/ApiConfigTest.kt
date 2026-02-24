package com.mllm.chat.data.model

import org.junit.Assert.*
import org.junit.Test

class ApiConfigTest {

    @Test
    fun `isConfigured returns true when all required fields are set`() {
        val config = ApiConfig(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test-key",
            model = "gpt-4"
        )
        assertTrue(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when baseUrl is blank`() {
        val config = ApiConfig(baseUrl = "", apiKey = "sk-key", model = "gpt-4")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when baseUrl is only whitespace`() {
        val config = ApiConfig(baseUrl = "   ", apiKey = "sk-key", model = "gpt-4")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when apiKey is blank`() {
        val config = ApiConfig(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "",
            model = "gpt-4"
        )
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when model is blank`() {
        val config = ApiConfig(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-key",
            model = ""
        )
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when multiple required fields are blank`() {
        val config = ApiConfig(baseUrl = "", apiKey = "", model = "")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `normalizedBaseUrl removes single trailing slash`() {
        val config = ApiConfig(baseUrl = "https://api.openai.com/v1/")
        assertEquals("https://api.openai.com/v1", config.normalizedBaseUrl())
    }

    @Test
    fun `normalizedBaseUrl preserves URL without trailing slash`() {
        val config = ApiConfig(baseUrl = "https://api.openai.com/v1")
        assertEquals("https://api.openai.com/v1", config.normalizedBaseUrl())
    }

    @Test
    fun `normalizedBaseUrl removes multiple trailing slashes`() {
        val config = ApiConfig(baseUrl = "https://api.openai.com/v1///")
        assertEquals("https://api.openai.com/v1", config.normalizedBaseUrl())
    }

    @Test
    fun `normalizedBaseUrl handles localhost URL`() {
        val config = ApiConfig(baseUrl = "http://localhost:11434/v1/")
        assertEquals("http://localhost:11434/v1", config.normalizedBaseUrl())
    }

    @Test
    fun `default values are applied correctly`() {
        val config = ApiConfig()
        assertEquals("https://api.openai.com/v1", config.baseUrl)
        assertEquals("", config.apiKey)
        assertEquals("gpt-4", config.model)
        assertEquals("", config.systemPrompt)
        assertNull(config.temperature)
        assertNull(config.maxTokens)
        assertEquals("Default", config.providerName)
    }

    @Test
    fun `default ApiConfig is not configured because apiKey is blank`() {
        assertFalse(ApiConfig().isConfigured)
    }

    @Test
    fun `ApiConfig equality works for identical instances`() {
        val a = ApiConfig(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-key",
            model = "gpt-4",
            temperature = 0.7f,
            maxTokens = 1000
        )
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `ApiConfig with temperature and maxTokens preserves optional fields`() {
        val config = ApiConfig(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-key",
            model = "gpt-4",
            temperature = 1.0f,
            maxTokens = 4096
        )
        assertEquals(1.0f, config.temperature)
        assertEquals(4096, config.maxTokens)
    }
}
