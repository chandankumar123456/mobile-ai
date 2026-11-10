package com.mobileai.assistant.domain.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {

    @Test
    fun testInferenceErrorProperties() {
        val oom = AppError.InferenceError.OutOfMemory(requiredBytes = 4096)
        assertEquals("ERR_INFERENCE_OOM", oom.code)
        assertFalse(oom.isRecoverable)
        assertTrue(oom.userFriendlyMessage.contains("out of memory", ignoreCase = true))

        val contextOverflow = AppError.InferenceError.ContextOverflow(contextSize = 4096, maxTokens = 2048)
        assertEquals("ERR_INFERENCE_CONTEXT_OVERFLOW", contextOverflow.code)
        assertTrue(contextOverflow.isRecoverable)
    }

    @Test
    fun testToolAndAgentErrors() {
        val toolNotFound = AppError.ToolExecutionError.ToolNotFound("unknown_tool")
        assertEquals("ERR_TOOL_NOT_FOUND", toolNotFound.code)
        assertFalse(toolNotFound.isRecoverable)

        val approvalRejected = AppError.AgentTaskError.ApprovalRejected(
            taskId = "task_1",
            toolName = "delete_file",
            reason = "Sensitive path"
        )
        assertEquals("ERR_AGENT_APPROVAL_REJECTED", approvalRejected.code)
        assertTrue(approvalRejected.isRecoverable)
    }

    @Test
    fun testPersistenceErrors() {
        val checksumMismatch = AppError.PersistenceError.ChecksumMismatch(
            fileName = "dots.json",
            expected = "abc123",
            actual = "def456"
        )
        assertEquals("ERR_PERSISTENCE_CHECKSUM", checksumMismatch.code)
        assertTrue(checksumMismatch.isRecoverable)
    }
}

