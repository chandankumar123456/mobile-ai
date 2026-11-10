package com.mobileai.assistant.task

import com.mobileai.assistant.data.native_engine.LocalToolCallParser
import com.mobileai.assistant.data.remote.RemoteRequestBuilder
import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.task.DotTaskStep
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus
import com.mobileai.assistant.domain.tool.ApprovalStatus
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolApprovalDecision
import com.mobileai.assistant.domain.tool.ToolCategory
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolExecutionResult
import com.mobileai.assistant.domain.tool.ToolSensitivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Empirical challenger stress test suite for Milestone 3:
 * 1. Critical tool approval gating invariant & Dot permission isolation.
 * 2. Tag parser fuzzing, malformed tags, unclosed JSON, and streaming edge cases.
 * 3. OpenAI tool schema serialization and degradation fallbacks.
 */
class ChallengerToolAndParserStressTest {

    private lateinit var tempDir: File
    private lateinit var toolRegistry: DefaultToolRegistry
    private lateinit var auditLogger: ToolAuditLoggerImpl
    private lateinit var taskManager: TaskManagerImpl

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("challenger_m3_stress").toFile()
        toolRegistry = DefaultToolRegistry()
        auditLogger = ToolAuditLoggerImpl(tempDir)
        taskManager = TaskManagerImpl(
            rootDirOverride = tempDir,
            toolRegistry = toolRegistry,
            auditLogger = auditLogger
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // SECTION 1: CRITICAL & SENSITIVE TOOL GATING ADVERSARIAL STRESS TESTS
    // =========================================================================

    @Test
    fun testCriticalToolNeverBypassesApprovalEvenWithAlwaysAllowedDecisionAttempt() = runBlocking {
        var executionCount = 0
        toolRegistry.registerTool(
            ToolDefinition(
                id = "crit-wipe",
                name = "clear_all_memories",
                description = "Wipes all persistent Dot memories",
                category = ToolCategory.MEMORY,
                sensitivity = ToolSensitivity.CRITICAL,
                handler = { _, _ ->
                    executionCount++
                    ToolExecutionResult.success("""{"cleared":true}""")
                }
            )
        )

        val task1 = taskManager.createTask(
            title = "Wipe Memories 1",
            goalDescription = "First memory wipe attempt",
            dotId = "dot-victim",
            conversationId = "conv-1",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Wipe", description = "", toolName = "clear_all_memories")
            )
        )

        // Step 1 halts for approval
        val result1 = taskManager.executeStep(task1.id)
        assertEquals(StepStatus.AWAITING_APPROVAL, result1.status)
        val pending1 = taskManager.getPendingApprovalForTask(task1.id)
        assertNotNull("Must generate pending approval for CRITICAL tool", pending1)

        // User attempts ALWAYS_ALLOWED on a CRITICAL tool
        val resolved1 = taskManager.handleApprovalDecision(
            requestId = pending1!!.id,
            decision = ToolApprovalDecision.ALWAYS_ALLOWED
        )
        assertNotNull(resolved1)
        assertEquals(1, executionCount)

        // Invariant check: CRITICAL tool must NOT be granted persistent bypass in registry
        assertFalse(
            "CRITICAL tool must NEVER be granted always-allowed permission in registry",
            toolRegistry.hasAlwaysAllowedPermission("dot-victim", "clear_all_memories")
        )

        // Now run a second task with the same Dot and same CRITICAL tool
        val task2 = taskManager.createTask(
            title = "Wipe Memories 2",
            goalDescription = "Second memory wipe attempt",
            dotId = "dot-victim",
            conversationId = "conv-2",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Wipe Again", description = "", toolName = "clear_all_memories")
            )
        )

