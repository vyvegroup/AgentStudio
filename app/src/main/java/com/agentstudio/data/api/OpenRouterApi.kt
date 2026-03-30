package com.agentstudio.data.api

import android.util.Log
import com.agentstudio.data.model.*
import com.agentstudio.domain.model.AgentTool
import com.agentstudio.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterApi(private val apiKey: String) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val eventSourceFactory = EventSources.createFactory(client)
    
    fun streamChat(
        messages: List<MessageContent>,
        tools: List<AgentTool>? = null,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onComplete: (OpenRouterResponse?) -> Unit,
        onError: (String) -> Unit
    ): EventSource? {
        val toolDefinitions = tools?.map { it.toToolDefinition() }
        
        val request = OpenRouterRequest(
            model = Constants.MODEL_ID,
            messages = messages,
            stream = true,
            tools = toolDefinitions,
            toolChoice = if (tools != null) "auto" else null
        )
        
        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            json.encodeToString(request)
        )
        
        val httpRequest = Request.Builder()
            .url("${Constants.OPENROUTER_BASE_URL}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", Constants.APP_REFERER)
            .addHeader("X-Title", Constants.APP_NAME)
            .post(requestBody)
            .build()
        
        val listener = object : EventSourceListener() {
            private var accumulatedContent = StringBuilder()
            private var toolCalls = mutableMapOf<Int, ToolCallBuilder>()
            
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    val response = buildFinalResponse(accumulatedContent.toString(), toolCalls)
                    onComplete(response)
                    return
                }
                
                try {
                    val chunk = json.decodeFromString<StreamChunk>(data)
                    chunk.choices.forEach { choice ->
                        choice.delta.content?.let { content ->
                            accumulatedContent.append(content)
                            onChunk(content)
                        }
                        
                        choice.delta.toolCalls?.forEach { deltaToolCall ->
                            val index = deltaToolCall.index
                            val builder = toolCalls.getOrPut(index) { ToolCallBuilder() }
                            
                            deltaToolCall.id?.let { builder.id = it }
                            deltaToolCall.function?.name?.let { builder.name = it }
                            deltaToolCall.function?.arguments?.let { builder.arguments.append(it) }
                            
                            if (builder.isComplete()) {
                                val toolCall = builder.build()
                                onToolCall(toolCall)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing stream chunk", e)
                }
            }
            
            override fun onClosed(eventSource: EventSource) {
                val response = buildFinalResponse(accumulatedContent.toString(), toolCalls)
                onComplete(response)
            }
            
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = t?.message ?: response?.message ?: "Unknown error"
                Log.e(TAG, "Stream failure: $errorMsg", t)
                onError(errorMsg)
            }
        }
        
        return eventSourceFactory.newEventSource(httpRequest, listener)
    }
    
    suspend fun completeChat(
        messages: List<MessageContent>,
        tools: List<AgentTool>? = null
    ): Result<OpenRouterResponse> = withContext(Dispatchers.IO) {
        try {
            val toolDefinitions = tools?.map { it.toToolDefinition() }
            
            val request = OpenRouterRequest(
                model = Constants.MODEL_ID,
                messages = messages,
                stream = false,
                tools = toolDefinitions,
                toolChoice = if (tools != null) "auto" else null
            )
            
            val requestBody = RequestBody.create(
                "application/json".toMediaType(),
                json.encodeToString(request)
            )
            
            val httpRequest = Request.Builder()
                .url("${Constants.OPENROUTER_BASE_URL}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", Constants.APP_REFERER)
                .addHeader("X-Title", Constants.APP_NAME)
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                body?.let {
                    Result.success(json.decodeFromString(it))
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun streamChatFlow(
        messages: List<MessageContent>,
        tools: List<AgentTool>? = null
    ): Flow<StreamEvent> = flow {
        val toolDefinitions = tools?.map { it.toToolDefinition() }
        
        val request = OpenRouterRequest(
            model = Constants.MODEL_ID,
            messages = messages,
            stream = true,
            tools = toolDefinitions,
            toolChoice = if (tools != null) "auto" else null
        )
        
        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            json.encodeToString(request)
        )
        
        val httpRequest = Request.Builder()
            .url("${Constants.OPENROUTER_BASE_URL}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", Constants.APP_REFERER)
            .addHeader("X-Title", Constants.APP_NAME)
            .post(requestBody)
            .build()
            
        var toolCalls = mutableMapOf<Int, ToolCallBuilder>()
        
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                emit(StreamEvent.Error("HTTP ${response.code}: ${response.message}"))
                return@use
            }
            
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (!line.startsWith("data: ")) continue
                    
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        emit(StreamEvent.Complete)
                        return@use
                    }
                    
                    try {
                        val chunk = json.decodeFromString<StreamChunk>(data)
                        chunk.choices.forEach { choice ->
                            choice.delta.content?.let { content ->
                                emit(StreamEvent.Content(content))
                            }
                            
                            choice.delta.toolCalls?.forEach { deltaToolCall ->
                                val index = deltaToolCall.index
                                val builder = toolCalls.getOrPut(index) { ToolCallBuilder() }
                                
                                deltaToolCall.id?.let { builder.id = it }
                                deltaToolCall.function?.name?.let { builder.name = it }
                                deltaToolCall.function?.arguments?.let { builder.arguments.append(it) }
                                
                                if (builder.isComplete()) {
                                    emit(StreamEvent.ToolCall(builder.build()))
                                    toolCalls.remove(index)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing chunk: $data", e)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private fun buildFinalResponse(
        content: String,
        toolCalls: Map<Int, ToolCallBuilder>
    ): OpenRouterResponse {
        return OpenRouterResponse(
            id = "chatcmpl-${System.currentTimeMillis()}",
            choices = listOf(
                Choice(
                    index = 0,
                    message = MessageContent(
                        role = "assistant",
                        content = content.ifEmpty { null },
                        toolCalls = toolCalls.values.filter { it.isComplete() }.map { it.build() }
                            .ifEmpty { null }
                    ),
                    finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop"
                )
            )
        )
    }
    
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
