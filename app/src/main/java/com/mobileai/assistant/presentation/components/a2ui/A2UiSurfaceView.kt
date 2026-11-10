package com.mobileai.assistant.presentation.components.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileai.assistant.data.a2ui.A2UiSurfaceState
import com.mobileai.assistant.domain.a2ui.A2UiResolvedNode

/**
 * Official Google A2UI Native Jetpack Compose Surface View.
 * Renders resolved component hierarchies conforming to specification v0.8.
 */
@Composable
fun A2UiSurfaceView(
    surfaceState: A2UiSurfaceState,
    modifier: Modifier = Modifier,
    onAction: (surfaceId: String, actionName: String, parameters: Map<String, String>) -> Unit = { _, _, _ -> }
) {
    val root = surfaceState.resolvedTree ?: return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RenderA2UiNode(
            node = root,
            surfaceId = surfaceState.surfaceId,
            onAction = onAction
        )
    }
}

@Composable
fun RenderA2UiNode(
    node: A2UiResolvedNode,
    surfaceId: String,
    modifier: Modifier = Modifier,
    onAction: (surfaceId: String, actionName: String, parameters: Map<String, String>) -> Unit
) {
    when (node.component.lowercase()) {
        "card" -> RenderA2UiCard(node, surfaceId, modifier, onAction)
        "column" -> RenderA2UiColumn(node, surfaceId, modifier, onAction)
        "row" -> RenderA2UiRow(node, surfaceId, modifier, onAction)
        "text" -> RenderA2UiText(node, modifier)
        "button" -> RenderA2UiButton(node, surfaceId, modifier, onAction)
        "textfield", "textinput" -> RenderA2UiTextField(node, surfaceId, modifier, onAction)
        "metric" -> RenderA2UiMetric(node, modifier)
        "progressbar", "progress" -> RenderA2UiProgressBar(node, modifier)
        "divider" -> HorizontalDivider(modifier = modifier.padding(vertical = 4.dp))
        else -> RenderA2UiFallback(node, modifier)
    }
}

@Composable
private fun RenderA2UiCard(
    node: A2UiResolvedNode,
    surfaceId: String,
    modifier: Modifier,
    onAction: (String, String, Map<String, String>) -> Unit
) {
    val title = node.properties["title"]
    val subtitle = node.properties["subtitle"]

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            node.children.forEach { child ->
                RenderA2UiNode(node = child, surfaceId = surfaceId, onAction = onAction)
            }
        }
    }
}

@Composable
private fun RenderA2UiColumn(
    node: A2UiResolvedNode,
    surfaceId: String,
    modifier: Modifier,
    onAction: (String, String, Map<String, String>) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        node.children.forEach { child ->
            RenderA2UiNode(node = child, surfaceId = surfaceId, onAction = onAction)
        }
    }
}

@Composable
private fun RenderA2UiRow(
    node: A2UiResolvedNode,
    surfaceId: String,
    modifier: Modifier,
    onAction: (String, String, Map<String, String>) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        node.children.forEach { child ->
            RenderA2UiNode(node = child, surfaceId = surfaceId, onAction = onAction)
        }
    }
}

@Composable
private fun RenderA2UiText(node: A2UiResolvedNode, modifier: Modifier) {
    val text = node.properties["text"] ?: node.properties["value"] ?: ""
    val styleStr = node.properties["style"] ?: "body"

    val typography = when (styleStr.lowercase()) {
        "title", "heading" -> MaterialTheme.typography.titleMedium
        "headline" -> MaterialTheme.typography.headlineSmall
        "caption" -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.bodyMedium
    }

    Text(text = text, style = typography, modifier = modifier)
}

@Composable
private fun RenderA2UiButton(
    node: A2UiResolvedNode,
    surfaceId: String,
    modifier: Modifier,
    onAction: (String, String, Map<String, String>) -> Unit
) {
    val label = node.properties["label"] ?: node.properties["text"] ?: "Submit"
    val actionName = node.properties["action"] ?: node.properties["actionName"] ?: "click"

    Button(
        onClick = { onAction(surfaceId, actionName, node.properties) },
        modifier = modifier
    ) {
        Text(text = label)
    }
}

@Composable
private fun RenderA2UiTextField(
    node: A2UiResolvedNode,
    surfaceId: String,
    modifier: Modifier,
    onAction: (String, String, Map<String, String>) -> Unit
) {
    var textValue by remember { mutableStateOf(node.properties["value"] ?: "") }
    val label = node.properties["label"] ?: "Input"

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            textValue = newValue
            onAction(surfaceId, "change", mapOf("fieldId" to node.id, "value" to newValue))
        },
        label = { Text(text = label) },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun RenderA2UiMetric(node: A2UiResolvedNode, modifier: Modifier) {
    val label = node.properties["label"] ?: ""
    val value = node.properties["value"] ?: ""
    val delta = node.properties["delta"] ?: node.properties["trend"]

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
            if (!delta.isNullOrBlank()) {
                Text(
                    text = "Delta: $delta",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RenderA2UiProgressBar(node: A2UiResolvedNode, modifier: Modifier) {
    val progress = node.properties["value"]?.toFloatOrNull() ?: 0.5f
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp)
    )
}

@Composable
private fun RenderA2UiFallback(node: A2UiResolvedNode, modifier: Modifier) {
    Text(
        text = "[A2UI: ${node.component}] (${node.id})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
