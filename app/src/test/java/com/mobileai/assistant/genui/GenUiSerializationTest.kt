package com.mobileai.assistant.genui

import com.mobileai.assistant.domain.genui.*
import org.junit.Assert.*
import org.junit.Test

class GenUiSerializationTest {

    private val json = GenUiSerialization.json

    @Test
    fun testMetricSerializationRoundTrip() {
        val metric = GenUiMetric(
            id = "metric_1",
            label = "Total Revenue",
            value = "$1.2M",
            trend = 12.5,
            trendLabel = "vs last month",
            trendDirection = TrendDirection.UP
        )
        val serialized = json.encodeToString(GenUiComponent.serializer(), metric)
        assertTrue(serialized.contains("\"type\":\"metric\""))

        val decoded = json.decodeFromString(GenUiComponent.serializer(), serialized)
        assertTrue(decoded is GenUiMetric)
        val m = decoded as GenUiMetric
        assertEquals("metric_1", m.id)
        assertEquals("Total Revenue", m.label)
        assertEquals("$1.2M", m.value)
        assertEquals(12.5, m.trend!!, 0.001)
        assertEquals(TrendDirection.UP, m.trendDirection)
    }

    @Test
    fun testCardSerializationWithChildren() {
        val card = GenUiCard(
            id = "card_1",
            title = "Summary Card",
            subtitle = "Q3 Performance",
            style = CardStyle.ELEVATED,
            children = listOf(
                GenUiMetric(id = "m1", label = "Sales", value = "450"),
                GenUiButton(id = "b1", label = "View Details", actionId = "view_details", variant = ButtonVariant.PRIMARY)
            )
        )
        val serialized = json.encodeToString(GenUiComponent.serializer(), card)
        assertTrue(serialized.contains("\"type\":\"card\""))
        assertTrue(serialized.contains("\"type\":\"metric\""))
        assertTrue(serialized.contains("\"type\":\"button\""))

        val decoded = json.decodeFromString(GenUiComponent.serializer(), serialized)
        assertTrue(decoded is GenUiCard)
        val c = decoded as GenUiCard
        assertEquals(2, c.children.size)
        assertTrue(c.children[0] is GenUiMetric)
        assertTrue(c.children[1] is GenUiButton)
    }

    @Test
    fun testChartSerializationRoundTrip() {
        val chart = GenUiChart(
            id = "chart_1",
            chartType = ChartType.LINE,
            title = "Monthly Active Users",
            xAxisLabel = "Month",
            yAxisLabel = "Users",
            series = listOf(
                ChartSeries(
                    name = "2026",
                    color = "#2563EB",
                    data = listOf(
                        ChartPoint(x = "Jan", y = 100.0),
                        ChartPoint(x = "Feb", y = 150.0),
                        ChartPoint(x = "Mar", y = 220.0)
                    )
                )
            )
        )
        val serialized = json.encodeToString(GenUiComponent.serializer(), chart)
        assertTrue(serialized.contains("\"type\":\"chart\""))

        val decoded = json.decodeFromString(GenUiComponent.serializer(), serialized)
        assertTrue(decoded is GenUiChart)
        val ch = decoded as GenUiChart
        assertEquals(ChartType.LINE, ch.chartType)
        assertEquals(1, ch.series.size)
        assertEquals(3, ch.series[0].data.size)
        assertEquals(220.0, ch.series[0].data[2].y, 0.001)
    }

    @Test
    fun testTableSerializationRoundTrip() {
        val table = GenUiTable(
            id = "table_1",
            title = "Employees",
            columns = listOf(
                TableColumn(key = "name", title = "Name", alignment = ColumnAlignment.LEFT),
                TableColumn(key = "salary", title = "Salary", alignment = ColumnAlignment.RIGHT, widthWeight = 1.2f)
            ),
            rows = listOf(
                mapOf("name" to "Alice", "salary" to "$120,000"),
                mapOf("name" to "Bob", "salary" to "$95,000")
            ),
            sortable = true,
            pageSize = 10
        )
        val serialized = json.encodeToString(GenUiComponent.serializer(), table)
        val decoded = json.decodeFromString(GenUiComponent.serializer(), serialized)
        assertTrue(decoded is GenUiTable)
        val t = decoded as GenUiTable
        assertEquals(2, t.columns.size)
        assertEquals(2, t.rows.size)
        assertEquals("Alice", t.rows[0]["name"])
    }

