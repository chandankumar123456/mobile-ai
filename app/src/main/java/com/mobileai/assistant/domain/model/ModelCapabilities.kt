package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CapabilitySource { METADATA, HEURISTIC, MANUAL }

@Serializable
enum class ReasoningSupport { NONE, TOGGLE, EFFORT }

@Serializable
data class ModelCapabilities(
    val reasoning: ReasoningSupport = ReasoningSupport.NONE,
    val reasoningEfforts: Set<ThinkingEffort> = emptySet(),
    val vision: Boolean = false,
    val tools: Boolean = false,
    val jsonMode: Boolean = false,
    val nativeSampling: Boolean = false,
    val nativeContext: Boolean = false,
    val nativeThreads: Boolean = false,
    val multilingual: Boolean = false,
    val contextLength: Int? = null,
    val maxOutputTokens: Int? = null,
    val chatTemplate: String? = null,
    val source: CapabilitySource = CapabilitySource.HEURISTIC
) {
    companion object {
        val DEFAULT = ModelCapabilities()
        val FALLBACK = DEFAULT
    }
}

object ModelCapabilitiesResolver {

    private const val DEFAULT_MAX_OUTPUT_TOKENS = 4096

    private val EFFORT_TOKENS = listOf("o1", "o3", "o4", "gpt-5", "gpt5")
    private val TOGGLE_TOKENS = listOf("r1", "reason", "qwq", "think", "cot")
    private val VISION_TOKENS = listOf("vl", "vision", "llava", "gemma3", "multimodal")
    private val TOOL_TOKENS = listOf("tool", "function")

    fun fromLocal(
        modelInfo: ModelInfo,
        chatTemplate: String? = null,
        tags: List<String> = emptyList(),
        languages: List<String> = emptyList(),
        hasVisionProjector: Boolean = false
    ): ModelCapabilities {
        val name = modelInfo.displayName.lowercase()
        val reasoning = detectReasoning(name)
        val effectiveTemplate = chatTemplate ?: modelInfo.chatTemplate
        val source = when {
            effectiveTemplate != null || modelInfo.contextLength != null || tags.isNotEmpty() ->
                CapabilitySource.METADATA
            else -> CapabilitySource.HEURISTIC
        }

        return ModelCapabilities(
            reasoning = reasoning,
            reasoningEfforts = effortsFor(reasoning),
            vision = hasVisionProjector || containsAny(name, VISION_TOKENS),
            tools = containsAny(name, TOOL_TOKENS),
            jsonMode = false,
            nativeSampling = true,
            nativeContext = true,
            nativeThreads = true,
            multilingual = languages.isNotEmpty() || name.contains("multilingual"),
            contextLength = modelInfo.contextLength,
            maxOutputTokens = modelInfo.contextLength
                ?.takeIf { it > 0 }
                ?.coerceAtMost(DEFAULT_MAX_OUTPUT_TOKENS),
            chatTemplate = effectiveTemplate,
            source = source
        )
    }

    fun fromRemote(
        modelId: String,
        contextLength: Int? = null,
        inputModalities: List<String> = emptyList(),
        supportedParameters: List<String> = emptyList()
    ): ModelCapabilities {
        val name = modelId.lowercase()
        val params = supportedParameters.map { it.lowercase() }
        val reasoning = when {
            params.contains("reasoning_effort") -> ReasoningSupport.EFFORT
            containsAny(name, EFFORT_TOKENS) -> ReasoningSupport.EFFORT
            params.contains("reasoning") -> ReasoningSupport.TOGGLE
            containsAny(name, TOGGLE_TOKENS) -> ReasoningSupport.TOGGLE
            else -> ReasoningSupport.NONE
        }
        val hasVisionModality = inputModalities.any { it.equals("image", ignoreCase = true) }
        val source = when {
            contextLength != null || inputModalities.isNotEmpty() || params.isNotEmpty() ->
                CapabilitySource.METADATA
            else -> CapabilitySource.HEURISTIC
        }

        return ModelCapabilities(
            reasoning = reasoning,
            reasoningEfforts = effortsFor(reasoning),
            vision = hasVisionModality || containsAny(name, VISION_TOKENS),
            tools = params.contains("tools") || containsAny(name, TOOL_TOKENS),
            jsonMode = params.any { it == "response_format" || it == "json_object" || it == "json" },
            nativeSampling = params.contains("top_k") || params.contains("min_p"),
            nativeContext = contextLength != null,
            nativeThreads = false,
            multilingual = false,
            contextLength = contextLength,
            maxOutputTokens = null,
            chatTemplate = null,
            source = source
        )
    }

    fun applyOverrides(base: ModelCapabilities, overrides: ModelCapabilities?): ModelCapabilities {
        if (overrides == null) return base
        return base.copy(
            reasoning = overrides.reasoning,
            reasoningEfforts = overrides.reasoningEfforts.ifEmpty { base.reasoningEfforts },
            vision = overrides.vision,
            tools = overrides.tools,
            jsonMode = overrides.jsonMode,
            nativeSampling = overrides.nativeSampling,
            nativeContext = overrides.nativeContext,
            nativeThreads = overrides.nativeThreads,
            multilingual = overrides.multilingual,
            contextLength = overrides.contextLength ?: base.contextLength,
            maxOutputTokens = overrides.maxOutputTokens ?: base.maxOutputTokens,
            chatTemplate = overrides.chatTemplate ?: base.chatTemplate,
            source = CapabilitySource.MANUAL
        )
    }

    private fun detectReasoning(name: String): ReasoningSupport = when {
        containsAny(name, EFFORT_TOKENS) -> ReasoningSupport.EFFORT
        containsAny(name, TOGGLE_TOKENS) -> ReasoningSupport.TOGGLE
        else -> ReasoningSupport.NONE
    }

    private fun effortsFor(support: ReasoningSupport): Set<ThinkingEffort> = when (support) {
        ReasoningSupport.EFFORT -> ThinkingEffort.values().toSet()
        else -> emptySet()
    }

    private fun containsAny(value: String, tokens: List<String>): Boolean =
        tokens.any { value.contains(it) }
}
