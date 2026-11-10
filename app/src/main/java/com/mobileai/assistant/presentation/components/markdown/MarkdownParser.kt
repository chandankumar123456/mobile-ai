package com.mobileai.assistant.presentation.components.markdown

/**
 * Robust, stream-safe Markdown parser that parses text into [MarkdownBlock] elements.
 * Handles incomplete tokens, unclosed code fences, table alignment, task lists,
 * math blocks, inline math, and streaming partial content gracefully without hanging.
 */
object MarkdownParser {

    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.*)$")
    private val TASK_LIST_REGEX = Regex("^[-*+]\\s+\\[([ xX])\\]\\s*(.*)$")
    private val HR_REGEX = Regex("^([-*_])\\s*\\1\\s*\\1(\\s*\\1)*$")

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isEmpty()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // 1. Check for fenced code block start: ```lang
            if (line.trimStart().startsWith("```")) {
                val indent = line.indexOf("```")
                val fenceInfo = line.substring(indent + 3).trim()
                val language = fenceInfo.takeWhile { !it.isWhitespace() }
                val codeLines = mutableListOf<String>()
                i++

                while (i < lines.size) {
                    val codeLine = lines[i]
                    if (codeLine.trimStart().startsWith("```")) {
                        i++
                        break
                    }
                    codeLines.add(codeLine)
                    i++
                }

                // Stream safety: even if unclosed, emit the code block
                blocks.add(MarkdownBlock.CodeBlock(language = language, code = codeLines.joinToString("\n")))
                continue
            }

            // 2. Check for display math blocks: $$ or \[ ... \]
            val trimmedLine = line.trim()

            // Single line display math: $$...$$
            if (trimmedLine.startsWith("$$") && trimmedLine.endsWith("$$") && trimmedLine.length > 4) {
                val formula = trimmedLine.removePrefix("$$").removeSuffix("$$").trim()
                blocks.add(MarkdownBlock.MathBlock(formula))
                i++
                continue
            }

            // Single line display math: \[...\]
            if (trimmedLine.startsWith("\\[") && trimmedLine.endsWith("\\]") && trimmedLine.length >= 4) {
                val formula = trimmedLine.removePrefix("\\[").removeSuffix("\\]").trim()
                blocks.add(MarkdownBlock.MathBlock(formula))
                i++
                continue
            }

            // Multi-line display math: $$
            if (trimmedLine == "$$" || (trimmedLine.startsWith("$$") && !trimmedLine.endsWith("$$"))) {
                val mathLines = mutableListOf<String>()
                val initialContent = trimmedLine.removePrefix("$$").trim()
                if (initialContent.isNotEmpty()) {
                    mathLines.add(initialContent)
                }
                i++
                while (i < lines.size) {
                    val mathLine = lines[i]
                    if (mathLine.trim().endsWith("$$")) {
                        val endContent = mathLine.trim().removeSuffix("$$").trim()
                        if (endContent.isNotEmpty()) mathLines.add(endContent)
                        i++
                        break
                    }
                    mathLines.add(mathLine)
                    i++
                }
                blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
                continue
            }

            // Multi-line display math: \[
            if (trimmedLine == "\\[" || (trimmedLine.startsWith("\\[") && !trimmedLine.endsWith("\\]"))) {
                val mathLines = mutableListOf<String>()
                val initialContent = trimmedLine.removePrefix("\\[").trim()
                if (initialContent.isNotEmpty()) {
                    mathLines.add(initialContent)
                }
                i++
                while (i < lines.size) {
                    val mathLine = lines[i]
                    if (mathLine.trim().endsWith("\\]")) {
                        val endContent = mathLine.trim().removeSuffix("\\]").trim()
                        if (endContent.isNotEmpty()) mathLines.add(endContent)
                        i++
                        break
                    }
                    mathLines.add(mathLine)
                    i++
                }
                blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
                continue
            }

            // 3. Horizontal Rule: ---, ***, ___
            if (trimmedLine.matches(HR_REGEX) && trimmedLine.length >= 3) {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // 4. Headings: # to ###### (Requires space after '#')
            val headingMatch = HEADING_REGEX.matchEntire(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val content = headingMatch.groupValues[2]
                blocks.add(MarkdownBlock.Heading(level = level, elements = parseInlines(content)))
                i++
                continue
            }

            // 5. Blockquotes: > ...
            if (line.trimStart().startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    val text = lines[i].trimStart().removePrefix(">").trimStart()
                    quoteLines.add(text)
                    i++
                }
                blocks.add(MarkdownBlock.Blockquote(parseInlines(quoteLines.joinToString(" "))))
                continue
            }

            // 6. Tables: starts with | and followed by delimiter row
            if (isTableRow(line) && i + 1 < lines.size && isTableDelimiter(lines[i + 1])) {
                val headers = parseTableRow(line)
                val alignments = parseAlignments(lines[i + 1])
                i += 2 // skip header and delimiter

                val rows = mutableListOf<List<List<InlineElement>>>()
                while (i < lines.size && isTableRow(lines[i])) {
                    rows.add(parseTableRow(lines[i]))
                    i++
                }
                blocks.add(MarkdownBlock.Table(headers = headers, rows = rows, alignments = alignments))
                continue
            }

            // 7. Task list: - [ ] item or - [x] item
            if (isTaskListItem(line)) {
                val items = mutableListOf<TaskItem>()
                while (i < lines.size && isTaskListItem(lines[i])) {
                    val match = TASK_LIST_REGEX.matchEntire(lines[i].trimStart())!!
                    val checked = match.groupValues[1].equals("x", ignoreCase = true)
                    val itemText = match.groupValues[2].trim()
                    items.add(TaskItem(text = itemText, checked = checked, elements = parseInlines(itemText)))
                    i++
                }
                blocks.add(MarkdownBlock.TaskList(items))
                continue
            }

            // 8. Unordered list: - , * , +
            if (isUnorderedListItem(line)) {
                val items = mutableListOf<List<InlineElement>>()
                while (i < lines.size && isUnorderedListItem(lines[i])) {
                    val trimmed = lines[i].trim()
                    val itemText = if (trimmed.length > 2) trimmed.substring(2).trim() else ""
                    items.add(parseInlines(itemText))
                    i++
                }
                blocks.add(MarkdownBlock.UnorderedList(items))
                continue
            }

            // 9. Ordered list: 1. , 2.
            if (isOrderedListItem(line)) {
                val items = mutableListOf<Pair<Int, List<InlineElement>>>()
                while (i < lines.size && isOrderedListItem(lines[i])) {
                    val dotIdx = lines[i].indexOf('.')
                    val num = lines[i].trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                    val itemText = lines[i].substring(dotIdx + 1).trim()
                    items.add(Pair(num, parseInlines(itemText)))
                    i++
                }
                blocks.add(MarkdownBlock.OrderedList(items))
                continue
            }

            // 10. Blank line: skip
            if (line.isBlank()) {
                i++
                continue
            }

            // 11. Paragraph: collect consecutive non-empty regular lines
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size &&
                !lines[i].isBlank() &&
                !lines[i].trimStart().startsWith("```") &&
                !isMathBlockStart(lines[i]) &&
                !isValidHeading(lines[i]) &&
                !lines[i].trimStart().startsWith(">") &&
                !isTaskListItem(lines[i]) &&
                !isUnorderedListItem(lines[i]) &&
                !isOrderedListItem(lines[i]) &&
                !(isTableRow(lines[i]) && i + 1 < lines.size && isTableDelimiter(lines[i + 1])) &&
                !lines[i].trim().matches(HR_REGEX)
            ) {
                paragraphLines.add(lines[i])
                i++
            }
            if (paragraphLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(parseInlines(paragraphLines.joinToString("\n"))))
            } else if (i < lines.size) {
                // Guaranteed forward progress: if line was not recognized or is a malformed/partial heading
                // (e.g. "#tag" or lone "#"), emit as paragraph and ALWAYS advance i!
                blocks.add(MarkdownBlock.Paragraph(parseInlines(lines[i])))
                i++
            }
        }

        return blocks
    }

    private fun isMathBlockStart(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("$$") || trimmed.startsWith("\\[")
    }

    private fun isValidHeading(line: String): Boolean {
        return HEADING_REGEX.matches(line)
    }

    private fun isTaskListItem(line: String): Boolean {
        return TASK_LIST_REGEX.matches(line.trimStart())
    }

    private fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("|")) return false
        var pipes = 0
        var idx = 0
        while (idx < trimmed.length) {
            if (trimmed[idx] == '\\' && idx + 1 < trimmed.length && trimmed[idx + 1] == '|') {
                idx += 2
            } else {
                if (trimmed[idx] == '|') pipes++
                idx++
            }
        }
        return pipes >= 2
    }

    private fun isTableDelimiter(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("|")) return false
        val cells = splitTableCells(trimmed).filter { it.isNotBlank() }
        return cells.isNotEmpty() && cells.all { cell ->
            cell.trim().matches(Regex("^:?-+:?$"))
        }
    }

    private fun parseTableRow(line: String): List<List<InlineElement>> {
        val cells = splitTableCells(line)
        return cells.map { parseInlines(it) }
    }

    fun splitTableCells(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) trimmed = trimmed.substring(0, trimmed.length - 1)

        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var idx = 0
        while (idx < trimmed.length) {
            if (trimmed[idx] == '\\' && idx + 1 < trimmed.length && trimmed[idx + 1] == '|') {
                current.append('|')
                idx += 2
            } else if (trimmed[idx] == '|') {
                cells.add(current.toString().trim())
                current.clear()
                idx++
            } else {
                current.append(trimmed[idx])
                idx++
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun parseAlignments(delimiterLine: String): List<TableAlignment> {
        val cells = splitTableCells(delimiterLine).filter { it.isNotBlank() }
        return cells.map { cell ->
            val trimmed = cell.trim()
            val left = trimmed.startsWith(":")
            val right = trimmed.endsWith(":")
            when {
                left && right -> TableAlignment.CENTER
                right -> TableAlignment.RIGHT
                else -> TableAlignment.LEFT
            }
        }
    }

    private fun isUnorderedListItem(line: String): Boolean {
        val trimmed = line.trimStart()
        return (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ||
                trimmed == "-" || trimmed == "*" || trimmed == "+")
    }

    private fun isOrderedListItem(line: String): Boolean {
        val trimmed = line.trimStart()
        val match = Regex("^\\d+\\.\\s*(.*)").matchEntire(trimmed)
        return match != null
    }

    /**
     * Parses inline formatting:
     * - Inline code: `code`
     * - Inline math: \(formula\) or $formula$ (excluding currency like $100)
     * - Bold + Italic: ***text*** or nested bold/italic (**bold *italic***)
     * - Bold: **text** or __text__
     * - Italic: *text* or _text_
     * - Strikethrough: ~~text~~
     * - Link: [text](url)
     * Handles unclosed tokens gracefully during streaming.
     */
    fun parseInlines(text: String): List<InlineElement> {
        if (text.isEmpty()) return emptyList()

        val elements = mutableListOf<InlineElement>()
        var cursor = 0
        val len = text.length

        while (cursor < len) {
            // Escaped dollar: \$
            if (text[cursor] == '\\' && cursor + 1 < len && text[cursor + 1] == '$') {
                appendOrMergeText(elements, "$")
                cursor += 2
                continue
            }

            // 1. Inline code: `...`
            if (text[cursor] == '`') {
                val closeIdx = text.indexOf('`', cursor + 1)
                if (closeIdx != -1) {
                    val code = text.substring(cursor + 1, closeIdx)
                    elements.add(InlineElement.InlineCode(code))
                    cursor = closeIdx + 1
                    continue
                } else {
                    // Streaming unclosed inline code
                    val code = text.substring(cursor + 1)
                    elements.add(InlineElement.InlineCode(code))
                    break
                }
            }

            // 2. LaTeX inline math: \( ... \)
            if (cursor + 1 < len && text.startsWith("\\(", cursor)) {
                val closeIdx = text.indexOf("\\)", cursor + 2)
                if (closeIdx != -1) {
                    val formula = text.substring(cursor + 2, closeIdx).trim()
                    elements.add(InlineElement.InlineMath(formula))
                    cursor = closeIdx + 2
                    continue
                }
            }

            // 3. Inline math: $...$
            if (text[cursor] == '$') {
                val insideWord = cursor > 0 && text[cursor - 1].isLetterOrDigit()
                if (!insideWord && cursor + 1 < len && text[cursor + 1] != '$' && !text[cursor + 1].isWhitespace()) {
                    val closeIdx = text.indexOf('$', cursor + 1)
                    if (closeIdx != -1 && closeIdx > cursor + 1 && !text[closeIdx - 1].isWhitespace()) {
                        val candidate = text.substring(cursor + 1, closeIdx)
                        // Avoid currency false positives like $100 or $10.50
                        val isCurrency = candidate.matches(Regex("^[0-9]+([.,][0-9]+)*$"))
                        if (!isCurrency) {
                            elements.add(InlineElement.InlineMath(candidate))
                            cursor = closeIdx + 1
                            continue
                        }
                    }
                }
            }

            // 4. Strikethrough: ~~...~~
            if (cursor + 1 < len && text.startsWith("~~", cursor)) {
                val closeIdx = text.indexOf("~~", cursor + 2)
                if (closeIdx != -1) {
                    val strikeText = text.substring(cursor + 2, closeIdx)
                    elements.add(InlineElement.Strikethrough(strikeText))
                    cursor = closeIdx + 2
                    continue
                }
            }

            // 5. Bold + Italic: ***...*** or ___...___
            if (cursor + 2 < len && (text.startsWith("***", cursor) || text.startsWith("___", cursor))) {
                val marker = text.substring(cursor, cursor + 3)
                val closeIdx = text.indexOf(marker, cursor + 3)
                if (closeIdx != -1) {
                    val content = text.substring(cursor + 3, closeIdx)
                    elements.add(InlineElement.BoldItalic(content))
                    cursor = closeIdx + 3
                    continue
                }
            }

            // 6. Bold: **...** or __...__ (with nested italic support)
            if (cursor + 1 < len && (text.startsWith("**", cursor) || text.startsWith("__", cursor))) {
                val marker = text.substring(cursor, cursor + 2)
                val closeIdx = text.indexOf(marker, cursor + 2)
                if (closeIdx != -1) {
                    val content = text.substring(cursor + 2, closeIdx)
                    elements.addAll(parseBoldContent(content))
                    cursor = closeIdx + 2
                    continue
                } else {
                    // Streaming unclosed bold: show as bold up to end
                    val content = text.substring(cursor + 2)
                    elements.addAll(parseBoldContent(content))
                    break
                }
            }

            // 7. Italic: *...* or _..._ (with nested bold support)
            if ((text[cursor] == '*' || text[cursor] == '_') && cursor + 1 < len && !text[cursor + 1].isWhitespace()) {
                val marker = text[cursor]
                val closeIdx = findClosingItalic(text, cursor, marker)
                if (closeIdx != -1 && closeIdx > cursor + 1 && !text[closeIdx - 1].isWhitespace()) {
                    val content = text.substring(cursor + 1, closeIdx)
                    elements.addAll(parseItalicContent(content))
                    cursor = closeIdx + 1
                    continue
                }
            }

            // 8. Link: [text](url)
            if (text[cursor] == '[') {
                val closeBracket = text.indexOf(']', cursor + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val linkText = text.substring(cursor + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        elements.add(InlineElement.Link(text = linkText, url = url))
                        cursor = closeParen + 1
                        continue
                    }
                }
            }

            // Regular plain text chunk
            val nextSpecial = findNextSpecialChar(text, cursor)
            val chunk = if (nextSpecial == -1) {
                val str = text.substring(cursor)
                cursor = len
                str
            } else if (nextSpecial == cursor) {
                val ch = text[cursor].toString()
                cursor++
                ch
            } else {
                val str = text.substring(cursor, nextSpecial)
                cursor = nextSpecial
                str
            }

            appendOrMergeText(elements, chunk)
        }

        return elements
    }

    private fun appendOrMergeText(elements: MutableList<InlineElement>, text: String) {
        if (text.isEmpty()) return
        if (elements.isNotEmpty() && elements.last() is InlineElement.Text) {
            val prev = elements.removeAt(elements.size - 1) as InlineElement.Text
            elements.add(InlineElement.Text(prev.text + text))
        } else {
            elements.add(InlineElement.Text(text))
        }
    }

    private fun parseBoldContent(content: String): List<InlineElement> {
        // If entire content is wrapped in _..._ or *...*
        if ((content.startsWith("_") && content.endsWith("_") && content.length >= 2) ||
            (content.startsWith("*") && content.endsWith("*") && content.length >= 2)) {
            val inner = content.substring(1, content.length - 1)
            return listOf(InlineElement.BoldItalic(inner))
        }
        // Check for nested italic *...* or _..._
        val elements = mutableListOf<InlineElement>()
        val italicPattern = Regex("(?<!\\\\)([*_])(.*?)(?<!\\\\)\\1")
        var lastIdx = 0
        for (match in italicPattern.findAll(content)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIdx) {
                elements.add(InlineElement.Bold(content.substring(lastIdx, start)))
            }
            val inner = match.groupValues[2]
            elements.add(InlineElement.BoldItalic(inner))
            lastIdx = end
        }
        if (lastIdx < content.length) {
            elements.add(InlineElement.Bold(content.substring(lastIdx)))
        }
        return if (elements.isEmpty()) listOf(InlineElement.Bold(content)) else elements
    }

    private fun parseItalicContent(content: String): List<InlineElement> {
        // If entire content is wrapped in __...__ or **...**
        if ((content.startsWith("__") && content.endsWith("__") && content.length >= 4) ||
            (content.startsWith("**") && content.endsWith("**") && content.length >= 4)) {
            val inner = content.substring(2, content.length - 2)
            return listOf(InlineElement.BoldItalic(inner))
        }
        // Check for nested bold **...** or __...__
        val elements = mutableListOf<InlineElement>()
        val boldPattern = Regex("(?<!\\\\)(\\*\\*|__)(.*?)(?<!\\\\)\\1")
        var lastIdx = 0
        for (match in boldPattern.findAll(content)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIdx) {
                elements.add(InlineElement.Italic(content.substring(lastIdx, start)))
            }
            val inner = match.groupValues[2]
            elements.add(InlineElement.BoldItalic(inner))
            lastIdx = end
        }
        if (lastIdx < content.length) {
            elements.add(InlineElement.Italic(content.substring(lastIdx)))
        }
        return if (elements.isEmpty()) listOf(InlineElement.Italic(content)) else elements
    }

    private fun findNextSpecialChar(text: String, start: Int): Int {
        for (i in start until text.length) {
            when (text[i]) {
                '`', '$', '*', '_', '~', '[', '\\' -> return i
            }
        }
        return -1
    }

    private fun findClosingItalic(text: String, start: Int, marker: Char): Int {
        var idx = start + 1
        while (idx < text.length) {
            if (text[idx] == marker) {
                // If it's part of a double marker (like ** or __), skip past it
                val hasNextSame = idx + 1 < text.length && text[idx + 1] == marker
                val hasPrevSame = idx > start && text[idx - 1] == marker
                if (hasNextSame) {
                    idx += 2
                    continue
                } else if (!hasPrevSame) {
                    return idx
                }
            }
            idx++
        }
        return -1
    }
}
