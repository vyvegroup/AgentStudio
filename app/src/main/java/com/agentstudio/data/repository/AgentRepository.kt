package com.agentstudio.data.repository

import android.util.Log
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.*
import com.agentstudio.domain.model.AgentTool
import com.agentstudio.domain.model.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.sse.EventSource
import java.util.UUID

class AgentRepository(private val api: OpenRouterApi) {
    
    private val _messageEvents = MutableSharedFlow<MessageEvent>()
    val messageEvents: Flow<MessageEvent> = _messageEvents.asSharedFlow()
    
    private var currentEventSource: EventSource? = null
    private val conversationHistory = mutableListOf<MessageContent>()
    
    suspend fun sendMessage(
        content: String,
        tools: List<AgentTool>? = null,
        onStreamChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onComplete: (OpenRouterResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        // Add user message to history
        conversationHistory.add(MessageContent(role = "user", content = content))
        
        currentEventSource = api.streamChat(
            messages = conversationHistory.toList(),
            tools = tools,
            onChunk = onStreamChunk,
            onToolCall = onToolCall,
            onComplete = { response ->
                response?.choices?.firstOrNull()?.message?.let { message ->
                    conversationHistory.add(message)
                }
                onComplete(response)
            },
            onError = onError
        )
    }
    
    fun sendToolResult(toolCallId: String, toolName: String, result: ToolResult) {
        val resultContent = if (result.success) {
            result.output
        } else {
            "Error: ${result.error}"
        }
        
        conversationHistory.add(
            MessageContent(
                role = "tool",
                content = resultContent,
                toolCallId = toolCallId,
                name = toolName
            )
        )
    }
    
    suspend fun continueWithToolResult(
        tools: List<AgentTool>? = null,
        onStreamChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onComplete: (OpenRouterResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        currentEventSource = api.streamChat(
            messages = conversationHistory.toList(),
            tools = tools,
            onChunk = onStreamChunk,
            onToolCall = onToolCall,
            onComplete = { response ->
                response?.choices?.firstOrNull()?.message?.let { message ->
                    conversationHistory.add(message)
                }
                onComplete(response)
            },
            onError = onError
        )
    }
    
    fun cancelStream() {
        currentEventSource?.cancel()
        currentEventSource = null
    }
    
    fun clearHistory() {
        conversationHistory.clear()
    }
    
    fun setSystemPrompt(prompt: String) {
        // Remove existing system message if any
        conversationHistory.removeAll { it.role == "system" }
        // Add new system message at the beginning
        conversationHistory.add(0, MessageContent(role = "system", content = prompt))
    }
    
    fun getConversationHistory(): List<MessageContent> = conversationHistory.toList()
    
    fun streamChatFlow(
        messages: List<MessageContent>,
        tools: List<AgentTool>? = null
    ): Flow<OpenRouterApi.StreamEvent> {
        return api.streamChatFlow(messages, tools)
    }
    
    sealed class MessageEvent {
        data class UserMessage(val content: String) : MessageEvent()
        data class StreamChunk(val content: String) : MessageEvent()
        data class ToolCall(val toolCall: ToolCallRequest) : MessageEvent()
        data class Complete(val response: OpenRouterResponse?) : MessageEvent()
        data class Error(val message: String) : MessageEvent()
    }
    
    companion object {
        private const val TAG = "AgentRepository"
    }
}
