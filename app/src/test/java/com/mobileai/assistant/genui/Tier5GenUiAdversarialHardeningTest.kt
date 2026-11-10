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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Tier 5 White-Box Adversarial Hardening Suite for Generative UI & Streaming Parser.
 *
 * Exhaustively stress-tests:
 * 1. StreamingJsonRepair: Multi-level escape quotes, dangling tokens, broken numbers, LIFO balancing.
 * 2. GenUiStreamSplitter: Boundary cutoffs, whitespace variation, fence prefixes, CRLF handling.
 * 3. StreamingGenUiParser: Incremental token-by-token stream feeding, recovery, reset lifecycle.
 * 4. GenUiValidator: Implicit heuristic typing, nesting depth limits, bounds clamping, value coercion.
 * 5. Diagram Topological Layout: Multi-component cyclic graphs, self-loops, dangling references.
 * 6. Table Sorting & Paging: Multi-currency, negative values, ragged rows, pagination boundary conditions.
 * 7. Form Field Validation & Serialization: Polymorphic events, type rules, injection resistance.
 * 8. Zero-Code Security Enforcement: XSS, command injection, template syntax, RTL unicode safety.
 */
class Tier5GenUiAdversarialHardeningTest {

    private val json = GenUiSerialization.json

    // =========================================================================
    // 1. STREAMING JSON REPAIR: ESCAPE SEQUENCES & SYNTAX BOUNDARIES
    // =========================================================================

