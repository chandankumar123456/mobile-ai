package com.mobileai.assistant.task

import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.domain.tool.ApprovalStatus
import com.mobileai.assistant.domain.tool.ToolAuditLogEntry
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

class ToolAuditLoggerTest {

    private lateinit var tempDir: File
    private lateinit var auditLogger: ToolAuditLoggerImpl

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("tool_audit_test").toFile()
        auditLogger = ToolAuditLoggerImpl(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testLogAppendAndFlowEmission() = runBlocking {
        val entry1 = ToolAuditLogEntry(
            dotId = "dot-1",
            dotName = "Analyst",
            taskId = "task-1",
            stepId = "step-1",
            toolName = "read_artifact",
            argumentsJson = """{"artifactId":"art-1"}""",
            wasSensitive = false,
            approvalStatus = ApprovalStatus.APPROVED_ALWAYS,
            executionStatus = "SUCCESS",
            outputSummaryJson = """{"content":"data"}""",
            durationMs = 45L
        )

        val entry2 = ToolAuditLogEntry(
            dotId = "dot-2",
            dotName = "Writer",
            taskId = "task-2",
            stepId = "step-2",
            toolName = "create_artifact",
            argumentsJson = """{"title":"Guide"}""",
            wasSensitive = true,
            approvalStatus = ApprovalStatus.APPROVED_ONCE,
            executionStatus = "SUCCESS",
            outputSummaryJson = """{"id":"art-2"}""",
            durationMs = 120L
        )

        auditLogger.log(entry1)
        auditLogger.log(entry2)

        val allLogs = auditLogger.getAuditLogs().first()
        assertEquals(2, allLogs.size)
        assertEquals("read_artifact", allLogs[0].toolName)
        assertEquals("create_artifact", allLogs[1].toolName)
    }

    @Test
    fun testFilteringByDotAndTask() = runBlocking {
        val entryA = ToolAuditLogEntry(
            dotId = "dot-A",
            dotName = "A",
            taskId = "task-100",
            toolName = "tool_a",
            argumentsJson = "{}",
            wasSensitive = false,
            approvalStatus = ApprovalStatus.APPROVED_ALWAYS,
            executionStatus = "SUCCESS"
        )
        val entryB = ToolAuditLogEntry(
            dotId = "dot-B",
            dotName = "B",
            taskId = "task-200",
            toolName = "tool_b",
            argumentsJson = "{}",
            wasSensitive = true,
            approvalStatus = ApprovalStatus.APPROVED_ONCE,
            executionStatus = "SUCCESS"
        )

        auditLogger.log(entryA)
        auditLogger.log(entryB)

        val dotALogs = auditLogger.getLogsForDot("dot-A").first()
        assertEquals(1, dotALogs.size)
        assertEquals("tool_a", dotALogs[0].toolName)

        val task200Logs = auditLogger.getLogsForTask("task-200").first()
        assertEquals(1, task200Logs.size)
        assertEquals("tool_b", task200Logs[0].toolName)
    }

    @Test
    fun testPersistenceReloadFromFile() = runBlocking {
        val entry = ToolAuditLogEntry(
            dotId = "dot-persist",
            dotName = "PersistDot",
            taskId = "task-persist",
            toolName = "save_file_to_storage",
            argumentsJson = """{"fileName":"export.csv"}""",
            wasSensitive = true,
            approvalStatus = ApprovalStatus.APPROVED_ONCE,
            executionStatus = "SUCCESS",
            durationMs = 300L
        )

        auditLogger.log(entry)

        // Verify underlying file exists
        val logFile = File(tempDir, ToolAuditLoggerImpl.FILE_LOGS)
        assertTrue(logFile.exists())
        assertTrue(logFile.length() > 0)

        // Create a completely new instance pointing to the same folder
        val freshLogger = ToolAuditLoggerImpl(tempDir)
        val loaded = freshLogger.getAuditLogs().first()

        assertEquals(1, loaded.size)
        val loadedEntry = loaded[0]
        assertEquals("dot-persist", loadedEntry.dotId)
        assertEquals("save_file_to_storage", loadedEntry.toolName)
        assertEquals(300L, loadedEntry.durationMs)
        assertEquals(ApprovalStatus.APPROVED_ONCE, loadedEntry.approvalStatus)
    }

    @Test
    fun testClearLogs() = runBlocking {
        auditLogger.log(
            ToolAuditLogEntry(
                dotId = "dot-temp",
                dotName = "Temp",
                toolName = "inspect_task",
                argumentsJson = "{}",
                wasSensitive = false,
                approvalStatus = ApprovalStatus.APPROVED_ALWAYS,
                executionStatus = "SUCCESS"
            )
        )
        assertEquals(1, auditLogger.getAuditLogs().first().size)

        auditLogger.clearLogs()
        assertEquals(0, auditLogger.getAuditLogs().first().size)

        val reloaded = ToolAuditLoggerImpl(tempDir)
        assertEquals(0, reloaded.getAuditLogs().first().size)
    }
}
