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
        
        fun buildToolCall(
            id: String,
            functionName: String,
            arguments: Map<String, JsonElement> = emptyMap()
        ): ToolCall {
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
        
        fun buildToolCallFromJson(
            id: String,
            functionName: String,
            argumentsJson: String
        ): ToolCall {
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
    
    // Build comprehensive tools for smart assistant
    fun buildAgentTools(): List<Tool> {
        return listOf(
            // Web Search Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "web_search",
                    description = "Tìm kiếm thông tin trên web. Sử dụng khi cần thông tin cập nhật, tin tức, hoặc kiến thức mới.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "query" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Nội dung cần tìm kiếm")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("query")))
                    ))
                )
            ),
            
            // Open App Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "open_app",
                    description = "Mở ứng dụng trên điện thoại. Có thể mở: settings, camera, gallery, browser, maps, play store, hoặc tên ứng dụng khác.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "app_name" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Tên ứng dụng cần mở (settings, camera, gallery, browser, maps, store, ...)")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("app_name")))
                    ))
                )
            ),
            
            // Get Weather Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "get_weather",
                    description = "Lấy thông tin thời tiết tại một địa điểm.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "location" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Tên thành phố hoặc địa điểm")
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
                    description = "Lấy ngày giờ hiện tại.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "timezone" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Múi giờ (mặc định: Asia/Ho_Chi_Minh)"),
                                "default" to JsonPrimitive("Asia/Ho_Chi_Minh")
                            ))
                        ))
                    ))
                )
            ),
            
            // Set Reminder Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "set_reminder",
                    description = "Đặt lời nhắc nhở cho người dùng.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "task" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Nội dung cần nhắc")
                            )),
                            "time" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Thời gian nhắc (ví dụ: 2 giờ chiều, sáng mai)")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("task")))
                    ))
                )
            ),
            
            // Play Music Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "play_music",
                    description = "Phát nhạc hoặc tìm kiếm bài hát.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "query" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Tên bài hát, nghệ sĩ, hoặc thể loại nhạc")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("query")))
                    ))
                )
            ),
            
            // Generate Image Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "generate_image",
                    description = "Tạo hình ảnh từ mô tả văn bản.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "prompt" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Mô tả hình ảnh cần tạo")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("prompt")))
                    ))
                )
            ),
            
            // Execute Code Tool
            Tool(
                type = "function",
                function = ToolFunction(
                    name = "execute_code",
                    description = "Thực thi code Python cho tính toán hoặc xử lý dữ liệu.",
                    parameters = JsonObject(mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(mapOf(
                            "code" to JsonObject(mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Code Python cần chạy")
                            ))
                        )),
                        "required" to JsonArray(listOf(JsonPrimitive("code")))
                    ))
                )
            )
        )
    }
}
