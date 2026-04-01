package com.agentstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentstudio.AgentStudioApp
import com.agentstudio.data.model.ALL_MODELS
import com.agentstudio.data.model.FREE_MODELS
import com.agentstudio.data.model.LOCAL_MODEL
import com.agentstudio.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isUsingLocal by viewModel.isUsingLocal.collectAsState()
    val isLocalReady by viewModel.isLocalReady.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    
    // Get local model manager from app instance
    val localModelManager = remember { AgentStudioApp.instance.localModelManager }
    
    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Show error snackbar
    error?.let { errorMsg ->
        LaunchedEffect(errorMsg) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearError()
        }
    }
    
    val selectedModelInfo = ALL_MODELS.find { it.id == selectedModel } ?: FREE_MODELS.first()
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Misty dreamy background
        MistyBackground(
            modifier = Modifier.fillMaxSize()
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top bar
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // VenAI Logo with animation
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUsingLocal) Color(0xFF059669) else Color(0xFF1e1e2e)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUsingLocal) {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = "Local",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    ReplitThinkingAnimation(size = 26)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "VenAI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFFE2E8F0)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isUsingLocal) {
                                    Icon(
                                        Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = Color(0xFF10B981)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = if (isUsingLocal) "Local AI" else selectedModelInfo.name,
                                    fontSize = 9.sp,
                                    color = if (isUsingLocal) Color(0xFF10B981) else Color(0xFF8B5CF6)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0x20000000),
                    titleContentColor = Color(0xFFE2E8F0)
                ),
                actions = {
                    // Local AI Toggle
                    IconButton(onClick = { viewModel.toggleLocalAI() }) {
                        Icon(
                            imageVector = if (isUsingLocal) Icons.Default.CloudOff else Icons.Default.Cloud,
                            contentDescription = "Toggle Local AI",
                            tint = if (isUsingLocal) Color(0xFF10B981) else Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = { showModelSelector = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF8B5CF6)
                        )
                    }
                    IconButton(onClick = { viewModel.clearMessages() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Clear",
                            tint = Color(0xFF8B5CF6)
                        )
                    }
                }
            )
            
            // Messages list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    // Welcome screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Large logo
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = if (isUsingLocal) Color(0xFF059669) else Color(0xFF1e1e2e)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUsingLocal) {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = "Local",
                                        tint = Color.White,
                                        modifier = Modifier.size(50.dp)
                                    )
                                } else {
                                    ReplitThinkingAnimation(size = 70)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(28.dp))
                        
                        Text(
                            text = "VenAI",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isUsingLocal) "AI chạy offline trên thiết bị của bạn"
                                   else "Trợ lý AI thông minh\nsẵn sàng hỗ trợ bạn",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        
                        // Local AI status indicator
                        if (isUsingLocal && !isLocalReady) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Cần tải model trước khi dùng",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        
                        // Demo mode banner
                        if (isUsingLocal && isLocalReady && isDemoMode) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Science,
                                            contentDescription = null,
                                            tint = Color(0xFF8B5CF6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Local AI - Demo Mode",
                                            color = Color(0xFF8B5CF6),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Model đã tải nhưng đang chạy demo.\nSử dụng Cloud AI để có trải nghiệm tốt nhất.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(36.dp))
                        
                        // Quick action chips
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickChip(
                                    text = "Bạn làm được gì?",
                                    icon = Icons.Default.HelpOutline,
                                    onClick = { viewModel.sendMessage("Bạn có thể làm gì cho tôi?") }
                                )
                                QuickChip(
                                    text = "Tìm kiếm web",
                                    icon = Icons.Default.Search,
                                    onClick = { viewModel.sendMessage("Tìm kiếm tin tức AI mới nhất") },
                                    enabled = !isUsingLocal
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickChip(
                                    text = "Thời tiết",
                                    icon = Icons.Default.WbSunny,
                                    onClick = { viewModel.sendMessage("Thời tiết hôm nay thế nào?") },
                                    enabled = !isUsingLocal
                                )
                                QuickChip(
                                    text = "Viết code",
                                    icon = Icons.Default.Code,
                                    onClick = { viewModel.sendMessage("Viết một hàm Python để tính fibonacci") }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatBubble(message = message)
                        }
                        
                        if (isLoading && messages.lastOrNull()?.isStreaming != true) {
                            item {
                                TypingIndicator(isLocal = isUsingLocal)
                            }
                        }
                    }
                }
                
                // Error banner
                if (error != null) {
                    Surface(
                        color = Color(0x80000000),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error ?: "",
                                color = Color(0xFFFCD34D),
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFFFCD34D)
                                )
                            }
                        }
                    }
                }
            }
            
            // Premium Floating Chat Input - Big Tech Style
            PremiumFloatingChatInput(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isLoading) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                isLoading = isLoading,
                isLocal = isUsingLocal
            )
        }
    }
    
    // Model selector sheet
    if (showModelSelector) {
        ModelSelectionSheet(
            selectedModel = selectedModel,
            onModelSelected = { modelId ->
                viewModel.setModel(modelId)
            },
            onDismiss = { showModelSelector = false },
            localModelManager = localModelManager
        )
    }
}

