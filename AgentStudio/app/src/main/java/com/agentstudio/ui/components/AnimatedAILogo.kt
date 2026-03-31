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
    
    // Gradient colors
    val gradientColors = remember {
        listOf(
            Color(0xFF6366F1),
            Color(0xFF8B5CF6),
            Color(0xFF06B6D4),
            Color(0xFF10B981),
            Color(0xFF6366F1)
        )
    }
    
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
        
        // Second ring
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
        
        // Central AI core
        val coreRadius = baseRadius * 0.25f
        
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
    }
}

@Composable
fun CompactAnimatedLogo(
    modifier: Modifier = Modifier
) {
    AnimatedAILogo(
        modifier = modifier,
        size = 32
    )
}

@Composable
fun LargeAnimatedLogo(
    modifier: Modifier = Modifier
) {
    AnimatedAILogo(
        modifier = modifier,
        size = 120
    )
}
