package com.agentstudio.data.agent

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
        maxIterations: Int = 5
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
                temperature = 0.7
            )
            
            val toolCalls = mutableListOf<ToolCallBuilder>()
            var currentContent = StringBuilder()
            var currentToolCallIndex = -1
            var hasToolCalls = false
            
            api.chatStream(request).collect { response ->
                val delta = response.choices.firstOrNull()?.delta
                
                // Handle content
                delta?.content?.let { content ->
                    currentContent.append(content)
                    emit(AgentEvent.Token(content))
                }
                
                // Handle tool calls - CRITICAL: ensure arguments are always valid
                delta?.toolCalls?.forEach { toolCallDelta ->
                    hasToolCalls = true
                    
                    val index = toolCallDelta.index
                    
                    // Ensure we have a builder for this index
                    while (toolCalls.size <= index) {
                        toolCalls.add(ToolCallBuilder())
                    }
                    
                    val builder = toolCalls[index]
                    
                    // Set ID if provided
                    toolCallDelta.id?.let { builder.id = it }
                    
                    // Set function name if provided
                    toolCallDelta.function?.name?.let { builder.name = it }
                    
                    // Append arguments - CRITICAL for avoiding the error
                    toolCallDelta.function?.arguments?.let { args ->
                        builder.argumentsBuilder.append(args)
                        emit(AgentEvent.ToolCallDelta(builder.id, args))
                    }
                    
                    // Update current tool call index
                    if (currentToolCallIndex != index) {
                        currentToolCallIndex = index
                        if (builder.id.isNotEmpty() && builder.name.isNotEmpty()) {
                            emit(AgentEvent.ToolCallStart(builder.id, builder.name))
                        }
                    }
                }
            }
            
            // Process tool calls if any
            if (hasToolCalls && toolCalls.isNotEmpty()) {
                // Build complete tool calls with VALID arguments
                val completeToolCalls = toolCalls.mapIndexed { index, builder ->
                    val id = builder.id.ifEmpty { "call_$index" }
                    val name = builder.name.ifEmpty { "unknown" }
                    
                    // CRITICAL: Ensure arguments is valid JSON
                    val rawArgs = builder.argumentsBuilder.toString().trim()
                    val safeArgs = if (rawArgs.isEmpty()) {
                        "{}"
                    } else {
                        try {
                            // Validate JSON
                            json.parseToJsonElement(rawArgs)
                            rawArgs
                        } catch (e: Exception) {
                            // Try to fix common issues
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
                
                // Add assistant message with tool calls
                currentMessages.add(ChatMessage(
                    role = "assistant",
                    content = currentContent.toString().ifEmpty { null },
                    toolCalls = completeToolCalls
                ))
                
                // Execute each tool and add results
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
                
                when (name) {
                    "web_search" -> {
                        val query = argsMap["query"]?.let { 
                            (it as? JsonPrimitive)?.content 
                        } ?: "unknown"
                        "Search results for '$query':\n- Result 1: Information about $query\n- Result 2: More details about $query\n- Result 3: Latest news on $query"
                    }
                    
                    "execute_code" -> {
                        val code = argsMap["code"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: ""
                        // Simulate code execution
                        "Code executed successfully.\nOutput: [Simulated output for code execution]"
                    }
                    
                    "generate_image" -> {
                        val prompt = argsMap["prompt"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: "an image"
                        "Image generated: [AI generated image for: $prompt]"
                    }
                    
                    "get_weather" -> {
                        val location = argsMap["location"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: "Unknown location"
                        "Weather in $location:\nTemperature: 28°C\nCondition: Partly Cloudy\nHumidity: 65%"
                    }
                    
                    "get_datetime" -> {
                        val timezone = argsMap["timezone"]?.let {
                            (it as? JsonPrimitive)?.content
                        } ?: "UTC"
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            sdf.timeZone = TimeZone.getTimeZone(timezone.replace("\"", ""))
                            "Current date/time ($timezone): ${sdf.format(Date())}"
                        } catch (e: Exception) {
                            "Current date/time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
                        }
                    }
                    
                    else -> "Unknown tool: $name"
                }
            } catch (e: Exception) {
                "Error executing tool $name: ${e.message}"
            }
        }
    }
    
    private data class ToolCallBuilder(
        var id: String = "",
        var name: String = "",
        val argumentsBuilder: StringBuilder = StringBuilder()
    )
}
