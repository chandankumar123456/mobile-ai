package com.mobileai.assistant.data.openui

import com.mobileai.assistant.domain.openui.OpenUiNode
import com.mobileai.assistant.domain.openui.OpenUiValue
import java.util.UUID

/**
 * Parser for the official OpenUI Lang specification (https://www.openui.com/docs/openui-lang).
 * Supports incremental streaming parsing of partial tokens without throwing on unclosed structures.
 */
object OpenUiLangParser {

    /**
     * Parses [input] text into a forest/tree of [OpenUiNode]s.
     * Defensively repairs unclosed brackets and partial attribute strings.
     */
    fun parse(input: String): List<OpenUiNode> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        val tokens = tokenize(trimmed)
        val reader = TokenReader(tokens)
        val nodes = mutableListOf<OpenUiNode>()

        while (reader.hasNext()) {
            val node = parseNode(reader)
            if (node != null) {
                nodes.add(node)
            } else {
                reader.advance()
            }
        }

        return nodes
    }

    private fun parseNode(reader: TokenReader): OpenUiNode? {
        val tagToken = reader.peek() ?: return null
        if (tagToken.type != TokenType.IDENTIFIER) return null

        val tag = tagToken.text
        reader.advance()

        val attributes = mutableMapOf<String, OpenUiValue>()

        // Check for attributes in parentheses: tag(k="v", k2=123)
        if (reader.peek()?.type == TokenType.LPAREN) {
            reader.advance() // consume '('
            while (reader.hasNext() && reader.peek()?.type != TokenType.RPAREN) {
                val keyToken = reader.peek()
                if (keyToken?.type == TokenType.IDENTIFIER) {
                    val key = keyToken.text
                    reader.advance()

                    if (reader.peek()?.type == TokenType.EQUALS) {
                        reader.advance() // consume '='
                        val value = parseValue(reader)
                        if (value != null) {
                            attributes[key] = value
                        }
                    } else {
                        // Boolean flag attribute: tag(flag) -> flag=true
                        attributes[key] = OpenUiValue.BooleanValue(true)
                    }
                }

                if (reader.peek()?.type == TokenType.COMMA) {
                    reader.advance() // consume ','
                } else if (reader.peek()?.type != TokenType.RPAREN) {
                    // Tolerant skip for malformed token
                    reader.advance()
                }
            }

            if (reader.peek()?.type == TokenType.RPAREN) {
                reader.advance() // consume ')'
            }
        }

        // Check for body / children in curly braces: { ... }
        val children = mutableListOf<OpenUiNode>()
        var textContent: String? = null

        if (reader.peek()?.type == TokenType.LBRACE) {
            reader.advance() // consume '{'
            while (reader.hasNext() && reader.peek()?.type != TokenType.RBRACE) {
                val next = reader.peek()
                if (next?.type == TokenType.STRING) {
                    textContent = next.text
                    reader.advance()
                } else if (next?.type == TokenType.IDENTIFIER) {
                    val childNode = parseNode(reader)
                    if (childNode != null) {
                        children.add(childNode)
                    }
                } else {
                    reader.advance()
                }
            }

            if (reader.peek()?.type == TokenType.RBRACE) {
                reader.advance() // consume '}'
            }
        }

        return OpenUiNode(
            id = UUID.randomUUID().toString(),
            tag = tag,
            attributes = attributes,
            children = children,
            textContent = textContent
        )
    }

    private fun parseValue(reader: TokenReader): OpenUiValue? {
        val token = reader.peek() ?: return null
        reader.advance()

        return when (token.type) {
            TokenType.STRING -> {
                val text = token.text
                if (text.startsWith("{{") && text.endsWith("}}")) {
                    OpenUiValue.BindingValue(text.substring(2, text.length - 2).trim())
                } else {
                    OpenUiValue.StringValue(text)
                }
            }
            TokenType.NUMBER -> {
                OpenUiValue.NumberValue(token.text.toDoubleOrNull() ?: 0.0)
            }
            TokenType.IDENTIFIER -> {
                when (token.text.lowercase()) {
                    "true" -> OpenUiValue.BooleanValue(true)
                    "false" -> OpenUiValue.BooleanValue(false)
                    else -> OpenUiValue.StringValue(token.text)
                }
            }
            else -> null
        }
    }

    // ========================================================================
    // Lexer & Tokenizer
    // ========================================================================
    private enum class TokenType {
        IDENTIFIER, STRING, NUMBER, LPAREN, RPAREN, LBRACE, RBRACE, EQUALS, COMMA
    }

    private data class Token(val type: TokenType, val text: String)

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = input.length

        while (i < len) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { tokens.add(Token(TokenType.LPAREN, "(")); i++ }
                c == ')' -> { tokens.add(Token(TokenType.RPAREN, ")")); i++ }
                c == '{' -> { tokens.add(Token(TokenType.LBRACE, "{")); i++ }
                c == '}' -> { tokens.add(Token(TokenType.RBRACE, "}")); i++ }
                c == '=' -> { tokens.add(Token(TokenType.EQUALS, "=")); i++ }
                c == ',' -> { tokens.add(Token(TokenType.COMMA, ",")); i++ }
                c == '"' || c == '\'' -> {
                    val quote = c
                    i++
                    val sb = StringBuilder()
                    while (i < len && input[i] != quote) {
                        if (input[i] == '\\' && i + 1 < len) {
                            i++
                            sb.append(input[i])
                        } else {
                            sb.append(input[i])
                        }
                        i++
                    }
                    if (i < len && input[i] == quote) i++ // consume closing quote
                    tokens.add(Token(TokenType.STRING, sb.toString()))
                }
                c.isDigit() || (c == '-' && i + 1 < len && input[i + 1].isDigit()) -> {
                    val start = i
                    if (c == '-') i++
                    while (i < len && (input[i].isDigit() || input[i] == '.')) {
                        i++
                    }
                    tokens.add(Token(TokenType.NUMBER, input.substring(start, i)))
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < len && (input[i].isLetterOrDigit() || input[i] == '_' || input[i] == '-')) {
                        i++
                    }
                    tokens.add(Token(TokenType.IDENTIFIER, input.substring(start, i)))
                }
                else -> i++ // Skip unexpected character
            }
        }

        return tokens
    }

    private class TokenReader(private val tokens: List<Token>) {
        private var index = 0

        fun hasNext(): Boolean = index < tokens.size
        fun peek(): Token? = if (hasNext()) tokens[index] else null
        fun advance(): Token? = if (hasNext()) tokens[index++] else null
    }
}

