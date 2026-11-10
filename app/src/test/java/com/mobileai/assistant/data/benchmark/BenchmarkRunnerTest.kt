package com.mobileai.assistant.data.benchmark

import com.mobileai.assistant.domain.benchmark.BenchmarkEnvironment
import com.mobileai.assistant.domain.benchmark.BenchmarkReport
import com.mobileai.assistant.domain.benchmark.BenchmarkSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkRunnerTest {

    @Test
    fun testBenchmarkReportToCsv() {
        val summary = BenchmarkSummary(
            promptId = "qa-01",
            category = "factual",
            trialCount = 3,
            meanTtftMs = 120.5,
            p50TtftMs = 118.0,
            p95TtftMs = 130.0,
            p99TtftMs = 130.0,
            stdDevTtftMs = 5.2,
            meanDecodeRate = 42.5,
            p50DecodeRate = 43.0,
            p95DecodeRate = 41.0,
            meanPeakRamMb = 380.0,
            assertionPassRate = 1.0
        )

        val report = BenchmarkReport(
            environment = BenchmarkEnvironment(modelId = "qwen-0.5b-q4"),
            summaries = listOf(summary),
            trials = emptyList()
        )

        val csv = report.toCsv()
        assertTrue(csv.contains("PromptID,Category,Trials"))
        assertTrue(csv.contains("qa-01,factual,3"))
        assertTrue(csv.contains("120.5"))
        assertTrue(csv.contains("42.5"))
        assertTrue(csv.contains("100.0%"))
    }
}

