package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Coordinator responsible for collecting, calculating, and exposing real-time inference metrics:
 * Time-to-first-token (TTFT), prefill throughput, decode throughput, memory usage, and context utilization.
 */
class RuntimeMetricsCoordinator {

    private val _currentMetrics = MutableStateFlow<RuntimeMetrics?>(null)
    val currentMetrics: StateFlow<RuntimeMetrics?> = _currentMetrics.asStateFlow()

    private var generationStartTime: Long = 0L
    private var firstTokenTime: Long = 0L
    private var tokenCount: Int = 0

    fun onGenerationStarted() {
        generationStartTime = System.currentTimeMillis()
        firstTokenTime = 0L
        tokenCount = 0
        _currentMetrics.value = null
    }

    fun onFirstTokenReceived() {
        if (firstTokenTime == 0L) {
            firstTokenTime = System.currentTimeMillis()
            val ttftMs = firstTokenTime - generationStartTime
            _currentMetrics.update { current ->
                (current ?: RuntimeMetrics()).copy(timeToFirstTokenMs = ttftMs)
            }
        }
    }

    fun onTokenEmitted() {
        tokenCount++
        val now = System.currentTimeMillis()
        val durationSec = (now - (if (firstTokenTime > 0) firstTokenTime else generationStartTime)) / 1000.0
        val tokensPerSec = if (durationSec > 0.05) (tokenCount / durationSec) else 0.0

        _currentMetrics.update { current ->
            (current ?: RuntimeMetrics()).copy(
                tokensPerSecond = tokensPerSec,
                completionTokens = tokenCount
            )
        }
    }

    fun onGenerationCompleted(finalMetrics: RuntimeMetrics?) {
        if (finalMetrics != null) {
            _currentMetrics.value = finalMetrics
        } else {
            val totalDurationMs = System.currentTimeMillis() - generationStartTime
            val ttft = if (firstTokenTime > 0) firstTokenTime - generationStartTime else totalDurationMs
            val decodeDurationSec = if (totalDurationMs > ttft) (totalDurationMs - ttft) / 1000.0 else 0.001
            val decodeRate = tokenCount / decodeDurationSec

            _currentMetrics.update { current ->
                (current ?: RuntimeMetrics()).copy(
                    timeToFirstTokenMs = ttft,
                    tokensPerSecond = decodeRate,
                    completionTokens = tokenCount,
                    totalDurationMs = totalDurationMs
                )
            }
        }
    }

    fun updateMetrics(metrics: RuntimeMetrics) {
        _currentMetrics.value = metrics
    }

    fun clear() {
        _currentMetrics.value = null
    }
}
