package com.agentstudio.data.api

import com.agentstudio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterApi(private val apiKey: String) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }
    
    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"
        
        // Build tool call with ALWAYS valid arguments
        fun buildToolCall(
            id: String,
            functionName: String,
            arguments: Map<String, JsonElement> = emptyMap()
        ): ToolCall {
            // CRITICAL: arguments must ALWAYS be a valid JSON string
            val argumentsJson = JsonObject(arguments).toString()
            return ToolCall(
                id = id,
                type = "function",
                function = ToolCallFunction(
                    name = functionName,
                    arguments = argumentsJson
                )
            )
        }
        
        // Build tool call from JSON string directly
        fun buildToolCallFromJson(
            id: String,
            functionName: String,
            argumentsJson: String
        ): ToolCall {
            // Ensure arguments is valid JSON, default to empty object if invalid
            val safeArgs = try {
                Json.parseToJsonElement(argumentsJson)
                argumentsJson
            } catch (e: Exception) {
                "{}"
            }
            return ToolCall(
                id = id,
                type = "function",
                function = ToolCallFunction(
                    name = functionName,
                    arguments = safeArgs
                )
            )
        }
    }
    
    fun chatStream(request: ChatRequest): Flow<ChatResponse> = callbackFlow {
        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            json.encodeToString(request)
        )
        
        val httpRequest = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/vyvegroup/AgentStudio")
            .addHeader("X-Title", "AI Agent Studio")
            .post(requestBody)
            .build()
        
        val eventSourceFactory = EventSources.createFactory(client)
        val eventSourceListener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                
                try {
                    val response = json.decodeFromString<ChatResponse>(data)
                    
                    // Check for errors
                    if (response.error != null) {
                        close(Exception("API Error: ${response.error.message}"))
                        return
                    }
                    
                    trySend(response)
                } catch (e: Exception) {
                    // Parse error, skip this chunk
                }
            }
            
            override fun onClosed(eventSource: EventSource) {
                close()
            }
            
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = when {
                    response != null -> "HTTP ${response.code}: ${response.body?.string() ?: t?.message}"
                    t != null -> t.message ?: "Unknown error"
                    else -> "Unknown error"
                }
                close(Exception(errorMsg))
            }
        }
        
        val eventSource = eventSourceFactory.newEventSource(httpRequest, eventSourceListener)
        
        awaitClose {
            eventSource.cancel()
        }
    }.flowOn(Dispatchers.IO)
    
    suspend fun chat(request: ChatRequest): Result<ChatResponse> {
        return try {
            val requestBody = RequestBody.create(
                "application/json".toMediaType(),
                json.encodeToString(request.copy(stream = false))
            )
            
            val httpRequest = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/vyvegroup/AgentStudio")
                .addHeader("X-Title", "AI Agent Studio")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "{}"
                val chatResponse = json.decodeFromString<ChatResponse>(body)
                Result.success(chatResponse)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("HTTP ${response.code}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Build tools for agent
    fun buildAgentTools(): List<Tool> {
        return listOf(
            // Web Search Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "web_search",
                    description = "Search the web for current information. Use this when you need up-to-date information.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "query" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("The search query")
                            )),
                            "num_results" to JsonObject(mapOf(
                                "type" to JsonPrimitive("integer"),
                                "description" to JsonPrimitive("Number of results to return"),
                                "default" to JsonPrimitive(5)
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("query")))
                    ))
                )
            ),
            // Code Execution Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "execute_code",
                    description = "Execute Python code for calculations, data analysis, or other tasks.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "code" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Python code to execute")
                            )),
                            "language" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Programming language"),
                                "default" to JsonPrimitive("python")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("code")))
                    ))
                )
            ),
            // Image Generation Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "generate_image",
                    description = "Generate an image from a text description.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "prompt" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Image description")
                            )),
                            "size" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Image size (1024x1024, 512x512)"),
                                "default" to JsonPrimitive("1024x1024")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("prompt")))
                    ))
                )
            ),
            // Get Weather Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "get_weather",
                    description = "Get current weather for a location.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "location" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("City name or coordinates")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("location")))
                    ))
                )
            ),
            // Get Date/Time Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "get_datetime",
                    description = "Get current date and time.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "timezone" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Timezone (e.g., Asia/Ho_Chi_Minh)"),
                                "default" to JsonPrimitive("UTC")
                            ))
                        ))
                    ))
                )
            )
        )
    }
}
