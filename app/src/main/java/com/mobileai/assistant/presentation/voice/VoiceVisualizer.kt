package com.mobileai.assistant.presentation.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobileai.assistant.domain.model.VoiceState

/**
 * Animated glowing orb visualizer responding dynamically to voice states and audio levels.
 */
@Composable
fun VoiceVisualizer(
    voiceState: VoiceState,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    // Continuous subtle breathing animation
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Fast rotation or ripple for generating / processing
    val rotationPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "generating_pulse"
    )

    // Dynamic RMS animation when listening
    val rmsScale = remember { Animatable(1f) }
    LaunchedEffect(voiceState) {
        if (voiceState is VoiceState.Listening) {
            val target = 1f + (voiceState.rmsDb / 10f) * 0.45f
            rmsScale.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 100)
            )
        } else {
            rmsScale.snapTo(1f)
        }
    }

    val (orbColors, centerIcon, activeScale) = when (voiceState) {
        is VoiceState.Listening -> Triple(
            listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1)),
            Icons.Default.Mic,
            rmsScale.value
        )
        is VoiceState.Processing -> Triple(
            listOf(Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFFB45309)),
            Icons.Default.Mic,
            rotationPulse
        )
        is VoiceState.Generating -> Triple(
            listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF5B21B6)),
            Icons.Default.Psychology,
            rotationPulse
        )
        is VoiceState.Speaking -> Triple(
            listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF047857)),
            Icons.AutoMirrored.Filled.VolumeUp,
            breathingScale * 1.1f
        )
        is VoiceState.Paused -> Triple(
            listOf(Color(0xFF94A3B8), Color(0xFF64748B), Color(0xFF475569)),
            Icons.Default.MicOff,
            1f
        )
        else -> Triple(
            listOf(Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFF3730A3)),
            Icons.Default.Mic,
            breathingScale
        )
    }

    Box(
        modifier = modifier
            .size(200.dp)
            .clickable(onClick = onOrbClick),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing halo
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(activeScale * 1.08f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(orbColors[0].copy(alpha = 0.35f), Color.Transparent)
                    )
                )
        )

        // Middle aura ring
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(activeScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(orbColors[0].copy(alpha = 0.6f), orbColors[1].copy(alpha = 0.2f))
                    )
                )
        )

        // Core solid orb
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(activeScale.coerceIn(0.9f, 1.3f))
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(orbColors[0], orbColors[1])
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = centerIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}
