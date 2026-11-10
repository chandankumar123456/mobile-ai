package com.mobileai.assistant.task

import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.TaskRecoveryCoordinator
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.DotTaskStep
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TaskRecoveryCoordinatorTest {

    private lateinit var tempDir: File
    private lateinit var toolRegistry: DefaultToolRegistry
    private lateinit var auditLogger: ToolAuditLoggerImpl
    private lateinit var coordinator: TaskRecoveryCoordinator

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("task_recovery_test").toFile()
        toolRegistry = DefaultToolRegistry()
        auditLogger = ToolAuditLoggerImpl(tempDir)
        coordinator = TaskRecoveryCoordinator()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testRecoverInterruptedRunningTask() {
        val steps = listOf(
            DotTaskStep(
                id = "step-0",
                taskId = "task-1",
                stepIndex = 0,
                title = "Step 0",
                description = "Already done",
                status = StepStatus.COMPLETED,
                toolOutputJson = """{"done":true}"""
            ),
            DotTaskStep(
                id = "step-1",
                taskId = "task-1",
                stepIndex = 1,
                title = "Step 1",
                description = "Interrupted mid-flight",
                status = StepStatus.RUNNING,
                startedAt = 1000L
            ),
            DotTaskStep(
                id = "step-2",
                taskId = "task-1",
                stepIndex = 2,
                title = "Step 2",
                description = "Not started yet",
                status = StepStatus.PENDING
            )
        )

        val runningTask = DotTask(
            id = "task-1",
            dotId = "dot-1",
            conversationId = "conv-1",
            title = "Long Running Task",
            goalDescription = "Process multiple items",
            status = TaskStatus.RUNNING,
            currentStepIndex = 1,
            totalSteps = 3,
            completedSteps = 1,
            steps = steps
        )

        val recovered = coordinator.recoverTask(runningTask)

        assertEquals(TaskStatus.PAUSED, recovered.status)
        assertEquals(1, recovered.completedSteps)
        assertEquals(1, recovered.currentStepIndex)
        assertTrue(recovered.failureReason?.contains("Interrupted") == true)

        // Step 0 remains COMPLETED
        assertEquals(StepStatus.COMPLETED, recovered.steps[0].status)
        // Step 1 was reset to PENDING
        assertEquals(StepStatus.PENDING, recovered.steps[1].status)
        assertTrue(recovered.steps[1].errorMessage?.contains("Interrupted by process restart") == true)
        assertEquals(null, recovered.steps[1].startedAt)
        // Step 2 remains PENDING
        assertEquals(StepStatus.PENDING, recovered.steps[2].status)
    }

    @Test
    fun testAwaitingApprovalTaskIsPreservedAcrossRestart() {
        val steps = listOf(
            DotTaskStep(
                id = "step-0",
                taskId = "task-2",
                stepIndex = 0,
                title = "Sensitive Step",
                description = "Awaiting user confirmation",
                status = StepStatus.AWAITING_APPROVAL,
                toolName = "delete_artifact"
            )
        )

        val awaitingTask = DotTask(
            id = "task-2",
            dotId = "dot-2",
            conversationId = "conv-2",
            title = "Awaiting Task",
            goalDescription = "Delete resource",
            status = TaskStatus.AWAITING_APPROVAL,
            currentStepIndex = 0,
            totalSteps = 1,
            completedSteps = 0,
            steps = steps
        )

        val recovered = coordinator.recoverTask(awaitingTask)

        // Must remain in AWAITING_APPROVAL so user prompt is not lost
        assertEquals(TaskStatus.AWAITING_APPROVAL, recovered.status)
        assertEquals(StepStatus.AWAITING_APPROVAL, recovered.steps[0].status)
    }

    @Test
    fun testTerminalAndAlreadyPausedTasksAreUnchanged() {
        val completedTask = DotTask(
            id = "t-comp",
            dotId = "d",
            conversationId = "c",
            title = "Done",
            goalDescription = "Done",
            status = TaskStatus.COMPLETED
        )
        val failedTask = DotTask(
            id = "t-fail",
            dotId = "d",
            conversationId = "c",
            title = "Fail",
            goalDescription = "Fail",
            status = TaskStatus.FAILED
        )
        val pausedTask = DotTask(
            id = "t-pause",
            dotId = "d",
            conversationId = "c",
            title = "Pause",
            goalDescription = "Pause",
            status = TaskStatus.PAUSED
        )

        assertEquals(TaskStatus.COMPLETED, coordinator.recoverTask(completedTask).status)
        assertEquals(TaskStatus.FAILED, coordinator.recoverTask(failedTask).status)
        assertEquals(TaskStatus.PAUSED, coordinator.recoverTask(pausedTask).status)
    }

    @Test
    fun testEndToEndRecoveryAndIdempotentResumption() = runBlocking {
        var step0ExecCount = 0
        var step1ExecCount = 0

        toolRegistry.registerTool(
            ToolDefinition(
                id = "safe-0",
                name = "tool_step_0",
                description = "",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step0ExecCount++
                    ToolExecutionResult.success("""{"step":0}""")
                }
            )
        )
        toolRegistry.registerTool(
            ToolDefinition(
                id = "safe-1",
                name = "tool_step_1",
                description = "",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    step1ExecCount++
                    ToolExecutionResult.success("""{"step":1}""")
                }
            )
        )

        val taskManager = TaskManagerImpl(
            rootDirOverride = tempDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        val steps = listOf(
            DotTaskStep(taskId = "", stepIndex = 0, title = "Step 0", description = "", toolName = "tool_step_0"),
            DotTaskStep(taskId = "", stepIndex = 1, title = "Step 1", description = "", toolName = "tool_step_1")
        )

        val task = taskManager.createTask(
            title = "Crash Recovery Test",
            goalDescription = "Simulate crash mid-flight",
            dotId = "dot-crash",
            conversationId = "conv-crash",
            initialSteps = steps
        )

        // Execute step 0 successfully
        val step0Result = taskManager.executeStep(task.id)
        assertEquals(StepStatus.COMPLETED, step0Result.status)
        assertEquals(1, step0ExecCount)

        // Now simulate app termination mid-execution:
        // Create a new TaskManager instance simulating a fresh app launch
        val restartedManager = TaskManagerImpl(
            rootDirOverride = tempDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        val coordinatorWithManager = TaskRecoveryCoordinator(restartedManager)
        val recoveredTasks = coordinatorWithManager.coordinateRecovery()
        assertNotNull(recoveredTasks)

        // Resume from restarted manager
        val resumedTask = restartedManager.resumeTask(task.id)
        assertNotNull(resumedTask)
        assertEquals(TaskStatus.COMPLETED, resumedTask?.status)
        assertEquals(2, resumedTask?.completedSteps)

        // Step 0 was NEVER re-executed (idempotency guarantee)
        assertEquals(1, step0ExecCount)
        // Step 1 was executed exactly once
        assertEquals(1, step1ExecCount)
    }
}
