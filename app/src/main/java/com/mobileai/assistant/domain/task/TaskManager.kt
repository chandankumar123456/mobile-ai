package com.mobileai.assistant.domain.task

import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolApprovalRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Core interface for managing multi-step task lifecycle, step execution, and human-in-the-loop approvals.
 */
interface TaskManager {
    /**
     * Observable stream of all persisted tasks.
     */
    val tasks: StateFlow<List<DotTask>>

    /**
     * Observable stream of all pending tool approval requests awaiting user decision.
     */
    val pendingApprovals: StateFlow<List<ToolApprovalRequest>>

    /**
     * Returns a reactive flow of tasks, optionally filtered by Dot ID.
     */
    fun getTasks(dotId: String? = null): Flow<List<DotTask>>

    /**
     * Returns a reactive flow of tasks for a specific Dot.
     */
    fun getTasksForDot(dotId: String): Flow<List<DotTask>>

    /**
     * Looks up a single task by ID.
     */
    suspend fun getTaskById(taskId: String): DotTask?

    /**
     * Creates and persists a new task.
     */
    suspend fun createTask(
        title: String,
        goalDescription: String,
        dotId: String,
        conversationId: String,
        initialSteps: List<DotTaskStep> = emptyList()
    ): DotTask

    /**
     * Appends a step to an existing non-terminal task.
     */
    suspend fun addStep(
        taskId: String,
        title: String,
        description: String,
        toolName: String? = null,
        toolInputJson: String? = null
    ): DotTaskStep

    /**
     * Executes the task sequentially from its current step until completion, pause, or approval pause.
     */
    suspend fun executeTask(taskId: String): DotTask

    /**
     * Executes the current single step of the task.
     */
    suspend fun executeStep(taskId: String): TaskStepResult

    /**
     * Pauses an active or queued task.
     */
    suspend fun pauseTask(taskId: String): DotTask?

    /**
     * Resumes a paused task from where it left off.
     */
    suspend fun resumeTask(taskId: String): DotTask?

    /**
     * Cancels an active or pending task.
     */
    suspend fun cancelTask(taskId: String, reason: String? = null): DotTask?

    /**
     * Resolves a pending tool approval request for a task awaiting user confirmation.
     */
    suspend fun handleApprovalDecision(
        requestId: String,
        decision: ToolApprovalDecision,
        rejectionReason: String? = null
    ): DotTask?
}
