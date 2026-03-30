package com.agentstudio.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// API Request Models
@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<MessageContent>,
    val stream: Boolean = true,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

@Serializable
data class MessageContent(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallRequest>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolCallRequest(
    val id: String,
    val type: String = "function",
    val function: FunctionCallRequest
)

@Serializable
data class FunctionCallRequest(
    val name: String,
    val arguments: String
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, PropertyDefinition>,
    val required: List<String> = emptyList()
)

@Serializable
data class PropertyDefinition(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null,
    @SerialName("default")
    val defaultValue: String? = null
)

// API Response Models
@Serializable
data class OpenRouterResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null,
    val model: String? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: MessageContent? = null,
    val delta: DeltaContent? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class DeltaContent(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeltaToolCall>? = null
)

@Serializable
data class DeltaToolCall(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: DeltaFunctionCall? = null
)

@Serializable
data class DeltaFunctionCall(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

// Streaming Models
@Serializable
data class StreamChunk(
    val id: String,
    val choices: List<StreamChoice>,
    val model: String? = null
)

@Serializable
data class StreamChoice(
    val index: Int,
    val delta: DeltaContent,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

// Error Response
@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null
)
