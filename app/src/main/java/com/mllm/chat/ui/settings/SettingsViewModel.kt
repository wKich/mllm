package com.mllm.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mllm.chat.data.model.ApiConfig
import com.mllm.chat.data.model.Provider
import com.mllm.chat.data.remote.ApiResult
import com.mllm.chat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val providers: List<Provider> = emptyList(),
    val activeProviderId: String? = null,
    val showProviderDialog: Boolean = false,
    val editingProviderId: String? = null,
    val dialogName: String = "",
    val dialogBaseUrl: String = "",
    val dialogApiKey: String = "",
    val dialogModel: String = "",
    val dialogSystemPrompt: String = "",
    val dialogTemperature: Float = 0.7f,
    val dialogUseTemperature: Boolean = false,
    val dialogMaxTokens: String = "",
    val dialogAvailableModels: List<String> = emptyList(),
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val isFetchingModels: Boolean = false,
    // Web search settings
    val webSearchEnabled: Boolean = false,
    val webSearchApiKey: String = "",
    val webSearchProvider: String = "brave"
) {
    val isDialogConfigValid: Boolean
        get() = dialogBaseUrl.isNotBlank() && dialogApiKey.isNotBlank() && dialogModel.isNotBlank()

    val isFetchModelsConfigValid: Boolean
        get() = dialogBaseUrl.isNotBlank() && dialogApiKey.isNotBlank()
}

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

    private var apiKeySaveJob: Job? = null

    init {
        loadProviders()
    }

    private fun loadProviders() {
        val providers = repository.getProviders()
        val activeId = repository.getActiveProvider()?.id
        _uiState.value = _uiState.value.copy(
            providers = providers,
            activeProviderId = activeId,
            webSearchEnabled = repository.getWebSearchEnabled(),
            webSearchApiKey = repository.getWebSearchApiKey(),
            webSearchProvider = repository.getWebSearchProvider()
        )
    }

    fun openAddProviderDialog() {
        _uiState.value = _uiState.value.copy(
            showProviderDialog = true,
            editingProviderId = null,
            dialogName = "",
            dialogBaseUrl = "",
            dialogApiKey = "",
            dialogModel = "",
            dialogSystemPrompt = "",
            dialogTemperature = 0.7f,
            dialogUseTemperature = false,
            dialogMaxTokens = "",
            dialogAvailableModels = emptyList(),
            testResult = null
        )
    }

    fun openEditProviderDialog(provider: Provider) {
        _uiState.value = _uiState.value.copy(
            showProviderDialog = true,
            editingProviderId = provider.id,
            dialogName = provider.name,
            dialogBaseUrl = provider.baseUrl,
            dialogApiKey = provider.apiKey,
            dialogModel = provider.selectedModel.ifBlank {
                provider.availableModels.firstOrNull() ?: "gpt-4"
            },
            dialogSystemPrompt = provider.systemPrompt,
            dialogTemperature = provider.temperature ?: 0.7f,
            dialogUseTemperature = provider.temperature != null,
            dialogMaxTokens = provider.maxTokens?.toString() ?: "",
            dialogAvailableModels = provider.availableModels,
            testResult = null
        )
    }

    fun dismissProviderDialog() {
        _uiState.value = _uiState.value.copy(showProviderDialog = false)
    }

    fun updateDialogName(value: String) {
        _uiState.value = _uiState.value.copy(dialogName = value)
    }

    fun updateDialogBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(dialogBaseUrl = value, testResult = null)
    }

    fun updateDialogApiKey(value: String) {
        _uiState.value = _uiState.value.copy(dialogApiKey = value, testResult = null)
    }

    fun updateDialogModel(value: String) {
        _uiState.value = _uiState.value.copy(dialogModel = value, testResult = null)
    }

    fun updateDialogSystemPrompt(value: String) {
        _uiState.value = _uiState.value.copy(dialogSystemPrompt = value)
    }

    fun updateDialogTemperature(value: Float) {
        _uiState.value = _uiState.value.copy(dialogTemperature = value)
    }

    fun toggleDialogUseTemperature(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(dialogUseTemperature = enabled)
    }

    fun updateDialogMaxTokens(value: String) {
        _uiState.value = _uiState.value.copy(dialogMaxTokens = value.filter { it.isDigit() })
    }

    fun saveProvider() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val provider = Provider(
                id = state.editingProviderId ?: UUID.randomUUID().toString(),
                name = state.dialogName.trim().ifBlank {
                    runCatching { java.net.URI(state.dialogBaseUrl.trim()).host }
                        .getOrNull()?.takeIf { it.isNotBlank() } ?: "Unnamed Provider"
                },
                baseUrl = state.dialogBaseUrl.trim(),
                apiKey = state.dialogApiKey.trim(),
                selectedModel = state.dialogModel.trim(),
                systemPrompt = state.dialogSystemPrompt.trim(),
                temperature = if (state.dialogUseTemperature) state.dialogTemperature else null,
                maxTokens = state.dialogMaxTokens.toIntOrNull(),
                availableModels = state.dialogAvailableModels
            )
            repository.saveProvider(provider)

            // Auto-activate if it's the first provider, if it was already the active one,
            // or if there is currently no active provider set.
            val allProviders = repository.getProviders()
            val hasNoActiveProvider = repository.getActiveProvider() == null
            if (allProviders.size == 1 || state.activeProviderId == provider.id || hasNoActiveProvider) {
                repository.setActiveProvider(provider.id)
            }

            _uiState.value = _uiState.value.copy(showProviderDialog = false)
            loadProviders()
        }
    }

    fun deleteProvider(providerId: String) {
        repository.deleteProvider(providerId)
        // Auto-activate the first remaining provider if the active one was deleted
        val remaining = repository.getProviders()
        if (repository.getActiveProvider() == null && remaining.isNotEmpty()) {
            repository.setActiveProvider(remaining.first().id)
        }
        loadProviders()
    }

    fun setActiveProvider(providerId: String) {
        repository.setActiveProvider(providerId)
        _uiState.value = _uiState.value.copy(activeProviderId = providerId)
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null)
            val config = createDialogConfig()
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

    fun fetchModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingModels = true)
            val config = createDialogConfig()
            val result = repository.fetchModels(config)
            _uiState.value = when (result) {
                is ApiResult.Success -> _uiState.value.copy(
                    dialogAvailableModels = result.data,
                    isFetchingModels = false,
                    testResult = TestResult.Success("Found ${result.data.size} models")
                )
                is ApiResult.Error -> _uiState.value.copy(
                    isFetchingModels = false,
                    testResult = TestResult.Error("Failed to fetch models: ${result.message}")
                )
            }
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    // Web search settings
    fun updateWebSearchEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(webSearchEnabled = enabled)
        saveWebSearchConfig()
    }

    fun updateWebSearchApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(webSearchApiKey = apiKey)
        apiKeySaveJob?.cancel()
        apiKeySaveJob = viewModelScope.launch {
            delay(500)
            saveWebSearchConfig()
        }
    }

    fun updateWebSearchProvider(provider: String) {
        _uiState.value = _uiState.value.copy(webSearchProvider = provider)
        saveWebSearchConfig()
    }

    private fun saveWebSearchConfig() {
        val state = _uiState.value
        repository.saveWebSearchConfig(state.webSearchEnabled, state.webSearchApiKey, state.webSearchProvider)
    }

    private fun createDialogConfig(): ApiConfig {
        val state = _uiState.value
        return ApiConfig(
            baseUrl = state.dialogBaseUrl.trim(),
            apiKey = state.dialogApiKey.trim(),
            model = state.dialogModel.trim(),
            systemPrompt = state.dialogSystemPrompt.trim(),
            temperature = if (state.dialogUseTemperature) state.dialogTemperature else null,
            maxTokens = state.dialogMaxTokens.toIntOrNull(),
            providerName = state.dialogName.trim()
        )
    }
}