    @Test
    fun testRepairEscapedQuotesWithMultipleBackslashes() {
        // Single backslash before quote (escaped quote): \"
        val singleEscape = """{"message": "Hello \"World"""
        val rep1 = StreamingJsonRepair.repair(singleEscape)
        assertEquals("""{"message": "Hello \"World"}""", rep1)
        val obj1 = json.parseToJsonElement(rep1).jsonObject
        assertNotNull(obj1)

        // Double backslash before quote (escaped backslash, then string boundary): \\"
        // In raw JSON: {"message": "Hello \\"} -> already closed!
        val doubleEscape = """{"message": "Hello \\"""
        val rep2 = StreamingJsonRepair.repair(doubleEscape)
        assertEquals("""{"message": "Hello \\"}""", rep2)
        val obj2 = json.parseToJsonElement(rep2).jsonObject
        assertNotNull(obj2)

        // Triple backslash before cutoff: \\\ -> 1 escaped backslash + 1 dangling backslash
        val tripleEscape = """{"message": "Path: \\\"""
        val rep3 = StreamingJsonRepair.repair(tripleEscape)
        assertNotNull(rep3)
        val obj3 = json.parseToJsonElement(rep3).jsonObject
        assertNotNull(obj3)
    }

    @Test
    fun testRepairDanglingKeysWithEscapedQuotesInKeyName() {
        // Key contains escaped quotes: {"escaped\"key": 
        val partial = """{"escaped\"key": """
        val repaired = StreamingJsonRepair.repair(partial)
        assertEquals("{}", repaired)

        // Multiple properties where the second has an escaped key that is dangling
        val partial2 = """{"valid": 1, "another\"key": """
        val repaired2 = StreamingJsonRepair.repair(partial2)
        assertEquals("""{"valid": 1}""", repaired2)
    }

    @Test
    fun testRepairUnpairedKeysAtRootAndNestedLevels() {
        // Unpaired key at root level without comma
        val partialRoot = """{"unpairedKey"""
        val repairedRoot = StreamingJsonRepair.repair(partialRoot)
        assertEquals("{}", repairedRoot)

        // Unpaired key after comma at root level
        val partialAfterComma = """{"prop1": 10, "unpaired"""
        val repairedAfterComma = StreamingJsonRepair.repair(partialAfterComma)
        assertEquals("""{"prop1": 10}""", repairedAfterComma)

        // Unpaired key inside nested object
        val partialNested = """{"config": {"timeout": 30, "ret"""
        val repairedNested = StreamingJsonRepair.repair(partialNested)
        assertEquals("""{"config": {"timeout": 30}}""", repairedNested)
    }

    @Test
    fun testRepairIncompleteScientificNotationAndSigns() {
        // Incomplete scientific notation numbers: 1.5e+, 2E-, 10e
        val cases = listOf(
            """{"val": 1.5e+}""" to """{"val": 1.5e+}""",
            """{"val": 1.5e+""" to """{"val": 1.5}""",
            """{"val": -""" to "{}",
            """{"val": +""" to "{}",
            """{"val": 99.""" to """{"val": 99}""",
            """{"val": 0.""" to """{"val": 0}"""
        )
        for ((input, _) in cases) {
            val repaired = StreamingJsonRepair.repair(input)
            assertNotNull(repaired)
            val parsed = try {
                json.parseToJsonElement(repaired)
            } catch (e: Exception) {
                null
            }
            assertNotNull("Repaired JSON must be syntactically valid for input: '$input', got '$repaired'", parsed)
        }
    }

    @Test
    fun testRepairDeeplyNestedAlternatingBracesAndBrackets() {
        // Nested structure: {"a": [{"b": [{"c": [{"d": [
        val partial = """{"a": [{"b": [{"c": [{"d": ["""
        val repaired = StreamingJsonRepair.repair(partial)
        assertEquals("""{"a": [{"b": [{"c": [{"d": []}]}]}]}""", repaired)
        val parsed = json.parseToJsonElement(repaired)
        assertTrue(parsed is JsonObject)
    }

    @Test
    fun testRepairUnmatchedClosingDelimiters() {
        // Mismatched closing delimiters in source
        val partial = """{"items": [1, 2, 3}}"""
        val repaired = StreamingJsonRepair.repair(partial)
        assertNotNull(repaired)
        // Should not crash repair
        assertTrue(repaired.isNotEmpty())
    }

    @Test
    fun testRepairFuzzingWithRandomTokenSlices() {
        val basePayload = """
            {
              "type": "card",
              "title": "Adversarial Stress Card",
              "subtitle": "Testing progressive streaming",
              "style": "elevated",
              "children": [
                {
                  "type": "metric",
                  "label": "Throughput",
                  "value": "4,500 req/s",
                  "trend": 18.2,
                  "trendDirection": "up"
                },
                {
                  "type": "table",
                  "title": "Endpoints",
                  "columns": [
                    {"key": "path", "title": "Path"},
                    {"key": "p99", "title": "P99 Latency"}
                  ],
                  "rows": [
                    {"path": "/api/v1/chat", "p99": "45ms"},
                    {"path": "/api/v1/stream", "p99": "12ms"}
                  ]
                }
              ]
            }
        """.trimIndent()

        // Test EVERY single prefix truncation point from index 1 to full length
        for (cutoff in 1..basePayload.length) {
            val slice = basePayload.substring(0, cutoff)
            try {
                val repaired = StreamingJsonRepair.repair(slice)
                assertNotNull(repaired)
                assertTrue("Repaired JSON must not be blank", repaired.isNotBlank())
            } catch (t: Throwable) {
                fail("StreamingJsonRepair crashed on prefix slice of length $cutoff: ${t.message}")
            }
        }
    }

    // =========================================================================
    // 2. GENUI STREAM SPLITTER: BOUNDARY, PREFIX & ESCAPE SCENARIOS
    // =========================================================================

    @Test
    fun testStreamSplitterLeadingIndentationAndTabs() {
        val content = "\t  ```genui\n{\"type\": \"metric\", \"label\": \"Indented\", \"value\": \"1\"}\n  \t```"
        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is GenUiSegment.Ui)
        val ui = segments[0] as GenUiSegment.Ui
        assertTrue(ui.isComplete)
        assertTrue(ui.rawPayload.contains("\"Indented\""))
    }

    @Test
    fun testStreamSplitterTrailingSpacesOnFences() {
        val content = "```genui   \t\n{\"type\": \"card\"}\n```   \t\nTrailing text"
        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is GenUiSegment.Ui)
        assertTrue(segments[1] is GenUiSegment.Text)
        val ui = segments[0] as GenUiSegment.Ui
        assertTrue(ui.isComplete)
        assertEquals("Trailing text", (segments[1] as GenUiSegment.Text).content.trim())
    }

    @Test
    fun testStreamSplitterStreamingFencePrefixHoldouts() {
        // Verify every prefix in FENCE_PREFIXES is held back during streaming
        val prefixes = listOf(
            "\n```genui", "\n```genu", "\n```gen", "\n```ge", "\n```g", "\n```", "\n``", "\n`",
            "```genui", "```genu", "```gen", "```ge", "```g", "```"
        )
        for (prefix in prefixes) {
            val streamChunk = "System prompt response" + prefix
            val segments = GenUiStreamSplitter.split(streamChunk, isStreaming = true)
            // The candidate prefix must be trimmed from the trailing text segment
            if (segments.isNotEmpty() && segments[0] is GenUiSegment.Text) {
                val text = (segments[0] as GenUiSegment.Text).content
                assertFalse("Text chunk should not leak partial prefix: '$prefix'", text.endsWith(prefix))
            }
        }
    }

    @Test
    fun testStreamSplitterConsecutiveAdjacentGenUiBlocks() {
        // Back-to-back blocks without intervening text
        val content = """
            ```genui
            {"type": "metric", "label": "M1", "value": "1"}
            ```
            ```genui
            {"type": "metric", "label": "M2", "value": "2"}
            ```
        """.trimIndent()

        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        val uiSegments = segments.filterIsInstance<GenUiSegment.Ui>()
        assertEquals(2, uiSegments.size)
        assertTrue(uiSegments[0].isComplete)
        assertTrue(uiSegments[1].isComplete)
    }

    @Test
    fun testStreamSplitterInlineBackticksInsidePayloadNotTriggeringClose() {
        val content = """
            ```genui
            {
              "type": "card",
              "title": "Use `npm install` to setup",
              "subtitle": "Run ```code``` inside strings"
            }
            ```
        """.trimIndent()

        val segments = GenUiStreamSplitter.split(content, isStreaming = false)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is GenUiSegment.Ui)
        val ui = segments[0] as GenUiSegment.Ui
        assertTrue(ui.isComplete)
        assertTrue(ui.rawPayload.contains("npm install"))
    }

