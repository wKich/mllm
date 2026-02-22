package com.mllm.chat.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores simple scalar app configuration (web search settings) in plain SharedPreferences.
 * Provider data is stored in Room via ProviderDao for reliable persistence.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_FILE = "mllm_prefs"
        private const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
        private const val KEY_WEB_SEARCH_API_KEY = "web_search_api_key"
        private const val KEY_WEB_SEARCH_PROVIDER = "web_search_provider"
    }

    fun saveWebSearchConfig(enabled: Boolean, apiKey: String, provider: String) {
        prefs.edit()
            .putBoolean(KEY_WEB_SEARCH_ENABLED, enabled)
            .putString(KEY_WEB_SEARCH_API_KEY, apiKey)
            .putString(KEY_WEB_SEARCH_PROVIDER, provider)
            .apply()
    }

    fun getWebSearchEnabled(): Boolean =
        prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, false)

    fun getWebSearchApiKey(): String =
        prefs.getString(KEY_WEB_SEARCH_API_KEY, "") ?: ""

    fun getWebSearchProvider(): String =
        prefs.getString(KEY_WEB_SEARCH_PROVIDER, "brave") ?: "brave"
}

