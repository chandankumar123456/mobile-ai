package com.mobileai.assistant.data.openui

import com.mobileai.assistant.domain.openui.OpenUiValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenUiLangParserTest {

    @Test
    fun testParseSimpleComponentWithAttributes() {
        val input = """metric(label="Total Revenue", value=14500.5, trend="+12%")"""
        val nodes = OpenUiLangParser.parse(input)

        assertEquals(1, nodes.size)
        val metric = nodes[0]
        assertEquals("metric", metric.tag)
        assertEquals("Total Revenue", metric.getStringAttr("label"))
        assertEquals(14500.5, metric.getNumberAttr("value"), 0.001)
        assertEquals("+12%", metric.getStringAttr("trend"))
    }

    @Test
    fun testParseNestedHierarchy() {
        val input = """
card(title="Server Cluster", style="outlined") {
    column {
        metric(label="Nodes", value=4)
        button(label="Deploy", action="trigger_deploy")
    }
}
        """.trimIndent()

        val nodes = OpenUiLangParser.parse(input)
        assertEquals(1, nodes.size)
        val card = nodes[0]
        assertEquals("card", card.tag)
        assertEquals("Server Cluster", card.getStringAttr("title"))
        assertEquals(1, card.children.size)

        val column = card.children[0]
        assertEquals("column", column.tag)
        assertEquals(2, column.children.size)
        assertEquals("metric", column.children[0].tag)
        assertEquals("button", column.children[1].tag)
        assertEquals("Deploy", column.children[1].getStringAttr("label"))
        assertEquals("trigger_deploy", column.children[1].getStringAttr("action"))
    }

    @Test
    fun testDataBindingResolution() {
        val input = """text(value="{{state.user_name}}")"""
        val nodes = OpenUiLangParser.parse(input)
        assertEquals(1, nodes.size)
        val valueAttr = nodes[0].attributes["value"]
        assertTrue(valueAttr is OpenUiValue.BindingValue)
        assertEquals("state.user_name", (valueAttr as OpenUiValue.BindingValue).path)
    }

    @Test
    fun testTolerantStreamingParsingUnclosedTag() {
        // Incomplete stream: closing brace omitted
        val partialStream = """card(title="Streaming Card") { metric(label="Active", value=99)"""
        val nodes = OpenUiLangParser.parse(partialStream)

        // Must not throw, should parse what is available
        assertTrue(nodes.isNotEmpty())
        assertEquals("card", nodes[0].tag)
        assertEquals("Streaming Card", nodes[0].getStringAttr("title"))
    }
}
