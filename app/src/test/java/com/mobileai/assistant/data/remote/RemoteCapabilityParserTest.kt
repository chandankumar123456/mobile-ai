package com.mobileai.assistant.data.remote

import com.mobileai.assistant.domain.model.ReasoningSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCapabilityParserTest {

    @Test
    fun parsesFullMetadata() {
        val payload = """
            {
              "object": "list",
              "data": [
                {
                  "id": "acme/vision-reasoner",
                  "context_length": 32768,
                  "architecture": {
                    "input_modalities": ["text", "image"],
                    "output_modalities": ["text"]
                  },
                  "supported_parameters": ["top_k", "min_p", "reasoning_effort", "tools"],
                  "top_provider": {"max_completion_tokens": 8192}
                }
              ]
            }
        """.trimIndent()

        val models = RemoteCapabilityParser.parseModels(payload)

        assertEquals(1, models.size)
        val info = models.first()
        assertEquals("acme/vision-reasoner", info.id)
        assertEquals(32768, info.contextLength)
        assertEquals(listOf("text", "image"), info.inputModalities)
        assertEquals(listOf("text"), info.outputModalities)
        assertEquals(listOf("top_k", "min_p", "reasoning_effort", "tools"), info.supportedParameters)
        assertEquals(8192, info.maxCompletionTokens)

        val capabilities = RemoteCapabilityParser.capabilitiesFor(info)
        assertTrue(capabilities.vision)
        assertTrue(capabilities.tools)
        assertTrue(capabilities.nativeSampling)
        assertEquals(ReasoningSupport.EFFORT, capabilities.reasoning)
        assertEquals(32768, capabilities.contextLength)
    }

    @Test
    fun parsesContextLengthAtTopLevelAndInArchitecture() {
        val payload = """
            {
              "data": [
                {"id": "top-level", "context_length": "8192"},
                {"id": "arch", "architecture": {"context_length": 4096}},
                {"id": "provider", "top_provider": {"context_length": 16384}}
              ]
            }
        """.trimIndent()

        val models = RemoteCapabilityParser.parseModels(payload).associateBy { it.id }

        assertEquals(8192, models.getValue("top-level").contextLength)
        assertEquals(4096, models.getValue("arch").contextLength)
        assertEquals(16384, models.getValue("provider").contextLength)
    }

    @Test
    fun toleratesMissingArchitectureAndTopProvider() {
        val payload = """
            {
              "data": [
                {"id": "plain-model", "object": "model"}
              ]
            }
        """.trimIndent()

        val info = RemoteCapabilityParser.parseModels(payload).single()

        assertEquals("plain-model", info.id)
        assertNull(info.contextLength)
        assertTrue(info.inputModalities.isEmpty())
        assertTrue(info.outputModalities.isEmpty())
        assertTrue(info.supportedParameters.isEmpty())
        assertNull(info.maxCompletionTokens)

        val capabilities = RemoteCapabilityParser.capabilitiesFor(info)
        assertFalse(capabilities.vision)
        assertFalse(capabilities.nativeSampling)
        assertFalse(capabilities.nativeContext)
    }

    @Test
    fun skipsMalformedEntriesAndSortsById() {
        val payload = """
            {
              "data": [
                {"id": "zeta", "context_length": 1024},
                {"context_length": 5},
                {"id": ""},
                null,
                "not-an-object",
                {"id": "alpha", "supported_parameters": "top_k"}
              ]
            }
        """.trimIndent()

        val ids = RemoteCapabilityParser.parseModels(payload).map { it.id }

        assertEquals(listOf("alpha", "zeta"), ids)
    }

    @Test
    fun returnsEmptyListForMalformedJson() {
        assertTrue(RemoteCapabilityParser.parseModels("not json").isEmpty())
        assertTrue(RemoteCapabilityParser.parseModels("{}").isEmpty())
        assertTrue(RemoteCapabilityParser.parseModels("""{"data": "nope"}""").isEmpty())
    }
}
