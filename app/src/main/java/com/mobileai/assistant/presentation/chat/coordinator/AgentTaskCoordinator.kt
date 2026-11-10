package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.TaskManager
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinator responsible for multi-step agent task lifecycle,
 * step execution, pause/resume, and approval resolution.
 */
class AgentTaskCoordinator(
    private val taskManager: TaskManager?,
    private val scope: CoroutineScope
) {
    private val _tasks = MutableStateFlow<List<DotTask>>(emptyList())
    val tasks: StateFlow<List<DotTask>> = _tasks.asStateFlow()

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
