package com.mobileai.assistant.domain.tool

import kotlinx.serialization.Serializable

/**
 * Three-tier sensitivity rating governing whether a tool requires human confirmation.
 */
@Serializable
enum class ToolSensitivity {
    /** Pure computation, read-only data query. Auto-allowed without prompt. */
    SAFE,

    /** Modifies workspace, creates files, writes memory. Requires user approval. */
    SENSITIVE,

    /** Destructive actions (deletion, persistent wipe, external network mutations). Always requires prompt. */
    CRITICAL
}

/**
 * Functional category for tools.
 */
@Serializable
enum class ToolCategory {
    WORKSPACE,
    STORAGE,
    NETWORK,
    MEMORY,
    SYSTEM
}

/**
 * Result returned by a tool invocation.
 */
@Serializable
data class ToolExecutionResult(
    val success: Boolean,
    val outputJson: String,
    val errorMessage: String? = null
) {
    companion object {
        fun success(outputJson: String = "{}"): ToolExecutionResult =
            ToolExecutionResult(success = true, outputJson = outputJson)

        fun failure(errorMessage: String, outputJson: String = "{}"): ToolExecutionResult =
            ToolExecutionResult(success = false, outputJson = outputJson, errorMessage = errorMessage)
    }
}

/**
 * Execution handler for an agent tool.
 */
fun interface ToolHandler {
    suspend fun execute(argumentsJson: String, dotId: String): ToolExecutionResult
}

/**
 * Formal definition of an agent tool available for execution by Dots.
 */
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val sensitivity: ToolSensitivity,
    val parameterSchemaJson: String = "{}",
    val handler: ToolHandler? = null
)
