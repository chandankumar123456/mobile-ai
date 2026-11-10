package com.mobileai.assistant.data.native_engine

import com.arm.aichat.NativeChatMessage
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.ResponseLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRequestFactoryTest {

    private fun build(
        messages: List<ChatMessage>,
        params: GenerationParams = GenerationParams(systemPrompt = "Base system")
    ): NativeMessageBuild = LocalRequestFactory.buildNativeMessages(
        GenerationRequest(messages = messages, params = params)
    )

    @Test
    fun systemPromptIsFirst() {
        val result = build(
            listOf(
                ChatMessage(content = "hello", isUser = true),
                ChatMessage(content = "hi there", isUser = false)
            )
        )

        assertEquals(NativeChatMessage(role = "system", content = "Base system"), result.messages.first())
        assertEquals(listOf("system", "user", "assistant"), result.messages.map { it.role })
        assertEquals("hello", result.messages[1].content)
        assertEquals("hi there", result.messages[2].content)
        assertFalse(result.continueFinalMessage)
    }

    @Test
    fun emptyTrailingStreamingAssistantIsSkipped() {
        val result = build(
            listOf(
                ChatMessage(content = "hello", isUser = true),
                ChatMessage(content = "", isUser = false, isStreaming = true)
            )
        )

        assertEquals(listOf("system", "user"), result.messages.map { it.role })
        assertFalse(result.continueFinalMessage)
    }

    @Test
    fun errorMessagesAreSkipped() {
        val result = build(
            listOf(
                ChatMessage(content = "hello", isUser = true),
                ChatMessage(content = "request failed", isUser = false, isError = true),
                ChatMessage(content = "try again", isUser = true)
            )
        )

        assertEquals(listOf("system", "user", "user"), result.messages.map { it.role })
        assertEquals(listOf("Base system", "hello", "try again"), result.messages.map { it.content })
    }

    @Test
    fun incompleteFinalAssistantIsIncludedAndContinues() {
        val result = build(
            listOf(
                ChatMessage(content = "hello", isUser = true),
                ChatMessage(
                    content = "partial answer",
                    isUser = false,
                    isStreaming = true,
                    isIncomplete = true
                )
            )
        )

        assertEquals("assistant", result.messages.last().role)
        assertEquals("partial answer", result.messages.last().content)
        assertTrue(result.continueFinalMessage)
    }

    @Test
    fun incompleteAssistantFollowedByUserDoesNotContinue() {
        val result = build(
            listOf(
                ChatMessage(content = "hello", isUser = true),
                ChatMessage(content = "partial answer", isUser = false, isIncomplete = true),
                ChatMessage(content = "new question", isUser = true)
            )
        )

        assertEquals(listOf("system", "user", "assistant", "user"), result.messages.map { it.role })
        assertFalse(result.continueFinalMessage)
    }

    @Test
    fun teluguLanguageDirectiveIsAppended() {
        val result = build(
            messages = listOf(ChatMessage(content = "hello", isUser = true)),
            params = GenerationParams(
                systemPrompt = "Base system",
                language = ResponseLanguage.TELUGU
            )
        )

        val system = result.messages.first()
        assertEquals("system", system.role)
        assertTrue(system.content.startsWith("Base system"))
        assertTrue(system.content.contains("Respond only in Telugu"))
        assertTrue(system.content.contains("తెలుగు"))
    }

    @Test
    fun autoLanguageDetectsTeluguFromLastUserMessage() {
        val result = build(
            messages = listOf(ChatMessage(content = "నమస్కారం", isUser = true)),
            params = GenerationParams(systemPrompt = "", language = ResponseLanguage.AUTO)
        )

        val system = result.messages.first()
        assertEquals("system", system.role)
        assertTrue(system.content.contains("Telugu"))
    }

    @Test
    fun blankSystemPromptAndAsciiAutoStartsWithUserMessage() {
        val result = build(
            messages = listOf(ChatMessage(content = "hello", isUser = true)),
            params = GenerationParams(systemPrompt = "", language = ResponseLanguage.AUTO)
        )

        assertEquals(listOf("user"), result.messages.map { it.role })
        assertEquals("user", result.messages.first().role)
    }
}
