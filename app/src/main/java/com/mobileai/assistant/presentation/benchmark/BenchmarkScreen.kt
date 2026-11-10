package com.mobileai.assistant.presentation.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileai.assistant.data.benchmark.BenchmarkRunner
import com.mobileai.assistant.domain.benchmark.BenchmarkEnvironment
import com.mobileai.assistant.domain.benchmark.BenchmarkPrompt
import com.mobileai.assistant.domain.benchmark.BenchmarkReport
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    engine: LocalInferenceEngine,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var latestReport by remember { mutableStateOf<BenchmarkReport?>(null) }

    val runner = remember { BenchmarkRunner(engine) }

    val samplePrompts = remember {
        listOf(
            BenchmarkPrompt(id = "qa-01", prompt = "What is the capital of France?", category = "factual", expectedContains = listOf("Paris")),
            BenchmarkPrompt(id = "reasoning-01", prompt = "Explain privacy benefits of on-device LLMs.", category = "reasoning", expectedContains = listOf("device", "privacy")),
            BenchmarkPrompt(id = "tool-01", prompt = "Store user preference in memory.", category = "tool_calling", expectedTool = "remember_fact")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inference Benchmarking") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Environment Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Hardware & Runtime Environment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val modelName = engine.loadedModel.value?.displayName ?: "No Model Loaded (Using Fallback)"
                    Text(text = "Model: $modelName", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Runtime: Native llama.cpp ARM64", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Quantization: Q4_K_M (4-bit integer weights)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Benchmark Action & Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            progress = 0f
                            val report = runner.runBenchmark(samplePrompts, trialsPerPrompt = 2, onProgress = { progress = it })
                            latestReport = report
                            isRunning = false
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(text = if (isRunning) "Benchmarking..." else "Run Benchmark Suite")
                }
            }

            if (isRunning) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            // Results Display
            latestReport?.let { report ->
                Text(text = "Benchmark Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(report.summaries) { summary ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "${summary.promptId} (${summary.category})", fontWeight = FontWeight.SemiBold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Mean TTFT: ${"%.1f".format(summary.meanTtftMs)} ms", style = MaterialTheme.typography.bodySmall)
                                    Text(text = "P95 TTFT: ${"%.1f".format(summary.p95TtftMs)} ms", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Decode: ${"%.2f".format(summary.meanDecodeRate)} tok/s", style = MaterialTheme.typography.bodySmall)
                                    Text(text = "Peak RAM: ${"%.1f".format(summary.meanPeakRamMb)} MB", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(text = "Assertion Accuracy: ${"%.0f".format(summary.assertionPassRate * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
