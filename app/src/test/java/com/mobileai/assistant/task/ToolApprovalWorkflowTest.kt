package com.mobileai.assistant.task

import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.domain.task.DotTaskStep
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.tool.ApprovalStatus
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolCategory
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ToolApprovalWorkflowTest {

    private lateinit var tempDir: File
    private lateinit var toolRegistry: DefaultToolRegistry
    private lateinit var auditLogger: ToolAuditLoggerImpl
    private lateinit var taskManager: TaskManagerImpl

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("tool_approval_test").toFile()
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
    fun testSafeToolExecutesWithoutApprovalPrompt() = runBlocking {
        var executed = false
        toolRegistry.registerTool(
            ToolDefinition(
                id = "safe-read",
                name = "read_artifact",
                description = "safe read",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SAFE,
                handler = { _, _ ->
                    executed = true
                    ToolExecutionResult.success("""{"content":"test data"}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Safe Tool Test",
            goalDescription = "Execute safe tool",
            dotId = "dot-safe",
            conversationId = "conv-1",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Read Artifact", description = "", toolName = "read_artifact")
            )
        )

        val completed = taskManager.executeTask(task.id)

        assertTrue("Safe tool must execute immediately", executed)
        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertEquals(1, completed.completedSteps)
        assertTrue(taskManager.pendingApprovals.value.isEmpty())
    }

    @Test
    fun testSensitiveToolInterceptionAndApproveOnce() = runBlocking {
        var executed = false
        toolRegistry.registerTool(
            ToolDefinition(
                id = "sens-create",
                name = "create_artifact",
                description = "create artifact",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ ->
                    executed = true
                    ToolExecutionResult.success("""{"artifactId":"art-123"}""")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Sensitive Tool Test",
            goalDescription = "Execute sensitive tool",
            dotId = "dot-worker",
            conversationId = "conv-1",
            initialSteps = listOf(
                DotTaskStep(
                    taskId = "",
                    stepIndex = 0,
                    title = "Create Document",
                    description = "",
                    toolName = "create_artifact",
                    toolInputJson = """{"title":"Architecture","content":"spec"}"""
                )
            )
        )

        // Initial execution should halt awaiting approval
        val stepResult = taskManager.executeStep(task.id)
        assertEquals(StepStatus.AWAITING_APPROVAL, stepResult.status)

        val updatedTask = taskManager.getTaskById(task.id)
        assertNotNull(updatedTask)
        assertEquals(TaskStatus.AWAITING_APPROVAL, updatedTask?.status)
        assertEquals(StepStatus.AWAITING_APPROVAL, updatedTask?.steps?.get(0)?.status)

        // Pending approval request should be registered
        val pending = taskManager.getPendingApprovalForTask(task.id)
        assertNotNull(pending)
        assertEquals("create_artifact", pending?.toolName)
        assertEquals("dot-worker", pending?.dotId)

        // Resolve with APPROVED
        val resolved = taskManager.handleApprovalDecision(
            requestId = pending!!.id,
            decision = ToolApprovalDecision.APPROVED
        )

        assertNotNull(resolved)
        assertTrue("Tool must be executed after approval", executed)
        assertEquals(TaskStatus.COMPLETED, resolved?.status)
        assertEquals(StepStatus.COMPLETED, resolved?.steps?.get(0)?.status)

        // Check audit log
        val logs = auditLogger.getAuditLogs().first()
        val entry = logs.firstOrNull { it.taskId == task.id }
        assertNotNull(entry)
        assertEquals(ApprovalStatus.APPROVED_ONCE, entry?.approvalStatus)
        assertEquals("SUCCESS", entry?.executionStatus)
        assertTrue(entry?.wasSensitive == true)
    }

    @Test
    fun testAlwaysAllowGrantsBypassOnSubsequentInvocations() = runBlocking {
        var invocationCount = 0
        toolRegistry.registerTool(
            ToolDefinition(
                id = "sens-export",
                name = "export_document",
                description = "export document",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ ->
                    invocationCount++
                    ToolExecutionResult.success("""{"exported":true}""")
                }
            )
        )

        val task1 = taskManager.createTask(
            title = "Task 1",
            goalDescription = "First export",
            dotId = "dot-export-dot",
            conversationId = "conv-1",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Export 1", description = "", toolName = "export_document")
            )
        )

        // Step 1 halts
        taskManager.executeStep(task1.id)
        val pending1 = taskManager.getPendingApprovalForTask(task1.id)
        assertNotNull(pending1)

        // User chooses ALWAYS_ALLOWED
        taskManager.handleApprovalDecision(
            requestId = pending1!!.id,
            decision = ToolApprovalDecision.ALWAYS_ALLOWED
        )

        assertEquals(1, invocationCount)
        assertTrue("Permission must be remembered in registry",
            toolRegistry.hasAlwaysAllowedPermission("dot-export-dot", "export_document"))

        // Run Task 2 with same Dot and tool: must execute immediately without halting
        val task2 = taskManager.createTask(
            title = "Task 2",
            goalDescription = "Second export",
            dotId = "dot-export-dot",
            conversationId = "conv-1",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Export 2", description = "", toolName = "export_document")
            )
        )

        val completed2 = taskManager.executeTask(task2.id)
        assertEquals(TaskStatus.COMPLETED, completed2.status)
        assertEquals(2, invocationCount)
    }

    @Test
    fun testCriticalToolAlwaysRequiresApprovalEvenIfPriorApproved() = runBlocking {
        var criticalExecuted = false
        toolRegistry.registerTool(
            ToolDefinition(
                id = "crit-del",
                name = "delete_artifact",
                description = "delete artifact",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.CRITICAL,
                handler = { _, _ ->
                    criticalExecuted = true
                    ToolExecutionResult.success("""{"deleted":true}""")
                }
            )
        )

        // Even if someone manually sets permission in registry, CRITICAL must never bypass
        toolRegistry.setAlwaysAllowedPermission("dot-admin", "delete_artifact", true)

        val task = taskManager.createTask(
            title = "Critical Task",
            goalDescription = "Attempt destructive action",
            dotId = "dot-admin",
            conversationId = "conv-admin",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Delete", description = "", toolName = "delete_artifact")
            )
        )

        val result = taskManager.executeStep(task.id)

        // Must still halt for approval
        assertEquals(StepStatus.AWAITING_APPROVAL, result.status)
        val pending = taskManager.getPendingApprovalForTask(task.id)
        assertNotNull(pending)
        assertEquals("delete_artifact", pending?.toolName)
    }

    @Test
    fun testSensitiveToolRejection() = runBlocking {
        toolRegistry.registerTool(
            ToolDefinition(
                id = "sens-wipe",
                name = "forget_memory",
                description = "forget memory",
                category = ToolCategory.MEMORY,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ -> ToolExecutionResult.success("""{"forgotten":true}""") }
            )
        )

        val task = taskManager.createTask(
            title = "Rejection Task",
            goalDescription = "Attempt action that user rejects",
            dotId = "dot-mem",
            conversationId = "conv-mem",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Forget", description = "", toolName = "forget_memory")
            )
        )

        taskManager.executeStep(task.id)
        val pending = taskManager.getPendingApprovalForTask(task.id)
        assertNotNull(pending)

        // User rejects with reason
        val updated = taskManager.handleApprovalDecision(
            requestId = pending!!.id,
            decision = ToolApprovalDecision.REJECTED,
            rejectionReason = "I want to keep this memory fact"
        )

        assertNotNull(updated)
        assertEquals(TaskStatus.PAUSED, updated?.status)
        assertEquals(StepStatus.FAILED, updated?.steps?.get(0)?.status)
        assertEquals("I want to keep this memory fact", updated?.steps?.get(0)?.errorMessage)

        // Audit log records rejection
        val logs = auditLogger.getAuditLogs().first()
        val entry = logs.firstOrNull { it.taskId == task.id }
        assertNotNull(entry)
        assertEquals(ApprovalStatus.REJECTED, entry?.approvalStatus)
        assertEquals("REJECTED", entry?.executionStatus)
    }
}
