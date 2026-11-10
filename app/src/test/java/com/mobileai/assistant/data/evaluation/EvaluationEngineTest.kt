package com.mobileai.assistant.data.evaluation

import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationEngineTest {

    @Test
    fun testEvaluationMetricsComputation() = runBlocking {
        val registry = DefaultToolRegistry()
        val engine = EvaluationEngine(
            toolRegistry = registry,
            taskManager = null,
            dotRepository = null
        )

        val metrics = engine.evaluateFullSystem("test-model")

        assertEquals("test-model", metrics.modelId)
        assertTrue(metrics.genUiValidationPassRate > 0.0)
        assertTrue(metrics.openUiParseSuccessRate > 0.0)
        assertTrue(metrics.a2UiTreeResolutionRate > 0.0)
        assertTrue(metrics.crossDotIsolationVerified)
        assertTrue(metrics.totalEvaluatedCases > 0)
    }
}

