package com.agentstudio.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.agentstudio.data.model.AgentMessage
import com.agentstudio.ui.theme.AgentMessageBackground
import com.agentstudio.ui.theme.SystemMessageBackground
import com.agentstudio.ui.theme.ToolMessageBackground
import com.agentstudio.ui.theme.UserMessageBackground

@Composable
fun MessageItem(
    message: AgentMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (message) {
        is AgentMessage.UserMessage -> UserMessageItem(
            message = message,
            onClick = onClick,
            modifier = modifier
        )
        is AgentMessage.AgentResponse -> AgentResponseItem(
            message = message,
            onClick = onClick,
            modifier = modifier
        )
        is AgentMessage.ToolMessage -> ToolMessageItem(
            message = message,
            onClick = onClick,
            modifier = modifier
        )
        is AgentMessage.SystemMessage -> SystemMessageItem(
            message = message,
            onClick = onClick,
            modifier = modifier
        )
    }
}

@Composable
fun UserMessageItem(
    message: AgentMessage.UserMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .clickable(onClick = onClick)
                .animateContentSize(),
            color = UserMessageBackground,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun AgentResponseItem(
    message: AgentMessage.AgentResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .clickable(onClick = onClick)
                .animateContentSize(),
            color = AgentMessageBackground,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Agent avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "Agent",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Message content
                if (message.content.isNotEmpty()) {
                    MarkdownText(
                        text = message.content,
                        modifier = Modifier.animateContentSize()
                    )
                } else if (message.isStreaming) {
                    TypingIndicator()
                }
                
                // Tool calls
                if (message.toolCalls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    message.toolCalls.forEach { toolCall ->
                        ToolCallChip(
                            toolName = toolCall.name,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolMessageItem(
    message: AgentMessage.ToolMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = ToolMessageBackground,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (message.isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = if (message.isSuccess) "Thành công" else "Thất bại",
                    modifier = Modifier.size(16.dp),
                    tint = if (message.isSuccess) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Thu gọn" else "Mở rộng",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kết quả:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = message.result,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(8.dp),
                        maxLines = if (expanded) Int.MAX_VALUE else 3
                    )
                }
            }
        }
    }
}

@Composable
fun SystemMessageItem(
    message: AgentMessage.SystemMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = SystemMessageBackground,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "System",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text) {
        parseMarkdown(text)
    }
    
    SelectionContainer {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier
        )
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        repeat(3) { index ->
            Surface(
                modifier = Modifier
                    .size(8.dp)
                    .padding(2.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50)
            ) {}
            if (index < 2) Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
fun ToolCallChip(
    toolName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = toolName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val codeBlockRegex = """```(\w*)\n([\s\S]*?)```""".toRegex()
        val inlineCodeRegex = """`([^`]+)`""".toRegex()
        val boldRegex = """\*\*([^*]+)\*\*""".toRegex()
        val italicRegex = """\*([^*]+)\*""".toRegex()
        
        var currentIndex = 0
        val processedText = text
        
        // Simple markdown parsing
        append(processedText)
        
        // Apply styles (simplified)
        codeBlockRegex.findAll(processedText).forEach { match ->
            addStyle(
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = androidx.compose.ui.graphics.Color.DarkGray
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
        
        inlineCodeRegex.findAll(processedText).forEach { match ->
            addStyle(
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = androidx.compose.ui.graphics.Color.DarkGray
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
        
        boldRegex.findAll(processedText).forEach { match ->
            addStyle(
                style = SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }
}
