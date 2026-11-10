package com.mobileai.assistant.presentation.components.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

/**
 * Robust, language-aware syntax highlighter for code blocks supporting:
 * JSON, Kotlin/Java, Python, JS/TS, HTML/XML, CSS, SQL, Shell, Markdown, Diff.
 */
object SyntaxHighlighter {

    val KEYWORD_COLOR = Color(0xFFC792EA)      // Purple
    val STRING_COLOR = Color(0xFFC3E88D)       // Green
    val NUMBER_COLOR = Color(0xFFF78C6C)       // Orange
    val COMMENT_COLOR = Color(0xFF676E95)      // Slate gray
    val TYPE_COLOR = Color(0xFFFFCB6B)         // Yellow / Gold
    val FUNCTION_COLOR = Color(0xFF82AAFF)     // Blue
    val PUNCTUATION_COLOR = Color(0xFF89DDFF)  // Cyan
    val PROPERTY_COLOR = Color(0xFF80CBC4)     // Teal
    val DIFF_ADDED_COLOR = Color(0xFFA6E3A1)   // Light green
    val DIFF_REMOVED_COLOR = Color(0xFFF38BA8) // Light red
    val DIFF_HEADER_COLOR = Color(0xFF89B4FA)  // Soft blue

    fun highlight(code: String, language: String = ""): AnnotatedString {
        if (code.isBlank()) return AnnotatedString(code)

        val lang = language.lowercase().trim()
        return when (lang) {
            "json" -> highlightJson(code)
            "kotlin", "kt", "java" -> highlightKotlinJava(code)
            "python", "py" -> highlightPython(code)
            "javascript", "js", "typescript", "ts", "jsx", "tsx" -> highlightJsTs(code)
            "html", "xml", "svg" -> highlightXml(code)
            "css", "scss", "sass", "less" -> highlightCss(code)
            "sql" -> highlightSql(code)
            "bash", "sh", "shell", "zsh" -> highlightShell(code)
            "markdown", "md" -> highlightMarkdown(code)
            "diff", "patch" -> highlightDiff(code)
            else -> highlightGeneric(code)
        }
    }

    private class HighlightContext(val code: String) {
        val builder = AnnotatedString.Builder(code)
        val occupiedRanges = mutableListOf<IntRange>()

        fun addStyle(start: Int, end: Int, style: SpanStyle, markOccupied: Boolean = true) {
            if (start >= end || start < 0 || end > code.length) return
            val targetRange = start until end
            if (occupiedRanges.any { it.first < targetRange.last && targetRange.first < it.last }) {
                return
            }
            builder.addStyle(style, start, end)
            if (markOccupied) {
                occupiedRanges.add(targetRange)
            }
        }

        fun applyRegex(regex: Pattern, style: SpanStyle, markOccupied: Boolean = true, groupIndex: Int = 0) {
            val matcher = regex.matcher(code)
            while (matcher.find()) {
                val start = matcher.start(groupIndex)
                val end = matcher.end(groupIndex)
                addStyle(start, end, style, markOccupied)
            }
        }
    }

