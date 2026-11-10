package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RuntimeMetrics(
    val timeToFirstTokenMs: Long? = null,
    val totalDurationMs: Long? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val tokensPerSecond: Double? = null,
    val contextUsed: Int = 0,
    val contextSize: Int = 0,
    val loadTimeMs: Long? = null,
    val nativeMemoryBytes: Long? = null,
    val javaHeapBytes: Long? = null,
    val source: String = "LOCAL"
) {
    companion object {
        val EMPTY = RuntimeMetrics()
    }
}
