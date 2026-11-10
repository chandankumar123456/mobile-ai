package com.mobileai.assistant.data.task

import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus

/**
 * Coordinates recovery of multi-step tasks across process death and application restarts.
 * Ensures tasks interrupted while RUNNING are safely paused and can be resumed idempotently.
 */
class TaskRecoveryCoordinator(
    private val taskManager: TaskManagerImpl? = null
) {

    /**
     * Identifies any interrupted tasks from the manager, resets mid-flight steps to PENDING,
     * updates task status to PAUSED, and commits the state.
     */
    suspend fun coordinateRecovery(): List<DotTask> {
        val manager = taskManager ?: return emptyList()
        val currentTasks = manager.tasks.value
        val recovered = recoverInterruptedTasks(currentTasks)
        manager.applyRecoveredTasks(recovered)
        return recovered
    }

    /**
     * Convenience parameterless overload that coordinates recovery on the configured task manager.
     */
    suspend fun recoverInterruptedTasks(): List<DotTask> = coordinateRecovery()

    /**
     * Pure function that inspects a list of tasks and recovers any that were interrupted mid-execution.
     */
    fun recoverInterruptedTasks(tasks: List<DotTask>): List<DotTask> {
        return tasks.map { task -> recoverTask(task) }
    }

    /**
     * Recovers a single task:
     * - If [TaskStatus.RUNNING]:
     *   - Steps in [StepStatus.RUNNING] are reset to [StepStatus.PENDING] with an interruption note.
     *   - Task status is reset to [TaskStatus.PAUSED].
     * - If [TaskStatus.AWAITING_APPROVAL]:
     *   - Preserved as [TaskStatus.AWAITING_APPROVAL] so the user can resolve the prompt upon return.
     * - Completed, Failed, Cancelled, or already Paused tasks are left unchanged.
     */
    fun recoverTask(task: DotTask): DotTask {
        if (task.status != TaskStatus.RUNNING) {
            return task
        }

        var anyStepInterrupted = false
        val recoveredSteps = task.steps.map { step ->
            if (step.status == StepStatus.RUNNING) {
                anyStepInterrupted = true
                step.copy(
                    status = StepStatus.PENDING,
                    errorMessage = "Interrupted by process restart. Ready to resume.",
                    startedAt = null
                )
            } else {
                step
            }
        }

        return task.copy(
            status = TaskStatus.PAUSED,
            steps = recoveredSteps,
            failureReason = if (anyStepInterrupted) "Interrupted by process termination" else task.failureReason,
            updatedAt = System.currentTimeMillis()
        )
    }
}
