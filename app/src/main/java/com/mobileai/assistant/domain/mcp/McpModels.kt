package com.mobileai.assistant.domain.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Supported MCP transport mechanisms.
 */
@Serializable
enum class McpTransportType {
    STREAMABLE_HTTP,
    SSE
}

/**
 * Configuration for an external Model Context Protocol (MCP) server.
 */
@Serializable
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverUrl: String,
    val transportType: McpTransportType = McpTransportType.STREAMABLE_HTTP,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val allowedDotIds: Set<String> = emptySet(), // Empty means accessible by all Dots
    val timeoutSeconds: Int = 30,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Representation of a tool discovered from an MCP server.
 */
@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String? = null,
    val inputSchema: String, // Raw JSON Schema string
    val serverId: String,
    val serverName: String
)

/**
 * Representation of a resource exposed by an MCP server.
 */
@Serializable
data class McpResourceDefinition(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
    val serverId: String
)

/**
 * Representation of a prompt template exposed by an MCP server.
 */
@Serializable
data class McpPromptDefinition(
    val name: String,
    val description: String? = null,
    val serverId: String
)

/**
 * Status of an MCP server connection.
 */
sealed interface McpConnectionStatus {
    data object Disconnected : McpConnectionStatus
    data object Connecting : McpConnectionStatus
    data class Connected(val protocolVersion: String, val toolCount: Int) : McpConnectionStatus
    data class Error(val message: String, val throwable: Throwable? = null) : McpConnectionStatus
}

