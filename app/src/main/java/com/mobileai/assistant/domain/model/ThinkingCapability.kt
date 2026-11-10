package com.mobileai.assistant.domain.model

/**
 * Determines whether a given model or inference target supports reasoning/thinking mode
 * and whether the thinking effort selector is applicable.
 */
object ThinkingCapability {

    /**
     * Checks if the active model/target supports thinking mode.
     */
    fun isThinkingSupported(
        target: InferenceTarget?,
        loadedModel: ModelInfo?,
        activeEndpoint: RemoteEndpointConfig?
    ): Boolean {
        val modelIdentifier = when {
            target is InferenceTarget.Local -> target.model.displayName
            target is InferenceTarget.Remote -> target.modelId
            loadedModel != null -> loadedModel.displayName
            activeEndpoint != null -> activeEndpoint.selectedModel
            else -> ""
        }
        return isThinkingCapableName(modelIdentifier)
    }

    /**
     * Checks if the Thinking Effort selector (Low/Medium/High/Max) is relevant for this model.
     * Relevant for OpenAI o-series (o1, o3, etc.) and deep-reasoning models (DeepSeek-R1, QwQ, etc.).
     */
    fun isEffortSelectorSupported(
        target: InferenceTarget?,
        loadedModel: ModelInfo?,
        activeEndpoint: RemoteEndpointConfig?
    ): Boolean {
        val modelIdentifier = when {
            target is InferenceTarget.Local -> target.model.displayName
            target is InferenceTarget.Remote -> target.modelId
            loadedModel != null -> loadedModel.displayName
            activeEndpoint != null -> activeEndpoint.selectedModel
            else -> ""
        }
        val lower = modelIdentifier.lowercase()
        return lower.contains("o1") || lower.contains("o3") || lower.contains("o4") ||
               lower.contains("r1") || lower.contains("reason") || lower.contains("qwq") ||
               lower.contains("thinking")
    }

    /**
     * Identifies OpenAI o-series models that accept the "reasoning_effort" API parameter.
     */
    fun isReasoningEffortApiModel(model: String): Boolean {
        val lower = model.lowercase()
        return lower.contains("o1") || lower.contains("o3") || lower.contains("o4")
    }

    private fun isThinkingCapableName(name: String): Boolean {
        if (name.isBlank()) return true // default to open if unknown
        val lower = name.lowercase()
        return lower.contains("o1") || lower.contains("o3") || lower.contains("o4") ||
               lower.contains("r1") || lower.contains("reason") || lower.contains("qwq") ||
               lower.contains("think") || lower.contains("cot") || lower.contains("instruct")
    }
}

