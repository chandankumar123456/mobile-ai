package com.mobileai.assistant.presentation.components.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Native Jetpack Compose real-time Markdown renderer.
 * Safely renders streaming Markdown without layout instability or syntax leakage.
 */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    isStreaming: Boolean = false
) {
    val blocks = remember(markdown) {
        MarkdownParser.parse(markdown)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> HeadingBlock(block, textColor)
                is MarkdownBlock.Paragraph -> ParagraphBlock(block, textColor)
                is MarkdownBlock.CodeBlock -> CodeBlockView(block)
                is MarkdownBlock.Blockquote -> BlockquoteView(block, textColor)
                is MarkdownBlock.TaskList -> TaskListView(block, textColor)
                is MarkdownBlock.UnorderedList -> UnorderedListView(block, textColor)
                is MarkdownBlock.OrderedList -> OrderedListView(block, textColor)
                is MarkdownBlock.Table -> TableView(block, textColor)
                is MarkdownBlock.HorizontalRule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                is MarkdownBlock.MathBlock -> MathRenderer.MathBlock(block.expression)
                is MarkdownBlock.InlineMath -> MathRenderer.InlineMathText(block.expression)
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MarkdownBlock.Heading, textColor: Color) {
    val typography = when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = textColor)
        3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = textColor)
        4 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = textColor)
        5 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = textColor)
        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = textColor)
    }

    val annotated = buildInlineAnnotatedString(block.elements, textColor)
    Text(
        text = annotated,
        style = typography,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ParagraphBlock(block: MarkdownBlock.Paragraph, textColor: Color) {
    val annotated = buildInlineAnnotatedString(block.elements, textColor)
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            lineHeight = 22.sp
        )
    )
}

@Composable
fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val codeContainerColor = Color(0xFF1E1E2E)
    val headerColor = Color(0xFF181825)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = codeContainerColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFA6ADC8)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    AnimatedVisibility(
                        visible = copied,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "Copied!",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA6E3A1),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(block.code))
                            copied = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) Color(0xFFA6E3A1) else Color(0xFFA6ADC8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Code content with upgraded SyntaxHighlighter
            val highlighted = remember(block.code, block.language) {
                SyntaxHighlighter.highlight(block.code, block.language)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = Color(0xFFCDD6F4)
                )
            }
        }
    }
}

@Composable
private fun BlockquoteView(block: MarkdownBlock.Blockquote, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        val annotated = buildInlineAnnotatedString(block.content, textColor)
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                color = textColor.copy(alpha = 0.85f),
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun TaskListView(block: MarkdownBlock.TaskList, textColor: Color) {
    Column(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        block.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = null,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 6.dp)
                )
                val elements = if (item.elements.isNotEmpty()) item.elements else MarkdownParser.parseInlines(item.text)
                val annotated = buildInlineAnnotatedString(elements, textColor)
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (item.checked) textColor.copy(alpha = 0.6f) else textColor,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UnorderedListView(block: MarkdownBlock.UnorderedList, textColor: Color) {
    Column(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        block.items.forEach { itemElements ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp)
                )
                val annotated = buildInlineAnnotatedString(itemElements, textColor)
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OrderedListView(block: MarkdownBlock.OrderedList, textColor: Color) {
    Column(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        block.items.forEach { (number, itemElements) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$number. ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )
                val annotated = buildInlineAnnotatedString(itemElements, textColor)
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TableView(block: MarkdownBlock.Table, textColor: Color) {
    val colCount = maxOf(block.headers.size, block.rows.maxOfOrNull { it.size } ?: 1)
    val cellWidth = 140.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                for (c in 0 until colCount) {
                    val headerElements = block.headers.getOrNull(c) ?: emptyList()
                    val alignment = block.alignments.getOrNull(c) ?: TableAlignment.LEFT
                    val boxAlignment = when (alignment) {
                        TableAlignment.CENTER -> Alignment.Center
                        TableAlignment.RIGHT -> Alignment.CenterEnd
                        TableAlignment.LEFT -> Alignment.CenterStart
                    }
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .padding(horizontal = 8.dp),
                        contentAlignment = boxAlignment
                    ) {
                        Text(
                            text = buildInlineAnnotatedString(headerElements, MaterialTheme.colorScheme.onPrimaryContainer),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Data Rows
            block.rows.forEach { rowCells ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    for (c in 0 until colCount) {
                        val cellElements = rowCells.getOrNull(c) ?: emptyList()
                        val alignment = block.alignments.getOrNull(c) ?: TableAlignment.LEFT
                        val boxAlignment = when (alignment) {
                            TableAlignment.CENTER -> Alignment.Center
                            TableAlignment.RIGHT -> Alignment.CenterEnd
                            TableAlignment.LEFT -> Alignment.CenterStart
                        }
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .padding(horizontal = 8.dp),
                            contentAlignment = boxAlignment
                        ) {
                            Text(
                                text = buildInlineAnnotatedString(cellElements, textColor),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds Compose [AnnotatedString] from list of [InlineElement]s.
 */
fun buildInlineAnnotatedString(
    elements: List<InlineElement>,
    textColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        elements.forEach { el ->
            when (el) {
                is InlineElement.Text -> append(el.text)
                is InlineElement.Bold -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor))
                    append(el.text)
                    pop()
                }
                is InlineElement.Italic -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor))
                    append(el.text)
                    pop()
                }
                is InlineElement.BoldItalic -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = textColor))
                    append(el.text)
                    pop()
                }
                is InlineElement.Strikethrough -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = textColor.copy(alpha = 0.7f)))
                    append(el.text)
                    pop()
                }
                is InlineElement.InlineCode -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = Color(0xFF2D2B38),
                            color = Color(0xFFFFB86C),
                            fontSize = 13.sp
                        )
                    )
                    append(" ${el.code} ")
                    pop()
                }
                is InlineElement.InlineMath -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF64B5F6)
                        )
                    )
                    append(el.expression)
                    pop()
                }
                is InlineElement.Link -> {
                    pushLink(androidx.compose.ui.text.LinkAnnotation.Url(el.url))
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFF64B5F6),
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append(el.text)
                    pop()
                    pop()
                }
            }
        }
    }
}
