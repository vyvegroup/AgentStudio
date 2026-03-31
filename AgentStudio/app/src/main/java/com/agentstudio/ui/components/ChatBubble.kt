package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

// ============================================
// Message Content Block Types
// ============================================
sealed class ContentBlock {
    data class TextBlock(val text: String) : ContentBlock()
    data class ToolBlock(
        val id: String,
        val name: String,
        val status: ToolStatus = ToolStatus.RUNNING
    ) : ContentBlock()
}

enum class ToolStatus {
    RUNNING, COMPLETED, ERROR
}

// ============================================
// UI Message Model
// ============================================
data class ChatMessageUi(
    val id: String,
    val blocks: List<ContentBlock>,
    val isUser: Boolean,
    val isStreaming: Boolean = false
) {
    val textContent: String
        get() = blocks.filterIsInstance<ContentBlock.TextBlock>().joinToString("") { it.text }
    
    val tools: List<ContentBlock.ToolBlock>
        get() = blocks.filterIsInstance<ContentBlock.ToolBlock>()
}

// ============================================
// Tool Icon & Color Mapping
// ============================================
fun getToolIcon(toolName: String): ImageVector = when (toolName.lowercase()) {
    "web_search", "websearch", "search" -> Icons.Default.Search
    "open_app", "openapp" -> Icons.Default.Apps
    "weather", "get_weather" -> Icons.Default.WbSunny
    "reminder", "set_reminder" -> Icons.Default.Notifications
    "calendar", "get_datetime" -> Icons.Default.Schedule
    "code", "execute_code" -> Icons.Default.Terminal
    "music", "play_music" -> Icons.Default.MusicNote
    "image", "generate_image" -> Icons.Default.Image
    else -> Icons.Default.Build
}

fun getToolColor(toolName: String): Color = when (toolName.lowercase()) {
    "web_search", "websearch", "search" -> Color(0xFF3B82F6)
    "open_app", "openapp" -> Color(0xFF8B5CF6)
    "weather", "get_weather" -> Color(0xFFF59E0B)
    "reminder", "set_reminder" -> Color(0xFFEF4444)
    "calendar", "get_datetime" -> Color(0xFF10B981)
    "code", "execute_code" -> Color(0xFF06B6D4)
    "music", "play_music" -> Color(0xFFEC4899)
    "image", "generate_image" -> Color(0xFFA855F7)
    else -> Color(0xFF6366F1)
}

// ============================================
// Misty Background
// ============================================
@Composable
fun MistyBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mist")
    
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )
    
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFF0a0a12), Color(0xFF0f0f1a), Color(0xFF12121f))
            )
        )
        
        repeat(3) { layer ->
            val spot = Offset(
                size.width * (0.2f + layer * 0.3f) + cos(drift + layer).toFloat() * 30.dp.toPx(),
                size.height * (0.3f + layer * 0.2f) + sin(drift * 0.7f + layer).toFloat() * 30.dp.toPx()
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.03f), Color.Transparent),
                    center = spot, radius = 200.dp.toPx()
                ),
                radius = 200.dp.toPx(), center = spot
            )
        }
    }
}

// ============================================
// Tool Badge - Clean Minimal Design
// ============================================
@Composable
fun ToolBadge(
    toolName: String,
    status: ToolStatus = ToolStatus.RUNNING,
    modifier: Modifier = Modifier
) {
    val icon = getToolIcon(toolName)
    val color = getToolColor(toolName)
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    
    val alpha = when (status) {
        ToolStatus.RUNNING -> 0.3f + 0.2f * pulse
        ToolStatus.COMPLETED -> 0.4f
        ToolStatus.ERROR -> 0.3f
    }
    
    Surface(
        modifier = modifier.size(28.dp),
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = alpha)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = toolName, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// ============================================
// Inline Tool Row
// ============================================
@Composable
fun InlineToolRow(tools: List<ContentBlock.ToolBlock>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tools.forEach { tool ->
            ToolBadge(toolName = tool.name, status = tool.status)
        }
    }
}

// ============================================
// Simple Markdown Renderer
// ============================================
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF1F5F9)
) {
    val annotatedString = remember(text) { parseSimpleMarkdown(text, color) }
    SelectionContainer {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            modifier = modifier
        )
    }
}

fun parseSimpleMarkdown(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // Code block
            text.startsWith("```", i) -> {
                val end = text.indexOf("```", i + 3)
                if (end > i) {
                    val code = text.substring(i + 3, end).trim()
                    val codeText = code.substringAfter('\n', code)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF94A3B8))) {
                        append(codeText)
                    }
                    i = end + 3
                } else { append(text[i++]) }
            }
            // Inline code
            text[i] == '`' && i + 1 < text.length -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF22D3EE))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i++]) }
            }
            // Bold
            text.startsWith("**", i) && i + 2 < text.length -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i++]) }
            }
            // Italic
            text[i] == '*' && i + 1 < text.length && text[i + 1] != '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = baseColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i++]) }
            }
            else -> append(text[i++])
        }
    }
}

// ============================================
// Chat Bubble - Clean Modern Design
// ============================================
@Composable
fun ChatBubble(message: ChatMessageUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            // AI Avatar
            if (!message.isUser) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1e1e2e)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ReplitThinkingAnimation(size = 22)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Message Content
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (message.isUser) Color(0xFF6366F1)
                        else Color(0xFF1a1a28).copy(alpha = 0.9f)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Render blocks
                val toolBuffer = mutableListOf<ContentBlock.ToolBlock>()
                
                message.blocks.forEachIndexed { index, block ->
                    when (block) {
                        is ContentBlock.TextBlock -> {
                            if (toolBuffer.isNotEmpty()) {
                                InlineToolRow(tools = toolBuffer.toList())
                                Spacer(modifier = Modifier.height(6.dp))
                                toolBuffer.clear()
                            }
                            if (block.text.isNotBlank()) {
                                MarkdownText(
                                    text = block.text,
                                    color = if (message.isUser) Color.White else Color(0xFFF1F5F9)
                                )
                            }
                        }
                        is ContentBlock.ToolBlock -> toolBuffer.add(block)
                    }
                }
                
                if (toolBuffer.isNotEmpty()) {
                    InlineToolRow(tools = toolBuffer.toList())
                }
                
                // Streaming indicator
                if (message.isStreaming && message.blocks.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReplitThinkingAnimation(size = 16)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("...", color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                }
            }
            
            // User avatar
            if (message.isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF8B5CF6)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("You", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ============================================
// Typing Indicator
// ============================================
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF1e1e2e)
        ) {
            Box(contentAlignment = Alignment.Center) {
                ReplitThinkingAnimation(size = 28)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("Thinking...", color = Color(0xFF64748B), fontSize = 14.sp)
    }
}
