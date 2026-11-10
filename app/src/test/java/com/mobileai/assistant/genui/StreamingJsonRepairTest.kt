package com.mobileai.assistant.genui

import com.mobileai.assistant.data.genui.StreamingJsonRepair
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class StreamingJsonRepairTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testEmptyAndBlankInputs() {
        assertEquals("{}", StreamingJsonRepair.repair(""))
        assertEquals("{}", StreamingJsonRepair.repair("   "))
        assertEquals("{}", StreamingJsonRepair.repair("\n\t"))
    }

    @Test
    fun testAutoClosesUnclosedStringLiteral() {
        val partial = """{"type": "ca"""
        val repaired = StreamingJsonRepair.repair(partial)
        assertEquals("""{"type": "ca"}""", repaired)
        val obj = json.parseToJsonElement(repaired).jsonObject
        assertNotNull(obj)
    }

    @Test
    fun testDropsDanglingKeyAndColon() {
        val partial = """{"type": "card", "title": """
        val repaired = StreamingJsonRepair.repair(partial)
        assertEquals("""{"type": "card"}""", repaired)
        val obj = json.parseToJsonElement(repaired).jsonObject
        assertNotNull(obj)
    }

    @Test
    fun testDropsDanglingUnpairedKeyWithoutColon() {
        val partial = """{"type": "card", "sub"""
        val repaired = StreamingJsonRepair.repair(partial)
        assertEquals("""{"type": "card"}""", repaired)
        val obj = json.parseToJsonElement(repaired).jsonObject
        assertNotNull(obj)
    }

    @Test
    fun testBalancesNestedArraysAndObjectsInLifoOrder() {
        val partial = """{"type": "chart", "series": [{"name": "Sales", "data": [{"x": "Jan", "y": 10"""
        val repaired = StreamingJsonRepair.repair(partial)
        val obj = json.parseToJsonElement(repaired).jsonObject
        assertNotNull(obj)
        assertEquals("chart", obj["type"]?.toString()?.replace("\"", ""))
    }

    @Test
    fun testStripsDanglingCommaInArrayAndObject() {
        val partial1 = """{"values": [1, 2, """
        val repaired1 = StreamingJsonRepair.repair(partial1)
        assertEquals("""{"values": [1, 2]}""", repaired1)

        val partial2 = """{"a": 1, "b": 2, """
        val repaired2 = StreamingJsonRepair.repair(partial2)
        assertEquals("""{"a": 1, "b": 2}""", repaired2)
    }

    @Test
    fun testRepairsIncompleteNumericLiterals() {
        val partial1 = """{"rate": 45."""
        val repaired1 = StreamingJsonRepair.repair(partial1)
        assertEquals("""{"rate": 45}""", repaired1)

        val partial2 = """{"diff": -"""
        val repaired2 = StreamingJsonRepair.repair(partial2)
        val obj = json.parseToJsonElement(repaired2).jsonObject
        assertNotNull(obj)
    }

    @Test
    fun testHandlesEscapedCharactersInStrings() {
        val partial = """{"message": "Hello \"World"""
        val repaired = StreamingJsonRepair.repair(partial)
        assertEquals("""{"message": "Hello \"World"}""", repaired)
        val obj = json.parseToJsonElement(repaired).jsonObject
        assertNotNull(obj)
    }

    @Test
    fun testComplexMultiLevelStructure() {
        val partial = """{"type":"card","title":"Dashboard","children":[{"type":"column","spacing":12,"children":[{"type":"metric","label":"Visits","value":"1,200"""
        val repaired = StreamingJsonRepair.repair(partial)
        val obj = json.parseToJsonElement(repaired).jsonObject
        assertNotNull(obj)
    }
}

