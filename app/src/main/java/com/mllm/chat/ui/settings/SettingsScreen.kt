package com.mllm.chat.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }
    var expandedModelDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveConfig()
                            onNavigateBack()
                        },
                        enabled = uiState.isConfigValid
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Configuration Section
            Text(
                text = "API Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Base URL
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("OpenAI-compatible API endpoint") }
            )

            // API Key
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
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
                            imageVector = if (showApiKey) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
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
                        value = uiState.model,
                        onValueChange = viewModel::updateModel,
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
                        val modelsToShow = if (uiState.availableModels.isNotEmpty()) {
                            uiState.availableModels
                        } else {
                            DEFAULT_MODELS
                        }

                        modelsToShow.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    viewModel.updateModel(model)
                                    expandedModelDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = viewModel::fetchModels,
                    enabled = uiState.isConfigValid && !uiState.isFetchingModels,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (uiState.isFetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Fetch")
                    }
                }
            }

            // Test Connection Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = viewModel::testConnection,
                    enabled = uiState.isConfigValid && !uiState.isTesting,
                    modifier = Modifier.weight(1f)
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
                        modifier = Modifier.padding(16.dp),
                        color = when (result) {
                            is TestResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                            is TestResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Optional Settings Section
            Text(
                text = "Optional Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // System Prompt
            OutlinedTextField(
                value = uiState.systemPrompt,
                onValueChange = viewModel::updateSystemPrompt,
                label = { Text("System Prompt") },
                placeholder = { Text("You are a helpful assistant...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                maxLines = 5,
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
                            text = if (uiState.useTemperature) "%.1f".format(uiState.temperature) else "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.useTemperature,
                        onCheckedChange = viewModel::toggleUseTemperature
                    )
                }
                if (uiState.useTemperature) {
                    Slider(
                        value = uiState.temperature,
                        onValueChange = viewModel::updateTemperature,
                        valueRange = 0f..1f,
                        steps = 9,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Lower = more focused, Higher = more creative",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Max Tokens
            OutlinedTextField(
                value = uiState.maxTokens,
                onValueChange = viewModel::updateMaxTokens,
                label = { Text("Max Tokens (optional)") },
                placeholder = { Text("Leave empty for default") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Maximum response length") }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
