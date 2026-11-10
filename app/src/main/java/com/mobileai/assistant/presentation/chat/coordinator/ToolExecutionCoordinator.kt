package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.domain.tool.ApprovalStatus
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolApprovalRequest
import com.mobileai.assistant.domain.tool.ToolAuditLogEntry
import com.mobileai.assistant.domain.tool.ToolAuditLogger
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolRegistry
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Coordinator responsible for tool execution gating, sensitivity checks,
 * human-in-the-loop approvals, and audit logging.
 */
class ToolExecutionCoordinator(
    private val toolRegistry: ToolRegistry?,
    private val auditLogger: ToolAuditLogger?
) {
    private val _pendingApprovals = MutableStateFlow<List<ToolApprovalRequest>>(emptyList())
    val pendingApprovals: StateFlow<List<ToolApprovalRequest>> = _pendingApprovals.asStateFlow()

    fun getAvailableTools(): List<ToolDefinition> {
        return toolRegistry?.getAllTools() ?: emptyList()
    }

    suspend fun requestApproval(
        toolName: String,
        argumentsJson: String,
        dotId: String,
        dotName: String = "Assistant",
        impactDescription: String = "Execute $toolName",
        taskId: String? = null,
        stepId: String? = null
    ): ToolApprovalRequest {
        val tool = toolRegistry?.getTool(toolName)
        val request = ToolApprovalRequest(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            stepId = stepId,
            dotId = dotId,
            dotName = dotName,
            toolName = toolName,
            argumentsJson = argumentsJson,
            impactDescription = impactDescription
        )
        _pendingApprovals.value = _pendingApprovals.value + request
        return request
    }

    suspend fun resolveApproval(
        requestId: String,
        decision: ToolApprovalDecision,
        rejectionReason: String? = null
    ): ToolApprovalRequest? {
        val currentList = _pendingApprovals.value
        val target = currentList.find { it.id == requestId } ?: return null
        _pendingApprovals.value = currentList.filterNot { it.id == requestId }
        return target
    }

    suspend fun executeToolDirect(
        toolName: String,
        argumentsJson: String,
        dotId: String,
        dotName: String = "Assistant",
        conversationId: String? = null,
        taskId: String? = null,
        stepId: String? = null
    ): ToolExecutionResult {
        val registry = toolRegistry
            ?: return ToolExecutionResult.failure("ToolRegistry is not available")
        val tool = registry.getTool(toolName)
            ?: return ToolExecutionResult.failure("Tool '$toolName' is not registered")

        val isSensitive = tool.sensitivity != ToolSensitivity.SAFE
        val startTime = System.currentTimeMillis()

        return try {
            val handler = tool.handler
            val result = handler?.execute(argumentsJson, dotId)
                ?: ToolExecutionResult.failure("No handler registered for tool '$toolName'")
            val duration = System.currentTimeMillis() - startTime

            auditLogger?.log(
                ToolAuditLogEntry(
                    dotId = dotId,
                    dotName = dotName,
                    conversationId = conversationId,
                    taskId = taskId,
                    stepId = stepId,
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    wasSensitive = isSensitive,
                    approvalStatus = if (isSensitive) ApprovalStatus.APPROVED_ONCE else ApprovalStatus.PENDING,
                    executionStatus = if (result.success) "SUCCESS" else "FAILED",
                    outputSummaryJson = if (result.success) result.outputJson.take(200) else null,
                    durationMs = duration,
                    errorMessage = result.errorMessage
                )
            )
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val msg = e.message ?: "Tool execution failed"
            auditLogger?.log(
                ToolAuditLogEntry(
                    dotId = dotId,
                    dotName = dotName,
                    conversationId = conversationId,
                    taskId = taskId,
                    stepId = stepId,
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    wasSensitive = isSensitive,
                    approvalStatus = if (isSensitive) ApprovalStatus.APPROVED_ONCE else ApprovalStatus.PENDING,
                    executionStatus = "FAILED",
                    outputSummaryJson = null,
                    durationMs = duration,
                    errorMessage = msg
                )
            )
            ToolExecutionResult.failure(msg)
        }
    }
}
