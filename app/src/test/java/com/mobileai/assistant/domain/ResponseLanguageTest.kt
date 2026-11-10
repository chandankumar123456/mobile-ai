package com.mobileai.assistant.domain

import com.mobileai.assistant.domain.model.ResponseLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseLanguageTest {

    @Test
    fun testDetectsTelugu() {
        assertEquals(ResponseLanguage.TELUGU, ResponseLanguage.detectFromText("నమస్కారం"))
    }

    @Test
    fun testDetectsHindi() {
        assertEquals(ResponseLanguage.HINDI, ResponseLanguage.detectFromText("नमस्ते कैसे हैं आप"))
    }

    @Test
    fun testDetectsTamilKannadaMalayalamBengali() {
        assertEquals(ResponseLanguage.TAMIL, ResponseLanguage.detectFromText("வணக்கம்"))
        assertEquals(ResponseLanguage.KANNADA, ResponseLanguage.detectFromText("ನಮಸ್ಕಾರ"))
        assertEquals(ResponseLanguage.MALAYALAM, ResponseLanguage.detectFromText("നമസ്കാരം"))
        assertEquals(ResponseLanguage.BENGALI, ResponseLanguage.detectFromText("নমস্কার"))
    }

    @Test
    fun testDetectsUrduAndArabic() {
        assertEquals(ResponseLanguage.URDU, ResponseLanguage.detectFromText("السلام علیکم"))
        assertEquals(ResponseLanguage.ARABIC, ResponseLanguage.detectFromText("مرحبا بالعالم"))
    }

    @Test
    fun testDetectsCjkLanguages() {
        assertEquals(ResponseLanguage.JAPANESE, ResponseLanguage.detectFromText("こんにちは世界"))
        assertEquals(ResponseLanguage.KOREAN, ResponseLanguage.detectFromText("안녕하세요"))
        assertEquals(ResponseLanguage.CHINESE, ResponseLanguage.detectFromText("你好世界"))
    }

    @Test
    fun testAsciiFallsBackToAuto() {
        assertEquals(ResponseLanguage.AUTO, ResponseLanguage.detectFromText("Hello, how are you?"))
        assertEquals(ResponseLanguage.AUTO, ResponseLanguage.detectFromText(""))
        assertEquals(ResponseLanguage.AUTO, ResponseLanguage.detectFromText("te hi"))
    }

    @Test
    fun testBelowThresholdIsAuto() {
        assertEquals(ResponseLanguage.AUTO, ResponseLanguage.detectFromText("నమ"))
    }

    @Test
    fun testResolveUsesDetectionForAuto() {
        assertEquals(ResponseLanguage.TELUGU, ResponseLanguage.AUTO.resolve("నమస్కారం"))
        assertEquals(ResponseLanguage.HINDI, ResponseLanguage.AUTO.resolve("नमस्ते"))
        assertEquals(ResponseLanguage.AUTO, ResponseLanguage.AUTO.resolve("hello"))
    }

    @Test
    fun testResolveKeepsExplicitLanguage() {
        assertEquals(ResponseLanguage.FRENCH, ResponseLanguage.FRENCH.resolve("నమస్కారం"))
        assertEquals(ResponseLanguage.ENGLISH, ResponseLanguage.ENGLISH.resolve("नमस्ते"))
    }

    @Test
    fun testDirectiveContainsScriptAndNativeName() {
        val telugu = ResponseLanguage.TELUGU.directive
        assertTrue(telugu.contains("Telugu"))
        assertTrue(telugu.contains("తెలుగు"))
        assertTrue(telugu.contains("Do not switch languages"))

        val hindi = ResponseLanguage.HINDI.directive
        assertTrue(hindi.contains("Devanagari"))
        assertTrue(hindi.contains("हिन्दी"))
    }

    @Test
    fun testAutoHasNoDirective() {
        assertTrue(ResponseLanguage.AUTO.directive.isEmpty())
    }

    @Test
    fun testBcp47AndDisplayNames() {
        assertEquals("te", ResponseLanguage.TELUGU.bcp47)
        assertEquals("hi", ResponseLanguage.HINDI.bcp47)
        assertEquals("English", ResponseLanguage.ENGLISH.displayName)
        assertFalse(ResponseLanguage.AUTO.bcp47.isBlank())
    }
}
