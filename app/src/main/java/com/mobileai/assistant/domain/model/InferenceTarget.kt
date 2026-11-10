package com.mobileai.assistant.domain.model

/**
 * Represents the active model or endpoint chosen by the user for inference.
 * Keeps local model execution and external remote endpoints strictly decoupled.
 */
sealed interface InferenceTarget {
    data class Local(val model: ModelInfo) : InferenceTarget
    data class Remote(val endpoint: RemoteEndpointConfig, val modelId: String) : InferenceTarget

    val displayName: String
        get() = when (this) {
            is Local -> model.displayName
            is Remote -> "${endpoint.name} ($modelId)"
        }

    val isLocal: Boolean
        get() = this is Local

    val isRemote: Boolean
        get() = this is Remote
}

