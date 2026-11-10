package com.mobileai.assistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.model.RuntimeMetrics
import java.util.Locale

/**
 * Compact, subtle Material 3 row/chips displaying runtime inference metrics
 * such as TTFT, generation speed, token count, context usage, and memory.
 */
@Composable
fun RuntimeMetricsRow(
    metrics: RuntimeMetrics,
    modifier: Modifier = Modifier
) {
    val items = mutableListOf<MetricItem>()

    // TTFT
    metrics.timeToFirstTokenMs?.takeIf { it > 0 }?.let { ttft ->
        items.add(MetricItem(Icons.Default.Timer, "TTFT: ${formatDurationMs(ttft)}"))
    }

    // Speed (tokens per second)
    metrics.tokensPerSecond?.takeIf { it > 0.0 }?.let { tps ->
        items.add(MetricItem(Icons.Default.Speed, String.format(Locale.US, "%.1f t/s", tps)))
    }

    // Token count
    val tokenCount = when {
        metrics.completionTokens > 0 -> metrics.completionTokens
        metrics.totalTokens > 0 -> metrics.totalTokens
        else -> 0
    }
    if (tokenCount > 0) {
        items.add(MetricItem(Icons.Default.Numbers, "$tokenCount tokens"))
    }

    // Context used
    if (metrics.contextSize > 0 && metrics.contextUsed > 0) {
        items.add(
            MetricItem(
                Icons.Default.DataUsage,
                "Ctx: ${metrics.contextUsed}/${metrics.contextSize}"
            )
        )
    }

    // Memory (Native or Java heap)
    val totalMemory = (metrics.nativeMemoryBytes ?: 0L) + (metrics.javaHeapBytes ?: 0L)
    if (totalMemory > 0) {
        items.add(MetricItem(Icons.Default.Memory, "Mem: ${formatBytes(totalMemory)}"))
    } else if ((metrics.nativeMemoryBytes ?: 0L) > 0) {
        items.add(MetricItem(Icons.Default.Memory, "RAM: ${formatBytes(metrics.nativeMemoryBytes!!)}"))
    }

    if (items.isEmpty()) return

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            MetricChip(item = item)
        }
    }
}

@Composable
private fun MetricChip(
    item: MetricItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

private data class MetricItem(
    val icon: ImageVector,
    val label: String
)

private fun formatDurationMs(ms: Long): String {
    return if (ms < 1000) {
        "${ms}ms"
    } else {
        String.format(Locale.US, "%.1fs", ms / 1000.0)
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }
}
