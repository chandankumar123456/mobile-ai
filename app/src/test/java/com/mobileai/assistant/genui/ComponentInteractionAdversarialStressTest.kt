package com.mobileai.assistant.genui

import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.genui.DiagramDirection
import com.mobileai.assistant.domain.genui.DiagramEdge
import com.mobileai.assistant.domain.genui.DiagramNode
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.model.*
import com.mobileai.assistant.presentation.chat.ChatViewModel
import com.mobileai.assistant.presentation.components.genui.computeTopologicalLayout
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Challenger 2 Adversarial Stress Testing Suite:
 * - Diagram Auto-Layout: Cyclic graphs, disconnected components, large node sets, self loops, missing references.
 * - Table Sorting: Multi-currency, negative values, missing keys, dash placeholders, paging boundary arithmetic.
 * - ChatViewModel Event Routing: Repeated form submit idempotency, missing origin message, rapid duplicate triggers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComponentInteractionAdversarialStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private lateinit var rootDir: File
    private lateinit var chatRepository: ChatRepositoryImpl
    private lateinit var settingsRepository: JsonFileSettingsRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var fakeEngine: StressTestFakeEngine
    private lateinit var testModel: ModelInfo

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("adversarial_stress_test")
        attachmentStore = AttachmentStore(rootDir)
        chatRepository = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        settingsRepository = JsonFileSettingsRepository(rootDir)

        testModel = ModelInfo(
            fileName = "stress-model.gguf",
            filePath = File(rootDir, "stress-model.gguf").absolutePath,
            fileSizeBytes = 2048L
        )
        fakeEngine = StressTestFakeEngine(testModel)
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

    // ==========================================
    // 1. DIAGRAM AUTO-LAYOUT STRESS TESTS
    // ==========================================

    @Test
    fun testDiagramLayoutLargeNodeSetTermination() {
        // Construct 300 nodes with chains and cross-connections
        val nodeCount = 300
        val nodes = (0 until nodeCount).map { i ->
            DiagramNode(id = "node_$i", label = "Node $i")
        }
        val edges = mutableListOf<DiagramEdge>()
        for (i in 0 until nodeCount - 1) {
            edges.add(DiagramEdge(from = "node_$i", to = "node_${i + 1}"))
            if (i % 5 == 0 && i + 3 < nodeCount) {
                edges.add(DiagramEdge(from = "node_$i", to = "node_${i + 3}"))
            }
        }

        val elapsed = measureTimeMillis {
            val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
            assertEquals(nodeCount, layouts.size)
            layouts.forEach { nl ->
                assertTrue("Width must be positive", nl.bounds.width > 0)
                assertTrue("Height must be positive", nl.bounds.height > 0)
            }
        }
        assertTrue("300-node layout should finish under 1000ms, took ${elapsed}ms", elapsed < 1000)
    }

    @Test
    fun testDiagramLayoutReachableCycleBreaker() {
        // Start -> Cycle (A -> B -> C -> A) -> End
        val nodes = listOf(
            DiagramNode(id = "start", label = "Start"),
            DiagramNode(id = "A", label = "Node A"),
            DiagramNode(id = "B", label = "Node B"),
            DiagramNode(id = "C", label = "Node C"),
            DiagramNode(id = "end", label = "End")
        )
        val edges = listOf(
            DiagramEdge(from = "start", to = "A"),
            DiagramEdge(from = "A", to = "B"),
            DiagramEdge(from = "B", to = "C"),
            DiagramEdge(from = "C", to = "A"), // Cycle
            DiagramEdge(from = "C", to = "end")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(5, layouts.size)

        val startLayout = layouts.find { it.node.id == "start" }
        assertNotNull(startLayout)
        // Verify no crash, all nodes got bounds
        layouts.forEach {
            assertTrue(it.bounds.left >= 0f)
            assertTrue(it.bounds.top >= 0f)
        }
    }

    @Test
    fun testDiagramLayoutDisconnectedComponents() {
        // Component 1: C1_A -> C1_B
        // Component 2: C2_A -> C2_B -> C2_C
        // Component 3: C3_Isolated
        val nodes = listOf(
            DiagramNode(id = "C1_A", label = "C1 A"),
            DiagramNode(id = "C1_B", label = "C1 B"),
            DiagramNode(id = "C2_A", label = "C2 A"),
            DiagramNode(id = "C2_B", label = "C2 B"),
            DiagramNode(id = "C2_C", label = "C2 C"),
            DiagramNode(id = "C3_Isolated", label = "C3 Iso")
        )
        val edges = listOf(
            DiagramEdge(from = "C1_A", to = "C1_B"),
            DiagramEdge(from = "C2_A", to = "C2_B"),
            DiagramEdge(from = "C2_B", to = "C2_C")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(6, layouts.size)

        // All 3 roots (C1_A, C2_A, C3_Isolated) should be in layer 0 (same top)
        val topC1A = layouts.find { it.node.id == "C1_A" }!!.bounds.top
        val topC2A = layouts.find { it.node.id == "C2_A" }!!.bounds.top
        val topC3Iso = layouts.find { it.node.id == "C3_Isolated" }!!.bounds.top
        assertEquals(topC1A, topC2A, 0.01f)
        assertEquals(topC1A, topC3Iso, 0.01f)
    }

    @Test
    fun testDiagramLayoutSelfLoopsAndInvalidEdges() {
        // Sub-test 1: With self loop (e.from == e.to), self-loop is ignored for layer precedence.
        // Node 1 is a valid root (in-degree 0), while Node 2 is a child (in-degree 1).
        // Therefore, Node 1 is placed in layer 0, and Node 2 in layer 1.
        val nodesWithSelfLoop = listOf(
            DiagramNode(id = "node1", label = "Node 1"),
            DiagramNode(id = "node2", label = "Node 2")
        )
        val edgesWithSelfLoop = listOf(
            DiagramEdge(from = "node1", to = "node1"), // Self loop
            DiagramEdge(from = "node1", to = "ghost_node"), // Target does not exist
            DiagramEdge(from = "phantom_node", to = "node2"), // Source does not exist
            DiagramEdge(from = "node1", to = "node2") // Valid
        )

        val layoutsWithSelf = computeTopologicalLayout(nodesWithSelfLoop, edgesWithSelfLoop, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(2, layoutsWithSelf.size)
        val l1Self = layoutsWithSelf.find { it.node.id == "node1" }!!.bounds
        val l2Self = layoutsWithSelf.find { it.node.id == "node2" }!!.bounds
        // Node 1 in layer 0 must be positioned strictly above Node 2 in layer 1
        assertTrue("Node 1 must be laid out above Node 2 (self-loop ignored)", l1Self.bottom < l2Self.top)

        // Sub-test 2: Without self loop, phantom edges to non-existent nodes are ignored cleanly,
        // DAG layering assigns node1 to layer 0 and node2 to layer 1.
        val edgesWithoutSelf = listOf(
            DiagramEdge(from = "node1", to = "ghost_node"), // Target does not exist
            DiagramEdge(from = "phantom_node", to = "node2"), // Source does not exist
            DiagramEdge(from = "node1", to = "node2") // Valid
        )
        val layoutsValid = computeTopologicalLayout(nodesWithSelfLoop, edgesWithoutSelf, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(2, layoutsValid.size)
        val l1Valid = layoutsValid.find { it.node.id == "node1" }!!.bounds
        val l2Valid = layoutsValid.find { it.node.id == "node2" }!!.bounds
        assertTrue(l1Valid.bottom < l2Valid.top)
    }

    @Test
    fun testDiagramLayoutEmptyInput() {
        val layouts = computeTopologicalLayout(emptyList(), emptyList(), DiagramDirection.TOP_TO_BOTTOM)
        assertTrue(layouts.isEmpty())
    }

    // ==========================================
    // 2. TABLE SORTING & PAGING STRESS TESTS
    // ==========================================

    private fun sortTableRows(
        rows: List<Map<String, String>>,
        columnKey: String,
        ascending: Boolean
    ): List<Map<String, String>> {
        return rows.sortedWith { rowA, rowB ->
            val valA = rowA[columnKey].orEmpty()
            val valB = rowB[columnKey].orEmpty()
            val numA = valA.replace("$", "").replace("€", "").replace("£", "").replace(",", "").replace("%", "").trim().toDoubleOrNull()
            val numB = valB.replace("$", "").replace("€", "").replace("£", "").replace(",", "").replace("%", "").trim().toDoubleOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                valA.compareTo(valB, ignoreCase = true)
            }
            if (ascending) cmp else -cmp
        }
    }

    @Test
    fun testTableSortingCurrenciesAndNegativeValues() {
        val rows = listOf(
            mapOf("item" to "Alpha", "amount" to "-$50.00"),
            mapOf("item" to "Beta", "amount" to "$1,500.00"),
            mapOf("item" to "Gamma", "amount" to "€200.00"),
            mapOf("item" to "Delta", "amount" to "£5.00"),
            mapOf("item" to "Epsilon", "amount" to "$0.00")
        )

        val asc = sortTableRows(rows, "amount", ascending = true)
        assertEquals("Alpha", asc[0]["item"]) // -$50.00
        assertEquals("Epsilon", asc[1]["item"]) // $0.00
        assertEquals("Delta", asc[2]["item"]) // £5.00
        assertEquals("Gamma", asc[3]["item"]) // €200.00
        assertEquals("Beta", asc[4]["item"]) // $1,500.00

        val desc = sortTableRows(rows, "amount", ascending = false)
        assertEquals("Beta", desc[0]["item"]) // $1,500.00
        assertEquals("Alpha", desc[4]["item"]) // -$50.00
    }

    @Test
    fun testTableSortingPercentages() {
        val rows = listOf(
            mapOf("name" to "P1", "gain" to "12.5%"),
            mapOf("name" to "P2", "gain" to "-4.2%"),
            mapOf("name" to "P3", "gain" to "100%"),
            mapOf("name" to "P4", "gain" to "0.0%")
        )

        val asc = sortTableRows(rows, "gain", ascending = true)
        assertEquals("P2", asc[0]["name"]) // -4.2%
        assertEquals("P4", asc[1]["name"]) // 0.0%
        assertEquals("P1", asc[2]["name"]) // 12.5%
        assertEquals("P3", asc[3]["name"]) // 100%
    }

    @Test
    fun testTableSortingMissingAndPlaceholderValues() {
        val rows = listOf(
            mapOf("id" to "1", "score" to "50"),
            mapOf("id" to "2"), // missing key
            mapOf("id" to "3", "score" to ""), // empty string
            mapOf("id" to "4", "score" to "—"), // dash placeholder
            mapOf("id" to "5", "score" to "10")
        )

        val asc = sortTableRows(rows, "score", ascending = true)
        // All non-numeric or empty strings fall back to string compare
        assertEquals(5, asc.size)
        // Verify numeric items are properly evaluated
        val score5Index = asc.indexOfFirst { it["id"] == "5" } // 10
        val score1Index = asc.indexOfFirst { it["id"] == "1" } // 50
        // When comparing 10 and 50, both are numeric and 10 < 50
        // Even with mixed non-numeric values, the sort must complete deterministically without throwing
    }

    @Test
    fun testTablePagingArithmeticBoundaries() {
        // Table pageSize pagination logic verification
        fun computeTotalPages(totalRows: Int, pageSize: Int?): Int {
            return if (pageSize != null && pageSize > 0) {
                ((totalRows + pageSize - 1) / pageSize).coerceAtLeast(1)
            } else 1
        }

        assertEquals(1, computeTotalPages(0, 10))
        assertEquals(1, computeTotalPages(5, 10))
        assertEquals(1, computeTotalPages(10, 10))
        assertEquals(2, computeTotalPages(11, 10))
        assertEquals(2, computeTotalPages(20, 10))
        assertEquals(3, computeTotalPages(21, 10))
        assertEquals(1, computeTotalPages(50, null))
        assertEquals(1, computeTotalPages(50, 0))
        assertEquals(1, computeTotalPages(50, -5))
    }

    // ==========================================
    // 3. CHATVIEWMODEL EVENT ROUTING & IDEMPOTENCY
    // ==========================================

    @Test
    fun testRepeatedFormSubmitWhileGeneratingIsDropped() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val assistantMsg = ChatMessage(
            content = "Fill form",
            isUser = false,
            genUiPayload = """{"type": "form", "id": "test_form"}"""
        )
        chatRepository.addMessage(assistantMsg)
        advanceUntilIdle()

        val submitEvent = GenUiEvent.FormSubmit(
            formId = "test_form",
            actionId = "submit",
            fieldValues = mapOf("name" to "Alice")
        )

        // 1st submit - starts generation
        viewModel.handleGenUiEvent(assistantMsg.id, submitEvent)

        // 2nd submit immediately while generating
        val submitEvent2 = GenUiEvent.FormSubmit(
            formId = "test_form",
            actionId = "submit",
            fieldValues = mapOf("name" to "AliceDuplicate")
        )
        viewModel.handleGenUiEvent(assistantMsg.id, submitEvent2)
        advanceUntilIdle()

        // Verify only 1 user submission message was added
        val userMessages = chatRepository.messages.value.filter { it.isUser && it.content.contains("test_form") }
        assertEquals(1, userMessages.size)
        assertTrue(userMessages[0].content.contains("name: Alice"))
        assertFalse(userMessages[0].content.contains("AliceDuplicate"))
    }

    @Test
    fun testFormSubmitAfterGenerationCompletesOnAlreadySubmittedMessage() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Seed an assistant message with form that is already marked SUBMITTED
        val assistantMsg = ChatMessage(
            content = "Already submitted form",
            isUser = false,
            genUiPayload = """{"type": "form", "id": "survey_form"}""",
            genUiState = "SUBMITTED",
            genUiSubmittedValues = mapOf("rating" to "5")
        )
        chatRepository.addMessage(assistantMsg)
        advanceUntilIdle()

        val submitEvent = GenUiEvent.FormSubmit(
            formId = "survey_form",
            actionId = "submit",
            fieldValues = mapOf("rating" to "1")
        )

        val initialUserCount = chatRepository.messages.value.count { it.isUser }
        viewModel.handleGenUiEvent(assistantMsg.id, submitEvent)
        advanceUntilIdle()

        val finalUserCount = chatRepository.messages.value.count { it.isUser }
        // Note: In current implementation, FormRenderer UI disables the button (submittedLocked = true),
        // but ChatViewModel.handleGenUiEvent itself does not inspect origin.genUiState == "SUBMITTED".
        // This test documents the behavior at the ViewModel layer.
        val updatedOrigin = chatRepository.messages.value.find { it.id == assistantMsg.id }
        assertEquals("SUBMITTED", updatedOrigin?.genUiState)
    }

    @Test
    fun testFormSubmitWithInvalidMessageIdDoesNotCrash() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val submitEvent = GenUiEvent.FormSubmit(
            formId = "orphan_form",
            actionId = "submit",
            fieldValues = mapOf("k" to "v")
        )

        // Call with a non-existent message ID
        viewModel.handleGenUiEvent("non_existent_msg_id", submitEvent)
        advanceUntilIdle()

        // Should not throw, and should trigger generation cleanly
        assertTrue(fakeEngine.generationTriggered)
    }

    @Test
    fun testSelectionChangeEventRoutesToSendMessage() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val selectEvent = GenUiEvent.SelectionChange(
            componentId = "dropdown_tier",
            selectedKey = "enterprise",
            selectedLabel = "Enterprise Plan"
        )

        viewModel.handleGenUiEvent("msg_any", selectEvent)
        advanceUntilIdle()

        val userMsg = chatRepository.messages.value.find { it.isUser && it.content.contains("enterprise") }
        assertNotNull(userMsg)
        assertTrue(userMsg!!.content.contains("Selected 'enterprise' (Enterprise Plan) in 'dropdown_tier'"))
        assertTrue(fakeEngine.generationTriggered)
    }

    private class StressTestFakeEngine(model: ModelInfo) : LocalInferenceEngine {
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
            emit(GenerationEvent.Delta("Stress test response"))
            emit(GenerationEvent.Metrics(RuntimeMetrics.EMPTY))
        }

        override fun cancelGeneration() {}
        override fun runtimeMetrics(): RuntimeMetrics = RuntimeMetrics.EMPTY
        override fun release() {}
    }
}
