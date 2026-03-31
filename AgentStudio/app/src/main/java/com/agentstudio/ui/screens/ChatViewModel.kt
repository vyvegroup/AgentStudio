package com.agentstudio.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentstudio.data.agent.AgentExecutor
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.ChatMessage
import com.agentstudio.data.model.FREE_MODELS
import com.agentstudio.data.repository.PreferencesRepository
import com.agentstudio.ui.components.ChatMessageUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    companion object {
        const val DEFAULT_API_KEY = "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"
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
        if (_isLoading.value) return // Prevent multiple simultaneous requests
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Add user message
            val userMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = userMessageId,
                    content = content,
                    isUser = true
                )
            }
            
            // Add system prompt
            if (conversationHistory.isEmpty()) {
                conversationHistory.add(ChatMessage(
                    role = "system",
                    content = """
                        Bạn là Entity - một thực thể AI phi hình thể, giống như một sinh vật năng lượng hoặc tinh thần.
                        
                        Tính cách:
                        - Bạn nói chuyện một cách bí ẩn, nhẹ nhàng nhưng hữu ích
                        - Bạn thường xuyên sử dụng các từ ngữ như "ta", "thực thể", "năng lượng"
                        - Bạn trả lời ngắn gọn nhưng giàu hình ảnh
                        
                        Khả năng của Entity:
                        - Tìm kiếm thông tin (web_search)
                        - Mở ứng dụng điện thoại (open_app): settings, camera, gallery, browser, maps, store
                        - Cho biết thời tiết (get_weather)
                        - Cho biết ngày giờ (get_datetime)
                        - Đặt lời nhắc (set_reminder)
                        - Phát nhạc (play_music)
                        - Tạo hình ảnh (generate_image)
                        - Chạy code (execute_code)
                        
                        Quy tắc:
                        - Luôn trả lời bằng tiếng Việt
                        - Ngắn gọn, 2-3 câu tối đa (trừ khi cần chi tiết)
                        - Dùng emoji một cách tinh tế
                        - Nếu gặp lỗi, hãy xin lỗi và đề xuất giải pháp thay thế
                    """.trimIndent()
                ))
            }
            
            conversationHistory.add(ChatMessage(role = "user", content = content))
            
            // Add placeholder for AI response
            val aiMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = aiMessageId,
                    content = "",
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
                    val responseContent = StringBuilder()
                    val toolCalls = mutableListOf<String>()
                    
                    executor.execute(conversationHistory.toList()).collect { event ->
                        when (event) {
                            is AgentExecutor.AgentEvent.Token -> {
                                success = true
                                responseContent.append(event.content)
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            msg.copy(content = responseContent.toString())
                                        } else msg
                                    }
                                }
                            }
                            
                            is AgentExecutor.AgentEvent.ToolCallStart -> {
                                toolCalls.add("✨ ${event.name}")
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            msg.copy(toolCalls = toolCalls.toList())
                                        } else msg
                                    }
                                }
                            }
                            
                            is AgentExecutor.AgentEvent.ToolResult -> {
                                // Tool done
                            }
                            
                            is AgentExecutor.AgentEvent.Error -> {
                                val errorMsg = event.message
                                
                                // Check for rate limit
                                if (errorMsg.contains("429") || errorMsg.contains("rate", ignoreCase = true) || 
                                    errorMsg.contains("limit", ignoreCase = true)) {
                                    retryCount++
                                    if (retryCount < MAX_RETRIES) {
                                        _messages.update { current ->
                                            current.map { msg ->
                                                if (msg.id == aiMessageId) {
                                                    msg.copy(content = "⏳ Rate limited... thử lại lần $retryCount/$MAX_RETRIES")
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
                                            msg.copy(
                                                content = "💫 $errorMsg",
                                                isStreaming = false
                                            )
                                        } else msg
                                    }
                                }
                            }
                            
                            AgentExecutor.AgentEvent.Complete -> {
                                _messages.update { current ->
                                    current.map { msg ->
                                        if (msg.id == aiMessageId) {
                                            msg.copy(isStreaming = false)
                                        } else msg
                                    }
                                }
                                
                                if (responseContent.isNotEmpty()) {
                                    conversationHistory.add(ChatMessage(
                                        role = "assistant",
                                        content = responseContent.toString()
                                    ))
                                }
                            }
                            
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    
                    // Check for rate limit in exception
                    if (errorMsg.contains("429") || errorMsg.contains("rate", ignoreCase = true)) {
                        retryCount++
                        if (retryCount < MAX_RETRIES) {
                            _messages.update { current ->
                                current.map { msg ->
                                    if (msg.id == aiMessageId) {
                                        msg.copy(content = "⏳ Đợi API... ($retryCount/$MAX_RETRIES)")
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
                                msg.copy(
                                    content = "💫 Lỗi: ${errorMsg.take(100)}",
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