// Premium Floating Chat Input - ChatGPT/Big Tech Style
@Composable
private fun PremiumFloatingChatInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isLocal: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Animated scale for send button
    val sendButtonScale by animateFloatAsState(
        targetValue = if (inputText.isNotBlank() && !isLoading) 1f else 0.9f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sendButtonScale"
    )
    
    // Glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Outer container with soft shadow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Soft glow effect behind the input
        if (isFocused || inputText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .graphicsLayer {
                        shadowElevation = 16f
                        shape = RoundedCornerShape(28.dp)
                        clip = false
                    }
                    .background(
                        if (isLocal) {
                            Color(0xFF10B981).copy(alpha = glowAlpha * 0.1f)
                        } else {
                            Color(0xFF8B5CF6).copy(alpha = glowAlpha * 0.1f)
                        },
                        RoundedCornerShape(28.dp)
                    )
            )
        }
        
        // Main input container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1a1a2e).copy(alpha = 0.97f),
            tonalElevation = if (isFocused) 4.dp else 2.dp,
            border = if (isFocused) {
                androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isLocal) Color(0xFF10B981).copy(alpha = 0.5f) 
                            else Color(0xFF8B5CF6).copy(alpha = 0.5f)
                )
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text input area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Placeholder
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (isLocal) "Nhắn tin cho Local AI..." else "Nhắn tin cho VenAI...",
                            color = Color(0xFF64748B),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    
                    // Text field
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFF1F5F9),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(
                            if (isLocal) Color(0xFF10B981) else Color(0xFF8B5CF6)
                        ),
                        maxLines = 5,
                        minLines = 1,
                        interactionSource = interactionSource,
                        decorationBox = { innerTextField ->
                            innerTextField()
                        }
                    )
                }
                
                // Send button with premium styling
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = sendButtonScale
                            scaleY = sendButtonScale
                        }
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isLoading -> Color(0xFF374151)
                                inputText.isNotBlank() -> {
                                    if (isLocal) {
                                        Color(0xFF10B981)
                                    } else {
                                        Color(0xFF8B5CF6)
                                    }
                                }
                                else -> Color(0xFF374151)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Ripple effect
                    if (inputText.isNotBlank() && !isLoading) {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Send",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun QuickChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) Color(0x20000000) else Color(0x10000000),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (enabled) Color(0xFF8B5CF6).copy(alpha = 0.3f) else Color(0xFF64748B).copy(alpha = 0.2f)
        ),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (enabled) Color(0xFF8B5CF6) else Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (enabled) Color(0xFFE2E8F0) else Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (!enabled) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = Color(0xFF64748B)
                )
            }
        }
    }
}
