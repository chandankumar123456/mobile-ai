package com.mobileai.assistant.presentation.components.markdown

/**
 * Abstract Syntax Tree representation of parsed Markdown content.
 */

sealed interface InlineElement {
    data class Text(val text: String) : InlineElement
    data class Bold(val text: String) : InlineElement
    data class Italic(val text: String) : InlineElement
    data class BoldItalic(val text: String) : InlineElement
    data class Strikethrough(val text: String) : InlineElement
    data class InlineCode(val code: String) : InlineElement
    data class Link(val text: String, val url: String) : InlineElement
    data class InlineMath(val expression: String) : InlineElement {
        val formula: String get() = expression
        constructor(formula: String, legacy: Boolean = true) : this(formula)
    }
}

enum class TableAlignment {
    LEFT, CENTER, RIGHT
}

data class TaskItem(
    val text: String,
    val checked: Boolean,
    val elements: List<InlineElement> = emptyList()
)

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val elements: List<InlineElement>) : MarkdownBlock
    data class Paragraph(val elements: List<InlineElement>) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Blockquote(val content: List<InlineElement>) : MarkdownBlock
    data class TaskList(val items: List<TaskItem>) : MarkdownBlock
    data class UnorderedList(val items: List<List<InlineElement>>) : MarkdownBlock
    data class OrderedList(val items: List<Pair<Int, List<InlineElement>>>) : MarkdownBlock
    data class Table(
        val headers: List<List<InlineElement>>,
        val rows: List<List<List<InlineElement>>>,
        val alignments: List<TableAlignment> = emptyList()
    ) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
    data class MathBlock(val expression: String) : MarkdownBlock {
        val formula: String get() = expression
        constructor(formula: String, legacy: Boolean = true) : this(formula)
    }
    data class InlineMath(val expression: String) : MarkdownBlock {
        val formula: String get() = expression
    }
}

typealias MarkdownNode = MarkdownBlock
