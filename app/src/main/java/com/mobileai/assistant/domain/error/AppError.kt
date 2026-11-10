package com.mobileai.assistant.domain.error

import kotlinx.serialization.Serializable

/**
 * Root typed error interface across the entire On-Device Agentic AI Platform.
 * Replaces unstructured string errors with typed, recoverable, categorized errors.
 */
@Serializable
sealed class AppError : Exception() {
    abstract val code: String
    abstract override val message: String
    abstract val isRecoverable: Boolean
    abstract val userFriendlyMessage: String

    // ========================================================================
    // 1. Inference and Native Runtime Errors
    // ========================================================================
    sealed class InferenceError : AppError() {
        data class OutOfMemory(
            override val message: String = "Out of memory during native tensor allocation",
            val requiredBytes: Long? = null
        ) : InferenceError() {
            override val code: String = "ERR_INFERENCE_OOM"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "Device ran out of memory while generating response."
        }

        data class ContextOverflow(
            val contextSize: Int,
            val maxTokens: Int,
            override val message: String = "Context size $contextSize exceeded limit $maxTokens"
        ) : InferenceError() {
            override val code: String = "ERR_INFERENCE_CONTEXT_OVERFLOW"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Conversation exceeded context limit. Trimming previous history."
        }

        data class NativeBridgeFailure(
            val returnCode: Int,
            override val message: String = "Native llama.cpp call failed with code $returnCode"
        ) : InferenceError() {
            override val code: String = "ERR_INFERENCE_NATIVE_FAILURE"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "Native AI engine error occurred."
        }

        data class GenerationCancelled(
            override val message: String = "Generation was cancelled by the user"
        ) : InferenceError() {
            override val code: String = "ERR_INFERENCE_CANCELLED"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Response generation stopped."
        }
    }

    // ========================================================================
    // 2. Model Loading Errors
    // ========================================================================
    sealed class ModelLoadError : AppError() {
        data class FileNotFound(
            val path: String,
            override val message: String = "GGUF model file not found at: $path"
        ) : ModelLoadError() {
            override val code: String = "ERR_MODEL_NOT_FOUND"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Model file could not be found on storage."
        }

        data class CorruptedGguf(
            val path: String,
            override val message: String = "Corrupted GGUF header or invalid magic number at: $path"
        ) : ModelLoadError() {
            override val code: String = "ERR_MODEL_CORRUPTED"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "The selected model file is corrupted or invalid."
        }

        data class InsufficientRam(
            val requiredMb: Long,
            val availableMb: Long,
            override val message: String = "Insufficient RAM. Model requires $requiredMb MB, available $availableMb MB"
        ) : ModelLoadError() {
            override val code: String = "ERR_MODEL_INSUFFICIENT_RAM"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Not enough free RAM to load this model. Try a smaller quantization."
        }
    }

    // ========================================================================
    // 3. Streaming & Network Errors
    // ========================================================================
    sealed class StreamingError : AppError() {
        data class NetworkTimeout(
            override val message: String = "Remote inference request timed out"
        ) : StreamingError() {
            override val code: String = "ERR_STREAM_TIMEOUT"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Connection to remote model timed out. Please retry."
        }

        data class HttpError(
            val statusCode: Int,
            val body: String?,
            override val message: String = "HTTP error $statusCode: $body"
        ) : StreamingError() {
            override val code: String = "ERR_STREAM_HTTP_$statusCode"
            override val isRecoverable: Boolean = statusCode in 500..599
            override val userFriendlyMessage: String = "Remote server returned an error ($statusCode)."
        }

        data class MalformedChunk(
            val chunk: String,
            override val message: String = "Malformed SSE chunk received: $chunk"
        ) : StreamingError() {
            override val code: String = "ERR_STREAM_MALFORMED_CHUNK"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Stream received invalid data chunk."
        }
    }

    // ========================================================================
    // 4. Tool Execution Errors
    // ========================================================================
    sealed class ToolExecutionError : AppError() {
        data class ToolNotFound(
            val toolName: String,
            override val message: String = "Tool '$toolName' is not registered in ToolRegistry"
        ) : ToolExecutionError() {
            override val code: String = "ERR_TOOL_NOT_FOUND"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "Requested tool '$toolName' is unavailable."
        }

        data class InvalidArguments(
            val toolName: String,
            val reason: String,
            override val message: String = "Invalid arguments for tool '$toolName': $reason"
        ) : ToolExecutionError() {
            override val code: String = "ERR_TOOL_INVALID_ARGS"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Tool parameters were invalid: $reason"
        }

        data class PermissionDenied(
            val toolName: String,
            val dotId: String,
            override val message: String = "Dot '$dotId' does not have permission to execute tool '$toolName'"
        ) : ToolExecutionError() {
            override val code: String = "ERR_TOOL_PERMISSION_DENIED"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "Dot lacks permission to use '$toolName'."
        }

        data class ExecutionFailed(
            val toolName: String,
            val detail: String,
            override val message: String = "Tool '$toolName' execution failed: $detail"
        ) : ToolExecutionError() {
            override val code: String = "ERR_TOOL_EXECUTION_FAILED"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Tool '$toolName' encountered an error: $detail"
        }
    }

    // ========================================================================
    // 5. Model Context Protocol (MCP) Errors
    // ========================================================================
    sealed class McpError : AppError() {
        data class ConnectionFailed(
            val serverUri: String,
            override val message: String = "Failed to connect to MCP server at: $serverUri"
        ) : McpError() {
            override val code: String = "ERR_MCP_CONNECTION_FAILED"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Could not connect to MCP server."
        }

