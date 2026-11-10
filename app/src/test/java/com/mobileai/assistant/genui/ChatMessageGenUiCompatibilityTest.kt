package com.mobileai.assistant.genui

import com.mobileai.assistant.domain.model.ChatMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatMessageGenUiCompatibilityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testLegacyChatMessageDecodesWithoutGenUiFields() {
        val legacyJson = """
            {
                "id": "legacy_msg_1",
                "content": "Hello, how can I help you?",
                "isUser": false,
                "timestamp": 1700000000000,
                "isStreaming": false,
                "isError": false
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ChatMessage>(legacyJson)
        assertEquals("legacy_msg_1", decoded.id)
        assertEquals("Hello, how can I help you?", decoded.content)
        assertFalse(decoded.isUser)
        assertNull(decoded.genUiPayload)
        assertNull(decoded.genUiState)
        assertNull(decoded.genUiSubmittedValues)
    }

    @Test
    fun testExtendedChatMessageRoundTrip() {
        val msg = ChatMessage(
            id = "genui_msg_1",
            content = "Here is the form:",
            isUser = false,
            genUiPayload = """{"type": "form", "id": "f1"}""",
            genUiState = "SUBMITTED",
            genUiSubmittedValues = mapOf("age" to "30", "role" to "Engineer")
        )

        val serialized = json.encodeToString(ChatMessage.serializer(), msg)
        assertTrue(serialized.contains("\"genUiPayload\""))
        assertTrue(serialized.contains("\"genUiState\":\"SUBMITTED\""))
        assertTrue(serialized.contains("\"genUiSubmittedValues\""))

        val decoded = json.decodeFromString<ChatMessage>(serialized)
        assertEquals("genui_msg_1", decoded.id)
        assertEquals("""{"type": "form", "id": "f1"}""", decoded.genUiPayload)
        assertEquals("SUBMITTED", decoded.genUiState)
        assertEquals("30", decoded.genUiSubmittedValues?.get("age"))
        assertEquals("Engineer", decoded.genUiSubmittedValues?.get("role"))
    }
}