    @Test
    fun testFormSerializationWithMultipleFieldTypes() {
        val form = GenUiForm(
            id = "form_1",
            title = "User Survey",
            submitLabel = "Send Feedback",
            fields = listOf(
                GenUiTextInput(fieldId = "f_text", label = "Feedback", required = true, inputType = TextInputType.MULTILINE),
                GenUiSelectInput(fieldId = "f_sel", label = "Category", options = listOf(SelectOption("bug", "Bug Report"), SelectOption("feat", "Feature Request"))),
                GenUiSliderInput(fieldId = "f_rating", label = "Rating", min = 1f, max = 5f, step = 1f, defaultValue = 5f),
                GenUiSwitchInput(fieldId = "f_notify", label = "Email Notifications", defaultValue = true)
            )
        )
        val serialized = json.encodeToString(GenUiComponent.serializer(), form)
        val decoded = json.decodeFromString(GenUiComponent.serializer(), serialized)
        assertTrue(decoded is GenUiForm)
        val f = decoded as GenUiForm
        assertEquals(4, f.fields.size)
        assertTrue(f.fields[0] is GenUiTextInput)
        assertTrue(f.fields[1] is GenUiSelectInput)
        assertTrue(f.fields[2] is GenUiSliderInput)
        assertTrue(f.fields[3] is GenUiSwitchInput)
    }

    @Test
    fun testDiagramSerializationRoundTrip() {
        val diagram = GenUiDiagram(
            id = "diag_1",
            title = "Deployment Pipeline",
            layoutDirection = DiagramDirection.LEFT_TO_RIGHT,
            nodes = listOf(
                DiagramNode(id = "build", label = "Build APK", shape = NodeShape.ROUNDED_RECT, status = NodeStatus.SUCCESS),
                DiagramNode(id = "test", label = "Run Tests", shape = NodeShape.DIAMOND, status = NodeStatus.IN_PROGRESS),
                DiagramNode(id = "deploy", label = "Deploy", shape = NodeShape.PILL, status = NodeStatus.NEUTRAL)
            ),
            edges = listOf(
                DiagramEdge(from = "build", to = "test", label = "on success"),
                DiagramEdge(from = "test", to = "deploy", dashed = true)
            )
        )
        val serialized = json.encodeToString(GenUiComponent.serializer(), diagram)
        val decoded = json.decodeFromString(GenUiComponent.serializer(), serialized)
        assertTrue(decoded is GenUiDiagram)
        val d = decoded as GenUiDiagram
        assertEquals(3, d.nodes.size)
        assertEquals(2, d.edges.size)
        assertEquals(DiagramDirection.LEFT_TO_RIGHT, d.layoutDirection)
    }

    @Test
    fun testUnknownComponentTypeDeserializesToUnsupported() {
        val jsonPayload = """{"type": "quantum_simulator_widget", "id": "qw1", "frequency": 42}"""
        val decoded = json.decodeFromString(GenUiComponent.serializer(), jsonPayload)
        assertTrue(decoded is GenUiUnsupported)
    }

    @Test
    fun testColumnAndRowLayoutContainers() {
        val col = GenUiColumn(
            id = "col_1",
            spacing = 16,
            children = listOf(
                GenUiRow(
                    id = "row_1",
                    spacing = 8,
                    children = listOf(
                        GenUiMetric(id = "m1", label = "A", value = "1"),
                        GenUiMetric(id = "m2", label = "B", value = "2")
                    )
                )
            )
        )
        val serialized = json.encodeToString(GenUiComponent.serializer(), col)
        val decoded = json.decodeFromString(GenUiComponent.serializer(), serialized) as GenUiColumn
        assertEquals(16, decoded.spacing)
        assertEquals(1, decoded.children.size)
        val r = decoded.children[0] as GenUiRow
        assertEquals(2, r.children.size)
    }
}

