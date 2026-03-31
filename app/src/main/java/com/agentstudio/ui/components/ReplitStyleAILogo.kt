package com.agentstudio.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

/**
 * Replit-style AI Logo Animation
 * Features: Glowing orb, orbiting particles, pulsating effects, gradient colors
 */
@Composable
fun ReplitStyleAILogo(
    modifier: Modifier = Modifier,
    size: Int = 48,
    isThinking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "replit_logo")
    
    // Main rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Core pulse
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )
    
    // Glow intensity
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Orbit speed (faster when thinking)
    val orbitSpeed = if (isThinking) 8000 else 15000
    val orbit1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(orbitSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit1"
    )
    
    val orbit2 by infiniteTransition.animateFloat(
        initialValue = 120f,
        targetValue = 480f,
        animationSpec = infiniteRepeatable(
            animation = (orbitSpeed * 1.3f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit2"
    )
    
    val orbit3 by infiniteTransition.animateFloat(
        initialValue = 240f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = (orbitSpeed * 0.8f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit3"
    )
    
    // Color phase
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "color_phase"
    )
    
    // Particles
    val particles = remember { generateParticles(8) }
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )
    
    // Sparkle
    val sparkle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle"
    )
    
    Box(modifier = modifier.size(size.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size.dp.toPx() / 2
            val baseRadius = size.dp.toPx() / 3.5f
            
            // Outer glow layers
            for (i in 5 downTo 1) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = glowIntensity * 0.15f / i),
                            Color(0xFF06B6D4).copy(alpha = glowIntensity * 0.1f / i),
                            Color.Transparent
                        ),
                        center = Offset(center, center),
                        radius = baseRadius * (1 + i * 0.5f)
                    ),
                    radius = baseRadius * (1 + i * 0.5f),
                    center = Offset(center, center)
                )
            }
            
            // Orbiting particles
            val orbitRadius = baseRadius * 1.8f
            listOf(orbit1, orbit2, orbit3).forEachIndexed { index, angle ->
                val x = center + cos(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius
                val y = center + sin(Math.toRadians(angle.toDouble())).toFloat() * orbitRadius
                
                // Particle glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            when (index) {
                                0 -> Color(0xFFA855F7)
                                1 -> Color(0xFF06B6D4)
                                else -> Color(0xFFEC4899)
                            },
                            Color.Transparent
                        ),
                        center = Offset(x, y),
                        radius = baseRadius * 0.3f
                    ),
                    radius = baseRadius * 0.3f,
                    center = Offset(x, y)
                )
                
                // Particle core
                drawCircle(
                    color = when (index) {
                        0 -> Color(0xFFC084FC)
                        1 -> Color(0xFF22D3EE)
                        else -> Color(0xFFF472B6)
                    },
                    radius = baseRadius * 0.08f,
                    center = Offset(x, y)
                )
            }
            
            // Background floating particles
            particles.forEach { particle ->
                val px = center + cos(Math.toRadians((particle.angle + particleTime * particle.speed).toDouble())).toFloat() * baseRadius * particle.radius
                val py = center + sin(Math.toRadians((particle.angle + particleTime * particle.speed).toDouble())).toFloat() * baseRadius * particle.radius
                
                drawCircle(
                    color = Color(0xFF818CF8).copy(alpha = particle.alpha),
                    radius = 2.dp.toPx() * particle.size,
                    center = Offset(px, py)
                )
            }
            
            // Main orb - multiple layers
            rotate(rotation, Offset(center, center)) {
                // Outer ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF8B5CF6).copy(alpha = 0.3f),
                            Color(0xFF06B6D4).copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(center, center),
                        radius = baseRadius * corePulse
                    ),
                    radius = baseRadius * corePulse,
                    center = Offset(center, center)
                )
                
                // Middle gradient orb
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6),
                            Color(0xFF06B6D4),
                            Color(0xFFEC4899),
                            Color(0xFF8B5CF6)
                        ),
                        center = Offset(center, center),
                        angleOffset = colorPhase
                    ),
                    radius = baseRadius * 0.7f * corePulse,
                    center = Offset(center, center)
                )
                
                // Inner core glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFDDD6FE),
                            Color(0xFF8B5CF6),
                            Color(0xFF6D28D9).copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        center = Offset(center, center),
                        radius = baseRadius * 0.5f * corePulse
                    ),
                    radius = baseRadius * 0.5f * corePulse,
                    center = Offset(center, center)
                )
            }
            
            // Sparkle effect
            if (sparkle < 0.5f) {
                val sparkleAlpha = sparkle * 2f
                val sparkleAngle = sparkle * 360f
                val sx = center + cos(Math.toRadians(sparkleAngle.toDouble())).toFloat() * baseRadius * 0.5f
                val sy = center + sin(Math.toRadians(sparkleAngle.toDouble())).toFloat() * baseRadius * 0.5f
                
                drawCircle(
                    color = Color.White.copy(alpha = sparkleAlpha * 0.8f),
                    radius = 4.dp.toPx(),
                    center = Offset(sx, sy)
                )
            }
            
            // Central "eye" / AI core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFC4B5FD).copy(alpha = 0.8f),
                        Color(0xFF8B5CF6).copy(alpha = 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(center, center),
                    radius = baseRadius * 0.25f
                ),
                radius = baseRadius * 0.25f * (if (isThinking) 0.7f + sin(particleTime * 0.01f) * 0.3f else corePulse * 0.8f),
                center = Offset(center, center)
            )
            
            // Innermost bright point
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = baseRadius * 0.08f,
                center = Offset(center, center)
            )
        }
    }
}

private data class ParticleData(
    val angle: Float,
    val radius: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float
)

private fun generateParticles(count: Int): List<ParticleData> {
    return (0 until count).map { i ->
        ParticleData(
            angle = (i.toFloat() / count) * 360f,
            radius = 1.2f + Random.nextFloat() * 0.6f,
            speed = 0.0003f + Random.nextFloat() * 0.0005f,
            size = 0.5f + Random.nextFloat() * 1f,
            alpha = 0.3f + Random.nextFloat() * 0.4f
        )
    }
}

private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
