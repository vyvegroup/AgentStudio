package com.agentstudio.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentstudio.data.model.AgentMessage
import com.agentstudio.domain.agent.AgentExecutor
import com.agentstudio.ui.components.AgentChatPanel
import com.agentstudio.ui.components.MessageItem
import com.agentstudio.ui.components.ToolCallIndicator
import com.agentstudio.ui.viewmodel.AgentViewModel
import com.agentstudio.ui.viewmodel.AgentViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel = viewModel(
        factory = AgentViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val pendingToolCalls by viewModel.pendingToolCalls.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Agent Studio")
                        if (isProcessing) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Xóa cuộc trò chuyện")
                    }
                    IconButton(onClick = { viewModel.retry() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Thử lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages list
            AgentChatPanel(
                messages = messages,
                streamingContent = streamingContent,
                isStreaming = isProcessing && streamingContent.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(),
                onMessageClick = { /* Handle message click */ }
            )
            
            // Tool call indicators
            if (pendingToolCalls.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Đang thực thi công cụ...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        pendingToolCalls.forEach { toolCall ->
                            ToolCallIndicator(
                                toolName = toolCall.name,
                                isExecuting = true,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // Error display
            error?.let { errorMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = "Lỗi",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.retry() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Thử lại",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(),
                        placeholder = { Text("Nhập tin nhắn...") },
                        maxLines = 5,
                        enabled = !isProcessing,
                        trailingIcon = {
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Xóa")
                                }
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isProcessing,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = "Gửi")
                        }
                    }
                    
                    if (isProcessing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { viewModel.cancelExecution() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Dừng")
                        }
                    }
                }
            }
        }
    }
}
