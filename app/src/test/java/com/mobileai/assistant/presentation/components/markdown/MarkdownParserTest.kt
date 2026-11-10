package com.mobileai.assistant.presentation.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testHeadingsParsing() {
        val md = "# Heading 1\n## Heading 2\n### Heading 3"
        val blocks = MarkdownParser.parse(md)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals(2, (blocks[1] as MarkdownBlock.Heading).level)
        assertEquals(3, (blocks[2] as MarkdownBlock.Heading).level)
    }

    @Test
    fun testPartialAndInvalidHeadingsDoNotInfiniteLoop() {
        // Critical bug test: malformed or streaming headings without spaces must NOT loop indefinitely!
        val testInputs = listOf(
            "#",
            "##",
            "###",
            "#tag",
            "##notAHeading",
            "#\n##\n###\n#tag\nNormal paragraph"
        )

        for (input in testInputs) {
            val blocks = MarkdownParser.parse(input)
            assertTrue(blocks.isNotEmpty())
        }
    }

    @Test
    fun testCodeBlockWithStreamingUnclosedFence() {
        // Simulates token streaming where closing ``` hasn't arrived yet
        val streamingMd = "```kotlin\nval x = 42\nprintln(x)"
        val blocks = MarkdownParser.parse(streamingMd)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.CodeBlock)
        val codeBlock = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals("val x = 42\nprintln(x)", codeBlock.code)
    }

    @Test
    fun testClosedCodeBlock() {
        val md = "```python\ndef hello():\n    return 'world'\n```"
        val blocks = MarkdownParser.parse(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.CodeBlock)
        val codeBlock = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("python", codeBlock.language)
        assertEquals("def hello():\n    return 'world'", codeBlock.code)
    }

    @Test
    fun testUnorderedAndOrderedLists() {
        val md = "- Item A\n- Item B\n\n1. Step One\n2. Step Two"
        val blocks = MarkdownParser.parse(md)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.UnorderedList)
        assertEquals(2, (blocks[0] as MarkdownBlock.UnorderedList).items.size)
        assertTrue(blocks[1] is MarkdownBlock.OrderedList)
        assertEquals(2, (blocks[1] as MarkdownBlock.OrderedList).items.size)
    }

    @Test
    fun testTaskListParsing() {
        val md = """
            - [ ] Buy groceries
            - [x] Write unit tests
            * [X] Review pull request
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.TaskList)
        val taskList = blocks[0] as MarkdownBlock.TaskList
        assertEquals(3, taskList.items.size)
        assertEquals("Buy groceries", taskList.items[0].text)
        assertFalse(taskList.items[0].checked)
        assertEquals("Write unit tests", taskList.items[1].text)
        assertTrue(taskList.items[1].checked)
        assertEquals("Review pull request", taskList.items[2].text)
        assertTrue(taskList.items[2].checked)
    }

    @Test
    fun testTableWithEscapedPipesAndAlignment() {
        val md = """
            | Left Column | Center Column | Right Column |
            | :--- | :---: | ---: |
            | Value 1 | Center \| Escaped | $100 |
            | Cell 4 | Normal | Cell 6 |
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Table)
        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(3, table.headers.size)
        assertEquals(2, table.rows.size)
        assertEquals(3, table.alignments.size)
        assertEquals(TableAlignment.LEFT, table.alignments[0])
        assertEquals(TableAlignment.CENTER, table.alignments[1])
        assertEquals(TableAlignment.RIGHT, table.alignments[2])

        // Verify escaped pipe in cell
        val escapedCell = table.rows[0][1]
        val text = escapedCell.filterIsInstance<InlineElement.Text>().joinToString("") { it.text }
        assertTrue(text.contains("Center | Escaped"))
    }

    @Test
    fun testMathBlockAndInlines() {
        val md = "$$ E = mc^2 $$"
        val blocks = MarkdownParser.parse(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.MathBlock)
        assertEquals("E = mc^2", (blocks[0] as MarkdownBlock.MathBlock).formula)

        val inlines = MarkdownParser.parseInlines("Formula: \$x^2\$ and inline `code` and **bold**")
        assertTrue(inlines.any { it is InlineElement.InlineMath })
        assertTrue(inlines.any { it is InlineElement.InlineCode })
        assertTrue(inlines.any { it is InlineElement.Bold })
    }

    @Test
    fun testMathBlockBracketSyntax() {
        // Single-line \[ ... \]
        val md1 = "\\[ \\frac{1}{2} \\]"
        val blocks1 = MarkdownParser.parse(md1)
        assertEquals(1, blocks1.size)
        assertTrue(blocks1[0] is MarkdownBlock.MathBlock)
        assertEquals("\\frac{1}{2}", (blocks1[0] as MarkdownBlock.MathBlock).formula)

        // Multi-line \[ ... \]
        val md2 = "\\[\n\\sqrt{x}\n\\]"
        val blocks2 = MarkdownParser.parse(md2)
        assertEquals(1, blocks2.size)
        assertTrue(blocks2[0] is MarkdownBlock.MathBlock)
        assertEquals("\\sqrt{x}", (blocks2[0] as MarkdownBlock.MathBlock).formula)
    }

    @Test
    fun testInlineMathAndCurrencyExclusion() {
        // Currency should NOT be parsed as inline math
        val currencyText = "The total is $100 and the discount is $20."
        val inlines1 = MarkdownParser.parseInlines(currencyText)
        assertFalse(inlines1.any { it is InlineElement.InlineMath })

        // Escaped dollar
        val escapedDollar = "Price: \\$50 each"
        val inlines2 = MarkdownParser.parseInlines(escapedDollar)
        assertFalse(inlines2.any { it is InlineElement.InlineMath })

        // LaTeX \( ... \)
        val latexInlines = MarkdownParser.parseInlines("Evaluate \\(x + y = z\\) now")
        assertTrue(latexInlines.any { it is InlineElement.InlineMath })
        val math = latexInlines.first { it is InlineElement.InlineMath } as InlineElement.InlineMath
        assertEquals("x + y = z", math.expression)
    }

    @Test
    fun testNestedInlineEmphasis() {
        val text1 = "**bold and *italic* inside**"
        val inlines1 = MarkdownParser.parseInlines(text1)
        assertTrue(inlines1.any { it is InlineElement.BoldItalic })

        val text2 = "*italic and **bold** inside*"
        val inlines2 = MarkdownParser.parseInlines(text2)
        assertTrue(inlines2.any { it is InlineElement.BoldItalic })

        val text3 = "***bold and italic***"
        val inlines3 = MarkdownParser.parseInlines(text3)
        assertEquals(1, inlines3.size)
        assertTrue(inlines3[0] is InlineElement.BoldItalic)
    }

    @Test
    fun testStreamingUnclosedInlinesDoNotCrash() {
        // Stream cut off halfway through bold or code
        val inlines1 = MarkdownParser.parseInlines("This is **unclosed bold")
        assertTrue(inlines1.any { it is InlineElement.Bold })

        val inlines2 = MarkdownParser.parseInlines("This is `unclosed code")
        assertTrue(inlines2.any { it is InlineElement.InlineCode })
    }

    @Test
    fun testMathBlockImmediatelyAfterParagraph() {
        val md = "Formula for energy:\n$$ E = mc^2 $$"
        val blocks = MarkdownParser.parse(md)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.MathBlock)
        assertEquals("E = mc^2", (blocks[1] as MarkdownBlock.MathBlock).formula)
    }

    @Test
    fun testStreamingUnorderedListItem() {
        // While user or LLM is typing a bullet
        val blocks = MarkdownParser.parse("- ")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.UnorderedList)
    }
}
