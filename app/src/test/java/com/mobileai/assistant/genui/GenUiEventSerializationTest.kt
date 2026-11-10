package com.mobileai.assistant.genui

import com.mobileai.assistant.domain.genui.GenUiEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GenUiEventSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testFormSubmitEventSerialization() {
        val event = GenUiEvent.FormSubmit(
            formId = "survey_form",
            actionId = "submit_survey",
            fieldValues = mapOf("name" to "John", "rating" to "5")
        )
        val serialized = json.encodeToString(GenUiEvent.serializer(), event)
        assertTrue(serialized.contains("\"eventType\":\"form_submit\""))
        assertTrue(serialized.contains("\"formId\":\"survey_form\""))

        val decoded = json.decodeFromString(GenUiEvent.serializer(), serialized)
        assertTrue(decoded is GenUiEvent.FormSubmit)
        val fs = decoded as GenUiEvent.FormSubmit
        assertEquals("survey_form", fs.formId)
        assertEquals("submit_survey", fs.actionId)
        assertEquals("John", fs.fieldValues["name"])
        assertEquals("5", fs.fieldValues["rating"])
    }

    @Test
    fun testButtonClickEventSerialization() {
        val event = GenUiEvent.ButtonClick(
            actionId = "refresh_data",
            payload = mapOf("force" to "true"),
            componentId = "btn_refresh"
        )
        val serialized = json.encodeToString(GenUiEvent.serializer(), event)
        assertTrue(serialized.contains("\"eventType\":\"button_click\""))

        val decoded = json.decodeFromString(GenUiEvent.serializer(), serialized)
        assertTrue(decoded is GenUiEvent.ButtonClick)
        val bc = decoded as GenUiEvent.ButtonClick
        assertEquals("refresh_data", bc.actionId)
        assertEquals("true", bc.payload["force"])
    }

    @Test
    fun testSelectionChangeEventSerialization() {
        val event = GenUiEvent.SelectionChange(
            selectedKey = "tab_q3",
            selectedLabel = "Quarter 3",
            componentId = "quarter_tabs"
        )
        val serialized = json.encodeToString(GenUiEvent.serializer(), event)
        assertTrue(serialized.contains("\"eventType\":\"selection_change\""))

        val decoded = json.decodeFromString(GenUiEvent.serializer(), serialized)
        assertTrue(decoded is GenUiEvent.SelectionChange)
        val sc = decoded as GenUiEvent.SelectionChange
        assertEquals("tab_q3", sc.selectedKey)
        assertEquals("Quarter 3", sc.selectedLabel)
        assertEquals("quarter_tabs", sc.componentId)
    }

    @Test
    fun testDiagramNodeClickEventSerialization() {
        val event = GenUiEvent.DiagramNodeClick(
            diagramId = "arch_diag",
            nodeId = "node_db",
            nodeLabel = "PostgreSQL DB"
        )
        val serialized = json.encodeToString(GenUiEvent.serializer(), event)
        assertTrue(serialized.contains("\"eventType\":\"diagram_node_click\""))

        val decoded = json.decodeFromString(GenUiEvent.serializer(), serialized)
        assertTrue(decoded is GenUiEvent.DiagramNodeClick)
        val dnc = decoded as GenUiEvent.DiagramNodeClick
        assertEquals("arch_diag", dnc.diagramId)
        assertEquals("node_db", dnc.nodeId)
        assertEquals("PostgreSQL DB", dnc.nodeLabel)
    }
}