        data class ProtocolViolation(
            val detail: String,
            override val message: String = "MCP protocol violation: $detail"
        ) : McpError() {
            override val code: String = "ERR_MCP_PROTOCOL_VIOLATION"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "MCP protocol communication failed."
        }

        data class ToolDiscoveryFailed(
            val serverUri: String,
            override val message: String = "Failed to discover tools on MCP server: $serverUri"
        ) : McpError() {
            override val code: String = "ERR_MCP_TOOL_DISCOVERY"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Failed to retrieve tools from MCP server."
        }
    }

    // ========================================================================
    // 6. Agent Task Errors
    // ========================================================================
    sealed class AgentTaskError : AppError() {
        data class PlanGenerationFailed(
            val taskId: String,
            override val message: String = "Model failed to generate valid multi-step plan for task $taskId"
        ) : AgentTaskError() {
            override val code: String = "ERR_AGENT_PLAN_FAILED"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Agent was unable to formulate a plan for this goal."
        }

        data class ApprovalRejected(
            val taskId: String,
            val toolName: String,
            val reason: String?,
            override val message: String = "User rejected tool approval for '$toolName' on task $taskId: $reason"
        ) : AgentTaskError() {
            override val code: String = "ERR_AGENT_APPROVAL_REJECTED"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Operation was rejected by user."
        }

        data class CheckpointCorrupted(
            val taskId: String,
            override val message: String = "Task checkpoint state corrupted for task: $taskId"
        ) : AgentTaskError() {
            override val code: String = "ERR_AGENT_CHECKPOINT_CORRUPTED"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "Agent task state could not be recovered."
        }
    }

    // ========================================================================
    // 7. Generative UI (GenUI) Errors
    // ========================================================================
    sealed class GenUiError : AppError() {
        data class JsonSyntaxError(
            val rawJson: String,
            override val message: String = "GenUI payload contains unrecoverable JSON syntax errors"
        ) : GenUiError() {
            override val code: String = "ERR_GENUI_JSON_SYNTAX"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Generated UI could not be parsed."
        }

        data class SchemaValidationError(
            val missingFields: List<String>,
            override val message: String = "GenUI schema missing required fields: $missingFields"
        ) : GenUiError() {
            override val code: String = "ERR_GENUI_SCHEMA_INVALID"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Generated UI components were incomplete."
        }

        data class AntiDosPayloadTooLarge(
            val byteCount: Int,
            val maxAllowedBytes: Int,
            override val message: String = "GenUI payload size ($byteCount bytes) exceeds anti-DoS limit ($maxAllowedBytes bytes)"
        ) : GenUiError() {
            override val code: String = "ERR_GENUI_PAYLOAD_TOO_LARGE"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "Generated UI exceeded safe complexity limits."
        }
    }

    // ========================================================================
    // 8. OpenUI Lang Errors
    // ========================================================================
    sealed class OpenUiError : AppError() {
        data class ParseError(
            val line: Int,
            val column: Int,
            val detail: String,
            override val message: String = "OpenUI Lang syntax error at [$line:$column]: $detail"
        ) : OpenUiError() {
            override val code: String = "ERR_OPENUI_PARSE"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "OpenUI syntax error at line $line: $detail"
        }

        data class UndefinedComponent(
            val componentName: String,
            override val message: String = "OpenUI component '$componentName' is not supported"
        ) : OpenUiError() {
            override val code: String = "ERR_OPENUI_UNDEFINED_COMPONENT"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Unsupported OpenUI component: $componentName"
        }
    }

    // ========================================================================
    // 9. Google A2UI Errors
    // ========================================================================
    sealed class A2UiError : AppError() {
        data class SurfaceNotFound(
            val surfaceId: String,
            override val message: String = "A2UI Surface '$surfaceId' does not exist"
        ) : A2UiError() {
            override val code: String = "ERR_A2UI_SURFACE_NOT_FOUND"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Interface surface '$surfaceId' was not found."
        }

        data class ProtocolViolation(
            val detail: String,
            override val message: String = "A2UI message protocol violation: $detail"
        ) : A2UiError() {
            override val code: String = "ERR_A2UI_PROTOCOL_VIOLATION"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "A2UI protocol message was malformed."
        }

        data class FlatTreeResolutionError(
            val rootId: String,
            val missingChildId: String,
            override val message: String = "A2UI flat tree cannot resolve child '$missingChildId' from root '$rootId'"
        ) : A2UiError() {
            override val code: String = "ERR_A2UI_TREE_RESOLUTION"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Failed to assemble A2UI component tree."
        }
    }

    // ========================================================================
    // 10. Persistence & Storage Errors
    // ========================================================================
    sealed class PersistenceError : AppError() {
        data class ChecksumMismatch(
            val fileName: String,
            val expected: String,
            val actual: String,
            override val message: String = "Checksum mismatch in $fileName: expected $expected, found $actual"
        ) : PersistenceError() {
            override val code: String = "ERR_PERSISTENCE_CHECKSUM"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Storage file integrity check failed. Restoring from backup."
        }

        data class MigrationFailed(
            val fromVersion: Int,
            val toVersion: Int,
            val reason: String,
            override val message: String = "Failed migration from v$fromVersion to v$toVersion: $reason"
        ) : PersistenceError() {
            override val code: String = "ERR_PERSISTENCE_MIGRATION"
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String = "Database migration failed."
        }

        data class WriteFailed(
            val fileName: String,
            val reason: String,
            override val message: String = "Failed atomic write to $fileName: $reason"
        ) : PersistenceError() {
            override val code: String = "ERR_PERSISTENCE_WRITE"
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String = "Could not save data to storage."
        }
    }
}
