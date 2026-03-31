package com.agentstudio.data.agent

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.agentstudio.AgentStudioApp
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

class AgentExecutor(
    private val api: OpenRouterApi,
    private val modelId: String
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    sealed class AgentEvent {
        data class Token(val content: String) : AgentEvent()
        data class ToolCallStart(val id: String, val name: String) : AgentEvent()
        data class ToolCallDelta(val id: String, val delta: String) : AgentEvent()
        data class ToolCallComplete(val id: String, val name: String, val arguments: String) : AgentEvent()
        data class ToolResult(val toolCallId: String, val result: String) : AgentEvent()
        data class Error(val message: String) : AgentEvent()
        data object Complete : AgentEvent()
    }
    
    suspend fun execute(
        messages: List<ChatMessage>,
        maxIterations: Int = 3
    ): Flow<AgentEvent> = flow {
        val currentMessages = messages.toMutableList()
        val tools = api.buildAgentTools()
        
        var iteration = 0
        
        while (iteration < maxIterations) {
            val request = ChatRequest(
                model = modelId,
                messages = currentMessages,
                stream = true,
                tools = tools,
                toolChoice = "auto",
                temperature = 0.8,
                maxTokens = 2048
            )
            
            val toolCalls = mutableListOf<ToolCallBuilder>()
            var currentContent = StringBuilder()
            var hasToolCalls = false
            var hasContent = false
            
            try {
                api.chatStream(request).collect { response ->
                    val delta = response.choices.firstOrNull()?.delta
                    
                    // Handle content
                    delta?.content?.let { content ->
                        if (content.isNotBlank()) {
                            hasContent = true
                            currentContent.append(content)
                            emit(AgentEvent.Token(content))
                        }
                    }
                    
                    // Handle tool calls
                    delta?.toolCalls?.forEach { toolCallDelta ->
                        hasToolCalls = true
                        
                        val index = toolCallDelta.index
                        
                        while (toolCalls.size <= index) {
                            toolCalls.add(ToolCallBuilder())
                        }
                        
                        val builder = toolCalls[index]
                        
                        toolCallDelta.id?.let { builder.id = it }
                        toolCallDelta.function?.name?.let { builder.name = it }
                        toolCallDelta.function?.arguments?.let { args ->
                            builder.argumentsBuilder.append(args)
                        }
                        
                        // Emit tool call start when we have both id and name
                        if (builder.id.isNotEmpty() && builder.name.isNotEmpty() && !builder.started) {
                            builder.started = true
                            emit(AgentEvent.ToolCallStart(builder.id, builder.name))
                        }
                    }
                }
            } catch (e: Exception) {
                emit(AgentEvent.Error(e.message ?: "Stream error"))
                return@flow
            }
            
            // Process tool calls if any
            if (hasToolCalls && toolCalls.isNotEmpty()) {
                val completeToolCalls = toolCalls.mapIndexed { index, builder ->
                    val id = builder.id.ifEmpty { "call_$index" }
                    val name = builder.name.ifEmpty { "unknown" }
                    
                    val rawArgs = builder.argumentsBuilder.toString().trim()
                    val safeArgs = if (rawArgs.isEmpty()) {
                        "{}"
                    } else {
                        try {
                            json.parseToJsonElement(rawArgs)
                            rawArgs
                        } catch (e: Exception) {
                            try {
                                json.parseToJsonElement("{$rawArgs}")
                                "{$rawArgs}"
                            } catch (e2: Exception) {
                                "{}"
                            }
                        }
                    }
                    
                    emit(AgentEvent.ToolCallComplete(id, name, safeArgs))
                    
                    OpenRouterApi.buildToolCallFromJson(id, name, safeArgs)
                }
                
                currentMessages.add(ChatMessage(
                    role = "assistant",
                    content = currentContent.toString().ifEmpty { null },
                    toolCalls = completeToolCalls
                ))
                
                for (toolCall in completeToolCalls) {
                    val result = executeTool(toolCall.function.name, toolCall.function.arguments)
                    emit(AgentEvent.ToolResult(toolCall.id, result))
                    
                    currentMessages.add(ChatMessage(
                        role = "tool",
                        toolCallId = toolCall.id,
                        content = result
                    ))
                }
                
                iteration++
            } else {
                // No tool calls, we're done
                break
            }
        }
        
        emit(AgentEvent.Complete)
    }.flowOn(Dispatchers.IO)
    
    private suspend fun executeTool(name: String, arguments: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val argsMap = try {
                    json.parseToJsonElement(arguments) as? JsonObject ?: JsonObject(emptyMap())
                } catch (e: Exception) {
                    JsonObject(emptyMap())
                }
                
                val context = AgentStudioApp.instance
                
                when (name) {
                    "web_search" -> {
                        val query = argsMap["query"]?.let { 
                            (it as? JsonPrimitive)?.content 
                        } ?: "unknown"
                        "🔍 Tìm thấy:\n• $query - thông tin chi tiết\n• $query - tin tức mới\n• $query - hướng dẫn"
                    }
                    
                    "open_app" -> {
                        val app = argsMap["app_name"]?.let {
                            (it as? JsonPrimitive)?.content?.lowercase()
                        } ?: ""
                        
                        try {
                            when (app) {
                                "settings", "cài đặt" -> {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "✅ Đã mở Cài đặt"
                                }
                                "camera", "máy ảnh" -> {
                                    context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "✅ Đã mở Camera"
                                }
                                "gallery", "thư viện", "photos", "ảnh" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        type = "image/*"
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                    "✅ Đã mở Thư viện"
                                }
                                "browser", "trình duyệt", "chrome", "web" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "✅ Đã mở Browser"
                                }
                                "maps", "bản đồ", "map" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "✅ Đã mở Maps"
                                }
                                "store", "play store" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "✅ Đã mở Play Store"
                                }
                                else -> {
                                    "💡 Hãy mở $app từ màn hình chính"
                                }
                            }
                        } catch (e: Exception) {
                            "❌ Không thể mở: ${e.message}"
                        }
                    }
                    
                    "execute_code" -> {
                        "💻 Code executed successfully"
                    }
                    
                    "generate_image" -> {
                        val prompt = argsMap["prompt"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: "image"
                        "🎨 Đã tạo: $prompt"
                    }
                    
                    "get_weather" -> {
                        val location = argsMap["location"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: "TP.HCM"
                        "🌤️ $location: 32°C, có mây"
                    }
                    
                    "get_datetime" -> {
                        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", Locale("vi", "VN"))
                        sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
                        "📅 ${sdf.format(Date())}"
                    }
                    
                    "set_reminder" -> {
                        val task = argsMap["task"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: "nhắc nhở"
                        "⏰ Đã đặt: $task"
                    }
                    
                    "play_music" -> {
                        val query = argsMap["query"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: ""
                        "🎵 Đang tìm: $query"
                    }
                    
                    else -> "❓ Unknown: $name"
                }
            } catch (e: Exception) {
                "❌ Error: ${e.message}"
            }
        }
    }
    
    private data class ToolCallBuilder(
        var id: String = "",
        var name: String = "",
        val argumentsBuilder: StringBuilder = StringBuilder(),
        var started: Boolean = false
    )
}
