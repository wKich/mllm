package com.mllm.chat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mllm.chat.data.model.Conversation
import com.mllm.chat.data.model.Message
import com.mllm.chat.data.model.MessageRole
import com.mllm.chat.data.remote.StreamEvent
import com.mllm.chat.data.repository.ChatRepository
import com.mllm.chat.util.NetworkUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: Long? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val isOffline: Boolean = false,
    val isConfigured: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val networkUtil: NetworkUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null
    private var currentAssistantMessageId: Long? = null
    private var currentStreamingContent = StringBuilder()

    init {
        checkConfiguration()
        observeNetworkStatus()
        loadConversations()
    }

    private fun checkConfiguration() {
        val config = repository.getApiConfig()
        _uiState.value = _uiState.value.copy(isConfigured = config.isConfigured)
    }

    fun refreshConfiguration() {
        checkConfiguration()
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            networkUtil.observeNetworkStatus().collect { isOnline ->
                _uiState.value = _uiState.value.copy(isOffline = !isOnline)
            }
        }
    }

    private fun loadConversations() {
        viewModelScope.launch {
            repository.getAllConversations().collect { conversations ->
                _uiState.value = _uiState.value.copy(conversations = conversations)
            }
        }
    }

    fun selectConversation(conversationId: Long) {
        _uiState.value = _uiState.value.copy(
            currentConversationId = conversationId,
            messages = emptyList()
        )
        loadMessages(conversationId)
    }

    private fun loadMessages(conversationId: Long) {
        viewModelScope.launch {
            repository.getMessagesForConversation(conversationId).collect { messages ->
                if (_uiState.value.currentConversationId == conversationId) {
                    _uiState.value = _uiState.value.copy(messages = messages)
                }
            }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val conversationId = repository.createConversation()
            selectConversation(conversationId)
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            if (_uiState.value.currentConversationId == conversationId) {
                _uiState.value = _uiState.value.copy(
                    currentConversationId = null,
                    messages = emptyList()
                )
            }
        }
    }

    fun clearCurrentConversation() {
        val conversationId = _uiState.value.currentConversationId ?: return
        viewModelScope.launch {
            repository.deleteMessagesForConversation(conversationId)
            repository.updateConversationTitle(conversationId, "New Chat")
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            // Create conversation if needed
            var conversationId = _uiState.value.currentConversationId
            if (conversationId == null) {
                conversationId = repository.createConversation()
                selectConversation(conversationId)
            }

            // Add user message
            repository.addMessage(conversationId, MessageRole.USER.value, text)

            // Update conversation title if first message
            val messages = repository.getMessagesForConversationSync(conversationId)
            if (messages.size == 1) {
                val title = repository.generateTitleFromMessage(text)
                repository.updateConversationTitle(conversationId, title)
            }

            // Clear input
            _uiState.value = _uiState.value.copy(inputText = "")

            // Start streaming response
            startStreaming(conversationId)
        }
    }

    private suspend fun startStreaming(conversationId: Long) {
        // Create placeholder for assistant message
        currentStreamingContent = StringBuilder()
        currentAssistantMessageId = repository.addMessage(
            conversationId,
            MessageRole.ASSISTANT.value,
            "",
            isStreaming = true
        )

        _uiState.value = _uiState.value.copy(isStreaming = true, error = null)

        // Get all messages for context
        val messages = repository.getMessagesForConversationSync(conversationId)
            .filter { !it.isStreaming && !it.isError }

        streamingJob = viewModelScope.launch {
            repository.streamChatCompletion(messages).collect { event ->
                when (event) {
                    is StreamEvent.Content -> {
                        currentStreamingContent.append(event.text)
                        currentAssistantMessageId?.let { messageId ->
                            repository.updateMessageContent(
                                messageId,
                                currentStreamingContent.toString(),
                                isStreaming = true
                            )
                        }
                    }
                    is StreamEvent.Error -> {
                        currentAssistantMessageId?.let { messageId ->
                            repository.updateMessageContent(
                                messageId,
                                event.message,
                                isStreaming = false
                            )
                            repository.updateMessageError(messageId, true)
                        }
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            error = event.message
                        )
                    }
                    StreamEvent.Done -> {
                        currentAssistantMessageId?.let { messageId ->
                            repository.updateMessageContent(
                                messageId,
                                currentStreamingContent.toString(),
                                isStreaming = false
                            )
                        }
                        _uiState.value = _uiState.value.copy(isStreaming = false)
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null

        currentAssistantMessageId?.let { messageId ->
            viewModelScope.launch {
                repository.updateMessageContent(
                    messageId,
                    currentStreamingContent.toString() + " [stopped]",
                    isStreaming = false
                )
            }
        }

        _uiState.value = _uiState.value.copy(isStreaming = false)
    }

    fun retryLastMessage() {
        val conversationId = _uiState.value.currentConversationId ?: return
        val messages = _uiState.value.messages

        // Find the last error message and remove it, then retry
        val lastMessage = messages.lastOrNull()
        if (lastMessage?.isError == true) {
            viewModelScope.launch {
                // We need to delete the error message and resend
                // For simplicity, we'll just start a new streaming attempt
                startStreaming(conversationId)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }
}
