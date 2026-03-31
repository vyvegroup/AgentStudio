package com.agentstudio.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentstudio.data.agent.AgentExecutor
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.ChatMessage
import com.agentstudio.data.model.FREE_MODELS
import com.agentstudio.data.repository.PreferencesRepository
import com.agentstudio.ui.components.ChatMessageUi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()
    
    private val _selectedModel = MutableStateFlow(FREE_MODELS.first().id)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val conversationHistory = mutableListOf<ChatMessage>()
    private var apiKey: String = ""
    
    init {
        viewModelScope.launch {
            preferencesRepository.selectedModel.collect { model ->
                _selectedModel.value = model
            }
        }
        viewModelScope.launch {
            preferencesRepository.apiKey.collect { key ->
                apiKey = key
            }
        }
    }
    
    fun setApiKey(key: String) {
        viewModelScope.launch {
            apiKey = key
            preferencesRepository.setApiKey(key)
        }
    }
    
    fun setModel(modelId: String) {
        viewModelScope.launch {
            _selectedModel.value = modelId
            preferencesRepository.setSelectedModel(modelId)
        }
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (apiKey.isBlank()) {
            _error.value = "Please enter your API key first"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Add user message
            val userMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = userMessageId,
                    content = content,
                    isUser = true
                )
            }
            
            // Add to conversation history
            conversationHistory.add(ChatMessage(role = "user", content = content))
            
            // Add placeholder for AI response
            val aiMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = aiMessageId,
                    content = "",
                    isUser = false,
                    isStreaming = true
                )
            }
            
            try {
                val api = OpenRouterApi(apiKey)
                val executor = AgentExecutor(api, _selectedModel.value)
                val responseContent = StringBuilder()
                val toolCalls = mutableListOf<String>()
                
                executor.execute(conversationHistory.toList()).collect { event ->
                    when (event) {
                        is AgentExecutor.AgentEvent.Token -> {
                            responseContent.append(event.content)
                            _messages.update { current ->
                                current.map { msg ->
                                    if (msg.id == aiMessageId) {
                                        msg.copy(content = responseContent.toString())
                                    } else msg
                                }
                            }
                        }
                        
                        is AgentExecutor.AgentEvent.ToolCallStart -> {
                            toolCalls.add("Using: ${event.name}")
                            _messages.update { current ->
                                current.map { msg ->
                                    if (msg.id == aiMessageId) {
                                        msg.copy(toolCalls = toolCalls.toList())
                                    } else msg
                                }
                            }
                        }
                        
                        is AgentExecutor.AgentEvent.ToolResult -> {
                            // Tool execution complete
                        }
                        
                        is AgentExecutor.AgentEvent.Error -> {
                            _error.value = event.message
                            _messages.update { current ->
                                current.map { msg ->
                                    if (msg.id == aiMessageId) {
                                        msg.copy(
                                            content = "Error: ${event.message}",
                                            isStreaming = false
                                        )
                                    } else msg
                                }
                            }
                        }
                        
                        AgentExecutor.AgentEvent.Complete -> {
                            _messages.update { current ->
                                current.map { msg ->
                                    if (msg.id == aiMessageId) {
                                        msg.copy(isStreaming = false)
                                    } else msg
                                }
                            }
                            
                            // Add to conversation history
                            conversationHistory.add(ChatMessage(
                                role = "assistant",
                                content = responseContent.toString()
                            ))
                        }
                        
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                _messages.update { current ->
                    current.map { msg ->
                        if (msg.id == aiMessageId) {
                            msg.copy(
                                content = "Error: ${e.message}",
                                isStreaming = false
                            )
                        } else msg
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
        conversationHistory.clear()
    }
    
    fun clearError() {
        _error.value = null
    }
}
