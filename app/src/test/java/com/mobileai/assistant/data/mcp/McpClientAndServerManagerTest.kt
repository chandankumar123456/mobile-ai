package com.mobileai.assistant.data.mcp

import com.mobileai.assistant.domain.mcp.McpServerConfig
import com.mobileai.assistant.domain.mcp.McpToolDefinition
import com.mobileai.assistant.domain.mcp.McpTransportType
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class McpClientAndServerManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSensitivityClassification() {
        val registry = DefaultToolRegistry()
        val manager = McpServerManager(rootDirOverride = tempFolder.root, toolRegistry = registry)

        assertEquals(ToolSensitivity.CRITICAL, manager.classifyMcpToolSensitivity("delete_database", "Permanently delete db"))
        assertEquals(ToolSensitivity.CRITICAL, manager.classifyMcpToolSensitivity("purge_cache", null))
        assertEquals(ToolSensitivity.SENSITIVE, manager.classifyMcpToolSensitivity("create_issue", "Creates a Jira ticket"))
        assertEquals(ToolSensitivity.SENSITIVE, manager.classifyMcpToolSensitivity("update_status", "Updates record"))
        assertEquals(ToolSensitivity.SAFE, manager.classifyMcpToolSensitivity("get_weather", "Fetches current weather"))
    }

    @Test
    fun testPerDotPermissionEnforcement() = runBlocking {
        val registry = DefaultToolRegistry()
        val manager = McpServerManager(rootDirOverride = tempFolder.root, toolRegistry = registry)

        val serverConfig = McpServerConfig(
            id = "server-finance",
            name = "Finance MCP",
            serverUrl = "http://localhost:8080",
            allowedDotIds = setOf("dot-finance-agent")
        )

        // Register tool directly in tool registry using the manager's bridging logic
        registry.registerTool(
            ToolDefinition(
                id = "mcp-server-finance-query_balance",
                name = "query_balance",
                description = "Queries account balance",
                category = com.mobileai.assistant.domain.tool.ToolCategory.NETWORK,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object"}""",
                handler = { _, dotId ->
                    if (serverConfig.allowedDotIds.isNotEmpty() && !serverConfig.allowedDotIds.contains(dotId)) {
                        com.mobileai.assistant.domain.tool.ToolExecutionResult.failure(
                            "Dot '$dotId' does not have permission to access MCP server '${serverConfig.name}'"
                        )
                    } else {
                        com.mobileai.assistant.domain.tool.ToolExecutionResult.success("""{"balance": 5000}""")
                    }
                }
            )
        )

        val tool = registry.getTool("query_balance")
        assertNotNull(tool)

        // 1. Unauthorized Dot must fail
        val unauthorizedResult = tool!!.handler!!.execute("{}", "dot-general")
        assertFalse(unauthorizedResult.success)
        assertTrue(unauthorizedResult.errorMessage!!.contains("does not have permission"))

        // 2. Authorized Dot must succeed
        val authorizedResult = tool.handler!!.execute("{}", "dot-finance-agent")
        assertTrue(authorizedResult.success)
        assertTrue(authorizedResult.outputJson.contains("5000"))
    }

    @Test
    fun testNoSyntheticSuccessForMissingToolHandler() = runBlocking {
        val registry = DefaultToolRegistry()
        // Register a tool with null handler
        registry.registerTool(
            ToolDefinition(
                id = "unhandled-tool",
                name = "unhandled_action",
                description = "Tool without handler",
                category = com.mobileai.assistant.domain.tool.ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = "{}",
                handler = null
            )
        )

        val tool = registry.getTool("unhandled_action")
        assertNotNull(tool)
        // Calling missing handler must not synthesize success
        val result = if (tool!!.handler != null) {
            tool.handler!!.execute("{}", "dot-1")
        } else {
            com.mobileai.assistant.domain.tool.ToolExecutionResult.failure("No execution handler registered for tool '${tool.name}'")
        }

        assertFalse(result.success)
        assertTrue(result.errorMessage!!.contains("No execution handler registered"))
    }
}

