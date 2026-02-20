package com.mllm.chat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mllm.chat.data.model.ApiConfig
import com.mllm.chat.data.model.Provider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_ACTIVE_PROVIDER_ID = "active_provider_id"
    }

    fun saveApiConfig(config: ApiConfig) {
        securePrefs.edit().apply {
            putString(KEY_BASE_URL, config.baseUrl)
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_MODEL, config.model)
            putString(KEY_SYSTEM_PROMPT, config.systemPrompt)
            putString(KEY_PROVIDER_NAME, config.providerName)
            if (config.temperature != null) {
                putFloat(KEY_TEMPERATURE, config.temperature)
            } else {
                remove(KEY_TEMPERATURE)
            }
            if (config.maxTokens != null) {
                putInt(KEY_MAX_TOKENS, config.maxTokens)
            } else {
                remove(KEY_MAX_TOKENS)
            }
            apply()
        }
        // Sync the active provider so model switches are persisted
        val activeId = getActiveProviderId()
        if (activeId != null) {
            val providers = getProviders().toMutableList()
            val idx = providers.indexOfFirst { it.id == activeId }
            if (idx >= 0) {
                providers[idx] = providers[idx].copy(
                    name = providers[idx].name,
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    selectedModel = config.model,
                    systemPrompt = config.systemPrompt,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens
                )
                saveProviders(providers)
            }
        }
    }

    fun getApiConfig(): ApiConfig {
        val activeProvider = getActiveProvider()
        if (activeProvider != null) {
            return activeProvider.toApiConfig()
        }

        // If there is no active provider but providers are stored, fall back to the first one
        val providers = getProviders()
        if (providers.isNotEmpty()) {
            val firstProvider = providers.first()
            // Repair the active provider ID so subsequent calls see a valid active provider
            securePrefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, firstProvider.id).apply()
            return firstProvider.toApiConfig()
        }

        // Final fallback to legacy flat keys when no providers exist
        return ApiConfig(
            baseUrl = securePrefs.getString(KEY_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            apiKey = securePrefs.getString(KEY_API_KEY, "") ?: "",
            model = securePrefs.getString(KEY_MODEL, "gpt-4") ?: "gpt-4",
            systemPrompt = securePrefs.getString(KEY_SYSTEM_PROMPT, "") ?: "",
            temperature = if (securePrefs.contains(KEY_TEMPERATURE)) securePrefs.getFloat(KEY_TEMPERATURE, 0.7f) else null,
            maxTokens = if (securePrefs.contains(KEY_MAX_TOKENS)) securePrefs.getInt(KEY_MAX_TOKENS, 0) else null,
            providerName = securePrefs.getString(KEY_PROVIDER_NAME, "Default") ?: "Default"
        )
    }

    fun clearApiKey() {
        securePrefs.edit().remove(KEY_API_KEY).apply()
    }

    // Provider management
    fun saveProviders(providers: List<Provider>) {
        val json = gson.toJson(providers)
        securePrefs.edit().putString(KEY_PROVIDERS, json).apply()
    }

    fun getProviders(): List<Provider> {
        val json = securePrefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        val type = object : TypeToken<List<Provider>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addProvider(provider: Provider) {
        val providers = getProviders().toMutableList()
        val existingIndex = providers.indexOfFirst { it.id == provider.id }
        if (existingIndex >= 0) {
            providers[existingIndex] = provider
        } else {
            providers.add(provider)
        }
        saveProviders(providers)
    }

    fun deleteProvider(providerId: String) {
        val providers = getProviders().toMutableList()
        providers.removeIf { it.id == providerId }
        saveProviders(providers)

        // If we deleted the active provider, clear the active provider ID
        if (getActiveProviderId() == providerId) {
            setActiveProviderId(null)
        }
    }

    fun setActiveProviderId(providerId: String?) {
        if (providerId != null) {
            securePrefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, providerId).apply()
        } else {
            securePrefs.edit().remove(KEY_ACTIVE_PROVIDER_ID).apply()
        }
    }

    fun getActiveProviderId(): String? {
        return securePrefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
    }

    fun getActiveProvider(): Provider? {
        val activeId = getActiveProviderId() ?: return null
        return getProviders().find { it.id == activeId }
    }
}
