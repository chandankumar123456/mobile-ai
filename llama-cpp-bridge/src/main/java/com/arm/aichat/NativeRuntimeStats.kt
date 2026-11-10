package com.arm.aichat

/**
 * Runtime counters reported by the native engine.
 */
data class NativeRuntimeStats(
    val promptTokens: Int,
    val generatedTokens: Int,
    val contextUsed: Int,
    val contextSize: Int,
)
