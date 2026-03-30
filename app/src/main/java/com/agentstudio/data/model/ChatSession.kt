package com.agentstudio.data.model

import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<AgentMessage> = emptyList(),
    val systemPrompt: String = "",
    val model: String = "",
    val totalTokens: Int = 0
) {
    val messageCount: Int
        get() = messages.size
    
    val preview: String
        get() = when (val first = messages.firstOrNull()) {
            is AgentMessage.UserMessage -> first.content.take(100)
            is AgentMessage.AgentResponse -> first.content.take(100)
            is AgentMessage.SystemMessage -> first.content.take(100)
            is AgentMessage.ToolMessage -> "[${first.toolName}]"
            null -> "Phiên chat trống"
        }
    
    fun withNewMessage(message: AgentMessage): ChatSession {
        return copy(
            messages = messages + message,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    fun withUpdatedMessage(messageId: String, newMessage: AgentMessage): ChatSession {
        return copy(
            messages = messages.map { if (it.id == messageId) newMessage else it },
            updatedAt = System.currentTimeMillis()
        )
    }
}
