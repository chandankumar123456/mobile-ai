package com.mobileai.assistant.presentation.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ReasoningSupport
import com.mobileai.assistant.domain.model.ThinkingEffort
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ModalBottomSheet allowing capability-driven fine-tuning of runtime parameters for the active model/conversation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsSheet(
    params: GenerationParams,
    capabilities: ModelCapabilities,
    isLocalModel: Boolean,
    onSaveParams: (GenerationParams) -> Unit,
    onReloadModel: (() -> Unit)? = null,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var temperature by remember(params) { mutableFloatStateOf(params.temperature.toFloat()) }
    var topP by remember(params) { mutableFloatStateOf(params.topP.toFloat()) }
    var topK by remember(params) { mutableIntStateOf(params.topK) }
    var minP by remember(params) { mutableFloatStateOf(params.minP.toFloat()) }
    var repeatPenalty by remember(params) { mutableFloatStateOf(params.repeatPenalty.toFloat()) }
    var maxTokens by remember(params) { mutableIntStateOf(params.maxTokens) }
    var contextSize by remember(params) { mutableIntStateOf(params.contextSize) }
    var threads by remember(params) { mutableIntStateOf(params.threads) }
    var thinkingEnabled by remember(params) { mutableStateOf(params.thinkingEnabled) }
    var thinkingEffort by remember(params) { mutableStateOf(params.thinkingEffort) }

    val reloadRequired = isLocalModel && (contextSize != params.contextSize || threads != params.threads)

    fun buildCurrentParams(): GenerationParams = params.copy(
        temperature = (temperature * 100).roundToInt() / 100.0,
        topP = (topP * 100).roundToInt() / 100.0,
        topK = topK,
        minP = (minP * 100).roundToInt() / 100.0,
        repeatPenalty = (repeatPenalty * 100).roundToInt() / 100.0,
        maxTokens = maxTokens,
        contextSize = contextSize,
        threads = threads,
        thinkingEnabled = thinkingEnabled,
        thinkingEffort = thinkingEffort
    ).clamp()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Runtime Parameters",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Fine-tune inference parameters for this session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reload Required Warning Banner
            if (reloadRequired) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reload Required",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Changing context size or threads requires reloading the local model into memory.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }
                        if (onReloadModel != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSaveParams(buildCurrentParams())
                                    onReloadModel()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reload", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Reasoning Controls (if supported by active model capabilities)
            if (capabilities.reasoning != ReasoningSupport.NONE) {
                Text(
                    text = "REASONING & THINKING",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Reasoning",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Model generates chain-of-thought before the final answer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { thinkingEnabled = it }
                    )
                }

                if (thinkingEnabled && capabilities.reasoning == ReasoningSupport.EFFORT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thinking Effort",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThinkingEffort.entries.forEach { effort ->
                            FilterChip(
                                selected = thinkingEffort == effort,
                                onClick = { thinkingEffort = effort },
                                label = { Text(effort.displayName, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Sampling Controls
            Text(
                text = "SAMPLING PARAMETERS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Temperature Slider
            SliderParameterRow(
                title = "Temperature",
                valueText = String.format(Locale.US, "%.2f", temperature),
                description = "Higher values increase creativity, lower values make responses more deterministic.",
                value = temperature,
                valueRange = 0.0f..2.0f,
                onValueChange = { temperature = it }
            )

            // Top-P Slider
            SliderParameterRow(
                title = "Top-P (Nucleus Sampling)",
                valueText = String.format(Locale.US, "%.2f", topP),
                description = "Only sample from tokens comprising the top P cumulative probability mass.",
                value = topP,
                valueRange = 0.0f..1.0f,
                onValueChange = { topP = it }
            )

            // Top-K Slider
            SliderParameterRow(
                title = "Top-K",
                valueText = "$topK",
                description = "Limit next-token selection to the K highest scoring tokens (0 to disable).",
                value = topK.toFloat(),
                valueRange = 0f..200f,
                steps = 199,
                onValueChange = { topK = it.roundToInt() }
            )

            // Min-P Slider
            SliderParameterRow(
                title = "Min-P",
                valueText = String.format(Locale.US, "%.2f", minP),
                description = "Exclude tokens with probability below min_p * max_probability.",
                value = minP,
                valueRange = 0.0f..1.0f,
                onValueChange = { minP = it }
            )

            // Repeat Penalty Slider
            SliderParameterRow(
                title = "Repeat Penalty",
                valueText = String.format(Locale.US, "%.2f", repeatPenalty),
                description = "Penalize repetitive token generation (1.0 = disabled).",
                value = repeatPenalty,
                valueRange = 0.5f..2.0f,
                onValueChange = { repeatPenalty = it }
            )

            // Max Tokens Slider
            SliderParameterRow(
                title = "Max Tokens",
                valueText = "$maxTokens",
                description = "Maximum output tokens per generation request.",
                value = maxTokens.toFloat(),
                valueRange = 64f..8192f,
                onValueChange = { maxTokens = it.roundToInt() }
            )

            // Local Engine Native Parameters (Context Size & Threads)
            if (isLocalModel || capabilities.nativeContext || capabilities.nativeThreads) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "LOCAL ENGINE RUNTIME",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Context Size Slider
                val maxContext = (capabilities.contextLength ?: 8192).coerceAtLeast(4096).toFloat()
                SliderParameterRow(
                    title = "Context Size",
                    valueText = "$contextSize tokens",
                    description = "Native context window size allocated in RAM. Changing requires reload.",
                    value = contextSize.toFloat(),
                    valueRange = 512f..maxContext,
                    steps = ((maxContext - 512f) / 512f).roundToInt().coerceAtLeast(1) - 1,
                    onValueChange = { contextSize = ((it / 512f).roundToInt() * 512).coerceAtLeast(512) }
                )

                // Threads Slider
                val availableCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 16)
                SliderParameterRow(
                    title = "CPU Threads",
                    valueText = "$threads threads",
                    description = "Number of CPU threads for inference. Available cores: $availableCores.",
                    value = threads.toFloat(),
                    valueRange = 1f..availableCores.toFloat(),
                    steps = (availableCores - 2).coerceAtLeast(0),
                    onValueChange = { threads = it.roundToInt().coerceIn(1, availableCores) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Defaults")
                }

                Button(
                    onClick = {
                        onSaveParams(buildCurrentParams())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SliderParameterRow(
    title: String,
    valueText: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
