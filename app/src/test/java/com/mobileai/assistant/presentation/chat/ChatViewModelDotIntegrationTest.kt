package com.mobileai.assistant.presentation.chat

import com.mobileai.assistant.data.artifact.ArtifactRepositoryImpl
import com.mobileai.assistant.data.dot.DotRepositoryImpl
import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.domain.dot.DotAvatarPreset
import com.mobileai.assistant.domain.dot.DotModelPreference
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RuntimeMetrics
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.presentation.dot.parseThemeColor
import com.mobileai.assistant.presentation.dot.toIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelDotIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private lateinit var rootDir: File
    private lateinit var chatRepository: ChatRepositoryImpl
    private lateinit var settingsRepository: JsonFileSettingsRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var dotRepository: DotRepositoryImpl
    private lateinit var artifactRepository: ArtifactRepositoryImpl
    private lateinit var toolRegistry: DefaultToolRegistry
    private lateinit var auditLogger: ToolAuditLoggerImpl
    private lateinit var taskManager: TaskManagerImpl
    private lateinit var fakeEngine: FakeInferenceEngine
    private lateinit var testModel: ModelInfo

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("dot_chat_integration_test")
        attachmentStore = AttachmentStore(rootDir)
        chatRepository = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        settingsRepository = JsonFileSettingsRepository(rootDir)
        dotRepository = DotRepositoryImpl(rootDir)
        artifactRepository = ArtifactRepositoryImpl(rootDirOverride = rootDir)
        toolRegistry = DefaultToolRegistry()
        auditLogger = ToolAuditLoggerImpl(rootDir)
        taskManager = TaskManagerImpl(
            rootDirOverride = rootDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        testModel = ModelInfo(
            fileName = "integration-model.gguf",
            filePath = File(rootDir, "integration-model.gguf").absolutePath,
            fileSizeBytes = 4096L
        )
        fakeEngine = FakeInferenceEngine(testModel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChatViewModel {
        return ChatViewModel(
            engine = fakeEngine,
            chatRepository = chatRepository,
            settingsRepository = settingsRepository,
            attachmentStore = attachmentStore,
            exportService = ConversationExportService(),
            dotRepository = dotRepository,
            artifactRepository = artifactRepository,
            taskManager = taskManager,
            toolRegistry = toolRegistry,
            toolAuditLogger = auditLogger
        )
    }

    private fun getActiveSession(): com.mobileai.assistant.domain.model.ConversationSession? {
        return chatRepository.getSession(chatRepository.activeConversationId.value)
    }

    @Test
    fun testDefaultActiveDotAndAvailableDots() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. Default active Dot is builtin-general
        assertEquals(DotProfile.BUILTIN_GENERAL_ID, viewModel.activeDot.value.id)
        assertEquals("General Assistant", viewModel.uiState.value.activeDot.name)
        assertEquals(DotProfile.BUILTIN_GENERAL_ID, viewModel.uiState.value.selectedDotFilterId)

        // 2. All 4 built-in starter profiles available
        val available = viewModel.availableDots.value
        assertTrue(available.size >= 4)
        val availableIds = available.map { it.id }.toSet()
        assertTrue(availableIds.contains(DotProfile.BUILTIN_GENERAL_ID))
        assertTrue(availableIds.contains(DotProfile.BUILTIN_CODER_ID))
        assertTrue(availableIds.contains(DotProfile.BUILTIN_RESEARCHER_ID))
        assertTrue(availableIds.contains(DotProfile.BUILTIN_WRITER_ID))
    }

    @Test
    fun testSelectDotUpdatesActiveDotAndSynchronizesConversation() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Switch to Code Architect
        viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()

        assertEquals(DotProfile.BUILTIN_CODER_ID, viewModel.activeDot.value.id)
        assertEquals("Code Architect", viewModel.uiState.value.activeDot.name)
        assertEquals(DotProfile.BUILTIN_CODER_ID, viewModel.uiState.value.selectedDotFilterId)

        // Verify active conversation has been tagged with builtin-coder
        val activeSession = getActiveSession()
        assertNotNull(activeSession)
        assertEquals(DotProfile.BUILTIN_CODER_ID, activeSession?.dotId)
    }

    @Test
    fun testSelectConversationSyncsActiveDot() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Create a conversation belonging to Technical Writer
        val writerSession = chatRepository.createNewSession("Documentation Draft", dotId = DotProfile.BUILTIN_WRITER_ID)
        advanceUntilIdle()

        // Select the conversation
        viewModel.selectConversation(writerSession.id)
        advanceUntilIdle()

        assertEquals(DotProfile.BUILTIN_WRITER_ID, viewModel.activeDot.value.id)
        assertEquals("Technical Writer", viewModel.uiState.value.activeDot.name)
        assertEquals(writerSession.id, viewModel.uiState.value.activeConversationId)
    }

    @Test
    fun testCreateNewChatTagsSessionWithActiveDot() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Select Research Analyst
        viewModel.selectDot(DotProfile.BUILTIN_RESEARCHER_ID)
        advanceUntilIdle()

        viewModel.createNewChat()
        advanceUntilIdle()

        val activeSession = getActiveSession()
        assertNotNull(activeSession)
        assertEquals(DotProfile.BUILTIN_RESEARCHER_ID, activeSession?.dotId)

        // Explicitly create chat with another Dot
        viewModel.createNewChat(dotId = DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()

        val coderSession = getActiveSession()
        assertNotNull(coderSession)
        assertEquals(DotProfile.BUILTIN_CODER_ID, coderSession?.dotId)
    }

    @Test
    fun testFilteredConversationsBySelectedDotFilterId() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Create conversations with diverse Dot associations
        val sGeneral = chatRepository.createNewSession("General Talk", dotId = DotProfile.BUILTIN_GENERAL_ID)
        val sLegacy = chatRepository.createNewSession("Legacy Untagged", dotId = null)
        val sBlank = chatRepository.createNewSession("Blank Tag", dotId = "")
        val sCoder = chatRepository.createNewSession("Code Review", dotId = DotProfile.BUILTIN_CODER_ID)
        val sResearcher = chatRepository.createNewSession("Market Analysis", dotId = DotProfile.BUILTIN_RESEARCHER_ID)
        advanceUntilIdle()

        // 1. "All" filter (null) should return all conversations
        viewModel.setDotFilter(null)
        advanceUntilIdle()
        val allConversations = viewModel.uiState.value.filteredConversations
        assertTrue(allConversations.any { it.id == sGeneral.id })
        assertTrue(allConversations.any { it.id == sLegacy.id })
        assertTrue(allConversations.any { it.id == sBlank.id })
        assertTrue(allConversations.any { it.id == sCoder.id })
        assertTrue(allConversations.any { it.id == sResearcher.id })

        // 2. "builtin-general" filter includes explicit general, null, and blank
        viewModel.setDotFilter(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        val generalFiltered = viewModel.uiState.value.filteredConversations
        assertTrue(generalFiltered.any { it.id == sGeneral.id })
        assertTrue(generalFiltered.any { it.id == sLegacy.id })
        assertTrue(generalFiltered.any { it.id == sBlank.id })
        assertFalse(generalFiltered.any { it.id == sCoder.id })
        assertFalse(generalFiltered.any { it.id == sResearcher.id })

        // 3. "builtin-coder" filter includes only coder sessions
        viewModel.setDotFilter(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        val coderFiltered = viewModel.uiState.value.filteredConversations
        assertTrue(coderFiltered.any { it.id == sCoder.id })
        assertFalse(coderFiltered.any { it.id == sGeneral.id })
        assertFalse(coderFiltered.any { it.id == sResearcher.id })

        // 4. "builtin-researcher" filter includes only researcher sessions
        viewModel.setDotFilter(DotProfile.BUILTIN_RESEARCHER_ID)
        advanceUntilIdle()
        val researcherFiltered = viewModel.uiState.value.filteredConversations
        assertTrue(researcherFiltered.any { it.id == sResearcher.id })
        assertFalse(researcherFiltered.any { it.id == sCoder.id })
    }

    @Test
    fun testEffectiveParamsIncorporatesActiveDotPromptAndPreferences() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. Switch to Code Architect and verify system prompt injection
        viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()

        val effectivePrompt = viewModel.uiState.value.effectiveParams.systemPrompt
        assertTrue(
            "Expected system prompt to contain Code Architect instructions but was: $effectivePrompt",
            effectivePrompt.contains("software engineer", ignoreCase = true) ||
                effectivePrompt.contains("code", ignoreCase = true) ||
                effectivePrompt.contains("architecture", ignoreCase = true)
        )

        // 2. Per-conversation system prompt override takes precedence
        val activeId = viewModel.uiState.value.activeConversationId
        chatRepository.setConversationSystemPrompt(activeId, "Mandatory Kotlin-only architecture guidelines")
        viewModel.selectConversation(activeId)
        advanceUntilIdle()

        assertEquals("Mandatory Kotlin-only architecture guidelines", viewModel.uiState.value.effectiveParams.systemPrompt)

        // 3. Custom Dot with model preferences (temperature and topP overrides)
        val customDot = DotProfile(
            id = "custom-creative",
            name = "Creative Visionary",
            role = "Conceptual Designer",
            description = "High creativity ideation assistant",
            avatarPreset = DotAvatarPreset.BRAIN,
            colorHex = "#9333EA",
            systemPrompt = "You are a creative visionary who brainstorms non-obvious solutions.",
            modelPreference = DotModelPreference(
                temperatureOverride = 0.95f,
                topPOverride = 0.85f
            )
        )
        dotRepository.saveDot(customDot)
        advanceUntilIdle()

        viewModel.selectDot("custom-creative")
        advanceUntilIdle()

        assertEquals(0.95, viewModel.uiState.value.effectiveParams.temperature, 0.001)
        assertEquals(0.85, viewModel.uiState.value.effectiveParams.topP, 0.001)
        assertTrue(viewModel.uiState.value.effectiveParams.systemPrompt.contains("creative visionary"))
    }

    @Test
    fun testApproveToolCallForwardsToTaskManagerAndResumes() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val activeSession = getActiveSession()!!

        // Create a task with a sensitive step requiring user approval
        val task = taskManager.createTask(
            title = "Export Module Artifact",
            goalDescription = "Export design system artifact",
            dotId = DotProfile.BUILTIN_CODER_ID,
            conversationId = activeSession.id
        )
        val step = taskManager.addStep(
            taskId = task.id,
            title = "Create Artifact",
            description = "Generating component artifact",
            toolName = "create_artifact",
            toolInputJson = """{"title":"Button.kt","type":"code","content":"class Button"}"""
        )

        // Execute step: halts in AWAITING_APPROVAL because create_artifact is SENSITIVE
        taskManager.executeStep(task.id)
        advanceUntilIdle()

        // Verify pending approval was emitted to ChatUiState
        val approvals = viewModel.uiState.value.pendingApprovals
        assertTrue(approvals.isNotEmpty())
        val pendingRequest = approvals.first()
        assertEquals("create_artifact", pendingRequest.toolName)

        // User approves tool invocation
        viewModel.approveToolCall(pendingRequest.id)
        advanceUntilIdle()

        // Pending approval is resolved and cleared from UI state
        assertTrue(viewModel.uiState.value.pendingApprovals.isEmpty())

        // Task and step have executed and advanced
        val updatedTask = taskManager.getTaskById(task.id)
        assertNotNull(updatedTask)
        val updatedStep = updatedTask?.steps?.firstOrNull { it.id == step.id }
        assertEquals(StepStatus.COMPLETED, updatedStep?.status)
    }

    @Test
    fun testRejectToolCallForwardsToTaskManager() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val activeSession = getActiveSession()!!

        val task = taskManager.createTask(
            title = "Delete File Task",
            goalDescription = "Remove temporary files",
            dotId = DotProfile.BUILTIN_GENERAL_ID,
            conversationId = activeSession.id
        )
        val step = taskManager.addStep(
            taskId = task.id,
            title = "Save External File",
            description = "Writing external storage",
            toolName = "save_file_to_storage",
            toolInputJson = """{"fileName":"dump.bin","content":"raw"}"""
        )

        taskManager.executeStep(task.id)
        advanceUntilIdle()

        val pendingRequest = viewModel.uiState.value.pendingApprovals.first()

        // User rejects tool invocation with reason
        viewModel.rejectToolCall(pendingRequest.id, "Unauthorized storage write")
        advanceUntilIdle()

        // Pending approval cleared from UI
        assertTrue(viewModel.uiState.value.pendingApprovals.isEmpty())

        // Task status transitioned to PAUSED and step to FAILED
        val updatedTask = taskManager.getTaskById(task.id)
        assertEquals(TaskStatus.PAUSED, updatedTask?.status)
        assertTrue(updatedTask?.failureReason?.contains("Unauthorized storage write") == true)
    }

    @Test
    fun testTaskProgressExpandedToggle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Default state is expanded
        assertTrue(viewModel.uiState.value.isTaskProgressExpanded)

        // Toggle to collapsed
        viewModel.toggleTaskProgressExpanded()
        assertFalse(viewModel.uiState.value.isTaskProgressExpanded)

        // Explicitly set to expanded
        viewModel.setTaskProgressExpanded(true)
        assertTrue(viewModel.uiState.value.isTaskProgressExpanded)
    }

    @Test
    fun testActiveTaskProgressObservationAndLifecycle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val activeSession = getActiveSession()!!

        // Create an active task
        val task = taskManager.createTask(
            title = "Continuous Refactoring",
            goalDescription = "Refactoring legacy codebase",
            dotId = DotProfile.BUILTIN_CODER_ID,
            conversationId = activeSession.id
        )
        advanceUntilIdle()

        // Non-terminal tasks appear in activeTasks
        assertTrue(viewModel.uiState.value.activeTasks.any { it.id == task.id })

        // Pause task through ViewModel
        viewModel.pauseTask(task.id)
        advanceUntilIdle()

        val pausedTask = taskManager.getTaskById(task.id)
        assertEquals(TaskStatus.PAUSED, pausedTask?.status)

        // Cancel task through ViewModel
        viewModel.cancelTask(task.id, "User requested cancellation")
        advanceUntilIdle()

        val cancelledTask = taskManager.getTaskById(task.id)
        assertEquals(TaskStatus.CANCELLED, cancelledTask?.status)

        // Terminal tasks (CANCELLED, COMPLETED, FAILED) are filtered out of activeTasks
        assertFalse(viewModel.uiState.value.activeTasks.any { it.id == task.id })
    }

    @Test
    fun testDotThemeColorAndAvatarIconMapping() {
        // 1. Valid 6-digit hex
        val profile6 = DotProfile.STARTER_PROFILES.first().copy(colorHex = "#10B981")
        val color6 = profile6.parseThemeColor()
        assertEquals(1f, color6.alpha, 0.01f)

        // 2. Valid 8-digit hex
        val profile8 = DotProfile.STARTER_PROFILES.first().copy(colorHex = "#804F46E5")
        val color8 = profile8.parseThemeColor()
        assertTrue(color8.alpha > 0.4f && color8.alpha < 0.6f)

        // 3. Fallback on invalid hex
        val profileInvalid = DotProfile.STARTER_PROFILES.first().copy(colorHex = "not-a-color")
        val colorFallback = profileInvalid.parseThemeColor()
        assertEquals(1f, colorFallback.alpha, 0.01f)

        // 4. Avatar preset mapping to vector icons
        DotAvatarPreset.values().forEach { preset ->
            val icon = preset.toIcon()
            assertNotNull("Icon for preset $preset should not be null", icon)
        }
    }

    // --- Test Helpers ---

    private class FakeInferenceEngine(
        model: ModelInfo
    ) : LocalInferenceEngine {
        private val _state = MutableStateFlow<EngineState>(EngineState.Ready)
        override val state: StateFlow<EngineState> = _state.asStateFlow()

        private val _loadedModel = MutableStateFlow<ModelInfo?>(model)
        override val loadedModel: StateFlow<ModelInfo?> = _loadedModel.asStateFlow()

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)
        override suspend fun loadModel(modelInfo: ModelInfo): Result<Unit> {
            _loadedModel.value = modelInfo
            return Result.success(Unit)
        }
        override suspend fun loadModel(modelInfo: ModelInfo, params: GenerationParams): Result<Unit> {
            _loadedModel.value = modelInfo
            return Result.success(Unit)
        }
        override suspend fun unloadModel(): Result<Unit> {
            _loadedModel.value = null
            return Result.success(Unit)
        }
        override suspend fun reload(params: GenerationParams): Result<Unit> = Result.success(Unit)

        override fun generate(prompt: String, settings: InferenceSettings): Flow<String> = flow {
            emit("response")
        }

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            emit(GenerationEvent.Delta("Generated content"))
            emit(
                GenerationEvent.Metrics(
                    RuntimeMetrics(
                        timeToFirstTokenMs = 10L,
                        totalDurationMs = 25L,
                        completionTokens = 2,
                        tokensPerSecond = 80.0
                    )
                )
            )
        }

        override fun cancelGeneration() {}
        override fun runtimeMetrics(): RuntimeMetrics = RuntimeMetrics.EMPTY
        override fun release() {}
    }
}
