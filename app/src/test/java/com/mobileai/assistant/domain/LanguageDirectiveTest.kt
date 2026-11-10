package com.mobileai.assistant.domain

import com.mobileai.assistant.domain.model.PromptBuilder
import com.mobileai.assistant.domain.model.ResponseLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDirectiveTest {

    @Test
    fun testTeluguDirectiveContainsNativeScriptAndInstructions() {
        val basePrompt = "You are a helpful assistant."
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.TELUGU,
            userText = "Can you help me?"
        )

        assertTrue("Expected prompt to contain Telugu native script 'తెలుగు'", prompt.contains("తెలుగు"))
        assertTrue("Expected prompt to contain 'Telugu' instructions", prompt.contains("Respond only in Telugu"))
        assertTrue("Expected prompt to contain Telugu script name", prompt.contains("Telugu script"))
    }

    @Test
    fun testHindiDirectiveContainsNativeScript() {
        val basePrompt = "You are a helpful assistant."
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.HINDI,
            userText = "Can you help me?"
        )

        assertTrue("Expected prompt to contain Hindi native script 'हिन्दी'", prompt.contains("हिन्दी"))
        assertTrue("Expected prompt to contain 'Hindi'", prompt.contains("Respond only in Hindi"))
        assertTrue("Expected prompt to contain Devanagari script name", prompt.contains("Devanagari script"))
    }

    @Test
    fun testEnglishDirectiveContainsEnglish() {
        val basePrompt = "You are a helpful assistant."
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.ENGLISH,
            userText = "Can you help me?"
        )

        assertTrue("Expected prompt to contain 'English'", prompt.contains("Respond only in English"))
        assertTrue("Expected prompt to contain Latin script name", prompt.contains("Latin script"))
    }

    @Test
    fun testAutoWithTeluguUserTextResolvesToTelugu() {
        val basePrompt = "You are a helpful assistant."
        val teluguUserText = "నమస్కారం ఎలా ఉన్నారు?"
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.AUTO,
            userText = teluguUserText
        )

        assertTrue("Expected AUTO with Telugu text to resolve to Telugu directive", prompt.contains("తెలుగు"))
        assertTrue(prompt.contains("Respond only in Telugu"))
    }

    @Test
    fun testAutoWithHindiUserTextResolvesToHindi() {
        val basePrompt = "You are a helpful assistant."
        val hindiUserText = "नमस्ते, आप कैसे हैं?"
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.AUTO,
            userText = hindiUserText
        )

        assertTrue("Expected AUTO with Hindi text to resolve to Hindi directive", prompt.contains("हिन्दी"))
        assertTrue(prompt.contains("Respond only in Hindi"))
    }

    @Test
    fun testAutoWithEnglishAsciiUserTextResolvesToAutoWithoutDirective() {
        val basePrompt = "You are a helpful assistant."
        val englishUserText = "Hello, what is the capital of France?"
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.AUTO,
            userText = englishUserText
        )

        assertEquals("Expected AUTO with English/ASCII text to return base prompt unchanged without directives", basePrompt, prompt)
    }

    @Test
    fun testBaseSystemPromptPreservedAndAppendedCleanlyWithDoubleNewline() {
        val basePrompt = "You are a helpful assistant specialized in mathematics."
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.TELUGU,
            userText = "hello"
        )

        val expected = basePrompt + "\n\n" + ResponseLanguage.TELUGU.directive
        assertEquals("Prompt should preserve base prompt and cleanly append directive with double newline", expected, prompt)
        assertTrue(prompt.startsWith(basePrompt))
        assertTrue(prompt.endsWith(ResponseLanguage.TELUGU.directive))
    }

    @Test
    fun testBaseSystemPromptWithTrailingWhitespaceTrimmedCleanly() {
        val basePrompt = "You are a helpful assistant specialized in mathematics.  \n\n\n  "
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = basePrompt,
            language = ResponseLanguage.HINDI,
            userText = "hello"
        )

        val expected = "You are a helpful assistant specialized in mathematics.\n\n" + ResponseLanguage.HINDI.directive
        assertEquals("Prompt should trim trailing whitespace and separate with double newline", expected, prompt)
    }

    @Test
    fun testBlankBasePromptReturnsOnlyDirective() {
        val prompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "   ",
            language = ResponseLanguage.TELUGU,
            userText = "hello"
        )

        assertEquals(ResponseLanguage.TELUGU.directive, prompt)
        assertFalse(prompt.startsWith("\n"))
    }

    @Test
    fun testOtherIndicLanguagesNativeScriptDirectives() {
        val tamilPrompt = PromptBuilder.buildEffectiveSystemPrompt("Base", ResponseLanguage.TAMIL, "hello")
        assertTrue(tamilPrompt.contains("தமிழ்"))
        assertTrue(tamilPrompt.contains("Tamil"))

        val kannadaPrompt = PromptBuilder.buildEffectiveSystemPrompt("Base", ResponseLanguage.KANNADA, "hello")
        assertTrue(kannadaPrompt.contains("ಕನ್ನಡ"))
        assertTrue(kannadaPrompt.contains("Kannada"))

        val malayalamPrompt = PromptBuilder.buildEffectiveSystemPrompt("Base", ResponseLanguage.MALAYALAM, "hello")
        assertTrue(malayalamPrompt.contains("മലയാളം"))
        assertTrue(malayalamPrompt.contains("Malayalam"))

        val bengaliPrompt = PromptBuilder.buildEffectiveSystemPrompt("Base", ResponseLanguage.BENGALI, "hello")
        assertTrue(bengaliPrompt.contains("বাংলা"))
        assertTrue(bengaliPrompt.contains("Bengali"))
    }

    @Test
    fun testExplicitLanguageWinsOverUserText() {
        val hindiPrompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = "Base",
            language = ResponseLanguage.HINDI,
            userText = "నమస్కారం" // Telugu input
        )

        assertTrue(hindiPrompt.contains("हिन्दी"))
        assertFalse(hindiPrompt.contains("తెలుగు"))
    }
}
