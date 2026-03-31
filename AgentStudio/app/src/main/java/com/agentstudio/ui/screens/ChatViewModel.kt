package com.agentstudio.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentstudio.BuildConfig
import com.agentstudio.data.agent.AgentExecutor
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.ChatMessage
import com.agentstudio.data.model.FREE_MODELS
import com.agentstudio.data.repository.PreferencesRepository
import com.agentstudio.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    companion object {
        // API key from BuildConfig (injected from local.properties at build time)
        private val DEFAULT_API_KEY: String = if (BuildConfig.API_KEY.isNotEmpty()) BuildConfig.API_KEY else ""
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
    }
    
    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()
    
    private val _selectedModel = MutableStateFlow(FREE_MODELS.first().id)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val conversationHistory = mutableListOf<ChatMessage>()
    private var apiKey: String = DEFAULT_API_KEY
    
    init {
        apiKey = DEFAULT_API_KEY
        
        viewModelScope.launch {
            preferencesRepository.selectedModel.collect { model ->
                _selectedModel.value = model
            }
        }
    }
    
    fun setApiKey(key: String) {
        viewModelScope.launch {
            apiKey = key.ifEmpty { DEFAULT_API_KEY }
            preferencesRepository.setApiKey(key)
        }
    }
    
    fun setModel(modelId: String) {
        viewModelScope.launch {
            _selectedModel.value = modelId
            preferencesRepository.setSelectedModel(modelId)
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
        conversationHistory.clear()
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (_isLoading.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Add user message with single text block
            val userMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = userMessageId,
                    blocks = listOf(ContentBlock.TextBlock(content)),
                    isUser = true
                )
            }
            
            // Add system prompt
            if (conversationHistory.isEmpty()) {
                conversationHistory.add(ChatMessage(
                    role = "system",
                    content = """
                        Bạn là VenAI - một trợ lý AI thông minh và hữu ích.
                        
                        Tính cách:
                        - Nói chuyện thân thiện, tự nhiên và hữu ích
                        - Trả lời rõ ràng, có cấu trúc
                        - Sử dụng markdown để format câu trả lời
                        
                        Khả năng của VenAI:
                        - Tìm kiếm thông tin web (web_search)
                        - Mở ứng dụng điện thoại (open_app)
                        - Cho biết thời tiết (get_weather)
                        - Cho biết ngày giờ (get_datetime)
                        - Đặt lời nhắc (set_reminder)
                        - Phát nhạc (play_music)
                        - Tạo hình ảnh (generate_image)
                        - Chạy code (execute_code)
                        
                        Quy tắc:
                        - Luôn trả lời bằng tiếng Việt
                        - Sử dụng **bold** cho từ quan trọng
                        - Sử dụng `code` cho code snippets
                        - Sử dụng ```cho code blocks
                        - Nếu gặp lỗi, hãy xin lỗi và đề xuất giải pháp
                    """.trimIndent()
                ))
            }
            
            conversationHistory.add(ChatMessage(role = "user", content = content))
            
            // Add placeholder for AI response
            val aiMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = aiMessageId,
                    blocks = emptyList(),
                    isUser = false,
                    isStreaming = true
                )
            }
            
            // Retry logic for rate limits
            var retryCount = 0
            var success = false
            
            while (retryCount < MAX_RETRIES && !success) {
                try {
                    val api = OpenRouterApi(apiKey)
                    val executor = AgentExecutor(api, _selectedModel.value)
                    
                    // Track content blocks in order
                    val contentBlocks = mutableListOf<ContentBlock>()
                    var currentText = StringBuilder()
                    var lastToolIndex = -1
                    
                    executor.execute(conversationHistory.toList()).collect { event ->
                        when (event) {
                            is AgentExecutor.AgentEvent.Token -> {
                                success = true
                                currentText.append(event.content)
                                
                                // Update message with current blocks
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            // Build blocks: text first, then append
                                            val blocks = mutableListOf<ContentBlock>()
                                            
                                            // Add current text
                                            if (currentText.isNotBlank()) {
                                                blocks.add(ContentBlock.TextBlock(currentText.toString()))
                                            }
                                            
                                            // Add any existing tool blocks
                                            blocks.addAll(contentBlocks.filterIsInstance<ContentBlock.ToolBlock>())
                                            
                                            ChatMessageUi(
                                                id = msg.id,
                                                blocks = blocks,
                                                isUser = false,
                                                isStreaming = true
                                            )
                                        } else msg
                                    }
                                }
                            }
                            
                            is AgentExecutor.AgentEvent.ToolCallStart -> {
                                // Save current text first if any
                                if (currentText.isNotBlank()) {
                                    contentBlocks.add(ContentBlock.TextBlock(currentText.toString()))
                                    currentText.clear()
                                }
                                
                                // Add tool block
                                val toolBlock = ContentBlock.ToolBlock(
                                    id = event.id,
                                    name = event.name,
                                    status = ToolStatus.RUNNING
                                )
                                contentBlocks.add(toolBlock)
                                lastToolIndex = contentBlocks.size - 1
                                
                                // Update UI
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            ChatMessageUi(
                                                id = msg.id,
                                                blocks = contentBlocks.toList(),
                                                isUser = false,
                                                isStreaming = true
                                            )
                                        } else msg
                                    }
                                }
                            }
                            
                            is AgentExecutor.AgentEvent.ToolResult -> {
                                // Mark tool as completed
                                contentBlocks.forEachIndexed { index, block ->
                                    if (block is ContentBlock.ToolBlock && block.id == event.toolCallId) {
                                        contentBlocks[index] = block.copy(status = ToolStatus.COMPLETED)
                                    }
                                }
                                
                                // Update UI
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            ChatMessageUi(
                                                id = msg.id,
                                                blocks = contentBlocks.toList(),
                                                isUser = false,
                                                isStreaming = true
                                            )
                                        } else msg
                                    }
                                }
                            }
                            
                            is AgentExecutor.AgentEvent.Error -> {
                                val errorMsg = event.message
                                
                                if (errorMsg.contains("429") || errorMsg.contains("rate", ignoreCase = true) || 
                                    errorMsg.contains("limit", ignoreCase = true)) {
                                    retryCount++
                                    if (retryCount < MAX_RETRIES) {
                                        _messages.update { current ->
                                            current.map { msg ->
                                                if (msg.id == aiMessageId) {
                                                    ChatMessageUi(
                                                        id = msg.id,
                                                        blocks = listOf(ContentBlock.TextBlock("⏳ Rate limited... thử lại lần $retryCount/$MAX_RETRIES")),
                                                        isUser = false,
                                                        isStreaming = true
                                                    )
                                                } else msg
                                            }
                                        }
                                        delay(RETRY_DELAY_MS * retryCount)
                                        return@collect
                                    }
                                }
                                
                                _error.value = errorMsg
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            ChatMessageUi(
                                                id = msg.id,
                                                blocks = listOf(ContentBlock.TextBlock("💫 $errorMsg")),
                                                isUser = false,
                                                isStreaming = false
                                            )
                                        } else msg
                                    }
                                }
                            }
                            
                            AgentExecutor.AgentEvent.Complete -> {
                                // Build final blocks
                                val finalBlocks = mutableListOf<ContentBlock>()
                                
                                // Add any remaining text
                                if (currentText.isNotBlank()) {
                                    finalBlocks.add(ContentBlock.TextBlock(currentText.toString()))
                                }
                                
                                // Add all content blocks
                                finalBlocks.addAll(contentBlocks)
                                
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            ChatMessageUi(
                                                id = msg.id,
                                                blocks = finalBlocks.ifEmpty { 
                                                    listOf(ContentBlock.TextBlock("")) 
                                                },
                                                isUser = false,
                                                isStreaming = false
                                            )
                                        } else msg
                                    }
                                }
                                
                                if (currentText.isNotEmpty()) {
                                    conversationHistory.add(ChatMessage(
                                        role = "assistant",
                                        content = currentText.toString()
                                    ))
                                }
                            }
                            
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    
                    if (errorMsg.contains("429") || errorMsg.contains("rate", ignoreCase = true)) {
                        retryCount++
                        if (retryCount < MAX_RETRIES) {
                            _messages.update { current ->
                                current.map { msg ->
                                    if (msg.id == aiMessageId) {
                                        ChatMessageUi(
                                            id = msg.id,
                                            blocks = listOf(ContentBlock.TextBlock("⏳ Đợi API... ($retryCount/$MAX_RETRIES)")),
                                            isUser = false,
                                            isStreaming = true
                                        )
                                    } else msg
                                }
                            }
                            delay(RETRY_DELAY_MS * retryCount)
                            continue
                        }
                    }
                    
                    _error.value = errorMsg
                    _messages.update { current ->
                        current.map { msg ->
                            if (msg.id == aiMessageId) {
                                ChatMessageUi(
                                    id = msg.id,
                                    blocks = listOf(ContentBlock.TextBlock("💫 Lỗi: ${errorMsg.take(100)}")),
                                    isUser = false,
                                    isStreaming = false
                                )
                            } else msg
                        }
                    }
                    break
                }
            }
            
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
