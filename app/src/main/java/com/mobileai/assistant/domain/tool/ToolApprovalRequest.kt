package com.mobileai.assistant.domain.tool

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Resolution status of a tool approval request.
 */
@Serializable
enum class ApprovalStatus {
    PENDING,
    APPROVED_ONCE,
    APPROVED_ALWAYS,
    REJECTED
}

/**
 * User decision when reviewing a sensitive or critical tool approval request.
 */
@Serializable
enum class ToolApprovalDecision {
    APPROVED,
    ALWAYS_ALLOWED,
    REJECTED
}

/**
 * Request emitted when an agent attempts to invoke a sensitive or critical tool.
 */
@Serializable
data class ToolApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val stepId: String? = null,
    val dotId: String,
    val dotName: String,
    val toolName: String,
    val argumentsJson: String,
    val impactDescription: String,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val rejectionReason: String? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
)

/**
 * Contract for intercepting and obtaining human-in-the-loop approval.
 */
interface ToolApprovalHandler {
    suspend fun requestApproval(request: ToolApprovalRequest): ToolApprovalDecision
}
