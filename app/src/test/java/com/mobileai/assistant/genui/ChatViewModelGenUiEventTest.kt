package com.mobileai.assistant.genui

import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.model.*
import com.mobileai.assistant.presentation.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelGenUiEventTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private lateinit var rootDir: File
    private lateinit var chatRepository: ChatRepositoryImpl
    private lateinit var settingsRepository: JsonFileSettingsRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var fakeEngine: FakeEngine
    private lateinit var testModel: ModelInfo

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("genui_vm_test")
        attachmentStore = AttachmentStore(rootDir)
        chatRepository = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        settingsRepository = JsonFileSettingsRepository(rootDir)

        testModel = ModelInfo(
            fileName = "test-model.gguf",
            filePath = File(rootDir, "test-model.gguf").absolutePath,
            fileSizeBytes = 2048L
        )
        fakeEngine = FakeEngine(testModel)
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
            exportService = ConversationExportService()
        )
    }

    @Test
    fun testFormSubmitLocksOriginMessageAndTriggersGeneration() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Seed an assistant message with form
        val assistantMsg = ChatMessage(
            content = "Please fill out this form:",
            isUser = false,
            genUiPayload = """{"type": "form", "id": "feedback_form"}"""
        )
        chatRepository.addMessage(assistantMsg)
        advanceUntilIdle()

        val submitEvent = GenUiEvent.FormSubmit(
            formId = "feedback_form",
            actionId = "submit",
            fieldValues = mapOf("satisfaction" to "5", "comments" to "Great experience")
        )

        viewModel.handleGenUiEvent(assistantMsg.id, submitEvent)
        advanceUntilIdle()

        // 1. Verify origin message locked
        val updatedOrigin = chatRepository.messages.value.find { it.id == assistantMsg.id }
        assertNotNull(updatedOrigin)
        assertEquals("SUBMITTED", updatedOrigin!!.genUiState)
        assertEquals("5", updatedOrigin.genUiSubmittedValues?.get("satisfaction"))
        assertEquals("Great experience", updatedOrigin.genUiSubmittedValues?.get("comments"))

        // 2. Verify synthetic user message added
        val userMsg = chatRepository.messages.value.find { it.isUser && it.content.contains("Submitted form 'feedback_form'") }
        assertNotNull(userMsg)
        assertTrue(userMsg!!.content.contains("satisfaction: 5"))
        assertTrue(userMsg.content.contains("comments: Great experience"))

        // 3. Verify assistant message created and generation started
        assertTrue(fakeEngine.generationTriggered)
    }

    @Test
    fun testButtonClickTriggersActionTurn() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val buttonEvent = GenUiEvent.ButtonClick(
            actionId = "restart_service",
            payload = mapOf("service" to "auth_daemon"),
            componentId = "btn_restart"
        )

        viewModel.handleGenUiEvent("msg_any", buttonEvent)
        advanceUntilIdle()

        val userMsg = chatRepository.messages.value.find { it.isUser && it.content.contains("restart_service") }
        assertNotNull(userMsg)
        assertTrue(userMsg!!.content.contains("Selected action 'restart_service' with service=auth_daemon"))
        assertTrue(fakeEngine.generationTriggered)
    }

    @Test
    fun testDiagramNodeClickTriggersFollowUp() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val nodeClick = GenUiEvent.DiagramNodeClick(
            diagramId = "architecture_flow",
            nodeId = "postgres_cluster",
            nodeLabel = "PostgreSQL DB"
        )

        viewModel.handleGenUiEvent("msg_any", nodeClick)
        advanceUntilIdle()

        val userMsg = chatRepository.messages.value.find { it.isUser && it.content.contains("postgres_cluster") }
        assertNotNull(userMsg)
        assertTrue(userMsg!!.content.contains("Tell me more about step 'postgres_cluster' ('PostgreSQL DB')"))
    }

    private class FakeEngine(model: ModelInfo) : LocalInferenceEngine {
        private val _state = MutableStateFlow<EngineState>(EngineState.Ready)
        override val state: StateFlow<EngineState> = _state.asStateFlow()

        private val _loadedModel = MutableStateFlow<ModelInfo?>(model)
        override val loadedModel: StateFlow<ModelInfo?> = _loadedModel.asStateFlow()

        var generationTriggered: Boolean = false

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)
        override suspend fun loadModel(modelInfo: ModelInfo): Result<Unit> = Result.success(Unit)
        override suspend fun loadModel(modelInfo: ModelInfo, params: GenerationParams): Result<Unit> = Result.success(Unit)
        override suspend fun unloadModel(): Result<Unit> = Result.success(Unit)
        override suspend fun reload(params: GenerationParams): Result<Unit> = Result.success(Unit)

        override fun generate(prompt: String, settings: InferenceSettings): Flow<String> = flow {
            emit("response")
        }

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            generationTriggered = true
            emit(GenerationEvent.Delta("AI response acknowledgment"))
            emit(GenerationEvent.Metrics(RuntimeMetrics.EMPTY))
        }

        override fun cancelGeneration() {}
        override fun runtimeMetrics(): RuntimeMetrics = RuntimeMetrics.EMPTY
        override fun release() {}
    }
}

