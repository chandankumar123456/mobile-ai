package com.mobileai.assistant.genui

import com.mobileai.assistant.data.genui.GenUiParseResult
import com.mobileai.assistant.data.genui.StreamingGenUiParser
import com.mobileai.assistant.domain.genui.GenUiMetric
import org.junit.Assert.*
import org.junit.Test

class StreamingGenUiParserTest {

    @Test
    fun testIncrementalChunksYieldResults() {
        val parser = StreamingGenUiParser()

        // 1. First text chunk
        val r1 = parser.feedChunk("Here is a metric:\n```genui\n{\"type\": \"metric\", ")
        assertTrue(r1.any { it is GenUiParseResult.Text })
        assertTrue(r1.any { it is GenUiParseResult.PartialUi })

        // 2. Second chunk completing the payload
        val r2 = parser.feedChunk("\"label\": \"Users\", \"value\": \"1,000\"}\n```\nDone!")
        assertTrue(r2.any { it is GenUiParseResult.CompleteUi })
        assertTrue(r2.any { it is GenUiParseResult.Text })

        // 3. Finish
        val r3 = parser.finish()
        val completeUi = r3.filterIsInstance<GenUiParseResult.CompleteUi>().firstOrNull()
        assertNotNull(completeUi)
        assertTrue(completeUi!!.component is GenUiMetric)
        val metric = completeUi.component as GenUiMetric
        assertEquals("Users", metric.label)
        assertEquals("1,000", metric.value)
    }
}

