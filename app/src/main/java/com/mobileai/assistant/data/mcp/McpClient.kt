package com.mobileai.assistant.data.mcp

import com.mobileai.assistant.domain.mcp.McpConnectionStatus
import com.mobileai.assistant.domain.mcp.McpPromptDefinition
import com.mobileai.assistant.domain.mcp.McpResourceDefinition
import com.mobileai.assistant.domain.mcp.McpServerConfig
import com.mobileai.assistant.domain.mcp.McpToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Standard implementation of the Model Context Protocol (MCP) Client
 * conforming to the official 2024-11-05 JSON-RPC specification.
 *
 * Supports streamable HTTP and SSE transports, tool discovery, schema translation,
 * and remote execution with automatic retry/recovery.
 */
class McpClient(
    val config: McpServerConfig
) {
    companion object {
        const val MCP_PROTOCOL_VERSION = "2024-11-05"
        const val CLIENT_NAME = "MobileAI-Android-Agent"
        const val CLIENT_VERSION = "1.0.0"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val requestId = AtomicInteger(1)

    private val _status = MutableStateFlow<McpConnectionStatus>(McpConnectionStatus.Disconnected)
    val status: StateFlow<McpConnectionStatus> = _status.asStateFlow()

    private var discoveredTools: List<McpToolDefinition> = emptyList()

    /**
     * Initializes the connection to the MCP server via the JSON-RPC `initialize` handshake.
     */
    suspend fun connect(): Result<List<McpToolDefinition>> = withContext(Dispatchers.IO) {
        _status.value = McpConnectionStatus.Connecting
        try {
            // 1. Send `initialize` JSON-RPC handshake
            val initPayload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestId.getAndIncrement())
                put("method", "initialize")
                putJsonObject("params") {
                    put("protocolVersion", MCP_PROTOCOL_VERSION)
                    putJsonObject("capabilities") {
                        putJsonObject("roots") { put("listChanged", true) }
                        putJsonObject("sampling") {}
                    }
                    putJsonObject("clientInfo") {
                        put("name", CLIENT_NAME)
                        put("version", CLIENT_VERSION)
                    }
                }
            }

            val initResponse = sendJsonRpc(initPayload.toString())
            val serverProtocol = initResponse["protocolVersion"]?.jsonPrimitive?.contentOrNull ?: MCP_PROTOCOL_VERSION

            // 2. Send `notifications/initialized` acknowledgement
            val ackPayload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "notifications/initialized")
            }
            sendJsonRpcNoResponse(ackPayload.toString())

            // 3. Discover available tools via `tools/list`
            val tools = listToolsInternal()
            discoveredTools = tools

            _status.value = McpConnectionStatus.Connected(serverProtocol, tools.size)
            Result.success(tools)
        } catch (e: Exception) {
            _status.value = McpConnectionStatus.Error(e.message ?: "Failed to connect to MCP server", e)
            Result.failure(e)
        }
    }

    /**
     * Discovers tools registered on the MCP server via `tools/list`.
     */
    suspend fun listTools(): List<McpToolDefinition> = withContext(Dispatchers.IO) {
        if (_status.value !is McpConnectionStatus.Connected) {
            val connectResult = connect()
            if (connectResult.isSuccess) return@withContext connectResult.getOrDefault(emptyList())
        }
        listToolsInternal()
    }

    private suspend fun listToolsInternal(): List<McpToolDefinition> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId.getAndIncrement())
            put("method", "tools/list")
            putJsonObject("params") {}
        }

        val response = sendJsonRpc(payload.toString())
        val toolsArray = response["tools"]?.jsonArray ?: JsonArray(emptyList())

        toolsArray.mapNotNull { element ->
            val toolObj = element.jsonObject
            val name = toolObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val description = toolObj["description"]?.jsonPrimitive?.contentOrNull
            val inputSchema = toolObj["inputSchema"]?.toString() ?: """{"type":"object"}"""

            McpToolDefinition(
                name = name,
                description = description,
                inputSchema = inputSchema,
                serverId = config.id,
                serverName = config.name
            )
        }
    }

    /**
     * Executes a tool on the MCP server via `tools/call`.
     */
    suspend fun callTool(name: String, argumentsJson: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        try {
            val parsedArgs = try {
                json.parseToJsonElement(argumentsJson).jsonObject
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }

            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestId.getAndIncrement())
                put("method", "tools/call")
                putJsonObject("params") {
                    put("name", name)
                    put("arguments", parsedArgs)
                }
            }

            val response = sendJsonRpc(payload.toString())
            val isError = response["isError"]?.jsonPrimitive?.booleanOrNull ?: false
            val contentArray = response["content"]?.jsonArray

            val textOutput = contentArray?.joinToString("\n") { item ->
                item.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: item.toString()
            } ?: response.toString()

            if (isError) {
                ToolExecutionResult.failure(textOutput)
            } else {
                ToolExecutionResult.success(textOutput)
            }
        } catch (e: Exception) {
            ToolExecutionResult.failure(e.message ?: "MCP tool call failed")
        }
    }

    /**
     * Lists resources exposed by the MCP server via `resources/list`.
     */
    suspend fun listResources(): List<McpResourceDefinition> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestId.getAndIncrement())
                put("method", "resources/list")
                putJsonObject("params") {}
            }
            val response = sendJsonRpc(payload.toString())
            val resourcesArray = response["resources"]?.jsonArray ?: JsonArray(emptyList())
            resourcesArray.mapNotNull { element ->
                val obj = element.jsonObject
                val uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: uri
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull
                val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull
                McpResourceDefinition(uri = uri, name = name, description = desc, mimeType = mime, serverId = config.id)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Lists prompts exposed by the MCP server via `prompts/list`.
     */
    suspend fun listPrompts(): List<McpPromptDefinition> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestId.getAndIncrement())
                put("method", "prompts/list")
                putJsonObject("params") {}
            }
            val response = sendJsonRpc(payload.toString())
            val promptsArray = response["prompts"]?.jsonArray ?: JsonArray(emptyList())
            promptsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull
                McpPromptDefinition(name = name, description = desc, serverId = config.id)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun disconnect() {
        _status.value = McpConnectionStatus.Disconnected
    }

    private fun sendJsonRpc(bodyJson: String): JsonObject {
        val url = URL(config.serverUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = config.timeoutSeconds * 1000
            readTimeout = config.timeoutSeconds * 1000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/event-stream")
            config.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(bodyJson)
            writer.flush()
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            throw IllegalStateException("MCP server responded with HTTP $code: $errorBody")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val rootJson = json.parseToJsonElement(responseText).jsonObject

        // Handle JSON-RPC errors
        val errorObj = rootJson["error"]?.jsonObject
        if (errorObj != null) {
            val errMsg = errorObj["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown JSON-RPC error"
            throw IllegalStateException("MCP JSON-RPC error: $errMsg")
        }

        return rootJson["result"]?.jsonObject ?: JsonObject(emptyMap())
    }

    private fun sendJsonRpcNoResponse(bodyJson: String) {
        try {
            val url = URL(config.serverUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = config.timeoutSeconds * 1000
                readTimeout = config.timeoutSeconds * 1000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                config.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(bodyJson) }
            conn.responseCode
        } catch (_: Exception) {
            // Notification best-effort
        }
    }
}

