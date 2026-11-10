package com.mobileai.assistant.presentation.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ThinkingEffort
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.presentation.components.LanguageSelectorSheet
import com.mobileai.assistant.presentation.components.MessageBubble
import com.mobileai.assistant.presentation.components.ModelSettingsSheet
import com.mobileai.assistant.presentation.components.ModelStatusBar
import com.mobileai.assistant.presentation.components.PendingAttachmentsStrip
import com.mobileai.assistant.presentation.dot.CreateDotDialog
import com.mobileai.assistant.presentation.dot.DotSelectorDialog
import com.mobileai.assistant.presentation.dot.parseThemeColor
import com.mobileai.assistant.presentation.dot.toIcon
import com.mobileai.assistant.presentation.task.TaskProgressCard
import com.mobileai.assistant.presentation.task.ToolApprovalCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToModelSelection: () -> Unit,
    onNavigateToEndpoints: () -> Unit,
    onNavigateToVoiceMode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showTargetDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }
    var showDotSelectorDialog by remember { mutableStateOf(false) }
    var showCreateDotDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var renamingSession by remember { mutableStateOf<ConversationSession?>(null) }
    var deletingSession by remember { mutableStateOf<ConversationSession?>(null) }

    // Audio recording permission launcher
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startVoiceInput()
        }
    }

    // Attachment picker launcher
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addAttachmentFromUri(uri, context)
        }
    }

    // Import conversation JSON launcher
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    val jsonString = reader.readText()
                    val result = viewModel.importSession(jsonString)
                    if (result.isSuccess) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Conversation imported successfully")
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Failed to import: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Error reading file: ${e.message}")
                }
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive or when streaming while at bottom
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.messages.lastOrNull()?.content?.length) {
        if (uiState.messages.isNotEmpty() && !listState.canScrollForward) {
            listState.scrollToItem(uiState.messages.size - 1)
        }
    }

    // Show error in snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    // Show export notification
    LaunchedEffect(uiState.exportNotification) {
        uiState.exportNotification?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearExportNotification()
        }
    }

    // Dot Selector Dialog
    if (showDotSelectorDialog) {
        DotSelectorDialog(
            dots = uiState.availableDots,
            activeDot = uiState.activeDot,
            onSelectDot = { dot ->
                viewModel.selectDot(dot.id)
                showDotSelectorDialog = false
            },
            onCreateDotClick = {
                showDotSelectorDialog = false
                showCreateDotDialog = true
            },
            onDeleteDot = { dotId ->
                viewModel.deleteCustomDot(dotId)
            },
            onDismiss = { showDotSelectorDialog = false }
        )
    }

    // Create Dot Dialog
    if (showCreateDotDialog) {
        CreateDotDialog(
            onDismiss = { showCreateDotDialog = false },
            onSaveDot = { name, role, desc, prompt, preset, color ->
                viewModel.createCustomDot(name, role, desc, prompt, preset, color)
                showCreateDotDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Created assistant: $name")
                }
            }
        )
    }

    // Target Selection Dialog
    if (showTargetDialog) {
        TargetSwitcherDialog(
            activeTarget = uiState.activeTarget,
            loadedModel = uiState.loadedModel,
            activeEndpoint = uiState.activeEndpoint,
            onSelectLocal = {
                uiState.loadedModel?.let {
                    viewModel.selectTarget(InferenceTarget.Local(it))
                }
                showTargetDialog = false
            },
            onSelectRemote = {
                uiState.activeEndpoint?.let {
                    viewModel.selectTarget(InferenceTarget.Remote(it, it.selectedModel))
                }
                showTargetDialog = false
            },
            onManageModels = {
                showTargetDialog = false
                onNavigateToModelSelection()
            },
            onManageEndpoints = {
                showTargetDialog = false
                onNavigateToEndpoints()
            },
            onDismiss = { showTargetDialog = false }
        )
    }

    // Language Selector Sheet
    if (uiState.isLanguageSelectorVisible) {
        LanguageSelectorSheet(
            selectedLanguage = uiState.responseLanguage,
            onLanguageSelected = { language ->
                viewModel.setResponseLanguage(language)
                viewModel.hideLanguageSelector()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Language set to ${language.displayName}")
                }
            },
            onDismiss = viewModel::hideLanguageSelector
        )
    }

    // Model Settings Sheet (Runtime Parameters)
    if (uiState.isModelSettingsSheetVisible) {
        val isLocal = uiState.loadedModel != null || uiState.activeTarget is InferenceTarget.Local
        ModelSettingsSheet(
            params = uiState.effectiveParams,
            capabilities = uiState.capabilities,
            isLocalModel = isLocal,
            onSaveParams = { params ->
                viewModel.setConversationParams(params)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Parameters updated")
                }
            },
            onReloadModel = {
                viewModel.reloadActiveModel()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Reloading model into memory...")
                }
            },
            onResetDefaults = {
                viewModel.resetConversationParams()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Reset to defaults")
                }
            },
            onDismiss = viewModel::hideModelSettingsSheet
        )
    }

    // Edit User Message Dialog
    editingMessage?.let { msg ->
        EditMessageDialog(
            initialContent = msg.content,
            onDismiss = { editingMessage = null },
            onSubmit = { newText ->
                viewModel.editAndResendMessage(msg.id, newText)
                editingMessage = null
            }
        )
    }

    // Rename Conversation Dialog
    renamingSession?.let { session ->
        RenameConversationDialog(
            currentTitle = session.title,
            onDismiss = { renamingSession = null },
            onSave = { newTitle ->
                viewModel.renameConversation(session.id, newTitle)
                renamingSession = null
            }
        )
    }

    // Delete Conversation Dialog
    deletingSession?.let { session ->
        DeleteConversationDialog(
            sessionTitle = session.title,
            onDismiss = { deletingSession = null },
            onConfirm = {
                viewModel.deleteConversation(session.id)
                deletingSession = null
            }
        )
    }

    // Clear Chat Confirmation Dialog
    if (showClearConfirmationDialog) {
        ClearChatConfirmationDialog(
            onDismiss = { showClearConfirmationDialog = false },
            onConfirm = {
                viewModel.clearChat()
                showClearConfirmationDialog = false
            }
        )
    }

    // Custom System Prompt Dialog
    if (uiState.isSystemPromptDialogVisible) {
        SystemPromptDialog(
            currentPrompt = uiState.conversationSystemPrompt ?: viewModel.getCurrentSystemPrompt(),
            onDismiss = viewModel::hideSystemPromptDialog,
            onSave = { updatedPrompt ->
                viewModel.setConversationSystemPrompt(updatedPrompt)
                viewModel.hideSystemPromptDialog()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("System instructions updated for this conversation")
                }
            },
            onReset = {
                viewModel.setConversationSystemPrompt(null)
                viewModel.hideSystemPromptDialog()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Reset to default instructions")
                }
            }
        )
    }

    // Modal Drawer for multi-conversation history
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ConversationDrawerContent(
                    conversations = uiState.filteredConversations,
                    activeConversationId = uiState.activeConversationId,
                    dots = uiState.availableDots,
                    activeDot = uiState.activeDot,
                    selectedDotFilterId = uiState.selectedDotFilterId,
                    onSelectDot = { dot -> viewModel.selectDot(dot.id) },
                    onSelectDotFilter = viewModel::setDotFilter,
                    onCreateDotClick = {
                        coroutineScope.launch { drawerState.close() }
                        showCreateDotDialog = true
                    },
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onNewChat = {
                        viewModel.createNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onSelectConversation = { sessionId ->
                        viewModel.selectConversation(sessionId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onRenameClick = { session -> renamingSession = session },
                    onDeleteClick = { session -> deletingSession = session },
                    onTogglePinClick = { session -> viewModel.togglePinConversation(session.id) },
                    onExportClick = { session ->
                        val exportText = viewModel.exportMarkdown(session.id)
                        shareConversationExport(
                            context = context,
                            exportText = exportText,
                            title = session.title,
                            onFallbackCopy = {
                                clipboardManager.setText(AnnotatedString(exportText))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Export copied to clipboard")
                                }
                            }
                        )
                    },
                    onNavigateToSettings = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToModels = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToModelSelection()
                    },
                    onNavigateToEndpoints = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToEndpoints()
                    }
                )
            }
        }
    ) {
        val dotThemeColor = uiState.activeDot.parseThemeColor()
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Chat History Drawer"
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Active Dot Avatar Circle - Clickable
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(dotThemeColor)
                                    .clickable { showDotSelectorDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = uiState.activeDot.avatarPreset.toIcon(),
                                    contentDescription = uiState.activeDot.name,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = uiState.activeConversationTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    // Active Dot colored chip - Clickable with Dropdown Arrow
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(dotThemeColor.copy(alpha = 0.15f))
                                            .border(1.dp, dotThemeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable { showDotSelectorDialog = true }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = uiState.activeDot.name,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                                color = dotThemeColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Switch Assistant",
                                                tint = dotThemeColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    // Active Dot role subtitle
                                    Text(
                                        text = uiState.activeDot.role,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )

                                    // Target picker chip
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                            .clickable { showTargetDialog = true }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = uiState.activeTargetName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Language selector chip
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                            .clickable { viewModel.showLanguageSelector(true) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = uiState.responseLanguage.displayName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        // Switch Assistant (Dots) button
                        IconButton(onClick = { showDotSelectorDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Switch Assistant (Dots)",
                                tint = dotThemeColor
                            )
                        }

                        // System instructions button
                        IconButton(onClick = { viewModel.showSystemPromptDialog(true) }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "System Instructions",
                                tint = if (!uiState.conversationSystemPrompt.isNullOrBlank()) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Voice Conversation Mode button
                        IconButton(onClick = onNavigateToVoiceMode) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Voice Conversation Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Overflow Options Menu
                        Box {
                            IconButton(onClick = { showTopMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Chat") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.createNewChat()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Model Parameters") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        viewModel.showModelSettingsSheet(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Markdown") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    enabled = uiState.messages.isNotEmpty(),
                                    onClick = {
                                        showTopMenu = false
                                        val exportText = viewModel.exportMarkdown()
                                        shareConversationExport(
                                            context = context,
                                            exportText = exportText,
                                            title = uiState.activeConversationTitle,
                                            onFallbackCopy = {
                                                clipboardManager.setText(AnnotatedString(exportText))
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Markdown copied to clipboard")
                                                }
                                            }
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export JSON") },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    enabled = uiState.messages.isNotEmpty(),
                                    onClick = {
                                        showTopMenu = false
                                        val exportJson = viewModel.exportJson()
                                        shareConversationExport(
                                            context = context,
                                            exportText = exportJson,
                                            title = "${uiState.activeConversationTitle}.json",
                                            onFallbackCopy = {
                                                clipboardManager.setText(AnnotatedString(exportJson))
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("JSON copied to clipboard")
                                                }
                                            }
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import Conversation") },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        importJsonLauncher.launch("*/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage Models") },
                                    leadingIcon = {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        onNavigateToModelSelection()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("AI Endpoints") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        onNavigateToEndpoints()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        onNavigateToSettings()
                                    }
                                )
                                if (uiState.messages.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Clear Conversation",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showTopMenu = false
                                            showClearConfirmationDialog = true
                                        }
                                    )
                                }
                            }
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
                    .imePadding()
            ) {
                // Persistent Model / Endpoint Status Bar
                val latestMetrics = uiState.messages.lastOrNull { !it.isUser && it.metrics != null }?.metrics
                ModelStatusBar(
                    engineState = uiState.engineState,
                    loadedModel = uiState.loadedModel,
                    activeEndpoint = uiState.activeEndpoint,
                    activeTarget = uiState.activeTarget,
                    runtimeMetrics = latestMetrics,
                    onBarClick = {
                        if (uiState.loadedModel != null || (uiState.activeEndpoint != null && uiState.activeEndpoint!!.isConfigured)) {
                            showTargetDialog = true
                        } else if (uiState.activeEndpoint != null) {
                            onNavigateToEndpoints()
                        } else {
                            onNavigateToModelSelection()
                        }
                    }
                )

                // Pinned Pending Tool Approval Requests (Prominent, High-Priority Banner)
                AnimatedVisibility(
                    visible = uiState.pendingApprovals.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val pendingRequest = uiState.pendingApprovals.firstOrNull()
                    if (pendingRequest != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            ToolApprovalCard(
                                request = pendingRequest,
                                onApprove = { viewModel.approveToolCall(pendingRequest.id) },
                                onAlwaysAllow = { viewModel.alwaysAllowToolCall(pendingRequest.id) },
                                onReject = { reason -> viewModel.rejectToolCall(pendingRequest.id, reason) }
                            )
                        }
                    }
                }

                // Pinned Active Task Progress Section (Collapsible Banner / Card)
                AnimatedVisibility(
                    visible = uiState.activeTasks.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val activeTask = uiState.activeTasks.firstOrNull()
                    if (activeTask != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            if (uiState.isTaskProgressExpanded) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    TaskProgressCard(
                                        task = activeTask,
                                        onPause = { viewModel.pauseTask(activeTask.id) },
                                        onResume = { viewModel.resumeTask(activeTask.id) },
                                        onCancel = { viewModel.cancelTask(activeTask.id) }
                                    )
                                    IconButton(
                                        onClick = { viewModel.toggleTaskProgressExpanded() },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Collapse Task Progress",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                CompactTaskProgressBanner(
                                    task = activeTask,
                                    onExpand = { viewModel.toggleTaskProgressExpanded() }
                                )
                            }
                        }
                    }
                }

                // Conversation List with Scroll-to-Bottom Floating Action Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (uiState.messages.isEmpty()) {
                        EmptyChatPlaceholder(
                            isModelLoaded = uiState.isModelAvailable,
                            onSelectModelClick = onNavigateToModelSelection,
                            onConfigureEndpointClick = onNavigateToEndpoints,
                            activeDot = uiState.activeDot,
                            onSwitchDotClick = { showDotSelectorDialog = true },
                            onSendPrompt = { promptText ->
                                viewModel.onInputTextChanged(promptText)
                                viewModel.sendMessage()
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(uiState.messages, key = { it.id }) { message ->
                                val isThisSpeaking = uiState.speakingMessageId == message.id && uiState.isSpeaking
                                val isThisPaused = uiState.speakingMessageId == message.id && uiState.isSpeechPaused
                                MessageBubble(
                                    message = message,
                                    onCopy = { textToCopy ->
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Copied plain text to clipboard")
                                        }
                                    },
                                    onCopyMarkdown = { mdToCopy ->
                                        clipboardManager.setText(AnnotatedString(mdToCopy))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Copied Markdown to clipboard")
                                        }
                                    },
                                    onEdit = if (!uiState.isGenerating) { userMsg ->
                                        editingMessage = userMsg
                                    } else null,
                                    onRegenerate = if (!uiState.isGenerating) { assistantMsg ->
                                        viewModel.regenerateResponse(assistantMsg.id)
                                    } else null,
                                    onDelete = { msg ->
                                        viewModel.deleteMessage(msg.id)
                                    },
                                    onContinue = { assistantMsg ->
                                        viewModel.continueGeneration(assistantMsg.id)
                                    },
                                    onSpeak = { viewModel.speakMessage(message) },
                                    onPauseSpeak = { viewModel.pauseSpeech() },
                                    onResumeSpeak = { viewModel.resumeSpeech() },
                                    onStopSpeak = { viewModel.interruptSpeech() },
                                    isSpeaking = isThisSpeaking,
                                    isPaused = isThisPaused,
                                    onGenUiEvent = { msgId, event ->
                                        viewModel.handleGenUiEvent(msgId, event)
                                    }
                                )
                            }
                        }
                    }

                    // Floating Scroll-to-Bottom Action Button
                    val isScrolledUp by remember {
                        derivedStateOf {
                            listState.canScrollForward
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScrolledUp && uiState.messages.isNotEmpty(),
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 12.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Thinking Controls Bar (Think Toggle + Thinking Effort Selector)
                ThinkingControlsBar(
                    isThinkingEnabled = uiState.isThinkingEnabled,
                    thinkingEffort = uiState.thinkingEffort,
                    isEffortSupported = uiState.isThinkingEffortSupported,
                    onToggleThinking = viewModel::toggleThinkingMode,
                    onSelectEffort = viewModel::setThinkingEffort
                )

                // Bottom Input Bar with Attachments, Speech-to-Text & Send
                ChatInputBar(
                    text = uiState.inputText,
                    isGenerating = uiState.isGenerating,
                    isListening = uiState.isListening,
                    canSend = uiState.canSendMessage,
                    pendingAttachments = uiState.pendingAttachments,
                    onRemoveAttachment = viewModel::removePendingAttachment,
                    onAttachClick = { attachmentPickerLauncher.launch("*/*") },
                    onTextChanged = viewModel::onInputTextChanged,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration,
                    onMicClick = {
                        if (uiState.isListening) {
                            viewModel.stopVoiceInput()
                        } else {
                            if (hasAudioPermission) {
                                viewModel.startVoiceInput()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    onCancelVoice = viewModel::cancelVoiceInput,
                    placeholderText = if (!uiState.isModelAvailable) "Load a model or configure an endpoint above..."
                                     else if (uiState.isListening) "Listening... speak now"
                                     else "Type or tap mic to speak..."
                )
            }
        }
    }
}

private fun shareConversationExport(
    context: Context,
    exportText: String,
    title: String,
    onFallbackCopy: (() -> Unit)? = null
) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, exportText)
            putExtra(Intent.EXTRA_SUBJECT, "Chat Export: $title")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Conversation")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    } catch (_: Exception) {
        onFallbackCopy?.invoke()
    }
}

