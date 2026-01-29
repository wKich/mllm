package com.mllm.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mllm.chat.data.model.ApiConfig
import com.mllm.chat.data.remote.ApiResult
import com.mllm.chat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: String = "",
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val isSaved: Boolean = false
)

sealed class TestResult {
    data class Success(val message: String) : TestResult()
    data class Error(val message: String) : TestResult()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        val config = repository.getApiConfig()
        _uiState.value = SettingsUiState(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = config.systemPrompt,
            temperature = config.temperature,
            maxTokens = config.maxTokens?.toString() ?: ""
        )
    }

    fun updateBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            baseUrl = value,
            testResult = null,
            isSaved = false
        )
    }

    fun updateApiKey(value: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = value,
            testResult = null,
            isSaved = false
        )
    }

    fun updateModel(value: String) {
        _uiState.value = _uiState.value.copy(
            model = value,
            testResult = null,
            isSaved = false
        )
    }

    fun updateSystemPrompt(value: String) {
        _uiState.value = _uiState.value.copy(
            systemPrompt = value,
            isSaved = false
        )
    }

    fun updateTemperature(value: Float) {
        _uiState.value = _uiState.value.copy(
            temperature = value,
            isSaved = false
        )
    }

    fun updateMaxTokens(value: String) {
        _uiState.value = _uiState.value.copy(
            maxTokens = value.filter { it.isDigit() },
            isSaved = false
        )
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null)

            val config = createConfig()
            val result = repository.testConnection(config)

            _uiState.value = _uiState.value.copy(
                isTesting = false,
                testResult = when (result) {
                    is ApiResult.Success -> TestResult.Success("Connection successful!")
                    is ApiResult.Error -> TestResult.Error(result.message)
                }
            )
        }
    }

    fun saveConfig() {
        val config = createConfig()
        repository.saveApiConfig(config)
        _uiState.value = _uiState.value.copy(isSaved = true)
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    private fun createConfig(): ApiConfig {
        val state = _uiState.value
        return ApiConfig(
            baseUrl = state.baseUrl.trim(),
            apiKey = state.apiKey.trim(),
            model = state.model.trim(),
            systemPrompt = state.systemPrompt.trim(),
            temperature = state.temperature,
            maxTokens = state.maxTokens.toIntOrNull()
        )
    }

    fun isConfigValid(): Boolean {
        val state = _uiState.value
        return state.baseUrl.isNotBlank() &&
                state.apiKey.isNotBlank() &&
                state.model.isNotBlank()
    }
}
