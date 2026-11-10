package com.mobileai.assistant.domain.tool

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Immutable audit trail entry recording tool invocations, inputs, approval states, and execution results.
 */
@Serializable
data class ToolAuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val dotId: String,
    val dotName: String,
    val conversationId: String? = null,
    val taskId: String? = null,
    val stepId: String? = null,
    val toolName: String,
    val argumentsJson: String,
    val wasSensitive: Boolean,
    val approvalStatus: ApprovalStatus,
    val executionStatus: String, // "SUCCESS", "FAILED", "REJECTED"
    val outputSummaryJson: String? = null,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Contract for appending and querying immutable tool audit logs.
 */
interface ToolAuditLogger {
    suspend fun log(entry: ToolAuditLogEntry)
    fun getAuditLogs(): Flow<List<ToolAuditLogEntry>>
    fun getLogsForDot(dotId: String): Flow<List<ToolAuditLogEntry>>
    fun getLogsForTask(taskId: String): Flow<List<ToolAuditLogEntry>>
    suspend fun clearLogs()
}
