package com.mobileai.assistant.genui

import org.junit.Assert.*
import org.junit.Test

class TableSortingLogicTest {

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

    @Test
    fun testNumericCurrencySorting() {
        val rows = listOf(
            mapOf("name" to "Widget A", "price" to "$1,200"),
            mapOf("name" to "Widget B", "price" to "$450"),
            mapOf("name" to "Widget C", "price" to "$3,000"),
            mapOf("name" to "Widget D", "price" to "$99.50")
        )

        val sortedAsc = sortRows(rows, "price", ascending = true)
        assertEquals("Widget D", sortedAsc[0]["name"]) // 99.50
        assertEquals("Widget B", sortedAsc[1]["name"]) // 450
        assertEquals("Widget A", sortedAsc[2]["name"]) // 1200
        assertEquals("Widget C", sortedAsc[3]["name"]) // 3000

        val sortedDesc = sortRows(rows, "price", ascending = false)
        assertEquals("Widget C", sortedDesc[0]["name"]) // 3000
        assertEquals("Widget D", sortedDesc[3]["name"]) // 99.50
    }

    @Test
    fun testAlphabeticalStringSorting() {
        val rows = listOf(
            mapOf("name" to "Charlie"),
            mapOf("name" to "alice"),
            mapOf("name" to "Bob")
        )

        val sorted = sortRows(rows, "name", ascending = true)
        assertEquals("alice", sorted[0]["name"])
        assertEquals("Bob", sorted[1]["name"])
        assertEquals("Charlie", sorted[2]["name"])
    }

    @Test
    fun testNullAndEmptyValueHandling() {
        val rows = listOf(
            mapOf("name" to "Zeta", "score" to "10"),
            mapOf("name" to "Alpha"),
            mapOf("name" to "Beta", "score" to "50")
        )

        val sorted = sortRows(rows, "score", ascending = true)
        assertEquals(3, sorted.size)
        // Missing key treated as empty string, which sorts first or gracefully
        assertEquals("Alpha", sorted[0]["name"])
        assertEquals("Zeta", sorted[1]["name"])
        assertEquals("Beta", sorted[2]["name"])
    }
}

