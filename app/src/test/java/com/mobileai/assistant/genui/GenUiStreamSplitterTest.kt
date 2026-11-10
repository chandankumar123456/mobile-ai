package com.mobileai.assistant.genui

import com.mobileai.assistant.data.genui.GenUiSegment
import com.mobileai.assistant.data.genui.GenUiStreamSplitter
import org.junit.Assert.*
import org.junit.Test

class GenUiStreamSplitterTest {

    @Test
    fun testPureMarkdownTextReturnsSingleTextSegment() {
        val content = "This is a simple response without any generative UI blocks."
        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is GenUiSegment.Text)
        assertEquals(content, (segments[0] as GenUiSegment.Text).content)
    }

    @Test
    fun testSingleCompleteGenUiBlock() {
        val content = """
            Here is your dashboard summary:
            ```genui
            {"type": "metric", "label": "Revenue", "value": "$50K"}
            ```
            Let me know if you need further details.
        """.trimIndent()

        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is GenUiSegment.Text)
        assertTrue(segments[1] is GenUiSegment.Ui)
        assertTrue(segments[2] is GenUiSegment.Text)

        val ui = segments[1] as GenUiSegment.Ui
        assertTrue(ui.isComplete)
        assertTrue(ui.rawPayload.contains("\"Revenue\""))
    }

    @Test
    fun testMultipleInterleavedBlocks() {
        val content = """
            Introductory text.
            ```genui
            {"type": "metric", "label": "A", "value": "1"}
            ```
            Middle transition text.
            ```genui
            {"type": "metric", "label": "B", "value": "2"}
            ```
            Closing remarks.
        """.trimIndent()

        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        assertEquals(5, segments.size)
        assertTrue(segments[0] is GenUiSegment.Text)
        assertTrue(segments[1] is GenUiSegment.Ui)
        assertTrue(segments[2] is GenUiSegment.Text)
        assertTrue(segments[3] is GenUiSegment.Ui)
        assertTrue(segments[4] is GenUiSegment.Text)
    }

    @Test
    fun testStreamingIncompleteBlock() {
        val content = """
            Generating chart:
            ```genui
            {"type": "chart", "series": [{"data": [{"x": "Q1"
        """.trimIndent()

        val segments = GenUiStreamSplitter.split(content, isStreaming = true)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is GenUiSegment.Text)
        assertTrue(segments[1] is GenUiSegment.Ui)

        val ui = segments[1] as GenUiSegment.Ui
        assertFalse(ui.isComplete)
        assertTrue(ui.rawPayload.contains("\"chart\""))
    }

    @Test
    fun testAntiFlickerPrefixCandidateHolding() {
        val content = "Here is the result:\n```gen"
        val segments = GenUiStreamSplitter.split(content, isStreaming = true)
        assertEquals(1, segments.size)
        val text = segments[0] as GenUiSegment.Text
        assertEquals("Here is the result:", text.content)
    }

    @Test
    fun testOpenUiAliasSupport() {
        val content = """
            ```openui
            {"type": "button", "label": "Confirm", "actionId": "confirm"}
            ```
        """.trimIndent()

        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is GenUiSegment.Ui)
        val ui = segments[0] as GenUiSegment.Ui
        assertTrue(ui.isComplete)
        assertTrue(ui.rawPayload.contains("\"confirm\""))
    }
}

