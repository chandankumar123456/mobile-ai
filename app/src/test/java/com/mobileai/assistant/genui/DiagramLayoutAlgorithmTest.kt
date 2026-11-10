package com.mobileai.assistant.genui

import com.mobileai.assistant.domain.genui.DiagramDirection
import com.mobileai.assistant.domain.genui.DiagramEdge
import com.mobileai.assistant.domain.genui.DiagramNode
import com.mobileai.assistant.presentation.components.genui.computeTopologicalLayout
import org.junit.Assert.*
import org.junit.Test

class DiagramLayoutAlgorithmTest {

    @Test
    fun testLinearChainTopologicalOrder() {
        val nodes = listOf(
            DiagramNode(id = "step1", label = "Step 1"),
            DiagramNode(id = "step2", label = "Step 2"),
            DiagramNode(id = "step3", label = "Step 3")
        )
        val edges = listOf(
            DiagramEdge(from = "step1", to = "step2"),
            DiagramEdge(from = "step2", to = "step3")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(3, layouts.size)

        val l1 = layouts.find { it.node.id == "step1" }!!.bounds
        val l2 = layouts.find { it.node.id == "step2" }!!.bounds
        val l3 = layouts.find { it.node.id == "step3" }!!.bounds

        assertTrue(l1.bottom < l2.top)
        assertTrue(l2.bottom < l3.top)
    }

    @Test
    fun testBranchingDagTopologicalOrder() {
        // A -> B, A -> C, B -> D, C -> D
        val nodes = listOf(
            DiagramNode(id = "A", label = "Start"),
            DiagramNode(id = "B", label = "Branch 1"),
            DiagramNode(id = "C", label = "Branch 2"),
            DiagramNode(id = "D", label = "Merge")
        )
        val edges = listOf(
            DiagramEdge(from = "A", to = "B"),
            DiagramEdge(from = "A", to = "C"),
            DiagramEdge(from = "B", to = "D"),
            DiagramEdge(from = "C", to = "D")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(4, layouts.size)

        val lA = layouts.find { it.node.id == "A" }!!.bounds
        val lB = layouts.find { it.node.id == "B" }!!.bounds
        val lC = layouts.find { it.node.id == "C" }!!.bounds
        val lD = layouts.find { it.node.id == "D" }!!.bounds

        assertTrue(lA.bottom < lB.top)
        assertTrue(lA.bottom < lC.top)
        assertTrue(lB.bottom < lD.top)
        assertTrue(lC.bottom < lD.top)

        // B and C are in same layer (same Y coordinate)
        assertEquals(lB.top, lC.top, 0.01f)
    }

    @Test
    fun testCyclicGraphDoesNotLoopInfinitely() {
        // Cycle: A -> B -> C -> A
        val nodes = listOf(
            DiagramNode(id = "A", label = "A"),
            DiagramNode(id = "B", label = "B"),
            DiagramNode(id = "C", label = "C")
        )
        val edges = listOf(
            DiagramEdge(from = "A", to = "B"),
            DiagramEdge(from = "B", to = "C"),
            DiagramEdge(from = "C", to = "A")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.TOP_TO_BOTTOM)
        assertEquals(3, layouts.size)
        // All nodes received valid positions
        layouts.forEach { nl ->
            assertTrue(nl.bounds.width > 0)
            assertTrue(nl.bounds.height > 0)
        }
    }

    @Test
    fun testLeftToRightDirectionLayout() {
        val nodes = listOf(
            DiagramNode(id = "start", label = "Start"),
            DiagramNode(id = "end", label = "End")
        )
        val edges = listOf(
            DiagramEdge(from = "start", to = "end")
        )

        val layouts = computeTopologicalLayout(nodes, edges, DiagramDirection.LEFT_TO_RIGHT)
        assertEquals(2, layouts.size)

        val lStart = layouts.find { it.node.id == "start" }!!.bounds
        val lEnd = layouts.find { it.node.id == "end" }!!.bounds

        assertTrue(lStart.right < lEnd.left)
    }
}

