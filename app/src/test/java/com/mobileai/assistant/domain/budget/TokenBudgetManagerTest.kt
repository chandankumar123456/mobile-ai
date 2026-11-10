package com.mobileai.assistant.domain.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBudgetManagerTest {

    @Test
    fun testUsageAndCostCalculation() {
        val manager = TokenBudgetManager()
        val budget = TokenBudget(id = "session-1", maxTokens = 10_000, warnThresholdRatio = 0.8)
        manager.setBudget("session-1", budget)

        // 1. Record usage under warning threshold
        val status1 = manager.recordUsage(
            targetId = "session-1",
            modelId = "gpt-4o-mini",
            promptTokens = 2000,
            completionTokens = 1000
        )
        assertEquals(3000L, status1.usedTokens)
        assertFalse(status1.isWarningTriggered)
        assertFalse(status1.isHardLimitReached)
        assertTrue(status1.estimatedCostUsd > 0.0)

        // 2. Record additional usage exceeding 80% (8000 tokens)
        val status2 = manager.recordUsage(
            targetId = "session-1",
            modelId = "gpt-4o-mini",
            promptTokens = 3000,
            completionTokens = 3000
        )
        assertEquals(9000L, status2.usedTokens)
        assertTrue(status2.isWarningTriggered)
        assertFalse(status2.isHardLimitReached)

        // 3. Exceed hard limit (10,000 tokens)
        val status3 = manager.recordUsage(
            targetId = "session-1",
            modelId = "gpt-4o-mini",
            promptTokens = 1500,
            completionTokens = 500
        )
        assertEquals(11000L, status3.usedTokens)
        assertTrue(status3.isWarningTriggered)
        assertTrue(status3.isHardLimitReached)
    }
}