        val result2 = taskManager.executeStep(task2.id)
        // MUST STILL HALT FOR APPROVAL! Never bypassed!
        assertEquals("Second CRITICAL tool execution MUST STILL halt for approval", StepStatus.AWAITING_APPROVAL, result2.status)
        val pending2 = taskManager.getPendingApprovalForTask(task2.id)
        assertNotNull("Second invocation must also generate a pending approval", pending2)
        assertEquals(1, executionCount) // Has NOT executed second time yet
    }

    @Test
    fun testCriticalToolNeverBypassesEvenIfRegistryExplicitlyPreSeeded() = runBlocking {
        var executed = false
        toolRegistry.registerTool(
            ToolDefinition(
                id = "crit-del",
                name = "delete_artifact",
                description = "Deletes an artifact",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.CRITICAL,
                handler = { _, _ ->
                    executed = true
                    ToolExecutionResult.success("""{"deleted":true}""")
                }
            )
        )

        // Adversarially pre-seed registry with always-allowed permission
        toolRegistry.setAlwaysAllowedPermission("dot-hacker", "delete_artifact", true)
        assertTrue(toolRegistry.hasAlwaysAllowedPermission("dot-hacker", "delete_artifact"))

        val task = taskManager.createTask(
            title = "Rogue Delete",
            goalDescription = "Try to exploit pre-seeded registry",
            dotId = "dot-hacker",
            conversationId = "conv-hacker",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Delete", description = "", toolName = "delete_artifact")
            )
        )

        val result = taskManager.executeStep(task.id)

        // Even with pre-seeded permission, CRITICAL sensitivity MUST strictly enforce approval
        assertEquals(StepStatus.AWAITING_APPROVAL, result.status)
        assertFalse("Tool handler must NOT have executed", executed)
        assertNotNull(taskManager.getPendingApprovalForTask(task.id))
    }

    @Test
    fun testMultipleConsecutiveCriticalStepsEachRequireIndependentApproval() = runBlocking {
        val executedTools = mutableListOf<String>()
        toolRegistry.registerTool(
            ToolDefinition(
                id = "crit-1",
                name = "crit_step_1",
                description = "Critical step 1",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.CRITICAL,
                handler = { _, _ -> executedTools.add("crit_step_1"); ToolExecutionResult.success("{}") }
            )
        )
        toolRegistry.registerTool(
            ToolDefinition(
                id = "crit-2",
                name = "crit_step_2",
                description = "Critical step 2",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.CRITICAL,
                handler = { _, _ -> executedTools.add("crit_step_2"); ToolExecutionResult.success("{}") }
            )
        )

        val task = taskManager.createTask(
            title = "Multi-Critical Task",
            goalDescription = "Consecutive critical actions",
            dotId = "dot-admin",
            conversationId = "conv-1",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Step 1", description = "", toolName = "crit_step_1"),
                DotTaskStep(taskId = "", stepIndex = 1, title = "Step 2", description = "", toolName = "crit_step_2")
            )
        )

        // Step 1 halts
        val res1 = taskManager.executeStep(task.id)
        assertEquals(StepStatus.AWAITING_APPROVAL, res1.status)
        assertEquals(0, executedTools.size)

        val pend1 = taskManager.getPendingApprovalForTask(task.id)
        assertNotNull(pend1)
        assertEquals("crit_step_1", pend1?.toolName)

        // Approve step 1
        taskManager.handleApprovalDecision(pend1!!.id, ToolApprovalDecision.APPROVED)
        assertEquals(listOf("crit_step_1"), executedTools)

        // Step 2 halts independently
        val res2 = taskManager.executeStep(task.id)
        assertEquals(StepStatus.AWAITING_APPROVAL, res2.status)
        assertEquals(1, executedTools.size) // Step 2 has NOT executed yet

        val pend2 = taskManager.getPendingApprovalForTask(task.id)
        assertNotNull(pend2)
        assertEquals("crit_step_2", pend2?.toolName)

        // Approve step 2
        taskManager.handleApprovalDecision(pend2!!.id, ToolApprovalDecision.APPROVED)
        assertEquals(listOf("crit_step_1", "crit_step_2"), executedTools)

        val finalTask = taskManager.getTaskById(task.id)
        assertEquals(TaskStatus.COMPLETED, finalTask?.status)
    }

    @Test
    fun testDotIsolationInAlwaysAllowedPermissions() = runBlocking {
        var exportCalls = 0
        toolRegistry.registerTool(
            ToolDefinition(
                id = "sens-export",
                name = "export_document",
                description = "Exports document",
                category = ToolCategory.WORKSPACE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ -> exportCalls++; ToolExecutionResult.success("{}") }
            )
        )

        // Dot A gets ALWAYS_ALLOWED
        val taskA = taskManager.createTask(
            title = "Dot A Task",
            goalDescription = "Dot A export",
            dotId = "dot-alpha",
            conversationId = "conv-a",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Export", description = "", toolName = "export_document")
            )
        )
        taskManager.executeStep(taskA.id)
        val pendA = taskManager.getPendingApprovalForTask(taskA.id)
        taskManager.handleApprovalDecision(pendA!!.id, ToolApprovalDecision.ALWAYS_ALLOWED)
        assertEquals(1, exportCalls)

        // Verify Dot A has permission
        assertTrue(toolRegistry.hasAlwaysAllowedPermission("dot-alpha", "export_document"))

        // Verify Dot B DOES NOT have permission
        assertFalse(toolRegistry.hasAlwaysAllowedPermission("dot-beta", "export_document"))

        // Dot B attempts same tool -> MUST HALT awaiting approval
        val taskB = taskManager.createTask(
            title = "Dot B Task",
            goalDescription = "Dot B export",
            dotId = "dot-beta",
            conversationId = "conv-b",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Export", description = "", toolName = "export_document")
            )
        )
        val resB = taskManager.executeStep(taskB.id)
        assertEquals("Dot B must NOT inherit Dot A's always-allowed permission", StepStatus.AWAITING_APPROVAL, resB.status)
        assertEquals(1, exportCalls) // Dot B tool has NOT run
    }

    @Test
    fun testSensitiveToolRejectionAbortsTaskWithoutExecutingHandler() = runBlocking {
        var handlerCalled = false
        toolRegistry.registerTool(
            ToolDefinition(
                id = "sens-save",
                name = "save_file_to_storage",
                description = "Writes file to storage",
                category = ToolCategory.STORAGE,
                sensitivity = ToolSensitivity.SENSITIVE,
                handler = { _, _ ->
                    handlerCalled = true
                    ToolExecutionResult.success("{}")
                }
            )
        )

        val task = taskManager.createTask(
            title = "Rejected Task",
            goalDescription = "User will reject this",
            dotId = "dot-reject",
            conversationId = "conv-rej",
            initialSteps = listOf(
                DotTaskStep(taskId = "", stepIndex = 0, title = "Write", description = "", toolName = "save_file_to_storage")
            )
        )

        taskManager.executeStep(task.id)
        val pending = taskManager.getPendingApprovalForTask(task.id)
        assertNotNull(pending)

        val updatedTask = taskManager.handleApprovalDecision(
            requestId = pending!!.id,
            decision = ToolApprovalDecision.REJECTED,
            rejectionReason = "User denied file write"
        )

        assertNotNull(updatedTask)
        assertFalse("Tool handler must NEVER be called after rejection", handlerCalled)
        assertEquals(TaskStatus.PAUSED, updatedTask?.status)
        assertEquals(StepStatus.FAILED, updatedTask?.steps?.get(0)?.status)
        assertEquals("User denied file write", updatedTask?.steps?.get(0)?.errorMessage)

        // Audit log validation
        val logs = auditLogger.getAuditLogs().first()
        val entry = logs.firstOrNull { it.taskId == task.id }
        assertNotNull(entry)
        assertEquals(ApprovalStatus.REJECTED, entry?.approvalStatus)
        assertEquals("REJECTED", entry?.executionStatus)
        assertEquals("User denied file write", entry?.errorMessage)
    }

    // =========================================================================
    // SECTION 2: LOCAL TOOL CALL PARSER FUZZING & MALFORMED INPUTS
    // =========================================================================

    @Test
    fun testTagParserFuzzingIncompleteTags() {
        val fuzzedIncompleteInputs = listOf(
            "<tool_call>",
            "<tool_call>{\"name\": \"foo\"}",
            "<tool_call>{\"name\": \"foo\", \"arguments\": {",
            "<tool",
            "<tool_call",
            "<tool_call ",
            "</tool_call>",
            "  </tool_call>  ",
            "<tool_call></tool_call>",
            "<tool_call>   </tool_call>",
            "<tool_call>\n\r\t</tool_call>",
            "Here is <tool_call> without close",
            "Here is <tool_call\nname=foo> malformed tag</tool_call>"
        )

        for (input in fuzzedIncompleteInputs) {
            val calls = LocalToolCallParser.parseToolCalls(input)
            assertEquals("Incomplete/empty tag input '$input' must yield 0 parsed calls", 0, calls.size)
        }
    }

    @Test
    fun testTagParserFuzzingUnclosedAndMalformedJson() {
        val malformedJsonInputs = listOf(
            "<tool_call>This is plain text inside tag</tool_call>",
            "<tool_call>{ unclosed object </tool_call>",
            "<tool_call>{\"name\": </tool_call>",
            "<tool_call>{\"name\": \"test\", \"arguments\": </tool_call>",
            "<tool_call>[1, 2, 3]</tool_call>",
            "<tool_call>\"just a string\"</tool_call>",
            "<tool_call>12345</tool_call>",
            "<tool_call>true</tool_call>",
            "<tool_call>false</tool_call>",
            "<tool_call>null</tool_call>",
            "<tool_call>{\"arguments\": {\"a\": 1}}</tool_call>", // missing name
            "<tool_call>{\"name\": \"\"}</tool_call>",           // blank name
            "<tool_call>{\"name\": \"   \"}</tool_call>",         // whitespace name
            "<tool_call>{\"name\": 123}</tool_call>",            // numeric name primitive
            "<tool_call>{\"name\": 3.14}</tool_call>",           // float name primitive
            "<tool_call>{\"name\": true}</tool_call>",           // boolean name primitive
            "<tool_call>{\"name\": null}</tool_call>",           // null name primitive
            "<tool_call>{\"name\": [\"tool\"]}</tool_call>",     // array name
            "<tool_call>{\"name\": {\"fn\": \"tool\"}}</tool_call>" // object name
        )

        for (input in malformedJsonInputs) {
            val calls = LocalToolCallParser.parseToolCalls(input)
            assertEquals("Malformed JSON inside tag '$input' must yield 0 parsed calls without crashing", 0, calls.size)
        }
    }

    @Test
    fun testTagParserPrimitiveTypeCoercionBehavior() {
        // Enforces strict string validation: numeric, boolean, and null primitives must yield 0 calls
        val numericNameInput = "<tool_call>{\"name\": 123}</tool_call>"
        val calls = LocalToolCallParser.parseToolCalls(numericNameInput)
        assertTrue("Numeric name primitive must yield 0 parsed calls", calls.isEmpty())

        val booleanNameInput = "<tool_call>{\"name\": true}</tool_call>"
        val boolCalls = LocalToolCallParser.parseToolCalls(booleanNameInput)
        assertTrue("Boolean name primitive must yield 0 parsed calls", boolCalls.isEmpty())

        val nullNameInput = "<tool_call>{\"name\": null}</tool_call>"
        val nullCalls = LocalToolCallParser.parseToolCalls(nullNameInput)
        assertTrue("Null name primitive must yield 0 parsed calls", nullCalls.isEmpty())
    }

    @Test
    fun testTagParserValidAndInvalidIdentifierPatterns() {
        // Valid tool names matching ^[a-zA-Z0-9_-]+$
        val validNames = listOf(
            "read_artifact",
            "list-artifacts",
            "recall_memories_v2",
            "inspectTask123",
            "TOOL-42_OK"
        )
        for (name in validNames) {
            val input = "<tool_call>{\"name\": \"$name\"}</tool_call>"
            val calls = LocalToolCallParser.parseToolCalls(input)
            assertEquals("Valid tool name '$name' must yield 1 parsed call", 1, calls.size)
            assertEquals(name, calls[0].name)
        }

        // Invalid tool names failing ^[a-zA-Z0-9_-]+$
        val invalidNames = listOf(
            "tool name with spaces",
            "tool.with.dots",
            "tool:with:colons",
            "tool/with/slashes",
            "tool@special#chars$",
            "tool\nwith\nnewlines",
            "   ",
            ""
        )
        for (name in invalidNames) {
            val input = "<tool_call>{\"name\": \"$name\"}</tool_call>"
            val calls = LocalToolCallParser.parseToolCalls(input)
            assertEquals("Invalid tool name '$name' must yield 0 parsed calls", 0, calls.size)
        }
    }

    @Test
    fun testTagParserWhitespaceVariationsAndAdjacentTags() {
        // Extreme whitespace inside and around tags
        val whitespaceInput = """
            
            Before text
               <tool_call>
                  
                  {
                     "name"   :   "format_content"   ,
                     "arguments"   :   {
                         "style"   :   "markdown"   
                     }
                  }
                  
               </tool_call>
            After text
        """.trimIndent()

        val calls = LocalToolCallParser.parseToolCalls(whitespaceInput)
        assertEquals(1, calls.size)
        assertEquals("format_content", calls[0].name)
        assertEquals("markdown", calls[0].arguments["style"]?.jsonPrimitive?.content)

        // Multiple directly adjacent tags with zero separating whitespace
        val adjacentInput = "<tool_call>{\"name\":\"t1\"}</tool_call><tool_call>{\"name\":\"t2\"}</tool_call><tool_call>{\"name\":\"t3\"}</tool_call>"
        val adjacentCalls = LocalToolCallParser.parseToolCalls(adjacentInput)
        assertEquals(3, adjacentCalls.size)
        assertEquals("t1", adjacentCalls[0].name)
        assertEquals("t2", adjacentCalls[1].name)
        assertEquals("t3", adjacentCalls[2].name)
    }

    @Test
    fun testTagParserMassiveStressBatch() {
        // Stress test with 50 consecutive valid tool calls mixed with noise
        val sb = StringBuilder()
        for (i in 1..50) {
            sb.append("Prose $i <tool_call>{\"name\":\"tool_$i\",\"arguments\":{\"idx\":$i}}</tool_call>\n")
        }

        val text = sb.toString()
        assertTrue(LocalToolCallParser.hasToolCalls(text))

        val calls = LocalToolCallParser.parseToolCalls(text)
        assertEquals(50, calls.size)
        for (i in 1..50) {
            assertEquals("tool_$i", calls[i - 1].name)
            assertEquals(i.toString(), calls[i - 1].arguments["idx"]?.jsonPrimitive?.content)
        }

        val stripped = LocalToolCallParser.stripToolCalls(text)
        assertFalse(stripped.contains("<tool_call>"))
        assertFalse(stripped.contains("</tool_call>"))
        assertTrue(stripped.contains("Prose 1"))
        assertTrue(stripped.contains("Prose 50"))
    }

    @Test
    fun testStreamingDetectionStateTransitions() {
        // Simulate progressive token stream
        val tokens = listOf(
            "I will ",
            "now look up ",
            "the data.\n",
            "<tool_call>",
            "\n{\"name\": ",
            "\"recall_memories\", ",
            "\"arguments\": {\"query\": \"project\"}}",
            "\n</tool_call>",
            "\nHope this helps!"
        )

        var accumulated = ""
        var wasOpenDetected = false

        for (token in tokens) {
            accumulated += token
            val state = LocalToolCallParser.detectStreamingToolCalls(accumulated)

            if (state.hasOpenTag) {
                wasOpenDetected = true
                // While open tag is in progress, the open tag must NOT leak into clean display text
                assertFalse("Clean display text must not contain open tag prefix", state.cleanDisplayText.contains("<tool_call>"))
            }
        }

        assertTrue("Streaming detector must detect in-progress open tag", wasOpenDetected)

        // Final state verification
        val finalState = LocalToolCallParser.detectStreamingToolCalls(accumulated)
        assertFalse("Final state should have closed tag", finalState.hasOpenTag)
        assertEquals(1, finalState.completedCalls.size)
        assertEquals("recall_memories", finalState.completedCalls[0].name)
        assertTrue(finalState.cleanDisplayText.contains("I will now look up the data."))
        assertTrue(finalState.cleanDisplayText.contains("Hope this helps!"))
        assertFalse(finalState.cleanDisplayText.contains("<tool_call>"))
    }

    // =========================================================================
    // SECTION 3: OPENAI SCHEMA SERIALIZATION & FALLBACK STRESS TESTS
    // =========================================================================

    @Test
    fun testOpenAiSchemaWithMalformedAndNonObjectParameterSchemas() {
        val adversarialTools = listOf(
            ToolDefinition(
                id = "adv-1",
                name = "empty_schema_tool",
                description = "Tool with empty schema",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = ""
            ),
            ToolDefinition(
                id = "adv-2",
                name = "blank_schema_tool",
                description = "Tool with blank whitespace schema",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = "   \n\t   "
            ),
            ToolDefinition(
                id = "adv-3",
                name = "broken_syntax_tool",
                description = "Tool with broken JSON syntax",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = "{\"type\": \"object\", broken..."
            ),
            ToolDefinition(
                id = "adv-4",
                name = "array_schema_tool",
                description = "Tool where schema is a JSON array instead of object",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = "[1, 2, 3]"
            ),
            ToolDefinition(
                id = "adv-5",
                name = "primitive_schema_tool",
                description = "Tool where schema is a JSON primitive",
                category = ToolCategory.SYSTEM,
                sensitivity = ToolSensitivity.SAFE,
                parameterSchemaJson = "\"string_schema\""
            )
        )

        // Must never throw an exception
        val formatted = RemoteRequestBuilder.formatOpenAiTools(adversarialTools)
        assertEquals(5, formatted.size)

        for (i in 0 until 5) {
            val toolObj = formatted[i].jsonObject
            assertEquals("function", toolObj["type"]?.jsonPrimitive?.content)
            val func = toolObj["function"]?.jsonObject
            assertNotNull(func)
            assertNotNull("Parameters element must never be null", func?.get("parameters"))
        }

        // Verify buildPayload survives with these adversarial tools
        val request = GenerationRequest(messages = listOf(ChatMessage(content = "Test", isUser = true)))
        val payload = RemoteRequestBuilder.buildPayload(
            model = "gpt-4o",
            request = request,
            tools = adversarialTools
        )

        val toolsArray = payload["tools"] as? JsonArray
        assertNotNull(toolsArray)
        assertEquals(5, toolsArray?.size)
        assertEquals("auto", payload["tool_choice"]?.jsonPrimitive?.content)
    }

    @Test
    fun testOpenAiSchemaDeeplyNestedAndComplexSchemaFidelity() {
        val complexSchema = """
            {
              "type": "object",
              "properties": {
                "title": {"type": "string", "description": "Doc title"},
                "tags": {
                  "type": "array",
                  "items": {"type": "string"}
                },
                "options": {
                  "type": "object",
                  "properties": {
                    "publish": {"type": "boolean"},
                    "format": {"type": "string", "enum": ["PDF", "HTML", "MARKDOWN"]}
                  },
                  "required": ["format"]
                }
              },
              "required": ["title", "options"]
            }
        """.trimIndent()

        val tool = ToolDefinition(
            id = "complex-tool",
            name = "create_complex_doc",
            description = "Creates complex doc",
            category = ToolCategory.WORKSPACE,
            sensitivity = ToolSensitivity.SENSITIVE,
            parameterSchemaJson = complexSchema
        )

        val formatted = RemoteRequestBuilder.formatOpenAiTools(listOf(tool))
        assertEquals(1, formatted.size)

        val func = formatted[0].jsonObject["function"]?.jsonObject
        assertEquals("create_complex_doc", func?.get("name")?.jsonPrimitive?.content)

        val params = func?.get("parameters")?.jsonObject
        assertNotNull(params)
        assertEquals("object", params?.get("type")?.jsonPrimitive?.content)
        val props = params?.get("properties")?.jsonObject
        assertNotNull(props?.get("title"))
        assertNotNull(props?.get("tags"))
        assertNotNull(props?.get("options"))
    }

    @Test
    fun testOpenAiSchemaLargeToolCatalog() {
        val largeCatalog = (1..30).map { i ->
            ToolDefinition(
                id = "tool-$i",
                name = "catalog_tool_$i",
                description = "Tool description number $i",
                category = ToolCategory.SYSTEM,
                sensitivity = if (i % 3 == 0) ToolSensitivity.CRITICAL else if (i % 2 == 0) ToolSensitivity.SENSITIVE else ToolSensitivity.SAFE,
                parameterSchemaJson = """{"type":"object","properties":{"p$i":{"type":"string"}}}"""
            )
        }

        val request = GenerationRequest(messages = listOf(ChatMessage(content = "Execute tools", isUser = true)))
        val payload = RemoteRequestBuilder.buildPayload(
            model = "gpt-4o",
            request = request,
            tools = largeCatalog
        )

        val toolsArray = payload["tools"] as? JsonArray
        assertNotNull(toolsArray)
        assertEquals(30, toolsArray?.size)

        // Verify prompt formatting for local models also handles 30 tools cleanly
        val localPrompt = LocalToolCallParser.formatLocalToolsSystemPrompt(largeCatalog)
        assertTrue(localPrompt.contains("catalog_tool_1"))
        assertTrue(localPrompt.contains("catalog_tool_30"))
        assertTrue(localPrompt.contains("CRITICAL"))
        assertTrue(localPrompt.contains("SENSITIVE"))
        assertTrue(localPrompt.contains("SAFE"))
    }
}
