package com.mobileai.assistant.presentation.components.genui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.data.genui.GenUiValidationResult
import com.mobileai.assistant.data.genui.GenUiValidator
import com.mobileai.assistant.domain.genui.GenUiButton
import com.mobileai.assistant.domain.genui.GenUiCard
import com.mobileai.assistant.domain.genui.GenUiChart
import com.mobileai.assistant.domain.genui.GenUiColumn
import com.mobileai.assistant.domain.genui.GenUiComponent
import com.mobileai.assistant.domain.genui.GenUiDiagram
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.genui.GenUiFallback
import com.mobileai.assistant.domain.genui.GenUiForm
import com.mobileai.assistant.domain.genui.GenUiMetric
import com.mobileai.assistant.domain.genui.GenUiRow
import com.mobileai.assistant.domain.genui.GenUiTable
import com.mobileai.assistant.domain.genui.GenUiUnsupported

/**
 * Root Composable dispatcher rendering any typed GenUiComponent.
 */
@Composable
fun GenUiDispatcher(
    component: GenUiComponent,
    isStreaming: Boolean,
    isSubmitted: Boolean,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when (component) {
        is GenUiCard -> GenUiCardView(
            card = component,
            isStreaming = isStreaming,
            isSubmitted = isSubmitted,
            onEvent = onEvent,
            modifier = modifier
        )
        is GenUiColumn -> GenUiColumnView(
            column = component,
            isStreaming = isStreaming,
            isSubmitted = isSubmitted,
            onEvent = onEvent,
            modifier = modifier
        )
        is GenUiRow -> GenUiRowView(
            row = component,
            isStreaming = isStreaming,
            isSubmitted = isSubmitted,
            onEvent = onEvent,
            modifier = modifier
        )
        is GenUiMetric -> GenUiMetricView(
            metric = component,
            modifier = modifier
        )
        is GenUiChart -> ChartRenderer(
            chart = component,
            isStreaming = isStreaming,
            modifier = modifier
        )
        is GenUiTable -> TableRenderer(
            table = component,
            modifier = modifier
        )
        is GenUiForm -> FormRenderer(
            form = component,
            isSubmitted = isSubmitted,
            onEvent = onEvent,
            modifier = modifier
        )
        is GenUiDiagram -> DiagramRenderer(
            diagram = component,
            onEvent = onEvent,
            modifier = modifier
        )
        is GenUiButton -> GenUiButtonView(
            button = component,
            onEvent = onEvent,
            modifier = modifier
        )
        is GenUiFallback -> GenUiFallbackView(
            fallback = component,
            modifier = modifier
        )
        is GenUiUnsupported -> GenUiUnsupportedView(
            unsupported = component,
            modifier = modifier
        )
    }
}

/**
 * Public GenUiRenderer entry point taking a typed GenUiComponent.
 */
@Composable
fun GenUiRenderer(
    component: GenUiComponent,
    onEvent: (GenUiEvent) -> Unit,
    isStreaming: Boolean = false,
    isSubmitted: Boolean = false,
    modifier: Modifier = Modifier
) {
    GenUiDispatcher(
        component = component,
        isStreaming = isStreaming,
        isSubmitted = isSubmitted,
        onEvent = onEvent,
        modifier = modifier
    )
}

/**
 * High-level GenUiView that accepts raw fenced JSON payload, validates via 4-tier
 * GenUiValidator, manages progressive streaming shimmer, and falls back safely to code.
 */
@Composable
fun GenUiView(
    rawPayload: String,
    isStreaming: Boolean,
    isSubmitted: Boolean = false,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val validationResult = remember(rawPayload, isStreaming) {
        GenUiValidator.validate(rawPayload, isStreaming)
    }

    when (validationResult) {
        is GenUiValidationResult.Success -> {
            GenUiRenderer(
                component = validationResult.component,
                onEvent = onEvent,
                isStreaming = isStreaming,
                isSubmitted = isSubmitted,
                modifier = modifier
            )
        }
        is GenUiValidationResult.Fallback -> {
            GenUiRenderer(
                component = validationResult.component,
                onEvent = onEvent,
                isStreaming = isStreaming,
                isSubmitted = isSubmitted,
                modifier = modifier
            )
        }
        is GenUiValidationResult.Malformed -> {
            if (isStreaming) {
                // Progressive streaming shimmer placeholder
                StreamingShimmerCard(modifier = modifier)
            } else {
                // Tier 4 Safe Text / Code Degradation
                Tier4DegradationView(
                    rawPayload = rawPayload,
                    errorMessage = validationResult.error,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
fun StreamingShimmerCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rendering dynamic component...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Tier4DegradationView(
    rawPayload: String,
    errorMessage: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Structured component degraded to raw definition",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(8.dp)
            ) {
                Text(
                    text = rawPayload,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun GenUiFallbackView(
    fallback: GenUiFallback,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Unsupported component '${fallback.unsupportedType}'",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (fallback.reason.isNotBlank()) {
                Text(
                    text = fallback.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GenUiUnsupportedView(
    unsupported: GenUiUnsupported,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Unsupported element: ${unsupported.rawType ?: "unknown"}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!unsupported.reason.isNullOrBlank()) {
                Text(
                    text = unsupported.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

