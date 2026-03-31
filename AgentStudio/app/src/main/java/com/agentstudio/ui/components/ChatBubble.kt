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
import androidx.compose.ui.text.style.TextDecoration
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
    // Helper to get all text content
    val textContent: String
        get() = blocks.filterIsInstance<ContentBlock.TextBlock>().joinToString("") { it.text }
    
    // Helper to get all tools
    val tools: List<ContentBlock.ToolBlock>
        get() = blocks.filterIsInstance<ContentBlock.ToolBlock>()
}

// ============================================
// Tool Icon Mapping
// ============================================
fun getToolIcon(toolName: String): ImageVector {
    return when (toolName.lowercase()) {
        "web_search", "websearch", "search" -> Icons.Default.Search
        "open_app", "openapp" -> Icons.Default.Apps
        "weather" -> Icons.Default.WbSunny
        "reminder", "set_reminder" -> Icons.Default.Notifications
        "calendar", "schedule" -> Icons.Default.CalendarMonth
        "code", "execute_code" -> Icons.Default.Code
        "music", "play_music" -> Icons.Default.MusicNote
        "location", "maps" -> Icons.Default.LocationOn
        "call", "phone" -> Icons.Default.Phone
        "message", "send_message" -> Icons.Default.Message
        "camera", "photo" -> Icons.Default.CameraAlt
        "file", "files" -> Icons.Default.Folder
        "calculator", "math" -> Icons.Default.Calculate
        "translate", "translation" -> Icons.Default.Translate
        else -> Icons.Default.Build
    }
}

fun getToolColor(toolName: String): Color {
    return when (toolName.lowercase()) {
        "web_search", "websearch", "search" -> Color(0xFF3B82F6) // Blue
        "open_app", "openapp" -> Color(0xFF8B5CF6) // Purple
        "weather" -> Color(0xFFF59E0B) // Amber
        "reminder", "set_reminder" -> Color(0xFFEF4444) // Red
        "calendar", "schedule" -> Color(0xFF10B981) // Green
        "code", "execute_code" -> Color(0xFF06B6D4) // Cyan
        "music", "play_music" -> Color(0xFFEC4899) // Pink
        else -> Color(0xFF6366F1) // Indigo
    }
}

// ============================================
// Misty Background
// ============================================
@Composable
fun MistyBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mist")
    
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    androidx.compose.foundation.Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Base dark gradient
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF050508),
                    Color(0xFF0a0a15),
                    Color(0xFF0f0f1a),
                    Color(0xFF12122a)
                )
            )
        )
        
        // Floating dark mist layers
        repeat(5) { layer ->
            val mistPath = androidx.compose.ui.graphics.Path()
            val segments = 80
            val width = size.width
            val height = size.height
            
            for (i in 0..segments) {
                val x = width * i / segments
                val baseY = height * (0.2f + layer * 0.15f)
                val waveY = sin(x / width * 5 * PI + drift * (0.8f + layer * 0.1f) + layer * 1.3f).toFloat() * 
                    40.dp.toPx() * pulse
                val y = baseY + waveY
                
                if (i == 0) mistPath.moveTo(x, y) else mistPath.lineTo(x, y)
            }
            
            mistPath.lineTo(width, height)
            mistPath.lineTo(0f, height)
            mistPath.close()
            
            drawPath(
                path = mistPath,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2d1b4e).copy(alpha = 0.02f * (5 - layer)),
                        Color(0xFF1a1a2e).copy(alpha = 0.015f),
                        Color.Transparent
                    )
                )
            )
        }
        
        // Subtle glow orbs
        val glowSpots = listOf(
            Offset(size.width * 0.15f, size.height * 0.25f),
            Offset(size.width * 0.85f, size.height * 0.4f),
            Offset(size.width * 0.5f, size.height * 0.65f),
            Offset(size.width * 0.3f, size.height * 0.85f)
        )
        
        glowSpots.forEachIndexed { index, spot ->
            val animatedSpot = Offset(
                spot.x + cos(drift * 0.5f + index * 1.5f).toFloat() * 30.dp.toPx(),
                spot.y + sin(drift * 0.4f + index * 2f).toFloat() * 30.dp.toPx()
            )
            
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6).copy(alpha = 0.03f * pulse),
                        Color.Transparent
                    ),
                    center = animatedSpot,
                    radius = 200.dp.toPx()
                ),
                radius = 200.dp.toPx(),
                center = animatedSpot
            )
        }
    }
}

// ============================================
// Tool Badge Component - Rounded Square with Icon
// ============================================
@Composable
fun ToolBadge(
    toolName: String,
    status: ToolStatus = ToolStatus.RUNNING,
    modifier: Modifier = Modifier
) {
    val icon = getToolIcon(toolName)
    val color = getToolColor(toolName)
    
    val infiniteTransition = rememberInfiniteTransition(label = "tool_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val (bgColor, iconColor) = when (status) {
        ToolStatus.RUNNING -> color.copy(alpha = 0.2f * pulse) to color
        ToolStatus.COMPLETED -> Color(0xFF10B981).copy(alpha = 0.2f) to Color(0xFF10B981)
        ToolStatus.ERROR -> Color(0xFFEF4444).copy(alpha = 0.2f) to Color(0xFFEF4444)
    }
    
    Surface(
        modifier = modifier.size(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, iconColor.copy(alpha = 0.3f))
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = toolName,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================
// Inline Tool Row - Multiple tools side by side
// ============================================
@Composable
fun InlineToolRow(
    tools: List<ContentBlock.ToolBlock>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tools.forEach { tool ->
            ToolBadge(
                toolName = tool.name,
                status = tool.status
            )
        }
    }
}

// ============================================
// Markdown Parser (Simple)
// ============================================
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE2E8F0)
) {
    val annotatedString = remember(text) { parseMarkdown(text, color) }
    
    SelectionContainer {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp,
            modifier = modifier
        )
    }
}

