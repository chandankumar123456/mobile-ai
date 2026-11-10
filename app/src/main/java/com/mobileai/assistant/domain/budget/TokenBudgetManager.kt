package com.mobileai.assistant.domain.budget

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ModelPricing(
    val modelId: String,
    val inputCostPerMillionTokens: Double = 0.15,
    val outputCostPerMillionTokens: Double = 0.60
)

@Serializable
data class TokenBudget(
    val id: String, // dotId or sessionId
    val maxTokens: Long = 100_000,
    val warnThresholdRatio: Double = 0.80,
    val hardLimitEnabled: Boolean = true
)

@Serializable
data class BudgetStatus(
    val usedTokens: Long,
    val maxTokens: Long,
    val estimatedCostUsd: Double,
    val isWarningTriggered: Boolean,
    val isHardLimitReached: Boolean
)

/**
 * Enterprise token accounting and budget enforcement manager for remote AI model endpoints.
 */
class TokenBudgetManager {

    private val pricingCatalog = ConcurrentHashMap<String, ModelPricing>().apply {
        put("gpt-4o", ModelPricing("gpt-4o", 2.50, 10.00))
        put("gpt-4o-mini", ModelPricing("gpt-4o-mini", 0.15, 0.60))
        put("claude-3-5-sonnet", ModelPricing("claude-3-5-sonnet", 3.00, 15.00))
    }

    private val budgets = ConcurrentHashMap<String, TokenBudget>()
    private val tokenUsage = ConcurrentHashMap<String, Long>()
    private val costUsage = ConcurrentHashMap<String, Double>()

    fun setBudget(targetId: String, budget: TokenBudget) {
        budgets[targetId] = budget
    }

    fun recordUsage(
        targetId: String,
        modelId: String,
        promptTokens: Int,
        completionTokens: Int
    ): BudgetStatus {
        val totalTokens = (promptTokens + completionTokens).toLong()
        val currentUsage = tokenUsage.merge(targetId, totalTokens) { old, new -> old + new } ?: totalTokens

        val pricing = pricingCatalog[modelId] ?: ModelPricing(modelId)
        val promptCost = (promptTokens / 1_000_000.0) * pricing.inputCostPerMillionTokens
        val completionCost = (completionTokens / 1_000_000.0) * pricing.outputCostPerMillionTokens
        val sessionCost = promptCost + completionCost
        val totalCost = costUsage.merge(targetId, sessionCost) { old, new -> old + new } ?: sessionCost

        val budget = budgets[targetId] ?: TokenBudget(id = targetId)
        val isWarning = currentUsage >= (budget.maxTokens * budget.warnThresholdRatio)
        val isHardLimit = budget.hardLimitEnabled && currentUsage >= budget.maxTokens

        return BudgetStatus(
            usedTokens = currentUsage,
            maxTokens = budget.maxTokens,
            estimatedCostUsd = totalCost,
            isWarningTriggered = isWarning,
            isHardLimitReached = isHardLimit
        )
    }

    fun getStatus(targetId: String): BudgetStatus {
        val used = tokenUsage[targetId] ?: 0L
        val cost = costUsage[targetId] ?: 0.0
        val budget = budgets[targetId] ?: TokenBudget(id = targetId)

        return BudgetStatus(
            usedTokens = used,
            maxTokens = budget.maxTokens,
            estimatedCostUsd = cost,
            isWarningTriggered = used >= (budget.maxTokens * budget.warnThresholdRatio),
            isHardLimitReached = budget.hardLimitEnabled && used >= budget.maxTokens
        )
    }

    fun reset(targetId: String) {
        tokenUsage.remove(targetId)
        costUsage.remove(targetId)
    }
}

