package com.agentstudio.data.api

import android.util.Log
import com.agentstudio.data.model.*
import com.agentstudio.domain.model.AgentTool
import com.agentstudio.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        explicitNulls = false  // Don't encode null values
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
        
        // Build request with proper structure
        val requestMap = buildMap {
            put("model", modelId)
            put("messages", messages.map { msg ->
                buildMap {
                    put("role", msg.role)
                    msg.content?.let { put("content", it) }
                    msg.toolCalls?.let { put("tool_calls", it) }
                    msg.toolCallId?.let { put("tool_call_id", it) }
                    msg.name?.let { put("name", it) }
                }
            })
            put("stream", true)
            toolDefinitions?.let { 
                put("tools", it)
                put("tool_choice", "auto")
            }
            put("max_tokens", 4096)
            put("temperature", 0.7)
        }
        
        val requestJson = json.encodeToString(requestMap)
        
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "API Request to: ${Constants.OPENROUTER_BASE_URL}/chat/completions")
        Log.d(TAG, "Model: $modelId")
        Log.d(TAG, "Messages: ${messages.size}")
        Log.d(TAG, "Tools: ${toolDefinitions?.size ?: 0}")
        
        // Log tools
        toolDefinitions?.forEach { td ->
            Log.d(TAG, "  Tool: ${td.function.name} - ${td.function.description?.take(50)}...")
        }
        
        // Log messages
        messages.forEachIndexed { index, msg ->
            Log.d(TAG, "  Msg[$index]: ${msg.role} - ${(msg.content?.take(50) ?: "tool_call")}...")
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
        
        // Track tool calls by index
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
                    
                    // Handle SSE format
                    val dataLine = when {
                        line.startsWith("data: ") -> line.removePrefix("data: ").trim()
                        line.startsWith(":") -> continue  // Keep-alive
                        line.isBlank() -> continue
                        else -> continue
                    }
                    
                    if (dataLine == "[DONE]") {
                        Log.d(TAG, "Stream [DONE] received")
                        
                        // Emit any remaining complete tool calls
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
                            // Handle content
                            choice.delta.content?.let { content ->
                                if (content.isNotEmpty()) {
                                    emit(StreamEvent.Content(content))
                                }
                            }
                            
                            // Handle reasoning (for DeepSeek R1 and other thinking models)
                            choice.delta.reasoning?.let { reasoning ->
                                if (reasoning.isNotEmpty()) {
                                    // Treat reasoning as content for now
                                    emit(StreamEvent.Content(reasoning))
                                }
                            }
                            
                            // Handle tool calls - accumulate
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
                                    
                                    // Check if complete and emit
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
                arguments = arguments.toString()
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
