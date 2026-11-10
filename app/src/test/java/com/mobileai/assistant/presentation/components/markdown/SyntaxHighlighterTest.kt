package com.mobileai.assistant.presentation.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    @Test
    fun testJsonHighlighting() {
        val json = """
            {
                "key": "value",
                "count": 42,
                "active": true
            }
        """.trimIndent()
        val highlighted = SyntaxHighlighter.highlight(json, "json")
        assertEquals(json, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testKotlinHighlighting() {
        val code = """
            package com.example
            class User(val id: Int, val name: String) {
                fun greet() = println("Hello")
            }
        """.trimIndent()
        val highlighted = SyntaxHighlighter.highlight(code, "kotlin")
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testPythonHighlighting() {
        val code = """
            # A python script
            def add(a: int, b: int) -> int:
                return a + b
        """.trimIndent()
        val highlighted = SyntaxHighlighter.highlight(code, "python")
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testJavaScriptTypeScriptHighlighting() {
        val code = """
            interface Person {
                name: string;
            }
            const greet = (p: Person) => `Hello, ${'$'}{p.name}`;
        """.trimIndent()
        val highlighted = SyntaxHighlighter.highlight(code, "typescript")
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testHtmlXmlHighlighting() {
        val xml = """<div class="container"><p>Hello World</p></div>"""
        val highlighted = SyntaxHighlighter.highlight(xml, "html")
        assertEquals(xml, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testCssHighlighting() {
        val css = """
            .card {
                background-color: #ffffff;
                padding: 16px;
            }
        """.trimIndent()
        val highlighted = SyntaxHighlighter.highlight(css, "css")
        assertEquals(css, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testSqlHighlighting() {
        val sql = "SELECT id, name FROM users WHERE active = 1 ORDER BY id DESC;"
        val highlighted = SyntaxHighlighter.highlight(sql, "sql")
        assertEquals(sql, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testShellHighlighting() {
        val sh = """
            #!/bin/bash
            export API_KEY="secret"
            echo "Running deployment for ${'$'}API_KEY"
        """.trimIndent()
        val highlighted = SyntaxHighlighter.highlight(sh, "bash")
        assertEquals(sh, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testMarkdownHighlighting() {
        val md = "# Heading 1\n**bold** and `code` and [link](https://example.com)"
        val highlighted = SyntaxHighlighter.highlight(md, "markdown")
        assertEquals(md, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testDiffHighlighting() {
        val diff = """
            @@ -1,3 +1,4 @@
            -old line
            +new line
             unchanged line
        """.trimIndent()
        val highlighted = SyntaxHighlighter.highlight(diff, "diff")
        assertEquals(diff, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testBlankAndGenericFallback() {
        val blank = SyntaxHighlighter.highlight("   ", "kotlin")
        assertEquals("   ", blank.text)

        val generic = SyntaxHighlighter.highlight("fn main() { let x = 42; }", "unknown_lang")
        assertEquals("fn main() { let x = 42; }", generic.text)
        assertTrue(generic.spanStyles.isNotEmpty())
    }
}
