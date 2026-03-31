package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/**
 * Replit Agent style thinking animation
 * A beautiful pulsing, morphing gradient animation
 */
@Composable
fun ReplitThinkingAnimation(
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    val infiniteTransition = rememberInfiniteTransition(label = "replit")
    
    // Main pulse animation
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Color shift
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "colorShift"
    )
    
    // Rotation for gradient
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Secondary pulse (offset)
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )
    
    val colors = remember {
        listOf(
            Color(0xFFf26207), // Replit orange
            Color(0xFFe2488b), // Pink
            Color(0xFF9b4dff), // Purple
            Color(0xFF5c8df5), // Blue
            Color(0xFFf26207)  // Back to orange
        )
    }
    
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val center = Offset(size.dp.toPx() / 2, size.dp.toPx() / 2)
        val baseSize = size.dp.toPx() * 0.35f
        
        // Outer glow layers
        repeat(3) { layer ->
            val glowSize = baseSize * (1.8f + layer * 0.3f + pulse * 0.2f)
            val alpha = (0.15f - layer * 0.04f) * (0.7f + pulse * 0.3f)
            
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors[(colorShift * colors.size).toInt() % colors.size].copy(alpha = alpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowSize
                ),
                topLeft = Offset(center.x - glowSize, center.y - glowSize),
                size = Size(glowSize * 2, glowSize * 2)
            )
        }
        
        // Main gradient square (Replit style)
        val mainSize = baseSize * (0.9f + pulse * 0.1f)
        val colorIndex = (colorShift * colors.size).toInt() % colors.size
        
        rotate(rotation, center) {
            val rectPath = Path().apply {
                val r = mainSize * 0.25f
                moveTo(center.x - mainSize + r, center.y - mainSize)
                lineTo(center.x + mainSize - r, center.y - mainSize)
                quadraticBezierTo(center.x + mainSize, center.y - mainSize, center.x + mainSize, center.y - mainSize + r)
                lineTo(center.x + mainSize, center.y + mainSize - r)
                quadraticBezierTo(center.x + mainSize, center.y + mainSize, center.x + mainSize - r, center.y + mainSize)
                lineTo(center.x - mainSize + r, center.y + mainSize)
                quadraticBezierTo(center.x - mainSize, center.y + mainSize, center.x - mainSize, center.y + mainSize - r)
                lineTo(center.x - mainSize, center.y - mainSize + r)
                quadraticBezierTo(center.x - mainSize, center.y - mainSize, center.x - mainSize + r, center.y - mainSize)
                close()
            }
            
            drawPath(
                path = rectPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors[colorIndex],
                        colors[(colorIndex + 1) % colors.size],
                        colors[(colorIndex + 2) % colors.size]
                    ),
                    start = Offset(center.x - mainSize, center.y - mainSize),
                    end = Offset(center.x + mainSize, center.y + mainSize)
                )
            )
        }
        
        // Inner highlight
        val innerSize = mainSize * 0.6f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.3f + pulse * 0.2f),
                    Color.Transparent
                ),
                center = Offset(center.x - innerSize * 0.3f, center.y - innerSize * 0.3f),
                radius = innerSize
            ),
            topLeft = Offset(center.x - innerSize, center.y - innerSize),
            size = Size(innerSize * 2, innerSize * 2)
        )
        
        // Secondary animated square (offset)
        val offsetSize = baseSize * 0.4f * (0.8f + pulse2 * 0.2f)
        val offset = baseSize * 0.8f
        val offsetX = center.x + cos(rotation * PI / 180f * 1.5f).toFloat() * offset * 0.3f
        val offsetY = center.y + sin(rotation * PI / 180f * 1.5f).toFloat() * offset * 0.3f
        
        rotate(-rotation * 0.5f, Offset(offsetX, offsetY)) {
            val smallRectPath = Path().apply {
                val r = offsetSize * 0.3f
                moveTo(offsetX - offsetSize + r, offsetY - offsetSize)
                lineTo(offsetX + offsetSize - r, offsetY - offsetSize)
                quadraticBezierTo(offsetX + offsetSize, offsetY - offsetSize, offsetX + offsetSize, offsetY - offsetSize + r)
                lineTo(offsetX + offsetSize, offsetY + offsetSize - r)
                quadraticBezierTo(offsetX + offsetSize, offsetY + offsetSize, offsetX + offsetSize - r, offsetY + offsetSize)
                lineTo(offsetX - offsetSize + r, offsetY + offsetSize)
                quadraticBezierTo(offsetX - offsetSize, offsetY + offsetSize, offsetX - offsetSize, offsetY + offsetSize - r)
                lineTo(offsetX - offsetSize, offsetY - offsetSize + r)
                quadraticBezierTo(offsetX - offsetSize, offsetY - offsetSize, offsetX - offsetSize + r, offsetY - offsetSize)
                close()
            }
            
            drawPath(
                path = smallRectPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors[(colorIndex + 2) % colors.size].copy(alpha = 0.6f),
                        colors[(colorIndex + 3) % colors.size].copy(alpha = 0.4f)
                    ),
                    start = Offset(offsetX - offsetSize, offsetY - offsetSize),
                    end = Offset(offsetX + offsetSize, offsetY + offsetSize)
                )
            )
        }
    }
}

/**
 * Compact version for inline use
 */
@Composable
fun CompactReplitThinking(
    modifier: Modifier = Modifier
) {
    ReplitThinkingAnimation(
        modifier = modifier,
        size = 32
    )
}

/**
 * Large version for welcome screen
 */
@Composable
fun LargeReplitThinking(
    modifier: Modifier = Modifier
) {
    ReplitThinkingAnimation(
        modifier = modifier,
        size = 80
    )
}

/**
 * Full thinking indicator with text
 */
@Composable
fun ReplitThinkingIndicator(
    modifier: Modifier = Modifier,
    text: String = "Thinking"
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ReplitThinkingAnimation(size = 24)
        
        // Animated dots
        val infiniteTransition = rememberInfiniteTransition(label = "dots")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
        
        Text(
            text = text + ".".repeat(phase.toInt().coerceIn(1, 3)),
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
