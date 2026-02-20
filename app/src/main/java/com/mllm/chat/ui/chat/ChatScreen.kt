package com.mllm.chat.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mllm.chat.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.isStreaming) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Refresh configuration when returning from settings
    LaunchedEffect(Unit) {
        viewModel.refreshConfiguration()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                onConversationSelected = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewConversation = {
                    viewModel.createNewConversation()
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                val conversation = uiState.conversations.find {
                                    it.id == uiState.currentConversationId
                                }
                                Text(
                                    text = conversation?.title ?: "AI Chat",
                                    maxLines = 1
                                )
                                if (uiState.currentModel.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uiState.currentModel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { showModelSelector = true }
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            if (uiState.currentConversationId != null && uiState.messages.isNotEmpty()) {
                                IconButton(onClick = { showClearDialog = true }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat")
                                }
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )

                    NetworkIndicator(isOffline = uiState.isOffline)
                }
            },
            bottomBar = {
                Column {
                    if (uiState.isSearching) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Searching the web…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    ChatInput(
                        value = uiState.inputText,
                        onValueChange = viewModel::updateInputText,
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopStreaming,
                        isStreaming = uiState.isStreaming,
                        enabled = uiState.isConfigured && !uiState.isOffline
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    !uiState.isConfigured -> {
                        // Configuration required
                        ConfigurationPrompt(
                            onConfigureClick = onNavigateToSettings,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.currentConversationId == null -> {
                        // No conversation selected
                        EmptyState(
                            onNewChat = viewModel::createNewConversation,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.messages.isEmpty() -> {
                        // Empty conversation
                        EmptyConversation(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        // Messages list
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = uiState.messages,
                                key = { it.id }
                            ) { message ->
                                MessageBubble(
                                    message = message,
                                    onRetry = if (message.isError) {
                                        { viewModel.retryLastMessage() }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }

        // Clear conversation dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear conversation?") },
                text = { Text("This will delete all messages in this conversation.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearCurrentConversation()
                            showClearDialog = false
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Model selector dialog
        if (showModelSelector) {
            ModelSelectorDialog(
                currentModel = uiState.currentModel,
                availableModels = uiState.availableModels,
                onModelSelected = { model ->
                    viewModel.switchModel(model)
                    showModelSelector = false
                },
                onDismiss = { showModelSelector = false },
                onFetchModels = { viewModel.loadAvailableModels() }
            )
        }
    }
}

@Composable
private fun ConversationDrawer(
    conversations: List<com.mllm.chat.data.model.Conversation>,
    currentConversationId: Long?,
    onConversationSelected: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Conversations",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Button(
            onClick = onNewConversation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        if (conversations.isEmpty()) {
            Text(
                text = "No conversations yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = conversations,
                    key = { it.id }
                ) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = { onConversationSelected(conversation.id) },
                        onDelete = { onDeleteConversation(conversation.id) },
                        modifier = if (conversation.id == currentConversationId) {
                            Modifier.padding(horizontal = 8.dp)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }

        Divider()

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ConfigurationPrompt(
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Configuration Required",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Set up your API connection to start chatting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onConfigureClick) {
            Text("Configure API")
        }
    }
}

@Composable
private fun EmptyState(
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Welcome to AI Chat",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Start a new conversation to begin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onNewChat) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }
    }
}

@Composable
private fun EmptyConversation(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "Send a message to start the conversation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectorDialog(
    currentModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onFetchModels: () -> Unit
) {
    var customModel by remember { mutableStateOf(currentModel) }
    val modelsToShow = if (availableModels.isNotEmpty()) {
        availableModels
    } else {
        com.mllm.chat.data.model.DEFAULT_MODELS
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Model") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (availableModels.isEmpty()) {
                    TextButton(onClick = onFetchModels) {
                        Text("Fetch available models")
                    }
                }

                modelsToShow.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModelSelected(model) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = model == currentModel,
                            onClick = { onModelSelected(model) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Custom Model",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    label = { Text("Model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (customModel.isNotBlank()) {
                        onModelSelected(customModel)
                    }
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
