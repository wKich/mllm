package com.mllm.chat.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mllm.chat.data.model.DEFAULT_MODELS
import com.mllm.chat.data.model.Provider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddProviderDialog) {
                Icon(Icons.Default.Add, contentDescription = "Add Provider")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Inference Providers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (uiState.providers.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No providers configured. Tap + to add one.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.providers.forEach { provider ->
                        ProviderCard(
                            provider = provider,
                            isActive = provider.id == uiState.activeProviderId,
                            onSetActive = { viewModel.setActiveProvider(provider.id) },
                            onEdit = { viewModel.openEditProviderDialog(provider) },
                            onDelete = { viewModel.deleteProvider(provider.id) }
                        )
                    }
                }
            }

            Divider()

            // Web Search Settings
            WebSearchSettings(uiState = uiState, viewModel = viewModel)
        }
    }

    if (uiState.showProviderDialog) {
        ProviderDialog(
            uiState = uiState,
            isEditing = uiState.editingProviderId != null,
            viewModel = viewModel,
            onDismiss = viewModel::dismissProviderDialog
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    provider: Provider,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isActive) {
            CardDefaults.outlinedCardBorder()
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isActive,
                onClick = onSetActive
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = provider.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val modelLabel = provider.selectedModel.ifBlank {
                    provider.availableModels.firstOrNull() ?: "gpt-4"
                }
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("Active", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit provider")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete provider",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDialog(
    uiState: SettingsUiState,
    isEditing: Boolean,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }
    var expandedModelDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Provider" else "Add Provider") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider Name
                OutlinedTextField(
                    value = uiState.dialogName,
                    onValueChange = viewModel::updateDialogName,
                    label = { Text("Provider Name") },
                    placeholder = { Text("My Provider") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Base URL
                OutlinedTextField(
                    value = uiState.dialogBaseUrl,
                    onValueChange = viewModel::updateDialogBaseUrl,
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("OpenAI-compatible API endpoint") }
                )

                // API Key
                OutlinedTextField(
                    value = uiState.dialogApiKey,
                    onValueChange = viewModel::updateDialogApiKey,
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide" else "Show"
                            )
                        }
                    },
                    supportingText = { Text("Stored securely on device") }
                )

                // Model Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expandedModelDropdown,
                        onExpandedChange = { expandedModelDropdown = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = uiState.dialogModel,
                            onValueChange = viewModel::updateDialogModel,
                            label = { Text("Model") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown)
                            },
                            singleLine = true,
                            supportingText = { Text("Select or type custom model name") }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedModelDropdown,
                            onDismissRequest = { expandedModelDropdown = false }
                        ) {
                            val modelsToShow = uiState.dialogAvailableModels.ifEmpty { DEFAULT_MODELS }
                            modelsToShow.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.updateDialogModel(model)
                                        expandedModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::fetchModels,
                        enabled = uiState.isDialogConfigValid && !uiState.isFetchingModels,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (uiState.isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Fetch")
                        }
                    }
                }

                // Test Connection
                Button(
                    onClick = viewModel::testConnection,
                    enabled = uiState.isDialogConfigValid && !uiState.isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isTesting) "Testing..." else "Test Connection")
                }

                // Test Result
                uiState.testResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (result) {
                                is TestResult.Success -> MaterialTheme.colorScheme.primaryContainer
                                is TestResult.Error -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Text(
                            text = when (result) {
                                is TestResult.Success -> result.message
                                is TestResult.Error -> result.message
                            },
                            modifier = Modifier.padding(12.dp),
                            color = when (result) {
                                is TestResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                                is TestResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }

                Divider()

                Text(
                    text = "Optional Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // System Prompt
                OutlinedTextField(
                    value = uiState.dialogSystemPrompt,
                    onValueChange = viewModel::updateDialogSystemPrompt,
                    label = { Text("System Prompt") },
                    placeholder = { Text("You are a helpful assistant...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    maxLines = 4,
                    supportingText = { Text("Instructions sent with every message") }
                )

                // Temperature
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Temperature")
                            Text(
                                text = if (uiState.dialogUseTemperature) "%.1f".format(uiState.dialogTemperature) else "Not set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.dialogUseTemperature,
                            onCheckedChange = viewModel::toggleDialogUseTemperature
                        )
                    }
                    if (uiState.dialogUseTemperature) {
                        Slider(
                            value = uiState.dialogTemperature,
                            onValueChange = viewModel::updateDialogTemperature,
                            valueRange = 0f..1f,
                            steps = 9,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Max Tokens
                OutlinedTextField(
                    value = uiState.dialogMaxTokens,
                    onValueChange = viewModel::updateDialogMaxTokens,
                    label = { Text("Max Tokens (optional)") },
                    placeholder = { Text("Leave empty for default") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Maximum response length") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::saveProvider,
                enabled = uiState.isDialogConfigValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebSearchSettings(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var showApiKey by remember { mutableStateOf(false) }
    var expandedProviderDropdown by remember { mutableStateOf(false) }

    val providers = listOf("brave" to "Brave Search", "tavily" to "Tavily")
    val selectedProviderLabel = providers.firstOrNull { it.first == uiState.webSearchProvider }?.second ?: "Brave Search"

    Text(
        text = "Web Search",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Web Search", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Allow the AI to search the web during chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.webSearchEnabled,
                    onCheckedChange = viewModel::updateWebSearchEnabled
                )
            }

            if (uiState.webSearchEnabled) {
                // Search provider dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedProviderDropdown,
                    onExpandedChange = { expandedProviderDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedProviderLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Search Provider") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProviderDropdown)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedProviderDropdown,
                        onDismissRequest = { expandedProviderDropdown = false }
                    ) {
                        providers.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.updateWebSearchProvider(value)
                                    expandedProviderDropdown = false
                                }
                            )
                        }
                    }
                }

                // API Key
                OutlinedTextField(
                    value = uiState.webSearchApiKey,
                    onValueChange = viewModel::updateWebSearchApiKey,
                    label = { Text("Search API Key") },
                    placeholder = {
                        Text(
                            if (uiState.webSearchProvider == "tavily") "tvly-..." else "BSA..."
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide" else "Show"
                            )
                        }
                    },
                    supportingText = {
                        val providerUrl = if (uiState.webSearchProvider == "tavily") {
                            "Get a free key at app.tavily.com"
                        } else {
                            "Get a key at brave.com/search/api"
                        }
                        Text(providerUrl)
                    }
                )
            }
        }
    }
}
