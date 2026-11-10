package com.mobileai.assistant.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.data.voice.VoiceInfo
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.ThinkingEffort
import com.mobileai.assistant.domain.model.VoiceSettings
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val voiceSettings = uiState.voiceSettings
    val decimal2 = remember { DecimalFormat("#.##") }
    val decimal1 = remember { DecimalFormat("#.#") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetToDefaults) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Defaults"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                SettingsTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            when (tab) {
                                SettingsTab.GENERAL -> Icon(Icons.Default.Tune, contentDescription = null)
                                SettingsTab.SAMPLING -> Icon(Icons.Default.Science, contentDescription = null)
                                SettingsTab.RUNTIME -> Icon(Icons.Default.Memory, contentDescription = null)
                                SettingsTab.VOICE -> Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                SettingsTab.STORAGE -> Icon(Icons.Default.Storage, contentDescription = null)
                            }
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                when (uiState.selectedTab) {
                    SettingsTab.GENERAL -> {
                        item {
                            LanguageSelectionCard(
                                currentLanguage = settings.responseLanguage,
                                onLanguageSelected = viewModel::updateResponseLanguage
                            )
                        }

                        item {
                            ThinkingSettingsCard(
                                thinkingEnabled = settings.thinkingEnabled,
                                thinkingEffort = settings.thinkingEffort,
                                onThinkingToggled = viewModel::updateThinkingEnabled,
                                onEffortSelected = viewModel::updateThinkingEffort
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Default System Instruction",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Prompt injected at the beginning of chat sessions for models supporting system instructions.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = settings.systemPrompt,
                                        onValueChange = viewModel::updateSystemPrompt,
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 6,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    SettingsTab.SAMPLING -> {
                        // Temperature
                        item {
                            SettingSliderCard(
                                title = "Temperature: ${decimal2.format(settings.temperature)}",
                                description = "Controls randomness: lower values are deterministic and focused; higher values increase creative variation.",
                                value = settings.temperature,
                                valueRange = InferenceSettings.MIN_TEMPERATURE..InferenceSettings.MAX_TEMPERATURE,
                                steps = 20,
                                onValueChange = viewModel::updateTemperature
                            )
                        }

                        // Top-P
                        item {
                            SettingSliderCard(
                                title = "Top-P (Nucleus): ${decimal2.format(settings.topP)}",
                                description = "Limits candidate tokens to those forming the cumulative probability threshold P.",
                                value = settings.topP,
                                valueRange = InferenceSettings.MIN_TOP_P..InferenceSettings.MAX_TOP_P,
                                steps = 19,
                                onValueChange = viewModel::updateTopP
                            )
                        }

                        // Top-K
                        item {
                            SettingSliderCard(
                                title = "Top-K: ${settings.topK}",
                                description = "Limits candidate tokens to the K most likely tokens before sampling.",
                                value = settings.topK.toFloat(),
                                valueRange = InferenceSettings.MIN_TOP_K.toFloat()..InferenceSettings.MAX_TOP_K.toFloat(),
                                steps = 99,
                                onValueChange = { viewModel.updateTopK(it.toInt()) }
                            )
                        }

                        // Min-P
                        item {
                            SettingSliderCard(
                                title = "Min-P: ${decimal2.format(settings.minP)}",
                                description = "Minimum probability threshold relative to the most likely token. Filters out improbable tail tokens.",
                                value = settings.minP.toFloat(),
                                valueRange = InferenceSettings.MIN_MIN_P.toFloat()..InferenceSettings.MAX_MIN_P.toFloat(),
                                steps = 20,
                                onValueChange = { viewModel.updateMinP(it.toDouble()) }
                            )
                        }

                        // Repeat Penalty
                        item {
                            SettingSliderCard(
                                title = "Repeat Penalty: ${decimal2.format(settings.repeatPenalty)}",
                                description = "Penalizes recently generated tokens to discourage verbatim repetition.",
                                value = settings.repeatPenalty.toFloat(),
                                valueRange = InferenceSettings.MIN_REPEAT_PENALTY.toFloat()..InferenceSettings.MAX_REPEAT_PENALTY.toFloat(),
                                steps = 20,
                                onValueChange = { viewModel.updateRepeatPenalty(it.toDouble()) }
                            )
                        }

                        // Presence Penalty
                        item {
                            SettingSliderCard(
                                title = "Presence Penalty: ${decimal2.format(settings.presencePenalty)}",
                                description = "Encourages introducing new topics by penalizing tokens that have appeared at all.",
                                value = settings.presencePenalty.toFloat(),
                                valueRange = InferenceSettings.MIN_PENALTY.toFloat()..InferenceSettings.MAX_PENALTY.toFloat(),
                                steps = 40,
                                onValueChange = { viewModel.updatePresencePenalty(it.toDouble()) }
                            )
                        }

                        // Frequency Penalty
                        item {
                            SettingSliderCard(
                                title = "Frequency Penalty: ${decimal2.format(settings.frequencyPenalty)}",
                                description = "Reduces repetition proportionally to how frequently tokens have already occurred.",
                                value = settings.frequencyPenalty.toFloat(),
                                valueRange = InferenceSettings.MIN_PENALTY.toFloat()..InferenceSettings.MAX_PENALTY.toFloat(),
                                steps = 40,
                                onValueChange = { viewModel.updateFrequencyPenalty(it.toDouble()) }
                            )
                        }

                        // Seed
                        item {
                            SeedSettingCard(
                                seed = settings.seed,
                                onSeedChanged = viewModel::updateSeed
                            )
                        }

                        // Max Tokens
                        item {
                            SettingSliderCard(
                                title = "Max Output Tokens: ${settings.maxTokens}",
                                description = "Maximum number of tokens generated per completion request.",
                                value = settings.maxTokens.toFloat(),
                                valueRange = InferenceSettings.MIN_MAX_TOKENS.toFloat()..InferenceSettings.MAX_MAX_TOKENS.toFloat(),
                                steps = 63,
                                onValueChange = { viewModel.updateMaxTokens(it.toInt()) }
                            )
                        }
                    }

                    SettingsTab.RUNTIME -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Hardware and runtime parameters are allocated when a local GGUF model is initialized. Changes take effect on the next model load.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        // Context Size
                        item {
                            SettingSliderCard(
                                title = "Context Size (n_ctx): ${settings.contextSize}",
                                description = "Total token buffer allocated for prompt context and model completion.",
                                value = settings.contextSize.toFloat(),
                                valueRange = InferenceSettings.MIN_CONTEXT_SIZE.toFloat()..InferenceSettings.MAX_CONTEXT_SIZE.toFloat(),
                                steps = 15,
                                onValueChange = { viewModel.updateContextSize(it.toInt()) },
                                requiresReload = true
                            )
                        }

                        // Threads
                        item {
                            val maxCores = uiState.availableProcessors.coerceAtLeast(1)
                            SettingSliderCard(
                                title = "CPU Inference Threads: ${settings.threads} (Host cores: $maxCores)",
                                description = "Number of CPU threads allocated for prompt processing and token decoding.",
                                value = settings.threads.toFloat(),
                                valueRange = 1f..maxCores.toFloat(),
                                steps = (maxCores - 1).coerceAtLeast(0),
                                onValueChange = { viewModel.updateThreads(it.toInt()) },
                                requiresReload = true
                            )
                        }

                        // Batch Size
                        item {
                            SettingSliderCard(
                                title = "Batch Size (n_batch): ${settings.batchSize}",
                                description = "Logical batch size for prompt processing. Larger values improve prompt processing throughput on high-RAM devices.",
                                value = settings.batchSize.toFloat(),
                                valueRange = InferenceSettings.MIN_BATCH_SIZE.toFloat()..InferenceSettings.MAX_BATCH_SIZE.toFloat(),
                                steps = 15,
                                onValueChange = { viewModel.updateBatchSize(it.toInt()) },
                                requiresReload = true
                            )
                        }
                    }

                    SettingsTab.VOICE -> {
                        item {
                            SettingSwitchCard(
                                title = "Auto-Speak Responses",
                                description = "Assistant will automatically read aloud generated responses using Text-to-Speech.",
                                checked = voiceSettings.autoSpeakResponses,
                                onCheckedChange = viewModel::updateAutoSpeak
                            )
                        }

                        if (uiState.availableVoices.isNotEmpty()) {
                            item {
                                SettingVoiceSelectionCard(
                                    voices = uiState.availableVoices,
                                    selectedVoiceName = voiceSettings.selectedVoiceName,
                                    onVoiceSelected = viewModel::updateSelectedVoice
                                )
                            }
                        }

                        item {
                            SettingSliderCard(
                                title = "Speech Speed: ${decimal1.format(voiceSettings.speechRate)}x",
                                description = "Playback rate for text-to-speech voice audio.",
                                value = voiceSettings.speechRate,
                                valueRange = VoiceSettings.MIN_SPEECH_RATE..VoiceSettings.MAX_SPEECH_RATE,
                                steps = 15,
                                onValueChange = viewModel::updateSpeechRate
                            )
                        }

                        item {
                            SettingSliderCard(
                                title = "Speech Pitch: ${decimal1.format(voiceSettings.speechPitch)}x",
                                description = "Audio tone and pitch of the speech voice.",
                                value = voiceSettings.speechPitch,
                                valueRange = VoiceSettings.MIN_SPEECH_PITCH..VoiceSettings.MAX_SPEECH_PITCH,
                                steps = 10,
                                onValueChange = viewModel::updateSpeechPitch
                            )
                        }

                        item {
                            SettingSwitchCard(
                                title = "Background Voice Conversation",
                                description = "Allows an active voice conversation session to continue running with notification when navigating outside the app.",
                                checked = voiceSettings.backgroundSessionEnabled,
                                onCheckedChange = viewModel::updateBackgroundVoice
                            )
                        }
                    }

                    SettingsTab.STORAGE -> {
                        item {
                            StorageSummaryCard(
                                modelStorage = uiState.formattedModelStorage,
                                attachmentStorage = uiState.formattedAttachmentStorage,
                                availableStorage = uiState.formattedAvailableStorage,
                                totalAppStorage = uiState.formattedTotalAppStorage,
                                modelStorageBytes = uiState.modelStorageBytes,
                                availableStorageBytes = uiState.availableStorageBytes,
                                onRefresh = viewModel::refreshStorage
                            )
                        }

                        item {
                            SystemInfoCard(
                                availableProcessors = uiState.availableProcessors
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::resetToDefaults,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Reset All Settings to Defaults")
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun LanguageSelectionCard(
    currentLanguage: ResponseLanguage,
    onLanguageSelected: (ResponseLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Response Language",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Specify the primary output language or set to Auto for dynamic language detection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (currentLanguage == ResponseLanguage.AUTO) "Auto (Follow User Language)" else currentLanguage.displayName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        if (currentLanguage != ResponseLanguage.AUTO && currentLanguage.nativeName != currentLanguage.displayName) {
                            Text(
                                text = currentLanguage.nativeName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Language",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ResponseLanguage.values().forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lang.displayName,
                                        fontWeight = if (lang == currentLanguage) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (lang != ResponseLanguage.AUTO && lang.nativeName != lang.displayName) {
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = lang.nativeName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onLanguageSelected(lang)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingSettingsCard(
    thinkingEnabled: Boolean,
    thinkingEffort: ThinkingEffort,
    onThinkingToggled: (Boolean) -> Unit,
    onEffortSelected: (ThinkingEffort) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Extended Thinking / Reasoning",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enables step-by-step reasoning tokens for supported thinking models (e.g. DeepSeek-R1, QwQ, o-series).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = thinkingEnabled,
                    onCheckedChange = onThinkingToggled
                )
            }

            AnimatedVisibility(visible = thinkingEnabled) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Reasoning Effort",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThinkingEffort.values().forEach { effort ->
                            FilterChip(
                                selected = thinkingEffort == effort,
                                onClick = { onEffortSelected(effort) },
                                label = { Text(effort.displayName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeedSettingCard(
    seed: Long,
    onSeedChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(seed) {
        mutableStateOf(if (seed < 0) "" else seed.toString())
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Random Seed: ${if (seed < 0) "Random (-1)" else seed.toString()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                if (seed >= 0) {
                    OutlinedButton(
                        onClick = {
                            textValue = ""
                            onSeedChanged(-1L)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Set Random", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Fixed seed provides deterministic responses across identical prompts. Leave empty or -1 for random generation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '-' }
                    textValue = filtered
                    val parsed = filtered.toLongOrNull() ?: -1L
                    onSeedChanged(parsed)
                },
                placeholder = { Text("-1 (Random)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
fun StorageSummaryCard(
    modelStorage: String,
    attachmentStorage: String,
    availableStorage: String,
    totalAppStorage: String,
    modelStorageBytes: Long,
    availableStorageBytes: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = (modelStorageBytes + availableStorageBytes).coerceAtLeast(1L)
    val usedFraction = (modelStorageBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Storage & Memory Usage",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh storage info"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gauge bar
            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            StorageInfoRow(label = "Imported Models Storage", value = modelStorage)
            Spacer(modifier = Modifier.height(6.dp))
            StorageInfoRow(label = "Attachments & Media Storage", value = attachmentStorage)
            Spacer(modifier = Modifier.height(6.dp))
            StorageInfoRow(label = "Total App Data Storage", value = totalAppStorage)
            Spacer(modifier = Modifier.height(6.dp))
            StorageInfoRow(label = "Free Internal Storage", value = availableStorage, highlight = true)
        }
    }
}

@Composable
fun StorageInfoRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SystemInfoCard(
    availableProcessors: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "System Diagnostics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(8.dp))
            StorageInfoRow(label = "CPU Logical Cores", value = "$availableProcessors cores")
            Spacer(modifier = Modifier.height(6.dp))
            StorageInfoRow(label = "Inference Architecture", value = System.getProperty("os.arch") ?: "arm64-v8a")
            Spacer(modifier = Modifier.height(6.dp))
            StorageInfoRow(label = "Local Inference Engine", value = "llama.cpp + ARM KleidiAI")
        }
    }
}

@Composable
fun SettingSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun SettingSliderCard(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    requiresReload: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                if (requiresReload) {
                    Text(
                        text = "Reload needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingVoiceSelectionCard(
    voices: List<VoiceInfo>,
    selectedVoiceName: String?,
    onVoiceSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentVoice = voices.find { it.name == selectedVoiceName }
    val displayLabel = currentVoice?.displayName ?: "Default System Voice"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Assistant Voice",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select an installed Text-to-Speech voice profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Voice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Default System Voice",
                                fontWeight = if (selectedVoiceName == null) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onVoiceSelected(null)
                            expanded = false
                        }
                    )
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = voice.displayName,
                                    fontWeight = if (voice.name == selectedVoiceName) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onVoiceSelected(voice.name)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
