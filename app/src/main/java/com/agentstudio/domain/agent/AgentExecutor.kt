package com.agentstudio.domain.agent

import android.util.Log
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.*
import com.agentstudio.domain.model.AgentTools
import com.agentstudio.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AgentExecutor(
    private val api: OpenRouterApi,
    private val toolHandler: ToolHandler,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()
    
    private val _debugLog = MutableStateFlow(StringBuilder())
    val debugLog: StateFlow<StringBuilder> = _debugLog.asStateFlow()
    
    private var executionJob: Job? = null
    private var systemPrompt: String = ""
    
    // Conversation history for context
    private val conversationHistory = mutableListOf<MessageContent>()
    
    fun executeWithFlow(
        userInput: String,
        onEvent: (ExecutionEvent) -> Unit
    ) {
        if (_state.value != AgentState.Idle) {
            Log.w(TAG, "Agent is already executing")
            return
        }
        
        executionJob = scope.launch {
            try {
                _state.value = AgentState.Processing
                _debugLog.value = StringBuilder()
                
                appendDebug("╔══════════════════════════════════════════╗")
                appendDebug("║          NEW EXECUTION STARTED           ║")
                appendDebug("╚══════════════════════════════════════════╝")
                appendDebug("📝 User Input: ${userInput.take(100)}...")
                
                // Build messages with history
                val messages = mutableListOf<MessageContent>()
                
                // Add system prompt
                if (systemPrompt.isNotEmpty()) {
                    messages.add(MessageContent(role = "system", content = systemPrompt))
                    appendDebug("📋 System prompt added (${systemPrompt.length} chars)")
                }
                
                // Add conversation history
                messages.addAll(conversationHistory)
                appendDebug("📚 History: ${conversationHistory.size} messages")
                
                // Add current user message
                val userMessage = MessageContent(role = "user", content = userInput)
                messages.add(userMessage)
                conversationHistory.add(userMessage)
                
                onEvent(ExecutionEvent.UserMessage(userInput))
                
                var iteration = 0
                val maxIterations = 15
                
                do {
                    iteration++
                    appendDebug("\n┌──────────────────────────────────────────┐")
                    appendDebug("│  ITERATION $iteration/$maxIterations")
                    appendDebug("└──────────────────────────────────────────┘")
                    
                    val toolCalls = mutableListOf<ToolCallRequest>()
                    var responseContent = StringBuilder()
                    var responseComplete = false
                    
                    // Stream the response
                    api.streamChatFlow(messages, AgentTools.ALL_TOOLS).collect { event ->
                        when (event) {
                            is OpenRouterApi.StreamEvent.Content -> {
                                responseContent.append(event.text)
                                onEvent(ExecutionEvent.StreamChunk(event.text))
                            }
                            is OpenRouterApi.StreamEvent.ToolCall -> {
                                appendDebug("🔧 TOOL CALL: ${event.toolCall.function.name}")
                                appendDebug("   Args: ${event.toolCall.function.arguments.take(100)}...")
                                toolCalls.add(event.toolCall)
                            }
                            is OpenRouterApi.StreamEvent.Complete -> {
                                responseComplete = true
                                appendDebug("✅ Stream complete")
                            }
                            is OpenRouterApi.StreamEvent.Error -> {
                                appendDebug("❌ ERROR: ${event.message}")
                                _state.value = AgentState.Error(event.message)
                                onEvent(ExecutionEvent.Error(event.message))
                            }
                        }
                    }
                    
                    // Add assistant message to history
                    val assistantContent = responseContent.toString()
                    if (assistantContent.isNotEmpty() || toolCalls.isNotEmpty()) {
                        val assistantMessage = MessageContent(
                            role = "assistant",
                            content = assistantContent.ifEmpty { null },
                            toolCalls = if (toolCalls.isNotEmpty()) toolCalls else null
                        )
                        messages.add(assistantMessage)
                        conversationHistory.add(assistantMessage)
                        
                        if (assistantContent.isNotEmpty()) {
                            onEvent(ExecutionEvent.ResponseComplete(assistantContent))
                            appendDebug("💬 Response: ${assistantContent.take(150)}...")
                        }
                    }
                    
                    // Execute tools if any
                    if (toolCalls.isNotEmpty()) {
                        _state.value = AgentState.ExecutingTools
                        appendDebug("\n⚡ EXECUTING ${toolCalls.size} TOOL(S)")
                        
                        for (toolCall in toolCalls) {
                            val toolName = toolCall.function.name
                            val toolArgs = toolCall.function.arguments
                            
                            appendDebug("\n┌─────────────────────────────────────────┐")
                            appendDebug("│ 🛠️  $toolName")
                            appendDebug("└─────────────────────────────────────────┘")
                            
                            onEvent(ExecutionEvent.ToolExecutionStarted(
                                toolCall.id,
                                toolName
                            ))
                            
                            val result = toolHandler.executeTool(toolName, toolArgs)
                            
                            appendDebug("📊 Result: ${if (result.success) "✅ SUCCESS" else "❌ FAILED"}")
                            if (result.success) {
                                appendDebug("📄 Output: ${result.output.take(200)}${if (result.output.length > 200) "..." else ""}")
                            } else {
                                appendDebug("⚠️ Error: ${result.error}")
                            }
                            
                            onEvent(ExecutionEvent.ToolExecutionComplete(
                                toolCall.id,
                                toolName,
                                result
                            ))
                            
                            // Add tool result to conversation
                            val toolMessage = MessageContent(
                                role = "tool",
                                content = if (result.success) result.output else "Error: ${result.error}",
                                toolCallId = toolCall.id,
                                name = toolName
                            )
                            messages.add(toolMessage)
                            conversationHistory.add(toolMessage)
                        }
                    }
                    
                    delay(100)
                    
                } while (toolCalls.isNotEmpty() && iteration < maxIterations)
                
                if (iteration >= maxIterations) {
                    appendDebug("\n⚠️ Max iterations reached!")
                }
                
                appendDebug("\n╔══════════════════════════════════════════╗")
                appendDebug("║        EXECUTION COMPLETE                ║")
                appendDebug("║  Iterations: $iteration")
                appendDebug("║  History size: ${conversationHistory.size}")
                appendDebug("╚══════════════════════════════════════════╝")
                
                _state.value = AgentState.Idle
                onEvent(ExecutionEvent.Complete)
                
            } catch (e: Exception) {
                Log.e(TAG, "Execution error", e)
                appendDebug("\n❌ EXCEPTION: ${e.message}")
                e.printStackTrace()
                _state.value = AgentState.Error(e.message ?: "Unknown error")
                onEvent(ExecutionEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }
    
    private fun appendDebug(message: String) {
        _debugLog.value = StringBuilder(_debugLog.value).append("\n$message")
        Log.d(TAG, message)
    }
    
    fun getDebugLog(): String = _debugLog.value.toString()
    
    fun cancel() {
        executionJob?.cancel()
        executionJob = null
        _state.value = AgentState.Idle
    }
    
    fun reset() {
        cancel()
        conversationHistory.clear()
        _debugLog.value = StringBuilder()
    }
    
    fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }
    
    sealed class AgentState {
        object Idle : AgentState()
        object Processing : AgentState()
        object ExecutingTools : AgentState()
        data class Error(val message: String) : AgentState()
    }
    
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
    
    data class PendingToolCall(
        val id: String,
        val name: String,
        val arguments: String
    )
    
    companion object {
        private const val TAG = "AgentExecutor"
    }
}
