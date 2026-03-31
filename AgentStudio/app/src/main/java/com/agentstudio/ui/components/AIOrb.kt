package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/**
 * Siri-like ethereal AI Orb with fluid animations
 */
@Composable
fun AIOrb(
    modifier: Modifier = Modifier,
    size: Int = 120,
    isAnimating: Boolean = true,
    isThinking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_animation")
    
    // Main fluid rotation
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation1"
    )
    
    // Counter rotation for inner layers
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation2"
    )
    
    // Breathing/pulsing effect
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    
    // Wave distortion
    val wave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    
    // Thinking intensity
    val thinkingIntensity by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking"
    )
    
    // Color shimmer
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "colorShift"
    )
    
    val colors = remember {
        listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF8B5CF6), // Purple
            Color(0xFF06B6D4), // Cyan
            Color(0xFF10B981), // Green
            Color(0xFFEC4899), // Pink
            Color(0xFF6366F1)  // Back to indigo
        )
    }
    
    val thinkingColor = Color(0xFF06B6D4) // Cyan for thinking state
    
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val center = Offset(size.dp.toPx() / 2, size.dp.toPx() / 2)
        val baseRadius = (size.dp.toPx() / 2.5f) * breathe
        val intensity = if (isThinking) thinkingIntensity else 1f
        
        // Outer glow - ethereal haze
        for (i in 5 downTo 1) {
            val glowRadius = baseRadius * (1f + i * 0.15f)
            val alpha = (0.15f / i) * intensity
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors[(colorShift * colors.size).toInt() % colors.size].copy(alpha = alpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )
        }
        
        // Main fluid body with gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.3f * intensity),
                    colors[(colorShift * colors.size).toInt() % colors.size].copy(alpha = 0.4f * intensity),
                    colors[((colorShift * colors.size).toInt() + 2) % colors.size].copy(alpha = 0.2f * intensity),
                    Color.Transparent
                ),
                center = Offset(
                    center.x - baseRadius * 0.2f,
                    center.y - baseRadius * 0.2f
                ),
                radius = baseRadius * 1.2f
            ),
            radius = baseRadius,
            center = center
        )
        
        // Rotating ethereal rings
        rotate(rotation1, center) {
            // Outer ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = colors,
                    center = center
                ),
                radius = baseRadius * 0.9f,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        
        rotate(rotation2, center) {
            // Middle ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = colors.reversed(),
                    center = center
                ),
                radius = baseRadius * 0.7f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        rotate(rotation1 * 1.5f, center) {
            // Inner ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = colors.map { it.copy(alpha = 0.6f) },
                    center = center
                ),
                radius = baseRadius * 0.45f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        
        // Fluid wave layers (ethereal feel)
        repeat(3) { layer ->
            val layerRadius = baseRadius * (0.3f + layer * 0.2f)
            val path = Path()
            val segments = 60
            
            for (i in 0..segments) {
                val angle = 2 * PI * i / segments
                val waveOffset = sin(angle * (3 + layer) + wave + layer).toFloat() * baseRadius * 0.05f * intensity
                val r = layerRadius + waveOffset
                val x = center.x + cos(angle).toFloat() * r
                val y = center.y + sin(angle).toFloat() * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            
            drawPath(
                path = path,
                color = colors[(layer + (colorShift * colors.size).toInt()) % colors.size].copy(alpha = 0.3f * intensity),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        
        // Core - inner glow
        val coreRadius = baseRadius * 0.2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color.White.copy(alpha = 0.5f),
                    colors.first().copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = center,
                radius = coreRadius * 2
            ),
            radius = coreRadius * 2,
            center = center
        )
        
        // Orbiting particles
        if (isAnimating) {
            val particleCount = 8
            for (i in 0 until particleCount) {
                val particleAngle = (2 * PI * i / particleCount) + (rotation1 * PI / 180 * 0.3f)
                val particleRadius = baseRadius * (0.85f + 0.1f * sin(wave + i).toFloat())
                val px = center.x + cos(particleAngle).toFloat() * particleRadius
                val py = center.y + sin(particleAngle).toFloat() * particleRadius
                val particleAlpha = (0.3f + 0.3f * sin(wave + i * 0.5f).toFloat()) * intensity
                
                drawCircle(
                    color = colors[i % colors.size].copy(alpha = particleAlpha),
                    radius = 2.dp.toPx() + 1.dp.toPx() * sin(wave + i).toFloat(),
                    center = Offset(px, py)
                )
            }
        }
        
        // Thinking pulse rings
        if (isThinking) {
            val pulseRadius = baseRadius * (0.5f + thinkingIntensity * 0.5f)
            drawCircle(
                color = thinkingColor.copy(alpha = 0.3f * (2f - thinkingIntensity)),
                radius = pulseRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
fun CompactAIOrb(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false
) {
    AIOrb(
        modifier = modifier,
        size = 36,
        isThinking = isThinking
    )
}

@Composable
fun LargeAIOrb(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false
) {
    AIOrb(
        modifier = modifier,
        size = 150,
        isThinking = isThinking
    )
}
