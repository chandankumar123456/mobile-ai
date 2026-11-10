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
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import com.mobileai.assistant.domain.repository.EndpointRepository
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class DotNavigationAndSessionConcurrencyStressTest {

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
    private lateinit var fakeEndpointRepository: FakeEndpointRepository
    private lateinit var testModel: ModelInfo

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("dot_concurrency_stress_test")
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
            fileName = "stress-model.gguf",
            filePath = File(rootDir, "stress-model.gguf").absolutePath,
            fileSizeBytes = 4096L
        )
        fakeEngine = FakeInferenceEngine(testModel)
        fakeEndpointRepository = FakeEndpointRepository()
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
            endpointRepository = fakeEndpointRepository,
            dotRepository = dotRepository,
            artifactRepository = artifactRepository,
            taskManager = taskManager,
            toolRegistry = toolRegistry,
            toolAuditLogger = auditLogger
        )
    }

    // =========================================================================
    // 1. LEGACY UNASSIGNED CONVERSATION MAPPING & INTEGRITY
    // =========================================================================

    @Test
    fun testLegacySessionsWithNullDotIdMapToGeneralAssistant() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Create legacy conversations with null dotId
        val sLegacy1 = chatRepository.createNewSession("Legacy Chat 1", dotId = null)
        val sLegacy2 = chatRepository.createNewSession("Legacy Chat 2", dotId = null)
        val sCoder = chatRepository.createNewSession("Coder Chat", dotId = DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()

        // 1. "All" view (selectedDotFilterId = null) must contain legacy chats without data loss
        viewModel.setDotFilter(null)
        advanceUntilIdle()
        val allConversations = viewModel.uiState.value.filteredConversations
        assertTrue("All view must contain Legacy Chat 1", allConversations.any { it.id == sLegacy1.id })
        assertTrue("All view must contain Legacy Chat 2", allConversations.any { it.id == sLegacy2.id })
        assertTrue("All view must contain Coder Chat", allConversations.any { it.id == sCoder.id })

        // 2. "builtin-general" filter must include legacy chats with null dotId
        viewModel.setDotFilter(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        val generalConversations = viewModel.uiState.value.filteredConversations
        assertTrue("General filter must include Legacy Chat 1", generalConversations.any { it.id == sLegacy1.id })
        assertTrue("General filter must include Legacy Chat 2", generalConversations.any { it.id == sLegacy2.id })
        assertFalse("General filter must NOT include Coder Chat", generalConversations.any { it.id == sCoder.id })

        // 3. "builtin-coder" filter must NOT include legacy chats
        viewModel.setDotFilter(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        val coderConversations = viewModel.uiState.value.filteredConversations
        assertFalse("Coder filter must NOT include Legacy Chat 1", coderConversations.any { it.id == sLegacy1.id })
        assertFalse("Coder filter must NOT include Legacy Chat 2", coderConversations.any { it.id == sLegacy2.id })
        assertTrue("Coder filter must include Coder Chat", coderConversations.any { it.id == sCoder.id })

        // 4. Selecting legacy conversation synchronizes activeDot to builtin-general
        viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        assertEquals(DotProfile.BUILTIN_CODER_ID, viewModel.activeDot.value.id)

        viewModel.selectConversation(sLegacy1.id)
        advanceUntilIdle()
        assertEquals(
            "Active Dot must switch to builtin-general when selecting legacy chat",
            DotProfile.BUILTIN_GENERAL_ID,
            viewModel.activeDot.value.id
        )
        assertEquals(sLegacy1.id, viewModel.uiState.value.activeConversationId)
    }

    @Test
    fun testLegacySessionsFromDiskWithoutDotIdField() = runTest(testDispatcher) {
        val legacyDir = tempFolder.newFolder("legacy_disk_test")

        // Synthesize raw legacy JSON file in conversations_v1.json (v1 format: raw List<ConversationSession>)
        val legacyJsonV1 = """
            [
              {
                "id": "disk-legacy-v1",
                "title": "Disk V1 Pre-Dot Session",
                "createdAt": 1700000000000,
                "updatedAt": 1700000000000,
                "messages": [
                  {
                    "id": "msg-1",
                    "content": "What is clean architecture?",
                    "isUser": true,
                    "timestamp": 1700000000000
                  },
                  {
                    "id": "msg-2",
                    "content": "Clean architecture separates concerns into layers.",
                    "isUser": false,
                    "timestamp": 1700000001000
                  }
                ],
                "pinned": false
              }
            ]
        """.trimIndent()

        val v1File = File(legacyDir, "conversations_v1.json")
        v1File.writeText(legacyJsonV1)

        // Initialize repository from disk: must migrate v1 -> memory and preserve session
        val legacyAttachmentStore = AttachmentStore(legacyDir)
        val diskRepo = ChatRepositoryImpl(rootDir = legacyDir, attachmentStore = legacyAttachmentStore)
        val loadedSession = diskRepo.getSession("disk-legacy-v1")
        assertNotNull("Disk legacy session from v1 must load successfully", loadedSession)
        assertNull("dotId must be null for pre-dot session", loadedSession?.dotId)
        assertEquals(2, loadedSession?.messages?.size)

        // Spin up ViewModel with diskRepo
        val vm = ChatViewModel(
            engine = fakeEngine,
            chatRepository = diskRepo,
            settingsRepository = settingsRepository,
            attachmentStore = legacyAttachmentStore,
            exportService = ConversationExportService(),
            dotRepository = dotRepository,
            artifactRepository = artifactRepository,
            taskManager = taskManager,
            toolRegistry = toolRegistry,
            toolAuditLogger = auditLogger
        )
        advanceUntilIdle()

        // Verify it appears in General filter and All filter
        vm.setDotFilter(null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.filteredConversations.any { it.id == "disk-legacy-v1" })

        vm.setDotFilter(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.filteredConversations.any { it.id == "disk-legacy-v1" })

        // Switch active dot to researcher first
        vm.selectDot(DotProfile.BUILTIN_RESEARCHER_ID)
        advanceUntilIdle()
        assertEquals(DotProfile.BUILTIN_RESEARCHER_ID, vm.activeDot.value.id)

        // Select disk-legacy-v1: must switch activeDot to General Assistant
        vm.selectConversation("disk-legacy-v1")
        advanceUntilIdle()
        assertEquals(DotProfile.BUILTIN_GENERAL_ID, vm.activeDot.value.id)
        assertEquals("disk-legacy-v1", vm.uiState.value.activeConversationId)
        val messages = diskRepo.getSession("disk-legacy-v1")?.messages ?: emptyList()
        assertEquals(2, messages.size)
    }

    @Test
    fun testBlankAndWhitespaceDotIdConversationsInFilteredConversations() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sEmptyDot = chatRepository.createNewSession("Empty Dot ID", dotId = "")
        val sWhitespaceDot = chatRepository.createNewSession("Whitespace Dot ID", dotId = "   ")
        advanceUntilIdle()

        // 1. In "All" filter (null), both must be visible
        viewModel.setDotFilter(null)
        advanceUntilIdle()
        val all = viewModel.uiState.value.filteredConversations
        assertTrue("All filter must include empty dotId session", all.any { it.id == sEmptyDot.id })
        assertTrue("All filter must include whitespace dotId session", all.any { it.id == sWhitespaceDot.id })

        // 2. In "builtin-general" filter, both must be grouped under General Assistant
        viewModel.setDotFilter(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        val general = viewModel.uiState.value.filteredConversations
        assertTrue("General filter must include empty dotId session", general.any { it.id == sEmptyDot.id })
        assertTrue("General filter must include whitespace dotId session", general.any { it.id == sWhitespaceDot.id })

        // 3. In other filters, neither must appear
        viewModel.setDotFilter(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        val coder = viewModel.uiState.value.filteredConversations
        assertFalse("Coder filter must NOT include empty dotId", coder.any { it.id == sEmptyDot.id })
        assertFalse("Coder filter must NOT include whitespace dotId", coder.any { it.id == sWhitespaceDot.id })
    }

    @Test
    fun testSelectConversationBehaviorWithEmptyDotId() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val emptyDotSession = chatRepository.createNewSession("Explicit Empty Dot Tag", dotId = "")
        advanceUntilIdle()

        // Switch to Coder
        viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        assertEquals(DotProfile.BUILTIN_CODER_ID, viewModel.activeDot.value.id)

        // Select the empty-dot session
        viewModel.selectConversation(emptyDotSession.id)
        advanceUntilIdle()

        // Verify active conversation is set
        assertEquals(emptyDotSession.id, viewModel.uiState.value.activeConversationId)

        // In ChatViewModel.kt:
        // val dotId = session?.dotId ?: DotProfile.BUILTIN_GENERAL_ID
        // When session?.dotId is "", dotId is "" -> not found in available dots -> falls back to current activeDot.
        // Documenting this empirical behavior:
        val currentActiveDot = viewModel.activeDot.value.id
        assertTrue(
            "Active dot should either be BUILTIN_CODER_ID (fallback) or BUILTIN_GENERAL_ID (if normalized)",
            currentActiveDot == DotProfile.BUILTIN_CODER_ID || currentActiveDot == DotProfile.BUILTIN_GENERAL_ID
        )
    }

    // =========================================================================
    // 2. DOT FILTERING & SEARCH EDGE CASES IN FILTEREDCONVERSATIONS
    // =========================================================================

    @Test
    fun testFilteredConversationsSpecialCharactersAndRegexSearch() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sRegex = chatRepository.createNewSession("Regex Test .* [a-z]+ (group)", dotId = DotProfile.BUILTIN_CODER_ID)
        chatRepository.addMessage(ChatMessage(content = "Pattern matching with \\d+ and \$^ symbols", isUser = true))

        val sSpecial = chatRepository.createNewSession("Symbols: <xml> & 'quotes' \"double\"", dotId = DotProfile.BUILTIN_GENERAL_ID)
        chatRepository.addMessage(ChatMessage(content = "Mathematical symbols: ∑, ∏, √, π, ∫", isUser = true))

        val sEmoji = chatRepository.createNewSession("Emoji 🚀 Rocket 💡 Light", dotId = DotProfile.BUILTIN_RESEARCHER_ID)
        chatRepository.addMessage(ChatMessage(content = "Multi-language: 你好, Bonjour, Здравствуйте, Café", isUser = true))
        advanceUntilIdle()

        viewModel.setDotFilter(null)

        // 1. Search literal regex meta-characters without crash
        val regexQueries = listOf(".*", "[a-z]+", "(group)", "\\d+", "\$^", "∑", "🚀", "Café", "<xml>")
        for (query in regexQueries) {
            viewModel.setSearchQuery(query)
            val filtered = viewModel.uiState.value.filteredConversations
            assertTrue("Query '$query' should match at least 1 session without regex syntax error", filtered.isNotEmpty())
        }

        // 2. Whitespace-padded query
        viewModel.setSearchQuery("   Rocket   ")
        val rocketFiltered = viewModel.uiState.value.filteredConversations
        assertEquals(1, rocketFiltered.size)
        assertEquals(sEmoji.id, rocketFiltered.first().id)

        // 3. Search restricted by selectedDotFilterId
        viewModel.setSearchQuery("symbols")
        viewModel.setDotFilter(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        val coderMatches = viewModel.uiState.value.filteredConversations
        assertEquals("Coder filter should only match sRegex containing '\$^ symbols'", 1, coderMatches.size)
        assertEquals(sRegex.id, coderMatches.first().id)

        viewModel.setDotFilter(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        val generalMatches = viewModel.uiState.value.filteredConversations
        assertEquals("General filter should match sSpecial containing 'Mathematical symbols'", 1, generalMatches.size)
        assertEquals(sSpecial.id, generalMatches.first().id)

        // 4. Massive search query (5,000 chars) should not crash or hang
        val massiveQuery = "a".repeat(5000)
        viewModel.setSearchQuery(massiveQuery)
        val emptyMatches = viewModel.uiState.value.filteredConversations
        assertTrue("Massive query should return empty without crash", emptyMatches.isEmpty())

        // 5. Blank queries return all filtered by Dot
        viewModel.setSearchQuery("   ")
        viewModel.setDotFilter(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        val coderAll = viewModel.uiState.value.filteredConversations
        assertEquals(1, coderAll.size)
        assertEquals(sRegex.id, coderAll.first().id)
    }

    // =========================================================================
    // 3. RAPID DOT SWITCHING & SESSION CONCURRENCY STRESS
    // =========================================================================

    @Test
    fun testRapidDotSwitchingStressLoop() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val dotIds = listOf(
            DotProfile.BUILTIN_GENERAL_ID,
            DotProfile.BUILTIN_CODER_ID,
            DotProfile.BUILTIN_RESEARCHER_ID,
            DotProfile.BUILTIN_WRITER_ID
        )

        // Rapid sequential switching 80 times
        for (i in 0 until 80) {
            val targetDotId = dotIds[i % dotIds.size]
            viewModel.selectDot(targetDotId)
        }
        advanceUntilIdle()

        val expectedLastDot = dotIds[79 % dotIds.size]
        assertEquals(expectedLastDot, viewModel.activeDot.value.id)
        assertEquals(expectedLastDot, viewModel.uiState.value.activeDot.id)
        assertEquals(expectedLastDot, viewModel.uiState.value.selectedDotFilterId)

        // Verify active conversation matches the expected last Dot
        val activeSession = chatRepository.getSession(viewModel.uiState.value.activeConversationId)
        assertNotNull(activeSession)
        assertEquals(expectedLastDot, activeSession?.dotId)
    }

    @Test
    fun testSwitchingDotsPreservesIndependentMessageHistories() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. In General Dot: add 5 messages
        viewModel.selectDot(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        val generalSessionId = viewModel.uiState.value.activeConversationId
        for (i in 1..5) {
            chatRepository.addMessage(ChatMessage(content = "General msg $i", isUser = i % 2 == 1))
        }
        advanceUntilIdle()

        // 2. Switch to Code Architect: add 8 messages
        viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        val coderSessionId = viewModel.uiState.value.activeConversationId
        assertNotEquals("Coder session must be distinct from General session", generalSessionId, coderSessionId)
        for (i in 1..8) {
            chatRepository.addMessage(ChatMessage(content = "Coder msg $i", isUser = i % 2 == 1))
        }
        advanceUntilIdle()

        // 3. Switch to Technical Writer: add 3 messages
        viewModel.selectDot(DotProfile.BUILTIN_WRITER_ID)
        advanceUntilIdle()
        val writerSessionId = viewModel.uiState.value.activeConversationId
        assertNotEquals(coderSessionId, writerSessionId)
        for (i in 1..3) {
            chatRepository.addMessage(ChatMessage(content = "Writer msg $i", isUser = i % 2 == 1))
        }
        advanceUntilIdle()

        // 4. Verify messages are completely isolated in repository
        assertEquals(5, chatRepository.getSession(generalSessionId)?.messages?.size)
        assertEquals(8, chatRepository.getSession(coderSessionId)?.messages?.size)
        assertEquals(3, chatRepository.getSession(writerSessionId)?.messages?.size)

        // 5. Switch back to General Assistant and check ViewModel message feed
        viewModel.selectDot(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        assertEquals(generalSessionId, viewModel.uiState.value.activeConversationId)
        val generalMessages = viewModel.uiState.value.messages
        assertEquals(5, generalMessages.size)
        assertTrue(generalMessages.all { it.content.startsWith("General msg") })

        // 6. Switch back to Code Architect and check ViewModel message feed
        viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        assertEquals(coderSessionId, viewModel.uiState.value.activeConversationId)
        val coderMessages = viewModel.uiState.value.messages
        assertEquals(8, coderMessages.size)
        assertTrue(coderMessages.all { it.content.startsWith("Coder msg") })
    }

    @Test
    fun testSwitchingDotsDoesNotCreateRunawayEmptyConversations() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Switching back and forth between two Dots when conversations are empty
        for (i in 0 until 10) {
            viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
            advanceUntilIdle()
            viewModel.selectDot(DotProfile.BUILTIN_RESEARCHER_ID)
            advanceUntilIdle()
        }

        // Each Dot should have reused its matching empty conversation rather than creating 20 conversations
        val finalConversations = chatRepository.conversations.value
        val coderCount = finalConversations.count { it.dotId == DotProfile.BUILTIN_CODER_ID }
        val researcherCount = finalConversations.count { it.dotId == DotProfile.BUILTIN_RESEARCHER_ID }

        assertEquals("Should reuse or maintain single empty conversation for Coder", 1, coderCount)
        assertEquals("Should reuse or maintain single empty conversation for Researcher", 1, researcherCount)
    }

    @Test
    fun testConcurrentDotSelectionAndConversationAccess() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val threadPool = Executors.newFixedThreadPool(4)
        val concurrentDispatcher = threadPool.asCoroutineDispatcher()

        try {
            // Concurrent switching across threads
            coroutineScope {
                val jobs = (1..40).map { idx ->
                    async(concurrentDispatcher) {
                        val dotId = when (idx % 4) {
                            0 -> DotProfile.BUILTIN_GENERAL_ID
                            1 -> DotProfile.BUILTIN_CODER_ID
                            2 -> DotProfile.BUILTIN_RESEARCHER_ID
                            else -> DotProfile.BUILTIN_WRITER_ID
                        }
                        viewModel.selectDot(dotId)
                    }
                }
                jobs.awaitAll()
            }
            advanceUntilIdle()

            // State must be intact and non-corrupted
            val activeDot = viewModel.activeDot.value
            assertNotNull(activeDot)
            assertTrue(
                "Active Dot ID must be one of the known profiles",
                listOf(
                    DotProfile.BUILTIN_GENERAL_ID,
                    DotProfile.BUILTIN_CODER_ID,
                    DotProfile.BUILTIN_RESEARCHER_ID,
                    DotProfile.BUILTIN_WRITER_ID
                ).contains(activeDot.id)
            )

            // UI state must match activeDot
            assertEquals(activeDot.id, viewModel.uiState.value.activeDot.id)
            assertEquals(activeDot.id, viewModel.uiState.value.selectedDotFilterId)
        } finally {
            threadPool.shutdown()
        }
    }

    // =========================================================================
    // 4. SESSION ISOLATION & TARGET PARAMETER INTEGRITY
    // =========================================================================

    @Test
    fun testTargetParametersAndPreferencesIsolatedPerDotAndSession() = runTest(testDispatcher) {
        // Register custom Dots with diverse model preferences
        val lowTempDot = DotProfile(
            id = "dot-precise",
            name = "Precise Inspector",
            role = "Auditor",
            description = "Low temp deterministic reasoning",
            avatarPreset = DotAvatarPreset.TERMINAL,
            colorHex = "#2563EB",
            systemPrompt = "You are a precise inspector with zero hallucination tolerance.",
            modelPreference = DotModelPreference(
                temperatureOverride = 0.1f,
                topPOverride = 0.2f
            )
        )
        val highTempDot = DotProfile(
            id = "dot-creative",
            name = "Wild Ideator",
            role = "Ideator",
            description = "High temp creative generation",
            avatarPreset = DotAvatarPreset.BRAIN,
            colorHex = "#EC4899",
            systemPrompt = "You are a divergent brainstormer exploring wild ideas.",
            modelPreference = DotModelPreference(
                temperatureOverride = 0.98f,
                topPOverride = 0.99f
            )
        )
        dotRepository.saveDot(lowTempDot)
        dotRepository.saveDot(highTempDot)
        advanceUntilIdle()

        val viewModel = createViewModel()
        advanceUntilIdle()

        // 1. Select Precise Inspector
        viewModel.selectDot("dot-precise")
        advanceUntilIdle()
        assertEquals(0.1, viewModel.uiState.value.effectiveParams.temperature, 0.001)
        assertEquals(0.2, viewModel.uiState.value.effectiveParams.topP, 0.001)
        assertTrue(viewModel.uiState.value.effectiveParams.systemPrompt.contains("precise inspector"))

        // 2. Select Wild Ideator
        viewModel.selectDot("dot-creative")
        advanceUntilIdle()
        assertEquals(0.98, viewModel.uiState.value.effectiveParams.temperature, 0.001)
        assertEquals(0.99, viewModel.uiState.value.effectiveParams.topP, 0.001)
        assertTrue(viewModel.uiState.value.effectiveParams.systemPrompt.contains("divergent brainstormer"))

        // 3. Switch back to Built-in General: resets parameters to default
        viewModel.selectDot(DotProfile.BUILTIN_GENERAL_ID)
        advanceUntilIdle()
        assertEquals(0.7, viewModel.uiState.value.effectiveParams.temperature, 0.001)
        assertEquals(0.9, viewModel.uiState.value.effectiveParams.topP, 0.001)
    }

    @Test
    fun testPerConversationTargetRestorationAcrossSessions() = runTest(testDispatcher) {
        val endpoint = RemoteEndpointConfig(
            id = "endpoint-test",
            name = "Test Endpoint",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test",
            selectedModel = "gpt-4o"
        )
        fakeEndpointRepository.saveEndpoint(endpoint)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Create Session A with Local Target
        val sessionA = chatRepository.createNewSession("Local Session", dotId = DotProfile.BUILTIN_CODER_ID)
        chatRepository.setConversationTarget(sessionA.id, "local:${testModel.displayName}")
        advanceUntilIdle()

        // Create Session B with Remote Target
        val sessionB = chatRepository.createNewSession("Remote Session", dotId = DotProfile.BUILTIN_RESEARCHER_ID)
        chatRepository.setConversationTarget(sessionB.id, "remote:endpoint-test:gpt-4o")
        advanceUntilIdle()

        // Select Session A
        viewModel.selectConversation(sessionA.id)
        advanceUntilIdle()
        assertTrue("Session A target must be Local", viewModel.uiState.value.conversationTarget is InferenceTarget.Local)
        assertEquals(testModel.displayName, (viewModel.uiState.value.conversationTarget as InferenceTarget.Local).model.displayName)

        // Select Session B
        viewModel.selectConversation(sessionB.id)
        advanceUntilIdle()
        assertTrue("Session B target must be Remote", viewModel.uiState.value.conversationTarget is InferenceTarget.Remote)
        assertEquals("gpt-4o", (viewModel.uiState.value.conversationTarget as InferenceTarget.Remote).modelId)

        // Switch back to Session A
        viewModel.selectConversation(sessionA.id)
        advanceUntilIdle()
        assertTrue("Session A target must restore to Local", viewModel.uiState.value.conversationTarget is InferenceTarget.Local)
    }

    @Test
    fun testSystemPromptOverridePrecedenceOverDotPrompt() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Select Code Architect
        viewModel.selectDot(DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()

        val activeSessionId = viewModel.uiState.value.activeConversationId

        // Default: effective prompt comes from Code Architect
        val coderPrompt = viewModel.uiState.value.effectiveParams.systemPrompt
        assertTrue(coderPrompt.contains("software engineer", ignoreCase = true) || coderPrompt.contains("code", ignoreCase = true))

        // Set conversation override
        chatRepository.setConversationSystemPrompt(activeSessionId, "OVERRIDE: You must speak like a 1920s detective.")
        viewModel.selectConversation(activeSessionId)
        advanceUntilIdle()

        // Override must supersede Dot prompt
        assertEquals("OVERRIDE: You must speak like a 1920s detective.", viewModel.uiState.value.effectiveParams.systemPrompt)

        // Add a message to activeSessionId so createNewChat does not reuse this exact session
        chatRepository.addMessage(ChatMessage(content = "Elementary, my dear Watson", isUser = true))
        advanceUntilIdle()

        // Switch to a new chat under Coder: creates fresh session and restores Code Architect system prompt
        viewModel.createNewChat(dotId = DotProfile.BUILTIN_CODER_ID)
        advanceUntilIdle()
        val newPrompt = viewModel.uiState.value.effectiveParams.systemPrompt
        assertFalse(newPrompt.contains("1920s detective"))
        assertTrue(newPrompt.contains("software engineer", ignoreCase = true) || newPrompt.contains("code", ignoreCase = true))
    }

    // =========================================================================
    // 5. TEST HELPERS
    // =========================================================================

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
            emit(GenerationEvent.Delta("Generated stress content"))
        }

        override fun cancelGeneration() {}
        override fun runtimeMetrics(): RuntimeMetrics = RuntimeMetrics.EMPTY
        override fun release() {}
    }

    private class FakeEndpointRepository : EndpointRepository {
        private val _endpoints = MutableStateFlow<List<RemoteEndpointConfig>>(emptyList())
        override val endpoints: StateFlow<List<RemoteEndpointConfig>> = _endpoints.asStateFlow()

        private val _activeEndpoint = MutableStateFlow<RemoteEndpointConfig?>(null)
        override val activeEndpoint: StateFlow<RemoteEndpointConfig?> = _activeEndpoint.asStateFlow()

        override suspend fun saveEndpoint(endpoint: RemoteEndpointConfig) {
            val current = _endpoints.value.toMutableList()
            val index = current.indexOfFirst { it.id == endpoint.id }
            if (index >= 0) {
                current[index] = endpoint
            } else {
                current.add(endpoint)
            }
            _endpoints.value = current
            if (_activeEndpoint.value == null || _activeEndpoint.value?.id == endpoint.id) {
                _activeEndpoint.value = endpoint
            }
        }

        override suspend fun deleteEndpoint(id: String) {
            _endpoints.value = _endpoints.value.filterNot { it.id == id }
            if (_activeEndpoint.value?.id == id) {
                _activeEndpoint.value = null
            }
        }

        override suspend fun setActiveEndpoint(endpoint: RemoteEndpointConfig?) {
            _activeEndpoint.value = endpoint
        }

        override suspend fun getEndpoints(): List<RemoteEndpointConfig> = _endpoints.value
    }
}
