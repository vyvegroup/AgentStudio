package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Replit-style animated AI logo with neural network animation
 */
@Composable
fun AnimatedAILogo(
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_animation")
    
    // Main rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Pulse animation for glow effect
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Inner ring rotation (opposite direction)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )
    
    // Neural pulse animation
    val neuralPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neural_pulse"
    )
    
    // Gradient colors
    val gradientColors = listOf(
        Color(0xFF6366F1),  // Primary indigo
        Color(0xFF8B5CF6),  // Purple
        Color(0xFF06B6D4),  // Cyan
        Color(0xFF10B981),  // Green
        Color(0xFF6366F1)   // Back to indigo
    )
    
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val center = Offset(size.dp.toPx() / 2, size.dp.toPx() / 2)
        val baseRadius = size.dp.toPx() / 2.5f
        
        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6366F1).copy(alpha = 0.3f * pulse),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 1.8f
            ),
            radius = baseRadius * 1.8f,
            center = center
        )
        
        // Outer rotating ring
        rotate(rotation, center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = gradientColors,
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                size = Size(baseRadius * 2, baseRadius * 2),
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius)
            )
        }
        
        // Second ring (slightly smaller, different rotation)
        val secondRadius = baseRadius * 0.8f
        rotate(innerRotation, center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = gradientColors.reversed(),
                    center = center
                ),
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                size = Size(secondRadius * 2, secondRadius * 2),
                topLeft = Offset(center.x - secondRadius, center.y - secondRadius)
            )
        }
        
        // Neural network nodes
        val nodeCount = 6
        val nodeRadius = baseRadius * 0.35f
        val nodeSize = 4.dp.toPx()
        
        for (i in 0 until nodeCount) {
            val angle = (2 * PI * i / nodeCount) + (rotation * PI / 180 * 0.5)
            val nodeX = center.x + cos(angle).toFloat() * nodeRadius
            val nodeY = center.y + sin(angle).toFloat() * nodeRadius
            
            // Node glow
            drawCircle(
                color = gradientColors[i % gradientColors.size].copy(alpha = 0.5f * pulse),
                radius = nodeSize * 2,
                center = Offset(nodeX, nodeY)
            )
            
            // Node
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        gradientColors[i % gradientColors.size]
                    ),
                    center = Offset(nodeX, nodeY),
                    radius = nodeSize
                ),
                radius = nodeSize,
                center = Offset(nodeX, nodeY)
            )
            
            // Connection lines between nodes
            val nextI = (i + 1) % nodeCount
            val nextAngle = (2 * PI * nextI / nodeCount) + (rotation * PI / 180 * 0.5)
            val nextX = center.x + cos(nextAngle).toFloat() * nodeRadius
            val nextY = center.y + sin(nextAngle).toFloat() * nodeRadius
            
            // Neural pulse traveling along connection
            val pulseProgress = (neuralPulse + i * 0.1f) % 1f
            val pulseX = nodeX + (nextX - nodeX) * pulseProgress
            val pulseY = nodeY + (nextY - nodeY) * pulseProgress
            
            // Draw connection line
            drawLine(
                color = gradientColors[i % gradientColors.size].copy(alpha = 0.3f),
                start = Offset(nodeX, nodeY),
                end = Offset(nextX, nextY),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw traveling pulse
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 2.dp.toPx(),
                center = Offset(pulseX, pulseY)
            )
        }
        
        // Central AI core
        val coreRadius = baseRadius * 0.2f
        
        // Core glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.9f),
                    Color(0xFF6366F1).copy(alpha = 0.6f),
                    Color.Transparent
                ),
                center = center,
                radius = coreRadius * 2
            ),
            radius = coreRadius * 2,
            center = center
        )
        
        // Core with gradient
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.White,
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6),
                    Color.White
                ),
                center = center
            ),
            radius = coreRadius,
            center = center
        )
        
        // Inner core highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.5f * pulse),
            radius = coreRadius * 0.4f,
            center = center
        )
        
        // Orbiting particles
        val particleCount = 3
        for (i in 0 until particleCount) {
            val particleAngle = (2 * PI * i / particleCount) + (rotation * 2 * PI / 180)
            val particleRadius = baseRadius * 1.2f
            val particleX = center.x + cos(particleAngle).toFloat() * particleRadius
            val particleY = center.y + sin(particleAngle).toFloat() * particleRadius
            
            drawCircle(
                color = gradientColors[i % gradientColors.size].copy(alpha = 0.6f),
                radius = 2.dp.toPx(),
                center = Offset(particleX, particleY)
            )
        }
    }
}

/**
 * Compact animated logo for chat messages
 */
@Composable
fun CompactAnimatedLogo(
    modifier: Modifier = Modifier
) {
    AnimatedAILogo(
        modifier = modifier,
        size = 32
    )
}

/**
 * Large animated logo for splash/loading screens
 */
@Composable
fun LargeAnimatedLogo(
    modifier: Modifier = Modifier
) {
    AnimatedAILogo(
        modifier = modifier,
        size = 120
    )
}
