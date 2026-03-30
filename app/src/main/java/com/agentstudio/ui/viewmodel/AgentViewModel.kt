package com.agentstudio.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentstudio.BuildConfig
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.AgentMessage
import com.agentstudio.data.model.ChatSession
import com.agentstudio.data.repository.AgentRepository
import com.agentstudio.data.repository.FileRepository
import com.agentstudio.domain.agent.AgentExecutor
import com.agentstudio.domain.agent.ToolHandler
import com.agentstudio.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    
    private val apiKey = BuildConfig.OPENROUTER_API_KEY
    private val openRouterApi = OpenRouterApi(apiKey)
    private val agentRepository = AgentRepository(openRouterApi)
    private val fileRepository = FileRepository(application)
    private val toolHandler = ToolHandler(fileRepository)
    private val agentExecutor = AgentExecutor(agentRepository, toolHandler, viewModelScope)
    
    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()
    
    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()
    
    private val _pendingToolCalls = MutableStateFlow<List<AgentExecutor.PendingToolCall>>(emptyList())
    val pendingToolCalls: StateFlow<List<AgentExecutor.PendingToolCall>> = _pendingToolCalls.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _agentState = MutableStateFlow<AgentExecutor.AgentState>(AgentExecutor.AgentState.Idle)
    val agentState: StateFlow<AgentExecutor.AgentState> = _agentState.asStateFlow()
    
    private var streamingMessageId: String? = null
    
    init {
        // Set default system prompt
        agentExecutor.setSystemPrompt(Constants.DEFAULT_SYSTEM_PROMPT)
        
        // Observe agent state
        viewModelScope.launch {
            agentExecutor.state.collect { state ->
                _agentState.value = state
                _isProcessing.value = state != AgentExecutor.AgentState.Idle
            }
        }
        
        // Observe current response
        viewModelScope.launch {
            agentExecutor.currentResponse.collect { response ->
                _streamingContent.value = response
            }
        }
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank() || _isProcessing.value) return
        
        viewModelScope.launch {
            _error.value = null
            
            // Add user message immediately to UI
            val userMessage = AgentMessage.UserMessage(content = content)
            addMessage(userMessage)
            
            // Add placeholder for agent response
            val agentMessage = AgentMessage.AgentResponse(
                content = "",
                isStreaming = true
            )
            streamingMessageId = agentMessage.id
            addMessage(agentMessage)
            
            agentExecutor.executeWithFlow(content) { event ->
                when (event) {
                    is AgentExecutor.ExecutionEvent.UserMessage -> {
                        // Already handled above
                    }
                    is AgentExecutor.ExecutionEvent.StreamChunk -> {
                        // Update streaming message
                        streamingMessageId?.let { id ->
                            updateMessage(id) { msg ->
                                (msg as? AgentMessage.AgentResponse)?.let {
                                    it.copy(content = it.content + event.text, isStreaming = true)
                                } ?: msg
                            }
                        }
                    }
                    is AgentExecutor.ExecutionEvent.ResponseComplete -> {
                        // Mark streaming as complete
                        streamingMessageId?.let { id ->
                            updateMessage(id) { msg ->
                                (msg as? AgentMessage.AgentResponse)?.let {
                                    it.copy(isStreaming = false)
                                } ?: msg
                            }
                        }
                        streamingMessageId = null
                    }
                    is AgentExecutor.ExecutionEvent.ToolCallReceived -> {
                        _pendingToolCalls.update { it + event.toolCall }
                    }
                    is AgentExecutor.ExecutionEvent.ToolExecutionStarted -> {
                        // Tool execution started
                    }
                    is AgentExecutor.ExecutionEvent.ToolExecutionComplete -> {
                        // Remove from pending
                        _pendingToolCalls.update { calls ->
                            calls.filter { it.id != event.id }
                        }
                        
                        // Add tool message
                        val toolMessage = AgentMessage.ToolMessage(
                            toolCallId = event.id,
                            toolName = event.name,
                            arguments = "",
                            result = event.result.output,
                            isSuccess = event.result.success
                        )
                        addMessage(toolMessage)
                    }
                    is AgentExecutor.ExecutionEvent.Complete -> {
                        streamingMessageId = null
                    }
                    is AgentExecutor.ExecutionEvent.Error -> {
                        _error.value = event.message
                        streamingMessageId = null
                    }
                }
            }
        }
    }
    
    fun cancelExecution() {
        agentExecutor.cancel()
        streamingMessageId = null
        _streamingContent.value = ""
    }
    
    fun clearChat() {
        agentExecutor.reset()
        _messages.value = emptyList()
        _currentSession.value = null
        _error.value = null
    }
    
    fun setSystemPrompt(prompt: String) {
        agentExecutor.setSystemPrompt(prompt)
    }
    
    private fun addMessage(message: AgentMessage) {
        _messages.update { it + message }
    }
    
    private fun updateMessage(id: String, update: (AgentMessage) -> AgentMessage) {
        _messages.update { messages ->
            messages.map { if (it.id == id) update(it) else it }
        }
    }
    
    fun retry() {
        // Get last user message and retry
        val lastUserMessage = _messages.value.lastOrNull { it is AgentMessage.UserMessage }
        if (lastUserMessage != null) {
            val content = (lastUserMessage as AgentMessage.UserMessage).content
            clearChat()
            sendMessage(content)
        }
    }
}

class AgentViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
            return AgentViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
