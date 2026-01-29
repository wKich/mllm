package com.mllm.chat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mllm.chat.data.model.ApiConfig
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

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
    }

    fun saveApiConfig(config: ApiConfig) {
        securePrefs.edit().apply {
            putString(KEY_BASE_URL, config.baseUrl)
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_MODEL, config.model)
            putString(KEY_SYSTEM_PROMPT, config.systemPrompt)
            putFloat(KEY_TEMPERATURE, config.temperature)
            if (config.maxTokens != null) {
                putInt(KEY_MAX_TOKENS, config.maxTokens)
            } else {
                remove(KEY_MAX_TOKENS)
            }
            apply()
        }
    }

    fun getApiConfig(): ApiConfig {
        return ApiConfig(
            baseUrl = securePrefs.getString(KEY_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            apiKey = securePrefs.getString(KEY_API_KEY, "") ?: "",
            model = securePrefs.getString(KEY_MODEL, "gpt-4") ?: "gpt-4",
            systemPrompt = securePrefs.getString(KEY_SYSTEM_PROMPT, "") ?: "",
            temperature = securePrefs.getFloat(KEY_TEMPERATURE, 0.7f),
            maxTokens = if (securePrefs.contains(KEY_MAX_TOKENS)) securePrefs.getInt(KEY_MAX_TOKENS, 0) else null
        )
    }

    fun clearApiKey() {
        securePrefs.edit().remove(KEY_API_KEY).apply()
    }
}
