package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

data class ChatMessageUi(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val toolCalls: List<String> = emptyList()
)

// Misty dreamy background
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
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Base dark gradient
        drawRect(
            brush = Brush.verticalGradient(
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
            val mistPath = Path()
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
                brush = Brush.verticalGradient(
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
                brush = Brush.radialGradient(
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
        // AI Avatar with formless entity
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x10000000)),
                contentAlignment = Alignment.Center
            ) {
                CompactFormlessEntity(isThinking = message.isStreaming)
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        
        // Message content
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (message.isUser) 20.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 20.dp
                    )
                )
                .background(
                    if (message.isUser)
                        Color(0xFF6366F1).copy(alpha = 0.9f)
                    else
                        Color(0xFF1a1a2e).copy(alpha = 0.85f)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Tool calls indicator
            if (message.toolCalls.isNotEmpty()) {
                message.toolCalls.forEach { tool ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Tool",
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tool,
                            fontSize = 11.sp,
                            color = Color(0xFF06B6D4),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (message.content.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            // Content text
            if (message.content.isNotEmpty()) {
                Text(
                    text = message.content,
                    color = if (message.isUser) Color.White else Color(0xFFE2E8F0),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }
            
            // Streaming indicator
            if (message.isStreaming && message.content.isEmpty()) {
                StreamThinkingIndicator()
            }
        }
        
        // User avatar
        if (message.isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6)),
                contentAlignment = Alignment.Center
            ) {
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

// Beautiful streaming thinking indicator
@Composable
private fun StreamThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { i ->
            val delay = i * 0.3f
            val scale = 0.6f + 0.4f * sin(phase + delay).toFloat().coerceIn(0f, 1f)
            val alpha = 0.4f + 0.6f * sin(phase + delay).toFloat().coerceIn(0f, 1f)
            
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = Color(0xFF8B5CF6).copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small formless entity
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            FormlessAIEntity(
                modifier = Modifier.size(45.dp),
                size = 45,
                isThinking = true
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        // Animated dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val delay = i * 0.4f
                val bounce = sin(phase + delay).toFloat()
                val alpha = (0.4f + 0.6f * (bounce + 1f) / 2f).coerceIn(0.3f, 1f)
                val size = (4 + 2 * (bounce + 1f) / 2f).dp
                
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6).copy(alpha = alpha),
                                    Color(0xFF6366F1).copy(alpha = alpha * 0.5f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "Thinking",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
