package com.agentstudio.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

@Serializable
data class ToolCallFunction(
    val name: String,
    // CRITICAL: arguments must ALWAYS be a valid JSON string, never null or missing
    val arguments: String = "{}"
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = "auto",
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 4096
)

@Serializable
data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val delta: ChatDelta? = null,
    val message: ChatMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ChatDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallDelta>? = null
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunctionDelta? = null
)

@Serializable
data class ToolCallFunctionDelta(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: Int? = null
)

// Agent Tool Models
@Serializable
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ToolResult(
    @SerialName("tool_call_id")
    val toolCallId: String,
    val role: String = "tool",
    val content: String
)

// Model info
data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val isFree: Boolean = false,
    val isLocal: Boolean = false,
    val requiresDownload: Boolean = false,
    val downloadSize: String? = null
)

// Free models only
val FREE_MODELS = listOf(
    ModelInfo(
        id = "stepfun/step-3.5-flash:free",
        name = "Step 3.5 Flash",
        provider = "StepFun",
        isFree = true
    ),
    ModelInfo(
        id = "qwen/qwen3.6-plus-preview:free",
        name = "Qwen 3.6 Plus",
        provider = "Alibaba",
        isFree = true
    ),
    ModelInfo(
        id = "z-ai/glm-4.5-air:free",
        name = "GLM 4.5 Air",
        provider = "Zhipu AI",
        isFree = true
    )
)

// Local AI Model (runs on device)
val LOCAL_MODEL = ModelInfo(
    id = "local-gemma-3-4b",
    name = "Gemma 3 4B VL",
    provider = "Local Device",
    isFree = true,
    isLocal = true,
    requiresDownload = true,
    downloadSize = "~4.5 GB"
)

// All available models including local
val ALL_MODELS = FREE_MODELS + LOCAL_MODEL
