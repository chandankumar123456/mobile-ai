package com.mobileai.assistant.domain

import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointConfigTest {

    @Test
    fun testEndpointCleanBaseUrl() {
        val endpoint1 = RemoteEndpointConfig(
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1/",
            selectedModel = "gpt-4o"
        )
        assertEquals("https://api.openai.com/v1", endpoint1.cleanBaseUrl)
        assertTrue(endpoint1.isConfigured)

        val unconfigured = RemoteEndpointConfig(
            name = "Empty",
            baseUrl = "",
            selectedModel = ""
        )
        assertFalse(unconfigured.isConfigured)
    }

    @Test
    fun testInferenceTargetSwitching() {
        val localModel = ModelInfo(
            fileName = "llama3.2-1b.gguf",
            filePath = "/path/to/llama.gguf"
        )
        val localTarget = InferenceTarget.Local(localModel)
        assertTrue(localTarget.isLocal)
        assertFalse(localTarget.isRemote)
        assertEquals("llama3.2-1b", localTarget.displayName)

        val remoteConfig = RemoteEndpointConfig(
            name = "Ollama",
            baseUrl = "http://localhost:11434/v1",
            selectedModel = "qwen2.5:latest"
        )
        val remoteTarget = InferenceTarget.Remote(remoteConfig, "qwen2.5:latest")
        assertTrue(remoteTarget.isRemote)
        assertFalse(remoteTarget.isLocal)
        assertEquals("Ollama (qwen2.5:latest)", remoteTarget.displayName)
    }
}

