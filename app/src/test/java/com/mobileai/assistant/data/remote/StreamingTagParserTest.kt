package com.mobileai.assistant.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTagParserTest {

    @Test
    fun testCleanTagStreaming() {
        val parser = StreamingTagParser()
        val out1 = parser.process("<think>Analyzing question...")
        val out2 = parser.process("</think>Here is the response.")
        val finish = parser.finish()

        val all = out1 + out2 + finish
        val reasoning = all.filter { it.isThinking }.joinToString("") { it.reasoning ?: "" }
        val text = all.filter { !it.isThinking }.joinToString("") { it.text }

        assertEquals("Analyzing question...", reasoning)
        assertEquals("Here is the response.", text)
        assertFalse(text.contains("<think>"))
        assertFalse(text.contains("</think>"))
    }

    @Test
    fun testSplitTokenBoundaries() {
        // Tag split across individual token pieces: <, thin, k>
        val parser = StreamingTagParser()
        val tokens = listOf("<", "thin", "k>", "step 1", "</", "th", "ink>", "done")
        val outputs = mutableListOf<StreamingTagParser.Output>()

        for (token in tokens) {
            outputs.addAll(parser.process(token))
        }
        outputs.addAll(parser.finish())

        val reasoning = outputs.filter { it.isThinking }.joinToString("") { it.reasoning ?: "" }
        val text = outputs.filter { !it.isThinking }.joinToString("") { it.text }

        assertEquals("step 1", reasoning)
        assertEquals("done", text)
    }

    @Test
    fun testTextWithoutTags() {
        val parser = StreamingTagParser()
        val out = parser.process("Hello world! Just plain text.") + parser.finish()
        val text = out.joinToString("") { it.text }
        assertEquals("Hello world! Just plain text.", text)
        assertTrue(out.none { it.isThinking })
    }

    @Test
    fun testFalseAlarmPrefixes() {
        val parser = StreamingTagParser()
        val tokens = listOf("if (a < 3 && b > 4) { return; }")
        val out = tokens.flatMap { parser.process(it) } + parser.finish()
        val text = out.joinToString("") { it.text }
        assertEquals("if (a < 3 && b > 4) { return; }", text)
        assertTrue(out.none { it.isThinking })
    }
}

