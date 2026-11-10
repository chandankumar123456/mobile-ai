package com.mobileai.assistant.presentation.components.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Structured AST for mathematical expressions.
 */
sealed interface MathNode {
    data class Text(val text: String) : MathNode
    data class Symbol(val unicode: String) : MathNode
    data class Fraction(val numerator: MathNode, val denominator: MathNode) : MathNode
    data class Sqrt(val radicand: MathNode, val rootIndex: MathNode? = null) : MathNode
    data class SuperScript(val base: MathNode, val exponent: MathNode) : MathNode
    data class SubScript(val base: MathNode, val subscript: MathNode) : MathNode
    data class SubSuperScript(val base: MathNode, val sub: MathNode, val sup: MathNode) : MathNode
    data class Group(val nodes: List<MathNode>) : MathNode
}

/**
 * Lightweight, fault-tolerant LaTeX math parser.
 */
object MathParser {

    private val GREEK_AND_SYMBOLS = mapOf(
        // Greek lowercase
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",

        // Greek uppercase
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",

        // Operators & Relations
        "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓",
        "cdot" to "·", "cdots" to "⋯", "dots" to "…", "ldots" to "…",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "ne" to "≠", "neq" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "propto" to "∝",
        "in" to "∈", "notin" to "∉", "ni" to "∋",
        "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "supseteq" to "⊇",
        "cap" to "∩", "cup" to "∪", "setminus" to "∖",
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "leftrightarrow" to "↔", "mapsto" to "↦",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄", "empty" to "∅", "emptyset" to "∅",
        "angle" to "∠", "circ" to "∘", "deg" to "°"
    )

    fun parse(expression: String): MathNode? {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val parser = MathLexerAndParser(trimmed)
            val nodes = parser.parseAll()
            when (nodes.size) {
                0 -> null
                1 -> nodes.first()
                else -> MathNode.Group(nodes)
            }
        } catch (e: Exception) {
            null
        }
    }

    private class MathLexerAndParser(private val input: String) {
        private var pos = 0
        private val len = input.length

        fun parseAll(): List<MathNode> {
            val nodes = mutableListOf<MathNode>()
            while (pos < len) {
                skipWhitespace()
                if (pos >= len) break
                val node = parseItem()
                if (node != null) {
                    nodes.add(node)
                }
            }
            return nodes
        }

        private fun skipWhitespace() {
            while (pos < len && input[pos].isWhitespace()) {
                pos++
            }
        }

        private fun parseItem(): MathNode? {
            skipWhitespace()
            if (pos >= len) return null

            var base: MathNode = when (val c = input[pos]) {
                '\\' -> parseCommand()
                '{' -> parseBracedGroup()
                '^', '_' -> MathNode.Text("")
                else -> {
                    pos++
                    MathNode.Text(c.toString())
                }
            }

            // Check for sub/superscripts
            while (pos < len) {
                skipWhitespace()
                if (pos >= len) break
                if (input[pos] == '^') {
                    pos++
                    val exp = parseScriptArgument()
                    base = if (base is MathNode.SubScript) {
                        MathNode.SubSuperScript(base.base, sub = base.subscript, sup = exp)
                    } else {
                        MathNode.SuperScript(base, exp)
                    }
                } else if (input[pos] == '_') {
                    pos++
                    val sub = parseScriptArgument()
                    base = if (base is MathNode.SuperScript) {
                        MathNode.SubSuperScript(base.base, sub = sub, sup = base.exponent)
                    } else {
                        MathNode.SubScript(base, sub)
                    }
                } else {
                    break
                }
            }

            return base
        }

        private fun parseScriptArgument(): MathNode {
            skipWhitespace()
            if (pos >= len) return MathNode.Text("")
            return if (input[pos] == '{') {
                parseBracedGroup()
            } else if (input[pos] == '\\') {
                parseCommand()
            } else {
                val ch = input[pos++]
                MathNode.Text(ch.toString())
            }
        }

        private fun parseBracedGroup(): MathNode {
            if (pos < len && input[pos] == '{') pos++
            val start = pos
            var depth = 1
            while (pos < len && depth > 0) {
                if (input[pos] == '{') depth++
                else if (input[pos] == '}') depth--
                if (depth > 0) pos++
            }
            val content = input.substring(start, pos)
            if (pos < len && input[pos] == '}') pos++

            val innerParser = MathLexerAndParser(content)
            val nodes = innerParser.parseAll()
            return when (nodes.size) {
                0 -> MathNode.Text("")
                1 -> nodes.first()
                else -> MathNode.Group(nodes)
            }
        }

        private fun parseCommand(): MathNode {
            if (pos < len && input[pos] == '\\') pos++
            val cmdStart = pos
            while (pos < len && (input[pos].isLetter() || input[pos] == '*')) {
                pos++
            }
            val cmd = input.substring(cmdStart, pos)

            return when (cmd) {
                "frac" -> {
                    skipWhitespace()
                    val num = parseArgument()
                    skipWhitespace()
                    val den = parseArgument()
                    MathNode.Fraction(num, den)
                }
                "sqrt" -> {
                    skipWhitespace()
                    val rootIndex = if (pos < len && input[pos] == '[') {
                        pos++
                        val optStart = pos
                        while (pos < len && input[pos] != ']') pos++
                        val optContent = input.substring(optStart, pos)
                        if (pos < len && input[pos] == ']') pos++
                        val optParser = MathLexerAndParser(optContent)
                        val optNodes = optParser.parseAll()
                        if (optNodes.size == 1) optNodes.first() else MathNode.Group(optNodes)
                    } else null

                    skipWhitespace()
                    val radicand = parseArgument()
                    MathNode.Sqrt(radicand, rootIndex)
                }
                "text", "mathrm", "mathbf", "mathit" -> {
                    skipWhitespace()
                    parseArgument()
                }
                "left", "right" -> {
                    skipWhitespace()
                    if (pos < len) {
                        val delim = input[pos++]
                        MathNode.Text(if (delim == '.') "" else delim.toString())
                    } else MathNode.Text("")
                }
                else -> {
                    val unicode = GREEK_AND_SYMBOLS[cmd]
                    if (unicode != null) {
                        MathNode.Symbol(unicode)
                    } else {
                        MathNode.Text("\\$cmd")
                    }
                }
            }
        }

        private fun parseArgument(): MathNode {
            skipWhitespace()
            if (pos >= len) return MathNode.Text("")
            return if (input[pos] == '{') {
                parseBracedGroup()
            } else if (input[pos] == '\\') {
                parseCommand()
            } else {
                val ch = input[pos++]
                MathNode.Text(ch.toString())
            }
        }
    }
}

