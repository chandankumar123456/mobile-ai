package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

/**
 * Thinking/Reasoning effort levels supported by advanced reasoning models.
 */
@Serializable
enum class ThinkingEffort(
    val displayName: String,
    val apiValue: String,
    val tokenBudgetMultiplier: Float
) {
    LOW("Low", "low", 1.0f),
    MEDIUM("Medium", "medium", 2.0f),
    HIGH("High", "high", 3.0f),
    MAX("Maximum", "max", 4.0f)
}

