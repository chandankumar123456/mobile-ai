package com.mobileai.assistant.presentation.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathRendererTest {

    @Test
    fun testParseFractions() {
        val node = MathParser.parse("\\frac{1}{2}")
        assertNotNull(node)
        assertTrue(node is MathNode.Fraction)
        val frac = node as MathNode.Fraction
        assertTrue(frac.numerator is MathNode.Text)
        assertEquals("1", (frac.numerator as MathNode.Text).text)
        assertTrue(frac.denominator is MathNode.Text)
        assertEquals("2", (frac.denominator as MathNode.Text).text)

        val complexFrac = MathParser.parse("\\frac{a + b}{c}")
        assertNotNull(complexFrac)
        assertTrue(complexFrac is MathNode.Fraction)
    }

    @Test
    fun testParseSquareRoots() {
        val simpleSqrt = MathParser.parse("\\sqrt{x}")
        assertNotNull(simpleSqrt)
        assertTrue(simpleSqrt is MathNode.Sqrt)
        val sqrt = simpleSqrt as MathNode.Sqrt
        assertNull(sqrt.rootIndex)
        assertTrue(sqrt.radicand is MathNode.Text)
        assertEquals("x", (sqrt.radicand as MathNode.Text).text)

        val indexedSqrt = MathParser.parse("\\sqrt[3]{8}")
        assertNotNull(indexedSqrt)
        assertTrue(indexedSqrt is MathNode.Sqrt)
        val indexed = indexedSqrt as MathNode.Sqrt
        assertNotNull(indexed.rootIndex)
        assertTrue(indexed.radicand is MathNode.Text)
        assertEquals("8", (indexed.radicand as MathNode.Text).text)
    }

    @Test
    fun testParseSubAndSuperscripts() {
        val superNode = MathParser.parse("x^2")
        assertNotNull(superNode)
        assertTrue(superNode is MathNode.SuperScript)
        val sup = superNode as MathNode.SuperScript
        assertEquals("x", (sup.base as MathNode.Text).text)
        assertEquals("2", (sup.exponent as MathNode.Text).text)

        val subNode = MathParser.parse("x_i")
        assertNotNull(subNode)
        assertTrue(subNode is MathNode.SubScript)
        val sub = subNode as MathNode.SubScript
        assertEquals("x", (sub.base as MathNode.Text).text)
        assertEquals("i", (sub.subscript as MathNode.Text).text)

        val subSupNode = MathParser.parse("x_i^2")
        assertNotNull(subSupNode)
        assertTrue(subSupNode is MathNode.SubSuperScript)
        val subSup = subSupNode as MathNode.SubSuperScript
        assertEquals("x", (subSup.base as MathNode.Text).text)
        assertEquals("i", (subSup.sub as MathNode.Text).text)
        assertEquals("2", (subSup.sup as MathNode.Text).text)
    }

    @Test
    fun testParseSymbolsAndGreek() {
        val alphaNode = MathParser.parse("\\alpha")
        assertNotNull(alphaNode)
        assertTrue(alphaNode is MathNode.Symbol)
        assertEquals("α", (alphaNode as MathNode.Symbol).unicode)

        val expr = MathParser.parse("\\sum_{i=1}^{\\infty} x_i \\le C")
        assertNotNull(expr)
        assertTrue(expr is MathNode.Group)
    }

    @Test
    fun testFallbackBehavior() {
        assertNull(MathParser.parse(""))
        assertNull(MathParser.parse("   "))

        // Malformed unclosed brace should not throw exception
        val malformed = MathParser.parse("\\frac{1}{2")
        assertNotNull(malformed) // Gracefully returns parsed structure without throwing
    }
}
