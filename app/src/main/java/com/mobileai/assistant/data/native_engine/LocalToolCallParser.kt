package com.mobileai.assistant.data.native_engine

import com.mobileai.assistant.domain.tool.ToolDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Parsed tool invocation emitted by a local model via `<tool_call>` tags.
 */
@Serializable
data class ParsedToolCall(
    val name: String,
    val argumentsJson: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
    val rawTag: String = ""
)

/**
 * Real-time detection state for streaming text containing tool tags.
 */
data class StreamingToolCallState(
    val hasOpenTag: Boolean,
    val completedCalls: List<ParsedToolCall>,
    val cleanDisplayText: String
)

/**
 * Parser and prompt formatter for local models (llama.cpp) supporting structured tool calling
 * via `<tool_call>{"name": ..., "arguments": ...}</tool_call>` tags.
 */
object LocalToolCallParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val TOOL_CALL_REGEX = Regex("<tool_call>\\s*([\\s\\S]*?)\\s*</tool_call>", RegexOption.MULTILINE)
    private val OPEN_TAG_REGEX = Regex("<tool_call>(?:(?!</tool_call>)[\\s\\S])*$")
    private val VALID_TOOL_NAME_REGEX = Regex("^[a-zA-Z0-9_-]+$")

    /**
     * Checks if the given text contains any complete `<tool_call>` tags.
     */
    fun hasToolCalls(text: String): Boolean = TOOL_CALL_REGEX.containsMatchIn(text)

    /**
     * Extracts and validates all complete tool call tags from the generated text.
     */
    fun parseToolCalls(text: String): List<ParsedToolCall> {
        val matches = TOOL_CALL_REGEX.findAll(text)
        val results = mutableListOf<ParsedToolCall>()

        for (match in matches) {
            val rawTag = match.value
            val innerPayload = match.groupValues[1].trim()
            if (innerPayload.isBlank()) continue

            try {
                val element = json.parseToJsonElement(innerPayload)
                if (element is JsonObject) {
                    val namePrimitive = element["name"] as? JsonPrimitive
                    if (namePrimitive != null && namePrimitive.isString) {
                        val name = namePrimitive.content.trim()
                        if (name.isNotEmpty() && VALID_TOOL_NAME_REGEX.matches(name)) {
                            val argsElement = element["arguments"]
                            val (argsJson, argsObj) = when (argsElement) {
                                is JsonObject -> Pair(argsElement.toString(), argsElement)
                                null -> Pair("{}", JsonObject(emptyMap()))
                                is JsonPrimitive -> {
                                    if (argsElement.isString) {
                                        val primitiveStr = argsElement.content
                                        val parsed = try {
                                            json.parseToJsonElement(primitiveStr) as? JsonObject
                                        } catch (_: Exception) {
                                            null
                                        }
                                        Pair(primitiveStr, parsed ?: JsonObject(emptyMap()))
                                    } else {
                                        Pair(argsElement.toString(), JsonObject(emptyMap()))
                                    }
                                }
                                else -> Pair(argsElement.toString(), JsonObject(emptyMap()))
                            }

                            results.add(
                                ParsedToolCall(
                                    name = name,
                                    argumentsJson = argsJson,
                                    arguments = argsObj,
                                    rawTag = rawTag
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Malformed JSON inside tag ignored safely without crashing
            }
        }

        return results
    }

    /**
     * Strips all `<tool_call>...</tool_call>` blocks and dangling open `<tool_call>` tokens
     * from model output so clean prose can be shown to the user.
     */
    fun stripToolCalls(text: String): String {
        var clean = TOOL_CALL_REGEX.replace(text, "")
        clean = OPEN_TAG_REGEX.replace(clean, "")
        return clean.trim()
    }

    /**
     * Analyzes streaming token text to detect in-progress `<tool_call>` emissions and clean prose.
     */
    fun detectStreamingToolCalls(text: String): StreamingToolCallState {
        val completedCalls = parseToolCalls(text)
        val hasOpen = OPEN_TAG_REGEX.containsMatchIn(text)
        val clean = stripToolCalls(text)
        return StreamingToolCallState(
            hasOpenTag = hasOpen,
            completedCalls = completedCalls,
            cleanDisplayText = clean
        )
    }

    /**
     * Formats the tool catalog into system prompt instructions instructing local models
     * how to query tools using structured `<tool_call>` tags.
     */
    fun formatLocalToolsSystemPrompt(tools: List<ToolDefinition>): String {
        if (tools.isEmpty()) return ""

        val toolsJsonArray = buildJsonArray {
            tools.forEach { tool ->
                val paramsObj = try {
                    if (tool.parameterSchemaJson.isNotBlank()) {
                        val parsed = json.parseToJsonElement(tool.parameterSchemaJson)
                        if (parsed is JsonObject) parsed else buildJsonObject { put("type", "object") }
                    } else {
                        buildJsonObject { put("type", "object") }
                    }
                } catch (_: Exception) {
                    buildJsonObject { put("type", "object") }
                }

                add(
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("sensitivity", tool.sensitivity.name)
                        put("parameters", paramsObj)
                    }
                )
            }
        }

        val formattedJson = json.encodeToString(toolsJsonArray)

        return buildString {
            appendLine("You have access to the following tools:")
            appendLine("<tools>")
            appendLine(formattedJson)
            appendLine("</tools>")
            appendLine()
            appendLine("To call a tool, you MUST emit a tool call in the following XML tag format:")
            appendLine("<tool_call>")
            appendLine("{\"name\": \"<tool_name>\", \"arguments\": {<args>}}")
            appendLine("</tool_call>")
            appendLine("You may include reasoning before or after the tool call.")
        }
    }
}
