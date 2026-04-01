package com.agentstudio.data.api

import android.util.Log
import com.agentstudio.data.model.*
import com.agentstudio.domain.model.AgentTool
import com.agentstudio.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class OpenRouterApi(
    private val apiKey: String,
    private val modelId: String = Constants.MODEL_ID
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    fun streamChatFlow(
        messages: List<MessageContent>,
        tools: List<AgentTool>? = null
    ): Flow<StreamEvent> = flow {
        val toolDefinitions = tools?.map { it.toToolDefinition() }
        
        val requestJson = buildJsonObject {
            put("model", modelId)
            put("stream", true)
            put("max_tokens", 4096)
            put("temperature", 0.7)
            
            put("messages", buildJsonArray {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        msg.content?.let { put("content", it) }
                        
                        msg.toolCalls?.let { toolCallsList ->
                            put("tool_calls", buildJsonArray {
                                toolCallsList.forEach { tc ->
                                    addJsonObject {
                                        put("id", tc.id)
                                        put("type", tc.type)
                                        put("function", buildJsonObject {
                                            put("name", tc.function.name)
                                            // Ensure arguments is valid JSON string
                                            val args = tc.function.arguments.ifEmpty { "{}" }
                                            val validatedArgs = try {
                                                Json.parseToJsonElement(args)
                                                args
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Invalid tool args in request, using empty: ${args.take(50)}")
                                                "{}"
                                            }
                                            put("arguments", validatedArgs)
                                        })
                                    }
                                }
                            })
                        }
                        
                        msg.toolCallId?.let { put("tool_call_id", it) }
                        msg.name?.let { put("name", it) }
                    }
                }
            })
            
            toolDefinitions?.let { 
                put("tools", buildJsonArray {
                    it.forEach { td ->
                        addJsonObject {
                            put("type", td.type)
                            put("function", buildJsonObject {
                                put("name", td.function.name)
                                put("description", td.function.description)
                                put("parameters", buildJsonObject {
                                    put("type", td.function.parameters.type)
                                    put("required", buildJsonArray {
                                        td.function.parameters.required.forEach { add(it) }
                                    })
                                    put("properties", buildJsonObject {
                                        td.function.parameters.properties.forEach { (name, prop) ->
                                            put(name, buildJsonObject {
                                                put("type", prop.type)
                                                prop.description?.let { put("description", it) }
                                                prop.enum?.let { enumList ->
                                                    put("enum", buildJsonArray {
                                                        enumList.forEach { add(it) }
                                                    })
                                                }
                                            })
                                        }
                                    })
                                })
                            })
                        }
                    }
                })
                put("tool_choice", "auto")
            }
        }.toString()
        
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "API Request: ${Constants.OPENROUTER_BASE_URL}/chat/completions")
        Log.d(TAG, "Model: $modelId, Messages: ${messages.size}, Tools: ${toolDefinitions?.size ?: 0}")
        
        val requestBody = RequestBody.create("application/json".toMediaType(), requestJson)
        
        val httpRequest = Request.Builder()
            .url("${Constants.OPENROUTER_BASE_URL}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", Constants.APP_REFERER)
            .addHeader("X-Title", Constants.APP_NAME)
            .post(requestBody)
            .build()
        
        // Track tool calls being built
        val pendingToolCalls = mutableMapOf<Int, ToolCallBuilder>()
        var lastEmittedToolCallId = mutableSetOf<String>()
        
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "API Error: HTTP ${response.code} - $errorBody")
                emit(StreamEvent.Error("HTTP ${response.code}: $errorBody"))
                return@use
            }
            
            Log.d(TAG, "Stream started successfully")
            
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    
                    val dataLine = when {
                        line.startsWith("data: ") -> line.removePrefix("data: ").trim()
                        line.startsWith(":") -> continue
                        line.isBlank() -> continue
                        else -> continue
                    }
                    
                    if (dataLine == "[DONE]") {
                        Log.d(TAG, "Stream [DONE]")
                        
                        // Emit any remaining tool calls
                        pendingToolCalls.values.forEach { builder ->
                            if (builder.id.isNotEmpty() && builder.name.isNotEmpty() && builder.id !in lastEmittedToolCallId) {
                                val toolCall = builder.build()
                                Log.d(TAG, "Final tool call: ${toolCall.function.name}(${toolCall.function.arguments})")
                                emit(StreamEvent.ToolCall(toolCall))
                                lastEmittedToolCallId.add(builder.id)
                            }
                        }
                        
                        emit(StreamEvent.Complete)
                        return@use
                    }
                    
                    try {
                        val chunk = json.decodeFromString<StreamChunk>(dataLine)
                        
                        chunk.choices.forEach { choice ->
                            // Emit content
                            choice.delta.content?.let { content ->
                                if (content.isNotEmpty()) {
                                    emit(StreamEvent.Content(content))
                                }
                            }
                            
                            // Emit reasoning
                            choice.delta.reasoning?.let { reasoning ->
                                if (reasoning.isNotEmpty()) {
                                    emit(StreamEvent.Content(reasoning))
                                }
                            }
                            
                            // Handle tool calls
                            choice.delta.toolCalls?.forEach { deltaToolCall ->
                                val index = deltaToolCall.index
                                val builder = pendingToolCalls.getOrPut(index) { ToolCallBuilder() }
                                
                                deltaToolCall.id?.let { 
                                    builder.id = it
                                    Log.d(TAG, "Tool call id: $it (index: $index)")
                                }
                                
                                deltaToolCall.function?.name?.let { 
                                    builder.name = it
                                    Log.d(TAG, "Tool name: $it")
                                }
                                
                                deltaToolCall.function?.arguments?.let { args ->
                                    builder.arguments.append(args)
                                    Log.d(TAG, "Tool args chunk: $args (total: ${builder.arguments.length})")
                                }
                                
                                // Emit when complete (has id, name, and args)
                                if (builder.id.isNotEmpty() && 
                                    builder.name.isNotEmpty() && 
                                    builder.arguments.isNotEmpty() &&
                                    builder.id !in lastEmittedToolCallId) {
                                    
                                    val toolCall = builder.build()
                                    Log.d(TAG, "Complete tool call: ${toolCall.function.name}(${toolCall.function.arguments})")
                                    emit(StreamEvent.ToolCall(toolCall))
                                    lastEmittedToolCallId.add(builder.id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse error: ${dataLine.take(100)}", e)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private data class ToolCallBuilder(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    ) {
        fun build(): ToolCallRequest = ToolCallRequest(
            id = id.ifEmpty { "tc_${System.nanoTime()}" },
            type = "function",
            function = FunctionCallRequest(
                name = name,
                arguments = validateAndFixJson(arguments.toString())
            )
        )

        private fun validateAndFixJson(args: String): String {
            if (args.isBlank()) return "{}"

            val trimmed = args.trim()

            // Try to parse as JSON
            return try {
                // If it's valid JSON, return as-is
                Json.parseToJsonElement(trimmed)
                trimmed
            } catch (e: Exception) {
                Log.w("ToolCallBuilder", "Invalid JSON arguments: ${trimmed.take(100)}")

                // Try to fix common issues
                when {
                    // Missing opening brace
                    !trimmed.startsWith("{") && !trimmed.startsWith("[") -> {
                        try {
                            val fixed = "{$trimmed}"
                            Json.parseToJsonElement(fixed)
                            fixed
                        } catch (e2: Exception) {
                            "{}"
                        }
                    }
                    // Incomplete JSON - try to close it
                    trimmed.startsWith("{") && !trimmed.endsWith("}") -> {
                        try {
                            val fixed = "$trimmed}"
                            Json.parseToJsonElement(fixed)
                            fixed
                        } catch (e2: Exception) {
                            "{}"
                        }
                    }
                    else -> "{}"
                }
            }
        }
    }
    
    sealed class StreamEvent {
        data class Content(val text: String) : StreamEvent()
        data class ToolCall(val toolCall: ToolCallRequest) : StreamEvent()
        object Complete : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }
    
    companion object {
        private const val TAG = "OpenRouterApi"
    }
}
