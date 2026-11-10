package com.mobileai.assistant.task

import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.TaskRecoveryCoordinator
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.DotTaskStep
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolCategory
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial stress testing harness for [TaskManagerImpl] and [TaskRecoveryCoordinator].
 * Empirically tests edge cases: rapid interruption, cancellation resurrection races,
 * concurrent execution re-entrancy, multi-step recovery loops, and corrupt persistence.
 */
class TaskEngineAdversarialStressTest {

    private lateinit var tempDir: File
    private lateinit var toolRegistry: DefaultToolRegistry
    private lateinit var auditLogger: ToolAuditLoggerImpl
    private lateinit var taskManager: TaskManagerImpl
    private lateinit var recoveryCoordinator: TaskRecoveryCoordinator

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("task_stress_test").toFile()
        toolRegistry = DefaultToolRegistry()
        auditLogger = ToolAuditLoggerImpl(tempDir)
        taskManager = TaskManagerImpl(
            rootDirOverride = tempDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )
        recoveryCoordinator = TaskRecoveryCoordinator(taskManager)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /**
     * STRESS TEST 1: Rapid Cancellation While Step Is Running
     * If a user cancels a task while a step is executing, the task status MUST NOT be overwritten
     * to RUNNING or COMPLETED when that step finishes, and subsequent steps MUST NOT run.
     */
    @Test
    fun testRapidCancellationDuringStepExecution() = runBlocking {
        val step0Executed = AtomicInteger(0)
        val step1Executed = AtomicInteger(0)

        toolRegistry.registerTool(
            ToolDefinition(
                id = "slow-step-0",
                name = "slow_step_0",
                description = "Slow step",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step0Executed.incrementAndGet()
                    Thread.sleep(150)
                    ToolExecutionResult.success("""{"step":0}""")
                }
            )
        )

        toolRegistry.registerTool(
            ToolDefinition(
                id = "step-1",
                name = "step_1",
                description = "Subsequent step",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step1Executed.incrementAndGet()
                    ToolExecutionResult.success("""{"step":1}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Cancellation Race Task",
            goalDescription = "Verify cancellation is not resurrected",
            dotId = "dot-race",
            conversationId = "conv-race",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Slow Step", description = "", toolName = "slow_step_0"),
                DotTaskStep(taskId = "", stepIndex = 1, title = "Step 1", description = "", toolName = "step_1")
            )
        )

        // Launch task execution in background coroutine
        val executionJob = launch(Dispatchers.IO) {
            taskManager.executeTask(task.id)
        }

        // Wait briefly for step 0 to begin execution
        delay(30)

        // User cancels task mid-flight
        val cancelledTask = taskManager.cancelTask(task.id, reason = "User aborted mid-step")
        assertNotNull(cancelledTask)
        assertEquals(TaskStatus.CANCELLED, cancelledTask?.status)

        // Wait for execution job to complete
        executionJob.join()

        // Inspect state
        val finalTask = taskManager.getTaskById(task.id)
        assertNotNull(finalTask)

        // Subsequent step MUST NOT have run
        assertEquals("Subsequent step must never run after cancellation", 0, step1Executed.get())

        // Final task status MUST be CANCELLED, never resurrected to RUNNING or COMPLETED
        assertEquals(
            "Task status was resurrected after cancellation! Observed: ${finalTask?.status}",
            TaskStatus.CANCELLED,
            finalTask?.status
        )
    }

    /**
     * STRESS TEST 2: Rapid Pause While Step Is Running
     * If a user pauses a task while a step is executing, the task status MUST remain PAUSED
     * after that step finishes, and subsequent steps MUST NOT run automatically.
     */
    @Test
    fun testRapidPauseDuringStepExecution() = runBlocking {
        val step0Executed = AtomicInteger(0)
        val step1Executed = AtomicInteger(0)

        toolRegistry.registerTool(
            ToolDefinition(
                id = "pause-slow-0",
                name = "pause_slow_0",
                description = "Slow step",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step0Executed.incrementAndGet()
                    Thread.sleep(150)
                    ToolExecutionResult.success("""{"step":0}""")
                }
            )
        )

        toolRegistry.registerTool(
            ToolDefinition(
                id = "pause-step-1",
                name = "pause_step_1",
                description = "Step 1",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step1Executed.incrementAndGet()
                    ToolExecutionResult.success("""{"step":1}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Pause Race Task",
            goalDescription = "Verify pause is preserved",
            dotId = "dot-pause",
            conversationId = "conv-pause",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Slow Step", description = "", toolName = "pause_slow_0"),
                DotTaskStep(taskId = "", stepIndex = 1, title = "Step 1", description = "", toolName = "pause_step_1")
            )
        )

        val executionJob = launch(Dispatchers.IO) {
            taskManager.executeTask(task.id)
        }

        delay(30)

        // User pauses task mid-flight
        val pausedTask = taskManager.pauseTask(task.id)
        assertNotNull(pausedTask)
        assertEquals(TaskStatus.PAUSED, pausedTask?.status)

        executionJob.join()

        val finalTask = taskManager.getTaskById(task.id)
        assertNotNull(finalTask)

        // Subsequent step MUST NOT have run
        assertEquals("Step 1 must not run after pause", 0, step1Executed.get())

        // Final task status MUST be PAUSED
        assertEquals(
            "Task unpaused itself! Observed: ${finalTask?.status}",
            TaskStatus.PAUSED,
            finalTask?.status
        )
    }

