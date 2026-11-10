package com.mobileai.assistant.domain

import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.model.ThinkingCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingCapabilityTest {

    @Test
    fun testOpenAiReasoningModelsSupportEffort() {
        val o1Endpoint = RemoteEndpointConfig(name = "OpenAI", baseUrl = "https://api.openai.com/v1", selectedModel = "o1-mini")
        val target = InferenceTarget.Remote(o1Endpoint, "o1-mini")

        assertTrue(ThinkingCapability.isEffortSelectorSupported(target, null, o1Endpoint))
        assertTrue(ThinkingCapability.isReasoningEffortApiModel("o1-mini"))
        assertTrue(ThinkingCapability.isReasoningEffortApiModel("o3-mini"))
    }

    @Test
    fun testStandardModelsDoNotSupportReasoningEffortApi() {
        assertFalse(ThinkingCapability.isReasoningEffortApiModel("gpt-4o"))
        assertFalse(ThinkingCapability.isReasoningEffortApiModel("gpt-4o-mini"))
        assertFalse(ThinkingCapability.isReasoningEffortApiModel("claude-3-5-sonnet"))
    }

    @Test
    fun testDeepSeekR1SupportsThinking() {
        val r1Target = InferenceTarget.Remote(
            RemoteEndpointConfig(name = "DeepSeek", baseUrl = "https://api.deepseek.com", selectedModel = "deepseek-reasoner"),
            "deepseek-reasoner"
        )
        assertTrue(ThinkingCapability.isEffortSelectorSupported(r1Target, null, null))
        assertTrue(ThinkingCapability.isThinkingSupported(r1Target, null, null))
    }
}
