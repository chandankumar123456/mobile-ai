package com.mobileai.assistant.presentation.chat

import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.data.settings.AppSettingsRepository
import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.AttachmentKind
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
class ChatViewModelLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private lateinit var rootDir: File
    private lateinit var chatRepository: ChatRepositoryImpl
    private lateinit var settingsRepository: JsonFileSettingsRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var fakeEngine: FakeLocalInferenceEngine
    private lateinit var testModel: ModelInfo

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("chat_vm_test")
        attachmentStore = AttachmentStore(rootDir)
        chatRepository = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        settingsRepository = JsonFileSettingsRepository(rootDir)

        testModel = ModelInfo(
            fileName = "test-model.gguf",
            filePath = File(rootDir, "test-model.gguf").absolutePath,
            fileSizeBytes = 2048L
        )
        fakeEngine = FakeLocalInferenceEngine(testModel)
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
    fun testPrecedenceResolution() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val modelKey = AppSettingsRepository.modelKeyLocal(testModel.filePath)

        // 1. Global default: 0.7
        val initialParams = viewModel.resolveEffectiveParams()
        assertEquals(0.7, initialParams.temperature, 0.001)

        // 2. Model Profile: 0.4
        val profile = ModelSettingsProfile(
            modelId = modelKey,
            displayName = "Profile Test",
            params = GenerationParams(temperature = 0.4, maxTokens = 500)
        )
        settingsRepository.saveProfile(modelKey, profile)
        advanceUntilIdle()

        val profileParams = viewModel.resolveEffectiveParams()
        assertEquals(0.4, profileParams.temperature, 0.001)
        assertEquals(500, profileParams.maxTokens)

        // 3. Conversation override: 0.2
        val activeSessionId = viewModel.uiState.value.activeConversationId
        val sessionOverride = GenerationParams(temperature = 0.2, maxTokens = 250)
        viewModel.setConversationParams(sessionOverride)
        advanceUntilIdle()

        val overrideParams = viewModel.resolveEffectiveParams()
        assertEquals(0.2, overrideParams.temperature, 0.001)
        assertEquals(250, overrideParams.maxTokens)

        // 4. Remove conversation override -> falls back to Model Profile (0.4)
        viewModel.setConversationParams(null)
        advanceUntilIdle()

        val fallbackProfileParams = viewModel.resolveEffectiveParams()
        assertEquals(0.4, fallbackProfileParams.temperature, 0.001)

        // 5. Delete profile -> falls back to Global default (0.7)
        settingsRepository.deleteProfile(modelKey)
        advanceUntilIdle()

        val fallbackGlobalParams = viewModel.resolveEffectiveParams()
        assertEquals(0.7, fallbackGlobalParams.temperature, 0.001)
    }

    @Test
    fun testStopGenerationMarksIncompleteWithReasonStopped() = runTest(testDispatcher) {
        fakeEngine.suspendOnGenerate = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onInputTextChanged("Hello world")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceTimeBy(10)

        assertTrue(viewModel.uiState.value.isGenerating)

        viewModel.stopGeneration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertTrue(fakeEngine.isCancelled)

        val messages = chatRepository.messages.value
        assertEquals(2, messages.size)
        val assistantMsg = messages[1]
        assertFalse(assistantMsg.isUser)
        assertTrue(assistantMsg.isIncomplete)
        assertEquals("stopped", assistantMsg.stopReason)
        assertFalse(assistantMsg.isStreaming)
    }

    @Test
    fun testLanguageAutoDetection() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // AUTO detection on Telugu
        val teluguText = "నమస్కారం ఎలా ఉన్నారు?"
        val detectedTelugu = viewModel.resolveLanguage(teluguText)
        assertEquals(ResponseLanguage.TELUGU, detectedTelugu)

        viewModel.sendMessage(teluguText)
        advanceUntilIdle()

        val messages = chatRepository.messages.value
        assertEquals(2, messages.size)
        val userMsg = messages[0]
        val assistantMsg = messages[1]
        assertEquals(ResponseLanguage.TELUGU, userMsg.language)
        assertEquals(ResponseLanguage.TELUGU, assistantMsg.language)
        assertEquals(ResponseLanguage.TELUGU, fakeEngine.lastRequest?.params?.language)

        // AUTO detection on Hindi
        val hindiText = "नमस्ते आप कैसे हैं?"
        val detectedHindi = viewModel.resolveLanguage(hindiText)
        assertEquals(ResponseLanguage.HINDI, detectedHindi)

        // Explicit response language override
        viewModel.setResponseLanguage(ResponseLanguage.SPANISH)
        advanceUntilIdle()

        val resolvedExplicit = viewModel.resolveLanguage("Any text")
        assertEquals(ResponseLanguage.SPANISH, resolvedExplicit)
    }

    @Test
    fun testPendingAttachmentAdditionAndClearingOnSend() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())

        val attachment1 = Attachment(
            id = "att-1",
            uri = "file:///tmp/img1.png",
            fileName = "img1.png",
            mimeType = "image/png",
            sizeBytes = 1024L,
            kind = AttachmentKind.IMAGE
        )
        val attachment2 = Attachment(
            id = "att-2",
            uri = "file:///tmp/doc.txt",
            fileName = "doc.txt",
            mimeType = "text/plain",
            sizeBytes = 512L,
            kind = AttachmentKind.TEXT
        )

        viewModel.addPendingAttachment(attachment1)
        viewModel.addPendingAttachment(attachment2)
        assertEquals(2, viewModel.uiState.value.pendingAttachments.size)

        viewModel.removePendingAttachment("att-2")
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
        assertEquals("att-1", viewModel.uiState.value.pendingAttachments[0].id)

        viewModel.sendMessage("Check this attachment")
        advanceUntilIdle()

        // Pending attachments must be cleared
        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())

        // User message must contain the attachment
        val userMsg = chatRepository.messages.value.first { it.isUser }
        assertEquals(1, userMsg.attachments.size)
        assertEquals("att-1", userMsg.attachments[0].id)
    }

    @Test
    fun testContinueGenerationLogic() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Prepare incomplete assistant message
        val userMsg = ChatMessage(content = "Write a story", isUser = true)
        val incompleteAssistant = ChatMessage(
            id = "assistant-msg-1",
            content = "Once upon a time in a",
            isUser = false,
            isStreaming = false,
            isIncomplete = true,
            stopReason = "stopped"
        )
        chatRepository.addMessage(userMsg)
        chatRepository.addMessage(incompleteAssistantMsg = incompleteAssistant)

        fakeEngine.tokensToEmit = listOf(" castle", " by the sea.")
        viewModel.continueGeneration(incompleteAssistant.id)
        advanceUntilIdle()

        assertNotNull(fakeEngine.lastRequest)
        // Check that continuation request has continue markers
        val lastInRequest = fakeEngine.lastRequest!!.messages.last()
        assertEquals(incompleteAssistant.id, lastInRequest.id)
        assertTrue(lastInRequest.isIncomplete)

        // Tokens appended to existing assistant message
        val updatedAssistant = chatRepository.messages.value.first { it.id == incompleteAssistant.id }
        assertEquals("Once upon a time in a castle by the sea.", updatedAssistant.content)
        assertFalse(updatedAssistant.isIncomplete)
        assertFalse(updatedAssistant.isStreaming)
    }

    @Test
    fun testTogglePinConversation() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val activeId = chatRepository.activeConversationId.value
        assertFalse(chatRepository.getSession(activeId)!!.pinned)

        viewModel.togglePinConversation(activeId)
        advanceUntilIdle()
        assertTrue(chatRepository.getSession(activeId)!!.pinned)

        viewModel.togglePinConversation(activeId)
        advanceUntilIdle()
        assertFalse(chatRepository.getSession(activeId)!!.pinned)
    }

    @Test
    fun testResetConversationParams() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Set conversation parameter override
        viewModel.setConversationParams(GenerationParams(temperature = 1.85))
        advanceUntilIdle()
        assertEquals(1.85, viewModel.uiState.value.effectiveParams.temperature, 0.001)

        // Reset to defaults
        viewModel.resetConversationParams()
        advanceUntilIdle()
        assertEquals(0.7, viewModel.uiState.value.effectiveParams.temperature, 0.001)
    }

    @Test
    fun testPendingAttachmentsManagement() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val attachment = Attachment(
            id = "att-1",
            uri = "file:///tmp/image.png",
            fileName = "image.png",
            mimeType = "image/png",
            sizeBytes = 1024L,
            kind = AttachmentKind.IMAGE
        )

        viewModel.addPendingAttachment(attachment)
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
        assertEquals("att-1", viewModel.uiState.value.pendingAttachments.first().id)

        viewModel.removePendingAttachment("att-1")
        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
    }

    private fun ChatRepositoryImpl.addMessage(incompleteAssistantMsg: ChatMessage) {
        addMessage(incompleteAssistantMsg)
    }

    private class FakeLocalInferenceEngine(
        model: ModelInfo
    ) : LocalInferenceEngine {
        private val _state = MutableStateFlow<EngineState>(EngineState.Ready)
        override val state: StateFlow<EngineState> = _state.asStateFlow()

        private val _loadedModel = MutableStateFlow<ModelInfo?>(model)
        override val loadedModel: StateFlow<ModelInfo?> = _loadedModel.asStateFlow()

        var lastRequest: GenerationRequest? = null
        var isCancelled: Boolean = false
        var suspendOnGenerate: Boolean = false
        var tokensToEmit: List<String> = listOf(" Response", " finished.")

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
            lastRequest = request
            if (suspendOnGenerate) {
                emit(GenerationEvent.Delta("Partial token"))
                awaitCancellation()
            } else {
                for (token in tokensToEmit) {
                    emit(GenerationEvent.Delta(token))
                }
                emit(
                    GenerationEvent.Metrics(
                        RuntimeMetrics(
                            timeToFirstTokenMs = 15L,
                            totalDurationMs = 50L,
                            completionTokens = tokensToEmit.size,
                            tokensPerSecond = 40.0
                        )
                    )
                )
            }
        }

        override fun cancelGeneration() {
            isCancelled = true
        }

        override fun runtimeMetrics(): RuntimeMetrics = RuntimeMetrics.EMPTY
        override fun release() {}
    }
}