@Composable
fun EditMessageDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var editedText by remember(initialContent) { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit & Resend Message",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Editing this message will fork the conversation from this point.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(editedText) },
                enabled = editedText.isNotBlank()
            ) {
                Text("Update & Resend")
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
fun RenameConversationDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var titleText by remember(currentTitle) { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Chat",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("Enter conversation title...") }
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(titleText) },
                enabled = titleText.isNotBlank()
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
fun DeleteConversationDialog(
    sessionTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Chat Session?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"$sessionTitle\"? All messages in this conversation will be permanently removed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
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
fun ClearChatConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Clear Current Chat?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "This will remove all messages from the current conversation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Clear")
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
fun SystemPromptDialog(
    currentPrompt: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: (() -> Unit)? = null
) {
    var promptText by remember(currentPrompt) { mutableStateOf(currentPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Instructions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Precedence Order: Conversation Override > Model Profile > Global Settings.\nInstructions set here apply specifically to this conversation session.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Enter custom system instructions...") }
                )

                if (onReset != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onReset) {
                            Text("Reset to Default", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(promptText) }) {
                Text("Apply")
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
fun ThinkingControlsBar(
    isThinkingEnabled: Boolean,
    thinkingEffort: ThinkingEffort,
    isEffortSupported: Boolean = true,
    onToggleThinking: () -> Unit,
    onSelectEffort: (ThinkingEffort) -> Unit,
    modifier: Modifier = Modifier
) {
    var effortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Think Toggle Chip
        FilterChip(
            selected = isThinkingEnabled,
            onClick = onToggleThinking,
            label = {
                Text(
                    text = if (isThinkingEnabled) "Think ON" else "Think OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isThinkingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Thinking Effort Selector (Visible only when Thinking Mode is ON AND supported by the active model)
        if (isThinkingEnabled && isEffortSupported) {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { effortMenuExpanded = true },
                    label = {
                        Text(
                            text = "Effort: ${thinkingEffort.displayName}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                DropdownMenu(
                    expanded = effortMenuExpanded,
                    onDismissRequest = { effortMenuExpanded = false }
                ) {
                    ThinkingEffort.entries.forEach { effort ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = effort.displayName,
                                    fontWeight = if (effort == thinkingEffort) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectEffort(effort)
                                effortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatPlaceholder(
    isModelLoaded: Boolean,
    onSelectModelClick: () -> Unit,
    onConfigureEndpointClick: () -> Unit,
    activeDot: DotProfile = DotProfile.STARTER_PROFILES.first(),
    onSwitchDotClick: () -> Unit = {},
    onSendPrompt: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dotThemeColor = activeDot.parseThemeColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(dotThemeColor.copy(alpha = 0.15f))
                .border(2.dp, dotThemeColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = activeDot.avatarPreset.toIcon(),
                contentDescription = activeDot.name,
                tint = dotThemeColor,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = activeDot.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(dotThemeColor.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = activeDot.role,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = dotThemeColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = activeDot.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSwitchDotClick,
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = dotThemeColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Switch Assistant (${activeDot.name})",
                color = dotThemeColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isModelLoaded) {
            Text(
                text = "Try Generative UI:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ElevatedCard(
                    onClick = { onSendPrompt("Show me a comparison table comparing local offline LLMs (llama.cpp) vs cloud endpoints with privacy, latency, and features.") },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Text(
                        text = "📊 Compare local vs cloud models (Table)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                ElevatedCard(
                    onClick = { onSendPrompt("Create a bar chart showing 2026 quarterly adoption of mobile AI in millions of users.") },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Text(
                        text = "📈 2026 Mobile AI growth (Chart)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                ElevatedCard(
                    onClick = { onSendPrompt("Draw a diagram flowchart showing how AI agents plan, request approvals, and stream Generative UI.") },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Text(
                        text = "🔀 AI Agent workflow lifecycle (Diagram)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                ElevatedCard(
                    onClick = { onSendPrompt("Create an interactive configuration form to adjust model sampling temperature and top-p.") },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Text(
                        text = "🎛️ Configure inference parameters (Form)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No model active. Tap to select a model or configure an endpoint.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTaskProgressBanner(
    task: DotTask,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { task.progressFraction },
                modifier = Modifier
                    .width(60.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${task.completedSteps}/${task.totalSteps}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand Task Progress",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    isGenerating: Boolean,
    isListening: Boolean,
    canSend: Boolean,
    pendingAttachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onAttachClick: () -> Unit = {},
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit,
    onCancelVoice: () -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Pending Attachments Strip
        if (pendingAttachments.isNotEmpty()) {
            PendingAttachmentsStrip(
                attachments = pendingAttachments,
                onRemoveAttachment = onRemoveAttachment
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attachment Button
            IconButton(
                onClick = onAttachClick,
                enabled = !isGenerating,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach image or document",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Speech-to-Text Microphone Button
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop Listening" else "Speech to Text",
                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text Input Field
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (isListening) {
                        IconButton(onClick = onCancelVoice) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel voice",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )

            // Send or Stop Generation Button
            if (isGenerating) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Generation",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TargetSwitcherDialog(
    activeTarget: InferenceTarget?,
    loadedModel: com.mobileai.assistant.domain.model.ModelInfo?,
    activeEndpoint: com.mobileai.assistant.domain.model.RemoteEndpointConfig?,
    onSelectLocal: () -> Unit,
    onSelectRemote: () -> Unit,
    onManageModels: () -> Unit,
    onManageEndpoints: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Switch AI Inference Target",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select whether to chat with your locally loaded GGUF model or your external AI endpoint. Chat history is preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Local Model Option
                if (loadedModel != null) {
                    val isLocalActive = activeTarget is InferenceTarget.Local
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSelectLocal),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLocalActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = loadedModel.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Local GGUF (${loadedModel.quantization ?: "Local"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isLocalActive) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Remote Endpoint Option
                if (activeEndpoint != null && activeEndpoint.isConfigured) {
                    val isRemoteActive = activeTarget is InferenceTarget.Remote
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSelectRemote),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRemoteActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
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
                                        text = activeEndpoint.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Remote: ${activeEndpoint.selectedModel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isRemoteActive) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onManageModels) {
                        Text("Manage GGUF")
                    }
                    TextButton(onClick = onManageEndpoints) {
                        Text("Manage Endpoints")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
