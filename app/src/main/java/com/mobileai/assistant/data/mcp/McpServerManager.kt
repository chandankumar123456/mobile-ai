package com.mobileai.assistant.data.mcp

import android.content.Context
import com.mobileai.assistant.data.persistence.ReliableStorageManager
import com.mobileai.assistant.domain.mcp.McpServerConfig
import com.mobileai.assistant.domain.mcp.McpToolDefinition
import com.mobileai.assistant.domain.tool.ToolCategory
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolRegistry
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class McpServerStoreEnvelope(
    val version: Int = 1,
    val servers: List<McpServerConfig> = emptyList()
)

/**
 * Manages external Model Context Protocol (MCP) server lifecycle, configuration persistence,
 * tool discovery, and dynamic bridging into [ToolRegistry].
 */
class McpServerManager(
    private val context: Context? = null,
    rootDirOverride: File? = null,
    private val toolRegistry: ToolRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        const val FILE_MCP_SERVERS = "mcp_servers_v1.json"
    }

    private val rootDir: File? = rootDirOverride ?: context?.filesDir

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val clients = ConcurrentHashMap<String, McpClient>()
    private val bridgedToolNames = ConcurrentHashMap.newKeySet<String>()

    private val _servers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    init {
        loadServers()
    }

    private fun loadServers() {
        val dir = rootDir ?: return
        val file = File(dir, FILE_MCP_SERVERS)
        val content = ReliableStorageManager.readSafe(file)
        if (!content.isNullOrBlank()) {
            try {
                val envelope = json.decodeFromString<McpServerStoreEnvelope>(content)
                _servers.value = envelope.servers
            } catch (_: Exception) {
                _servers.value = emptyList()
            }
        }
    }

    private fun saveServers() {
        val dir = rootDir ?: return
        val file = File(dir, FILE_MCP_SERVERS)
        val envelope = McpServerStoreEnvelope(version = 1, servers = _servers.value)
        val serialized = json.encodeToString(envelope)
        ReliableStorageManager.writeAtomic(file, serialized)
    }

    suspend fun addServer(config: McpServerConfig): Result<List<McpToolDefinition>> {
        _servers.value = _servers.value.filterNot { it.id == config.id } + config
        saveServers()

        return if (config.enabled) {
            connectAndBridgeServer(config)
        } else {
            Result.success(emptyList())
        }
    }

    suspend fun removeServer(serverId: String) {
        val client = clients.remove(serverId)
        client?.disconnect()
        _servers.value = _servers.value.filterNot { it.id == serverId }
        saveServers()
    }

    suspend fun toggleServer(serverId: String, enabled: Boolean) {
        val target = _servers.value.find { it.id == serverId } ?: return
        val updated = target.copy(enabled = enabled)
        _servers.value = _servers.value.map { if (it.id == serverId) updated else it }
        saveServers()

        if (enabled) {
            connectAndBridgeServer(updated)
        } else {
            clients.remove(serverId)?.disconnect()
        }
    }

    /**
     * Connects to enabled servers, discovers their tools, and registers them into [ToolRegistry].
     */
    suspend fun refreshAllServers(): List<McpToolDefinition> {
        val allDiscovered = mutableListOf<McpToolDefinition>()
        _servers.value.filter { it.enabled }.forEach { config ->
            val result = connectAndBridgeServer(config)
            result.getOrNull()?.let { allDiscovered.addAll(it) }
        }
        return allDiscovered
    }

    private suspend fun connectAndBridgeServer(config: McpServerConfig): Result<List<McpToolDefinition>> {
        val client = clients.computeIfAbsent(config.id) { McpClient(config) }
        val connectResult = client.connect()

        if (connectResult.isSuccess) {
            val tools = connectResult.getOrDefault(emptyList())
            bridgeToolsIntoRegistry(config, client, tools)
            return Result.success(tools)
        }
        return connectResult
    }

    private fun bridgeToolsIntoRegistry(
        config: McpServerConfig,
        client: McpClient,
        tools: List<McpToolDefinition>
    ) {
        tools.forEach { mcpTool ->
            val toolName = mcpTool.name
            val sensitivity = classifyMcpToolSensitivity(toolName, mcpTool.description)

            val toolDef = ToolDefinition(
                id = "mcp-${config.id}-$toolName",
                name = toolName,
                description = mcpTool.description ?: "MCP tool from ${config.name}",
                category = ToolCategory.NETWORK,
                sensitivity = sensitivity,
                parameterSchemaJson = mcpTool.inputSchema,
                handler = { argsJson, dotId ->
                    // 1. Enforce Per-Dot Permissions
                    if (config.allowedDotIds.isNotEmpty() && !config.allowedDotIds.contains(dotId)) {
                        return@ToolDefinition ToolExecutionResult.failure(
                            "Dot '$dotId' does not have permission to access MCP server '${config.name}'"
                        )
                    }

                    // 2. Real remote execution via MCP Client (zero synthetic mocks)
                    client.callTool(toolName, argsJson)
                }
            )

            toolRegistry.registerTool(toolDef)
            bridgedToolNames.add(toolName)
        }
    }

    /**
     * Infers appropriate sensitivity level based on tool name and semantic description.
     */
    fun classifyMcpToolSensitivity(name: String, description: String?): ToolSensitivity {
        val lower = (name + " " + (description ?: "")).lowercase()
        return when {
            lower.contains("delete") || lower.contains("drop") || lower.contains("destroy") ||
            lower.contains("wipe") || lower.contains("purge") || lower.contains("terminate") -> {
                ToolSensitivity.CRITICAL
            }
            lower.contains("write") || lower.contains("create") || lower.contains("update") ||
            lower.contains("modify") || lower.contains("insert") || lower.contains("send") ||
            lower.contains("post") || lower.contains("upload") || lower.contains("execute") -> {
                ToolSensitivity.SENSITIVE
            }
            else -> ToolSensitivity.SAFE
        }
    }
}
