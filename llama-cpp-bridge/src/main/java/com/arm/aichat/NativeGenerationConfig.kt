package com.arm.aichat

/**
 * Sampling and runtime configuration passed to the native inference engine.
 */
data class NativeGenerationConfig(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.1f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val seed: Long = -1L,
    val predictLength: Int = 1024,
    val stopSequences: List<String> = emptyList(),
    val enableThinking: Boolean = false,
    val continueFinalMessage: Boolean = false,
    val nCtx: Int = 4096,
    val nThreads: Int = 4,
    val nBatch: Int = 512,
)
