package com.mobileai.assistant.presentation.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.remote.RemoteInferenceEngine
import com.mobileai.assistant.data.settings.AppSettingsRepository
import com.mobileai.assistant.data.voice.SpeechToTextManager
import com.mobileai.assistant.data.voice.TextSanitizer
import com.mobileai.assistant.data.voice.TextToSpeechManager
import com.mobileai.assistant.data.voice.VoiceAssistantForegroundService
import com.mobileai.assistant.domain.artifact.ArtifactRepository
import com.mobileai.assistant.domain.dot.DotAvatarPreset
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.dot.DotPromptBuilder
import com.mobileai.assistant.domain.dot.DotRepository
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.genui.GenUiPromptSpec
import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.PromptBuilder
import com.mobileai.assistant.domain.model.ReasoningSupport
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import com.mobileai.assistant.domain.model.ThinkingCapability
import com.mobileai.assistant.domain.model.ThinkingEffort
import com.mobileai.assistant.domain.model.VoiceState
import com.mobileai.assistant.domain.repository.ChatRepository
import com.mobileai.assistant.domain.repository.EndpointRepository
import com.mobileai.assistant.domain.repository.VoiceSettingsRepository
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.TaskManager
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.tool.ApprovalStatus
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolApprovalRequest
import com.mobileai.assistant.domain.tool.ToolAuditLogger
import com.mobileai.assistant.domain.tool.ToolRegistry
import com.mobileai.assistant.domain.usecase.CancelGenerationUseCase
import com.mobileai.assistant.domain.usecase.GenerateResponseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    private val engine: LocalInferenceEngine,
    private val chatRepository: ChatRepository,
    private val generateResponseUseCase: GenerateResponseUseCase? = null,
    private val cancelGenerationUseCase: CancelGenerationUseCase? = null,
    private val remoteEngine: RemoteInferenceEngine = RemoteInferenceEngine(),
    private val endpointRepository: EndpointRepository? = null,
    private val voiceSettingsRepository: VoiceSettingsRepository? = null,
    private val sttManager: SpeechToTextManager? = null,
    private val ttsManager: TextToSpeechManager? = null,
    private val context: Context? = null,
    private val getSettings: () -> InferenceSettings = { InferenceSettings.DEFAULT },
    private val onUpdateSettings: ((InferenceSettings) -> Unit)? = null,
    private val settingsRepository: AppSettingsRepository? = null,
    private val attachmentStore: AttachmentStore? = null,
    private val exportService: ConversationExportService = ConversationExportService(),
    private val dotRepository: DotRepository? = null,
    private val artifactRepository: ArtifactRepository? = null,
    private val taskManager: TaskManager? = null,
    private val toolRegistry: ToolRegistry? = null,
    private val toolAuditLogger: ToolAuditLogger? = null
) : ViewModel() {

    private val _activeDot = MutableStateFlow<DotProfile>(DotProfile.STARTER_PROFILES.first())
    val activeDot: StateFlow<DotProfile> = _activeDot.asStateFlow()

    private val _availableDots = MutableStateFlow<List<DotProfile>>(DotProfile.STARTER_PROFILES)
    val availableDots: StateFlow<List<DotProfile>> = _availableDots.asStateFlow()

    private val _activeTasks = MutableStateFlow<List<DotTask>>(emptyList())
    val activeTasks: StateFlow<List<DotTask>> = _activeTasks.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<ToolApprovalRequest>>(emptyList())
    val pendingApprovals: StateFlow<List<ToolApprovalRequest>> = _pendingApprovals.asStateFlow()

    private val _uiState = MutableStateFlow(
        ChatUiState(
            activeConversationId = chatRepository.activeConversationId.value,
            activeConversationTitle = chatRepository.getActiveSessionTitle(),
            activeDot = _activeDot.value,
            availableDots = _availableDots.value,
            selectedDotFilterId = _activeDot.value.id
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        // 1. Observe conversation messages with throttling for streaming
        viewModelScope.launch {
            var lastEmittedTime = 0L
            var pendingMessages: List<ChatMessage>? = null
            var flushJob: Job? = null

            chatRepository.messages.collect { messages ->
                val isStreaming = messages.lastOrNull()?.isStreaming == true
                if (!isStreaming) {
                    flushJob?.cancel()
                    pendingMessages = null
                    lastEmittedTime = System.currentTimeMillis()
                    _uiState.update { it.copy(messages = messages) }
                } else {
                    val now = System.currentTimeMillis()
                    val timeSinceLast = now - lastEmittedTime
                    if (timeSinceLast >= 50L) {
                        flushJob?.cancel()
                        pendingMessages = null
                        lastEmittedTime = now
                        _uiState.update { it.copy(messages = messages) }
                    } else {
                        pendingMessages = messages
                        if (flushJob == null || !flushJob!!.isActive) {
                            flushJob = launch {
                                delay(50L - timeSinceLast)
                                pendingMessages?.let { msgs ->
                                    lastEmittedTime = System.currentTimeMillis()
                                    _uiState.update { it.copy(messages = msgs) }
                                    pendingMessages = null
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1a. Observe conversation sessions
        viewModelScope.launch {
            chatRepository.conversations.collect { sessions ->
                val activeId = _uiState.value.activeConversationId
                val activeSession = sessions.find { it.id == activeId }
                _uiState.update {
                    it.copy(
                        conversations = sessions,
                        activeConversationTitle = chatRepository.getActiveSessionTitle(),
                        responseLanguage = activeSession?.responseLanguage ?: it.responseLanguage,
                        conversationSystemPrompt = activeSession?.systemPromptOverride ?: it.conversationSystemPrompt,
                        effectiveParams = resolveEffectiveParams(it.conversationTarget ?: it.activeTarget, activeSession)
                    )
                }
            }
        }

        // 1b. Observe active conversation ID
        viewModelScope.launch {
            chatRepository.activeConversationId.collect { id ->
                val session = chatRepository.getSession(id)
                val dotId = session?.dotId ?: DotProfile.BUILTIN_GENERAL_ID
                val matchedDot = _availableDots.value.find { it.id == dotId }
                    ?: DotProfile.STARTER_PROFILES.find { it.id == dotId }
                    ?: _activeDot.value
                if (matchedDot.id != _activeDot.value.id) {
                    _activeDot.value = matchedDot
                    dotRepository?.let { repo ->
                        launch {
                            repo.selectActiveDot(matchedDot.id)
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        activeConversationId = id,
                        activeConversationTitle = chatRepository.getActiveSessionTitle(),
                        responseLanguage = session?.responseLanguage ?: ResponseLanguage.AUTO,
                        conversationSystemPrompt = session?.systemPromptOverride,
                        effectiveParams = resolveEffectiveParams(it.conversationTarget ?: it.activeTarget, session, matchedDot),
                        activeDot = matchedDot
                    )
                }
            }
        }

        // 2. Observe local engine state
        viewModelScope.launch {
            engine.state.collect { state ->
                if (_uiState.value.activeTarget is InferenceTarget.Local || _uiState.value.activeTarget == null) {
                    _uiState.update { it.copy(engineState = state) }
                }
            }
        }

        // 3. Observe local loaded model
        viewModelScope.launch {
            engine.loadedModel.collect { model ->
                _uiState.update { current ->
                    val target = if (model != null && current.activeTarget == null) InferenceTarget.Local(model) else current.activeTarget
                    val isEffortSupported = ThinkingCapability.isEffortSelectorSupported(target, model, current.activeEndpoint)
                    val isThinkingSupported = ThinkingCapability.isThinkingSupported(target, model, current.activeEndpoint)
                    val caps = resolveCapabilitiesForTarget(target)
                    val params = resolveEffectiveParams(target)
                    current.copy(
                        loadedModel = model,
                        activeTarget = target,
                        capabilities = caps,
                        effectiveParams = params,
                        isThinkingEffortSupported = isEffortSupported,
                        isThinkingSupported = isThinkingSupported,
                        isThinkingEnabled = if (isThinkingSupported) params.thinkingEnabled else false,
                        thinkingEffort = params.thinkingEffort
                    )
                }
            }
        }

        // 4. Observe remote engine state
        viewModelScope.launch {
            remoteEngine.state.collect { state ->
                if (_uiState.value.activeTarget is InferenceTarget.Remote) {
                    _uiState.update { it.copy(engineState = state) }
                }
            }
        }

        // 5. Observe endpoint repository
        endpointRepository?.let { repo ->
            viewModelScope.launch {
                repo.activeEndpoint.collect { endpoint ->
                    _uiState.update { current ->
                        val target = if (endpoint != null && endpoint.isConfigured && current.activeTarget == null) {
                            InferenceTarget.Remote(endpoint, endpoint.selectedModel)
                        } else current.activeTarget
                        val isEffortSupported = ThinkingCapability.isEffortSelectorSupported(target, current.loadedModel, endpoint)
                        val isThinkingSupported = ThinkingCapability.isThinkingSupported(target, current.loadedModel, endpoint)
                        val caps = resolveCapabilitiesForTarget(target)
                        val params = resolveEffectiveParams(target)
                        current.copy(
                            activeEndpoint = endpoint,
                            activeTarget = target,
                            capabilities = caps,
                            effectiveParams = params,
                            isThinkingEffortSupported = isEffortSupported,
                            isThinkingSupported = isThinkingSupported,
                            isThinkingEnabled = if (isThinkingSupported) params.thinkingEnabled else false,
                            thinkingEffort = params.thinkingEffort
                        )
                    }
                }
            }
        }

        // 5b. Observe settings repository (profiles and settings)
        settingsRepository?.let { repo ->
            viewModelScope.launch {
                repo.settings.collect { _ ->
                    _uiState.update { current ->
                        current.copy(
                            effectiveParams = resolveEffectiveParams(current.conversationTarget ?: current.activeTarget)
                        )
                    }
                }
            }
            viewModelScope.launch {
                repo.profiles.collect { profiles ->
                    _uiState.update { current ->
                        current.copy(
                            profiles = profiles,
                            effectiveParams = resolveEffectiveParams(current.conversationTarget ?: current.activeTarget),
                            capabilities = resolveCapabilitiesForTarget(current.conversationTarget ?: current.activeTarget)
                        )
                    }
                }
            }
        }

        // 6. Observe voice settings
        voiceSettingsRepository?.let { voiceRepo ->
            viewModelScope.launch {
                voiceRepo.settings.collect { settings ->
                    _uiState.update { it.copy(autoSpeakEnabled = settings.autoSpeakResponses) }
                    ttsManager?.speechRate = settings.speechRate
                    ttsManager?.speechPitch = settings.speechPitch
                    ttsManager?.selectedVoiceName = settings.selectedVoiceName
                }
            }
        }

        // 7. Setup STT manager callbacks
        sttManager?.let { stt ->
            stt.onPartialResult = { partial ->
                _uiState.update { it.copy(inputText = partial, partialVoiceInput = partial) }
            }
            stt.onFinalResult = { finalTranscript ->
                _uiState.update { it.copy(inputText = finalTranscript, isListening = false, partialVoiceInput = "") }
                if (_uiState.value.isVoiceModeActive && finalTranscript.isNotBlank()) {
                    sendMessage()
                }
            }
            stt.onError = { errorMsg ->
                _uiState.update { it.copy(isListening = false, voiceState = VoiceState.Error(errorMsg)) }
            }
            stt.onListeningStarted = {
                _uiState.update { it.copy(isListening = true, voiceState = VoiceState.Listening()) }
            }
            stt.onListeningEnded = {
                _uiState.update { it.copy(isListening = false) }
            }
            viewModelScope.launch {
                stt.rmsDb.collect { rms ->
                    if (_uiState.value.voiceState.isListening) {
                        _uiState.update { it.copy(voiceState = VoiceState.Listening(rms)) }
                    }
                }
            }
        }

        // 8. Setup TTS manager observers
        ttsManager?.let { tts ->
            viewModelScope.launch {
                tts.isSpeaking.collect { speaking ->
                    _uiState.update { it.copy(isSpeaking = speaking) }
                    if (speaking) {
                        _uiState.update { it.copy(voiceState = VoiceState.Speaking(tts.currentSpeakingMessageId.value ?: "")) }
                    } else if (_uiState.value.voiceState.isSpeaking && !tts.isPaused.value) {
                        val nextState = if (_uiState.value.isVoiceModeActive) VoiceState.Listening() else VoiceState.Idle
                        _uiState.update { it.copy(voiceState = nextState, speakingMessageId = null) }
                        if (_uiState.value.isVoiceModeActive) {
                            sttManager?.startListening()
                        }
                    }
                }
            }
            viewModelScope.launch {
                tts.isPaused.collect { paused ->
                    _uiState.update { it.copy(isSpeechPaused = paused) }
                    if (paused && _uiState.value.isVoiceModeActive) {
                        _uiState.update { it.copy(voiceState = VoiceState.Paused) }
                    }
                }
            }
            viewModelScope.launch {
                tts.currentSpeakingMessageId.collect { id ->
                    _uiState.update { it.copy(speakingMessageId = id) }
                }
            }
        }

        // 9. Foreground service action receiver
        VoiceAssistantForegroundService.onServiceActionCallback = { action ->
            when (action) {
                VoiceAssistantForegroundService.ACTION_PAUSE -> pauseVoiceSession()
                VoiceAssistantForegroundService.ACTION_RESUME -> resumeVoiceSession()
                VoiceAssistantForegroundService.ACTION_STOP -> stopVoiceConversationMode()
            }
        }

        // 10. Observe DotRepository
        dotRepository?.let { repo ->
            viewModelScope.launch {
                repo.activeDot.collect { dot ->
                    val resolved = dot ?: DotProfile.STARTER_PROFILES.first()
                    _activeDot.value = resolved
                    _uiState.update { current ->
                        current.copy(
                            activeDot = resolved,
                            selectedDotFilterId = resolved.id,
                            effectiveParams = resolveEffectiveParams(
                                target = current.conversationTarget ?: current.activeTarget,
                                dot = resolved
                            )
                        )
                    }
                }
            }
            viewModelScope.launch {
                repo.dots.collect { dotsList ->
                    val list = if (dotsList.isNotEmpty()) dotsList else DotProfile.STARTER_PROFILES
                    _availableDots.value = list
                    _uiState.update { it.copy(availableDots = list) }
                }
            }
        }

        // 11. Observe TaskManager
        taskManager?.let { tm ->
            viewModelScope.launch {
                tm.pendingApprovals.collect { approvals ->
                    val pending = approvals.filter { it.status == ApprovalStatus.PENDING }
                    _pendingApprovals.value = pending
                    _uiState.update { it.copy(pendingApprovals = pending) }
                }
            }

            viewModelScope.launch {
                tm.tasks.collect { allTasks ->
                    val active = allTasks.filter { !it.isTerminal }
                    _activeTasks.value = active
                    _uiState.update { it.copy(activeTasks = active) }
                }
            }
        }
    }

    // --- Precedence & Settings Resolution ---

    fun getModelKey(target: InferenceTarget? = _uiState.value.conversationTarget ?: _uiState.value.activeTarget): String? {
        return when (target) {
            is InferenceTarget.Local -> {
                val path = target.model.filePath
                if (path.isNotBlank()) AppSettingsRepository.modelKeyLocal(path)
                else "local:${target.model.fileName}"
            }
            is InferenceTarget.Remote -> {
                AppSettingsRepository.modelKeyRemote(target.endpoint.id, target.modelId)
            }
            null -> {
                val loaded = _uiState.value.loadedModel
                if (loaded != null && loaded.filePath.isNotBlank()) {
                    AppSettingsRepository.modelKeyLocal(loaded.filePath)
                } else {
                    val endpoint = _uiState.value.activeEndpoint
                    if (endpoint != null && endpoint.selectedModel.isNotBlank()) {
                        AppSettingsRepository.modelKeyRemote(endpoint.id, endpoint.selectedModel)
                    } else null
                }
            }
        }
    }

    fun resolveEffectiveParams(
        target: InferenceTarget? = _uiState.value.conversationTarget ?: _uiState.value.activeTarget,
        session: ConversationSession? = chatRepository.getSession(_uiState.value.activeConversationId),
        dot: DotProfile? = _activeDot.value
    ): GenerationParams {
        val global = settingsRepository?.settings?.value?.toGenerationParams()
            ?: getSettings().toGenerationParams()

        val key = getModelKey(target)
        val profileParams = if (key != null) settingsRepository?.profileFor(key)?.params else null

        val base = profileParams ?: global
        var effective = session?.paramOverrides ?: base

        val activeDotProfile = dot ?: _activeDot.value
        val pref = activeDotProfile.modelPreference
        if (pref != null) {
            if (session?.paramOverrides == null && pref.temperatureOverride != null) {
                effective = effective.copy(temperature = pref.temperatureOverride.toDouble())
            }
            if (session?.paramOverrides == null && pref.topPOverride != null) {
                effective = effective.copy(topP = pref.topPOverride.toDouble())
            }
        }

        if (!session?.systemPromptOverride.isNullOrBlank()) {
            effective = effective.copy(systemPrompt = session!!.systemPromptOverride!!)
        } else {
            val dotPrompt = DotPromptBuilder.buildSystemPrompt(activeDotProfile, emptyList())
            if (dotPrompt.isNotBlank()) {
                effective = effective.copy(systemPrompt = dotPrompt)
            } else if (effective.systemPrompt.isBlank()) {
                effective = effective.copy(systemPrompt = base.systemPrompt)
            }
        }

        if (session?.responseLanguage != null && session.responseLanguage != ResponseLanguage.AUTO) {
            effective = effective.copy(language = session.responseLanguage)
        }

        return effective
    }

    fun resolveCapabilitiesForTarget(target: InferenceTarget? = _uiState.value.conversationTarget ?: _uiState.value.activeTarget): ModelCapabilities {
        if (target == null) {
            val loaded = _uiState.value.loadedModel
            if (loaded != null) {
                val base = loaded.capabilities ?: ModelCapabilitiesResolver.fromLocal(loaded)
                val key = AppSettingsRepository.modelKeyLocal(loaded.filePath)
                val profile = settingsRepository?.profileFor(key)
                return ModelCapabilitiesResolver.applyOverrides(base, profile?.capabilitiesOverride)
            }
            val endpoint = _uiState.value.activeEndpoint
            if (endpoint != null && endpoint.selectedModel.isNotBlank()) {
                val base = ModelCapabilitiesResolver.fromRemote(endpoint.selectedModel)
                val key = AppSettingsRepository.modelKeyRemote(endpoint.id, endpoint.selectedModel)
                val profile = settingsRepository?.profileFor(key)
                return ModelCapabilitiesResolver.applyOverrides(base, profile?.capabilitiesOverride)
            }
            return ModelCapabilities.FALLBACK
        }
        val base = when (target) {
            is InferenceTarget.Local -> target.model.capabilities ?: ModelCapabilitiesResolver.fromLocal(target.model)
            is InferenceTarget.Remote -> ModelCapabilitiesResolver.fromRemote(target.modelId)
        }
        val key = getModelKey(target)
        val profile = if (key != null) settingsRepository?.profileFor(key) else null
        return ModelCapabilitiesResolver.applyOverrides(base, profile?.capabilitiesOverride)
    }

    fun resolveLanguage(userText: String): ResponseLanguage {
        val currentSetting = _uiState.value.responseLanguage
        if (currentSetting != ResponseLanguage.AUTO) return currentSetting

        val sessionLanguage = chatRepository.getSession(_uiState.value.activeConversationId)?.responseLanguage
        if (sessionLanguage != null && sessionLanguage != ResponseLanguage.AUTO) return sessionLanguage

        val paramsLanguage = _uiState.value.effectiveParams.language
        if (paramsLanguage != ResponseLanguage.AUTO) return paramsLanguage

        return ResponseLanguage.detectFromText(userText)
    }

    private data class ModelAttribution(val modelId: String, val modelName: String, val targetType: String)

    private fun resolveModelAttribution(target: InferenceTarget?): ModelAttribution {
        val targetType = when {
            target is InferenceTarget.Local -> "local"
            target is InferenceTarget.Remote -> "remote"
            _uiState.value.loadedModel != null -> "local"
            else -> "remote"
        }
        val modelId = when (target) {
            is InferenceTarget.Local -> target.model.fileName.ifBlank { target.model.displayName }
            is InferenceTarget.Remote -> target.modelId
            else -> _uiState.value.loadedModel?.fileName ?: _uiState.value.activeEndpoint?.selectedModel ?: ""
        }
        val modelName = when (target) {
            is InferenceTarget.Local -> target.model.displayName
            is InferenceTarget.Remote -> target.modelId
            else -> _uiState.value.loadedModel?.displayName ?: _uiState.value.activeEndpoint?.selectedModel ?: ""
        }
        return ModelAttribution(modelId, modelName, targetType)
    }

    // --- UI State Modifiers & Dialogs ---

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleThinkingMode() {
        val newThinking = !_uiState.value.isThinkingEnabled
        val newParams = _uiState.value.effectiveParams.copy(thinkingEnabled = newThinking)
        _uiState.update {
            it.copy(
                isThinkingEnabled = newThinking,
                effectiveParams = newParams
            )
        }
    }

    fun setThinkingEffort(effort: ThinkingEffort) {
        val newParams = _uiState.value.effectiveParams.copy(thinkingEffort = effort)
        _uiState.update {
            it.copy(
                thinkingEffort = effort,
                effectiveParams = newParams
            )
        }
    }

    fun showModelSettingsSheet(visible: Boolean = true) {
        _uiState.update { it.copy(isModelSettingsSheetVisible = visible) }
    }

    fun hideModelSettingsSheet() {
        showModelSettingsSheet(false)
    }

    fun showSystemPromptDialog(visible: Boolean = true) {
        _uiState.update { it.copy(isSystemPromptDialogVisible = visible) }
    }

    fun hideSystemPromptDialog() {
        showSystemPromptDialog(false)
    }

    fun showLanguageSelector(visible: Boolean = true) {
        _uiState.update { it.copy(isLanguageSelectorVisible = visible) }
    }

    fun hideLanguageSelector() {
        showLanguageSelector(false)
    }

    fun setResponseLanguage(language: ResponseLanguage) {
        val activeId = _uiState.value.activeConversationId
        chatRepository.setConversationLanguage(activeId, language)
        _uiState.update {
            it.copy(
                responseLanguage = language,
                effectiveParams = resolveEffectiveParams().copy(language = language)
            )
        }
    }

    fun setConversationSystemPrompt(prompt: String?) {
        val activeId = _uiState.value.activeConversationId
        chatRepository.setConversationSystemPrompt(activeId, prompt)
        _uiState.update {
            it.copy(
                conversationSystemPrompt = prompt,
                effectiveParams = resolveEffectiveParams()
            )
        }
    }

    fun setConversationParams(params: GenerationParams?) {
        val activeId = _uiState.value.activeConversationId
        chatRepository.setConversationParams(activeId, params)
        _uiState.update {
            it.copy(
                effectiveParams = resolveEffectiveParams()
            )
        }
    }

    fun resetConversationParams() {
        setConversationParams(null)
    }

    fun reloadActiveModel() {
        val model = _uiState.value.loadedModel ?: return
        val params = _uiState.value.effectiveParams
        viewModelScope.launch {
            engine.reload(params)
            _uiState.update { it.copy(engineState = engine.state.value) }
        }
    }

    // --- Attachment Management ---

    fun addPendingAttachment(attachment: Attachment) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
    }

    fun addAttachmentFromUri(uri: android.net.Uri, context: Context) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                var fileName = "attachment_${System.currentTimeMillis()}"
                var sizeBytes = 0L
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }

                val attachment = if (attachmentStore != null) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        attachmentStore.saveAttachment(fileName, mimeType, sizeBytes, stream)
                    }
                } else {
                    Attachment(
                        uri = uri.toString(),
                        fileName = fileName,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        kind = if (mimeType.startsWith("image/")) com.mobileai.assistant.domain.model.AttachmentKind.IMAGE
                               else com.mobileai.assistant.domain.model.AttachmentKind.DOCUMENT
                    )
                }
                if (attachment != null) {
                    addPendingAttachment(attachment)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load attachment: ${e.message}") }
            }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { a -> a.id == attachmentId }) }
    }

    fun clearPendingAttachments() {
        _uiState.update { it.copy(pendingAttachments = emptyList()) }
    }

    // --- Target Selection & Model Switching ---

    fun switchTarget(target: InferenceTarget) {
        val activeId = _uiState.value.activeConversationId
        val targetId = targetToId(target)
        chatRepository.setConversationTarget(activeId, targetId)

        val effectiveCaps = resolveCapabilitiesForTarget(target)
        val oldParams = _uiState.value.effectiveParams
        val newParams = resolveEffectiveParams(target)

        when (target) {
            is InferenceTarget.Local -> {
                if (engine.loadedModel.value?.filePath != target.model.filePath) {
                    viewModelScope.launch {
                        engine.loadModel(target.model, newParams)
                    }
                } else if (engine.requiresReload(oldParams, newParams)) {
                    viewModelScope.launch {
                        engine.reload(newParams)
                    }
                }
                _uiState.update { it.copy(engineState = engine.state.value, loadedModel = target.model) }
            }
            is InferenceTarget.Remote -> {
                remoteEngine.setEndpoint(target.endpoint)
                _uiState.update { it.copy(engineState = remoteEngine.state.value, activeEndpoint = target.endpoint) }
            }
        }

        val isEffortSupported = effectiveCaps.reasoning == ReasoningSupport.EFFORT
        val isThinkingSupported = effectiveCaps.reasoning != ReasoningSupport.NONE

        _uiState.update {
            it.copy(
                conversationTarget = target,
                activeTarget = target,
                capabilities = effectiveCaps,
                effectiveParams = newParams,
                isThinkingSupported = isThinkingSupported,
                isThinkingEffortSupported = isEffortSupported,
                isThinkingEnabled = if (isThinkingSupported) newParams.thinkingEnabled else false,
                thinkingEffort = newParams.thinkingEffort
            )
        }
    }

    fun selectTarget(target: InferenceTarget) {
        switchTarget(target)
    }

    // --- Multi-Conversation Management ---

    fun createNewChat(dotId: String? = _activeDot.value.id) {
        stopGeneration()
        interruptSpeech()
        val session = chatRepository.createNewSession(title = "New Chat", dotId = dotId)
        val target = _uiState.value.activeTarget
        val effectiveCaps = resolveCapabilitiesForTarget(target)
        val effectiveParams = resolveEffectiveParams(target, session, _activeDot.value)

        _uiState.update {
            it.copy(
                inputText = "",
                pendingAttachments = emptyList(),
                activeConversationId = session.id,
                activeConversationTitle = session.title,
                conversationTarget = target,
                capabilities = effectiveCaps,
                effectiveParams = effectiveParams,
                responseLanguage = ResponseLanguage.AUTO,
                conversationSystemPrompt = null,
                errorMessage = null
            )
        }
    }

    fun selectConversation(sessionId: String) {
        val session = chatRepository.getSession(sessionId)
        val dotId = session?.dotId ?: DotProfile.BUILTIN_GENERAL_ID
        val matchedDot = _availableDots.value.find { it.id == dotId }
            ?: DotProfile.STARTER_PROFILES.find { it.id == dotId }
            ?: _activeDot.value

        if (sessionId == _uiState.value.activeConversationId && matchedDot.id == _activeDot.value.id) return
        stopGeneration()
        interruptSpeech()
        chatRepository.selectSession(sessionId)

        if (matchedDot.id != _activeDot.value.id) {
            _activeDot.value = matchedDot
            dotRepository?.let { repo ->
                viewModelScope.launch {
                    repo.selectActiveDot(matchedDot.id)
                }
            }
        }

        val target = resolveTargetFromId(session?.targetId) ?: _uiState.value.activeTarget
        val effectiveCaps = resolveCapabilitiesForTarget(target)
        val effectiveParams = resolveEffectiveParams(target, session, matchedDot)

        _uiState.update {
            it.copy(
                inputText = "",
                pendingAttachments = emptyList(),
                activeConversationId = sessionId,
                activeConversationTitle = chatRepository.getActiveSessionTitle(),
                conversationTarget = target,
                activeTarget = target ?: it.activeTarget,
                capabilities = effectiveCaps,
                effectiveParams = effectiveParams,
                responseLanguage = session?.responseLanguage ?: ResponseLanguage.AUTO,
                conversationSystemPrompt = session?.systemPromptOverride,
                errorMessage = null,
                activeDot = matchedDot
            )
        }
    }

    // --- Dot Management & Filtering ---

    fun selectDot(dotId: String) {
        viewModelScope.launch {
            dotRepository?.selectActiveDot(dotId)
            val targetDot = _availableDots.value.find { it.id == dotId }
                ?: dotRepository?.getDotById(dotId)
                ?: DotProfile.STARTER_PROFILES.find { it.id == dotId }
                ?: return@launch

            _activeDot.value = targetDot
            _uiState.update { current ->
                current.copy(
                    activeDot = targetDot,
                    selectedDotFilterId = targetDot.id,
                    effectiveParams = resolveEffectiveParams(
                        target = current.conversationTarget ?: current.activeTarget,
                        dot = targetDot
                    )
                )
            }
            ensureActiveConversationMatchesDot(targetDot.id)
        }
    }

    fun setDotFilter(dotId: String?) {
        _uiState.update { it.copy(selectedDotFilterId = dotId) }
    }

    fun createCustomDot(
        name: String,
        role: String,
        description: String,
        systemPrompt: String,
        avatarPreset: DotAvatarPreset = DotAvatarPreset.ROBOT,
        colorHex: String = "#4F46E5"
    ) {
        val newDot = DotProfile(
            name = name.trim(),
            role = role.trim(),
            description = description.trim(),
            systemPrompt = systemPrompt.trim(),
            avatarPreset = avatarPreset,
            colorHex = colorHex,
            isBuiltIn = false
        )
        viewModelScope.launch {
            dotRepository?.createDot(newDot)
            selectDot(newDot.id)
        }
    }

    fun deleteCustomDot(dotId: String) {
        viewModelScope.launch {
            dotRepository?.deleteDot(dotId)
            if (_activeDot.value.id == dotId) {
                selectDot(DotProfile.BUILTIN_GENERAL_ID)
            }
        }
    }

    private fun ensureActiveConversationMatchesDot(targetDotId: String) {
        val currentSession = chatRepository.getSession(_uiState.value.activeConversationId)
        val currentMatches = if (targetDotId == DotProfile.BUILTIN_GENERAL_ID) {
            currentSession?.dotId == DotProfile.BUILTIN_GENERAL_ID || currentSession?.dotId.isNullOrBlank()
        } else {
            currentSession?.dotId == targetDotId
        }
        if (currentMatches) return

        val matchingSessions = chatRepository.conversations.value.filter {
            if (targetDotId == DotProfile.BUILTIN_GENERAL_ID) {
                it.dotId == DotProfile.BUILTIN_GENERAL_ID || it.dotId.isNullOrBlank()
            } else {
                it.dotId == targetDotId
            }
        }.sortedByDescending { it.updatedAt }

        if (matchingSessions.isNotEmpty()) {
            selectConversation(matchingSessions.first().id)
        } else {
            createNewChat(dotId = targetDotId)
        }
    }

    // --- Task & Sensitive Tool Approval Actions ---

    fun toggleTaskProgressExpanded() {
        _uiState.update { it.copy(isTaskProgressExpanded = !it.isTaskProgressExpanded) }
    }

    fun setTaskProgressExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isTaskProgressExpanded = expanded) }
    }

    fun approveToolCall(requestId: String) {
        handleApprovalDecision(requestId, ToolApprovalDecision.APPROVED)
    }

    fun alwaysAllowToolCall(requestId: String) {
        handleApprovalDecision(requestId, ToolApprovalDecision.ALWAYS_ALLOWED)
    }

    fun rejectToolCall(requestId: String, reason: String? = null) {
        handleApprovalDecision(requestId, ToolApprovalDecision.REJECTED, reason)
    }

    fun handleApprovalDecision(
        requestId: String,
        decision: ToolApprovalDecision,
        rejectionReason: String? = null
    ) {
        val tm = taskManager ?: return
        viewModelScope.launch {
            val updatedTask = tm.handleApprovalDecision(requestId, decision, rejectionReason)
            if (decision != ToolApprovalDecision.REJECTED &&
                updatedTask != null &&
                updatedTask.status == TaskStatus.RUNNING
            ) {
                tm.executeTask(updatedTask.id)
            }
        }
    }

    fun pauseTask(taskId: String) {
        val tm = taskManager ?: return
        viewModelScope.launch {
            tm.pauseTask(taskId)
        }
    }

    fun resumeTask(taskId: String) {
        val tm = taskManager ?: return
        viewModelScope.launch {
            tm.resumeTask(taskId)
        }
    }

    fun cancelTask(taskId: String, reason: String? = null) {
        val tm = taskManager ?: return
        viewModelScope.launch {
            tm.cancelTask(taskId, reason)
        }
    }

    private fun targetToId(target: InferenceTarget): String = when (target) {
        is InferenceTarget.Local -> "local:${target.model.displayName}"
        is InferenceTarget.Remote -> "remote:${target.endpoint.id}:${target.modelId}"
    }

    private fun resolveTargetFromId(targetId: String?): InferenceTarget? {
        if (targetId == null) return null
        if (targetId.startsWith("local:")) {
            val name = targetId.removePrefix("local:")
            val loaded = engine.loadedModel.value
            if (loaded != null && (loaded.displayName == name || loaded.fileName == name)) {
                return InferenceTarget.Local(loaded)
            }
        } else if (targetId.startsWith("remote:")) {
            val parts = targetId.removePrefix("remote:").split(":")
            if (parts.size >= 2) {
                val endpointId = parts[0]
                val modelId = parts.drop(1).joinToString(":")
                val endpoint = endpointRepository?.endpoints?.value?.find { it.id == endpointId }
                    ?: _uiState.value.activeEndpoint?.takeIf { it.id == endpointId }
                if (endpoint != null) {
                    return InferenceTarget.Remote(endpoint, modelId)
                }
            }
        }
        return null
    }

    fun renameConversation(sessionId: String, newTitle: String) {
        chatRepository.renameSession(sessionId, newTitle)
        _uiState.update {
            it.copy(activeConversationTitle = chatRepository.getActiveSessionTitle())
        }
    }

    fun togglePinConversation(sessionId: String) {
        val session = chatRepository.getSession(sessionId)
            ?: chatRepository.conversations.value.find { it.id == sessionId }
            ?: return
        chatRepository.setConversationPinned(sessionId, !session.pinned)
    }

    fun setConversationPinned(sessionId: String, pinned: Boolean) {
        chatRepository.setConversationPinned(sessionId, pinned)
    }

    fun deleteConversation(sessionId: String) {
        val wasActive = sessionId == _uiState.value.activeConversationId
        if (wasActive) {
            stopGeneration()
            interruptSpeech()
        }
        chatRepository.deleteSession(sessionId)
        _uiState.update {
            it.copy(
                inputText = if (wasActive) "" else it.inputText,
                activeConversationId = chatRepository.activeConversationId.value,
                activeConversationTitle = chatRepository.getActiveSessionTitle()
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // --- Message Actions: Send, Edit, Regenerate, Continue ---

    fun sendMessage(text: String = _uiState.value.inputText) {
        val prompt = text.trim()
        val attachments = _uiState.value.pendingAttachments
        if (prompt.isBlank() && attachments.isEmpty()) return
        if (!_uiState.value.isModelAvailable || _uiState.value.isGenerating) return

        interruptSpeech()
        _uiState.update { it.copy(inputText = "", pendingAttachments = emptyList(), errorMessage = null) }

        val resolvedLang = resolveLanguage(prompt)
        val userMsg = ChatMessage(
            content = prompt,
            isUser = true,
            attachments = attachments,
            language = resolvedLang
        )
        chatRepository.addMessage(userMsg)

        val target = _uiState.value.conversationTarget ?: _uiState.value.activeTarget
        val attribution = resolveModelAttribution(target)

        val assistantMsg = ChatMessage(
            content = "",
            isUser = false,
            isStreaming = true,
            modelId = attribution.modelId,
            modelName = attribution.modelName,
            targetType = attribution.targetType,
            language = resolvedLang
        )
        chatRepository.addMessage(assistantMsg)

        val currentParams = resolveEffectiveParams()
        startGeneration(
            messages = chatRepository.messages.value,
            params = currentParams,
            isContinue = false,
            continueTargetMessageId = assistantMsg.id
        )
    }

    fun handleGenUiEvent(messageId: String, event: GenUiEvent) {
        if (_uiState.value.isGenerating) return

        when (event) {
            is GenUiEvent.FormSubmit -> {
                // 1. Mark origin message as submitted to lock controls (idempotency) and save values
                chatRepository.updateMessage(messageId) {
                    it.copy(
                        genUiState = "SUBMITTED",
                        genUiSubmittedValues = event.fieldValues
                    )
                }

                // 2. Synthesize conversational user turn summarizing submission
                val summary = event.fieldValues.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                val formattedTurn = "Submitted form '${event.formId}':\n$summary"

                // 3. Dispatch user message & trigger model response
                val userMsg = ChatMessage(
                    content = formattedTurn,
                    isUser = true,
                    language = _uiState.value.responseLanguage
                )
                chatRepository.addMessage(userMsg)

                val target = _uiState.value.conversationTarget ?: _uiState.value.activeTarget
                val attribution = resolveModelAttribution(target)
                val assistantMsg = ChatMessage(
                    content = "",
                    isUser = false,
                    isStreaming = true,
                    modelId = attribution.modelId,
                    modelName = attribution.modelName,
                    targetType = attribution.targetType,
                    language = _uiState.value.responseLanguage
                )
                chatRepository.addMessage(assistantMsg)

                startGeneration(
                    messages = chatRepository.messages.value,
                    params = resolveEffectiveParams(),
                    isContinue = false,
                    continueTargetMessageId = assistantMsg.id
                )
            }

            is GenUiEvent.ButtonClick -> {
                val payloadStr = if (event.payload.isNotEmpty()) {
                    " with " + event.payload.entries.joinToString(", ") { "${it.key}=${it.value}" }
                } else ""
                val formattedTurn = "Selected action '${event.actionId}'$payloadStr"

                val userMsg = ChatMessage(
                    content = formattedTurn,
                    isUser = true,
                    language = _uiState.value.responseLanguage
                )
                chatRepository.addMessage(userMsg)

                val target = _uiState.value.conversationTarget ?: _uiState.value.activeTarget
                val attribution = resolveModelAttribution(target)
                val assistantMsg = ChatMessage(
                    content = "",
                    isUser = false,
                    isStreaming = true,
                    modelId = attribution.modelId,
                    modelName = attribution.modelName,
                    targetType = attribution.targetType,
                    language = _uiState.value.responseLanguage
                )
                chatRepository.addMessage(assistantMsg)

                startGeneration(
                    messages = chatRepository.messages.value,
                    params = resolveEffectiveParams(),
                    isContinue = false,
                    continueTargetMessageId = assistantMsg.id
                )
            }

            is GenUiEvent.SelectionChange -> {
                val labelStr = event.selectedLabel?.let { " ($it)" } ?: ""
                val formattedTurn = "Selected '${event.selectedKey}'$labelStr in '${event.componentId}'"
                sendMessage(formattedTurn)
            }

            is GenUiEvent.DiagramNodeClick -> {
                val labelStr = event.nodeLabel?.let { " ('$it')" } ?: ""
                val formattedTurn = "Tell me more about step '${event.nodeId}'$labelStr in diagram '${event.diagramId}'."
                sendMessage(formattedTurn)
            }
        }
    }

    fun editAndResendMessage(messageId: String, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isBlank() || !_uiState.value.isModelAvailable || _uiState.value.isGenerating) return

        stopGeneration()
        interruptSpeech()

        chatRepository.removeMessagesFrom(messageId)

        val resolvedLang = resolveLanguage(trimmed)
        val userMsg = ChatMessage(content = trimmed, isUser = true, language = resolvedLang)
        chatRepository.addMessage(userMsg)

        val target = _uiState.value.conversationTarget ?: _uiState.value.activeTarget
        val attribution = resolveModelAttribution(target)

        val assistantMsg = ChatMessage(
            content = "",
            isUser = false,
            isStreaming = true,
            modelId = attribution.modelId,
            modelName = attribution.modelName,
            targetType = attribution.targetType,
            language = resolvedLang
        )
        chatRepository.addMessage(assistantMsg)

        val currentParams = resolveEffectiveParams()
        startGeneration(
            messages = chatRepository.messages.value,
            params = currentParams,
            isContinue = false,
            continueTargetMessageId = assistantMsg.id
        )
    }

    fun regenerateResponse(assistantMessageId: String) {
        if (!_uiState.value.isModelAvailable || _uiState.value.isGenerating) return

        val currentMessages = chatRepository.messages.value
        val index = currentMessages.indexOfFirst { it.id == assistantMessageId }
        if (index == -1) return

        stopGeneration()
        interruptSpeech()

        chatRepository.removeMessagesFrom(assistantMessageId)

        val target = _uiState.value.conversationTarget ?: _uiState.value.activeTarget
        val attribution = resolveModelAttribution(target)

        val lastUser = chatRepository.messages.value.lastOrNull { it.isUser }
        val resolvedLang = resolveLanguage(lastUser?.content.orEmpty())

        val assistantMsg = ChatMessage(
            content = "",
            isUser = false,
            isStreaming = true,
            modelId = attribution.modelId,
            modelName = attribution.modelName,
            targetType = attribution.targetType,
            language = resolvedLang
        )
        chatRepository.addMessage(assistantMsg)

        val currentParams = resolveEffectiveParams()
        startGeneration(
            messages = chatRepository.messages.value,
            params = currentParams,
            isContinue = false,
            continueTargetMessageId = assistantMsg.id
        )
    }

    fun continueGeneration(messageId: String) {
        if (!_uiState.value.isModelAvailable || _uiState.value.isGenerating) return

        val currentMessages = chatRepository.messages.value
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index == -1) return
        val targetMessage = currentMessages[index]
        if (targetMessage.isUser) return

        stopGeneration()
        interruptSpeech()

        if (index < currentMessages.lastIndex) {
            val nextMessage = currentMessages[index + 1]
            chatRepository.removeMessagesFrom(nextMessage.id)
        }

        chatRepository.setMessageIncomplete(messageId, false, null)
        chatRepository.updateMessage(messageId) {
            it.copy(
                isStreaming = true,
                isThinking = false,
                isIncomplete = false,
                stopReason = null
            )
        }

        val messagesForRequest = chatRepository.messages.value.take(index + 1)
        val effectiveParams = resolveEffectiveParams()

        startGeneration(
            messages = messagesForRequest,
            params = effectiveParams,
            isContinue = true,
            continueTargetMessageId = messageId
        )
    }

    fun startGeneration(
        messages: List<ChatMessage>,
        params: GenerationParams,
        isContinue: Boolean = false,
        continueTargetMessageId: String? = null
    ) {
        val target = _uiState.value.conversationTarget ?: _uiState.value.activeTarget
        val isRemote = target is InferenceTarget.Remote || (target == null && _uiState.value.activeEndpoint != null)
        val endpoint = (target as? InferenceTarget.Remote)?.endpoint ?: _uiState.value.activeEndpoint
        val attribution = resolveModelAttribution(target)

        val lastUserText = messages.lastOrNull { it.isUser }?.content.orEmpty()
        val resolvedLanguage = when {
            _uiState.value.responseLanguage != ResponseLanguage.AUTO -> _uiState.value.responseLanguage
            params.language != ResponseLanguage.AUTO -> params.language
            else -> ResponseLanguage.detectFromText(lastUserText)
        }

        val effectiveParams = params.copy(language = resolvedLanguage)
        val effectiveCapabilities = _uiState.value.capabilities

        val continueMessages = if (isContinue) {
            if (isRemote) {
                messages + ChatMessage(content = "Continue", isUser = true)
            } else {
                messages.mapIndexed { idx, m ->
                    if (idx == messages.lastIndex && !m.isUser) m.copy(isIncomplete = true) else m
                }
            }
        } else {
            messages
        }

        val systemPromptWithGenUi = if (effectiveParams.systemPrompt.contains("```genui")) {
            effectiveParams.systemPrompt
        } else if (effectiveParams.systemPrompt.isNotBlank()) {
            "${effectiveParams.systemPrompt}\n\n${GenUiPromptSpec.SYSTEM_INSTRUCTION}"
        } else {
            GenUiPromptSpec.SYSTEM_INSTRUCTION
        }
        val generationParams = effectiveParams.copy(systemPrompt = systemPromptWithGenUi)

        val request = GenerationRequest(
            messages = continueMessages,
            params = generationParams,
            capabilities = effectiveCapabilities,
            modelId = attribution.modelId,
            modelName = attribution.modelName,
            isRemote = isRemote
        )

        val lastMsg = chatRepository.messages.value.lastOrNull()
        val assistantMsgId = if (isContinue && continueTargetMessageId != null) {
            continueTargetMessageId
        } else if (lastMsg != null && !lastMsg.isUser && lastMsg.isStreaming) {
            lastMsg.id
        } else {
            val newAssistantMsg = ChatMessage(
                content = "",
                isUser = false,
                isStreaming = true,
                modelId = attribution.modelId,
                modelName = attribution.modelName,
                targetType = attribution.targetType,
                language = resolvedLanguage
            )
            chatRepository.addMessage(newAssistantMsg)
            newAssistantMsg.id
        }

        chatRepository.updateMessage(assistantMsgId) {
            it.copy(
                modelId = attribution.modelId,
                modelName = attribution.modelName,
                targetType = attribution.targetType,
                language = resolvedLanguage,
                isStreaming = true
            )
        }

        _uiState.update {
            it.copy(
                isGenerating = true,
                isContinuing = isContinue,
                errorMessage = null,
                voiceState = VoiceState.Generating
            )
        }

        generationJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var firstTokenTime: Long? = null
            var tokenCount = 0
            var latestMetrics: RuntimeMetrics? = null

            try {
                val eventFlow = if (isRemote && endpoint != null) {
                    remoteEngine.generate(request, endpoint, attribution.modelId)
                } else {
                    generateResponseUseCase?.invoke(request) ?: engine.generate(request)
                }

                eventFlow
                    .onCompletion { cause ->
                        if (cause == null) {
                            finalizeResponse(
                                messageId = assistantMsgId,
                                startTime = startTime,
                                firstTokenTime = firstTokenTime,
                                tokenCount = tokenCount,
                                latestMetrics = latestMetrics,
                                isRemote = isRemote
                            )
                        }
                    }
                    .catch { error ->
                        handleGenerationError(error, assistantMsgId)
                    }
                    .collect { event ->
                        when (event) {
                            is GenerationEvent.Delta -> {
                                if (firstTokenTime == null) {
                                    firstTokenTime = System.currentTimeMillis()
                                }
                                tokenCount++
                                if (continueTargetMessageId != null) {
                                    chatRepository.updateMessage(assistantMsgId) {
                                        it.copy(
                                            content = it.content + event.text,
                                            isStreaming = true,
                                            isThinking = false
                                        )
                                    }
                                } else {
                                    if (chatRepository.messages.value.lastOrNull()?.isThinking == true) {
                                        chatRepository.setLastMessageThinking(false)
                                    }
                                    chatRepository.appendToLastMessage(event.text)
                                }
                            }
                            is GenerationEvent.Reasoning -> {
                                if (firstTokenTime == null) {
                                    firstTokenTime = System.currentTimeMillis()
                                }
                                tokenCount++
                                if (continueTargetMessageId != null) {
                                    val currentReasoning = chatRepository.messages.value.find { it.id == assistantMsgId }?.reasoning ?: ""
                                    chatRepository.updateMessage(assistantMsgId) {
                                        it.copy(
                                            reasoning = currentReasoning + event.text,
                                            isStreaming = true,
                                            isThinking = true
                                        )
                                    }
                                } else {
                                    chatRepository.setLastMessageThinking(true)
                                    chatRepository.appendReasoningToLastMessage(event.text)
                                }
                            }
                            is GenerationEvent.Metrics -> {
                                latestMetrics = event.metrics
                                chatRepository.setMessageMetrics(assistantMsgId, event.metrics)
                            }
                        }
                    }
            } catch (e: CancellationException) {
                handleGenerationError(e, assistantMsgId)
            } catch (e: Exception) {
                handleGenerationError(e, assistantMsgId)
            }
        }
    }

    private fun finalizeResponse(
        messageId: String,
        startTime: Long,
        firstTokenTime: Long?,
        tokenCount: Int,
        latestMetrics: RuntimeMetrics?,
        isRemote: Boolean
    ) {
        _uiState.update { it.copy(isGenerating = false, isContinuing = false) }

        val totalTime = System.currentTimeMillis() - startTime
        val ttft = firstTokenTime?.let { it - startTime }
        val tps = if (totalTime > 0 && tokenCount > 0) (tokenCount.toDouble() / (totalTime / 1000.0)) else null

        val finalMetrics = latestMetrics?.copy(
            timeToFirstTokenMs = latestMetrics.timeToFirstTokenMs ?: ttft,
            totalDurationMs = latestMetrics.totalDurationMs ?: totalTime,
            completionTokens = if (latestMetrics.completionTokens > 0) latestMetrics.completionTokens else tokenCount,
            tokensPerSecond = latestMetrics.tokensPerSecond ?: tps,
            source = latestMetrics.source.ifBlank { if (isRemote) "REMOTE" else "LOCAL" }
        ) ?: RuntimeMetrics(
            timeToFirstTokenMs = ttft,
            totalDurationMs = totalTime,
            completionTokens = tokenCount,
            tokensPerSecond = tps,
            source = if (isRemote) "REMOTE" else "LOCAL"
        )

        chatRepository.setMessageMetrics(messageId, finalMetrics)

        val currentMsg = chatRepository.messages.value.find { it.id == messageId }
        val content = currentMsg?.content ?: ""
        chatRepository.updateMessage(messageId) {
            it.copy(
                isStreaming = false,
                isThinking = false,
                metrics = finalMetrics
            )
        }

        val cleanSpeech = TextSanitizer.cleanForSpeech(content)
        if ((_uiState.value.autoSpeakEnabled || _uiState.value.isVoiceModeActive) && cleanSpeech.isNotBlank() && currentMsg != null) {
            speakMessage(currentMsg.copy(content = content, metrics = finalMetrics))
        } else {
            val nextState = if (_uiState.value.isVoiceModeActive) VoiceState.Listening() else VoiceState.Idle
            _uiState.update { it.copy(voiceState = nextState) }
            if (_uiState.value.isVoiceModeActive) {
                sttManager?.startListening()
            }
        }
    }

    private fun handleGenerationError(error: Throwable, messageId: String) {
        if (error is CancellationException) {
            _uiState.update { it.copy(isGenerating = false, isContinuing = false, voiceState = VoiceState.Idle) }
            chatRepository.setMessageIncomplete(messageId, true, "stopped")
            chatRepository.updateMessage(messageId) {
                it.copy(
                    isStreaming = false,
                    isThinking = false,
                    isIncomplete = true,
                    stopReason = "stopped"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    isContinuing = false,
                    errorMessage = "Generation error: ${error.message}",
                    voiceState = VoiceState.Error(error.message ?: "Unknown error")
                )
            }
            chatRepository.updateMessage(messageId) {
                it.copy(
                    content = if (it.content.isBlank()) "Error: ${error.message}" else it.content,
                    isStreaming = false,
                    isThinking = false,
                    isError = true
                )
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        cancelGenerationUseCase?.invoke() ?: engine.cancelGeneration()
        remoteEngine.cancelGeneration()
        _uiState.update { it.copy(isGenerating = false, isContinuing = false, voiceState = VoiceState.Idle) }

        val lastMsg = chatRepository.messages.value.lastOrNull { !it.isUser }
        if (lastMsg != null) {
            chatRepository.setMessageIncomplete(lastMsg.id, true, "stopped")
            chatRepository.updateMessage(lastMsg.id) {
                it.copy(
                    isStreaming = false,
                    isThinking = false,
                    isIncomplete = true,
                    stopReason = "stopped"
                )
            }
        }
    }

    // --- Voice Operations ---

    fun startVoiceInput() {
        interruptSpeech()
        val languageTag = resolveLanguage("").takeIf { it != ResponseLanguage.AUTO }?.bcp47
        sttManager?.languageBcp47 = languageTag
        sttManager?.startListening(languageTag)
    }

    fun stopVoiceInput() {
        sttManager?.stopListening()
    }

    fun cancelVoiceInput() {
        sttManager?.cancel()
    }

    fun speakMessage(message: ChatMessage) {
        if (message.content.isBlank()) return
        if (message.id == _uiState.value.speakingMessageId && _uiState.value.isSpeechPaused) {
            resumeSpeech()
            return
        }
        interruptSpeech()
        val languageTag = (message.language ?: resolveLanguage(message.content))
            .resolve(message.content)
            .takeIf { it != ResponseLanguage.AUTO }
            ?.bcp47
        ttsManager?.speak(
            text = message.content,
            messageId = message.id,
            languageBcp47 = languageTag
        ) {
            _uiState.update { it.copy(speakingMessageId = null, isSpeaking = false, isSpeechPaused = false) }
        }
    }

    fun pauseSpeech() {
        ttsManager?.pause()
    }

    fun resumeSpeech() {
        ttsManager?.resume()
    }

    fun interruptSpeech() {
        ttsManager?.stop()
        _uiState.update { it.copy(speakingMessageId = null, isSpeaking = false, isSpeechPaused = false) }
    }

    fun startVoiceConversationMode() {
        _uiState.update { it.copy(isVoiceModeActive = true, voiceState = VoiceState.Listening()) }
        context?.let { ctx ->
            VoiceAssistantForegroundService.start(ctx, "Active voice conversation session")
        }
        val languageTag = resolveLanguage("").takeIf { it != ResponseLanguage.AUTO }?.bcp47
        sttManager?.languageBcp47 = languageTag
        sttManager?.startListening(languageTag)
    }

    fun stopVoiceConversationMode() {
        _uiState.update { it.copy(isVoiceModeActive = false, voiceState = VoiceState.Idle) }
        sttManager?.cancel()
        ttsManager?.stop()
        context?.let { ctx ->
            VoiceAssistantForegroundService.stop(ctx)
        }
    }

    fun pauseVoiceSession() {
        _uiState.update { it.copy(voiceState = VoiceState.Paused) }
        sttManager?.cancel()
        ttsManager?.stop()
    }

    fun resumeVoiceSession() {
        _uiState.update { it.copy(voiceState = VoiceState.Listening()) }
        val languageTag = resolveLanguage("").takeIf { it != ResponseLanguage.AUTO }?.bcp47
        sttManager?.languageBcp47 = languageTag
        sttManager?.startListening(languageTag)
    }

    fun clearChat() {
        stopGeneration()
        interruptSpeech()
        chatRepository.clearMessages()
        _uiState.update {
            it.copy(
                inputText = "",
                activeConversationTitle = "New Chat"
            )
        }
    }

    fun deleteMessage(messageId: String) {
        if (_uiState.value.isGenerating && _uiState.value.messages.lastOrNull()?.id == messageId) {
            stopGeneration()
        }
        if (_uiState.value.speakingMessageId == messageId) {
            interruptSpeech()
        }
        chatRepository.deleteMessage(messageId)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // --- Export Chat History ---

    fun exportMarkdown(sessionId: String = _uiState.value.activeConversationId): String {
        val session = chatRepository.getSession(sessionId)
            ?: chatRepository.conversations.value.find { it.id == sessionId }
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        val md = exportService.exportMarkdown(session)
        _uiState.update { it.copy(exportNotification = "Exported to Markdown") }
        return md
    }

    fun exportJson(sessionId: String = _uiState.value.activeConversationId): String {
        val session = chatRepository.getSession(sessionId)
            ?: chatRepository.conversations.value.find { it.id == sessionId }
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        val json = exportService.exportJson(session)
        _uiState.update { it.copy(exportNotification = "Exported to JSON") }
        return json
    }

    fun importSession(jsonString: String): Result<Unit> {
        return exportService.importJson(jsonString).map { importedSession ->
            chatRepository.importSession(importedSession)
            chatRepository.selectSession(importedSession.id)
            _uiState.update {
                it.copy(
                    activeConversationId = importedSession.id,
                    activeConversationTitle = importedSession.title,
                    exportNotification = "Imported conversation: ${importedSession.title}"
                )
            }
        }
    }

    fun clearExportNotification() {
        _uiState.update { it.copy(exportNotification = null) }
    }

    fun getFormattedChatExport(): String {
        val activeId = _uiState.value.activeConversationId
        return if (activeId.isNotBlank()) {
            try {
                exportMarkdown(activeId)
            } catch (_: Exception) {
                legacyExportFallback()
            }
        } else {
            legacyExportFallback()
        }
    }

    private fun legacyExportFallback(): String {
        val title = chatRepository.getActiveSessionTitle()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = sdf.format(Date())
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n")
        sb.append("*Exported on ").append(formattedDate).append(" from Mobile AI Assistant*\n\n")
        sb.append("---\n\n")
        for (msg in _uiState.value.messages) {
            val role = if (msg.isUser) "**User**" else "**Assistant**"
            val time = sdf.format(Date(msg.timestamp))
            sb.append(role).append(" (").append(time).append("):\n\n")
            if (!msg.reasoning.isNullOrBlank()) {
                sb.append("> **Reasoning / Thought Process:**\n")
                msg.reasoning.lines().forEach { line ->
                    sb.append("> ").append(line).append("\n")
                }
                sb.append("\n")
            }
            sb.append(msg.content).append("\n\n---\n\n")
        }
        return sb.toString()
    }

    fun getCurrentSystemPrompt(): String {
        return _uiState.value.conversationSystemPrompt
            ?: _uiState.value.effectiveParams.systemPrompt.ifBlank { getSettings().systemPrompt }
    }

    fun updateSystemPrompt(newPrompt: String) {
        setConversationSystemPrompt(newPrompt)
        val current = getSettings()
        val updated = current.copy(systemPrompt = newPrompt)
        onUpdateSettings?.invoke(updated)
    }

    override fun onCleared() {
        generationJob?.cancel()
        sttManager?.cancel()
        ttsManager?.release()
        context?.let { VoiceAssistantForegroundService.stop(it) }
        super.onCleared()
    }
}