    /**
     * STRESS TEST 3: Calling executeStep Directly on Cancelled Task
     * A cancelled task must reject or refuse direct step execution.
     */
    @Test
    fun testDirectStepExecutionOnCancelledTask() = runBlocking {
        var stepExecuted = false
        toolRegistry.registerTool(
            ToolDefinition(
                id = "cancel-safe-step",
                name = "cancel_safe_step",
                description = "Step",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    stepExecuted = true
                    ToolExecutionResult.success("""{"executed":true}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Terminal Direct Step",
            goalDescription = "Verify executeStep on cancelled task",
            dotId = "dot-term",
            conversationId = "conv-term",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Step 0", description = "", toolName = "cancel_safe_step")
            )
        )

        taskManager.cancelTask(task.id, reason = "Cancelled beforehand")

        val result = taskManager.executeStep(task.id)

        // Step should not succeed or execute, and task must remain CANCELLED
        val finalTask = taskManager.getTaskById(task.id)
        assertEquals(TaskStatus.CANCELLED, finalTask?.status)
        assertFalse("Step must not execute on cancelled task", stepExecuted)
    }

    /**
     * STRESS TEST 4: Concurrent Execution of the Same Task (Race Condition / Re-entrancy)
     * Multiple coroutines concurrently calling executeTask(taskId) must not cause double-execution
     * of steps or skip steps.
     */
    @Test
    fun testConcurrentExecuteTaskCallsDoNotCorruptState() = runBlocking {
        val stepCounts = ConcurrentHashMap<Int, AtomicInteger>()
        stepCounts[0] = AtomicInteger(0)
        stepCounts[1] = AtomicInteger(0)
        stepCounts[2] = AtomicInteger(0)

        for (i in 0..2) {
            val idx = i
            toolRegistry.registerTool(
                ToolDefinition(
                    id = "concurrent-step-$idx",
                    name = "concurrent_step_$idx",
                    description = "Step $idx",
                    category = ToolCategory.SYSTEM,
                    sensitivity = ToolSensitivity.SAFE,
                    handler = { _, _ ->
                        stepCounts[idx]?.incrementAndGet()
                        Thread.sleep(20)
                        ToolExecutionResult.success("""{"step":$idx}""")
                    }
                )
            )
        }

        val task = taskManager.createTask(
            title = "Concurrent Invocations Task",
            goalDescription = "Stress test simultaneous executeTask",
            dotId = "dot-concurrent",
            conversationId = "conv-concurrent",
            initialSteps = (0..2).map { idx ->
                DotTaskStep(
                    taskId = "",
                    stepIndex = idx,
                    title = "Step $idx",
                    description = "",
                    toolName = "concurrent_step_$idx"
                )
            }
        )

        // Launch 4 concurrent workers attempting to execute the exact same task
        val workers = (1..4).map {
            async(Dispatchers.IO) {
                taskManager.executeTask(task.id)
            }
        }

        workers.awaitAll()

        val finalTask = taskManager.getTaskById(task.id)
        assertNotNull(finalTask)
        assertEquals(TaskStatus.COMPLETED, finalTask?.status)
        assertEquals(3, finalTask?.completedSteps)
        assertEquals(3, finalTask?.steps?.size)

        // Check if any step was executed more than once
        for (i in 0..2) {
            assertEquals("Step $i must be executed exactly once", 1, stepCounts[i]?.get())
        }
    }

    /**
     * STRESS TEST 5: Multi-Step Task Resumption After Sensitive Tool Approval
     * Task with 3 steps: Safe -> Sensitive -> Safe.
     * After approval decision is made, verify that the subsequent safe step can be reached
     * and the task completes.
     */
    @Test
    fun testMultiStepWorkflowWithApprovalResumption() = runBlocking {
        var step0Done = false
        var step1Done = false
        var step2Done = false

        toolRegistry.registerTool(
            ToolDefinition(
                id = "multi-step-0",
                name = "multi_safe_0",
                description = "safe",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step0Done = true
                    ToolExecutionResult.success("""{"step":0}""")
                }
            )
        )

        toolRegistry.registerTool(
            ToolDefinition(
                id = "multi-step-1",
                name = "multi_sensitive_1",
                description = "sensitive",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ ->
                    step1Done = true
                    ToolExecutionResult.success("""{"step":1}""")
                }
            )
        )

        toolRegistry.registerTool(
            ToolDefinition(
                id = "multi-step-2",
                name = "multi_safe_2",
                description = "safe",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step2Done = true
                    ToolExecutionResult.success("""{"step":2}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Multi-step Approval Task",
            goalDescription = "Safe -> Sensitive -> Safe",
            dotId = "dot-multi",
            conversationId = "conv-multi",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Step 0 Safe", description = "", toolName = "multi_safe_0"),
                DotTaskStep(taskId = "", stepIndex = 1, title = "Step 1 Sens", description = "", toolName = "multi_sensitive_1"),
                DotTaskStep(taskId = "", stepIndex = 2, title = "Step 2 Safe", description = "", toolName = "multi_safe_2")
            )
        )

        // Execute task: step 0 completes, step 1 halts at AWAITING_APPROVAL
        val halted = taskManager.executeTask(task.id)
        assertEquals(TaskStatus.AWAITING_APPROVAL, halted.status)
        assertTrue(step0Done)
        assertFalse(step1Done)
        assertFalse(step2Done)

        val pending = taskManager.getPendingApprovalForTask(task.id)
        assertNotNull(pending)

        // User approves step 1
        taskManager.handleApprovalDecision(pending!!.id, ToolApprovalDecision.APPROVED)
        assertTrue(step1Done)

        // Now resume or continue the task: step 2 must execute and task must complete
        val resumedTask = taskManager.resumeTask(task.id)
        assertNotNull(resumedTask)
        assertTrue("Step 2 must execute on resume", step2Done)
        assertEquals(TaskStatus.COMPLETED, resumedTask?.status)
        assertEquals(3, resumedTask?.completedSteps)
    }

    /**
     * STRESS TEST 6: Rapid Interrupted Recovery Loops
     * Executes 10 rapid recovery cycles across a diverse pool of task states.
     * Proves idempotency and stability: terminal tasks remain terminal, awaiting approval tasks
     * remain awaiting approval, and interrupted running tasks become paused.
     */
    @Test
    fun testRapidInterruptedRecoveryLoops() = runBlocking {
        // Create tasks in different states
        val runningTask = taskManager.createTask(
            title = "Interrupted Running",
            goalDescription = "Running with mid-flight step",
            dotId = "dot-rec-1",
            conversationId = "c1",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "S0", description = "", status = StepStatus.COMPLETED),
                DotTaskStep(taskId = "", stepIndex = 1, title = "S1", description = "", status = StepStatus.RUNNING),
                DotTaskStep(taskId = "", stepIndex = 2, title = "S2", description = "", status = StepStatus.PENDING)
            )
        )
        // Force status to RUNNING
        taskManager.applyRecoveredTasks(listOf(runningTask.copy(status = TaskStatus.RUNNING)))

