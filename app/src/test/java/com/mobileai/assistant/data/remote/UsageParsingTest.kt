package com.mobileai.assistant.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsageParsingTest {

    @Test
    fun parsesContentAndReasoningDeltas() {
        val reasoning = RemoteSseParser.parseChunkData(
            """{"choices":[{"delta":{"reasoning_content":"thinking"}}]}"""
        )
        assertEquals("thinking", reasoning?.reasoning)
        assertNull(reasoning?.content)

        val thought = RemoteSseParser.parseChunkData(
            """{"choices":[{"delta":{"thought":"thought text"}}]}"""
        )
        assertEquals("thought text", thought?.reasoning)

        val content = RemoteSseParser.parseChunkData(
            """{"choices":[{"delta":{"content":"hello"}}]}"""
        )
        assertEquals("hello", content?.content)
        assertNull(content?.reasoning)
    }

    @Test
    fun parsesUsageObject() {
        val chunk = RemoteSseParser.parseChunkData(
            """
                {
                  "choices": [{"delta": {}}],
                  "usage": {"prompt_tokens": 12, "completion_tokens": 34, "total_tokens": 46}
                }
            """.trimIndent()
        )

        assertNotNull(chunk)
        assertEquals(12, chunk?.usage?.promptTokens)
        assertEquals(34, chunk?.usage?.completionTokens)
        assertEquals(46, chunk?.usage?.totalTokens)
    }

    @Test
    fun acceptsUsageNumbersEncodedAsStrings() {
        val chunk = RemoteSseParser.parseChunkData(
            """{"choices":[],"usage":{"prompt_tokens":"5","completion_tokens":"7","total_tokens":"12"}}"""
        )

        assertEquals(5, chunk?.usage?.promptTokens)
        assertEquals(7, chunk?.usage?.completionTokens)
        assertEquals(12, chunk?.usage?.totalTokens)
    }

    @Test
    fun ignoresMalformedOrEmptyChunks() {
        assertNull(RemoteSseParser.parseChunkData("not json"))
        assertNull(RemoteSseParser.parseChunkData("{}"))
        assertNull(RemoteSseParser.parseChunkData("""{"choices":[{"delta":{}}]}"""))
        assertNull(RemoteSseParser.parseChunkData("""{"choices":[]}"""))
    }

    @Test
    fun accumulatorBuildsMetricsWithTokensPerSecond() {
        val accumulator = UsageAccumulator()
        accumulator.add(RemoteUsage(promptTokens = 10, completionTokens = 0, totalTokens = 10))
        accumulator.add(RemoteUsage(promptTokens = 10, completionTokens = 20, totalTokens = 30))
        accumulator.add(null)

        val metrics = accumulator.toMetrics(
            timeToFirstTokenMs = 500,
            totalDurationMs = 2500,
            contextSize = 4096,
            source = "REMOTE"
        )

        assertEquals(10, metrics.promptTokens)
        assertEquals(20, metrics.completionTokens)
        assertEquals(30, metrics.totalTokens)
        assertEquals(30, metrics.contextUsed)
        assertEquals(4096, metrics.contextSize)
        assertEquals(500L, metrics.timeToFirstTokenMs)
        assertEquals(2500L, metrics.totalDurationMs)
        assertEquals(10.0, metrics.tokensPerSecond ?: 0.0, 0.0001)
        assertEquals("REMOTE", metrics.source)
    }

    @Test
    fun accumulatorFallsBackToPromptPlusCompletionTotal() {
        val accumulator = UsageAccumulator()
        accumulator.add(RemoteUsage(promptTokens = 4, completionTokens = 6, totalTokens = 0))

        val metrics = accumulator.toMetrics(timeToFirstTokenMs = null, totalDurationMs = 1000)

        assertEquals(10, metrics.totalTokens)
        assertEquals(6.0, metrics.tokensPerSecond ?: 0.0, 0.0001)
        assertNull(metrics.timeToFirstTokenMs)
    }

    @Test
    fun accumulatorWithoutUsageProducesNoSpeed() {
        val metrics = UsageAccumulator().toMetrics(timeToFirstTokenMs = null, totalDurationMs = 1000)

        assertEquals(0, metrics.totalTokens)
        assertNull(metrics.tokensPerSecond)
        assertEquals("REMOTE", metrics.source)
    }
}
