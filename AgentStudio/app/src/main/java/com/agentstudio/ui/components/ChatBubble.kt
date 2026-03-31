package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import kotlin.math.cos
import kotlin.math.sin

// ============================================
// Content Block Types
// ============================================
sealed class ContentBlock {
    data class TextBlock(val text: String) : ContentBlock()
    data class ToolBlock(
        val id: String,
        val name: String,
        val status: ToolStatus = ToolStatus.RUNNING
    ) : ContentBlock()
}

enum class ToolStatus { RUNNING, COMPLETED, ERROR }

// ============================================
// UI Message Model
// ============================================
data class ChatMessageUi(
    val id: String,
    val blocks: List<ContentBlock>,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val isLocal: Boolean = false
) {
    val textContent: String
        get() = blocks.filterIsInstance<ContentBlock.TextBlock>().joinToString("") { it.text }
    val tools: List<ContentBlock.ToolBlock>
        get() = blocks.filterIsInstance<ContentBlock.ToolBlock>()
}

// ============================================
// Tool Icon & Color
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
// Background
// ============================================
@Composable
fun MistyBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mist")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )
    
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFF0a0a12), Color(0xFF0d0d18), Color(0xFF101020))
            )
        )
        repeat(3) { layer ->
            val spot = Offset(
                size.width * (0.2f + layer * 0.3f) + cos(drift + layer).toFloat() * 20.dp.toPx(),
                size.height * (0.3f + layer * 0.2f) + sin(drift * 0.7f + layer).toFloat() * 20.dp.toPx()
            )
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.025f), Color.Transparent),
                    center = spot, radius = 180.dp.toPx()
                ),
                radius = 180.dp.toPx(), center = spot
            )
        }
    }
}

// ============================================
// Tool Badge - Minimal
// ============================================
@Composable
fun ToolBadge(toolName: String, status: ToolStatus = ToolStatus.RUNNING, modifier: Modifier = Modifier) {
    val icon = getToolIcon(toolName)
    val color = getToolColor(toolName)
    
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    
    val alpha = when (status) {
        ToolStatus.RUNNING -> 0.25f + 0.15f * pulse
        ToolStatus.COMPLETED -> 0.35f
        ToolStatus.ERROR -> 0.25f
    }
    
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = toolName, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

// ============================================
// Tool Row
// ============================================
@Composable
fun InlineToolRow(tools: List<ContentBlock.ToolBlock>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tools.forEach { ToolBadge(toolName = it.name, status = it.status) }
    }
}

// ============================================
// Simple Markdown
// ============================================
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFFF1F5F9)) {
    val annotatedString = remember(text) { parseSimpleMarkdown(text, color) }
    SelectionContainer {
        Text(text = annotatedString, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, modifier = modifier)
    }
}

fun parseSimpleMarkdown(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("```", i) -> {
                val end = text.indexOf("```", i + 3)
                if (end > i) {
                    val code = text.substring(i + 3, end).trim().substringAfter('\n', "")
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF94A3B8))) {
                        append(code)
                    }
                    i = end + 3
                } else append(text[i++])
            }
            text[i] == '`' && i + 1 < text.length -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF22D3EE))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else append(text[i++])
            }
            text.startsWith("**", i) && i + 2 < text.length -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else append(text[i++])
            }
            text[i] == '*' && i + 1 < text.length && text[i + 1] != '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = baseColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else append(text[i++])
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            // AI Avatar
            if (!message.isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (message.isLocal) Color(0xFF059669) else Color(0xFF1e1e2e)),
                    contentAlignment = Alignment.Center
                ) {
                    if (message.isLocal) {
                        Icon(Icons.Default.Storage, contentDescription = "Local", tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        ReplitThinkingAnimation(size = 20)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Message Content
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (message.isUser) Color(0xFF4F46E5)
                        else Color(0xFF16161e).copy(alpha = 0.95f)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val toolBuffer = mutableListOf<ContentBlock.ToolBlock>()
                
                message.blocks.forEach { block ->
                    when (block) {
                        is ContentBlock.TextBlock -> {
                            if (toolBuffer.isNotEmpty()) {
                                InlineToolRow(tools = toolBuffer.toList())
                                Spacer(modifier = Modifier.height(4.dp))
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
                
                if (message.isStreaming && message.blocks.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReplitThinkingAnimation(size = 14)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("...", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                }
            }
            
            // User avatar
            if (message.isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF8B5CF6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "You", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        // Local model indicator
        if (!message.isUser && message.isLocal) {
            Text(
                text = "Local AI",
                color = Color(0xFF059669),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 36.dp, top = 2.dp)
            )
        }
    }
}

// ============================================
// Typing Indicator
// ============================================
@Composable
fun TypingIndicator(modifier: Modifier = Modifier, isLocal: Boolean = false) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                .background(if (isLocal) Color(0xFF059669) else Color(0xFF1e1e2e)),
            contentAlignment = Alignment.Center
        ) {
            if (isLocal) {
                Icon(Icons.Default.Storage, contentDescription = "Local", tint = Color.White, modifier = Modifier.size(18.dp))
            } else {
                ReplitThinkingAnimation(size = 24)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (isLocal) "Processing locally..." else "Thinking...",
            color = if (isLocal) Color(0xFF059669) else Color(0xFF64748B),
            fontSize = 13.sp
        )
    }
}
