package com.mobileai.assistant.domain

import com.mobileai.assistant.domain.model.PromptBuilder
import com.mobileai.assistant.domain.model.ResponseLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun testDirectiveAppendedLast() {
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "You are a helpful assistant.",
            language = ResponseLanguage.TELUGU,
            userText = "hello"
        )

        assertTrue(prompt.startsWith("You are a helpful assistant."))
        assertTrue(prompt.endsWith(ResponseLanguage.TELUGU.directive))
        assertTrue(prompt.indexOf("You are a helpful assistant.") < prompt.indexOf("Respond only in Telugu"))
    }

    @Test
    fun testAutoLanguageDetectedFromUserText() {
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "Base",
            language = ResponseLanguage.AUTO,
            userText = "నమస్కారం"
        )

        assertTrue(prompt.contains("Telugu"))
        assertTrue(prompt.contains("తెలుగు"))
    }

    @Test
    fun testAutoWithAsciiReturnsBaseUnchanged() {
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "Base prompt",
            language = ResponseLanguage.AUTO,
            userText = "hello"
        )

        assertEquals("Base prompt", prompt)
    }

    @Test
    fun testBlankBaseReturnsOnlyDirective() {
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "   ",
            language = ResponseLanguage.HINDI,
            userText = "hello"
        )

        assertEquals(ResponseLanguage.HINDI.directive, prompt)
    }

    @Test
    fun testBlankBaseAndAutoReturnsBlank() {
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "",
            language = ResponseLanguage.AUTO,
            userText = "hello"
        )

        assertEquals("", prompt)
    }

    @Test
    fun testLanguageDirectiveResolvesAuto() {
        assertEquals(
            ResponseLanguage.HINDI.directive,
            PromptBuilder.languageDirective(ResponseLanguage.AUTO, "नमस्ते")
        )
        assertEquals(
            "",
            PromptBuilder.languageDirective(ResponseLanguage.AUTO, "hello")
        )
    }

    @Test
    fun testExplicitLanguageWinsOverText() {
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "Base",
            language = ResponseLanguage.FRENCH,
            userText = "నమస్కారం"
        )

        assertTrue(prompt.contains("French"))
        assertTrue(prompt.contains("Français"))
    }

    @Test
    fun testDeterministicOutput() {
        val first = PromptBuilder.buildEffectiveSystemPrompt("Base", ResponseLanguage.TELUGU, "hello")
        val second = PromptBuilder.buildEffectiveSystemPrompt("Base", ResponseLanguage.TELUGU, "hello")

        assertEquals(first, second)
    }
}
