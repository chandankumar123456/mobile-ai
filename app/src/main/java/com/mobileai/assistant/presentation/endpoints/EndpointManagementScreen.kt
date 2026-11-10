package com.mobileai.assistant.presentation.endpoints

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.ReasoningSupport
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.presentation.theme.StatusError
import com.mobileai.assistant.presentation.theme.StatusSuccess
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointManagementScreen(
    viewModel: EndpointManagementViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
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
                        text = "External AI Endpoints",
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
                // Form Card: Add or Edit Endpoint
                EndpointFormCard(
                    uiState = uiState,
                    onNameChanged = viewModel::onNameChanged,
                    onUrlChanged = viewModel::onUrlChanged,
                    onApiKeyChanged = viewModel::onApiKeyChanged,
                    onSelectedModelChanged = viewModel::onSelectedModelChanged,
                    onTestConnection = viewModel::testConnection,
                    onFetchModels = viewModel::fetchModels,
                    onSave = viewModel::saveEndpoint,
                    onCancel = viewModel::resetForm
                )
            }

            // Saved Endpoints Header
            item {
                Text(
                    text = "Configured Endpoints (${uiState.endpoints.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            if (uiState.endpoints.isEmpty()) {
                item {
                    EmptyEndpointsCard()
                }
            } else {
                items(uiState.endpoints, key = { it.id }) { endpoint ->
                    val isActive = uiState.activeEndpoint?.id == endpoint.id
                    val latency = uiState.endpointLatencies[endpoint.id]
                    val caps = uiState.modelCapabilities[endpoint.selectedModel]
                        ?: ModelCapabilitiesResolver.fromRemote(endpoint.selectedModel)

                    EndpointItemCard(
                        endpoint = endpoint,
                        isActive = isActive,
                        latencyMs = latency,
                        capabilities = caps,
                        onSelect = { viewModel.selectActiveEndpoint(endpoint) },
                        onPing = { viewModel.testEndpointPing(endpoint) },
                        onConfigure = { viewModel.openModelProfile(endpoint, endpoint.selectedModel) },
                        onEdit = { viewModel.startEditing(endpoint) },
                        onDelete = { viewModel.deleteEndpoint(endpoint) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Remote Model Profile Configuration Dialog
    if (uiState.configuringProfileEndpoint != null && uiState.currentProfile != null) {
        RemoteModelProfileDialog(
            endpoint = uiState.configuringProfileEndpoint!!,
            profile = uiState.currentProfile!!,
            onDismiss = viewModel::closeModelProfile,
            onSave = viewModel::saveModelProfile
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointFormCard(
    uiState: EndpointManagementUiState,
    onNameChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSelectedModelChanged: (String) -> Unit,
    onTestConnection: () -> Unit,
    onFetchModels: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var isModelDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (uiState.isEditing) "Edit Endpoint" else "Configure AI Endpoint",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Endpoint Name
            OutlinedTextField(
                value = uiState.nameInput,
                onValueChange = onNameChanged,
                label = { Text("Display Name") },
                placeholder = { Text("e.g. Ollama Local, vLLM Server, Groq, OpenAI") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Base URL
            OutlinedTextField(
                value = uiState.urlInput,
                onValueChange = onUrlChanged,
                label = { Text("Base URL / Endpoint") },
                placeholder = { Text("e.g. http://10.0.2.2:11434/v1 or https://api.openai.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // API Key
            OutlinedTextField(
                value = uiState.apiKeyInput,
                onValueChange = onApiKeyChanged,
                label = { Text("API Key (Optional for local servers)") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Model Selection (Dropdown or Manual Input)
            if (uiState.availableModels.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = isModelDropdownExpanded,
                    onExpandedChange = { isModelDropdownExpanded = !isModelDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = uiState.selectedModelInput,
                        onValueChange = onSelectedModelChanged,
                        label = { Text("Model ID") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded) },
                        shape = RoundedCornerShape(8.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = isModelDropdownExpanded,
                        onDismissRequest = { isModelDropdownExpanded = false }
                    ) {
                        uiState.availableModels.forEach { modelName ->
                            val caps = uiState.modelCapabilities[modelName]
                                ?: ModelCapabilitiesResolver.fromRemote(modelName)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(modelName, modifier = Modifier.weight(1f, fill = false))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        MiniCapabilityPills(caps)
                                    }
                                },
                                onClick = {
                                    onSelectedModelChanged(modelName)
                                    isModelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = uiState.selectedModelInput,
                    onValueChange = onSelectedModelChanged,
                    label = { Text("Model ID") },
                    placeholder = { Text("e.g. llama3.2, gpt-4o-mini, qwen2.5-coder") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Test Connection Result Banner
            uiState.testResult?.let { res ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (res.isSuccessful) StatusSuccess.copy(alpha = 0.12f)
                            else StatusError.copy(alpha = 0.12f)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (res.isSuccessful) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (res.isSuccessful) StatusSuccess else StatusError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${res.message} • ${res.latencyMs} ms latency",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (res.isSuccessful) StatusSuccess else StatusError
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Action Buttons Row: Test Connection & Fetch Models
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !uiState.isTesting && uiState.urlInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test Connection")
                    }
                }

                OutlinedButton(
                    onClick = onFetchModels,
                    enabled = !uiState.isFetchingModels && uiState.urlInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uiState.isFetchingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Fetch Models")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Save / Cancel Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isEditing) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = uiState.canSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (uiState.isEditing) "Update Endpoint" else "Save Endpoint")
                }
            }
        }
    }
}

@Composable
private fun EndpointItemCard(
    endpoint: RemoteEndpointConfig,
    isActive: Boolean,
    latencyMs: Long?,
    capabilities: ModelCapabilities?,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onConfigure: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = endpoint.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = endpoint.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
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
                    } else {
                        Button(
                            onClick = onSelect,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Use")
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onConfigure, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Model Parameters",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges row: Model name, ping latency pill, auth status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Model: ${endpoint.selectedModel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (latencyMs != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusSuccess.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "$latencyMs ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onPing,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Test Ping",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (!endpoint.apiKey.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Auth Key",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (capabilities != null) {
                Spacer(modifier = Modifier.height(8.dp))
                RemoteCapabilityBadges(capabilities)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteCapabilityBadges(capabilities: ModelCapabilities) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (capabilities.reasoning) {
            ReasoningSupport.EFFORT -> CapabilityBadgeItem("Reasoning (Effort)", Icons.Default.Psychology, MaterialTheme.colorScheme.primary)
            ReasoningSupport.TOGGLE -> CapabilityBadgeItem("Reasoning (Toggle)", Icons.Default.Psychology, MaterialTheme.colorScheme.primary)
            ReasoningSupport.NONE -> Unit
        }

        if (capabilities.vision) {
            CapabilityBadgeItem("Vision", Icons.Default.Visibility, MaterialTheme.colorScheme.secondary)
        }

        if (capabilities.tools) {
            CapabilityBadgeItem("Tools", Icons.Default.Build, MaterialTheme.colorScheme.tertiary)
        }

        if (capabilities.jsonMode) {
            CapabilityBadgeItem("JSON Mode", Icons.Default.CheckCircle, MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun MiniCapabilityPills(capabilities: ModelCapabilities) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (capabilities.reasoning != ReasoningSupport.NONE) {
            Text(
                text = "Reasoning",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp
            )
        }
        if (capabilities.vision) {
            Text(
                text = "Vision",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 10.sp
            )
        }
        if (capabilities.tools) {
            Text(
                text = "Tools",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun CapabilityBadgeItem(
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

@Composable
fun RemoteModelProfileDialog(
    endpoint: RemoteEndpointConfig,
    profile: ModelSettingsProfile,
    onDismiss: () -> Unit,
    onSave: (ModelSettingsProfile) -> Unit
) {
    var displayName by remember { mutableStateOf(profile.displayName.ifBlank { profile.modelId }) }
    var temperature by remember { mutableFloatStateOf(profile.params.temperature.toFloat()) }
    var topP by remember { mutableFloatStateOf(profile.params.topP.toFloat()) }
    var topK by remember { mutableFloatStateOf(profile.params.topK.toFloat()) }
    var minP by remember { mutableDoubleStateOf(profile.params.minP) }
    var maxTokens by remember { mutableFloatStateOf(profile.params.maxTokens.toFloat()) }
    var systemPrompt by remember { mutableStateOf(profile.params.systemPrompt) }
    val decimal2 = remember { DecimalFormat("#.##") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Parameters: ${endpoint.name} (${profile.modelId})",
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
                    text = "Min-P: ${decimal2.format(minP)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = minP.toFloat(),
                    onValueChange = { minP = it.toDouble() },
                    valueRange = InferenceSettings.MIN_MIN_P.toFloat()..InferenceSettings.MAX_MIN_P.toFloat(),
                    steps = 20
                )

                Text(
                    text = "Max Output Tokens: ${maxTokens.toInt()}",
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
                    placeholder = { Text("Leave blank to inherit global system prompt") },
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
                            minP = minP,
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
private fun EmptyEndpointsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No custom endpoints configured",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter a base URL above (such as a local Ollama, LM Studio, vLLM instance or OpenAI endpoint) to connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}
