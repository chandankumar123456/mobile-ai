package com.mobileai.assistant.genui

import com.mobileai.assistant.data.genui.GenUiValidationResult
import com.mobileai.assistant.data.genui.GenUiValidator
import com.mobileai.assistant.domain.genui.*
import org.junit.Assert.*
import org.junit.Test

class GenUiValidatorTest {

    @Test
    fun testValidMetricPassesValidation() {
        val payload = """{"type": "metric", "label": "CPU Usage", "value": "42%", "trend": -5.2, "trendDirection": "DOWN"}"""
        val result = GenUiValidator.validate(payload)
        assertTrue(result is GenUiValidationResult.Success)
        val metric = (result as GenUiValidationResult.Success).component as GenUiMetric
        assertEquals("CPU Usage", metric.label)
        assertEquals("42%", metric.value)
        assertEquals(-5.2, metric.trend!!, 0.001)
        assertEquals(TrendDirection.DOWN, metric.trendDirection)
    }

    @Test
    fun testTier1SyntaxRepairOnTruncatedPayload() {
        val truncated = """{"type": "card", "title": "In-Flight Card", "children": [{"type": "metric", "label": "Sales", "value": "100"""
        val result = GenUiValidator.validate(truncated, isStreaming = true)
        assertTrue(result is GenUiValidationResult.Success)
        val card = (result as GenUiValidationResult.Success).component as GenUiCard
        assertEquals("In-Flight Card", card.title)
        assertEquals(1, card.children.size)
        assertTrue(card.children[0] is GenUiMetric)
    }

    @Test
    fun testTier2IsolatedFallbackForUnknownChildComponent() {
        val payload = """
            {
                "type": "card",
                "title": "Mixed Card",
                "children": [
                    {"type": "metric", "label": "Valid Metric", "value": "99"},
                    {"type": "hologram_projector", "id": "holo_1"}
                ]
            }
        """.trimIndent()
        val result = GenUiValidator.validate(payload)
        assertTrue(result is GenUiValidationResult.Success)
        val card = (result as GenUiValidationResult.Success).component as GenUiCard
        assertEquals(2, card.children.size)
        assertTrue(card.children[0] is GenUiMetric)
        assertTrue(card.children[1] is GenUiFallback)
        val fallback = card.children[1] as GenUiFallback
        assertEquals("hologram_projector", fallback.unsupportedType)
    }

    @Test
    fun testTier3ValueCoercion() {
        // Trend as string percentage, missing id, case-insensitive enum
        val payload = """{"type": "METRIC", "label": "Growth", "value": "35%", "trend": "14.5%"}"""
        val result = GenUiValidator.validate(payload)
        assertTrue(result is GenUiValidationResult.Success)
        val metric = (result as GenUiValidationResult.Success).component as GenUiMetric
        assertEquals(14.5, metric.trend!!, 0.001)
        assertEquals(TrendDirection.UP, metric.trendDirection)
        assertTrue(metric.id.isNotEmpty())
    }

    @Test
    fun testTier4SafeDegradationForNonJsonObject() {
        val nonJson = "Hello this is just a plain string without JSON"
        val result = GenUiValidator.validate(nonJson)
        assertTrue(result is GenUiValidationResult.Malformed)
    }

    @Test
    fun testAntiDosMaxDepthLimitation() {
        // Build 12 levels of nested cards (exceeding MAX_NESTING_DEPTH = 8)
        var nested = """{"type": "metric", "label": "Deep Leaf", "value": "0"}"""
        for (i in 1..12) {
            nested = """{"type": "card", "title": "Level $i", "children": [$nested]}"""
        }

        val result = GenUiValidator.validate(nested)
        assertTrue(result is GenUiValidationResult.Success)
        // Deepest nodes will be replaced with GenUiFallback
        var curr = (result as GenUiValidationResult.Success).component as GenUiCard
        var depth = 1
        while (curr.children.isNotEmpty() && curr.children[0] is GenUiCard) {
            curr = curr.children[0] as GenUiCard
            depth++
        }
        assertTrue("Depth should be capped at <= 9", depth <= 9)
        assertTrue(curr.children[0] is GenUiFallback)
    }

    @Test
    fun testPayloadExceedingMaxSizeReturnsMalformed() {
        val hugePayload = "x".repeat(150 * 1024)
        val result = GenUiValidator.validate(hugePayload)
        assertTrue(result is GenUiValidationResult.Malformed)
        val malformed = result as GenUiValidationResult.Malformed
        assertTrue(malformed.error.contains("maximum allowable size"))
    }
}

