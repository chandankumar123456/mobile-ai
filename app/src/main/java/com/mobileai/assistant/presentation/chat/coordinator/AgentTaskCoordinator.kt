package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.task.AgentPlanner
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.TaskManager
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordinator responsible for multi-step agent task lifecycle,
 * planning, step execution, pause/resume, and approval resolution.
 */
class AgentTaskCoordinator(
    private val taskManager: TaskManager?,
    private val toolRegistry: ToolRegistry? = null,
    private val scope: CoroutineScope
) {
    private val _tasks = MutableStateFlow<List<DotTask>>(emptyList())
    val tasks: StateFlow<List<DotTask>> = _tasks.asStateFlow()

    private val planner: AgentPlanner? = toolRegistry?.let { AgentPlanner(it) }

    init {
        if (taskManager != null) {
            scope.launch {
                taskManager.tasks.collectLatest { updatedTasks ->
                    _tasks.value = updatedTasks
                }
            }
        }
    }

    suspend fun createTask(
        title: String,
        goal: String,
        dotId: String,
        conversationId: String
    ): DotTask? {
        return taskManager?.createTask(
            title = title,
            goalDescription = goal,
            dotId = dotId,
            conversationId = conversationId
        )
    }

    /**
     * Creates a task with model-driven planning steps parsed by [AgentPlanner].
     */
    suspend fun createPlannedTask(
        title: String,
        goal: String,
        dot: DotProfile,
        conversationId: String,
        modelPlanOutput: String? = null
    ): DotTask? {
        val manager = taskManager ?: return null
        val taskId = UUID.randomUUID().toString()

        val steps = if (!modelPlanOutput.isNullOrBlank() && planner != null) {
            planner.parsePlanResponse(modelPlanOutput, taskId)
        } else if (planner != null) {
            planner.fallbackPlan(taskId, goal)
        } else {
            emptyList()
        }

        return manager.createTask(
            title = title,
            goalDescription = goal,
            dotId = dot.id,
            conversationId = conversationId,
            initialSteps = steps
        )
    }

    suspend fun executeTask(taskId: String): DotTask? {
        return taskManager?.executeTask(taskId)
    }

    suspend fun pauseTask(taskId: String): DotTask? {
        return taskManager?.pauseTask(taskId)
    }

    suspend fun resumeTask(taskId: String): DotTask? {
        return taskManager?.resumeTask(taskId)
    }

    suspend fun cancelTask(taskId: String, reason: String? = null): DotTask? {
        return taskManager?.cancelTask(taskId, reason)
    }

    suspend fun handleApprovalDecision(
        requestId: String,
        decision: ToolApprovalDecision,
        rejectionReason: String? = null
    ): DotTask? {
        return taskManager?.handleApprovalDecision(requestId, decision, rejectionReason)
    }
}