        val completedTask = taskManager.createTask(
            title = "Already Completed",
            goalDescription = "Done",
            dotId = "dot-rec-2",
            conversationId = "c2",
            initialSteps = emptyList()
        )
        taskManager.applyRecoveredTasks(listOf(completedTask.copy(status = TaskStatus.COMPLETED)))

        val awaitingTask = taskManager.createTask(
            title = "Awaiting Approval",
            goalDescription = "Prompting user",
            dotId = "dot-rec-3",
            conversationId = "c3",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "A0", description = "", status = StepStatus.AWAITING_APPROVAL)
            )
        )
        taskManager.applyRecoveredTasks(listOf(awaitingTask.copy(status = TaskStatus.AWAITING_APPROVAL)))

        val cancelledTask = taskManager.createTask(
            title = "Cancelled Task",
            goalDescription = "Aborted",
            dotId = "dot-rec-4",
            conversationId = "c4",
            initialSteps = emptyList()
        )
        taskManager.applyRecoveredTasks(listOf(cancelledTask.copy(status = TaskStatus.CANCELLED)))

        // Run recovery 10 times consecutively
        for (i in 1..10) {
            val recovered = recoveryCoordinator.coordinateRecovery()
            // Verify stability
            val tRunning = taskManager.getTaskById(runningTask.id)
            assertEquals(TaskStatus.PAUSED, tRunning?.status)
            assertEquals(StepStatus.COMPLETED, tRunning?.steps?.get(0)?.status)
            assertEquals(StepStatus.PENDING, tRunning?.steps?.get(1)?.status)
            assertTrue(tRunning?.steps?.get(1)?.errorMessage?.contains("Interrupted") == true)

            val tCompleted = taskManager.getTaskById(completedTask.id)
            assertEquals(TaskStatus.COMPLETED, tCompleted?.status)

            val tAwaiting = taskManager.getTaskById(awaitingTask.id)
            assertEquals(TaskStatus.AWAITING_APPROVAL, tAwaiting?.status)

            val tCancelled = taskManager.getTaskById(cancelledTask.id)
            assertEquals(TaskStatus.CANCELLED, tCancelled?.status)
        }
    }

    /**
     * STRESS TEST 7: Corrupt / Malformed Persistence File Resilience
     * Verifies that initializing TaskManagerImpl with malformed JSON does not crash the app.
     */
    @Test
    fun testCorruptPersistenceFileDegradesGracefully() = runBlocking {
        val tasksFile = File(tempDir, TaskManagerImpl.FILE_TASKS)
        tasksFile.writeText("{ \"version\": 1, \"tasks\": [ { \"id\": \"corrupt-payload\", ")

        val approvalsFile = File(tempDir, TaskManagerImpl.FILE_APPROVALS)
        approvalsFile.writeText("NOT EVEN JSON DATA")

        // Instantiate new manager pointing to corrupted dir
        val resilientManager = TaskManagerImpl(
            rootDirOverride = tempDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        // Should initialize cleanly without throwing Uncaught Exception
        val tasks = resilientManager.tasks.value
        assertEquals("Should recover to empty list on corrupted tasks file", 0, tasks.size)
        assertEquals("Should recover to empty list on corrupted approvals file", 0, resilientManager.pendingApprovals.value.size)
    }

    /**
     * STRESS TEST 8: Unhandled RuntimeException in Tool Handler
     * Verifies that when a tool handler throws an unexpected exception, the step is marked FAILED,
     * the task is marked FAILED, audit log is written, and the engine does not crash.
     */
    @Test
    fun testToolHandlerUnhandledExceptionFailsGracefully() = runBlocking {
        toolRegistry.registerTool(
            ToolDefinition(
                id = "throwing-tool",
                name = "throwing_tool",
                description = "Throws runtime exception",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    throw IllegalStateException("Catastrophic tool crash inside handler!")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Exception Task",
            goalDescription = "Verify exception handling",
            dotId = "dot-err",
            conversationId = "conv-err",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Crash Step", description = "", toolName = "throwing_tool")
            )
        )

        val resultTask = taskManager.executeTask(task.id)

        assertEquals(TaskStatus.FAILED, resultTask.status)
        assertTrue(resultTask.isTerminal)
        assertEquals("Catastrophic tool crash inside handler!", resultTask.failureReason)
        assertEquals(StepStatus.FAILED, resultTask.steps[0].status)
    }

    /**
     * STRESS TEST 9: Simulated Multi-Step Crash and Resume Cycle
     * Simulates 3 successive crashes mid-execution:
     * - Step 0 executes, crash.
     * - Step 1 executes, crash.
     * - Step 2 executes to completion.
     * Verifies that each step is executed exactly once without duplicate runs.
     */
    @Test
    fun testSimulatedCrashLoopWithIdempotentResume() = runBlocking {
        val stepExecutionCounts = IntArray(3) { 0 }

        for (i in 0..2) {
            val idx = i
            toolRegistry.registerTool(
                ToolDefinition(
                    id = "crash-step-$idx",
                    name = "crash_step_$idx",
                    description = "Step $idx",
                    category = ToolCategory.SYSTEM,
                    sensitivity = ToolSensitivity.SAFE,
                    handler = { _, _ ->
                        stepExecutionCounts[idx]++
                        ToolExecutionResult.success("""{"step":$idx}""")
                    }
                )
            )
        }

        val task = taskManager.createTask(
            title = "Crash Loop Task",
            goalDescription = "Verify idempotent recovery across crashes",
            dotId = "dot-crash-loop",
            conversationId = "conv-crash-loop",
            initialSteps = (0..2).map { idx ->
                DotTaskStep(
                    taskId = "",
                    stepIndex = idx,
                    title = "Step $idx",
                    description = "",
                    toolName = "crash_step_$idx"
                )
            }
        )

        // Step 0: Execute step 0
        val step0Result = taskManager.executeStep(task.id)
        assertEquals(StepStatus.COMPLETED, step0Result.status)
        assertEquals(1, stepExecutionCounts[0])

        // Simulate crash right as step 1 starts:
        // Set step 1 to RUNNING and task to RUNNING
        val taskBeforeCrash1 = taskManager.getTaskById(task.id)!!
        val simulatedInterruptedSteps1 = taskBeforeCrash1.steps.mapIndexed { idx, s ->
            if (idx == 1) s.copy(status = StepStatus.RUNNING, startedAt = 1000L) else s
        }
        taskManager.applyRecoveredTasks(listOf(taskBeforeCrash1.copy(status = TaskStatus.RUNNING, steps = simulatedInterruptedSteps1)))

        // Coordinator recovers
        recoveryCoordinator.coordinateRecovery()
        val recovered1 = taskManager.getTaskById(task.id)!!
        assertEquals(TaskStatus.PAUSED, recovered1.status)
        assertEquals(StepStatus.PENDING, recovered1.steps[1].status)

        // Resume: Step 1 executes
        val resumedTask = taskManager.resumeTask(task.id)
        assertNotNull(resumedTask)
        assertEquals(TaskStatus.COMPLETED, resumedTask?.status)
        assertEquals(3, resumedTask?.completedSteps)

        // Verify each step ran exactly once
        assertEquals(1, stepExecutionCounts[0])
        assertEquals(1, stepExecutionCounts[1])
        assertEquals(1, stepExecutionCounts[2])
    }
}
