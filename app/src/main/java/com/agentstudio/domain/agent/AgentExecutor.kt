package com.agentstudio.domain.agent

import android.util.Log
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.*
import com.agentstudio.data.repository.AgentRepository
import com.agentstudio.domain.model.AgentTools
import com.agentstudio.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AgentExecutor(
    private val agentRepository: AgentRepository,
    private val toolHandler: ToolHandler,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()
    
    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()
    
    private val _pendingToolCalls = MutableStateFlow<List<PendingToolCall>>(emptyList())
    val pendingToolCalls: StateFlow<List<PendingToolCall>> = _pendingToolCalls.asStateFlow()
    
    private var executionJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }
    
    fun execute(
        userInput: String,
        onMessage: (AgentMessage) -> Unit,
        onToolCall: (PendingToolCall) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_state.value != AgentState.Idle) {
            Log.w(TAG, "Agent is already executing, ignoring new request")
            return
        }
        
        executionJob = scope.launch {
            try {
                _state.value = AgentState.Processing
                _currentResponse.value = ""
                _pendingToolCalls.value = emptyList()
                
                // Add user message immediately
                onMessage(AgentMessage.UserMessage(content = userInput))
                
                var hasToolCalls = false
                var iteration = 0
                val maxIterations = 10
                
                do {
                    hasToolCalls = false
                    
                    agentRepository.sendMessage(
                        content = if (iteration == 0) userInput else "",
                        tools = AgentTools.ALL_TOOLS,
                        onStreamChunk = { chunk ->
                            _currentResponse.value += chunk
                        },
                        onToolCall = { toolCall ->
                            hasToolCalls = true
                            val pending = PendingToolCall(
                                id = toolCall.id,
                                name = toolCall.function.name,
                                arguments = toolCall.function.arguments
                            )
                            _pendingToolCalls.value += pending
                            onToolCall(pending)
                        },
                        onComplete = { response ->
                            Log.d(TAG, "Stream completed")
                        },
                        onError = { error ->
                            _state.value = AgentState.Error(error)
                            onError(error)
                        }
                    )
                    
                    // Wait for streaming to complete
                    while (_state.value == AgentState.Processing && _currentResponse.value.isNotEmpty()) {
                        kotlinx.coroutines.delay(100)
                    }
                    
                    // Finalize current response
                    val responseText = _currentResponse.value
                    if (responseText.isNotEmpty()) {
                        onMessage(AgentMessage.AgentResponse(
                            content = responseText,
                            isStreaming = false
                        ))
                        _currentResponse.value = ""
                    }
                    
                    // Execute pending tool calls
                    if (_pendingToolCalls.value.isNotEmpty()) {
                        _state.value = AgentState.ExecutingTools
                        
                        _pendingToolCalls.value.forEach { pendingCall ->
                            val result = toolHandler.executeTool(pendingCall.name, pendingCall.arguments)
                            
                            onMessage(AgentMessage.ToolMessage(
                                toolCallId = pendingCall.id,
                                toolName = pendingCall.name,
                                arguments = pendingCall.arguments,
                                result = result.output,
                                isSuccess = result.success
                            ))
                            
                            // Send tool result back to agent
                            agentRepository.sendToolResult(
                                toolCallId = pendingCall.id,
                                toolName = pendingCall.name,
                                result = result
                            )
                        }
                        
                        _pendingToolCalls.value = emptyList()
                    }
                    
                    iteration++
                    
                } while (hasToolCalls && iteration < maxIterations)
                
                _state.value = AgentState.Idle
                onComplete()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during execution", e)
                _state.value = AgentState.Error(e.message ?: "Unknown error")
                onError(e.message ?: "Unknown error")
            }
        }
    }
    
    fun executeWithFlow(
        userInput: String,
        onEvent: (ExecutionEvent) -> Unit
    ) {
        if (_state.value != AgentState.Idle) {
            Log.w(TAG, "Agent is already executing, ignoring new request")
            return
        }
        
        executionJob = scope.launch {
            try {
                _state.value = AgentState.Processing
                _currentResponse.value = ""
                
                onEvent(ExecutionEvent.UserMessage(userInput))
                
                // Build messages list
                val messages = mutableListOf<MessageContent>()
                messages.add(MessageContent(role = "user", content = userInput))
                
                var iteration = 0
                val maxIterations = 10
                
                do {
                    val events = mutableListOf<ToolCallRequest>()
                    
                    agentRepository.streamChatFlow(
                        messages = messages,
                        tools = AgentTools.ALL_TOOLS
                    ).collect { event ->
                        when (event) {
                            is OpenRouterApi.StreamEvent.Content -> {
                                _currentResponse.value += event.text
                                onEvent(ExecutionEvent.StreamChunk(event.text))
                            }
                            is OpenRouterApi.StreamEvent.ToolCall -> {
                                events.add(event.toolCall)
                                onEvent(ExecutionEvent.ToolCallReceived(
                                    PendingToolCall(
                                        id = event.toolCall.id,
                                        name = event.toolCall.function.name,
                                        arguments = event.toolCall.function.arguments
                                    )
                                ))
                            }
                            is OpenRouterApi.StreamEvent.Complete -> {
                                // Done
                            }
                            is OpenRouterApi.StreamEvent.Error -> {
                                _state.value = AgentState.Error(event.message)
                                onEvent(ExecutionEvent.Error(event.message))
                            }
                        }
                    }
                    
                    // Add assistant message
                    if (_currentResponse.value.isNotEmpty()) {
                        messages.add(MessageContent(
                            role = "assistant",
                            content = _currentResponse.value
                        ))
                        onEvent(ExecutionEvent.ResponseComplete(_currentResponse.value))
                        _currentResponse.value = ""
                    }
                    
                    // Execute tools
                    if (events.isNotEmpty()) {
                        _state.value = AgentState.ExecutingTools
                        
                        for (toolCall in events) {
                            onEvent(ExecutionEvent.ToolExecutionStarted(
                                toolCall.id,
                                toolCall.function.name
                            ))
                            
                            val result = toolHandler.executeTool(
                                toolCall.function.name,
                                toolCall.function.arguments
                            )
                            
                            onEvent(ExecutionEvent.ToolExecutionComplete(
                                toolCall.id,
                                toolCall.function.name,
                                result
                            ))
                            
                            // Add tool result to messages
                            messages.add(MessageContent(
                                role = "tool",
                                content = if (result.success) result.output else "Error: ${result.error}",
                                toolCallId = toolCall.id,
                                name = toolCall.function.name
                            ))
                        }
                    }
                    
                    iteration++
                    
                } while (events.isNotEmpty() && iteration < maxIterations)
                
                _state.value = AgentState.Idle
                onEvent(ExecutionEvent.Complete)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during execution", e)
                _state.value = AgentState.Error(e.message ?: "Unknown error")
                onEvent(ExecutionEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }
    
    fun cancel() {
        executionJob?.cancel()
        executionJob = null
        agentRepository.cancelStream()
        _state.value = AgentState.Idle
        _currentResponse.value = ""
        _pendingToolCalls.value = emptyList()
    }
    
    fun reset() {
        cancel()
        agentRepository.clearHistory()
    }
    
    fun setSystemPrompt(prompt: String) {
        agentRepository.setSystemPrompt(prompt)
    }
    
    sealed class AgentState {
        object Idle : AgentState()
        object Processing : AgentState()
        object ExecutingTools : AgentState()
        data class Error(val message: String) : AgentState()
    }
    
    data class PendingToolCall(
        val id: String,
        val name: String,
        val arguments: String
    )
    
    sealed class ExecutionEvent {
        data class UserMessage(val content: String) : ExecutionEvent()
        data class StreamChunk(val text: String) : ExecutionEvent()
        data class ResponseComplete(val content: String) : ExecutionEvent()
        data class ToolCallReceived(val toolCall: PendingToolCall) : ExecutionEvent()
        data class ToolExecutionStarted(val id: String, val name: String) : ExecutionEvent()
        data class ToolExecutionComplete(val id: String, val name: String, val result: ToolResult) : ExecutionEvent()
        object Complete : ExecutionEvent()
        data class Error(val message: String) : ExecutionEvent()
    }
    
    companion object {
        private const val TAG = "AgentExecutor"
    }
}
