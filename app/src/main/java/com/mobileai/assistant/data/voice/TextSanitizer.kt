package com.mobileai.assistant.data.voice

/**
 * Sanitizes Markdown and technical symbols into clean, pronounceable text for Text-to-Speech.
 */
object TextSanitizer {

    fun cleanForSpeech(raw: String): String {
        if (raw.isBlank()) return ""

        var text = raw

        // 1. Remove XML/think tags and internal reasoning traces
        text = text.replace(Regex("<think>[\\s\\S]*?</think>"), "")
        text = text.replace(Regex("<[^>]+>"), "")

        // 2. Replace fenced code blocks with spoken summary
        text = text.replace(Regex("```[a-zA-Z0-9_-]*\\n([\\s\\S]*?)```")) { matchResult ->
            val code = matchResult.groupValues[1].trim()
            val lineCount = code.lines().size
            " Code block with $lineCount lines omitted. "
        }

        // 3. Display Math blocks: $$...$$
        text = text.replace(Regex("\\$\\$([\\s\\S]*?)\\$\\$")) { matchResult ->
            " Mathematical formula: ${matchResult.groupValues[1].trim()} "
        }

        // 4. Inline Math: $...$
        text = text.replace(Regex("\\$([^\\$]+)\\$")) { matchResult ->
            " ${matchResult.groupValues[1].trim()} "
        }

        // 5. Inline Code: `...`
        text = text.replace(Regex("`([^`]+)`"), "$1")

        // 6. Markdown Links: [text](url) -> text
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")

        // 7. Headings: # to ######
        text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")

        // 8. Blockquotes: > ...
        text = text.replace(Regex("(?m)^>\\s*"), "")

        // 9. Horizontal rules: ---, ***, ___
        text = text.replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")

        // 10. Table delimiters and pipes
        text = text.replace(Regex("(?m)^\\|[-:| ]+\\|$"), "")
        text = text.replace("|", " ")

        // 11. Bullet and numbered lists: remove leading bullet/number markers
        text = text.replace(Regex("(?m)^[-*+]\\s+"), "")
        text = text.replace(Regex("(?m)^\\d+\\.\\s+"), "")

        // 12. Bold, italic, strikethrough formatting
        text = text.replace(Regex("\\*\\*\\*([^*]+)\\*\\*\\*"), "$1")
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")
        text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
        text = text.replace(Regex("_([^_]+)_"), "$1")
        text = text.replace(Regex("~~([^~]+)~~"), "$1")

        // 13. Clean up multiple whitespaces and linebreaks
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }
}

