package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerationParams(
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    val topK: Int = 40,
    val minP: Double = 0.05,
    val repeatPenalty: Double = 1.1,
    val presencePenalty: Double = 0.0,
    val frequencyPenalty: Double = 0.0,
    val maxTokens: Int = 1024,
    val contextSize: Int = 4096,
    val threads: Int = 4,
    val batchSize: Int = 512,
    val seed: Long = -1L,
    val stopSequences: List<String> = emptyList(),
    val thinkingEnabled: Boolean = false,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val language: ResponseLanguage = ResponseLanguage.AUTO,
    val systemPrompt: String = "",
    val extraBody: Map<String, String> = emptyMap()
) {
    fun clamp(): GenerationParams = copy(
        temperature = temperature.coerceIn(Bounds.MIN_TEMPERATURE, Bounds.MAX_TEMPERATURE),
        topP = topP.coerceIn(Bounds.MIN_TOP_P, Bounds.MAX_TOP_P),
        topK = topK.coerceIn(Bounds.MIN_TOP_K, Bounds.MAX_TOP_K),
        minP = minP.coerceIn(Bounds.MIN_MIN_P, Bounds.MAX_MIN_P),
        repeatPenalty = repeatPenalty.coerceIn(Bounds.MIN_REPEAT_PENALTY, Bounds.MAX_REPEAT_PENALTY),
        presencePenalty = presencePenalty.coerceIn(Bounds.MIN_PENALTY, Bounds.MAX_PENALTY),
        frequencyPenalty = frequencyPenalty.coerceIn(Bounds.MIN_PENALTY, Bounds.MAX_PENALTY),
        maxTokens = maxTokens.coerceIn(Bounds.MIN_MAX_TOKENS, Bounds.MAX_MAX_TOKENS),
        contextSize = contextSize.coerceIn(Bounds.MIN_CONTEXT_SIZE, Bounds.MAX_CONTEXT_SIZE),
        threads = threads.coerceIn(Bounds.MIN_THREADS, Bounds.MAX_THREADS),
        batchSize = batchSize.coerceIn(Bounds.MIN_BATCH_SIZE, Bounds.MAX_BATCH_SIZE)
    )

    object Bounds {
        const val MIN_TEMPERATURE = 0.0
        const val MAX_TEMPERATURE = 2.0

        const val MIN_TOP_P = 0.0
        const val MAX_TOP_P = 1.0

        const val MIN_TOP_K = 0
        const val MAX_TOP_K = 200

        const val MIN_MIN_P = 0.0
        const val MAX_MIN_P = 1.0

        const val MIN_REPEAT_PENALTY = 0.0
        const val MAX_REPEAT_PENALTY = 2.0

        const val MIN_PENALTY = -2.0
        const val MAX_PENALTY = 2.0

        const val MIN_MAX_TOKENS = 1
        const val MAX_MAX_TOKENS = 32768

        const val MIN_CONTEXT_SIZE = 512
        const val MAX_CONTEXT_SIZE = 131072

        const val MIN_THREADS = 1
        const val MAX_THREADS = 64

        const val MIN_BATCH_SIZE = 128
        const val MAX_BATCH_SIZE = 2048
    }
}