/**
 * Composable rendering of LaTeX Math expressions.
 */
object MathRenderer {

    @Composable
    fun MathBlock(expression: String, modifier: Modifier = Modifier) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val ast = remember(expression) { MathParser.parse(expression) }
                if (ast != null) {
                    MathNodeView(
                        node = ast,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Graceful fallback
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    fun InlineMathText(expression: String, modifier: Modifier = Modifier) {
        val ast = remember(expression) { MathParser.parse(expression) }
        if (ast != null) {
            MathNodeView(
                node = ast,
                fontSize = 14.sp,
                color = Color(0xFF64B5F6),
                modifier = modifier
            )
        } else {
            Text(
                text = expression,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic
                ),
                color = Color(0xFF64B5F6),
                modifier = modifier
            )
        }
    }

    @Composable
    fun MathNodeView(
        node: MathNode,
        fontSize: TextUnit,
        color: Color,
        modifier: Modifier = Modifier
    ) {
        when (node) {
            is MathNode.Text -> {
                Text(
                    text = node.text,
                    fontSize = fontSize,
                    fontFamily = FontFamily.Serif,
                    fontStyle = if (node.text.any { it.isLetter() }) FontStyle.Italic else FontStyle.Normal,
                    color = color,
                    modifier = modifier
                )
            }
            is MathNode.Symbol -> {
                Text(
                    text = node.unicode,
                    fontSize = fontSize,
                    color = color,
                    modifier = modifier
                )
            }
            is MathNode.Fraction -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = modifier
                        .width(IntrinsicSize.Min)
                        .padding(horizontal = 3.dp)
                ) {
                    MathNodeView(node.numerator, fontSize = fontSize * 0.85f, color = color)
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    )
                    MathNodeView(node.denominator, fontSize = fontSize * 0.85f, color = color)
                }
            }
            is MathNode.Sqrt -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier.padding(horizontal = 2.dp)
                ) {
                    if (node.rootIndex != null) {
                        Box(modifier = Modifier.offset(y = (-4).dp)) {
                            MathNodeView(node.rootIndex, fontSize = fontSize * 0.6f, color = color)
                        }
                    }
                    Text(
                        text = "√",
                        fontSize = fontSize * 1.15f,
                        color = color
                    )
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .padding(start = 1.dp)
                    ) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = color,
                            modifier = Modifier.fillMaxWidth()
                        )
                        MathNodeView(node.radicand, fontSize = fontSize, color = color)
                    }
                }
            }
            is MathNode.SuperScript -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                ) {
                    MathNodeView(node.base, fontSize = fontSize, color = color)
                    Box(modifier = Modifier.offset(y = (-fontSize.value * 0.35f).dp)) {
                        MathNodeView(node.exponent, fontSize = fontSize * 0.7f, color = color)
                    }
                }
            }
            is MathNode.SubScript -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                ) {
                    MathNodeView(node.base, fontSize = fontSize, color = color)
                    Box(modifier = Modifier.offset(y = (fontSize.value * 0.35f).dp)) {
                        MathNodeView(node.subscript, fontSize = fontSize * 0.7f, color = color)
                    }
                }
            }
            is MathNode.SubSuperScript -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                ) {
                    MathNodeView(node.base, fontSize = fontSize, color = color)
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.padding(start = 1.dp)
                    ) {
                        Box(modifier = Modifier.offset(y = (-fontSize.value * 0.15f).dp)) {
                            MathNodeView(node.sup, fontSize = fontSize * 0.65f, color = color)
                        }
                        Box(modifier = Modifier.offset(y = (fontSize.value * 0.15f).dp)) {
                            MathNodeView(node.sub, fontSize = fontSize * 0.65f, color = color)
                        }
                    }
                }
            }
            is MathNode.Group -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                ) {
                    node.nodes.forEach { child ->
                        MathNodeView(child, fontSize = fontSize, color = color)
                    }
                }
            }
        }
    }
}
