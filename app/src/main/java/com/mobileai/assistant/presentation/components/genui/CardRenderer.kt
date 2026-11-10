package com.mobileai.assistant.presentation.components.genui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.genui.ButtonVariant
import com.mobileai.assistant.domain.genui.CardStyle
import com.mobileai.assistant.domain.genui.GenUiButton
import com.mobileai.assistant.domain.genui.GenUiCard
import com.mobileai.assistant.domain.genui.GenUiColumn
import com.mobileai.assistant.domain.genui.GenUiComponent
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.genui.GenUiMetric
import com.mobileai.assistant.domain.genui.GenUiRow
import com.mobileai.assistant.domain.genui.TrendDirection

@Composable
fun GenUiCardView(
    card: GenUiCard,
    isStreaming: Boolean,
    isSubmitted: Boolean,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (!card.title.isNullOrBlank()) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!card.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!card.title.isNullOrBlank() || !card.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            card.children.forEachIndexed { index, child ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                GenUiDispatcher(
                    component = child,
                    isStreaming = isStreaming,
                    isSubmitted = isSubmitted,
                    onEvent = onEvent
                )
            }
        }
    }

    when (card.style) {
        CardStyle.ELEVATED -> {
            ElevatedCard(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                content = { content() }
            )
        }
        CardStyle.OUTLINED -> {
            OutlinedCard(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                content = { content() }
            )
        }
        CardStyle.TONAL -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                content = { content() }
            )
        }
    }
}

@Composable
fun GenUiMetricView(
    metric: GenUiMetric,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = metric.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = metric.value,
                    style = if (metric.value.length <= 6) {
                        MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (metric.trend != null || !metric.trendLabel.isNullOrBlank()) {
                    val direction = metric.trendDirection ?: when {
                        metric.trend != null && metric.trend > 0 -> TrendDirection.UP
                        metric.trend != null && metric.trend < 0 -> TrendDirection.DOWN
                        else -> TrendDirection.NEUTRAL
                    }

                    val (badgeBg, badgeFg, icon) = when (direction) {
                        TrendDirection.UP -> Triple(
                            Color(0xFF10B981).copy(alpha = 0.15f),
                            Color(0xFF10B981),
                            Icons.AutoMirrored.Filled.TrendingUp
                        )
                        TrendDirection.DOWN -> Triple(
                            Color(0xFFEF4444).copy(alpha = 0.15f),
                            Color(0xFFEF4444),
                            Icons.AutoMirrored.Filled.TrendingDown
                        )
                        TrendDirection.NEUTRAL -> Triple(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Icons.AutoMirrored.Filled.TrendingFlat
                        )
                    }

                    val trendText = buildString {
                        if (metric.trend != null) {
                            if (metric.trend > 0) append("+")
                            append(metric.trend)
                            append("%")
                        }
                        if (!metric.trendLabel.isNullOrBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(metric.trendLabel)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = trendText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = badgeFg
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GenUiColumnView(
    column: GenUiColumn,
    isStreaming: Boolean,
    isSubmitted: Boolean,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(column.spacing.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        column.children.forEach { child ->
            GenUiDispatcher(
                component = child,
                isStreaming = isStreaming,
                isSubmitted = isSubmitted,
                onEvent = onEvent
            )
        }
    }
}

@Composable
fun GenUiRowView(
    row: GenUiRow,
    isStreaming: Boolean,
    isSubmitted: Boolean,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(row.spacing.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        row.children.forEach { child ->
            Box(modifier = Modifier.weight(1f)) {
                GenUiDispatcher(
                    component = child,
                    isStreaming = isStreaming,
                    isSubmitted = isSubmitted,
                    onEvent = onEvent
                )
            }
        }
    }
}

@Composable
fun GenUiButtonView(
    button: GenUiButton,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val onClick = {
        onEvent(
            GenUiEvent.ButtonClick(
                actionId = button.actionId,
                payload = button.payload,
                componentId = button.id
            )
        )
    }

    when (button.variant) {
        ButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = button.label)
            }
        }
        ButtonVariant.SECONDARY -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = button.label)
            }
        }
        ButtonVariant.TONAL -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = button.label)
            }
        }
        ButtonVariant.DESTRUCTIVE -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = button.label)
            }
        }
    }
}