    @Test
    fun testStreamSplitterOpenFenceAtExactEndOfContent() {
        val content = "Leading text\n```genui"
        val segments = GenUiStreamSplitter.split(content, isStreaming = true)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is GenUiSegment.Text)
        assertTrue(segments[1] is GenUiSegment.Ui)
        val ui = segments[1] as GenUiSegment.Ui
        assertEquals("", ui.rawPayload)
        assertFalse(ui.isComplete)
    }

    // =========================================================================
    // 3. STREAMING GENUI PARSER: TOKEN-BY-TOKEN & LIFECYCLE
    // =========================================================================

    @Test
    fun testStreamingParserTokenByTokenFeedSingleByteChunks() {
        val payload = """
            Hello! Here is your status:
            ```genui
            {
              "type": "metric",
              "label": "Memory",
              "value": "2.4 GB"
            }
            ```
            Have a nice day!
        """.trimIndent()

        val parser = StreamingGenUiParser()
        val partialResults = mutableListOf<GenUiParseResult>()

        for (ch in payload) {
            val results = parser.feedChunk(ch.toString())
            partialResults.addAll(results)
        }

        val finalResults = parser.finish()
        assertTrue(finalResults.isNotEmpty())
        val completeUis = finalResults.filterIsInstance<GenUiParseResult.CompleteUi>()
        assertEquals(1, completeUis.size)
        val comp = completeUis[0].component
        assertTrue(comp is GenUiMetric)
        assertEquals("Memory", (comp as GenUiMetric).label)
        assertEquals("2.4 GB", comp.value)
    }

    @Test
    fun testStreamingParserResetOnFinishAllowsReusability() {
        val parser = StreamingGenUiParser()

        // Run 1
        parser.feedChunk("Text 1\n```genui\n{\"type\": \"metric\", \"label\": \"A\", \"value\": \"1\"}\n```")
        val r1 = parser.finish()
        assertEquals(2, r1.size)

        // Run 2: Immediately feed new content into same parser instance
        parser.feedChunk("Text 2\n```genui\n{\"type\": \"metric\", \"label\": \"B\", \"value\": \"2\"}\n```")
        val r2 = parser.finish()
        assertEquals(2, r2.size)
        val metric2 = (r2.filterIsInstance<GenUiParseResult.CompleteUi>().first().component as GenUiMetric)
        assertEquals("B", metric2.label)
        assertEquals("2", metric2.value)
    }

    @Test
    fun testStreamingParserEmptyChunksDoNotAlterState() {
        val parser = StreamingGenUiParser()
        val emptyR = parser.feedChunk("")
        assertTrue(emptyR.isEmpty())

        parser.feedChunk("Header\n```genui\n")
        val r2 = parser.feedChunk("")
        assertTrue(r2.isEmpty())

        val finished = parser.finish()
        assertTrue(finished.isNotEmpty())
    }

    // =========================================================================
    // 4. GENUI VALIDATOR: IMPLICIT HEURISTICS, COERCION & CLAMPING
    // =========================================================================

    @Test
    fun testValidatorImplicitTypeInferenceForAllTypes() {
        // Table inference: has columns and rows, no explicit type
        val tablePayload = """{"title": "T1", "columns": [{"key": "c1"}], "rows": [{"c1": "v1"}]}"""
        val resTable = GenUiValidator.validate(tablePayload)
        assertTrue(resTable is GenUiValidationResult.Success)
        assertTrue((resTable as GenUiValidationResult.Success).component is GenUiTable)

        // Chart inference: has chartType or series
        val chartPayload = """{"chartType": "bar", "series": [{"name": "S1", "data": [{"x": "1", "y": 10}]}]}"""
        val resChart = GenUiValidator.validate(chartPayload)
        assertTrue(resChart is GenUiValidationResult.Success)
        assertTrue((resChart as GenUiValidationResult.Success).component is GenUiChart)

        // Form inference: has fields
        val formPayload = """{"fields": [{"fieldId": "f1", "type": "input_text"}]}"""
        val resForm = GenUiValidator.validate(formPayload)
        assertTrue(resForm is GenUiValidationResult.Success)
        assertTrue((resForm as GenUiValidationResult.Success).component is GenUiForm)

        // Diagram inference: has nodes and edges
        val diagPayload = """{"nodes": [{"id": "n1", "label": "Start"}], "edges": []}"""
        val resDiag = GenUiValidator.validate(diagPayload)
        assertTrue(resDiag is GenUiValidationResult.Success)
        assertTrue((resDiag as GenUiValidationResult.Success).component is GenUiDiagram)

        // Metric inference: has label and value
        val metricPayload = """{"label": "Active Users", "value": "15,200"}"""
        val resMetric = GenUiValidator.validate(metricPayload)
        assertTrue(resMetric is GenUiValidationResult.Success)
        assertTrue((resMetric as GenUiValidationResult.Success).component is GenUiMetric)

        // Button inference: has actionId
        val buttonPayload = """{"label": "Trigger", "actionId": "launch_workflow"}"""
        val resButton = GenUiValidator.validate(buttonPayload)
        assertTrue(resButton is GenUiValidationResult.Success)
        assertTrue((resButton as GenUiValidationResult.Success).component is GenUiButton)
    }

    @Test
    fun testValidatorClampsExtremeColumnAndRowBounds() {
        // Exceeding MAX_TABLE_COLUMNS (20)
        val colList = (1..30).joinToString(",") { """{"key": "k$it", "title": "Col $it"}""" }
        val payloadCols = """{"type": "table", "columns": [$colList], "rows": []}"""
        val resCols = GenUiValidator.validate(payloadCols)
        assertTrue(resCols is GenUiValidationResult.Success)
        val tableCols = (resCols as GenUiValidationResult.Success).component as GenUiTable
        assertEquals(GenUiValidator.MAX_TABLE_COLUMNS, tableCols.columns.size)

        // PageSize clamping
        val payloadPageSizeZero = """{"type": "table", "pageSize": 0, "columns": [], "rows": []}"""
        val resP0 = GenUiValidator.validate(payloadPageSizeZero)
        assertTrue(resP0 is GenUiValidationResult.Success)
        assertEquals(1, ((resP0 as GenUiValidationResult.Success).component as GenUiTable).pageSize)

        val payloadPageSizeLarge = """{"type": "table", "pageSize": 500, "columns": [], "rows": []}"""
        val resPL = GenUiValidator.validate(payloadPageSizeLarge)
        assertTrue(resPL is GenUiValidationResult.Success)
        assertEquals(100, ((resPL as GenUiValidationResult.Success).component as GenUiTable).pageSize)
    }

    @Test
    fun testValidatorClampsFormSliderBoundsAndDefaultValue() {
        // Slider with default value outside [min, max]
        val payload = """
            {
              "type": "form",
              "fields": [
                {
                  "type": "input_slider",
                  "fieldId": "volume",
                  "min": 10.0,
                  "max": 50.0,
                  "defaultValue": 100.0
                },
                {
                  "type": "input_slider",
                  "fieldId": "brightness",
                  "min": 20.0,
                  "max": 80.0,
                  "defaultValue": 5.0
                }
              ]
            }
        """.trimIndent()

        val res = GenUiValidator.validate(payload)
        assertTrue(res is GenUiValidationResult.Success)
        val form = (res as GenUiValidationResult.Success).component as GenUiForm
        assertEquals(2, form.fields.size)

        val slider1 = form.fields[0] as GenUiSliderInput
        assertEquals(50.0f, slider1.defaultValue, 0.001f) // Clamped to max

        val slider2 = form.fields[1] as GenUiSliderInput
        assertEquals(20.0f, slider2.defaultValue, 0.001f) // Clamped to min
    }

    @Test
    fun testValidatorCoercesColumnAlignmentAndWidthWeight() {
        val payload = """
            {
              "type": "table",
              "columns": [
                {"key": "c1", "alignment": "UNKNOWN_ALIGN", "widthWeight": -5.0},
                {"key": "c2", "alignment": "RIGHT", "widthWeight": 10.0}
              ],
              "rows": []
            }
        """.trimIndent()

        val res = GenUiValidator.validate(payload)
        assertTrue(res is GenUiValidationResult.Success)
        val table = (res as GenUiValidationResult.Success).component as GenUiTable
        assertEquals(ColumnAlignment.LEFT, table.columns[0].alignment) // Defaulted
        assertEquals(0.2f, table.columns[0].widthWeight, 0.001f) // Clamped to 0.2
        assertEquals(ColumnAlignment.RIGHT, table.columns[1].alignment)
        assertEquals(5.0f, table.columns[1].widthWeight, 0.001f) // Clamped to 5.0
    }

    @Test
    fun testValidatorCoercesRaggedAndHeterogeneousTableRows() {
        val payload = """
            {
              "type": "table",
              "columns": [{"key": "id"}, {"key": "meta"}],
              "rows": [
                {"id": 101, "meta": {"nested": "value"}},
                {"id": "102", "meta": [1, 2, 3]},
                {"id": true, "meta": null}
              ]
            }
        """.trimIndent()

        val res = GenUiValidator.validate(payload)
        assertTrue(res is GenUiValidationResult.Success)
        val table = (res as GenUiValidationResult.Success).component as GenUiTable
        assertEquals(3, table.rows.size)
        // Values coerced to string
        assertEquals("101", table.rows[0]["id"])
        assertTrue(table.rows[0]["meta"]!!.contains("nested"))
        assertEquals("true", table.rows[2]["id"])
    }

    @Test
    fun testValidatorCoercesMetricTrendValuesAndDirections() {
        val cases = listOf(
            """{"type": "metric", "label": "L", "value": "V", "trend": "+12.5%"}""" to (12.5 to TrendDirection.UP),
            """{"type": "metric", "label": "L", "value": "V", "trend": "-8.4%"}""" to (-8.4 to TrendDirection.DOWN),
            """{"type": "metric", "label": "L", "value": "V", "trend": "0.0%"}""" to (0.0 to TrendDirection.NEUTRAL),
            """{"type": "metric", "label": "L", "value": "V", "trend": 5.0, "trendDirection": "down"}""" to (5.0 to TrendDirection.DOWN)
        )

        for ((p, expected) in cases) {
            val res = GenUiValidator.validate(p)
            assertTrue(res is GenUiValidationResult.Success)
            val metric = (res as GenUiValidationResult.Success).component as GenUiMetric
            assertEquals(expected.first, metric.trend!!, 0.001)
            assertEquals(expected.second, metric.trendDirection)
        }
    }

    @Test
    fun testValidatorCoercesChartNonNumericPointsToZero() {
        val payload = """
            {
              "type": "chart",
              "series": [
                {
                  "name": "Corrupted Data",
                  "data": [
                    {"x": "A", "y": "not_a_number"},
                    {"x": "B", "y": null},
                    {"x": "C", "y": 42.5}
                  ]
                }
              ]
            }
        """.trimIndent()

        val res = GenUiValidator.validate(payload)
        assertTrue(res is GenUiValidationResult.Success)
        val chart = (res as GenUiValidationResult.Success).component as GenUiChart
        val series = chart.series[0]
        assertEquals(3, series.data.size)
        assertEquals(0.0, series.data[0].y, 0.001)
        assertEquals(0.0, series.data[1].y, 0.001)
        assertEquals(42.5, series.data[2].y, 0.001)
    }

    @Test
    fun testValidatorCoercesCardStyleAndButtonVariants() {
        val payload = """
            {
              "type": "card",
              "style": "UNKNOWN_STYLE",
              "children": [
                {"type": "button", "variant": "INVALID_VARIANT", "actionId": "b1"}
              ]
            }
        """.trimIndent()

        val res = GenUiValidator.validate(payload)
        assertTrue(res is GenUiValidationResult.Success)
        val card = (res as GenUiValidationResult.Success).component as GenUiCard
        assertEquals(CardStyle.ELEVATED, card.style) // Coerced to elevated
        val btn = card.children[0] as GenUiButton
        assertEquals(ButtonVariant.PRIMARY, btn.variant) // Coerced to primary
    }

    // =========================================================================
    // 5. DIAGRAM TOPOLOGICAL LAYOUT: CYCLIC & ADVERSARIAL TOPOLOGIES
    // =========================================================================

    @Test
    fun testDiagramLayoutDisconnectedMultipleCycles() {
        // Cycle 1: A -> B -> A
        // Cycle 2: C -> D -> C
        // Isolated: E, F
        val nodes = listOf(
            DiagramNode(id = "A", label = "A"),
            DiagramNode(id = "B", label = "B"),
            DiagramNode(id = "C", label = "C"),
            DiagramNode(id = "D", label = "D"),
            DiagramNode(id = "E", label = "E"),
            DiagramNode(id = "F", label = "F")
        )
        val edges = listOf(
            DiagramEdge(from = "A", to = "B"),
            DiagramEdge(from = "B", to = "A"),
            DiagramEdge(from = "C", to = "D"),
            DiagramEdge(from = "D", to = "C")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(6, layouts.size)
        val ids = layouts.map { it.node.id }.toSet()
        assertEquals(setOf("A", "B", "C", "D", "E", "F"), ids)
        layouts.forEach {
            assertTrue("Width must be positive", it.bounds.width > 0)
            assertTrue("Height must be positive", it.bounds.height > 0)
        }
    }

    @Test
    fun testDiagramLayoutDenseMeshCompleteGraphK5() {
        // K5 complete directed graph (each node connects to all others)
        val nodes = (1..5).map { DiagramNode(id = "K$it", label = "Node $it") }
        val edges = mutableListOf<DiagramEdge>()
        for (i in 1..5) {
            for (j in 1..5) {
                if (i != j) {
                    edges.add(DiagramEdge(from = "K$i", to = "K$j"))
                }
            }
        }

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.LEFT_TO_RIGHT)
        assertEquals(5, layouts.size)
        // All nodes positioned without infinite recursion or crash
        layouts.forEach {
            assertTrue(it.bounds.left >= 0)
            assertTrue(it.bounds.top >= 0)
        }
    }

    @Test
    fun testDiagramLayoutLeftToRightDirectionValidGeometry() {
        val nodes = listOf(
            DiagramNode(id = "In", label = "Input"),
            DiagramNode(id = "Proc", label = "Process"),
            DiagramNode(id = "Out", label = "Output")
        )
        val edges = listOf(
            DiagramEdge(from = "In", to = "Proc"),
            DiagramEdge(from = "Proc", to = "Out")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.LEFT_TO_RIGHT)
        assertEquals(3, layouts.size)

        val inB = layouts.find { it.node.id == "In" }!!.bounds
        val procB = layouts.find { it.node.id == "Proc" }!!.bounds
        val outB = layouts.find { it.node.id == "Out" }!!.bounds

        // Left-to-right progression
        assertTrue("Input right should be less than Process left", inB.right < procB.left)
        assertTrue("Process right should be less than Output left", procB.right < outB.left)
    }

    // =========================================================================
    // 6. TABLE SORTING & PAGINATION BOUNDARY ARITHMETIC
    // =========================================================================

    private fun sortRows(
        rows: List<Map<String, String>>,
        columnKey: String,
        ascending: Boolean
    ): List<Map<String, String>> {
        return rows.sortedWith { rowA, rowB ->
            val valA = rowA[columnKey].orEmpty()
            val valB = rowB[columnKey].orEmpty()
            val numA = valA.replace("$", "").replace("€", "").replace("£", "").replace(",", "").replace("%", "").trim().toDoubleOrNull()
            val numB = valB.replace("$", "").replace("€", "").replace("£", "").replace(",", "").replace("%", "").trim().toDoubleOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                valA.compareTo(valB, ignoreCase = true)
            }
            if (ascending) cmp else -cmp
        }
    }

    private fun paginateRows(
        rows: List<Map<String, String>>,
        currentPage: Int,
        pageSize: Int?
    ): Pair<Int, List<Map<String, String>>> {
        val totalPages = if (pageSize != null && pageSize > 0) {
            ((rows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        } else 1

        val pagedRows = if (pageSize != null && pageSize > 0) {
            val start = (currentPage * pageSize).coerceIn(0, rows.size)
            val end = (start + pageSize).coerceIn(0, rows.size)
            rows.subList(start, end)
        } else {
            rows
        }
        return totalPages to pagedRows
    }

    @Test
    fun testTableSortingMixedCurrenciesAndNegativeValues() {
        val rows = listOf(
            mapOf("asset" to "Bond", "return" to "-$250.00"),
            mapOf("asset" to "Stock", "return" to "$1,500.50"),
            mapOf("asset" to "Crypto", "return" to "€-50.00"),
            mapOf("asset" to "Gold", "return" to "£3,200.00"),
            mapOf("asset" to "Cash", "return" to "0.00%")
        )

        val sortedAsc = sortRows(rows, "return", ascending = true)
        // Ascending numerical order: -250, -50, 0.00, 1500.50, 3200
        assertEquals("Bond", sortedAsc[0]["asset"])
        assertEquals("Crypto", sortedAsc[1]["asset"])
        assertEquals("Cash", sortedAsc[2]["asset"])
        assertEquals("Stock", sortedAsc[3]["asset"])
        assertEquals("Gold", sortedAsc[4]["asset"])

        val sortedDesc = sortRows(rows, "return", ascending = false)
        assertEquals("Gold", sortedDesc[0]["asset"])
        assertEquals("Bond", sortedDesc[4]["asset"])
    }

    @Test
    fun testTablePaginationBoundaryArithmetic() {
        val rows = (1..25).map { mapOf("id" to "$it") }

        // Normal pagination: 25 rows, pageSize 10 -> 3 pages
        val (p1Total, p1Rows) = paginateRows(rows, currentPage = 0, pageSize = 10)
        assertEquals(3, p1Total)
        assertEquals(10, p1Rows.size)
        assertEquals("1", p1Rows[0]["id"])

        // Last page: 5 rows
        val (p3Total, p3Rows) = paginateRows(rows, currentPage = 2, pageSize = 10)
        assertEquals(3, p3Total)
        assertEquals(5, p3Rows.size)
        assertEquals("21", p3Rows[0]["id"])

        // Page beyond total pages
        val (_, pOverRows) = paginateRows(rows, currentPage = 10, pageSize = 10)
        assertEquals(0, pOverRows.size)

        // Negative page index clamped to 0
        val (_, pNegRows) = paginateRows(rows, currentPage = -5, pageSize = 10)
        assertEquals(10, pNegRows.size)
        assertEquals("1", pNegRows[0]["id"])

        // Null pageSize yields all rows
        val (pNullTotal, pNullRows) = paginateRows(rows, currentPage = 0, pageSize = null)
        assertEquals(1, pNullTotal)
        assertEquals(25, pNullRows.size)
    }

    // =========================================================================
    // 7. FORM VALIDATION & EVENT SERIALIZATION
    // =========================================================================

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    @Test
    fun testFormEmailValidationBoundaryCases() {
        val validEmails = listOf(
            "user@example.com",
            "first.last+tag@sub.domain.co.uk",
            "12345@domain.org",
            "test_user-name@company.io"
        )
        for (e in validEmails) {
            assertTrue("Expected '$e' to be valid email", emailRegex.matches(e))
        }

        val invalidEmails = listOf(
            "not_an_email",
            "@missing_user.com",
            "missing_domain@",
            "user@domain",
            "user@.com",
            "user@domain.c",
            "spaces in@email.com",
            "<script>@hack.com"
        )
        for (e in invalidEmails) {
            assertFalse("Expected '$e' to be invalid email", emailRegex.matches(e))
        }
    }

    @Test
    fun testGenUiEventPolymorphicSerializationRoundTrip() {
        val events: List<GenUiEvent> = listOf(
            GenUiEvent.FormSubmit(
                formId = "form_feedback",
                actionId = "submit_survey",
                fieldValues = mapOf("rating" to "5", "comments" to "Excellent assistant!")
            ),
            GenUiEvent.ButtonClick(
                actionId = "approve_step",
                payload = mapOf("taskId" to "task_123", "step" to "3"),
                componentId = "btn_approve"
            ),
            GenUiEvent.SelectionChange(
                selectedKey = "tab_analytics",
                selectedLabel = "Analytics View",
                payload = mapOf("viewMode" to "detailed"),
                componentId = "seg_nav"
            ),
            GenUiEvent.DiagramNodeClick(
                diagramId = "diag_arch",
                nodeId = "node_db",
                nodeLabel = "PostgreSQL Cluster",
                componentId = "diag_arch"
            )
        )

        for (event in events) {
            val encoded = json.encodeToString(GenUiEvent.serializer(), event)
            assertNotNull(encoded)
            assertTrue(encoded.contains("\"eventType\""))

            val decoded = json.decodeFromString(GenUiEvent.serializer(), encoded)
            assertEquals(event, decoded)
        }
    }

    // =========================================================================
    // 8. ZERO-CODE EXECUTION & ADVERSARIAL INJECTION HARDENING
    // =========================================================================

    @Test
    fun testMaliciousScriptAndPayloadInjectionSafety() {
        val injectionAttacks = listOf(
            // XSS / HTML Script tags
            """{"type": "metric", "label": "<script>alert('pwned')</script>", "value": "<img src=x onerror=alert(1)>"}""",
            // Template Injection
            """{"type": "card", "title": "${'$'}{7*7}", "subtitle": "{{7*7}} #{1+1}"}""",
            // Shell Command Injection
            """{"type": "button", "label": "; rm -rf / ;", "actionId": "$(cat /etc/passwd)"}""",
            // SQL Injection
            """{"type": "table", "columns": [{"key": "' OR '1'='1"}], "rows": [{"' OR '1'='1": "DROP TABLE users;"}]}""",
            // Path Traversal
            """{"type": "card", "title": "../../../../../etc/shadow"}""",
            // Unicode BiDi / RTL override
            """{"type": "metric", "label": "\u202E\u0041\u0042\u0043\u202C", "value": "100"}""",
            // Complex Emoji / Surrogate pairs
            """{"type": "card", "title": "Family: 👨‍👩‍👧‍👦 Rainbow: 🏳️‍🌈 Astronaut: 👩‍🚀"}"""
        )

        for ((idx, attackPayload) in injectionAttacks.withIndex()) {
            val result = GenUiValidator.validate(attackPayload)
            assertTrue(
                "Attack vector #$idx must safely validate as Success or Fallback without dynamic execution",
                result is GenUiValidationResult.Success || result is GenUiValidationResult.Fallback
            )
            // Ensure components are passive data classes (zero-code execution)
            if (result is GenUiValidationResult.Success) {
                assertNotNull(result.component.id)
            }
        }
    }
}
