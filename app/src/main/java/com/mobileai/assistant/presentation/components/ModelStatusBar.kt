package com.mobileai.assistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.model.RuntimeMetrics
import com.mobileai.assistant.presentation.theme.StatusError
import com.mobileai.assistant.presentation.theme.StatusSuccess
import com.mobileai.assistant.presentation.theme.StatusWarning
import java.util.Locale

@Composable
fun ModelStatusBar(
    engineState: EngineState,
    loadedModel: ModelInfo?,
    activeEndpoint: RemoteEndpointConfig? = null,
    activeTarget: InferenceTarget? = null,
    runtimeMetrics: RuntimeMetrics? = null,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRemote = activeTarget is InferenceTarget.Remote || (activeEndpoint != null && loadedModel == null)
    val hasTarget = loadedModel != null || (activeEndpoint != null && activeEndpoint.isConfigured)

    val targetTitle = when {
        activeTarget is InferenceTarget.Local -> "Local: ${activeTarget.model.displayName}"
        activeTarget is InferenceTarget.Remote -> "Remote: ${activeTarget.endpoint.name} (${activeTarget.modelId})"
        loadedModel != null -> "Local: ${loadedModel.displayName}"
        activeEndpoint != null -> "Remote: ${activeEndpoint.name} (${activeEndpoint.selectedModel})"
        else -> null
    }

    val (statusColor, statusText, isBusy) = when (engineState) {
        is EngineState.Uninitialized -> Triple(StatusWarning, "Engine uninitialized", false)
        is EngineState.Initializing -> Triple(StatusWarning, "Loading native engine...", true)
        is EngineState.Ready -> {
            if (hasTarget && targetTitle != null) {
                Triple(StatusSuccess, "$targetTitle • Ready", false)
            } else {
                Triple(StatusWarning, "No model loaded • Tap to select", false)
            }
        }
        is EngineState.LoadingModel -> Triple(StatusWarning, "Loading ${engineState.modelName}...", true)
        is EngineState.ModelReady -> Triple(StatusSuccess, "$targetTitle • Ready", false)
        is EngineState.Generating -> Triple(StatusSuccess, "Generating response...", true)
        is EngineState.Unloading -> Triple(StatusWarning, "Unloading model...", true)
        is EngineState.Error -> Triple(StatusError, "Engine error: ${engineState.message}", false)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onBarClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 2.dp,
                            color = statusColor
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Metrics & Target Type Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Live or Last Generation Speed
                    val tps = runtimeMetrics?.tokensPerSecond
                    if (tps != null && tps > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f t/s", tps),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Target Type Badge
                    if (hasTarget) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isRemote) Icons.Default.Cloud else Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isRemote) "REMOTE" else (loadedModel?.quantization ?: "LOCAL"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Context Gauge (if context size is known)
            if (runtimeMetrics != null && runtimeMetrics.contextSize > 0 && runtimeMetrics.contextUsed > 0) {
                val fraction = (runtimeMetrics.contextUsed.toFloat() / runtimeMetrics.contextSize.toFloat()).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (fraction > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ctx ${runtimeMetrics.contextUsed}/${runtimeMetrics.contextSize}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
