package com.agentstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentstudio.data.model.FREE_MODELS
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
    
    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    
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
                        // VenAI Logo
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1e1e2e)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                ReplitThinkingAnimation(size = 26)
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
                            val modelName = FREE_MODELS.find { it.id == selectedModel }?.name ?: "Unknown"
                            Text(
                                text = modelName,
                                fontSize = 9.sp,
                                color = Color(0xFF8B5CF6)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0x30000000),
                    titleContentColor = Color(0xFFE2E8F0)
                ),
                actions = {
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
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1e1e2e)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                ReplitThinkingAnimation(size = 70)
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
                            text = "Trợ lý AI thông minh\nsẵn sàng hỗ trợ bạn",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        
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
                                    onClick = { viewModel.sendMessage("Tìm kiếm tin tức AI mới nhất") }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickChip(
                                    text = "Thời tiết",
                                    icon = Icons.Default.WbSunny,
                                    onClick = { viewModel.sendMessage("Thời tiết hôm nay thế nào?") }
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
                                TypingIndicator()
                            }
                        }
                    }
                }
                
                // Error banner
                if (error != null) {
                    Surface(
                        color = Color(0x70000000),
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
            
            // Floating Chat Input
            FloatingChatInput(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isLoading) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                isLoading = isLoading
            )
        }
    }
    
    // Model selector sheet
    if (showModelSelector) {
        ModalBottomSheet(
            onDismissRequest = { showModelSelector = false },
            containerColor = Color(0xFF1a1a2e)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Chọn Model",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                FREE_MODELS.forEach { model ->
                    ModelOption(
                        model = model,
                        isSelected = model.id == selectedModel,
                        onClick = {
                            viewModel.setModel(model.id)
                            showModelSelector = false
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// Floating ChatGPT-style input bar
@Composable
private fun FloatingChatInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1a1a2e).copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Nhắn tin cho VenAI...",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0x15000000),
                    unfocusedContainerColor = Color(0x10000000),
                    focusedTextColor = Color(0xFFE2E8F0),
                    unfocusedTextColor = Color(0xFFE2E8F0),
                    cursorColor = Color(0xFF8B5CF6)
                ),
                maxLines = 5,
                minLines = 1,
                trailingIcon = {
                    IconButton(
                        onClick = onSend,
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (inputText.isNotBlank() && !isLoading)
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFf26207),
                                            Color(0xFFe2488b)
                                        )
                                    )
                                else
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF334155),
                                            Color(0xFF1e293b)
                                        )
                                    )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isLoading)
                                Color.White
                            else
                                Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun QuickChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0x20000000),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFF8B5CF6).copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF8B5CF6)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelOption(
    model: com.agentstudio.data.model.ModelInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFF3730A3)
            else
                Color(0xFF1e1e2e)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    text = model.provider,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
            if (model.isFree) {
                Text(
                    text = "FREE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF8B5CF6)
                )
            }
        }
    }
}
