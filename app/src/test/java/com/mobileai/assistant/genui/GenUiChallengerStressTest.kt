package com.mobileai.assistant.genui

import com.mobileai.assistant.data.genui.GenUiParseResult
import com.mobileai.assistant.data.genui.GenUiSegment
import com.mobileai.assistant.data.genui.GenUiStreamSplitter
import com.mobileai.assistant.data.genui.GenUiValidationResult
import com.mobileai.assistant.data.genui.GenUiValidator
import com.mobileai.assistant.data.genui.StreamingGenUiParser
import com.mobileai.assistant.data.genui.StreamingJsonRepair
import com.mobileai.assistant.domain.genui.*
import com.mobileai.assistant.presentation.components.genui.computeTopologicalLayout
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GenUiChallengerStressTest {

    // =========================================================================
    // 1. ADVERSARIAL AST & TYPE MISMATCH TESTS (GenUiValidator Crash Surface)
    // =========================================================================

    @Test
    fun testNonPrimitiveTypePropertyDoesNotCrashValidator() {
        val payloadWithArrayType = """{"type": ["not", "a", "string"]}"""
        val result = GenUiValidator.validate(payloadWithArrayType)
        assertNotNull(result)
        assertTrue(
            "Validator must degrade gracefully to Malformed or Fallback, got ${result::class.simpleName}",
            result is GenUiValidationResult.Malformed || result is GenUiValidationResult.Fallback
        )
    }

    @Test
    fun testNonPrimitiveObjectTypePropertyDoesNotCrashValidator() {
        val payloadWithObjectType = """{"type": {"subType": "card"}}"""
        val result = GenUiValidator.validate(payloadWithObjectType)
        assertNotNull(result)
        assertTrue(
            "Validator must degrade gracefully to Malformed or Fallback, got ${result::class.simpleName}",
            result is GenUiValidationResult.Malformed || result is GenUiValidationResult.Fallback
        )
    }

    @Test
    fun testNonPrimitiveIdPropertyDoesNotCrashValidator() {
        val payloadWithArrayId = """{"type": "card", "id": ["id1", "id2"]}"""
        try {
            val result = GenUiValidator.validate(payloadWithArrayId)
            assertNotNull(result)
        } catch (t: Throwable) {
            fail("GenUiValidator.validate threw uncaught exception on array 'id': ${t::class.qualifiedName}: ${t.message}")
        }
    }

    @Test
    fun testNonPrimitivePropertiesInAllComponentTypes() {
        val payloads = listOf(
            """{"type": "metric", "label": ["array_label"], "value": 123}""",
            """{"type": "metric", "label": "CPU", "value": {"complex": "value"}}""",
            """{"type": "card", "title": {"text": "hello"}, "children": []}""",
            """{"type": "chart", "chartType": ["line"], "series": []}""",
            """{"type": "table", "columns": [{"key": "col1", "widthWeight": {"weight": 2}}], "rows": []}""",
            """{"type": "form", "fields": [{"fieldId": "f1", "type": ["input_text"]}], "submitLabel": {"txt": "ok"}}""",
            """{"type": "diagram", "nodes": [{"id": "n1", "shape": ["pill"]}], "edges": []}""",
            """{"type": "button", "label": {"btn": 1}, "actionId": ["act"]}"""
        )

        for ((idx, p) in payloads.withIndex()) {
            try {
                val res = GenUiValidator.validate(p)
                assertNotNull("Payload #$idx should return a validation result", res)
            } catch (t: Throwable) {
                fail("Payload #$idx threw uncaught exception: ${t::class.qualifiedName}: ${t.message}\nPayload: $p")
            }
        }
    }

    // =========================================================================
    // 2. STREAMING JSON REPAIR STRESS & CORNER CASES
    // =========================================================================

    @Test
    fun testRepairAdversarialDelimitersAndCutoffs() {
        val cases = listOf(
            "" to "{}",
            "   " to "{}",
            "{" to "{}",
            "}" to "{}",
            "[" to "[]",
            "]" to "[]",
            "\"" to "\"\"",
            "\\" to "{}",
            "\"\\" to "\"\"",
            "\"\\\"" to "\"\"",
            "{\"a\": 1, \"b\": " to "{\"a\": 1}",
            "{\"a\": 1, \"b\":" to "{\"a\": 1}",
            "{\"a\": 1, \"b\"" to "{\"a\": 1}",
            "{\"a\": 1, " to "{\"a\": 1}",
            "{\"a\": [1, 2, " to "{\"a\": [1, 2]}",
            "{\"a\": 12." to "{\"a\": 12}",
            "{\"a\": - " to "{}",
            "{\"a\": + " to "{}",
            "[[[[[[[[[[[[[[[" to "[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]",
            "{{{{{{{{{{{{{{{" to "{{{{{{{{{{{{{{{}}}}}}}}}}}}}}}",
            "{\"text\": \"hello \\n world" to "{\"text\": \"hello \\n world\"}",
            "{\"escaped_quote\": \"before \\\" cut" to "{\"escaped_quote\": \"before \\\" cut\"}"
        )

        for ((input, _) in cases) {
            try {
                val repaired = StreamingJsonRepair.repair(input)
                assertNotNull("Repaired result should not be null for: $input", repaired)
                assertTrue("Repaired string must not be blank", repaired.isNotBlank())
            } catch (t: Throwable) {
                fail("StreamingJsonRepair.repair crashed on input: '$input' with ${t::class.qualifiedName}: ${t.message}")
            }
        }
    }

    @Test
    fun testRepairFuzzingRandomInputDoesNotThrow() {
        val chars = listOf('{', '}', '[', ']', ':', ',', '"', '\\', 'a', '1', ' ', '\n', '\t')
        val rng = Random(42)

        for (i in 1..200) {
            val length = rng.nextInt(1, 100)
            val randomString = (1..length).map { chars[rng.nextInt(chars.size)] }.joinToString("")
            try {
                val repaired = StreamingJsonRepair.repair(randomString)
                assertNotNull(repaired)
            } catch (t: Throwable) {
                fail("StreamingJsonRepair crashed on fuzz input: '$randomString' with ${t::class.qualifiedName}: ${t.message}")
            }
        }
    }

    @Test
    fun testRepairDeeplyNestedBraces() {
        // 500 opening braces
        val deepOpen = "{".repeat(500)
        val repaired = StreamingJsonRepair.repair(deepOpen)
        assertEquals(1000, repaired.length)
        assertTrue(repaired.startsWith("{".repeat(500)))
        assertTrue(repaired.endsWith("}".repeat(500)))
    }

    @Test
    fun testRepairCutoffUnicodeEscape() {
        val cutoffUnicode = """{"title": "\u00"""
        val repaired = StreamingJsonRepair.repair(cutoffUnicode)
        assertNotNull(repaired)
        // Validator should safely handle the syntax error from invalid unicode
        val result = GenUiValidator.validate(cutoffUnicode)
        assertTrue(result is GenUiValidationResult.Malformed)
    }

    @Test
    fun testRepairSurrogatePairCutoff() {
        // High surrogate \uD83D alone before quote
        val cutoffEmoji = "{\"title\": \"\uD83D"
        val repaired = StreamingJsonRepair.repair(cutoffEmoji)
        assertNotNull(repaired)
        val result = GenUiValidator.validate(cutoffEmoji)
        assertNotNull(result)
    }

    // =========================================================================
    // 3. GENUI STREAM SPLITTER & PARSER STRESS
    // =========================================================================

    @Test
    fun testStreamSplitterTokenByTokenSimulation() {
        val fullStream = """
            Here is your requested data card:
            ```genui
            {
              "type": "card",
              "title": "Quarterly Performance",
              "children": [
                {
                  "type": "metric",
                  "label": "Revenue",
                  "value": "${'$'}1.2M",
                  "trend": 14.5,
                  "trendDirection": "UP"
                }
              ]
            }
            ```
            Hope this helps! Feel free to ask more.
        """.trimIndent()

        val parser = StreamingGenUiParser()
        val accumulated = StringBuilder()

        for (c in fullStream) {
            accumulated.append(c)
            try {
                val results = parser.feedChunk(c.toString())
                assertNotNull(results)
            } catch (t: Throwable) {
                fail("StreamingGenUiParser crashed on character '$c' at offset ${accumulated.length}: ${t::class.qualifiedName}: ${t.message}")
            }
        }

        val finishResults = parser.finish()
        assertTrue("Finish should produce segments", finishResults.isNotEmpty())
        val hasCompleteUi = finishResults.any { it is GenUiParseResult.CompleteUi }
        assertTrue("Parser should yield CompleteUi at end of stream", hasCompleteUi)
    }

    @Test
    fun testStreamSplitterPrefixFlickerHolding() {
        val prefixChunk1 = "Some introductory text\n```ge"
        val segments1 = GenUiStreamSplitter.split(prefixChunk1, isStreaming = true)
        assertEquals(1, segments1.size)
        assertTrue(segments1[0] is GenUiSegment.Text)
        assertEquals("Some introductory text", (segments1[0] as GenUiSegment.Text).content)

        val prefixChunk2 = "Some introductory text\n```genui\n{\"type\": \"card\"}"
        val segments2 = GenUiStreamSplitter.split(prefixChunk2, isStreaming = true)
        assertEquals(2, segments2.size)
        assertTrue(segments2[0] is GenUiSegment.Text)
        assertTrue(segments2[1] is GenUiSegment.Ui)
        assertFalse((segments2[1] as GenUiSegment.Ui).isComplete)
    }

    @Test
    fun testStreamSplitterWindowsCrlfDelimiters() {
        val crlfContent = "Header\r\n```genui\r\n{\"type\": \"card\"}\r\n```\r\nFooter"
        val segments = GenUiStreamSplitter.split(crlfContent, isStreaming = false)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is GenUiSegment.Text)
        assertTrue(segments[1] is GenUiSegment.Ui)
        assertTrue(segments[2] is GenUiSegment.Text)
    }

    @Test
    fun testStreamSplitterMultipleInterleavedBlocks() {
        val multiContent = """
            Block 1 Intro
            ```genui
            {"type": "metric", "label": "M1", "value": "10"}
            ```
            Middle Commentary
            ```genui
            {"type": "metric", "label": "M2", "value": "20"}
            ```
            Final Outro
        """.trimIndent()

        val segments = GenUiStreamSplitter.split(multiContent, isStreaming = false)
        assertEquals(5, segments.size)
        assertTrue(segments[0] is GenUiSegment.Text)
        assertTrue(segments[1] is GenUiSegment.Ui)
        assertTrue(segments[2] is GenUiSegment.Text)
        assertTrue(segments[3] is GenUiSegment.Ui)
        assertTrue(segments[4] is GenUiSegment.Text)
    }

    // =========================================================================
    // 4. DIAGRAM TOPOLOGICAL LAYOUT CYCLIC GRAPH & ADVERSARIAL STRESS
    // =========================================================================

    @Test
    fun testDiagramLayoutHandlesPureCycleWithoutInfiniteLoop() {
        val nodes = listOf(
            DiagramNode(id = "A", label = "Node A"),
            DiagramNode(id = "B", label = "Node B"),
            DiagramNode(id = "C", label = "Node C")
        )
        val edges = listOf(
            DiagramEdge(from = "A", to = "B"),
            DiagramEdge(from = "B", to = "C"),
            DiagramEdge(from = "C", to = "A")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(3, layouts.size)
        val ids = layouts.map { it.node.id }.toSet()
        assertEquals(setOf("A", "B", "C"), ids)
    }

    @Test
    fun testDiagramLayoutHandlesReachableCycleWithoutInfiniteLoop() {
        val nodes = listOf(
            DiagramNode(id = "Root", label = "Root Node"),
            DiagramNode(id = "A", label = "Node A"),
            DiagramNode(id = "B", label = "Node B")
        )
        val edges = listOf(
            DiagramEdge(from = "Root", to = "A"),
            DiagramEdge(from = "A", to = "B"),
            DiagramEdge(from = "B", to = "A")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.LEFT_TO_RIGHT)
        assertEquals(3, layouts.size)
    }

    @Test
    fun testDiagramLayoutDenseSelfConnectedGraph() {
        val nodes = (1..10).map { DiagramNode(id = "N$it", label = "Node $it") }
        val edges = (1..10).map { DiagramEdge(from = "N$it", to = "N$it") }

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(10, layouts.size)
    }

    @Test
    fun testDiagramLayoutDisconnectedAndDanglingEdges() {
        val nodes = listOf(
            DiagramNode(id = "N1", label = "Node 1"),
            DiagramNode(id = "N2", label = "Node 2")
        )
        val edges = listOf(
            DiagramEdge(from = "N1", to = "NON_EXISTENT_1"),
            DiagramEdge(from = "NON_EXISTENT_2", to = "N2")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(2, layouts.size)
    }

    // =========================================================================
    // 5. VALIDATOR BOUNDS & GRACEFUL DEGRADATION
    // =========================================================================

    @Test
    fun testAntiDosMaxBoundsEnforcement() {
        // Table with 1,000 rows clamped to MAX_TABLE_ROWS (500)
        val hugeTable = buildString {
            append("""{"type": "table", "columns": [{"key": "col1"}], "rows": [""")
            for (i in 1..1000) {
                append("""{"col1": "r$i"}""")
                if (i < 1000) append(",")
            }
            append("]}")
        }

        val result = GenUiValidator.validate(hugeTable)
        assertTrue(result is GenUiValidationResult.Success)
        val table = (result as GenUiValidationResult.Success).component as GenUiTable
        assertEquals(GenUiValidator.MAX_TABLE_ROWS, table.rows.size)
    }

    @Test
    fun testChartMaxSeriesAndPointsClamped() {
        val hugeChart = buildString {
            append("""{"type": "chart", "series": [""")
            for (s in 1..12) { // Max 8
                append("""{"name": "S$s", "data": [""")
                for (p in 1..300) { // Max 250
                    append("""{"x": "$p", "y": $p}""")
                    if (p < 300) append(",")
                }
                append("]}")
                if (s < 12) append(",")
            }
            append("]}")
        }

        val result = GenUiValidator.validate(hugeChart)
        assertTrue(result is GenUiValidationResult.Success)
        val chart = (result as GenUiValidationResult.Success).component as GenUiChart
        assertEquals(GenUiValidator.MAX_CHART_SERIES, chart.series.size)
        assertEquals(GenUiValidator.MAX_CHART_POINTS, chart.series[0].data.size)
    }

    @Test
    fun testNonJsonDegradesGracefullyToMalformed() {
        val nonJson = "This is clearly not valid JSON syntax."
        val result = GenUiValidator.validate(nonJson)
        assertTrue(result is GenUiValidationResult.Malformed)
    }

    @Test
    fun testNonJsonObjectArrayDegradesGracefullyToMalformed() {
        val arrayJson = "[1, 2, 3, 4]"
        val result = GenUiValidator.validate(arrayJson)
        assertTrue(result is GenUiValidationResult.Malformed)
    }
}
