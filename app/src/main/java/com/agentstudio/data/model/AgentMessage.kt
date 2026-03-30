package com.agentstudio.data.model

import java.util.UUID

sealed class AgentMessage {
    abstract val id: String
    abstract val timestamp: Long
    
    data class UserMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val content: String
    ) : AgentMessage()
    
    data class AgentResponse(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val content: String,
        val isStreaming: Boolean = false,
        val toolCalls: List<ToolCallInfo> = emptyList()
    ) : AgentMessage()
    
    data class ToolMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val toolCallId: String,
        val toolName: String,
        val arguments: String,
        val result: String,
        val isSuccess: Boolean
    ) : AgentMessage()
    
    data class SystemMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val content: String
    ) : AgentMessage()
    
    data class ToolCallInfo(
        val id: String,
        val name: String,
        val arguments: String
    )
}
