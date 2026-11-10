package com.mobileai.assistant.task

import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.task.AgentPlanner
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolCategory
import com.mobileai.assistant.domain.tool.ToolDefinition
import com.mobileai.assistant.domain.tool.ToolSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentPlannerTest {

    private lateinit var registry: DefaultToolRegistry
    private lateinit var planner: AgentPlanner

    @Before
    fun setUp() {
        registry = DefaultToolRegistry()
        planner = AgentPlanner(registry)
    }

    @Test
    fun testPlanningPromptContainsAvailableTools() {
        val dot = DotProfile.STARTER_PROFILES.first()
        val prompt = planner.buildPlanningPrompt("Organize project files", dot)

        assertTrue(prompt.contains("format_content"))
        assertTrue(prompt.contains("inspect_task"))
        assertTrue(prompt.contains("read_artifact"))
        assertTrue(prompt.contains("Organize project files"))
    }

    @Test
    fun testParseValidModelPlanResponse() {
        val modelResponse = """
```json
{
  "title": "Document Analysis",
  "summary": "Reads and formats artifact",
  "steps": [
    {
      "title": "Read Artifact",
      "description": "Fetch content from workspace",
      "toolName": "read_artifact",
      "arguments": { "artifactId": "art-101" }
    },
    {
      "title": "Format Summary",
      "description": "Format summary text",
      "toolName": "format_content",
      "arguments": { "content": "Sample text", "format": "markdown" }
    }
  ]
}
```
        """.trimIndent()

        val steps = planner.parsePlanResponse(modelResponse, "task-1")
        assertEquals(2, steps.size)
        assertEquals("read_artifact", steps[0].toolName)
        assertEquals("format_content", steps[1].toolName)
        assertTrue(steps[0].toolInputJson!!.contains("art-101"))
    }

    @Test
    fun testRejectsUnregisteredToolInPlan() {
        val modelResponse = """
{
  "title": "Hack Goal",
  "summary": "Attempts unauthorized tool",
  "steps": [
    {
      "title": "Nonexistent Tool",
      "description": "Calls unknown tool",
      "toolName": "malicious_unregistered_tool",
      "arguments": {}
    }
  ]
}
        """.trimIndent()

        val steps = planner.parsePlanResponse(modelResponse, "task-2")
        assertEquals(1, steps.size)
        // Unknown tool should be neutralized to null
        assertNull(steps[0].toolName)
        assertNull(steps[0].toolInputJson)
    }
}
