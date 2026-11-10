package com.mobileai.assistant.domain

import com.mobileai.assistant.domain.model.CapabilitySource
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ReasoningSupport
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilitiesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRemoteReasoningEffortModel() {
        val capabilities = ModelCapabilitiesResolver.fromRemote("o3-mini")

        assertEquals(ReasoningSupport.EFFORT, capabilities.reasoning)
        assertTrue(capabilities.reasoningEfforts.isNotEmpty())
        assertFalse(capabilities.nativeSampling)
        assertFalse(capabilities.nativeContext)
        assertEquals(CapabilitySource.HEURISTIC, capabilities.source)
    }

    @Test
    fun testRemoteToggleReasoningModel() {
        val capabilities = ModelCapabilitiesResolver.fromRemote("deepseek-r1-distill")

        assertEquals(ReasoningSupport.TOGGLE, capabilities.reasoning)
        assertTrue(capabilities.reasoningEfforts.isEmpty())
    }

    @Test
    fun testRemoteStandardModelHasConservativeDefaults() {
        val capabilities = ModelCapabilitiesResolver.fromRemote("gpt-4o-mini")

        assertEquals(ReasoningSupport.NONE, capabilities.reasoning)
        assertFalse(capabilities.vision)
        assertFalse(capabilities.tools)
        assertFalse(capabilities.nativeSampling)
        assertFalse(capabilities.nativeContext)
        assertFalse(capabilities.nativeThreads)
    }

    @Test
    fun testRemoteMetadataWinsOverHeuristics() {
        val capabilities = ModelCapabilitiesResolver.fromRemote(
            modelId = "custom-model",
            contextLength = 32768,
            inputModalities = listOf("text", "image"),
            supportedParameters = listOf("top_k", "min_p", "tools", "response_format")
        )

        assertTrue(capabilities.vision)
        assertTrue(capabilities.tools)
        assertTrue(capabilities.jsonMode)
        assertTrue(capabilities.nativeSampling)
        assertTrue(capabilities.nativeContext)
        assertEquals(32768, capabilities.contextLength)
        assertEquals(CapabilitySource.METADATA, capabilities.source)
    }

    @Test
    fun testLocalUsesNativeRuntimeDefaults() {
        val capabilities = ModelCapabilitiesResolver.fromLocal(
            ModelInfo(fileName = "qwen2.5-7b-instruct-q4.gguf", filePath = "/models/qwen.gguf")
        )

        assertTrue(capabilities.nativeSampling)
        assertTrue(capabilities.nativeContext)
        assertTrue(capabilities.nativeThreads)
        assertFalse(capabilities.vision)
        assertEquals(ReasoningSupport.NONE, capabilities.reasoning)
    }

    @Test
    fun testLocalVisionHeuristicsAndProjector() {
        val byName = ModelCapabilitiesResolver.fromLocal(
            ModelInfo(fileName = "Qwen2.5-VL-7B-Instruct.gguf", filePath = "/models/qwen-vl.gguf")
        )
        assertTrue(byName.vision)

        val byProjector = ModelCapabilitiesResolver.fromLocal(
            ModelInfo(fileName = "llama-3.2-3b.gguf", filePath = "/models/llama.gguf"),
            hasVisionProjector = true
        )
        assertTrue(byProjector.vision)
    }

    @Test
    fun testLocalMetadataPreserved() {
        val model = ModelInfo(
            fileName = "gemma3-4b.gguf",
            filePath = "/models/gemma3.gguf",
            contextLength = 8192
        )
        val capabilities = ModelCapabilitiesResolver.fromLocal(
            modelInfo = model,
            chatTemplate = "{{ messages }}",
            tags = listOf("vision"),
            languages = listOf("en", "te")
        )

        assertEquals(CapabilitySource.METADATA, capabilities.source)
        assertEquals(8192, capabilities.contextLength)
        assertEquals("{{ messages }}", capabilities.chatTemplate)
        assertNotNull(capabilities.maxOutputTokens)
        assertTrue(capabilities.multilingual)
    }

    @Test
    fun testManualOverridesWin() {
        val base = ModelCapabilitiesResolver.fromLocal(
            ModelInfo(fileName = "qwen2.5-7b.gguf", filePath = "/models/qwen.gguf")
        )
        val override = base.copy(
            vision = true,
            tools = true,
            contextLength = 16384,
            reasoning = ReasoningSupport.TOGGLE
        )

        val merged = ModelCapabilitiesResolver.applyOverrides(base, override)

        assertTrue(merged.vision)
        assertTrue(merged.tools)
        assertEquals(16384, merged.contextLength)
        assertEquals(ReasoningSupport.TOGGLE, merged.reasoning)
        assertEquals(CapabilitySource.MANUAL, merged.source)
    }

    @Test
    fun testNullOverridesReturnBase() {
        val base = ModelCapabilities.DEFAULT.copy(vision = true)

        assertEquals(base, ModelCapabilitiesResolver.applyOverrides(base, null))
    }

    @Test
    fun testSerializationDefaultsAllowOldJson() {
        val decoded = json.decodeFromString<ModelCapabilities>("{}")

        assertEquals(ModelCapabilities.DEFAULT, decoded)
    }
}
