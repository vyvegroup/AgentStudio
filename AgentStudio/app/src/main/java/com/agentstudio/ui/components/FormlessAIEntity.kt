package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Truly formless AI entity - like dark smoke, ethereal mist, formless plasma
 * No discernible shape, constantly morphing, completely amorphous
 */
@Composable
fun FormlessAIEntity(
    modifier: Modifier = Modifier,
    size: Int = 150,
    isThinking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "entity")
    
    // Multiple organic wave phases
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    
    val phase2 by infiniteTransition.animateFloat(
        initialValue = PI.toFloat(),
        targetValue = 3f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0.5f * PI.toFloat(),
        targetValue = 2.5f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )
    
    // Slow breathing
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    
    // Thinking chaos
    val chaos by if (isThinking) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "chaos"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    
    // Slow color cycle
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "colorPhase"
    )
    
    // Ethereal color palette - dark mysterious colors
    val colors = remember {
        listOf(
            Color(0xFF0a0a15),  // Near black
            Color(0xFF12122a),  // Dark purple-black
            Color(0xFF1a1a3e),  // Deep navy
            Color(0xFF2d1b4e),  // Dark purple
            Color(0xFF8B5CF6),  // Soft purple glow
            Color(0xFF6366F1),  // Indigo
            Color(0xFF06B6D4),  // Cyan
        )
    }
    
    Canvas(
        modifier = modifier.size(size.dp)
    ) {
        val center = Offset(size.dp.toPx() / 2, size.dp.toPx() / 2)
        val baseRadius = size.dp.toPx() / 2.5f
        val breathScale = breathe * (if (isThinking) 1f + 0.15f * sin(chaos * 4) else 1f)
        
        // ============================================
        // LAYER 1: Deep outer void - very subtle dark fog
        // ============================================
        for (layer in 12 downTo 1) {
            val fogPath = Path()
            val segments = 90
            val fogRadius = baseRadius * (1.2f + layer * 0.15f) * breathScale
            
            for (i in 0..segments) {
                val angle = 2f * PI * i / segments
                
                // Multi-frequency noise for organic chaos
                val n1 = sin(angle * 2.3f + phase1 * 0.7f + layer * 0.2f)
                val n2 = sin(angle * 3.7f + phase2 * 0.5f + layer * 0.3f)
                val n3 = sin(angle * 5.1f + phase3 * 0.3f + layer * 0.4f)
                val n4 = cos(angle * 1.7f + phase1 * 0.9f + layer * 0.1f)
                
                val noise = (n1 + n2 * 0.7f + n3 * 0.5f + n4 * 0.3f).toFloat() * 
                    (if (isThinking) 0.18f + 0.08f * sin(chaos * 6) else 0.12f)
                
                val r = fogRadius * (1f + noise)
                val x = center.x + cos(angle).toFloat() * r
                val y = center.y + sin(angle).toFloat() * r
                
                if (i == 0) fogPath.moveTo(x, y) else fogPath.lineTo(x, y)
            }
            fogPath.close()
            
            // Extremely subtle fog - almost invisible
            val alpha = 0.025f / (layer * 0.7f)
            val colorIndex = ((colorPhase * colors.size + layer) % colors.size).toInt()
            
            drawPath(
                path = fogPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors[colorIndex].copy(alpha = alpha * 1.5f),
                        colors[(colorIndex + 1) % colors.size].copy(alpha = alpha),
                        Color.Transparent
                    ),
                    center = Offset(
                        center.x + sin(phase1 + layer).toFloat() * baseRadius * 0.03f,
                        center.y + cos(phase2 + layer).toFloat() * baseRadius * 0.03f
                    ),
                    radius = fogRadius * 0.8f
                )
            )
        }
        
        // ============================================
        // LAYER 2: Floating smoke wisps
        // ============================================
        val wispCount = if (isThinking) 15 else 8
        repeat(wispCount) { wisp ->
            val seed = wisp * 73.13f
            val wispPath = Path()
            val segments = 50
            
            // Wisp position orbits slowly
            val orbitAngle = phase1 * (0.2f + wisp * 0.05f) + seed
            val orbitRadius = baseRadius * (0.3f + (wisp % 5) * 0.12f) * breathScale
            
            val wispCenterX = center.x + cos(orbitAngle).toFloat() * orbitRadius
            val wispCenterY = center.y + sin(orbitAngle * 1.3f).toFloat() * orbitRadius
            val wispRadius = baseRadius * (0.15f + 0.1f * sin(phase2 + wisp)).toFloat() * breathScale
            
            for (i in 0..segments) {
                val angle = 2f * PI * i / segments
                
                // Wisp shape noise
                val wn1 = sin(angle * 4f + phase1 + wisp).toFloat()
                val wn2 = sin(angle * 6f + phase2 * 0.7f + wisp * 0.5f).toFloat()
                val wNoise = (wn1 + wn2) * 0.25f
                
                val r = wispRadius * (1f + wNoise)
                val x = wispCenterX + cos(angle).toFloat() * r
                val y = wispCenterY + sin(angle).toFloat() * r
                
                if (i == 0) wispPath.moveTo(x, y) else wispPath.lineTo(x, y)
            }
            wispPath.close()
            
            // Very subtle wisp
            val wispAlpha = (0.04f + 0.02f * sin(phase3 + wisp)) * (if (isThinking) 1.5f else 1f)
            val wispColorIdx = ((wisp + colorPhase * colors.size) % colors.size).toInt()
            
            drawPath(
                path = wispPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors[wispColorIdx].copy(alpha = wispAlpha),
                        colors[(wispColorIdx + 2) % colors.size].copy(alpha = wispAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(wispCenterX, wispCenterY),
                    radius = wispRadius * 1.5f
                )
            )
        }
        
        // ============================================
        // LAYER 3: Core void - dark center with subtle glow
        // ============================================
        val corePath = Path()
        val coreSegments = 70
        val coreRadius = baseRadius * 0.25f * breathScale
        
        for (i in 0..coreSegments) {
            val angle = 2f * PI * i / coreSegments
            
            val cn1 = sin(angle * 5f + phase1 * 1.5f).toFloat()
            val cn2 = sin(angle * 7f + phase2).toFloat()
            val cNoise = (cn1 + cn2) * 0.2f
            
            val r = coreRadius * (1f + cNoise)
            val x = center.x + cos(angle).toFloat() * r
            val y = center.y + sin(angle).toFloat() * r
            
            if (i == 0) corePath.moveTo(x, y) else corePath.lineTo(x, y)
        }
        corePath.close()
        
        // Dark void center
        drawPath(
            path = corePath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF050508).copy(alpha = 0.95f),
                    Color(0xFF0a0a15).copy(alpha = 0.8f),
                    Color(0xFF12122a).copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = center,
                radius = coreRadius * 2.5f
            )
        )
        
        // ============================================
        // LAYER 4: Ambient particles - dust in void
        // ============================================
        val particleCount = if (isThinking) 35 else 18
        repeat(particleCount) { p ->
            val seed = p * 89.17f
            val pAngle = phase1 * (0.15f + p * 0.03f) + seed
            val pRadius = baseRadius * (0.2f + (p % 7) * 0.11f) * breathScale
            
            val px = center.x + cos(pAngle).toFloat() * pRadius
            val py = center.y + sin(pAngle * 1.4f + phase2 * 0.3f).toFloat() * pRadius
            
            // Particle size varies
            val pSize = (0.8f + 0.6f * sin(phase3 + p)).toFloat() * (if (isThinking) 1.3f else 1f)
            val pAlpha = (0.15f + 0.1f * sin(phase1 * 2f + p)) * (if (isThinking) 0.7f else 0.5f)
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = pAlpha),
                        colors[p % colors.size].copy(alpha = pAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(px, py),
                    radius = pSize * 3.dp.toPx()
                ),
                radius = pSize * 3.dp.toPx(),
                center = Offset(px, py)
            )
        }
        
        // ============================================
        // LAYER 5: Thinking energy - soft pulsing glow
        // ============================================
        if (isThinking) {
            // Subtle energy pulses
            repeat(3) { pulse ->
                val pulseAngle = chaos * 1.5f + pulse * PI.toFloat() * 2f / 3f
                val pulseRadius = baseRadius * (0.4f + 0.3f * sin(chaos * 2f + pulse))
                val px = center.x + cos(pulseAngle).toFloat() * pulseRadius
                val py = center.y + sin(pulseAngle).toFloat() * pulseRadius
                
                val pulseAlpha = 0.08f + 0.05f * sin(chaos * 4f + pulse)
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = pulseAlpha),
                            Color(0xFF06B6D4).copy(alpha = pulseAlpha * 0.5f),
                            Color.Transparent
                        ),
                        center = Offset(px, py),
                        radius = baseRadius * 0.25f
                    ),
                    radius = baseRadius * 0.25f,
                    center = Offset(px, py)
                )
            }
            
            // Subtle rim glow
            val rimAlpha = 0.03f + 0.02f * sin(chaos * 3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF8B5CF6).copy(alpha = rimAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.3f
                ),
                radius = baseRadius * 1.3f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // ============================================
        // LAYER 6: Overall ambient glow
        // ============================================
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF8B5CF6).copy(alpha = 0.02f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 1.6f
            ),
            radius = baseRadius * 1.6f,
            center = center
        )
    }
}

@Composable
fun CompactFormlessEntity(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false
) {
    FormlessAIEntity(
        modifier = modifier,
        size = 40,
        isThinking = isThinking
    )
}

@Composable
fun LargeFormlessEntity(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false
) {
    FormlessAIEntity(
        modifier = modifier,
        size = 200,
        isThinking = isThinking
    )
}
