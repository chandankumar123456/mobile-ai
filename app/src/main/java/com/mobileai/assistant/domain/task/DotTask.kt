package com.mobileai.assistant.domain.task

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * High-level execution state for a multi-step task.
 */
@Serializable
enum class TaskStatus {
    CREATED,
    PLANNING,
    QUEUED,
    RUNNING,
    PAUSED,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Execution status for an individual step in a task.
 */
@Serializable
enum class StepStatus {
    PENDING,
    RUNNING,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Result of executing a single task step.
 */
@Serializable
data class TaskStepResult(
    val stepId: String,
    val status: StepStatus,
    val outputJson: String? = null,
    val errorMessage: String? = null
)

/**
 * Individual step within a [DotTask].
 */
@Serializable
data class DotTaskStep(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val stepIndex: Int,
    val title: String,
    val description: String,
    val status: StepStatus = StepStatus.PENDING,
    val toolName: String? = null,
    val toolInputJson: String? = null,
    val toolOutputJson: String? = null,
    val thoughtLog: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val startedAt: Long? = null,
    val finishedAt: Long? = null
)

/**
 * Persistent, multi-step task executed by a Dot.
 */
@Serializable
data class DotTask(
    val id: String = UUID.randomUUID().toString(),
    val dotId: String,
    val conversationId: String,
    val title: String,
    val goalDescription: String,
    val status: TaskStatus = TaskStatus.CREATED,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val completedSteps: Int = 0,
    val failureReason: String? = null,
    val steps: List<DotTaskStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val isTerminal: Boolean
        get() = status == TaskStatus.COMPLETED ||
                status == TaskStatus.FAILED ||
                status == TaskStatus.CANCELLED

    val progressFraction: Float
        get() = if (totalSteps > 0) completedSteps.toFloat() / totalSteps.toFloat() else 0f
}
