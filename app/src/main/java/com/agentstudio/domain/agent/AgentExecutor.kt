package com.agentstudio.domain.agent

import android.util.Log
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.*
import com.agentstudio.domain.model.AgentTools
import com.agentstudio.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AgentExecutor(
    private val api: OpenRouterApi,
    private val toolHandler: ToolHandler,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()
    
    private val _debugLog = MutableStateFlow(StringBuilder())
    val debugLog: StateFlow<StringBuilder> = _debugLog.asStateFlow()
    
    private var executionJob: Job? = null
    private var systemPrompt: String = ""
    
    // Conversation history for context
    private val conversationHistory = mutableListOf<MessageContent>()
    
    fun executeWithFlow(
        userInput: String,
        onEvent: (ExecutionEvent) -> Unit
    ) {
        if (_state.value != AgentState.Idle) {
            Log.w(TAG, "Agent is already executing")
            return
        }
        
        executionJob = scope.launch {
            try {
                _state.value = AgentState.Processing
                _debugLog.value = StringBuilder()
                
                appendDebug("╔══════════════════════════════════════════╗")
                appendDebug("║          NEW EXECUTION STARTED           ║")
                appendDebug("╚══════════════════════════════════════════╝")
                appendDebug("📝 User Input: ${userInput.take(100)}...")
                
                val messages = mutableListOf<MessageContent>()
                
                if (systemPrompt.isNotEmpty()) {
                    messages.add(MessageContent(role = "system", content = systemPrompt))
                    appendDebug("📋 System prompt added (${systemPrompt.length} chars)")
                }
                
                val recentHistory = conversationHistory.takeLast(20)
                messages.addAll(recentHistory)
                appendDebug("📚 History: ${recentHistory.size} messages")
                
                val userMessage = MessageContent(role = "user", content = userInput)
                messages.add(userMessage)
                conversationHistory.add(userMessage)
                
                onEvent(ExecutionEvent.UserMessage(userInput))
                
                var iteration = 0
                val maxIterations = 8
                var failedToolCalls = 0
                
                do {
                    iteration++
                    appendDebug("\n┌──────────────────────────────────────────┐")
                    appendDebug("│  ITERATION $iteration/$maxIterations")
                    appendDebug("└──────────────────────────────────────────┘")
                    
                    val toolCalls = mutableListOf<ToolCallRequest>()
                    var responseContent = StringBuilder()
                    
                    // Stream the response
                    api.streamChatFlow(messages, AgentTools.ALL_TOOLS).collect { event ->
                        when (event) {
                            is OpenRouterApi.StreamEvent.Content -> {
                                responseContent.append(event.text)
                                onEvent(ExecutionEvent.StreamChunk(event.text))
                            }
                            is OpenRouterApi.StreamEvent.ToolCall -> {
                                appendDebug("🔧 TOOL CALL (API): ${event.toolCall.function.name}")
                                appendDebug("   Args: ${event.toolCall.function.arguments}")
                                toolCalls.add(event.toolCall)
                            }
                            is OpenRouterApi.StreamEvent.Complete -> {
                                appendDebug("✅ Stream complete")
                            }
                            is OpenRouterApi.StreamEvent.Error -> {
                                appendDebug("❌ ERROR: ${event.message}")
                                _state.value = AgentState.Error(event.message)
                                onEvent(ExecutionEvent.Error(event.message))
                            }
                        }
                    }
                    
                    val assistantContent = responseContent.toString()
                    
                    // Process tool calls - fix empty arguments
                    val validToolCalls = mutableListOf<ToolCallRequest>()
                    
                    for (toolCall in toolCalls) {
                        val toolName = toolCall.function.name
                        val toolArgs = toolCall.function.arguments
                        
                        // Check for empty or invalid arguments
                        if (toolArgs.isBlank() || toolArgs == "{}" || toolArgs == "{\"\"}") {
                            appendDebug("⚠️ Empty args for $toolName - trying to extract from text")
                            
                            // Try to extract from response content
                            val extractedArgs = extractArgumentsFromText(assistantContent, toolName, userMessage.content ?: "")
                            
                            if (extractedArgs.isNotEmpty()) {
                                val fixedToolCall = ToolCallRequest(
                                    id = toolCall.id,
                                    type = toolCall.type,
                                    function = FunctionCallRequest(
                                        name = toolName,
                                        arguments = extractedArgs
                                    )
                                )
                                validToolCalls.add(fixedToolCall)
                                appendDebug("✅ Extracted args: $extractedArgs")
                            } else {
                                // Send error back to model
                                val errorMessage = MessageContent(
                                    role = "tool",
                                    content = "ERROR: You called '$toolName' but did not provide the required parameters. " +
                                              "For $toolName, you MUST provide valid JSON arguments. " +
                                              "Example: {\"path\": \"/storage/emulated/0/Documents/AgentStudioProject/file.txt\"}. " +
                                              "Do NOT call this tool again without valid arguments.",
                                    toolCallId = toolCall.id,
                                    name = toolName
                                )
                                messages.add(errorMessage)
                                conversationHistory.add(errorMessage)
                                failedToolCalls++
                            }
                        } else {
                            validToolCalls.add(toolCall)
                        }
                    }
                    
                    // Add assistant message to history
                    if (assistantContent.isNotEmpty() || toolCalls.isNotEmpty()) {
                        val assistantMessage = MessageContent(
                            role = "assistant",
                            content = assistantContent.ifEmpty { null },
                            toolCalls = if (toolCalls.isNotEmpty()) toolCalls else null
                        )
                        messages.add(assistantMessage)
                        conversationHistory.add(assistantMessage)
                        
                        if (assistantContent.isNotEmpty()) {
                            onEvent(ExecutionEvent.ResponseComplete(assistantContent))
                        }
                    }
                    
                    // Execute valid tool calls
                    if (validToolCalls.isNotEmpty()) {
                        _state.value = AgentState.ExecutingTools
                        appendDebug("\n⚡ EXECUTING ${validToolCalls.size} TOOL(S)")
                        
                        for (toolCall in validToolCalls) {
                            val toolName = toolCall.function.name
                            val toolArgs = toolCall.function.arguments
                            
                            appendDebug("\n┌─────────────────────────────────────────┐")
                            appendDebug("│ 🛠️  $toolName")
                            appendDebug("│ Args: ${toolArgs.take(100)}")
                            appendDebug("└─────────────────────────────────────────┘")
                            
                            onEvent(ExecutionEvent.ToolExecutionStarted(toolCall.id, toolName))
                            
                            val result = toolHandler.executeTool(toolName, toolArgs)
                            
                            appendDebug("📊 Result: ${if (result.success) "✅ SUCCESS" else "❌ FAILED"}")
                            if (result.success) {
                                appendDebug("📄 Output: ${result.output.take(200)}${if (result.output.length > 200) "..." else ""}")
                            } else {
                                appendDebug("⚠️ Error: ${result.error}")
                            }
                            
                            onEvent(ExecutionEvent.ToolExecutionComplete(toolCall.id, toolName, result))
                            
                            val toolMessage = MessageContent(
                                role = "tool",
                                content = if (result.success) result.output else "Error: ${result.error}",
                                toolCallId = toolCall.id,
                                name = toolName
                            )
                            messages.add(toolMessage)
                            conversationHistory.add(toolMessage)
                        }
                    }
                    
                    // Stop if too many failed tool calls or no tools to execute
                    if (failedToolCalls >= 3) {
                        appendDebug("\n⚠️ Too many failed tool calls - stopping")
                        break
                    }
                    
                    delay(100)
                    
                } while (validToolCalls.isNotEmpty() && iteration < maxIterations && failedToolCalls < 3)
                
                appendDebug("\n╔══════════════════════════════════════════╗")
                appendDebug("║        EXECUTION COMPLETE                ║")
                appendDebug("║  Iterations: $iteration")
                appendDebug("║  Failed calls: $failedToolCalls")
                appendDebug("╚══════════════════════════════════════════╝")
                
                _state.value = AgentState.Idle
                onEvent(ExecutionEvent.Complete)
                
            } catch (e: Exception) {
                Log.e(TAG, "Execution error", e)
                appendDebug("\n❌ EXCEPTION: ${e.message}")
                _state.value = AgentState.Error(e.message ?: "Unknown error")
                onEvent(ExecutionEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }
    
    /**
     * Extract tool arguments from text or user input context
     */
    private fun extractArgumentsFromText(text: String, toolName: String, userInput: String): String {
        // Tool-specific extraction based on user input
        when (toolName) {
            "read_file" -> {
                // Look for file paths in user input
                val pathPattern = Regex("""["']?(/[\w/\-\.]+)["']?""")
                val matches = pathPattern.findAll(userInput)
                for (match in matches) {
                    val path = match.groupValues[1]
                    if (path.isNotEmpty()) {
                        return "{\"path\":\"$path\"}"
                    }
                }
                // Look for filename in input
                val filePattern = Regex("""(\w+\.\w+)""")
                val fileMatch = filePattern.find(userInput)
                if (fileMatch != null) {
                    return "{\"path\":\"/storage/emulated/0/Documents/AgentStudioProject/${fileMatch.value}\"}"
                }
            }
            
            "list_directory" -> {
                val pathPattern = Regex("""["']?(/[\w/\-\.]+)["']?""")
                val match = pathPattern.find(userInput)
                if (match != null) {
                    return "{\"path\":\"${match.groupValues[1]}\"}"
                }
                return "{\"path\":\"/storage/emulated/0/Documents/AgentStudioProject\"}"
            }
            
            "image_search" -> {
                // Extract tags from user input
                val tagsWords = mutableListOf<String>()
                
                // Common words to exclude
                val excludeWords = setOf("search", "find", "image", "images", "picture", "pictures", "photo", "with", "tags", "tagged", "show", "me", "the", "a", "an", "for", "of", "and", "or")
                
                userInput.lowercase().split(Regex("\\s+"))
                    .filter { it.length > 2 && it !in excludeWords }
                    .forEach { tagsWords.add(it) }
                
                if (tagsWords.isNotEmpty()) {
                    return "{\"tags\":\"${tagsWords.joinToString(" ")}\",\"limit\":10,\"rating\":\"safe\"}"
                }
            }
            
            "web_search", "wiki_search" -> {
                // Extract search query from user input
                val queryWords = userInput.split(Regex("\\s+"))
                    .filter { it.length > 2 }
                    .filter { it.lowercase() !in setOf("search", "find", "web", "wiki", "wikipedia", "for", "about", "the", "me") }
                
                if (queryWords.isNotEmpty()) {
                    return "{\"query\":\"${queryWords.joinToString(" ")}\",\"max_results\":10}"
                }
            }
            
            "create_file" -> {
                val filePattern = Regex("""(\w+\.\w+)""")
                val fileMatch = filePattern.find(userInput)
                if (fileMatch != null) {
                    return "{\"path\":\"/storage/emulated/0/Documents/AgentStudioProject/${fileMatch.value}\",\"content\":\"\"}"
                }
            }
        }
        
        return ""
    }
    
    private fun appendDebug(message: String) {
        _debugLog.value = StringBuilder(_debugLog.value).append("\n$message")
        Log.d(TAG, message)
    }
    
    fun getDebugLog(): String = _debugLog.value.toString()
    
    fun cancel() {
        executionJob?.cancel()
        executionJob = null
        _state.value = AgentState.Idle
    }
    
    fun reset() {
        cancel()
        conversationHistory.clear()
        _debugLog.value = StringBuilder()
    }
    
    fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }
    
    sealed class AgentState {
        object Idle : AgentState()
        object Processing : AgentState()
        object ExecutingTools : AgentState()
        data class Error(val message: String) : AgentState()
    }
    
    sealed class ExecutionEvent {
        data class UserMessage(val content: String) : ExecutionEvent()
        data class StreamChunk(val text: String) : ExecutionEvent()
        data class ResponseComplete(val content: String) : ExecutionEvent()
        data class ToolCallReceived(val toolCall: PendingToolCall) : ExecutionEvent()
        data class ToolExecutionStarted(val id: String, val name: String) : ExecutionEvent()
        data class ToolExecutionComplete(val id: String, val name: String, val result: ToolResult) : ExecutionEvent()
        object Complete : ExecutionEvent()
        data class Error(val message: String) : ExecutionEvent()
    }
    
    data class PendingToolCall(
        val id: String,
        val name: String,
        val arguments: String
    )
    
    companion object {
        private const val TAG = "AgentExecutor"
    }
}