fun parseMarkdown(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        
        while (remaining.isNotEmpty()) {
            when {
                // Code block
                remaining.startsWith("```") -> {
                    val endIndex = remaining.indexOf("```", 3)
                    if (endIndex != -1) {
                        val codeContent = remaining.substring(3, endIndex)
                        // Skip language identifier if present
                        val codeStart = codeContent.indexOf('\n').let { if (it >= 0) it + 1 else 0 }
                        val code = codeContent.substring(codeStart)
                        
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            background = Color(0xFF1e1e2e),
                            color = Color(0xFF94A3B8)
                        )) {
                            append(code.trim())
                        }
                        remaining = remaining.substring(endIndex + 3)
                        if (remaining.startsWith("\n")) remaining = remaining.substring(1)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
                // Inline code
                remaining.startsWith("`") && remaining.indexOf("`", 1) > 0 -> {
                    val endIndex = remaining.indexOf("`", 1)
                    val code = remaining.substring(1, endIndex)
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = Color(0xFF1e1e2e),
                        color = Color(0xFF06B6D4)
                    )) {
                        append(code)
                    }
                    remaining = remaining.substring(endIndex + 1)
                }
                // Bold
                remaining.startsWith("**") && remaining.indexOf("**", 2) > 0 -> {
                    val endIndex = remaining.indexOf("**", 2)
                    val boldText = remaining.substring(2, endIndex)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(boldText)
                    }
                    remaining = remaining.substring(endIndex + 2)
                }
                // Italic
                remaining.startsWith("*") && remaining.indexOf("*", 1) > 0 -> {
                    val endIndex = remaining.indexOf("*", 1)
                    val italicText = remaining.substring(1, endIndex)
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = baseColor)) {
                        append(italicText)
                    }
                    remaining = remaining.substring(endIndex + 1)
                }
                // Link
                remaining.startsWith("[") && remaining.contains("](") && remaining.contains(")") -> {
                    val textEnd = remaining.indexOf("](")
                    val urlEnd = remaining.indexOf(")", textEnd)
                    if (textEnd > 0 && urlEnd > textEnd) {
                        val linkText = remaining.substring(1, textEnd)
                        val url = remaining.substring(textEnd + 2, urlEnd)
                        withStyle(SpanStyle(
                            color = Color(0xFF8B5CF6),
                            textDecoration = TextDecoration.Underline
                        )) {
                            append(linkText)
                        }
                        remaining = remaining.substring(urlEnd + 1)
                    } else {
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    }
                }
                // Normal text
                else -> {
                    val nextSpecial = listOf(
                        remaining.indexOf("```").let { if (it < 0) Int.MAX_VALUE else it },
                        remaining.indexOf("`").let { if (it < 0) Int.MAX_VALUE else it },
                        remaining.indexOf("**").let { if (it < 0) Int.MAX_VALUE else it },
                        remaining.indexOf("*").let { if (it < 0) Int.MAX_VALUE else it },
                        remaining.indexOf("[").let { if (it < 0) Int.MAX_VALUE else it }
                    ).minOrNull() ?: Int.MAX_VALUE
                    
                    if (nextSpecial == Int.MAX_VALUE || nextSpecial == 0) {
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    } else {
                        append(remaining.substring(0, nextSpecial))
                        remaining = remaining.substring(nextSpecial)
                    }
                }
            }
        }
    }
}

// ============================================
// Chat Bubble Component
// ============================================
@Composable
fun ChatBubble(
    message: ChatMessageUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // AI Avatar
        if (!message.isUser) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1e1e2e)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ReplitThinkingAnimation(size = 24)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        
        // Message Content
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.isUser) 18.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 18.dp
                    )
                )
                .background(
                    if (message.isUser)
                        Color(0xFF6366F1).copy(alpha = 0.9f)
                    else
                        Color(0xFF1a1a2e).copy(alpha = 0.9f)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Render content blocks in order
            var toolBuffer = mutableListOf<ContentBlock.ToolBlock>()
            
            message.blocks.forEachIndexed { index, block ->
                when (block) {
                    is ContentBlock.TextBlock -> {
                        // Flush any pending tools first
                        if (toolBuffer.isNotEmpty()) {
                            InlineToolRow(tools = toolBuffer.toList())
                            Spacer(modifier = Modifier.height(6.dp))
                            toolBuffer.clear()
                        }
                        
                        // Render text with markdown
                        if (block.text.isNotBlank()) {
                            MarkdownText(
                                text = block.text,
                                color = if (message.isUser) Color.White else Color(0xFFE2E8F0)
                            )
                            
                            // Add spacing if there's more content
                            if (index < message.blocks.lastIndex) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                    is ContentBlock.ToolBlock -> {
                        toolBuffer.add(block)
                    }
                }
            }
            
            // Flush remaining tools
            if (toolBuffer.isNotEmpty()) {
                InlineToolRow(tools = toolBuffer.toList())
            }
            
            // Streaming indicator
            if (message.isStreaming && message.blocks.isEmpty()) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReplitThinkingAnimation(size = 20)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thinking...",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            }
        }
        
        // User avatar
        if (message.isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF8B5CF6)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "You",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ============================================
// Typing Indicator
// ============================================
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1e1e2e)
        ) {
            Box(contentAlignment = Alignment.Center) {
                ReplitThinkingAnimation(size = 32)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = "Thinking...",
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
