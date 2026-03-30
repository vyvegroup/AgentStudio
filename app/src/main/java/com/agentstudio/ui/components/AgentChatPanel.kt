package com.agentstudio.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentstudio.data.model.AgentMessage
import kotlinx.coroutines.launch

@Composable
fun AgentChatPanel(
    messages: List<AgentMessage>,
    streamingContent: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    onMessageClick: (AgentMessage) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize()
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                MessageItem(
                    message = message,
                    onClick = { onMessageClick(message) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .animateContentSize()
                )
            }
            
            // Streaming indicator
            if (isStreaming && streamingContent.isNotEmpty()) {
                item {
                    MessageItem(
                        message = AgentMessage.AgentResponse(
                            content = streamingContent,
                            isStreaming = true
                        ),
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateContentSize()
                    )
                }
            }
            
            // Empty state
            if (messages.isEmpty() && !isStreaming) {
                item {
                    EmptyChatState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = "🤖",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Xin chào! Tôi là Agent Studio",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tôi có thể giúp bạn:\n" +
                    "• Trả lời câu hỏi và hỗ trợ lập trình\n" +
                    "• Tạo, đọc, sửa và xóa file\n" +
                    "• Tìm kiếm và quản lý thư mục\n\n" +
                    "Hãy nhập tin nhắn để bắt đầu!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
