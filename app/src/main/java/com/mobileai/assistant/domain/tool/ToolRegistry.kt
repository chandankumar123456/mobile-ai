package com.mobileai.assistant.domain.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry catalog maintaining tool definitions and per-Dot permission grants.
 */
interface ToolRegistry {
    fun registerTool(tool: ToolDefinition)
    fun getTool(name: String): ToolDefinition?
    fun getAllTools(): List<ToolDefinition>
    fun getToolsForSensitivity(sensitivity: ToolSensitivity): List<ToolDefinition>
    fun hasAlwaysAllowedPermission(dotId: String, toolName: String): Boolean
    fun setAlwaysAllowedPermission(dotId: String, toolName: String, allowed: Boolean)
    fun clearPermissions()
}

/**
 * Standard thread-safe implementation of [ToolRegistry], pre-populated with default agent tools.
 */
class DefaultToolRegistry : ToolRegistry {

    private val tools = ConcurrentHashMap<String, ToolDefinition>()
    private val alwaysAllowedPermissions = ConcurrentHashMap<String, Boolean>()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        registerBuiltInTools()
    }

    override fun registerTool(tool: ToolDefinition) {
        tools[tool.name] = tool
    }

    override fun getTool(name: String): ToolDefinition? = tools[name]

    override fun getAllTools(): List<ToolDefinition> = tools.values.toList()

    override fun getToolsForSensitivity(sensitivity: ToolSensitivity): List<ToolDefinition> =
        tools.values.filter { it.sensitivity == sensitivity }

    override fun hasAlwaysAllowedPermission(dotId: String, toolName: String): Boolean {
        return alwaysAllowedPermissions[permissionKey(dotId, toolName)] == true
    }

    override fun setAlwaysAllowedPermission(dotId: String, toolName: String, allowed: Boolean) {
        val key = permissionKey(dotId, toolName)
        if (allowed) {
            alwaysAllowedPermissions[key] = true
        } else {
            alwaysAllowedPermissions.remove(key)
        }
    }

    override fun clearPermissions() {
        alwaysAllowedPermissions.clear()
    }

    private fun permissionKey(dotId: String, toolName: String): String = "$dotId::$toolName"

    private fun registerBuiltInTools() {
        // SAFE tools: Read-only, inspection, pure calculation
        registerTool(
            ToolDefinition(
                id = "tool-read-artifact",
                name = "read_artifact",
                description = "Reads content of an existing artifact in the active Dot workspace.",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object","properties":{"artifactId":{"type":"string"}},"required":["artifactId"]}""",
                handler = { args, _ ->
                    val artifactId = parseField(args, "artifactId") ?: "default"
                    ToolExecutionResult.success("""{"artifactId":"$artifactId","status":"read","content":"Sample workspace content"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-list-artifacts",
                name = "list_artifacts",
                description = "Returns metadata list of current workspace artifacts.",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object","properties":{}}""",
                handler = { _, _ ->
                    ToolExecutionResult.success("""{"artifacts":[],"count":0}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-recall-memories",
                name = "recall_memories",
                description = "Searches stored facts and context in Dot persistent memory.",
                category = ToolCategory.MEMORY,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""",
                handler = { args, dotId ->
                    val query = parseField(args, "query") ?: ""
                    ToolExecutionResult.success("""{"dotId":"$dotId","query":"$query","results":[]}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-inspect-task",
                name = "inspect_task",
                description = "Queries status and progress logs of the current task.",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object","properties":{"taskId":{"type":"string"}},"required":["taskId"]}""",
                handler = { args, _ ->
                    val taskId = parseField(args, "taskId") ?: "current"
                    ToolExecutionResult.success("""{"taskId":"$taskId","status":"RUNNING"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-format-content",
                name = "format_content",
                description = "Performs text formatting, calculations, or data transformations.",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object","properties":{"content":{"type":"string"},"format":{"type":"string"}},"required":["content"]}""",
                handler = { args, _ ->
                    val content = parseField(args, "content") ?: args
                    ToolExecutionResult.success("""{"formatted":"$content"}""")
                }
            )
        )

        // SENSITIVE tools: Workspace mutation, storage write, network access
        registerTool(
            ToolDefinition(
                id = "tool-create-artifact",
                name = "create_artifact",
                description = "Generates a new code or markdown document in the Dot workspace.",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"title":{"type":"string"},"type":{"type":"string"},"content":{"type":"string"},"language":{"type":"string"}},"required":["title","type","content"]}""",
                handler = { args, _ ->
                    val title = parseField(args, "title") ?: "Document"
                    ToolExecutionResult.success("""{"status":"created","title":"$title"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-update-artifact",
                name = "update_artifact",
                description = "Updates an existing workspace artifact with a new version.",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"artifactId":{"type":"string"},"content":{"type":"string"},"changeSummary":{"type":"string"}},"required":["artifactId","content"]}""",
                handler = { args, _ ->
                    val artifactId = parseField(args, "artifactId") ?: ""
                    ToolExecutionResult.success("""{"status":"updated","artifactId":"$artifactId"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-export-document",
                name = "export_document",
                description = "Exports a workspace document to external device storage or share intent.",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"artifactId":{"type":"string"},"format":{"type":"string"}},"required":["artifactId"]}""",
                handler = { args, _ ->
                    val artifactId = parseField(args, "artifactId") ?: ""
                    ToolExecutionResult.success("""{"status":"exported","artifactId":"$artifactId"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-remember-fact",
                name = "remember_fact",
                description = "Writes a new long-term fact to Dot persistent memory.",
                category = ToolCategory.MEMORY,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"fact":{"type":"string"},"category":{"type":"string"}},"required":["fact"]}""",
                handler = { args, dotId ->
                    val fact = parseField(args, "fact") ?: ""
                    ToolExecutionResult.success("""{"status":"remembered","dotId":"$dotId","fact":"$fact"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-forget-memory",
                name = "forget_memory",
                description = "Removes a specific memory entry from Dot persistent memory.",
                category = ToolCategory.MEMORY,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"memoryId":{"type":"string"}},"required":["memoryId"]}""",
                handler = { args, dotId ->
                    val memoryId = parseField(args, "memoryId") ?: ""
                    ToolExecutionResult.success("""{"status":"forgotten","dotId":"$dotId","memoryId":"$memoryId"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-save-file-to-storage",
                name = "save_file_to_storage",
                description = "Writes an artifact or data payload to device external storage.",
                category = ToolCategory.STORAGE,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"fileName":{"type":"string"},"content":{"type":"string"}},"required":["fileName","content"]}""",
                handler = { args, _ ->
                    val fileName = parseField(args, "fileName") ?: "output.txt"
                    ToolExecutionResult.success("""{"status":"saved","fileName":"$fileName"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-fetch-url-content",
                name = "fetch_url_content",
                description = "Downloads and summarizes content from a specified external URL.",
                category = ToolCategory.NETWORK,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""",
                handler = { args, _ ->
                    val url = parseField(args, "url") ?: ""
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        ToolExecutionResult.failure("Invalid URL protocol: $url")
                    } else {
                        ToolExecutionResult.success("""{"status":"fetched","url":"$url"}""")
                    }
                }
            )
        )

        // CRITICAL tools: Destructive irreversible actions
        registerTool(
            ToolDefinition(
                id = "tool-delete-artifact",
                name = "delete_artifact",
                description = "Permanently removes an artifact and its entire version history.",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.CRITICAL,
                parameterSchemaJson = """{"type":"object","properties":{"artifactId":{"type":"string"}},"required":["artifactId"]}""",
                handler = { args, _ ->
                    val artifactId = parseField(args, "artifactId") ?: ""
                    ToolExecutionResult.success("""{"status":"deleted","artifactId":"$artifactId"}""")
                }
            )
        )
        registerTool(
            ToolDefinition(
                id = "tool-clear-all-memories",
                name = "clear_all_memories",
                description = "Permanently clears all learned memories and facts for this Dot.",
                category = ToolCategory.MEMORY,
                sensitivity = ToolSensitivity.CRITICAL,
                parameterSchemaJson = """{"type":"object","properties":{"confirm":{"type":"boolean"}},"required":["confirm"]}""",
                handler = { args, dotId ->
                    val confirmed = parseField(args, "confirm")?.toBooleanStrictOrNull() ?: false
                    if (confirmed) {
                        ToolExecutionResult.success("""{"status":"cleared","dotId":"$dotId"}""")
                    } else {
                        ToolExecutionResult.failure("Operation requires explicit 'confirm: true' confirmation")
                    }
                }
            )
        )
    }

    private fun parseField(jsonStr: String, fieldName: String): String? {
        return try {
            json.parseToJsonElement(jsonStr).jsonObject[fieldName]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}
