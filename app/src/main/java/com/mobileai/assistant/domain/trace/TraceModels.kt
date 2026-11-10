package com.mobileai.assistant.domain.trace

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TraceCategory {
    INFERENCE,
    MODEL_LOAD,
    TOOL_CALL,
    MCP,
    AGENT_TASK,
    GENUI,
    OPENUI,
    A2UI,
    PERSISTENCE
}

@Serializable
enum class TraceLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

@Serializable
data class TraceEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val correlationId: String,
    val category: TraceCategory,
    val level: TraceLevel = TraceLevel.INFO,
    val tag: String,
    val message: String,
    val durationMs: Long? = null,
    val attributes: Map<String, String> = emptyMap()
)

