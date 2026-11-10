package com.mobileai.assistant.presentation.components.openui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileai.assistant.domain.openui.OpenUiNode

/**
 * Native Jetpack Compose renderer for OpenUI Lang AST nodes.
 */
@Composable
fun OpenUiView(
    nodes: List<OpenUiNode>,
    modifier: Modifier = Modifier,
    onAction: (String, Map<String, String>) -> Unit = { _, _ -> }
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        nodes.forEach { node ->
            RenderOpenUiNode(node = node, onAction = onAction)
        }
    }
}

@Composable
fun RenderOpenUiNode(
    node: OpenUiNode,
    modifier: Modifier = Modifier,
    onAction: (String, Map<String, String>) -> Unit
) {
    when (node.tag.lowercase()) {
        "card" -> RenderCard(node, modifier, onAction)
        "column" -> RenderColumn(node, modifier, onAction)
        "row" -> RenderRow(node, modifier, onAction)
        "metric" -> RenderMetric(node, modifier)
        "button" -> RenderButton(node, modifier, onAction)
        "badge" -> RenderBadge(node, modifier)
        "progress" -> RenderProgress(node, modifier)
        "divider" -> HorizontalDivider(modifier = modifier.padding(vertical = 4.dp))
        "text" -> RenderText(node, modifier)
        else -> RenderFallback(node, modifier)
    }
}

@Composable
private fun RenderCard(
    node: OpenUiNode,
    modifier: Modifier,
    onAction: (String, Map<String, String>) -> Unit
) {
    val title = node.getStringAttr("title")
    val subtitle = node.getStringAttr("subtitle")
    val style = node.getStringAttr("style", "elevated")

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (node.textContent != null) {
                Text(text = node.textContent, style = MaterialTheme.typography.bodyMedium)
            }
            node.children.forEach { child ->
                RenderOpenUiNode(node = child, onAction = onAction)
            }
        }
    }

    if (style.equals("outlined", ignoreCase = true)) {
        OutlinedCard(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) { content() }
    }
}

@Composable
private fun RenderColumn(
    node: OpenUiNode,
    modifier: Modifier,
    onAction: (String, Map<String, String>) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        node.children.forEach { child ->
            RenderOpenUiNode(node = child, onAction = onAction)
        }
    }
}

@Composable
private fun RenderRow(
    node: OpenUiNode,
    modifier: Modifier,
    onAction: (String, Map<String, String>) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        node.children.forEach { child ->
            RenderOpenUiNode(node = child, onAction = onAction)
        }
    }
}

@Composable
private fun RenderMetric(node: OpenUiNode, modifier: Modifier) {
    val label = node.getStringAttr("label")
    val value = node.getStringAttr("value")
    val trend = node.getStringAttr("trend")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (trend.isNotBlank()) {
                Text(
                    text = "Trend: $trend",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RenderButton(
    node: OpenUiNode,
    modifier: Modifier,
    onAction: (String, Map<String, String>) -> Unit
) {
    val label = node.getStringAttr("label", "Action")
    val action = node.getStringAttr("action", "click")

    Button(
        onClick = { onAction(action, emptyMap()) },
        modifier = modifier
    ) {
        Text(text = label)
    }
}

@Composable
private fun RenderBadge(node: OpenUiNode, modifier: Modifier) {
    val label = node.getStringAttr("label", node.textContent ?: "Badge")
    SuggestionChip(
        onClick = {},
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier
    )
}

@Composable
private fun RenderProgress(node: OpenUiNode, modifier: Modifier) {
    val progress = node.getNumberAttr("value", 0.5).toFloat()
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp)
    )
}

@Composable
private fun RenderText(node: OpenUiNode, modifier: Modifier) {
    val text = node.textContent ?: node.getStringAttr("value", "")
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
private fun RenderFallback(node: OpenUiNode, modifier: Modifier) {
    Text(
        text = "[OpenUI: ${node.tag}] ${node.textContent ?: ""}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
