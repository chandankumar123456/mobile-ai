package com.mobileai.assistant.data.trace

import com.mobileai.assistant.domain.trace.TraceCategory
import com.mobileai.assistant.domain.trace.TraceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TraceManagerTest {

    @Before
    fun setUp() {
        TraceManager.clear()
    }

    @Test
    fun testLogAndQueryByCorrelationId() {
        TraceManager.log(
            correlationId = "corr-100",
            category = TraceCategory.INFERENCE,
            tag = "LlamaCpp",
            message = "Model loaded successfully",
            durationMs = 250L
        )

        TraceManager.log(
            correlationId = "corr-100",
            category = TraceCategory.TOOL_CALL,
            tag = "ToolRegistry",
            message = "Invoked read_artifact",
            durationMs = 15L
        )

        TraceManager.log(
            correlationId = "corr-200",
            category = TraceCategory.MCP,
            tag = "McpClient",
            message = "Ping server"
        )

        val corr100Events = TraceManager.getEventsForCorrelation("corr-100")
        assertEquals(2, corr100Events.size)
        assertEquals(TraceCategory.INFERENCE, corr100Events[0].category)
        assertEquals(TraceCategory.TOOL_CALL, corr100Events[1].category)

        val export = TraceManager.exportJson()
        assertTrue(export.contains("corr-100"))
        assertTrue(export.contains("Model loaded successfully"))
    }
}

