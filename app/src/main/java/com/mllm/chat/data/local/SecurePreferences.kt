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

/**
 * Plain SharedPreferences-backed storage for app configuration and provider data.
 * Data lives in the app's private storage (/data/data/<pkg>/shared_prefs/) which is
 * inaccessible to other apps on non-rooted devices; OS-level isolation is sufficient here.
 *
 * Previously named SecurePreferences (used EncryptedSharedPreferences); renamed to
 * AppPreferences to accurately reflect that data is stored unencrypted.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val gson = Gson()

    companion object {
        private const val PREFS_FILE = "mllm_prefs"
        private const val LEGACY_PREFS_FILE = "secure_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_ACTIVE_PROVIDER_ID = "active_provider_id"
        private const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
        private const val KEY_WEB_SEARCH_API_KEY = "web_search_api_key"
        private const val KEY_WEB_SEARCH_PROVIDER = "web_search_provider"
    }

    init {
        migrateFromEncryptedPrefs()
    }

    /**
     * One-time migration from the legacy EncryptedSharedPreferences file ("secure_prefs")
     * to plain SharedPreferences ("mllm_prefs"). Wrapped in try-catch because
     * EncryptedSharedPreferences can be unreliable on some devices/OS versions; if migration
     * fails, users start fresh with empty preferences.
     */
    private fun migrateFromEncryptedPrefs() {
        if (prefs.contains(KEY_PROVIDERS) || prefs.contains(KEY_API_KEY)) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                LEGACY_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val editor = prefs.edit()
            encryptedPrefs.getString(KEY_PROVIDERS, null)?.let { editor.putString(KEY_PROVIDERS, it) }
            encryptedPrefs.getString(KEY_ACTIVE_PROVIDER_ID, null)?.let { editor.putString(KEY_ACTIVE_PROVIDER_ID, it) }
            encryptedPrefs.getString(KEY_BASE_URL, null)?.let { editor.putString(KEY_BASE_URL, it) }
            encryptedPrefs.getString(KEY_API_KEY, null)?.let { editor.putString(KEY_API_KEY, it) }
            encryptedPrefs.getString(KEY_MODEL, null)?.let { editor.putString(KEY_MODEL, it) }
            encryptedPrefs.getString(KEY_SYSTEM_PROMPT, null)?.let { editor.putString(KEY_SYSTEM_PROMPT, it) }
            encryptedPrefs.getString(KEY_PROVIDER_NAME, null)?.let { editor.putString(KEY_PROVIDER_NAME, it) }
            if (encryptedPrefs.contains(KEY_TEMPERATURE)) editor.putFloat(KEY_TEMPERATURE, encryptedPrefs.getFloat(KEY_TEMPERATURE, 0.7f))
            if (encryptedPrefs.contains(KEY_MAX_TOKENS)) editor.putInt(KEY_MAX_TOKENS, encryptedPrefs.getInt(KEY_MAX_TOKENS, 0))
            if (encryptedPrefs.contains(KEY_WEB_SEARCH_ENABLED)) editor.putBoolean(KEY_WEB_SEARCH_ENABLED, encryptedPrefs.getBoolean(KEY_WEB_SEARCH_ENABLED, false))
            encryptedPrefs.getString(KEY_WEB_SEARCH_API_KEY, null)?.let { editor.putString(KEY_WEB_SEARCH_API_KEY, it) }
            encryptedPrefs.getString(KEY_WEB_SEARCH_PROVIDER, null)?.let { editor.putString(KEY_WEB_SEARCH_PROVIDER, it) }
            editor.apply()
            context.deleteSharedPreferences(LEGACY_PREFS_FILE)
        } catch (_: Exception) {
            // Migration failed; start fresh — users will need to re-enter their configuration.
        }
    }

    fun saveApiConfig(config: ApiConfig) {
        prefs.edit().apply {
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
        return try {
            val activeProvider = getActiveProvider()
            if (activeProvider != null) {
                return activeProvider.toApiConfig()
            }

            // If there is no active provider but providers are stored, fall back to the first one
            val providers = getProviders()
            if (providers.isNotEmpty()) {
                val firstProvider = providers.first()
                // Repair the active provider ID so subsequent calls see a valid active provider
                prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, firstProvider.id).apply()
                return firstProvider.toApiConfig()
            }

            // Final fallback to legacy flat keys when no providers exist
            ApiConfig(
                baseUrl = prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
                apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
                model = prefs.getString(KEY_MODEL, "gpt-4") ?: "gpt-4",
                systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, "") ?: "",
                temperature = if (prefs.contains(KEY_TEMPERATURE)) prefs.getFloat(KEY_TEMPERATURE, 0.7f) else null,
                maxTokens = if (prefs.contains(KEY_MAX_TOKENS)) prefs.getInt(KEY_MAX_TOKENS, 0) else null,
                providerName = prefs.getString(KEY_PROVIDER_NAME, "Default") ?: "Default"
            )
        } catch (e: Exception) {
            ApiConfig()
        }
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    // Provider management
    fun saveProviders(providers: List<Provider>) {
        val json = gson.toJson(providers)
        prefs.edit().putString(KEY_PROVIDERS, json).commit()
    }

    fun getProviders(): List<Provider> {
        return try {
            val json = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
            val type = object : TypeToken<List<Provider>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
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
        prefs.edit().apply {
            if (providerId != null) {
                putString(KEY_ACTIVE_PROVIDER_ID, providerId)
            } else {
                remove(KEY_ACTIVE_PROVIDER_ID)
            }
            commit()
        }
    }

    fun getActiveProviderId(): String? {
        return try {
            prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
        } catch (e: Exception) {
            null
        }
    }

    fun getActiveProvider(): Provider? {
        val activeId = getActiveProviderId() ?: return null
        return getProviders().find { it.id == activeId }
    }

    // Web search configuration
    fun saveWebSearchConfig(enabled: Boolean, apiKey: String, provider: String) {
        prefs.edit().apply {
            putBoolean(KEY_WEB_SEARCH_ENABLED, enabled)
            putString(KEY_WEB_SEARCH_API_KEY, apiKey)
            putString(KEY_WEB_SEARCH_PROVIDER, provider)
            apply()
        }
    }

    fun getWebSearchEnabled(): Boolean =
        try {
            prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, false)
        } catch (e: Exception) {
            false
        }

    fun getWebSearchApiKey(): String =
        try {
            prefs.getString(KEY_WEB_SEARCH_API_KEY, "") ?: ""
        } catch (e: Exception) {
            ""
        }

    fun getWebSearchProvider(): String =
        try {
            prefs.getString(KEY_WEB_SEARCH_PROVIDER, "brave") ?: "brave"
        } catch (e: Exception) {
            "brave"
        }
}
