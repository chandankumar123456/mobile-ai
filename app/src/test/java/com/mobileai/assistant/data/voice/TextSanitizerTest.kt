package com.mobileai.assistant.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun testStripThinkTags() {
        val input = "<think>Let me calculate 2+2...</think>The answer is 4."
        val sanitized = TextSanitizer.cleanForSpeech(input)
        assertEquals("The answer is 4.", sanitized)
        assertFalse(sanitized.contains("calculate"))
    }

    @Test
    fun testStripCodeBlocks() {
        val input = "Here is the code:\n```kotlin\nval x = 10\nprintln(x)\n```\nHope that helps!"
        val sanitized = TextSanitizer.cleanForSpeech(input)
        assertFalse(sanitized.contains("println"))
        assertFalse(sanitized.contains("```"))
        assertFalse(sanitized.contains("kotlin"))
    }

    @Test
    fun testStripMarkdownFormatting() {
        val input = "# Title\n\nThis is **bold**, *italic*, and `code` with a [link](https://example.com)."
        val sanitized = TextSanitizer.cleanForSpeech(input)
        assertEquals("Title This is bold, italic, and code with a link.", sanitized)
    }
}

