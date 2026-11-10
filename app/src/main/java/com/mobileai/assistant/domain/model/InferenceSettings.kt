package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing inference parameters supported by the native engine.
 *
 * Note: [contextSize] and [threads] are applied during context initialization and
 * require reloading the model to take effect if modified.
 */
@Serializable
data class InferenceSettings(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 1024,
    val contextSize: Int = 4096,
    val threads: Int = 4,
    val batchSize: Int = 512,
    val systemPrompt: String = "You are a helpful, respectful, and honest local AI assistant. Keep responses clear and concise.",
    val minP: Double = 0.05,
    val repeatPenalty: Double = 1.1,
    val presencePenalty: Double = 0.0,
    val frequencyPenalty: Double = 0.0,
    val seed: Long = -1L,
    val stopSequences: List<String> = emptyList(),
    val thinkingEnabled: Boolean = false,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val responseLanguage: ResponseLanguage = ResponseLanguage.AUTO
) {
    fun toGenerationParams(): GenerationParams = GenerationParams(
        temperature = temperature.toDouble(),
        topP = topP.toDouble(),
        topK = topK,
        minP = minP,
        repeatPenalty = repeatPenalty,
        presencePenalty = presencePenalty,
        frequencyPenalty = frequencyPenalty,
        maxTokens = maxTokens,
        contextSize = contextSize,
        threads = threads,
        batchSize = batchSize,
        seed = seed,
        stopSequences = stopSequences,
        thinkingEnabled = thinkingEnabled,
        thinkingEffort = thinkingEffort,
        language = responseLanguage,
        systemPrompt = systemPrompt
    )

    fun withParams(params: GenerationParams): InferenceSettings = copy(
        temperature = params.temperature.toFloat(),
        topP = params.topP.toFloat(),
        topK = params.topK,
        minP = params.minP,
        repeatPenalty = params.repeatPenalty,
        presencePenalty = params.presencePenalty,
        frequencyPenalty = params.frequencyPenalty,
        maxTokens = params.maxTokens,
        contextSize = params.contextSize,
        threads = params.threads,
        batchSize = params.batchSize,
        seed = params.seed,
        stopSequences = params.stopSequences,
        thinkingEnabled = params.thinkingEnabled,
        thinkingEffort = params.thinkingEffort,
        responseLanguage = params.language,
        systemPrompt = params.systemPrompt
    )

    companion object {
        val DEFAULT = InferenceSettings()

        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 2.0f

        const val MIN_TOP_P = 0.05f
        const val MAX_TOP_P = 1.0f

        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 100

        const val MIN_MIN_P = 0.0
        const val MAX_MIN_P = 1.0

        const val MIN_REPEAT_PENALTY = 0.0
        const val MAX_REPEAT_PENALTY = 2.0

        const val MIN_PENALTY = -2.0
        const val MAX_PENALTY = 2.0

        const val MIN_MAX_TOKENS = 64
        const val MAX_MAX_TOKENS = 4096

        const val MIN_CONTEXT_SIZE = 512
        const val MAX_CONTEXT_SIZE = 8192

        const val MIN_THREADS = 1
        const val MAX_THREADS = 8

        const val MIN_BATCH_SIZE = 128
        const val MAX_BATCH_SIZE = 2048
    }
}

