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
        
        // Build JSON using JsonObject builder to avoid Any serialization issues
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
                        
                        // Handle tool_calls - must ensure arguments is always a string
                        msg.toolCalls?.let { toolCallsList ->
                            put("tool_calls", buildJsonArray {
                                toolCallsList.forEach { tc ->
                                    addJsonObject {
                                        put("id", tc.id)
                                        put("type", tc.type)
                                        put("function", buildJsonObject {
                                            put("name", tc.function.name)
                                            // CRITICAL: arguments must always be a string, even if empty
                                            put("arguments", tc.function.arguments.ifEmpty { "{}" })
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
        Log.d(TAG, "API Request to: ${Constants.OPENROUTER_BASE_URL}/chat/completions")
        Log.d(TAG, "Model: $modelId")
        Log.d(TAG, "Messages: ${messages.size}")
        Log.d(TAG, "Tools: ${toolDefinitions?.size ?: 0}")
        
        toolDefinitions?.forEach { td ->
            Log.d(TAG, "  Tool: ${td.function.name} - ${td.function.description?.take(50)}...")
        }
        
        messages.forEachIndexed { index, msg ->
            val contentPreview = msg.content?.take(50) ?: if (msg.toolCalls != null) "tool_calls(${msg.toolCalls.size})" else "tool_result"
            Log.d(TAG, "  Msg[$index]: ${msg.role} - $contentPreview...")
        }
        
        Log.d(TAG, "Request JSON length: ${requestJson.length}")
        
        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            requestJson
        )
        
        val httpRequest = Request.Builder()
            .url("${Constants.OPENROUTER_BASE_URL}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", Constants.APP_REFERER)
            .addHeader("X-Title", Constants.APP_NAME)
            .post(requestBody)
            .build()
        
        val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
        var lastEmittedToolCall = mutableSetOf<String>()
        
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "API Error: HTTP ${response.code}")
                Log.e(TAG, "Error body: $errorBody")
                emit(StreamEvent.Error("HTTP ${response.code}: $errorBody"))
                return@use
            }
            
            Log.d(TAG, "═══════════════════════════════════════════")
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
                        Log.d(TAG, "Stream [DONE] received")
                        
                        toolCalls.values.forEach { builder ->
                            if (builder.isComplete() && builder.id !in lastEmittedToolCall) {
                                val toolCall = builder.build()
                                Log.d(TAG, "Final tool call: ${toolCall.function.name}")
                                emit(StreamEvent.ToolCall(toolCall))
                                lastEmittedToolCall.add(builder.id)
                            }
                        }
                        
                        emit(StreamEvent.Complete)
                        return@use
                    }
                    
                    try {
                        val chunk = json.decodeFromString<StreamChunk>(dataLine)
                        chunk.choices.forEach { choice ->
                            choice.delta.content?.let { content ->
                                if (content.isNotEmpty()) {
                                    emit(StreamEvent.Content(content))
                                }
                            }
                            
                            choice.delta.reasoning?.let { reasoning ->
                                if (reasoning.isNotEmpty()) {
                                    emit(StreamEvent.Content(reasoning))
                                }
                            }
                            
                            choice.delta.toolCalls?.forEach { deltaToolCall ->
                                val index = deltaToolCall.index
                                val builder = toolCalls.getOrPut(index) { ToolCallBuilder() }
                                
                                deltaToolCall.id?.let { 
                                    builder.id = it
                                }
                                deltaToolCall.function?.name?.let { 
                                    builder.name = it
                                    Log.d(TAG, "Tool name received: $it (index: $index)")
                                }
                                deltaToolCall.function?.arguments?.let { args ->
                                    builder.arguments.append(args)
                                    
                                    if (builder.isComplete() && builder.id !in lastEmittedToolCall) {
                                        val toolCall = builder.build()
                                        Log.d(TAG, "Complete tool call: ${toolCall.function.name} with ${toolCall.function.arguments.length} chars args")
                                        emit(StreamEvent.ToolCall(toolCall))
                                        lastEmittedToolCall.add(builder.id)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing chunk: ${dataLine.take(100)}...", e)
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
        fun isComplete(): Boolean = id.isNotEmpty() && name.isNotEmpty()
        
        fun build(): ToolCallRequest = ToolCallRequest(
            id = id,
            type = "function",
            function = FunctionCallRequest(
                name = name,
                // Ensure arguments is always a valid JSON string
                arguments = arguments.toString().ifEmpty { "{}" }
            )
        )
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
