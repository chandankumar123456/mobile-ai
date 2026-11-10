package com.mobileai.assistant.presentation.chat

import com.mobileai.assistant.data.artifact.ArtifactRepositoryImpl
import com.mobileai.assistant.data.dot.DotRepositoryImpl
import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.TaskRecoveryCoordinator
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RuntimeMetrics
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.DotTaskStep
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.tool.ApprovalStatus
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolCategory
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Empirical Stress Test and Challenge Suite for Milestone 4:
 * 1. Tool approval action routing (approval continues execution, rejection pauses with reason,
 *    always-allow saves permission for non-critical tools while preventing CRITICAL bypass).
 * 2. Task progress expand/collapse toggling and multi-task representation.
 * 3. TaskRecoveryCoordinator edge-case task states (interrupted RUNNING tasks reset to PAUSED,
 *    AWAITING_APPROVAL preserved, terminal untouched).
 * 4. Startup recovery resilience (empty/corrupted files, UI non-blocking behavior).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskProgressAndToolApprovalStressTest {

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
    private lateinit var recoveryCoordinator: TaskRecoveryCoordinator
    private lateinit var fakeEngine: FakeInferenceEngine
    private lateinit var testModel: ModelInfo

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("challenger_m4_stress_test")
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
        recoveryCoordinator = TaskRecoveryCoordinator(taskManager)

        testModel = ModelInfo(
            fileName = "stress-test-model.gguf",
            filePath = File(rootDir, "stress-test-model.gguf").absolutePath,
            fileSizeBytes = 2048L
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

    // =========================================================================
    // 1. TOOL APPROVAL ACTION ROUTING CHALLENGES
    // =========================================================================

    /**
     * Challenge 1A: Approving a sensitive tool request executes the tool, completes the step,
     * and automatically triggers execution of subsequent steps to complete the multi-step task.
     */
    @Test
    fun testApprovalContinuesMultiStepTaskExecutionToEnd() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        var safeStep0Executed = false
        var sensitiveStep1Executed = false
        var safeStep2Executed = false

        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-step-0",
                name = "prep_data",
                description = "Prepare data",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    safeStep0Executed = true
                    ToolExecutionResult.success("""{"step":0,"status":"ok"}""")
                }
            )
        )

        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-step-1",
                name = "write_critical_report",
                description = "Sensitive write operation",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ ->
                    sensitiveStep1Executed = true
                    ToolExecutionResult.success("""{"step":1,"reportId":"rep-999"}""")
                }
            )
        )

        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-step-2",
                name = "finalize_task",
                description = "Safe completion step",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    safeStep2Executed = true
                    ToolExecutionResult.success("""{"step":2,"status":"finalized"}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Multi-Step Approval Workflow",
            goalDescription = "Verify approval continues task execution through remaining steps",
            dotId = viewModel.activeDot.value.id,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Prep", description = "", toolName = "prep_data"),
                DotTaskStep(taskId = "", stepIndex = 1, title = "Write Report", description = "", toolName = "write_critical_report"),
                DotTaskStep(taskId = "", stepIndex = 2, title = "Finalize", description = "", toolName = "finalize_task")
            )
        )

        // Start execution: Step 0 runs, Step 1 halts for approval
        taskManager.executeTask(task.id)
        advanceUntilIdle()

        assertTrue("Safe step 0 must have executed", safeStep0Executed)
        assertFalse("Sensitive step 1 must NOT execute yet", sensitiveStep1Executed)
        assertFalse("Step 2 must NOT execute yet", safeStep2Executed)

        // Verify task state is AWAITING_APPROVAL
        val awaitingTask = taskManager.getTaskById(task.id)
        assertNotNull(awaitingTask)
        assertEquals(TaskStatus.AWAITING_APPROVAL, awaitingTask?.status)
        assertEquals(StepStatus.AWAITING_APPROVAL, awaitingTask?.steps?.get(1)?.status)

        // Verify pending approval exposed in ViewModel
        val pendingApprovals = viewModel.pendingApprovals.value
        assertEquals(1, pendingApprovals.size)
        val request = pendingApprovals.first()
        assertEquals("write_critical_report", request.toolName)

        // Act: User approves the tool invocation via ChatViewModel
        viewModel.approveToolCall(request.id)
        advanceUntilIdle()

        // Verify: Step 1 executed, AND step 2 was continued and executed automatically!
        assertTrue("Sensitive step 1 must have executed upon approval", sensitiveStep1Executed)
        assertTrue("Step 2 must have continued automatically to completion", safeStep2Executed)

        val completedTask = taskManager.getTaskById(task.id)
        assertNotNull(completedTask)
        assertEquals(TaskStatus.COMPLETED, completedTask?.status)
        assertEquals(3, completedTask?.completedSteps)
        assertEquals(StepStatus.COMPLETED, completedTask?.steps?.get(0)?.status)
        assertEquals(StepStatus.COMPLETED, completedTask?.steps?.get(1)?.status)
        assertEquals(StepStatus.COMPLETED, completedTask?.steps?.get(2)?.status)

        // Pending approval queue must be empty
        assertTrue(viewModel.pendingApprovals.value.isEmpty())

        // Audit log must confirm APPROVED_ONCE with SUCCESS
        val logs = auditLogger.getAuditLogs().first()
        val step1Log = logs.firstOrNull { it.stepId == awaitingTask?.steps?.get(1)?.id }
        assertNotNull(step1Log)
        assertEquals(ApprovalStatus.APPROVED_ONCE, step1Log?.approvalStatus)
        assertEquals("SUCCESS", step1Log?.executionStatus)
        assertTrue(step1Log?.wasSensitive == true)
    }

    /**
     * Challenge 1B: Rejecting a sensitive tool request sets the step to FAILED,
     * transitions the task to PAUSED with the rejection reason, halts subsequent steps,
     * and logs the rejection to the audit log.
     */
    @Test
    fun testRejectionPausesTaskWithExplicitReasonAndAbortsRemainingSteps() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        var toolExecuted = false
        var subsequentStepExecuted = false

        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-sens",
                name = "modify_system_setting",
                description = "Sensitive modification",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ ->
                    toolExecuted = true
                    ToolExecutionResult.success("""{"modified":true}""")
                }
            )
        )

        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-next",
                name = "after_rejection_step",
                description = "Should never run",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    subsequentStepExecuted = true
                    ToolExecutionResult.success("""{"ran":true}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Rejection Workflow",
            goalDescription = "Verify rejection halts task with reason",
            dotId = viewModel.activeDot.value.id,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Sensitive Step", description = "", toolName = "modify_system_setting"),
                DotTaskStep(taskId = "", stepIndex = 1, title = "Subsequent Step", description = "", toolName = "after_rejection_step")
            )
        )

        taskManager.executeTask(task.id)
        advanceUntilIdle()

        val pending = viewModel.pendingApprovals.value.first()
        assertEquals("modify_system_setting", pending.toolName)

        val customRejectionReason = "User denied permission: sensitive setting change is prohibited"

        // Act: User rejects the tool call with custom reason
        viewModel.rejectToolCall(pending.id, customRejectionReason)
        advanceUntilIdle()

        // Verify tool was NOT executed
        assertFalse("Rejected tool must NEVER execute", toolExecuted)
        assertFalse("Subsequent step must NEVER execute after rejection", subsequentStepExecuted)

        // Verify task state
        val pausedTask = taskManager.getTaskById(task.id)
        assertNotNull(pausedTask)
        assertEquals(TaskStatus.PAUSED, pausedTask?.status)
        assertTrue(pausedTask?.failureReason?.contains(customRejectionReason) == true)

        // Step 0 must be FAILED with custom reason
        val step0 = pausedTask?.steps?.get(0)
        assertEquals(StepStatus.FAILED, step0?.status)
        assertEquals(customRejectionReason, step0?.errorMessage)

        // Step 1 must remain PENDING
        val step1 = pausedTask?.steps?.get(1)
        assertEquals(StepStatus.PENDING, step1?.status)

        // Pending approval queue cleared
        assertTrue(viewModel.pendingApprovals.value.isEmpty())

        // Audit log verifies rejection
        val logs = auditLogger.getAuditLogs().first()
        val rejectionLog = logs.firstOrNull { it.toolName == "modify_system_setting" }
        assertNotNull(rejectionLog)
        assertEquals(ApprovalStatus.REJECTED, rejectionLog?.approvalStatus)
        assertEquals("REJECTED", rejectionLog?.executionStatus)
        assertEquals(customRejectionReason, rejectionLog?.errorMessage)
    }

    /**
     * Challenge 1C: "Always Allow" saves permission in ToolRegistry for non-critical tools,
     * allowing subsequent task invocations with the same tool to execute immediately without halting.
     */
    @Test
    fun testAlwaysAllowSavesPermissionForNonCriticalToolAndAutoBypassesFutureCalls() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        var invocationCount = 0
        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-export",
                name = "export_artifact",
                description = "Sensitive export",
                category = ToolCategory.STORAGE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ ->
                    invocationCount++
                    ToolExecutionResult.success("""{"exported":true}""")
                }
            )
        )

        val activeDotId = viewModel.activeDot.value.id

        // Task 1: First invocation requires approval
        val task1 = taskManager.createTask(
            title = "First Export Task",
            goalDescription = "First run requiring approval",
            dotId = activeDotId,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Export", description = "", toolName = "export_artifact")
            )
        )

        taskManager.executeTask(task1.id)
        advanceUntilIdle()

        val pending = viewModel.pendingApprovals.value.first()

        // Act: User selects ALWAYS_ALLOWED
        viewModel.alwaysAllowToolCall(pending.id)
        advanceUntilIdle()

        assertEquals(1, invocationCount)
        assertTrue(
            "ToolRegistry must record always-allowed permission for this Dot",
            toolRegistry.hasAlwaysAllowedPermission(activeDotId, "export_artifact")
        )

        // Task 2: Second invocation for the SAME Dot and tool should execute automatically!
        val task2 = taskManager.createTask(
            title = "Second Export Task",
            goalDescription = "Should bypass approval because it was always-allowed",
            dotId = activeDotId,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Export Again", description = "", toolName = "export_artifact")
            )
        )

        val task2Result = taskManager.executeTask(task2.id)
        advanceUntilIdle()

        assertEquals(TaskStatus.COMPLETED, task2Result.status)
        assertEquals(2, invocationCount)
        assertTrue("Pending approvals must remain empty on bypass", viewModel.pendingApprovals.value.isEmpty())
    }

    /**
     * Challenge 1D: "Always Allow" permissions are strictly isolated per-Dot.
     * Granting always-allow for Dot A does NOT grant permission for Dot B.
     */
    @Test
    fun testAlwaysAllowPermissionIsStrictlyIsolatedPerDot() = runTest(testDispatcher) {
        val dotAId = "dot-persona-a"
        val dotBId = "dot-persona-b"

        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-write-db",
                name = "write_database",
                description = "Sensitive database write",
                category = ToolCategory.STORAGE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ -> ToolExecutionResult.success("""{"written":true}""") }
            )
        )

        // Grant permission only for Dot A
        toolRegistry.setAlwaysAllowedPermission(dotAId, "write_database", true)

        assertTrue(toolRegistry.hasAlwaysAllowedPermission(dotAId, "write_database"))
        assertFalse(toolRegistry.hasAlwaysAllowedPermission(dotBId, "write_database"))

        // Task for Dot B must STILL require approval
        val taskB = taskManager.createTask(
            title = "Dot B Task",
            goalDescription = "Should NOT inherit Dot A's always-allowed permission",
            dotId = dotBId,
            conversationId = "conv-b",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Write", description = "", toolName = "write_database")
            )
        )

        val stepResult = taskManager.executeStep(taskB.id)
        assertEquals(StepStatus.AWAITING_APPROVAL, stepResult.status)
        val pendingB = taskManager.getPendingApprovalForTask(taskB.id)
        assertNotNull(pendingB)
        assertEquals(dotBId, pendingB?.dotId)
    }

    /**
     * Challenge 1E: CRITICAL tools can NEVER be bypassed by "Always Allow".
     * Even if the user chooses ALWAYS_ALLOWED, the ToolRegistry does NOT save the permission,
     * and even if forced into registry, executeStep enforces ToolSensitivity.CRITICAL -> true.
     */
    @Test
    fun testCriticalToolAlwaysRequiresApprovalAndCannotBeBypassed() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        var deleteCount = 0
        toolRegistry.registerTool(
            ToolDefinition(
                id = "tool-purge",
                name = "delete_all_artifacts",
                description = "Critical destructive action",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.CRITICAL,
                handler = { _, _ ->
                    deleteCount++
                    ToolExecutionResult.success("""{"deleted":true}""")
                }
            )
        )

        val activeDotId = viewModel.activeDot.value.id

        val task1 = taskManager.createTask(
            title = "Critical Purge Task 1",
            goalDescription = "First critical run",
            dotId = activeDotId,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Purge", description = "", toolName = "delete_all_artifacts")
            )
        )

        taskManager.executeTask(task1.id)
        advanceUntilIdle()

        val pending = viewModel.pendingApprovals.value.first()

        // Act: User attempts to select ALWAYS_ALLOWED on a CRITICAL tool
        viewModel.alwaysAllowToolCall(pending.id)
        advanceUntilIdle()

        assertEquals(1, deleteCount)

        // 1. ToolRegistry must NOT have saved permission (prevented by sensitivity guard)
        assertFalse(
            "CRITICAL tool must NOT have permission saved in ToolRegistry",
            toolRegistry.hasAlwaysAllowedPermission(activeDotId, "delete_all_artifacts")
        )

        // 2. Even if an attacker forcibly inserts permission into ToolRegistry...
        toolRegistry.setAlwaysAllowedPermission(activeDotId, "delete_all_artifacts", true)
        assertTrue(toolRegistry.hasAlwaysAllowedPermission(activeDotId, "delete_all_artifacts"))

        // Task 2: Second invocation must STILL halt for approval regardless of registry contents!
        val task2 = taskManager.createTask(
            title = "Critical Purge Task 2",
            goalDescription = "Second critical run with forced permission",
            dotId = activeDotId,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Purge 2", description = "", toolName = "delete_all_artifacts")
            )
        )

        val task2Result = taskManager.executeStep(task2.id)
        assertEquals(
            "CRITICAL tool must still halt for approval even if registry was forced",
            StepStatus.AWAITING_APPROVAL,
            task2Result.status
        )
        val pending2 = taskManager.getPendingApprovalForTask(task2.id)
        assertNotNull(pending2)
        assertEquals("delete_all_artifacts", pending2?.toolName)
    }

    // =========================================================================
    // 2. TASK PROGRESS EXPAND/COLLAPSE & MULTI-TASK REPRESENTATION
    // =========================================================================

    /**
     * Challenge 2A: Task progress card expand/collapse state machine toggles cleanly,
     * supports explicit setter idempotency, and handles rapid UI interaction.
     */
    @Test
    fun testTaskProgressExpandCollapseTogglingAndIdempotency() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Default state: expanded is true
        assertTrue(viewModel.uiState.value.isTaskProgressExpanded)

        // Toggle to collapsed
        viewModel.toggleTaskProgressExpanded()
        assertFalse(viewModel.uiState.value.isTaskProgressExpanded)

        // Toggle back to expanded
        viewModel.toggleTaskProgressExpanded()
        assertTrue(viewModel.uiState.value.isTaskProgressExpanded)

        // Explicit setter idempotency
        viewModel.setTaskProgressExpanded(false)
        assertFalse(viewModel.uiState.value.isTaskProgressExpanded)
        viewModel.setTaskProgressExpanded(false)
        assertFalse(viewModel.uiState.value.isTaskProgressExpanded)

        viewModel.setTaskProgressExpanded(true)
        assertTrue(viewModel.uiState.value.isTaskProgressExpanded)

        // Stress toggle loop: 100 alternating toggles
        for (i in 0 until 100) {
            viewModel.toggleTaskProgressExpanded()
        }
        // Even number of toggles leaves it back at true
        assertTrue(viewModel.uiState.value.isTaskProgressExpanded)
    }

    /**
     * Challenge 2B: Multi-task representation and FIFO queue promotion:
     * When multiple active tasks exist, all non-terminal tasks are tracked in activeTasks,
     * firstOrNull() presents the active banner, and upon completion of task A, task B is
     * seamlessly promoted to firstOrNull() without dropping state.
     */
    @Test
    fun testMultiTaskRepresentationAndFifoPromotionOnCompletion() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val taskA = taskManager.createTask(
            title = "Task Alpha",
            goalDescription = "First background task",
            dotId = viewModel.activeDot.value.id,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Alpha Step 1", description = "")
            )
        )

        val taskB = taskManager.createTask(
            title = "Task Beta",
            goalDescription = "Second background task",
            dotId = viewModel.activeDot.value.id,
            conversationId = chatRepository.activeConversationId.value,
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Beta Step 1", description = "")
            )
        )

        advanceUntilIdle()

        // Both non-terminal tasks are in activeTasks
        val activeTasks = viewModel.uiState.value.activeTasks
        assertEquals(2, activeTasks.size)
        val taskIds = activeTasks.map { it.id }
        assertTrue(taskIds.contains(taskA.id))
        assertTrue(taskIds.contains(taskB.id))

        // UI primary banner renders the first active task (Task A)
        assertEquals(taskA.id, activeTasks.firstOrNull()?.id)
        assertEquals("Task Alpha", activeTasks.firstOrNull()?.title)

        // Complete Task A
        taskManager.executeTask(taskA.id)
        advanceUntilIdle()

        // Active tasks should now only contain Task B
        val updatedActiveTasks = viewModel.uiState.value.activeTasks
        assertEquals(1, updatedActiveTasks.size)
        assertEquals(taskB.id, updatedActiveTasks.firstOrNull()?.id)
        assertEquals("Task Beta", updatedActiveTasks.firstOrNull()?.title)

        // Cancel Task B
        viewModel.cancelTask(taskB.id)
        advanceUntilIdle()

        // All tasks terminal -> activeTasks becomes empty
        assertTrue(viewModel.uiState.value.activeTasks.isEmpty())
    }

    /**
     * Challenge 2C: Zero-step tasks do not throw ArithmeticException / divide-by-zero
     * in progress fraction calculations and complete immediately.
     */
    @Test
    fun testZeroStepTaskHandledSafelyWithoutCrashOrDivideByZero() = runTest(testDispatcher) {
        val task = taskManager.createTask(
            title = "Zero Step Task",
            goalDescription = "Edge case with no steps",
            dotId = "dot-zero",
            conversationId = "conv-zero",
            initialSteps = emptyList()
        )

        // progressFraction must not throw ArithmeticException and returns 0f for 0 total steps
        val fraction = task.progressFraction
        assertEquals(0f, fraction, 0.001f)

        // Executing zero-step task immediately marks it completed
        val executed = taskManager.executeTask(task.id)
        assertEquals(TaskStatus.COMPLETED, executed.status)
        assertEquals(0, executed.totalSteps)
        assertEquals(0, executed.completedSteps)
    }

    // =========================================================================
    // 3. TASK RECOVERY COORDINATOR EDGE-CASE TASK STATES
    // =========================================================================

    /**
     * Challenge 3A: Interrupted RUNNING task has its mid-flight RUNNING step reset to PENDING
     * with startedAt nullified, and the task status reset to PAUSED.
     */
    @Test
    fun testRecoveryResetsRunningTaskToPausedAndMidFlightStepToPending() {
        val steps = listOf(
            DotTaskStep(
                id = "step-0",
                taskId = "task-interrupted",
                stepIndex = 0,
                title = "Done Step",
                description = "",
                status = StepStatus.COMPLETED,
                startedAt = 1000L,
                finishedAt = 2000L
            ),
            DotTaskStep(
                id = "step-1",
                taskId = "task-interrupted",
                stepIndex = 1,
                title = "Interrupted Step",
                description = "",
                status = StepStatus.RUNNING,
                startedAt = 2500L
            ),
            DotTaskStep(
                id = "step-2",
                taskId = "task-interrupted",
                stepIndex = 2,
                title = "Upcoming Step",
                description = "",
                status = StepStatus.PENDING
            )
        )

        val task = DotTask(
            id = "task-interrupted",
            dotId = "dot-rec",
            conversationId = "conv-rec",
            title = "Interrupted Task",
            goalDescription = "",
            status = TaskStatus.RUNNING,
            currentStepIndex = 1,
            totalSteps = 3,
            completedSteps = 1,
            steps = steps
        )

        val recovered = recoveryCoordinator.recoverTask(task)

        assertEquals(TaskStatus.PAUSED, recovered.status)
        assertTrue(recovered.failureReason?.contains("Interrupted") == true)

        // Step 0 remains COMPLETED
        assertEquals(StepStatus.COMPLETED, recovered.steps[0].status)
        assertEquals(2000L, recovered.steps[0].finishedAt)

        // Step 1 reset to PENDING, startedAt cleared
        assertEquals(StepStatus.PENDING, recovered.steps[1].status)
        assertNull(recovered.steps[1].startedAt)
        assertTrue(recovered.steps[1].errorMessage?.contains("Interrupted by process restart") == true)

        // Step 2 remains PENDING
        assertEquals(StepStatus.PENDING, recovered.steps[2].status)
    }

    /**
     * Challenge 3B: A task marked RUNNING where no steps are RUNNING (e.g., between steps
     * or interrupted before the first step started) safely transitions to PAUSED without step corruption.
     */
    @Test
    fun testRecoveryRunningTaskWithNoStepsInRunningState() {
        val steps = listOf(
            DotTaskStep(
                id = "step-0",
                taskId = "task-no-running-step",
                stepIndex = 0,
                title = "Not started",
                description = "",
                status = StepStatus.PENDING
            )
        )

        val task = DotTask(
            id = "task-no-running-step",
            dotId = "dot-rec",
            conversationId = "conv-rec",
            title = "Task between steps",
            goalDescription = "",
            status = TaskStatus.RUNNING,
            currentStepIndex = 0,
            totalSteps = 1,
            completedSteps = 0,
            steps = steps
        )

        val recovered = recoveryCoordinator.recoverTask(task)

        assertEquals(TaskStatus.PAUSED, recovered.status)
        assertEquals(StepStatus.PENDING, recovered.steps[0].status)
        assertNull(recovered.steps[0].errorMessage)
    }

    /**
     * Challenge 3C: AWAITING_APPROVAL tasks are strictly preserved so pending user
     * prompts are not lost across app restarts.
     */
    @Test
    fun testRecoveryPreservesAwaitingApprovalTasksUnmodified() {
        val steps = listOf(
            DotTaskStep(
                id = "step-0",
                taskId = "task-awaiting",
                stepIndex = 0,
                title = "Pending Confirmation",
                description = "",
                status = StepStatus.AWAITING_APPROVAL,
                toolName = "delete_artifact"
            )
        )

        val task = DotTask(
            id = "task-awaiting",
            dotId = "dot-rec",
            conversationId = "conv-rec",
            title = "Awaiting Task",
            goalDescription = "",
            status = TaskStatus.AWAITING_APPROVAL,
            currentStepIndex = 0,
            totalSteps = 1,
            completedSteps = 0,
            steps = steps
        )

        val recovered = recoveryCoordinator.recoverTask(task)

        assertEquals(TaskStatus.AWAITING_APPROVAL, recovered.status)
        assertEquals(StepStatus.AWAITING_APPROVAL, recovered.steps[0].status)
    }

    /**
     * Challenge 3D: Terminal tasks (COMPLETED, FAILED, CANCELLED) and already PAUSED tasks
     * are completely untouched by recovery.
     */
    @Test
    fun testRecoveryLeavesTerminalAndPausedTasksUntouched() {
        val completedTask = DotTask(id = "t-comp", dotId = "d", conversationId = "c", title = "Done", goalDescription = "", status = TaskStatus.COMPLETED)
        val failedTask = DotTask(id = "t-fail", dotId = "d", conversationId = "c", title = "Failed", goalDescription = "", status = TaskStatus.FAILED)
        val cancelledTask = DotTask(id = "t-canc", dotId = "d", conversationId = "c", title = "Cancelled", goalDescription = "", status = TaskStatus.CANCELLED)
        val pausedTask = DotTask(id = "t-pause", dotId = "d", conversationId = "c", title = "Paused", goalDescription = "", status = TaskStatus.PAUSED)
        val createdTask = DotTask(id = "t-creat", dotId = "d", conversationId = "c", title = "Created", goalDescription = "", status = TaskStatus.CREATED)

        assertEquals(TaskStatus.COMPLETED, recoveryCoordinator.recoverTask(completedTask).status)
        assertEquals(TaskStatus.FAILED, recoveryCoordinator.recoverTask(failedTask).status)
        assertEquals(TaskStatus.CANCELLED, recoveryCoordinator.recoverTask(cancelledTask).status)
        assertEquals(TaskStatus.PAUSED, recoveryCoordinator.recoverTask(pausedTask).status)
        assertEquals(TaskStatus.CREATED, recoveryCoordinator.recoverTask(createdTask).status)
    }

    /**
     * Challenge 3E: An edge case where multiple steps are concurrently marked RUNNING
     * (e.g. from corrupt external file state) has ALL running steps reset to PENDING.
     */
    @Test
    fun testRecoveryMultipleRunningStepsAllResetToPending() {
        val steps = listOf(
            DotTaskStep(id = "s-0", taskId = "t-multi", stepIndex = 0, title = "S0", description = "", status = StepStatus.RUNNING, startedAt = 100L),
            DotTaskStep(id = "s-1", taskId = "t-multi", stepIndex = 1, title = "S1", description = "", status = StepStatus.RUNNING, startedAt = 200L)
        )

        val task = DotTask(
            id = "t-multi",
            dotId = "d",
            conversationId = "c",
            title = "Multi Running",
            goalDescription = "",
            status = TaskStatus.RUNNING,
            currentStepIndex = 0,
            totalSteps = 2,
            completedSteps = 0,
            steps = steps
        )

        val recovered = recoveryCoordinator.recoverTask(task)

        assertEquals(TaskStatus.PAUSED, recovered.status)
        assertEquals(StepStatus.PENDING, recovered.steps[0].status)
        assertEquals(StepStatus.PENDING, recovered.steps[1].status)
        assertNull(recovered.steps[0].startedAt)
        assertNull(recovered.steps[1].startedAt)
    }

    /**
     * Challenge 3F: Recovery idempotency: recovering an already-recovered task list
     * produces identical stable results.
     */
    @Test
    fun testRecoveryIdempotency() {
        val task = DotTask(
            id = "t-idem",
            dotId = "d",
            conversationId = "c",
            title = "Idempotency",
            goalDescription = "",
            status = TaskStatus.RUNNING,
            steps = listOf(
                DotTaskStep(id = "s-0", taskId = "t-idem", stepIndex = 0, title = "S0", description = "", status = StepStatus.RUNNING)
            )
        )

        val firstRecovery = recoveryCoordinator.recoverTask(task)
        val secondRecovery = recoveryCoordinator.recoverTask(firstRecovery)

        assertEquals(firstRecovery.status, secondRecovery.status)
        assertEquals(firstRecovery.steps[0].status, secondRecovery.steps[0].status)
        assertEquals(firstRecovery.failureReason, secondRecovery.failureReason)
    }

    // =========================================================================
    // 4. MAIN ACTIVITY STARTUP RECOVERY RESILIENCE & CORRUPTED FILES
    // =========================================================================

    /**
     * Challenge 4A: Corrupted tasks_v1.json (malformed syntax) does NOT throw,
     * degrades safely to emptyList(), and startup recovery completes cleanly.
     */
    @Test
    fun testTaskManagerHandlesCorruptTasksJsonGracefully() = runTest(testDispatcher) {
        val tasksFile = File(rootDir, TaskManagerImpl.FILE_TASKS)
        tasksFile.writeText("{ corrupted_json: [ unclosed bracket !!!")

        // Constructing TaskManagerImpl with corrupt file must not throw
        val resilientManager = TaskManagerImpl(
            rootDirOverride = rootDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        assertTrue(
            "Corrupt file must degrade to empty list without crashing",
            resilientManager.tasks.value.isEmpty()
        )

        val coordinator = TaskRecoveryCoordinator(resilientManager)
        val recovered = coordinator.coordinateRecovery()
        assertTrue("Recovery on empty tasks returns empty list", recovered.isEmpty())
    }

    /**
     * Challenge 4B: Empty (0 bytes) tasks_v1.json and tool_approvals_v1.json files
     * load cleanly without EOFException.
     */
    @Test
    fun testTaskManagerHandlesEmptyTasksAndApprovalsFiles() = runTest(testDispatcher) {
        val tasksFile = File(rootDir, TaskManagerImpl.FILE_TASKS)
        val approvalsFile = File(rootDir, TaskManagerImpl.FILE_APPROVALS)
        tasksFile.writeText("")
        approvalsFile.writeText("")

        val resilientManager = TaskManagerImpl(
            rootDirOverride = rootDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        assertTrue(resilientManager.tasks.value.isEmpty())
        assertTrue(resilientManager.pendingApprovals.value.isEmpty())
    }

    /**
     * Challenge 4C: TaskManager handles non-existent nested root directory cleanly,
     * creating directories on demand during state persistence.
     */
    @Test
    fun testTaskManagerHandlesNonExistentDirectory() = runTest(testDispatcher) {
        val nonExistentDir = File(rootDir, "nested/path/to/tasks")
        assertFalse(nonExistentDir.exists())

        val manager = TaskManagerImpl(
            rootDirOverride = nonExistentDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        assertTrue(manager.tasks.value.isEmpty())

        // Creating a task triggers saveStateLocked which must create directory
        val task = manager.createTask(
            title = "Dir Test",
            goalDescription = "Verify dir creation",
            dotId = "dot-dir",
            conversationId = "conv-dir"
        )

        assertTrue("Nested directory must be created on save", nonExistentDir.exists())
        assertEquals("Dir Test", manager.getTaskById(task.id)?.title)
    }

    /**
     * Challenge 4D: Startup recovery execution time benchmark:
     * Recovering 50 interrupted tasks and persisting recovered state takes well under 500ms
     * ensuring it does not stall startup.
     */
    @Test
    fun testRecoveryExecutionTimeIsSubSecond() = runTest(testDispatcher) {
        val taskCount = 50
        val interruptedTasks = (1..taskCount).map { i ->
            DotTask(
                id = "bench-task-$i",
                dotId = "dot-bench",
                conversationId = "conv-bench",
                title = "Benchmark Task $i",
                goalDescription = "Testing performance",
                status = TaskStatus.RUNNING,
                currentStepIndex = 1,
                totalSteps = 3,
                completedSteps = 1,
                steps = listOf(
                    DotTaskStep(id = "s-$i-0", taskId = "bench-task-$i", stepIndex = 0, title = "S0", description = "", status = StepStatus.COMPLETED),
                    DotTaskStep(id = "s-$i-1", taskId = "bench-task-$i", stepIndex = 1, title = "S1", description = "", status = StepStatus.RUNNING, startedAt = 1000L),
                    DotTaskStep(id = "s-$i-2", taskId = "bench-task-$i", stepIndex = 2, title = "S2", description = "", status = StepStatus.PENDING)
                )
            )
        }

        val durationMs = measureTimeMillis {
            val recovered = recoveryCoordinator.recoverInterruptedTasks(interruptedTasks)
            assertEquals(taskCount, recovered.size)
            assertTrue(recovered.all { it.status == TaskStatus.PAUSED })
        }

        assertTrue("Recovery of 50 tasks must complete within 500ms (took ${durationMs}ms)", durationMs < 500)
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
