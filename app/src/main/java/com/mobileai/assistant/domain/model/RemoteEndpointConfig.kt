package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Configuration model for custom external or self-hosted AI inference endpoints.
 */
@Serializable
data class RemoteEndpointConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String? = null,
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val cleanBaseUrl: String
        get() = baseUrl.trimEnd('/')

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && selectedModel.isNotBlank()
}