    private fun highlightJson(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments (if JSONC)
        ctx.applyRegex(Pattern.compile("(//.*$|/\\*.*?\\*/)", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 2. Keys: "key":
        ctx.applyRegex(Pattern.compile("\"([^\"\\\\]|\\\\.)*\"(?=\\s*:)"), SpanStyle(color = PROPERTY_COLOR, fontWeight = FontWeight.SemiBold))
        // 3. String values
        ctx.applyRegex(Pattern.compile("\"([^\"\\\\]|\\\\.)*\""), SpanStyle(color = STRING_COLOR))
        // 4. Numbers
        ctx.applyRegex(Pattern.compile("-?\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), SpanStyle(color = NUMBER_COLOR))
        // 5. Literals (true, false, null)
        ctx.applyRegex(Pattern.compile("\\b(true|false|null)\\b"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 6. Punctuation
        ctx.applyRegex(Pattern.compile("[{}\\[\\]:,]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightKotlinJava(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("(//.*$|(?s)/\\*.*?\\*/)", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings (multiline raw & regular)
        ctx.applyRegex(Pattern.compile("(\"\"\"(?s).*?\"\"\"|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')"), SpanStyle(color = STRING_COLOR))
        // 3. Numbers
        ctx.applyRegex(Pattern.compile("\\b0[xX][0-9a-fA-F_]+[lL]?\\b|\\b0[bB][01_]+[lL]?\\b|\\b\\d[\\d_]*(\\.[\\d_]+)?([eE][+-]?[\\d_]+)?[fFlLuU]?\\b"), SpanStyle(color = NUMBER_COLOR))
        // 4. Keywords
        val kw = "abstract|as|break|by|case|catch|class|companion|const|constructor|continue|data|default|" +
                "do|else|enum|extends|final|finally|for|fun|if|implements|import|in|init|inline|instanceof|" +
                "interface|internal|is|lateinit|lazy|new|null|object|open|operator|out|override|package|" +
                "private|protected|public|record|reified|return|sealed|static|super|suspend|switch|this|" +
                "throw|throws|trait|try|typealias|typeof|val|var|void|when|while|yield|boolean|int|long|" +
                "float|double|char|byte|short|true|false"
        ctx.applyRegex(Pattern.compile("\\b($kw)\\b"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 5. Types
        ctx.applyRegex(Pattern.compile("\\b[A-Z][a-zA-Z0-9_]*\\b"), SpanStyle(color = TYPE_COLOR, fontWeight = FontWeight.Medium))
        // 6. Functions
        ctx.applyRegex(Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()"), SpanStyle(color = FUNCTION_COLOR))
        // 7. Punctuation
        ctx.applyRegex(Pattern.compile("[{}()\\[\\],.;:]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightPython(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("#.*$", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings
        ctx.applyRegex(Pattern.compile("[frbFRB]?(\"\"\"(?s).*?\"\"\"|'''(?s).*?'''|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')"), SpanStyle(color = STRING_COLOR))
        // 3. Numbers
        ctx.applyRegex(Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[jJ]?\\b"), SpanStyle(color = NUMBER_COLOR))
        // 4. Keywords
        val kw = "def|class|return|if|elif|else|while|for|in|try|except|finally|raise|import|from|as|global|" +
                "nonlocal|lambda|with|yield|assert|pass|break|continue|async|await|match|case|True|False|None|" +
                "and|or|not|is|self|cls"
        ctx.applyRegex(Pattern.compile("\\b($kw)\\b"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 5. Types & Decorators
        ctx.applyRegex(Pattern.compile("@[a-zA-Z_][a-zA-Z0-9_.]*|\\b[A-Z][a-zA-Z0-9_]*\\b"), SpanStyle(color = TYPE_COLOR, fontWeight = FontWeight.Medium))
        // 6. Functions
        ctx.applyRegex(Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()"), SpanStyle(color = FUNCTION_COLOR))
        // 7. Punctuation
        ctx.applyRegex(Pattern.compile("[{}()\\[\\],.:]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightJsTs(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("(//.*$|(?s)/\\*.*?\\*/)", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings & Template Literals
        ctx.applyRegex(Pattern.compile("(`(?s).*?`|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')"), SpanStyle(color = STRING_COLOR))
        // 3. Numbers
        ctx.applyRegex(Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?n?\\b"), SpanStyle(color = NUMBER_COLOR))
        // 4. Keywords
        val kw = "function|const|let|var|return|if|else|switch|case|default|for|while|do|break|continue|" +
                "try|catch|finally|throw|new|delete|typeof|instanceof|void|this|class|extends|super|import|" +
                "export|from|as|async|await|yield|null|undefined|true|false|interface|type|enum|namespace|" +
                "implements|declare|abstract|readonly|private|protected|public|override|keyof|is|any|" +
                "unknown|never|string|number|boolean|symbol|bigint|of"
        ctx.applyRegex(Pattern.compile("\\b($kw)\\b"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 5. Types
        ctx.applyRegex(Pattern.compile("\\b[A-Z][a-zA-Z0-9_]*\\b"), SpanStyle(color = TYPE_COLOR, fontWeight = FontWeight.Medium))
        // 6. Functions
        ctx.applyRegex(Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()"), SpanStyle(color = FUNCTION_COLOR))
        // 7. Punctuation
        ctx.applyRegex(Pattern.compile("[{}()\\[\\],.;:]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightXml(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("<!--(?s).*?-->"), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings
        ctx.applyRegex(Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"), SpanStyle(color = STRING_COLOR))
        // 3. Tag names (<tag or </tag)
        ctx.applyRegex(Pattern.compile("(?<=<)[/a-zA-Z0-9:-]+|(?<=</)[a-zA-Z0-9:-]+"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 4. Attribute names
        ctx.applyRegex(Pattern.compile("\\b[a-zA-Z0-9:-]+(?=\\=)"), SpanStyle(color = PROPERTY_COLOR))
        // 5. Entities & Brackets
        ctx.applyRegex(Pattern.compile("&[a-zA-Z0-9#]+;"), SpanStyle(color = TYPE_COLOR))
        ctx.applyRegex(Pattern.compile("[<>/=]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightCss(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("(?s)/\\*.*?\\*/"), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings
        ctx.applyRegex(Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"), SpanStyle(color = STRING_COLOR))
        // 3. Property names
        ctx.applyRegex(Pattern.compile("[a-zA-Z-]+(?=\\s*:)"), SpanStyle(color = PROPERTY_COLOR, fontWeight = FontWeight.SemiBold))
        // 4. Units & Numbers
        ctx.applyRegex(Pattern.compile("\\b\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|s|ms|deg|fr)?\\b"), SpanStyle(color = NUMBER_COLOR))
        // 5. Selectors (classes, ids)
        ctx.applyRegex(Pattern.compile("[.][a-zA-Z0-9_-]+|#[a-zA-Z0-9_-]+"), SpanStyle(color = KEYWORD_COLOR))
        // 6. Colors
        ctx.applyRegex(Pattern.compile("#[0-9a-fA-F]{3,8}\\b"), SpanStyle(color = TYPE_COLOR))
        // 7. Punctuation
        ctx.applyRegex(Pattern.compile("[{}(),;:]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightSql(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("(--.*$|(?s)/\\*.*?\\*/)", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings
        ctx.applyRegex(Pattern.compile("'([^'\\\\]|\\\\.)*'"), SpanStyle(color = STRING_COLOR))
        // 3. Numbers
        ctx.applyRegex(Pattern.compile("\\b\\d+(\\.\\d+)?\\b"), SpanStyle(color = NUMBER_COLOR))
        // 4. Keywords
        val kw = "select|from|where|insert|into|values|update|set|delete|create|table|drop|alter|add|" +
                "primary|key|foreign|references|join|inner|left|right|full|outer|cross|on|group|by|" +
                "having|order|asc|desc|limit|offset|union|all|distinct|as|and|or|not|in|between|like|" +
                "is|null|case|when|then|else|end|count|sum|avg|min|max|exists|index|view|database|" +
                "schema|cascade|constraint|default|check|unique"
        ctx.applyRegex(Pattern.compile("(?i)\\b($kw)\\b"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 5. Data Types
        val types = "varchar|text|int|integer|bigint|smallint|float|double|decimal|numeric|boolean|date|time|timestamp|blob|json"
        ctx.applyRegex(Pattern.compile("(?i)\\b($types)\\b"), SpanStyle(color = TYPE_COLOR, fontWeight = FontWeight.Medium))
        // 6. Functions
        ctx.applyRegex(Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()"), SpanStyle(color = FUNCTION_COLOR))
        // 7. Punctuation
        ctx.applyRegex(Pattern.compile("[(),;.]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightShell(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("#.*$", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings
        ctx.applyRegex(Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"), SpanStyle(color = STRING_COLOR))
        // 3. Variables: $VAR, ${VAR}, $1, $?
        ctx.applyRegex(Pattern.compile("\\$[a-zA-Z_][a-zA-Z0-9_]*|\\$\\{[^}]+\\}|\\$[@*?#$!0-9]"), SpanStyle(color = PROPERTY_COLOR, fontWeight = FontWeight.SemiBold))
        // 4. Commands and Keywords
        val kw = "if|then|else|elif|fi|for|while|do|done|case|esac|in|function|select|time|echo|cd|ls|" +
                "mkdir|rm|cp|mv|cat|grep|sed|awk|curl|chmod|chown|export|source|local|return|exit|sudo|" +
                "pwd|head|tail|touch|find|kill|ps"
        ctx.applyRegex(Pattern.compile("\\b($kw)\\b"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 5. Numbers
        ctx.applyRegex(Pattern.compile("\\b\\d+\\b"), SpanStyle(color = NUMBER_COLOR))
        // 6. Punctuation
        ctx.applyRegex(Pattern.compile("[|&;<>()\\[\\]]"), SpanStyle(color = PUNCTUATION_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightMarkdown(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Headers
        ctx.applyRegex(Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold))
        // 2. Inline code
        ctx.applyRegex(Pattern.compile("`[^`\n]+`"), SpanStyle(color = STRING_COLOR))
        // 3. Bold / Italic
        ctx.applyRegex(Pattern.compile("(\\*\\*|__)(.*?)\\1"), SpanStyle(color = TYPE_COLOR, fontWeight = FontWeight.Bold))
        // 4. Links
        ctx.applyRegex(Pattern.compile("\\[.*?\\]\\(.*?\\)"), SpanStyle(color = FUNCTION_COLOR))
        // 5. Quotes
        ctx.applyRegex(Pattern.compile("^>.*$", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 6. List bullets
        ctx.applyRegex(Pattern.compile("^\\s*[-*+]\\s+|^\\s*\\d+\\.\\s+", Pattern.MULTILINE), SpanStyle(color = PROPERTY_COLOR))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightDiff(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Added lines
        ctx.applyRegex(Pattern.compile("^\\+.*$", Pattern.MULTILINE), SpanStyle(color = DIFF_ADDED_COLOR, fontWeight = FontWeight.Normal))
        // 2. Removed lines
        ctx.applyRegex(Pattern.compile("^-.*$", Pattern.MULTILINE), SpanStyle(color = DIFF_REMOVED_COLOR, fontWeight = FontWeight.Normal))
        // 3. Headers / hunks
        ctx.applyRegex(Pattern.compile("(^@@.*@@$|^diff.*$|^index.*$|^---.*$|^\\+\\+\\+.*$)", Pattern.MULTILINE), SpanStyle(color = DIFF_HEADER_COLOR, fontWeight = FontWeight.Bold))
        return ctx.builder.toAnnotatedString()
    }

    private fun highlightGeneric(code: String): AnnotatedString {
        val ctx = HighlightContext(code)
        // 1. Comments
        ctx.applyRegex(Pattern.compile("(//.*$|#.*$|(?s)/\\*.*?\\*/)", Pattern.MULTILINE), SpanStyle(color = COMMENT_COLOR))
        // 2. Strings
        ctx.applyRegex(Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"), SpanStyle(color = STRING_COLOR))
        // 3. Numbers
        ctx.applyRegex(Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFlL]?\\b"), SpanStyle(color = NUMBER_COLOR))
        // 4. Common keywords
        val kw = "abstract|as|async|await|break|case|catch|class|const|continue|data|def|default|delete|" +
                "do|else|enum|export|extends|false|final|finally|fn|for|from|fun|function|if|implements|" +
                "import|in|inline|instanceof|interface|internal|is|let|match|mut|new|null|object|open|" +
                "operator|override|package|private|protected|pub|public|return|sealed|select|self|struct|" +
                "super|suspend|switch|this|throw|trait|true|try|type|typeof|val|var|void|when|where|while|yield"
        ctx.applyRegex(Pattern.compile("\\b($kw)\\b"), SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold))
        // 5. Types
        ctx.applyRegex(Pattern.compile("\\b[A-Z][a-zA-Z0-9_]*\\b"), SpanStyle(color = TYPE_COLOR, fontWeight = FontWeight.Medium))
        // 6. Functions
        ctx.applyRegex(Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()"), SpanStyle(color = FUNCTION_COLOR))
        return ctx.builder.toAnnotatedString()
    }
}
