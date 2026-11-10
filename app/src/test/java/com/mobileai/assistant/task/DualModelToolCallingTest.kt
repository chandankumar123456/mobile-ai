package com.mobileai.assistant.task

import com.mobileai.assistant.data.native_engine.LocalToolCallParser
import com.mobileai.assistant.data.remote.RemoteRequestBuilder
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.tool.ToolCategory
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualModelToolCallingTest {

    // --- 1. Remote OpenAI Tools Serialization Tests ---

    @Test
    fun testOpenAiToolsPayloadSerialization() {
        val tools = listOf(
            ToolDefinition(
                id = "tool-create-doc",
                name = "create_artifact",
                description = "Creates a document in workspace",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                parameterSchemaJson = """{"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"}},"required":["title","content"]}"""
            ),
            ToolDefinition(
                id = "tool-read-doc",
                name = "read_artifact",
                description = "Reads a document",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}"""
            )
        )

        val request = GenerationRequest(
            messages = listOf(ChatMessage(content = "Create a summary document", isUser = true)),
            params = GenerationParams(temperature = 0.5)
        )

        val payload = RemoteRequestBuilder.buildPayload(
            model = "gpt-4o",
            request = request,
            tools = tools
        )

        // Verify tools array is included
        val toolsArray = payload["tools"] as? JsonArray
        assertNotNull("Payload must contain 'tools' array", toolsArray)
        assertEquals(2, toolsArray?.size)

        // Verify tool_choice is auto
        assertEquals("auto", payload["tool_choice"]?.jsonPrimitive?.content)

        // Verify first tool structure
        val tool0 = toolsArray?.get(0)?.jsonObject
        assertEquals("function", tool0?.get("type")?.jsonPrimitive?.content)

        val function0 = tool0?.get("function")?.jsonObject
        assertEquals("create_artifact", function0?.get("name")?.jsonPrimitive?.content)
        assertEquals("Creates a document in workspace", function0?.get("description")?.jsonPrimitive?.content)

        val params0 = function0?.get("parameters")?.jsonObject
        assertEquals("object", params0?.get("type")?.jsonPrimitive?.content)
        assertNotNull(params0?.get("properties")?.jsonObject?.get("title"))
    }

    @Test
    fun testOpenAiToolsPayloadOmittedWhenNoToolsProvided() {
        val request = GenerationRequest(
            messages = listOf(ChatMessage(content = "Hello there", isUser = true))
        )

        val payload = RemoteRequestBuilder.buildPayload(
            model = "gpt-4o",
            request = request,
            tools = emptyList()
        )

        assertNull("tools array should be omitted when tools list is empty", payload["tools"])
        assertNull("tool_choice should be omitted when tools list is empty", payload["tool_choice"])
    }

    @Test
    fun testOpenAiToolsGracefulHandlingOfMalformedSchema() {
        val toolsWithBrokenSchema = listOf(
            ToolDefinition(
                id = "broken-schema",
                name = "broken_tool",
                description = "Tool with invalid JSON schema",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = "Not valid JSON {"
            )
        )

        val toolsArray = RemoteRequestBuilder.formatOpenAiTools(toolsWithBrokenSchema)
        assertEquals(1, toolsArray.size)

        val func = toolsArray[0].jsonObject["function"]?.jsonObject
        val params = func?.get("parameters")?.jsonObject
        assertNotNull(params)
        assertEquals("object", params?.get("type")?.jsonPrimitive?.content)
    }

    // --- 2. Local llama.cpp Tag Parsing Tests ---

    @Test
    fun testLocalToolCallParsingSingleTag() {
        val modelOutput = """
            I will now create the requested document.
            <tool_call>
            {"name": "create_artifact", "arguments": {"title": "Project Plan", "type": "MARKDOWN", "content": "# Overview"}}
            </tool_call>
            Done creating the document.
        """.trimIndent()

        assertTrue(LocalToolCallParser.hasToolCalls(modelOutput))

        val calls = LocalToolCallParser.parseToolCalls(modelOutput)
        assertEquals(1, calls.size)

        val call = calls[0]
        assertEquals("create_artifact", call.name)
        assertEquals("Project Plan", call.arguments["title"]?.jsonPrimitive?.content)
        assertEquals("MARKDOWN", call.arguments["type"]?.jsonPrimitive?.content)
        assertEquals("# Overview", call.arguments["content"]?.jsonPrimitive?.content)

        // Verify text stripping leaves clean prose
        val stripped = LocalToolCallParser.stripToolCalls(modelOutput)
        assertFalse(stripped.contains("<tool_call>"))
        assertFalse(stripped.contains("</tool_call>"))
        assertTrue(stripped.contains("I will now create the requested document."))
        assertTrue(stripped.contains("Done creating the document."))
    }

    @Test
    fun testLocalToolCallParsingMultipleTags() {
        val modelOutput = """
            <tool_call>{"name": "recall_memories", "arguments": {"query": "database"}}</tool_call>
            Let me also inspect the task.
            <tool_call>{"name": "inspect_task", "arguments": {"taskId": "t-1"}}</tool_call>
        """.trimIndent()

        val calls = LocalToolCallParser.parseToolCalls(modelOutput)
        assertEquals(2, calls.size)

        assertEquals("recall_memories", calls[0].name)
        assertEquals("database", calls[0].arguments["query"]?.jsonPrimitive?.content)

        assertEquals("inspect_task", calls[1].name)
        assertEquals("t-1", calls[1].arguments["taskId"]?.jsonPrimitive?.content)
    }

    @Test
    fun testLocalToolCallStreamingDetection() {
        val completeChunk = "Thinking... <tool_call>{\"name\": \"test\"}</tool_call> More prose."
        val stateComplete = LocalToolCallParser.detectStreamingToolCalls(completeChunk)
        assertFalse(stateComplete.hasOpenTag)
        assertEquals(1, stateComplete.completedCalls.size)

        val openChunk = "Thinking... <tool_call>{\"name\": \"in_progress"
        val stateOpen = LocalToolCallParser.detectStreamingToolCalls(openChunk)
        assertTrue(stateOpen.hasOpenTag)
        assertEquals(0, stateOpen.completedCalls.size)
        assertEquals("Thinking...", stateOpen.cleanDisplayText)
    }

    @Test
    fun testLocalToolCallMalformedTagIgnoredGracefully() {
        val brokenPayload = "<tool_call>This is not json at all</tool_call>"
        val calls = LocalToolCallParser.parseToolCalls(brokenPayload)
        assertEquals(0, calls.size)
    }

    @Test
    fun testFormatLocalToolsSystemPrompt() {
        val tools = listOf(
            ToolDefinition(
                id = "t1",
                name = "test_tool",
                description = "A test tool",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object"}"""
            )
        )

        val prompt = LocalToolCallParser.formatLocalToolsSystemPrompt(tools)
        assertTrue(prompt.contains("<tools>"))
        assertTrue(prompt.contains("</tools>"))
        assertTrue(prompt.contains("test_tool"))
        assertTrue(prompt.contains("<tool_call>"))
        assertTrue(prompt.contains("</tool_call>"))
    }
}
