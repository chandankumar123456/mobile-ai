package com.mobileai.assistant.domain.evaluation

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class EvaluationMetrics(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val modelId: String,

    // Inference Quality
    val responseCompletionRate: Double,
    val assertionAccuracyRate: Double,
    val structuredJsonValidityRate: Double,

    // Agent Reliability
    val toolSelectionAccuracy: Double,
    val toolExecutionSuccessRate: Double,
    val taskCompletionRate: Double,
    val taskRecoverySuccessRate: Double,
    val approvalComplianceRate: Double,

    // UI Protocol Conformance
    val genUiValidationPassRate: Double,
    val openUiParseSuccessRate: Double,
    val a2UiTreeResolutionRate: Double,

    // Dot Context Isolation
    val memoryRetrievalRelevance: Double,
    val crossDotIsolationVerified: Boolean,

    val totalEvaluatedCases: Int
)

