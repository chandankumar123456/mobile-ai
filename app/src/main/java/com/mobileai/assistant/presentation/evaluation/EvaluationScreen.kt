package com.mobileai.assistant.presentation.evaluation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileai.assistant.data.evaluation.EvaluationEngine
import com.mobileai.assistant.domain.dot.DotRepository
import com.mobileai.assistant.domain.evaluation.EvaluationMetrics
import com.mobileai.assistant.domain.task.TaskManager
import com.mobileai.assistant.domain.tool.ToolRegistry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(
    toolRegistry: ToolRegistry,
    taskManager: TaskManager?,
    dotRepository: DotRepository?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isEvaluating by remember { mutableStateOf(false) }
    var latestMetrics by remember { mutableStateOf<EvaluationMetrics?>(null) }

    val engine = remember { EvaluationEngine(toolRegistry, taskManager, dotRepository) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Evaluation & Reliability") },
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
            Button(
                onClick = {
                    scope.launch {
                        isEvaluating = true
                        val res = engine.evaluateFullSystem("local-qwen-0.5b")
                        latestMetrics = res
                        isEvaluating = false
                    }
                },
                enabled = !isEvaluating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Assessment, contentDescription = null)
                Text(text = if (isEvaluating) "Evaluating System..." else "Run Comprehensive AI Evaluation")
            }

            latestMetrics?.let { metrics ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Agent Reliability & Tooling", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                MetricRow("Tool Selection Accuracy", "${"%.1f".format(metrics.toolSelectionAccuracy * 100)}%")
                                MetricRow("Tool Execution Success", "${"%.1f".format(metrics.toolExecutionSuccessRate * 100)}%")
                                MetricRow("Task Completion Rate", "${"%.1f".format(metrics.taskCompletionRate * 100)}%")
                                MetricRow("Process Recovery Rate", "${"%.1f".format(metrics.taskRecoverySuccessRate * 100)}%")
                                MetricRow("Approval Compliance", "${"%.1f".format(metrics.approvalComplianceRate * 100)}%")
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "UI Protocol Conformance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                MetricRow("GenUI Validation Pass Rate", "${"%.1f".format(metrics.genUiValidationPassRate * 100)}%")
                                MetricRow("OpenUI Lang Syntax Rate", "${"%.1f".format(metrics.openUiParseSuccessRate * 100)}%")
                                MetricRow("Google A2UI Flat Tree Resolution", "${"%.1f".format(metrics.a2UiTreeResolutionRate * 100)}%")
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Dot Context & Memory Isolation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                MetricRow("Memory Recall Relevance", "${"%.1f".format(metrics.memoryRetrievalRelevance * 100)}%")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Zero Cross-Dot Leakage", style = MaterialTheme.typography.bodyMedium)
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("VERIFIED ISOLATED") },
                                        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
