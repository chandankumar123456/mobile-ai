package com.mobileai.assistant.data.genui

/**
 * Real-time auto-closing state machine that synthesizes valid closing quotes,
 * brackets, and braces from incomplete or cut-off streaming JSON chunks.
 */
object StreamingJsonRepair {

    /**
     * Synthesizes closing quotes, brackets, and braces to produce syntactically
     * valid JSON from an incomplete or cut-off streaming chunk.
     */
    fun repair(partialJson: String): String {
        val trimmed = partialJson.trim()
        if (trimmed.isEmpty()) return "{}"

        val stack = ArrayDeque<Char>()
        var inString = false
        var isEscaped = false
        val sb = StringBuilder()

        for (i in trimmed.indices) {
            val c = trimmed[i]
            sb.append(c)

            if (inString) {
                if (isEscaped) {
                    isEscaped = false
                } else if (c == '\\') {
                    isEscaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> stack.addLast('}')
                    '[' -> stack.addLast(']')
                    '}', ']' -> {
                        if (stack.isNotEmpty() && stack.last() == c) {
                            stack.removeLast()
                        }
                    }
                }
            }
        }

        // Step 1: Auto-close unclosed string literal
        if (inString) {
            if (isEscaped) {
                sb.setLength(sb.length - 1)
            }
            sb.append('"')
        }

        // Step 2: Clean up dangling tail tokens (colons, commas, incomplete keys/values)
        var cleaned = sb.toString().trimEnd()

        // 2a. Remove dangling trailing commas
        while (cleaned.endsWith(",") || cleaned.endsWith(" ")) {
            cleaned = cleaned.dropLast(1).trimEnd()
        }

        // 2b. Remove dangling key with trailing colon (e.g. `{"type": "card", "title": ` -> `{"type": "card"`)
        if (cleaned.endsWith(":")) {
            cleaned = cleaned.dropLast(1).trimEnd()
            cleaned = dropLastPropertyKey(cleaned)
        } else if (stack.isNotEmpty() && stack.last() == '}') {
            // 2c. Check if the object ends with an unvalued key (e.g. `{"type": "card", "sub"` where "sub" has no colon)
            cleaned = dropTrailingUnpairedKey(cleaned)
        }

        // 2d. Clean up any trailing comma exposed by dropping a key
        while (cleaned.endsWith(",") || cleaned.endsWith(" ")) {
            cleaned = cleaned.dropLast(1).trimEnd()
        }

        // 2e. Repair incomplete numeric literals (e.g. `12.` -> `12`, `-` -> drop)
        cleaned = repairIncompleteNumbers(cleaned)

        // Step 3: Balance unclosed braces and brackets in LIFO order
        val closingBuilder = StringBuilder(cleaned)
        while (stack.isNotEmpty()) {
            closingBuilder.append(stack.removeLast())
        }

        val result = closingBuilder.toString()
        return if (result.isBlank()) "{}" else result
    }

    /**
     * Drops the last string literal property key preceding a colon.
     */
    private fun dropLastPropertyKey(text: String): String {
        var str = text.trimEnd()
        if (str.endsWith('"')) {
            val openQuote = findPrecedingUnescapedQuote(str, str.length - 1)
            if (openQuote != -1) {
                str = str.substring(0, openQuote).trimEnd()
                if (str.endsWith(',')) {
                    str = str.dropLast(1).trimEnd()
                }
            }
        }
        return str
    }

    /**
     * Detects if the last token in an object context is an unpaired key (string not preceded by a colon).
     */
    private fun dropTrailingUnpairedKey(text: String): String {
        var str = text.trimEnd()
        if (str.endsWith('"')) {
            val openQuote = findPrecedingUnescapedQuote(str, str.length - 1)
            if (openQuote != -1) {
                val beforeQuote = str.substring(0, openQuote).trimEnd()
                if (beforeQuote.endsWith(',') || beforeQuote.endsWith('{')) {
                    str = if (beforeQuote.endsWith(',')) {
                        beforeQuote.dropLast(1).trimEnd()
                    } else {
                        beforeQuote
                    }
                }
            }
        }
        return str
    }

    private fun repairIncompleteNumbers(text: String): String {
        var str = text
        if (str.endsWith('.')) {
            str = str.dropLast(1)
        } else if (str.endsWith('-') || str.endsWith('+')) {
            str = str.dropLast(1).trimEnd()
            if (str.endsWith(':')) {
                str = dropLastPropertyKey(str.dropLast(1).trimEnd())
            }
        }
        return str
    }

    private fun findPrecedingUnescapedQuote(text: String, endIdx: Int): Int {
        for (i in (endIdx - 1) downTo 0) {
            if (text[i] == '"') {
                var backslashes = 0
                var j = i - 1
                while (j >= 0 && text[j] == '\\') {
                    backslashes++
                    j--
                }
                if (backslashes % 2 == 0) {
                    return i
                }
            }
        }
        return -1
    }
}

