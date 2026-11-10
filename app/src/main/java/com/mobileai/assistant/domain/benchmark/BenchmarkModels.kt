package com.mobileai.assistant.domain.benchmark

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class BenchmarkPrompt(
    val id: String,
    val prompt: String,
    val category: String,
    val maxTokens: Int = 128,
    val expectedContains: List<String> = emptyList(),
    val expectedTool: String? = null
)

@Serializable
data class BenchmarkTrialResult(
    val trialIndex: Int,
    val promptId: String,
    val isColdStart: Boolean,
    val ttftMs: Long,
    val prefillTokensPerSec: Double,
    val decodeTokensPerSec: Double,
    val totalDurationMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val peakRamMb: Double,
    val nativeMemoryBytes: Long,
    val outputText: String,
    val passedAssertions: Boolean
)

@Serializable
data class BenchmarkSummary(
    val promptId: String,
    val category: String,
    val trialCount: Int,
    val meanTtftMs: Double,
    val p50TtftMs: Double,
    val p95TtftMs: Double,
    val p99TtftMs: Double,
    val stdDevTtftMs: Double,
    val meanDecodeRate: Double,
    val p50DecodeRate: Double,
    val p95DecodeRate: Double,
    val meanPeakRamMb: Double,
    val assertionPassRate: Double
)

@Serializable
data class BenchmarkEnvironment(
    val manufacturer: String = "Generic",
    val model: String = "Device",
    val soc: String = "ARM64",
    val totalRamMb: Long = 8192,
    val androidVersion: Int = 35,
    val appVersion: String = "1.0.0",
    val nativeRuntimeVersion: String = "llama.cpp b4120",
    val modelId: String,
    val quantization: String = "Q4_K_M",
    val contextSize: Int = 2048,
    val threads: Int = 4
)

@Serializable
data class BenchmarkReport(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val environment: BenchmarkEnvironment,
    val summaries: List<BenchmarkSummary>,
    val trials: List<BenchmarkTrialResult>
) {
    fun toCsv(): String {
        val sb = StringBuilder()
        sb.append("PromptID,Category,Trials,MeanTTFT_ms,P50TTFT_ms,P95TTFT_ms,MeanDecodeTokS,P50DecodeTokS,MeanPeakRAM_MB,PassRate\n")
        summaries.forEach { s ->
            sb.append("${s.promptId},${s.category},${s.trialCount},${"%.1f".format(s.meanTtftMs)},${"%.1f".format(s.p50TtftMs)},${"%.1f".format(s.p95TtftMs)},${"%.2f".format(s.meanDecodeRate)},${"%.2f".format(s.p50DecodeRate)},${"%.1f".format(s.meanPeakRamMb)},${"%.1f".format(s.assertionPassRate * 100)}%\n")
        }
        return sb.toString()
    }
}

