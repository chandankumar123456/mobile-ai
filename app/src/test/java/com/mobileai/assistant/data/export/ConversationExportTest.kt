package com.mobileai.assistant.data.export

import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.AttachmentKind
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationExportTest {

    private lateinit var exportService: ConversationExportService

    @Before
    fun setUp() {
        exportService = ConversationExportService()
    }

    @Test
    fun testMarkdownExportContainsAllElements() {
        val attachment = Attachment(
            fileName = "diagram.png",
            mimeType = "image/png",
            sizeBytes = 2048,
            uri = "file:///tmp/diagram.png",
            kind = AttachmentKind.IMAGE
        )

        val userMessage = ChatMessage(
            content = "Explain the diagram attached.",
            isUser = true,
            attachments = listOf(attachment)
        )

        val assistantMessage = ChatMessage(
            content = "This diagram shows a client-server architecture.",
            isUser = false,
            modelId = "llama-3-8b",
            modelName = "Llama 3 8B",
            reasoning = "The user has provided an architecture diagram.\nLet's analyze the components.",
            metrics = RuntimeMetrics(
                completionTokens = 42,
                totalTokens = 128,
                tokensPerSecond = 24.5,
                totalDurationMs = 1714
            )
        )

        val session = ConversationSession(
            title = "System Architecture Discussion",
            targetId = "local:llama-3",
            responseLanguage = ResponseLanguage.ENGLISH,
            systemPromptOverride = "You are a software architect.",
            messages = listOf(userMessage, assistantMessage)
        )

        val markdown = exportService.exportMarkdown(session)

        // Title and metadata
        assertTrue(markdown.contains("# System Architecture Discussion"))
        assertTrue(markdown.contains("- **Target:** local:llama-3"))
        assertTrue(markdown.contains("- **Language:** English"))
        assertTrue(markdown.contains("- **System Prompt:** You are a software architect."))

        // User heading & attachment
        assertTrue(markdown.contains("### User:"))
        assertTrue(markdown.contains("Explain the diagram attached."))
        assertTrue(markdown.contains("diagram.png (image/png, 2048 bytes)"))

        // Assistant heading with model attribution
        assertTrue(markdown.contains("### Assistant (Model: Llama 3 8B):"))
        assertTrue(markdown.contains("This diagram shows a client-server architecture."))

        // Thought process
        assertTrue(markdown.contains("<details><summary>Thought Process</summary>"))
        assertTrue(markdown.contains("The user has provided an architecture diagram."))
        assertTrue(markdown.contains("</details>"))

        // Metrics
        assertTrue(markdown.contains("*Metrics: 42 tokens, 24.5 t/s, 1714ms*"))
    }

    @Test
    fun testJsonRoundTripPreservesAllFields() {
        val original = ConversationSession(
            title = "Test Session",
            targetId = "remote:gpt-4o",
            responseLanguage = ResponseLanguage.FRENCH,
            systemPromptOverride = "Réponds en français.",
            messages = listOf(
                ChatMessage(
                    content = "Bonjour!",
                    isUser = true,
                    timestamp = 1000L
                ),
                ChatMessage(
                    content = "Bonjour! Comment puis-je vous aider?",
                    isUser = false,
                    timestamp = 2000L,
                    modelId = "gpt-4o",
                    modelName = "GPT-4o",
                    reasoning = "Thinking in French...",
                    metrics = RuntimeMetrics(completionTokens = 10, totalTokens = 20, tokensPerSecond = 30.0)
                )
            )
        )

        val jsonString = exportService.exportJson(original)
        assertTrue(jsonString.contains("Test Session"))
        assertTrue(jsonString.contains("gpt-4o"))

        val importedResult = exportService.importJson(jsonString)
        assertTrue(importedResult.isSuccess)

        val imported = importedResult.getOrThrow()
        assertEquals(original.id, imported.id)
        assertEquals(original.title, imported.title)
        assertEquals(original.targetId, imported.targetId)
        assertEquals(original.responseLanguage, imported.responseLanguage)
        assertEquals(original.systemPromptOverride, imported.systemPromptOverride)
        assertEquals(original.messages.size, imported.messages.size)
        assertEquals(original.messages[0].content, imported.messages[0].content)
        assertEquals(original.messages[1].content, imported.messages[1].content)
        assertEquals(original.messages[1].reasoning, imported.messages[1].reasoning)
        assertEquals(original.messages[1].metrics?.completionTokens, imported.messages[1].metrics?.completionTokens)
    }

    @Test
    fun testExportAllJsonAndImportArray() {
        val session1 = ConversationSession(title = "Session One")
        val session2 = ConversationSession(title = "Session Two")

        val allJson = exportService.exportAllJson(listOf(session1, session2))
        assertTrue(allJson.trim().startsWith("["))
        assertTrue(allJson.contains("Session One"))
        assertTrue(allJson.contains("Session Two"))

        // Importing from array yields the first session
        val importedResult = exportService.importJson(allJson)
        assertTrue(importedResult.isSuccess)
        assertEquals("Session One", importedResult.getOrThrow().title)
    }

    @Test
    fun testImportEnvelopeJson() {
        val envelopeJson = """
            {
                "version": 2,
                "activeConversationId": "session-2",
                "conversations": [
                    { "id": "session-1", "title": "First Chat", "messages": [] },
                    { "id": "session-2", "title": "Second Active Chat", "messages": [] }
                ]
            }
        """.trimIndent()

        val result = exportService.importJson(envelopeJson)
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("session-2", session.id)
        assertEquals("Second Active Chat", session.title)
    }

    @Test
    fun testImportJsonWithUnknownKeysGracefully() {
        val forwardCompatibleJson = """
            {
                "id": "future-session",
                "title": "Future Chat",
                "messages": [],
                "unsupportedFutureField": "ignored value",
                "extraObject": { "nested": 123 }
            }
        """.trimIndent()

        val result = exportService.importJson(forwardCompatibleJson)
        assertTrue(result.isSuccess)
        assertEquals("future-session", result.getOrThrow().id)
        assertEquals("Future Chat", result.getOrThrow().title)
    }

    @Test
    fun testImportMalformedJsonReturnsFailure() {
        val badJson = "{ not valid json "
        val result = exportService.importJson(badJson)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())

        val emptyJson = "   "
        val emptyResult = exportService.importJson(emptyJson)
        assertTrue(emptyResult.isFailure)
    }
}
