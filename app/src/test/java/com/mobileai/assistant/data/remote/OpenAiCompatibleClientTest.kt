package com.mobileai.assistant.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientTest {

    private val client = OpenAiCompatibleClient()

    @Test
    fun testUrlNormalization() {
        assertEquals("https://api.openai.com/v1", client.normalizeUrl("https://api.openai.com/v1/"))
        assertEquals("http://192.168.1.100:11434", client.normalizeUrl("http://192.168.1.100:11434"))
        assertEquals("https://api.groq.com/openai/v1", client.normalizeUrl("api.groq.com/openai/v1"))
    }

    @Test
    fun testParseModelList() {
        val sampleJson = """
            {
                "object": "list",
                "data": [
                    {"id": "gpt-4o", "object": "model"},
                    {"id": "gpt-4o-mini", "object": "model"},
                    {"id": "o1-preview", "object": "model"}
                ]
            }
        """.trimIndent()

        // Reflection or test helper for parseModelList
        val method = OpenAiCompatibleClient::class.java.getDeclaredMethod("parseModelList", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val models = method.invoke(client, sampleJson) as List<String>

        assertEquals(3, models.size)
        assertTrue(models.contains("gpt-4o"))
        assertTrue(models.contains("o1-preview"))
    }
}

