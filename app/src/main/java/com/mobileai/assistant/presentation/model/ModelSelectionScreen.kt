package com.mobileai.assistant.presentation.model

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.ReasoningSupport
import com.mobileai.assistant.presentation.theme.StatusSuccess
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    viewModel: ModelSelectionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF Document Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onModelFileSelected(it) }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissMessages()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { info ->
            snackbarHostState.showSnackbar(info)
            viewModel.dismissMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Model Management",
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
                    IconButton(onClick = viewModel::refreshStorageInfo) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh storage"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Storage Gauge Card
                ModelStorageGaugeCard(
                    modelStorage = uiState.formattedModelStorage,
                    availableStorage = uiState.formattedAvailableStorage,
                    fraction = uiState.storageUsedFraction
                )
            }

            item {
                // Import GGUF Button
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !uiState.isImporting && !uiState.isLoadingModel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Import GGUF Model from Device")
                }
            }

            // Import Progress Card
            if (uiState.isImporting) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Importing Model",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.importStatusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.importProgressFraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Currently Loaded Model Section
            if (uiState.loadedModel != null) {
                item {
                    Text(
                        text = "Currently Loaded Model",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ActiveModelCard(
                        model = uiState.loadedModel!!,
                        isBusy = uiState.engineState.isBusy,
                        onUse = { viewModel.selectActiveModel(uiState.loadedModel!!) },
                        onReload = { viewModel.reloadModel(uiState.loadedModel!!) },
                        onUnload = viewModel::unloadModel,
                        onConfigure = { viewModel.openModelSettings(uiState.loadedModel!!) }
                    )
                }
            }

            // Imported Models Header
            item {
                Text(
                    text = "Imported Models (${uiState.importedModels.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            if (uiState.importedModels.isEmpty()) {
                item {
                    EmptyModelsCard()
                }
            } else {
                items(uiState.importedModels, key = { it.filePath }) { model ->
                    val isCurrentModel = uiState.loadedModel?.filePath == model.filePath
                    ModelItemCard(
                        model = model,
                        isCurrent = isCurrentModel,
                        isLoading = uiState.isLoadingModel && isCurrentModel,
                        isEngineBusy = uiState.engineState.isBusy,
                        onLoad = { viewModel.loadModel(model) },
                        onReload = { viewModel.reloadModel(model) },
                        onUnload = viewModel::unloadModel,
                        onConfigure = { viewModel.openModelSettings(model) },
                        onDelete = { viewModel.deleteModel(model) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Model Profile / Settings Configuration Dialog
    if (uiState.configuredProfileModel != null && uiState.currentProfile != null) {
        ModelProfileConfigDialog(
            model = uiState.configuredProfileModel!!,
            profile = uiState.currentProfile!!,
            onDismiss = viewModel::closeModelSettings,
            onSave = viewModel::saveModelProfile
        )
    }
}

@Composable
fun ModelStorageGaugeCard(
    modelStorage: String,
    availableStorage: String,
    fraction: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Model Storage",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
                Text(
                    text = "Models: $modelStorage / Free: $availableStorage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun ActiveModelCard(
    model: ModelInfo,
    isBusy: Boolean,
    onUse: () -> Unit,
    onReload: () -> Unit,
    onUnload: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${model.fileName} • ${model.formattedSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onConfigure,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Model Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onUse,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Use")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = onReload,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = onUnload,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Unload")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            CapabilityBadgesRow(model.capabilities)
            Spacer(modifier = Modifier.height(10.dp))
            MetadataGrid(model)
        }
    }
}

@Composable
fun ModelItemCard(
    model: ModelInfo,
    isCurrent: Boolean,
    isLoading: Boolean,
    isEngineBusy: Boolean,
    onLoad: () -> Unit,
    onReload: () -> Unit,
    onUnload: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${model.fileName} • ${model.formattedSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onConfigure,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Model Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isCurrent) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusSuccess,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(StatusSuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = onReload,
                            enabled = !isEngineBusy,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onLoad,
                            enabled = !isEngineBusy,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(text = "Load")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onDelete,
                        enabled = !isEngineBusy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete model",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            CapabilityBadgesRow(model.capabilities)
            Spacer(modifier = Modifier.height(10.dp))
            MetadataGrid(model)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilityBadgesRow(capabilities: ModelCapabilities?) {
    if (capabilities == null) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (capabilities.reasoning) {
            ReasoningSupport.EFFORT -> CapabilityBadge(
                label = "Reasoning (Effort)",
                icon = Icons.Default.Psychology,
                tint = MaterialTheme.colorScheme.primary
            )
            ReasoningSupport.TOGGLE -> CapabilityBadge(
                label = "Reasoning (Toggle)",
                icon = Icons.Default.Psychology,
                tint = MaterialTheme.colorScheme.primary
            )
            ReasoningSupport.NONE -> Unit
        }

        if (capabilities.vision) {
            CapabilityBadge(
                label = "Vision",
                icon = Icons.Default.Visibility,
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        if (capabilities.tools) {
            CapabilityBadge(
                label = "Tools",
                icon = Icons.Default.Build,
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        if (capabilities.multilingual) {
            CapabilityBadge(
                label = "Multilingual",
                icon = Icons.Default.Language,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CapabilityBadge(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetadataGrid(model: ModelInfo) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        model.architecture?.let {
            MetadataTag(label = "Arch", value = it)
        }
        model.quantization?.let {
            MetadataTag(label = "Quant", value = it)
        }
        model.contextLength?.let {
            MetadataTag(label = "Context", value = "$it")
        }
        model.tensorCount?.let {
            MetadataTag(label = "Tensors", value = "$it")
        }
    }
}

@Composable
fun MetadataTag(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ModelProfileConfigDialog(
    model: ModelInfo,
    profile: ModelSettingsProfile,
    onDismiss: () -> Unit,
    onSave: (ModelSettingsProfile) -> Unit
) {
    var displayName by remember { mutableStateOf(profile.displayName.ifBlank { model.displayName }) }
    var temperature by remember { mutableFloatStateOf(profile.params.temperature.toFloat()) }
    var topP by remember { mutableFloatStateOf(profile.params.topP.toFloat()) }
    var topK by remember { mutableFloatStateOf(profile.params.topK.toFloat()) }
    var maxTokens by remember { mutableFloatStateOf(profile.params.maxTokens.toFloat()) }
    var systemPrompt by remember { mutableStateOf(profile.params.systemPrompt) }
    val decimal2 = remember { DecimalFormat("#.##") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Model Settings: ${model.displayName}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Text(
                    text = "Temperature: ${decimal2.format(temperature)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = InferenceSettings.MIN_TEMPERATURE..InferenceSettings.MAX_TEMPERATURE,
                    steps = 20
                )

                Text(
                    text = "Top-P: ${decimal2.format(topP)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = InferenceSettings.MIN_TOP_P..InferenceSettings.MAX_TOP_P,
                    steps = 19
                )

                Text(
                    text = "Top-K: ${topK.toInt()}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = topK,
                    onValueChange = { topK = it },
                    valueRange = InferenceSettings.MIN_TOP_K.toFloat()..InferenceSettings.MAX_TOP_K.toFloat(),
                    steps = 99
                )

                Text(
                    text = "Max Tokens: ${maxTokens.toInt()}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    valueRange = InferenceSettings.MIN_MAX_TOKENS.toFloat()..InferenceSettings.MAX_MAX_TOKENS.toFloat(),
                    steps = 63
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Custom System Prompt") },
                    placeholder = { Text("Leave blank to use default") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedProfile = profile.copy(
                        displayName = displayName.trim(),
                        params = profile.params.copy(
                            temperature = temperature.toDouble(),
                            topP = topP.toDouble(),
                            topK = topK.toInt(),
                            maxTokens = maxTokens.toInt(),
                            systemPrompt = systemPrompt.trim()
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(updatedProfile)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyModelsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No models in storage",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the 'Import GGUF Model from Device' button above to select and copy a compatible GGUF model file into private app storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
