package com.agentstudio.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentstudio.BuildConfig
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.AgentMessage
import com.agentstudio.data.repository.FileRepository
import com.agentstudio.domain.agent.AgentExecutor
import com.agentstudio.domain.agent.ToolHandler
import com.agentstudio.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PREFS_NAME = "agent_studio_prefs"
private const val KEY_MODEL_ID = "selected_model_id"

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Dynamic model selection
    private var currentModelId: String = prefs.getString(KEY_MODEL_ID, Constants.MODEL_ID) ?: Constants.MODEL_ID
    
    private val apiKey = BuildConfig.OPENROUTER_API_KEY
    private var api = OpenRouterApi(apiKey, currentModelId)
    private val fileRepository = FileRepository(application)
    private val toolHandler = ToolHandler(fileRepository)
    private var agentExecutor = AgentExecutor(api, toolHandler, viewModelScope)
    
    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _debugLog = MutableStateFlow("")
    val debugLog: StateFlow<String> = _debugLog.asStateFlow()
    
    private val _showDebug = MutableStateFlow(false)
    val showDebug: StateFlow<Boolean> = _showDebug.asStateFlow()
    
    private val _currentModel = MutableStateFlow(currentModelId)
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()
    
    private var currentStreamingMessageId: String? = null
    
    init {
        agentExecutor.setSystemPrompt(Constants.DEFAULT_SYSTEM_PROMPT)
        
        viewModelScope.launch {
            agentExecutor.state.collect { state ->
                _isProcessing.value = state != AgentExecutor.AgentState.Idle
            }
        }
        
        viewModelScope.launch {
            agentExecutor.debugLog.collect { log ->
                _debugLog.value = log.toString()
            }
        }
    }
    
    fun setModel(modelId: String) {
        if (modelId != currentModelId) {
            currentModelId = modelId
            _currentModel.value = modelId
            prefs.edit().putString(KEY_MODEL_ID, modelId).apply()
            
            // Recreate API and executor with new model
            api = OpenRouterApi(apiKey, modelId)
            agentExecutor = AgentExecutor(api, toolHandler, viewModelScope)
            agentExecutor.setSystemPrompt(Constants.DEFAULT_SYSTEM_PROMPT)
        }
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank() || _isProcessing.value) return
        
        viewModelScope.launch {
            _error.value = null
            currentStreamingMessageId = null
            
            // Add user message
            val userMessage = AgentMessage.UserMessage(content = content)
            addMessage(userMessage)
            
            // Create streaming message placeholder
            val messageId = java.util.UUID.randomUUID().toString()
            currentStreamingMessageId = messageId
            
            agentExecutor.executeWithFlow(content) { event ->
                when (event) {
                    is AgentExecutor.ExecutionEvent.UserMessage -> {
                        // Already added above
                    }
                    is AgentExecutor.ExecutionEvent.StreamChunk -> {
                        updateOrCreateStreamingMessage(messageId) { current ->
                            (current as? AgentMessage.AgentResponse)?.let {
                                it.copy(content = it.content + event.text, isStreaming = true)
                            } ?: AgentMessage.AgentResponse(
                                id = messageId,
                                content = event.text,
                                isStreaming = true
                            )
                        }
                    }
                    is AgentExecutor.ExecutionEvent.ResponseComplete -> {
                        finalizeStreamingMessage(messageId, event.content)
                        currentStreamingMessageId = null
                    }
                    is AgentExecutor.ExecutionEvent.ToolCallReceived -> {
                        // Show tool call indicator if needed
                    }
                    is AgentExecutor.ExecutionEvent.ToolExecutionStarted -> {
                        // Show loading indicator with tool name
                        val loadingMessage = AgentMessage.ToolMessage(
                            toolCallId = event.id,
                            toolName = event.name,
                            arguments = "",
                            result = "⏳ Executing ${event.name}...",
                            isSuccess = true
                        )
                        addMessage(loadingMessage)
                    }
                    is AgentExecutor.ExecutionEvent.ToolExecutionComplete -> {
                        // Update tool result message
                        updateToolMessage(event.id, event.name, event.result.output, event.result.success)
                        
                        // Create new streaming message for next AI response
                        currentStreamingMessageId = java.util.UUID.randomUUID().toString()
                    }
                    is AgentExecutor.ExecutionEvent.Complete -> {
                        currentStreamingMessageId = null
                        // Finalize any remaining streaming message
                        _messages.update { messages ->
                            messages.map { msg ->
                                if (msg is AgentMessage.AgentResponse && msg.isStreaming) {
                                    msg.copy(isStreaming = false)
                                } else msg
                            }
                        }
                    }
                    is AgentExecutor.ExecutionEvent.Error -> {
                        _error.value = event.message
                        currentStreamingMessageId = null
                        // Finalize streaming message on error
                        _messages.update { messages ->
                            messages.map { msg ->
                                if (msg is AgentMessage.AgentResponse && msg.isStreaming) {
                                    msg.copy(isStreaming = false)
                                } else msg
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun addMessage(message: AgentMessage) {
        _messages.update { it + message }
    }
    
    private fun updateOrCreateStreamingMessage(id: String, update: (AgentMessage?) -> AgentMessage) {
        _messages.update { messages ->
            val existingIndex = messages.indexOfFirst { it.id == id }
            if (existingIndex >= 0) {
                messages.mapIndexed { index, msg ->
                    if (index == existingIndex) update(msg) else msg
                }
            } else {
                messages + update(null)
            }
        }
    }
    
    private fun finalizeStreamingMessage(id: String, content: String) {
        _messages.update { messages ->
            messages.map { msg ->
                if (msg.id == id) {
                    AgentMessage.AgentResponse(
                        id = id,
                        content = content,
                        isStreaming = false
                    )
                } else msg
            }
        }
    }
    
    private fun updateToolMessage(toolCallId: String, toolName: String, result: String, success: Boolean) {
        _messages.update { messages ->
            messages.map { msg ->
                if (msg is AgentMessage.ToolMessage && msg.toolCallId == toolCallId && msg.result.contains("Executing")) {
                    msg.copy(
                        result = result.take(1500) + if (result.length > 1500) "\n... (truncated)" else "",
                        isSuccess = success
                    )
                } else msg
            }
        }
    }
    
    fun toggleDebug() {
        _showDebug.value = !_showDebug.value
    }
    
    fun cancelExecution() {
        agentExecutor.cancel()
        currentStreamingMessageId = null
        _isProcessing.value = false
    }
    
    fun clearChat() {
        agentExecutor.reset()
        _messages.value = emptyList()
        _error.value = null
        _debugLog.value = ""
        currentStreamingMessageId = null
    }
    
    fun setSystemPrompt(prompt: String) {
        agentExecutor.setSystemPrompt(prompt)
    }
    
    fun retry() {
        val lastUserMessage = _messages.value.lastOrNull { it is AgentMessage.UserMessage }
        if (lastUserMessage != null) {
            val content = (lastUserMessage as AgentMessage.UserMessage).content
            clearChat()
            sendMessage(content)
        }
    }
    
    fun clearError() {
        _error.value = null
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
