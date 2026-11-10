package com.mobileai.assistant.data.task

import android.content.Context
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.DotTaskStep
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskManager
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.task.TaskStepResult
import com.mobileai.assistant.domain.tool.ApprovalStatus
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolApprovalHandler
import com.mobileai.assistant.domain.tool.ToolApprovalRequest
import com.mobileai.assistant.domain.tool.ToolAuditLogEntry
import com.mobileai.assistant.domain.tool.ToolAuditLogger
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolRegistry
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class TaskStoreEnvelope(
    val version: Int = 1,
    val tasks: List<DotTask> = emptyList()
)

@Serializable
data class TaskStepStoreEnvelope(
    val version: Int = 1,
    val steps: List<DotTaskStep> = emptyList()
)

@Serializable
data class ApprovalStoreEnvelope(
    val version: Int = 1,
    val requests: List<ToolApprovalRequest> = emptyList()
)

/**
 * Durable, file-backed atomic implementation of [TaskManager].
 * Manages task state machine, step execution, sensitive tool interception, and resumption.
 */
class TaskManagerImpl(
    private val context: Context? = null,
    rootDirOverride: File? = null,
    val toolRegistry: ToolRegistry = DefaultToolRegistry(),
    val auditLogger: ToolAuditLogger = ToolAuditLoggerImpl(rootDirOverride ?: context?.filesDir ?: File(".")),
    var approvalHandler: ToolApprovalHandler? = null
) : TaskManager {

    internal constructor(
        rootDir: File,
        toolRegistry: ToolRegistry = DefaultToolRegistry(),
        auditLogger: ToolAuditLogger = ToolAuditLoggerImpl(rootDir),
        approvalHandler: ToolApprovalHandler? = null
    ) : this(null, rootDir, toolRegistry, auditLogger, approvalHandler)

    private val rootDir: File? = rootDirOverride ?: context?.filesDir

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val lock = Any()
    private val taskExecutionMutexes = ConcurrentHashMap<String, Mutex>()

    private val _tasks = MutableStateFlow<List<DotTask>>(emptyList())
    override val tasks: StateFlow<List<DotTask>> = _tasks.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<ToolApprovalRequest>>(emptyList())
    override val pendingApprovals: StateFlow<List<ToolApprovalRequest>> = _pendingApprovals.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        synchronized(lock) {
            val dir = rootDir ?: return
            val tasksFile = File(dir, FILE_TASKS)
            if (tasksFile.exists() && tasksFile.length() > 0) {
                try {
                    val envelope = json.decodeFromString<TaskStoreEnvelope>(tasksFile.readText())
                    _tasks.value = envelope.tasks
                } catch (_: Exception) {
                    _tasks.value = emptyList()
                }
            } else {
                _tasks.value = emptyList()
            }

            val approvalsFile = File(dir, FILE_APPROVALS)
            if (approvalsFile.exists() && approvalsFile.length() > 0) {
                try {
                    val envelope = json.decodeFromString<ApprovalStoreEnvelope>(approvalsFile.readText())
                    _pendingApprovals.value = envelope.requests.filter { it.status == ApprovalStatus.PENDING }
                } catch (_: Exception) {
                    _pendingApprovals.value = emptyList()
                }
            }
        }
    }

    override fun getTasks(dotId: String?): Flow<List<DotTask>> {
        return if (dotId == null) {
            _tasks.asStateFlow()
        } else {
            _tasks.map { list -> list.filter { it.dotId == dotId } }
        }
    }

    override fun getTasksForDot(dotId: String): Flow<List<DotTask>> = getTasks(dotId)

    override suspend fun getTaskById(taskId: String): DotTask? {
        return synchronized(lock) {
            _tasks.value.firstOrNull { it.id == taskId }
        }
    }

    fun getPendingApprovalForTask(taskId: String): ToolApprovalRequest? {
        return synchronized(lock) {
            _pendingApprovals.value.firstOrNull { it.taskId == taskId && it.status == ApprovalStatus.PENDING }
        }
    }

    fun getPendingApprovalByRequestId(requestId: String): ToolApprovalRequest? {
        return synchronized(lock) {
            _pendingApprovals.value.firstOrNull { it.id == requestId }
        }
    }

    override suspend fun createTask(
        title: String,
        goalDescription: String,
        dotId: String,
        conversationId: String,
        initialSteps: List<DotTaskStep>
    ): DotTask {
        val taskId = UUID.randomUUID().toString()
        val assignedSteps = initialSteps.mapIndexed { index, step ->
            step.copy(
                id = if (step.id.isBlank()) UUID.randomUUID().toString() else step.id,
                taskId = taskId,
                stepIndex = index
            )
        }

        val newTask = DotTask(
            id = taskId,
            dotId = dotId,
            conversationId = conversationId,
            title = title,
            goalDescription = goalDescription,
            status = TaskStatus.CREATED,
            currentStepIndex = 0,
            totalSteps = assignedSteps.size,
            completedSteps = 0,
            steps = assignedSteps,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        synchronized(lock) {
            val updated = _tasks.value + newTask
            _tasks.value = updated
            saveStateLocked()
        }
        return newTask
    }

    override suspend fun addStep(
        taskId: String,
        title: String,
        description: String,
        toolName: String?,
        toolInputJson: String?
    ): DotTaskStep {
        return synchronized(lock) {
            val existingTask = _tasks.value.firstOrNull { it.id == taskId }
                ?: throw IllegalArgumentException("Task $taskId not found")

            val stepIndex = existingTask.steps.size
            val newStep = DotTaskStep(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                stepIndex = stepIndex,
                title = title,
                description = description,
                status = StepStatus.PENDING,
                toolName = toolName,
                toolInputJson = toolInputJson,
                retryCount = 0
            )

            val updatedSteps = existingTask.steps + newStep
            val updatedTask = existingTask.copy(
                steps = updatedSteps,
                totalSteps = updatedSteps.size,
                updatedAt = System.currentTimeMillis()
            )

            _tasks.value = _tasks.value.map { if (it.id == taskId) updatedTask else it }
            saveStateLocked()
            newStep
        }
    }

    override suspend fun executeTask(taskId: String): DotTask {
        val mutex = taskExecutionMutexes.computeIfAbsent(taskId) { Mutex() }
        return mutex.withLock {
            var currentTask = getTaskById(taskId)
                ?: throw IllegalArgumentException("Task $taskId not found")

            if (currentTask.isTerminal ||
                currentTask.status == TaskStatus.PAUSED ||
                currentTask.status == TaskStatus.AWAITING_APPROVAL
            ) {
                return@withLock currentTask
            }

            // Zero-step task handling: complete immediately
            if (currentTask.steps.isEmpty()) {
                val now = System.currentTimeMillis()
                val completedTask = synchronized(lock) {
                    val existing = _tasks.value.firstOrNull { it.id == taskId } ?: return@withLock currentTask
                    if (existing.isTerminal) return@withLock existing
                    val updated = existing.copy(
                        status = TaskStatus.COMPLETED,
                        completedSteps = 0,
                        currentStepIndex = 0,
                        completedAt = now,
                        updatedAt = now
                    )
                    _tasks.value = _tasks.value.map { if (it.id == taskId) updated else it }
                    saveStateLocked()
                    updated
                }
                return@withLock completedTask
            }

            // Set status to RUNNING if not already
            if (currentTask.status != TaskStatus.RUNNING) {
                currentTask = synchronized(lock) {
                    updateTaskStatusLocked(taskId, TaskStatus.RUNNING)
                }
            }

            // Sequential step execution loop
            while (currentTask.status == TaskStatus.RUNNING &&
                currentTask.currentStepIndex < currentTask.steps.size
            ) {
                val stepResult = executeStepInternal(taskId)
                currentTask = getTaskById(taskId) ?: break

                if (stepResult.status == StepStatus.AWAITING_APPROVAL) {
                    // Task was halted awaiting user approval
                    break
                } else if (stepResult.status == StepStatus.FAILED) {
                    // Step failed, stop execution loop
                    break
                }
            }

            currentTask
        }
    }

    override suspend fun executeStep(taskId: String): TaskStepResult {
        val mutex = taskExecutionMutexes.computeIfAbsent(taskId) { Mutex() }
        return mutex.withLock {
            executeStepInternal(taskId)
        }
    }

    private suspend fun executeStepInternal(taskId: String): TaskStepResult {
        val task = getTaskById(taskId)
            ?: return TaskStepResult(stepId = "", status = StepStatus.FAILED, errorMessage = "Task $taskId not found")

        val currentStepId = if (task.currentStepIndex in task.steps.indices) task.steps[task.currentStepIndex].id else ""

        // Terminal state precondition guard
        if (task.isTerminal) {
            return TaskStepResult(
                stepId = currentStepId,
                status = StepStatus.FAILED,
                errorMessage = "Cannot execute step on terminal task: ${task.status}"
            )
        }

        // Paused state precondition guard
        if (task.status == TaskStatus.PAUSED) {
            return TaskStepResult(
                stepId = currentStepId,
                status = StepStatus.FAILED,
                errorMessage = "Cannot execute step on paused task"
            )
        }

        // Awaiting approval precondition guard
        if (task.status == TaskStatus.AWAITING_APPROVAL) {
            return TaskStepResult(
                stepId = currentStepId,
                status = StepStatus.AWAITING_APPROVAL,
                errorMessage = "Task is awaiting approval"
            )
        }

        if (task.currentStepIndex >= task.steps.size) {
            // All steps already completed
            completeTaskIfAllDone(taskId)
            return TaskStepResult(stepId = "", status = StepStatus.COMPLETED)
        }

        val step = task.steps[task.currentStepIndex]
        val stepId = step.id

        // Mark step as RUNNING
        synchronized(lock) {
            updateStepStatusLocked(taskId, stepId, StepStatus.RUNNING, startedAt = System.currentTimeMillis())
        }

        val toolName = step.toolName
        if (toolName != null) {
            val toolDef = toolRegistry.getTool(toolName)
            val sensitivity = toolDef?.sensitivity ?: ToolSensitivity.SAFE

            val requiresApproval = when (sensitivity) {
                ToolSensitivity.SAFE -> false
                ToolSensitivity.SENSITIVE -> !toolRegistry.hasAlwaysAllowedPermission(task.dotId, toolName)
                ToolSensitivity.CRITICAL -> true // Always requires approval
            }

            if (requiresApproval) {
                // Intercept sensitive/critical tool call
                val approvalRequest = ToolApprovalRequest(
                    taskId = taskId,
                    stepId = stepId,
                    dotId = task.dotId,
                    dotName = "Dot-${task.dotId.take(8)}",
                    toolName = toolName,
                    argumentsJson = step.toolInputJson ?: "{}",
                    impactDescription = "Execute tool '$toolName' with sensitivity ${sensitivity.name}",
                    status = ApprovalStatus.PENDING,
                    requestedAt = System.currentTimeMillis()
                )

                synchronized(lock) {
                    _pendingApprovals.value = _pendingApprovals.value + approvalRequest
                    updateStepStatusLocked(taskId, stepId, StepStatus.AWAITING_APPROVAL)
                    updateTaskStatusLocked(taskId, TaskStatus.AWAITING_APPROVAL)
                    saveStateLocked()
                }

                // If a synchronous/direct approval handler is configured, invoke it
                val handler = approvalHandler
                if (handler != null) {
                    val decision = handler.requestApproval(approvalRequest)
                    handleApprovalDecision(approvalRequest.id, decision)
                    val updatedStep = getTaskById(taskId)?.steps?.firstOrNull { it.id == stepId }
                    return TaskStepResult(
                        stepId = stepId,
                        status = updatedStep?.status ?: StepStatus.AWAITING_APPROVAL,
                        outputJson = updatedStep?.toolOutputJson,
                        errorMessage = updatedStep?.errorMessage
                    )
                }

                return TaskStepResult(
                    stepId = stepId,
                    status = StepStatus.AWAITING_APPROVAL,
                    outputJson = null,
                    errorMessage = null
                )
            }

            // Execute safe or pre-approved tool
            return runToolAndRecordStep(task, step, toolDef, sensitivity, approvalStatus = ApprovalStatus.APPROVED_ALWAYS)
        } else {
            // No tool associated: pure reasoning / content step
            val finishedAt = System.currentTimeMillis()
            completeStepAndAdvance(taskId, stepId, outputJson = """{"status":"completed"}""", finishedAt = finishedAt)
            return TaskStepResult(stepId = stepId, status = StepStatus.COMPLETED, outputJson = """{"status":"completed"}""")
        }
    }

    private suspend fun runToolAndRecordStep(
        task: DotTask,
        step: DotTaskStep,
        toolDef: com.mobileai.assistant.domain.tool.ToolDefinition?,
        sensitivity: ToolSensitivity,
        approvalStatus: ApprovalStatus
    ): TaskStepResult {
        val startTime = System.currentTimeMillis()
        val argsJson = step.toolInputJson ?: "{}"

        val executionResult: ToolExecutionResult = try {
            if (toolDef?.handler != null) {
                toolDef.handler.execute(argsJson, task.dotId)
            } else {
                // Built-in synthetic fallback execution for tools without custom handler
                ToolExecutionResult.success("""{"tool":"${toolDef?.name}","status":"ok","echoArgs":$argsJson}""")
            }
        } catch (e: Exception) {
            ToolExecutionResult.failure(e.message ?: "Tool execution failed")
        }

        val duration = System.currentTimeMillis() - startTime
        val finishedAt = System.currentTimeMillis()

        // Log to immutable audit logger
        auditLogger.log(
            ToolAuditLogEntry(
                timestamp = finishedAt,
                dotId = task.dotId,
                dotName = "Dot-${task.dotId.take(8)}",
                conversationId = task.conversationId,
                taskId = task.id,
                stepId = step.id,
                toolName = step.toolName ?: "unknown",
                argumentsJson = argsJson,
                wasSensitive = sensitivity != ToolSensitivity.SAFE,
                approvalStatus = approvalStatus,
                executionStatus = if (executionResult.success) "SUCCESS" else "FAILED",
                outputSummaryJson = executionResult.outputJson,
                durationMs = duration,
                errorMessage = executionResult.errorMessage
            )
        )

        if (executionResult.success) {
            completeStepAndAdvance(task.id, step.id, executionResult.outputJson, finishedAt)
            return TaskStepResult(
                stepId = step.id,
                status = StepStatus.COMPLETED,
                outputJson = executionResult.outputJson
            )
        } else {
            failStepAndTask(task.id, step.id, executionResult.errorMessage ?: "Step execution error", finishedAt)
            return TaskStepResult(
                stepId = step.id,
                status = StepStatus.FAILED,
                errorMessage = executionResult.errorMessage
            )
        }
    }

    override suspend fun handleApprovalDecision(
        requestId: String,
        decision: ToolApprovalDecision,
        rejectionReason: String?
    ): DotTask? {
        var targetTask: DotTask? = null
        var targetStep: DotTaskStep? = null
        var request: ToolApprovalRequest? = null

        synchronized(lock) {
            val req = _pendingApprovals.value.firstOrNull { it.id == requestId } ?: return null
            request = req
            targetTask = _tasks.value.firstOrNull { it.id == req.taskId }
            targetStep = targetTask?.steps?.firstOrNull { it.id == req.stepId }

            val resolvedStatus = when (decision) {
                ToolApprovalDecision.APPROVED -> ApprovalStatus.APPROVED_ONCE
                ToolApprovalDecision.ALWAYS_ALLOWED -> ApprovalStatus.APPROVED_ALWAYS
                ToolApprovalDecision.REJECTED -> ApprovalStatus.REJECTED
            }

            // Update approval request
            val updatedReq = req.copy(
                status = resolvedStatus,
                rejectionReason = rejectionReason,
                resolvedAt = System.currentTimeMillis()
            )
            _pendingApprovals.value = _pendingApprovals.value.map { if (it.id == requestId) updatedReq else it }

            // Pre-transition task and step to RUNNING before running tool, so crash recovery
            // correctly identifies mid-flight execution upon reboot
            if (decision == ToolApprovalDecision.APPROVED || decision == ToolApprovalDecision.ALWAYS_ALLOWED) {
                val now = System.currentTimeMillis()
                if (req.taskId != null && req.stepId != null) {
                    updateStepStatusLocked(req.taskId, req.stepId, StepStatus.RUNNING, startedAt = now)
                    updateTaskStatusLocked(req.taskId, TaskStatus.RUNNING)
                }
            }

            saveStateLocked()

            targetTask = _tasks.value.firstOrNull { it.id == req.taskId }
            targetStep = targetTask?.steps?.firstOrNull { it.id == req.stepId }
        }

        val reqSnapshot = request ?: return null
        val taskSnapshot = targetTask ?: return null
        val stepSnapshot = targetStep ?: return null

        when (decision) {
            ToolApprovalDecision.APPROVED, ToolApprovalDecision.ALWAYS_ALLOWED -> {
                if (decision == ToolApprovalDecision.ALWAYS_ALLOWED) {
                    val toolDef = toolRegistry.getTool(reqSnapshot.toolName)
                    if (toolDef?.sensitivity != ToolSensitivity.CRITICAL) {
                        toolRegistry.setAlwaysAllowedPermission(taskSnapshot.dotId, reqSnapshot.toolName, true)
                    }
                }

                // Resume execution of this step
                val toolDef = toolRegistry.getTool(reqSnapshot.toolName)
                val sensitivity = toolDef?.sensitivity ?: ToolSensitivity.SENSITIVE
                val approvalStatus = if (decision == ToolApprovalDecision.ALWAYS_ALLOWED) {
                    ApprovalStatus.APPROVED_ALWAYS
                } else {
                    ApprovalStatus.APPROVED_ONCE
                }

                runToolAndRecordStep(taskSnapshot, stepSnapshot, toolDef, sensitivity, approvalStatus)
            }
            ToolApprovalDecision.REJECTED -> {
                val finishedAt = System.currentTimeMillis()
                val reason = rejectionReason ?: "Action rejected by user"

                // Log audit for rejection
                auditLogger.log(
                    ToolAuditLogEntry(
                        timestamp = finishedAt,
                        dotId = taskSnapshot.dotId,
                        dotName = reqSnapshot.dotName,
                        conversationId = taskSnapshot.conversationId,
                        taskId = taskSnapshot.id,
                        stepId = stepSnapshot.id,
                        toolName = reqSnapshot.toolName,
                        argumentsJson = reqSnapshot.argumentsJson,
                        wasSensitive = true,
                        approvalStatus = ApprovalStatus.REJECTED,
                        executionStatus = "REJECTED",
                        errorMessage = reason
                    )
                )

                synchronized(lock) {
                    updateStepStatusLocked(
                        taskSnapshot.id,
                        stepSnapshot.id,
                        StepStatus.FAILED,
                        errorMessage = reason,
                        finishedAt = finishedAt
                    )
                    updateTaskStatusLocked(
                        taskSnapshot.id,
                        TaskStatus.PAUSED,
                        failureReason = "Step rejected by user: $reason"
                    )
                    saveStateLocked()
                }
            }
        }

        return getTaskById(taskSnapshot.id)
    }

    override suspend fun pauseTask(taskId: String): DotTask? {
        return synchronized(lock) {
            val task = _tasks.value.firstOrNull { it.id == taskId } ?: return null
            if (task.isTerminal) return task

            val updated = updateTaskStatusLocked(taskId, TaskStatus.PAUSED)
            saveStateLocked()
            updated
        }
    }

    override suspend fun resumeTask(taskId: String): DotTask? {
        val task = synchronized(lock) {
            val existing = _tasks.value.firstOrNull { it.id == taskId } ?: return null
            if (existing.isTerminal) return existing
            updateTaskStatusLocked(taskId, TaskStatus.RUNNING)
        }
        return executeTask(taskId)
    }

    override suspend fun cancelTask(taskId: String, reason: String?): DotTask? {
        return synchronized(lock) {
            val existing = _tasks.value.firstOrNull { it.id == taskId } ?: return null
            if (existing.isTerminal) return existing

            val updated = existing.copy(
                status = TaskStatus.CANCELLED,
                failureReason = reason ?: "Task cancelled by user",
                updatedAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis()
            )
            _tasks.value = _tasks.value.map { if (it.id == taskId) updated else it }
            saveStateLocked()
            updated
        }
    }

    /**
     * Direct recovery injection used by [TaskRecoveryCoordinator].
     */
    internal fun applyRecoveredTasks(recovered: List<DotTask>) {
        synchronized(lock) {
            val map = recovered.associateBy { it.id }
            _tasks.value = _tasks.value.map { map[it.id] ?: it }
            saveStateLocked()
        }
    }

    private fun completeStepAndAdvance(
        taskId: String,
        stepId: String,
        outputJson: String,
        finishedAt: Long
    ) {
        synchronized(lock) {
            val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
            val updatedSteps = task.steps.map { s ->
                if (s.id == stepId) {
                    s.copy(
                        status = StepStatus.COMPLETED,
                        toolOutputJson = outputJson,
                        finishedAt = finishedAt
                    )
                } else s
            }

            val nextStepIndex = task.currentStepIndex + 1
            val completedCount = updatedSteps.count { it.status == StepStatus.COMPLETED }
            val allDone = nextStepIndex >= updatedSteps.size

            val newStatus = when {
                task.status == TaskStatus.CANCELLED -> TaskStatus.CANCELLED
                task.status == TaskStatus.PAUSED -> TaskStatus.PAUSED
                task.status == TaskStatus.FAILED -> TaskStatus.FAILED
                allDone -> TaskStatus.COMPLETED
                else -> TaskStatus.RUNNING
            }

            val newCompletedAt = when {
                task.status == TaskStatus.CANCELLED -> task.completedAt ?: finishedAt
                task.status == TaskStatus.FAILED -> task.completedAt
                task.status == TaskStatus.PAUSED -> null
                newStatus == TaskStatus.COMPLETED -> finishedAt
                else -> null
            }

            val updatedTask = task.copy(
                steps = updatedSteps,
                currentStepIndex = nextStepIndex,
                completedSteps = completedCount,
                status = newStatus,
                completedAt = newCompletedAt,
                updatedAt = finishedAt
            )

            _tasks.value = _tasks.value.map { if (it.id == taskId) updatedTask else it }
            saveStateLocked()
        }
    }

    private fun failStepAndTask(
        taskId: String,
        stepId: String,
        errorMessage: String,
        finishedAt: Long
    ) {
        synchronized(lock) {
            val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
            val updatedSteps = task.steps.map { s ->
                if (s.id == stepId) {
                    s.copy(
                        status = StepStatus.FAILED,
                        errorMessage = errorMessage,
                        finishedAt = finishedAt
                    )
                } else s
            }

            val newStatus = when {
                task.status == TaskStatus.CANCELLED -> TaskStatus.CANCELLED
                task.status == TaskStatus.PAUSED -> TaskStatus.PAUSED
                else -> TaskStatus.FAILED
            }

            val newFailureReason = when {
                task.status == TaskStatus.CANCELLED -> task.failureReason ?: errorMessage
                task.status == TaskStatus.PAUSED -> task.failureReason ?: errorMessage
                else -> errorMessage
            }

            val newCompletedAt = when {
                task.status == TaskStatus.CANCELLED -> task.completedAt ?: finishedAt
                newStatus == TaskStatus.FAILED -> finishedAt
                else -> null
            }

            val updatedTask = task.copy(
                steps = updatedSteps,
                status = newStatus,
                failureReason = newFailureReason,
                completedAt = newCompletedAt,
                updatedAt = finishedAt
            )

            _tasks.value = _tasks.value.map { if (it.id == taskId) updatedTask else it }
            saveStateLocked()
        }
    }

    private fun completeTaskIfAllDone(taskId: String) {
        synchronized(lock) {
            val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
            if (task.isTerminal || task.status == TaskStatus.PAUSED) return

            val allCompleted = task.steps.all { it.status == StepStatus.COMPLETED || it.status == StepStatus.SKIPPED }
            if (allCompleted && task.status != TaskStatus.COMPLETED) {
                val updated = task.copy(
                    status = TaskStatus.COMPLETED,
                    completedSteps = task.steps.size,
                    currentStepIndex = task.steps.size,
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                _tasks.value = _tasks.value.map { if (it.id == taskId) updated else it }
                saveStateLocked()
            }
        }
    }

    private fun updateTaskStatusLocked(taskId: String, status: TaskStatus, failureReason: String? = null): DotTask {
        val task = _tasks.value.firstOrNull { it.id == taskId }
            ?: throw IllegalArgumentException("Task $taskId not found")
        val updated = task.copy(
            status = status,
            failureReason = failureReason ?: task.failureReason,
            updatedAt = System.currentTimeMillis()
        )
        _tasks.value = _tasks.value.map { if (it.id == taskId) updated else it }
        saveStateLocked()
        return updated
    }

    private fun updateStepStatusLocked(
        taskId: String,
        stepId: String,
        status: StepStatus,
        startedAt: Long? = null,
        finishedAt: Long? = null,
        errorMessage: String? = null
    ) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        val updatedSteps = task.steps.map { s ->
            if (s.id == stepId) {
                s.copy(
                    status = status,
                    startedAt = startedAt ?: s.startedAt,
                    finishedAt = finishedAt ?: s.finishedAt,
                    errorMessage = errorMessage ?: s.errorMessage
                )
            } else s
        }
        val updatedTask = task.copy(
            steps = updatedSteps,
            updatedAt = System.currentTimeMillis()
        )
        _tasks.value = _tasks.value.map { if (it.id == taskId) updatedTask else it }
        saveStateLocked()
    }

    private fun saveStateLocked() {
        val dir = rootDir ?: return
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Save tasks_v1.json
        val tasksTarget = File(dir, FILE_TASKS)
        val tasksTmp = File(dir, "$FILE_TASKS.tmp")
        try {
            val content = json.encodeToString(TaskStoreEnvelope(version = 1, tasks = _tasks.value))
            tasksTmp.writeText(content)
            if (!tasksTmp.renameTo(tasksTarget)) {
                tasksTmp.copyTo(tasksTarget, overwrite = true)
                tasksTmp.delete()
            }
        } catch (_: Exception) {}

        // Save task_steps_v1.json
        val stepsTarget = File(dir, FILE_STEPS)
        val stepsTmp = File(dir, "$FILE_STEPS.tmp")
        try {
            val allSteps = _tasks.value.flatMap { it.steps }
            val content = json.encodeToString(TaskStepStoreEnvelope(version = 1, steps = allSteps))
            stepsTmp.writeText(content)
            if (!stepsTmp.renameTo(stepsTarget)) {
                stepsTmp.copyTo(stepsTarget, overwrite = true)
                stepsTmp.delete()
            }
        } catch (_: Exception) {}

        // Save approvals_v1.json
        val approvalsTarget = File(dir, FILE_APPROVALS)
        val approvalsTmp = File(dir, "$FILE_APPROVALS.tmp")
        try {
            val content = json.encodeToString(ApprovalStoreEnvelope(version = 1, requests = _pendingApprovals.value))
            approvalsTmp.writeText(content)
            if (!approvalsTmp.renameTo(approvalsTarget)) {
                approvalsTmp.copyTo(approvalsTarget, overwrite = true)
                approvalsTmp.delete()
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val FILE_TASKS = "tasks_v1.json"
        const val FILE_STEPS = "task_steps_v1.json"
        const val FILE_APPROVALS = "tool_approvals_v1.json"
    }
}
