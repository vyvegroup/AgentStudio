package com.agentstudio.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentstudio.BuildConfig
import com.agentstudio.data.agent.AgentExecutor
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.local.LocalLLMEngine
import com.agentstudio.data.local.LocalModelManager
import com.agentstudio.data.local.LocalModels
import com.agentstudio.data.model.ChatMessage
import com.agentstudio.data.model.FREE_MODELS
import com.agentstudio.data.model.LOCAL_MODEL
import com.agentstudio.data.repository.PreferencesRepository
import com.agentstudio.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val application: Application? = null
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
    
    // Local AI states
    private val _isUsingLocal = MutableStateFlow(false)
    val isUsingLocal: StateFlow<Boolean> = _isUsingLocal.asStateFlow()
    
    private val _isLocalReady = MutableStateFlow(false)
    val isLocalReady: StateFlow<Boolean> = _isLocalReady.asStateFlow()
    
    private val conversationHistory = mutableListOf<ChatMessage>()
    private var apiKey: String = DEFAULT_API_KEY
    
    // Local LLM Engine
    private var localLLMEngine: LocalLLMEngine? = null
    private var localModelManager: LocalModelManager? = null
    
    init {
        apiKey = DEFAULT_API_KEY
        
        application?.let { app ->
            localLLMEngine = LocalLLMEngine.getInstance(app)
            localModelManager = LocalModelManager(app)
            
            // Check if local model is downloaded
            viewModelScope.launch {
                val isDownloaded = localModelManager?.isModelDownloaded(LocalModels.GEMMA_4B) ?: false
                _isLocalReady.value = isDownloaded
            }
        }
        
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
    
    fun toggleLocalAI() {
        if (!_isLocalReady.value && !_isUsingLocal.value) {
            // Show message that model needs to be downloaded
            _error.value = "Cần tải model Local AI trước. Vào Settings để tải."
            return
        }
        _isUsingLocal.value = !_isUsingLocal.value
    }
    
    fun checkLocalModelStatus() {
        viewModelScope.launch {
            val isDownloaded = localModelManager?.isModelDownloaded(LocalModels.GEMMA_4B) ?: false
            _isLocalReady.value = isDownloaded
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
        conversationHistory.clear()
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (_isLoading.value) return
        
        // Check if using local AI
        if (_isUsingLocal.value) {
            sendLocalMessage(content)
            return
        }
        
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
                        Bạn là VenAI - trợ lý AI thông minh.

                        ## Công cụ có sẵn:
                        - web_search(query): Tìm kiếm thông tin trên web
                        - get_weather(location): Xem thời tiết
                        - get_datetime(): Xem ngày giờ hiện tại
                        - open_app(app_name): Mở ứng dụng (settings, camera, browser, maps, youtube, spotify...)
                        - set_reminder(task, time): Đặt nhắc nhở
                        - play_music(query): Tìm nhạc
                        - calculate(expression): Tính toán
                        - execute_code(code, language): Chạy code

                        ## Quy tắc:
                        1. Trả lời ngắn gọn, hữu ích bằng tiếng Việt
                        2. Khi người dùng hỏi thông tin mới → dùng web_search
                        3. Khi hỏi thời tiết → dùng get_weather
                        4. Khi yêu cầu mở app → dùng open_app
                        5. Format: **quan trọng**, `code`, không dùng markdown phức tạp
                        6. Nếu không cần tool → trả lời trực tiếp
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
    
    private fun sendLocalMessage(content: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Add user message
            val userMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = userMessageId,
                    blocks = listOf(ContentBlock.TextBlock(content)),
                    isUser = true
                )
            }
            
            // Add placeholder for AI response
            val aiMessageId = UUID.randomUUID().toString()
            _messages.update { current ->
                current + ChatMessageUi(
                    id = aiMessageId,
                    blocks = emptyList(),
                    isUser = false,
                    isStreaming = true,
                    isLocal = true
                )
            }
            
            try {
                val engine = localLLMEngine
                if (engine == null || !engine.isReady()) {
                    // Try to load model
                    val modelPath = localModelManager?.getLocalModelPath(LocalModels.GEMMA_4B)
                    if (modelPath != null) {
                        engine?.loadModel(modelPath)
                    } else {
                        _error.value = "Model chưa được tải. Vào Settings để tải model."
                        _messages.update { current ->
                            current.map { msg ->
                                if (msg.id == aiMessageId) {
                                    ChatMessageUi(
                                        id = msg.id,
                                        blocks = listOf(ContentBlock.TextBlock("⚠️ Model chưa được tải. Vào Settings để tải model Local AI.")),
                                        isUser = false,
                                        isStreaming = false,
                                        isLocal = true
                                    )
                                } else msg
                            }
                        }
                        _isLoading.value = false
                        return@launch
                    }
                }
                
                // Build prompt from conversation
                val messages = mutableListOf<Pair<String, String>>()
                messages.add("system" to "Bạn là VenAI - trợ lý AI thông minh chạy trên thiết bị. Trả lời ngắn gọn, hữu ích bằng tiếng Việt.")
                messages.add("user" to content)
                
                val responseText = StringBuilder()
                
                engine?.chat(messages)?.collect { token ->
                    responseText.append(token)
                    
                    _messages.update { current ->
                        current.map { msg ->
                            if (msg.id == aiMessageId) {
                                ChatMessageUi(
                                    id = msg.id,
                                    blocks = listOf(ContentBlock.TextBlock(responseText.toString())),
                                    isUser = false,
                                    isStreaming = true,
                                    isLocal = true
                                )
                            } else msg
                        }
                    }
                }
                
                // Mark as complete
                _messages.update { current ->
                    current.map { msg ->
                        if (msg.id == aiMessageId) {
                            ChatMessageUi(
                                id = msg.id,
                                blocks = listOf(ContentBlock.TextBlock(responseText.toString())),
                                isUser = false,
                                isStreaming = false,
                                isLocal = true
                            )
                        } else msg
                    }
                }
                
            } catch (e: Exception) {
                _error.value = e.message
                _messages.update { current ->
                    current.map { msg ->
                        if (msg.id == aiMessageId) {
                            ChatMessageUi(
                                id = msg.id,
                                blocks = listOf(ContentBlock.TextBlock("💫 Lỗi Local AI: ${e.message}")),
                                isUser = false,
                                isStreaming = false,
                                isLocal = true
                            )
                        } else msg
                    }
                }
            }
            
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
