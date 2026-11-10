package com.mobileai.assistant.task

import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TaskStateMachineTest {

    private lateinit var tempDir: File
    private lateinit var toolRegistry: DefaultToolRegistry
    private lateinit var auditLogger: ToolAuditLoggerImpl
    private lateinit var taskManager: TaskManagerImpl

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("task_sm_test").toFile()
        toolRegistry = DefaultToolRegistry()
        auditLogger = ToolAuditLoggerImpl(tempDir)
        taskManager = TaskManagerImpl(
            rootDirOverride = tempDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testTaskCreationAndInitialState() = runBlocking {
        val initialSteps = listOf(
            DotTaskStep(
                taskId = "",
                stepIndex = 0,
                title = "Step 1: Inspect environment",
                description = "Query active context",
                toolName = "inspect_task",
                toolInputJson = """{"taskId":"self"}"""
            ),
            DotTaskStep(
                taskId = "",
                stepIndex = 1,
                title = "Step 2: Format summary",
                description = "Format findings",
                toolName = "format_content",
                toolInputJson = """{"content":"data"}"""
            )
        )

        val task = taskManager.createTask(
            title = "Analyze System Context",
            goalDescription = "Inspect context and format summary",
            dotId = "dot-analyst-1",
            conversationId = "conv-101",
            initialSteps = initialSteps
        )

        assertNotNull(task.id)
        assertEquals(TaskStatus.CREATED, task.status)
        assertEquals(2, task.totalSteps)
        assertEquals(0, task.completedSteps)
        assertEquals(0, task.currentStepIndex)
        assertEquals(2, task.steps.size)
        assertEquals(StepStatus.PENDING, task.steps[0].status)
        assertEquals(StepStatus.PENDING, task.steps[1].status)
        assertFalse(task.isTerminal)
        assertEquals(0f, task.progressFraction, 0.001f)
    }

    @Test
    fun testFullSequentialExecutionToCompletion() = runBlocking {
        // Register custom safe execution handlers
        toolRegistry.registerTool(
            ToolDefinition(
                id = "safe-step-1",
                name = "inspect_task",
                description = "inspect",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ -> ToolExecutionResult.success("""{"active":true}""") }
            )
        )
        toolRegistry.registerTool(
            ToolDefinition(
                id = "safe-step-2",
                name = "format_content",
                description = "format",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ -> ToolExecutionResult.success("""{"formatted":"All good"}""") }
            )
        )

        val steps = listOf(
            DotTaskStep(
                taskId = "",
                stepIndex = 0,
                title = "Inspect",
                description = "Inspect",
                toolName = "inspect_task"
            ),
            DotTaskStep(
                taskId = "",
                stepIndex = 1,
                title = "Format",
                description = "Format",
                toolName = "format_content"
            )
        )

        val created = taskManager.createTask(
            title = "Execution Test",
            goalDescription = "Run both safe steps",
            dotId = "dot-1",
            conversationId = "conv-1",
            initialSteps = steps
        )

        val completedTask = taskManager.executeTask(created.id)

        assertEquals(TaskStatus.COMPLETED, completedTask.status)
        assertEquals(2, completedTask.completedSteps)
        assertEquals(2, completedTask.currentStepIndex)
        assertTrue(completedTask.isTerminal)
        assertEquals(1.0f, completedTask.progressFraction, 0.001f)
        assertNotNull(completedTask.completedAt)

        val retrieved = taskManager.getTaskById(created.id)
        assertNotNull(retrieved)
        assertEquals(TaskStatus.COMPLETED, retrieved?.status)
        assertEquals(StepStatus.COMPLETED, retrieved?.steps?.get(0)?.status)
        assertEquals(StepStatus.COMPLETED, retrieved?.steps?.get(1)?.status)
    }

    @Test
    fun testStepFailureHaltsTaskExecution() = runBlocking {
        toolRegistry.registerTool(
            ToolDefinition(
                id = "failing-tool",
                name = "failing_step",
                description = "Tool that throws failure",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ -> ToolExecutionResult.failure("Disk I/O error occurred") }
            )
        )

        val steps = listOf(
            DotTaskStep(
                taskId = "",
                stepIndex = 0,
                title = "Will Fail",
                description = "Fails immediately",
                toolName = "failing_step"
            ),
            DotTaskStep(
                taskId = "",
                stepIndex = 1,
                title = "Unreached",
                description = "Should not execute",
                toolName = "inspect_task"
            )
        )

        val task = taskManager.createTask(
            title = "Failure Test",
            goalDescription = "Verify failure stop",
            dotId = "dot-1",
            conversationId = "conv-1",
            initialSteps = steps
        )

        val failedTask = taskManager.executeTask(task.id)

        assertEquals(TaskStatus.FAILED, failedTask.status)
        assertEquals(0, failedTask.completedSteps)
        assertEquals("Disk I/O error occurred", failedTask.failureReason)
        assertTrue(failedTask.isTerminal)

        val step0 = failedTask.steps[0]
        assertEquals(StepStatus.FAILED, step0.status)
        assertEquals("Disk I/O error occurred", step0.errorMessage)

        val step1 = failedTask.steps[1]
        assertEquals(StepStatus.PENDING, step1.status)
    }

    @Test
    fun testPauseAndResumeLifecycle() = runBlocking {
        toolRegistry.registerTool(
            ToolDefinition(
                id = "step1",
                name = "inspect_task",
                description = "inspect",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ -> ToolExecutionResult.success("""{"step":1}""") }
            )
        )
        toolRegistry.registerTool(
            ToolDefinition(
                id = "step2",
                name = "format_content",
                description = "format",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ -> ToolExecutionResult.success("""{"step":2}""") }
            )
        )

        val steps = listOf(
            DotTaskStep(taskId = "", stepIndex = 0, title = "Step 1", description = "s1", toolName = "inspect_task"),
            DotTaskStep(taskId = "", stepIndex = 1, title = "Step 2", description = "s2", toolName = "format_content")
        )

        val task = taskManager.createTask(
            title = "Pause Resume Test",
            goalDescription = "Verify pause and resume",
            dotId = "dot-1",
            conversationId = "conv-1",
            initialSteps = steps
        )

        // Execute single step
        val step1Result = taskManager.executeStep(task.id)
        assertEquals(StepStatus.COMPLETED, step1Result.status)

        // Explicitly pause
        val paused = taskManager.pauseTask(task.id)
        assertNotNull(paused)
        assertEquals(TaskStatus.PAUSED, paused?.status)
        assertEquals(1, paused?.completedSteps)
        assertEquals(1, paused?.currentStepIndex)

        // Resume task to completion
        val resumed = taskManager.resumeTask(task.id)
        assertNotNull(resumed)
        assertEquals(TaskStatus.COMPLETED, resumed?.status)
        assertEquals(2, resumed?.completedSteps)
    }

    @Test
    fun testCancelTask() = runBlocking {
        val steps = listOf(
            DotTaskStep(taskId = "", stepIndex = 0, title = "Step 1", description = "s1")
        )

        val task = taskManager.createTask(
            title = "Cancel Test",
            goalDescription = "Test cancellation",
            dotId = "dot-1",
            conversationId = "conv-1",
            initialSteps = steps
        )

        val cancelled = taskManager.cancelTask(task.id, reason = "User aborted")
        assertNotNull(cancelled)
        assertEquals(TaskStatus.CANCELLED, cancelled?.status)
        assertEquals("User aborted", cancelled?.failureReason)
        assertTrue(cancelled?.isTerminal == true)
    }

    @Test
    fun testDynamicAddStep() = runBlocking {
        val task = taskManager.createTask(
            title = "Dynamic Steps",
            goalDescription = "Add step on the fly",
            dotId = "dot-1",
            conversationId = "conv-1",
            initialSteps = emptyList()
        )
        assertEquals(0, task.totalSteps)

        val step1 = taskManager.addStep(
            taskId = task.id,
            title = "Added Step 1",
            description = "Dynamically appended",
            toolName = "inspect_task"
        )
        assertEquals(0, step1.stepIndex)
        assertEquals("Added Step 1", step1.title)

        val updatedTask = taskManager.getTaskById(task.id)
        assertNotNull(updatedTask)
        assertEquals(1, updatedTask?.totalSteps)
        assertEquals(1, updatedTask?.steps?.size)
    }

    @Test
    fun testPersistenceReloadAcrossManagerInstances() = runBlocking {
        val task = taskManager.createTask(
            title = "Persistence Check",
            goalDescription = "Survive re-instantiation",
            dotId = "dot-persist",
            conversationId = "conv-persist",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Step A", description = "dA")
            )
        )

        // Instantiate completely fresh TaskManager pointing to same directory
        val freshManager = TaskManagerImpl(
            rootDirOverride = tempDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )

        val reloaded = freshManager.getTaskById(task.id)
        assertNotNull(reloaded)
        assertEquals("Persistence Check", reloaded?.title)
        assertEquals("dot-persist", reloaded?.dotId)
        assertEquals(1, reloaded?.steps?.size)
        assertEquals("Step A", reloaded?.steps?.get(0)?.title)
    }
}
