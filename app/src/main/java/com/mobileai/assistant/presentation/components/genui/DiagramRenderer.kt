package com.mobileai.assistant.presentation.components.genui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.genui.DiagramDirection
import com.mobileai.assistant.domain.genui.DiagramEdge
import com.mobileai.assistant.domain.genui.DiagramNode
import com.mobileai.assistant.domain.genui.GenUiDiagram
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.genui.NodeShape
import com.mobileai.assistant.domain.genui.NodeStatus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class NodeLayout(
    val node: DiagramNode,
    val bounds: androidx.compose.ui.geometry.Rect
)

@Composable
fun DiagramRenderer(
    diagram: GenUiDiagram,
    onEvent: (GenUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val textMeasurer = rememberTextMeasurer()
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary

    // Layout computation
    val nodeLayouts = remember(diagram.nodes, diagram.edges, diagram.layoutDirection) {
        computeTopologicalLayout(diagram.nodes, diagram.edges, diagram.layoutDirection)
    }

    // Diagram bounding size
    val diagramBounds = remember(nodeLayouts) {
        if (nodeLayouts.isEmpty()) Size(300f, 200f)
        else {
            val maxR = nodeLayouts.maxOf { it.bounds.right } + 40f
            val maxB = nodeLayouts.maxOf { it.bounds.bottom } + 40f
            Size(max(320f, maxR), max(220f, maxB))
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (!diagram.title.isNullOrBlank()) {
                Text(
                    text = diagram.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clipToBounds()
                    .pointerInput(diagram) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.6f, 2.5f)
                            panOffset += pan
                        }
                    }
                    .pointerInput(diagram, nodeLayouts, zoomScale, panOffset) {
                        detectTapGestures { tapOffset ->
                            // Transform tap to diagram coordinate space
                            val transformed = Offset(
                                (tapOffset.x - panOffset.x) / zoomScale,
                                (tapOffset.y - panOffset.y) / zoomScale
                            )
                            val clickedNode = nodeLayouts.find { it.bounds.contains(transformed) }
                            if (clickedNode != null) {
                                onEvent(
                                    GenUiEvent.DiagramNodeClick(
                                        diagramId = diagram.id,
                                        nodeId = clickedNode.node.id,
                                        nodeLabel = clickedNode.node.label
                                    )
                                )
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    translate(left = panOffset.x, top = panOffset.y) {
                        scale(scaleX = zoomScale, scaleY = zoomScale, pivot = Offset.Zero) {
                            // Draw Edges
                            diagram.edges.forEach { edge ->
                                val fromNode = nodeLayouts.find { it.node.id == edge.from }
                                val toNode = nodeLayouts.find { it.node.id == edge.to }
                                if (fromNode != null && toNode != null) {
                                    drawDiagramEdge(
                                        edge = edge,
                                        fromRect = fromNode.bounds,
                                        toRect = toNode.bounds,
                                        direction = diagram.layoutDirection,
                                        edgeColor = outlineVariant,
                                        textMeasurer = textMeasurer,
                                        labelColor = onSurfaceVariant
                                    )
                                }
                            }

                            // Draw Nodes
                            nodeLayouts.forEach { nl ->
                                drawDiagramNode(
                                    node = nl.node,
                                    bounds = nl.bounds,
                                    surfaceColor = surfaceColor,
                                    primaryColor = primaryColor,
                                    defaultBorderColor = outlineVariant,
                                    textMeasurer = textMeasurer,
                                    textColor = onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Topologically ranks nodes into layers and calculates coordinate positions.
 * Safely handles self-loops, disconnected subgraphs, duplicate edges, and cyclic graphs.
 */
fun computeTopologicalLayout(
    nodes: List<DiagramNode>,
    edges: List<DiagramEdge>,
    direction: DiagramDirection
): List<NodeLayout> {
    val distinctNodes = nodes.distinctBy { it.id }
    if (distinctNodes.isEmpty()) return emptyList()

    val nodeMap = distinctNodes.associateBy { it.id }

    // 1. Deduplicate and filter out invalid edges and self-loops (self-loops do not define DAG rank)
    val validEdges = edges.filter { e ->
        nodeMap.containsKey(e.from) && nodeMap.containsKey(e.to) && e.from != e.to
    }.distinctBy { it.from to it.to }

    // 2. Build incoming prerequisite map
    val incoming = mutableMapOf<String, MutableSet<String>>().apply {
        distinctNodes.forEach { put(it.id, mutableSetOf()) }
    }
    validEdges.forEach { e ->
        incoming[e.to]?.add(e.from)
    }

    // 3. Level-by-level topological layer assignment
    val layers = mutableMapOf<String, Int>()
    val visited = mutableSetOf<String>()
    var currentLayer = 0

    while (visited.size < distinctNodes.size) {
        val remaining = distinctNodes.map { it.id }.filter { it !in visited }
        if (remaining.isEmpty()) break

        // Find all nodes whose prerequisites have all been assigned to earlier layers
        val layerNodes = remaining.filter { id ->
            val inc = incoming[id] ?: emptySet()
            inc.none { it !in visited }
        }

        val selected = if (layerNodes.isNotEmpty()) {
            layerNodes
        } else {
            // Cycle detected among remaining unvisited nodes: break cycle by picking node with minimal remaining incoming edges
            val best = remaining.minByOrNull { id ->
                val inc = incoming[id] ?: emptySet()
                inc.count { it !in visited }
            } ?: remaining.first()
            listOf(best)
        }

        selected.forEach { id ->
            layers[id] = currentLayer
            visited.add(id)
        }
        currentLayer++
    }

    val layerGroups = layers.entries.groupBy({ it.value }, { it.key })
    val maxLayer = layerGroups.keys.maxOrNull() ?: 0

    val nodeWidth = 130f
    val nodeHeight = 52f
    val layerSpacing = 85f
    val nodeSpacing = 24f

    val layouts = mutableListOf<NodeLayout>()

    if (direction == DiagramDirection.TOP_TO_BOTTOM) {
        for (layerIdx in 0..maxLayer) {
            val nodeIds = layerGroups[layerIdx] ?: continue
            val rowWidth = nodeIds.size * nodeWidth + (nodeIds.size - 1) * nodeSpacing
            val startX = 20f + max(0f, (340f - rowWidth) / 2f)
            val y = 20f + layerIdx * (nodeHeight + layerSpacing)

            nodeIds.forEachIndexed { i, id ->
                val x = startX + i * (nodeWidth + nodeSpacing)
                val node = nodeMap[id] ?: return@forEachIndexed
                layouts.add(
                    NodeLayout(
                        node = node,
                        bounds = androidx.compose.ui.geometry.Rect(x, y, x + nodeWidth, y + nodeHeight)
                    )
                )
            }
        }
    } else {
        // LEFT_TO_RIGHT
        for (layerIdx in 0..maxLayer) {
            val nodeIds = layerGroups[layerIdx] ?: continue
            val colHeight = nodeIds.size * nodeHeight + (nodeIds.size - 1) * nodeSpacing
            val startY = 20f + max(0f, (240f - colHeight) / 2f)
            val x = 20f + layerIdx * (nodeWidth + layerSpacing)

            nodeIds.forEachIndexed { i, id ->
                val y = startY + i * (nodeHeight + nodeSpacing)
                val node = nodeMap[id] ?: return@forEachIndexed
                layouts.add(
                    NodeLayout(
                        node = node,
                        bounds = androidx.compose.ui.geometry.Rect(x, y, x + nodeWidth, y + nodeHeight)
                    )
                )
            }
        }
    }

    return layouts
}

private fun DrawScope.drawDiagramEdge(
    edge: DiagramEdge,
    fromRect: androidx.compose.ui.geometry.Rect,
    toRect: androidx.compose.ui.geometry.Rect,
    direction: DiagramDirection,
    edgeColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelColor: Color
) {
    val (start, end) = if (direction == DiagramDirection.TOP_TO_BOTTOM) {
        Pair(
            Offset(fromRect.center.x, fromRect.bottom),
            Offset(toRect.center.x, toRect.top)
        )
    } else {
        Pair(
            Offset(fromRect.right, fromRect.center.y),
            Offset(toRect.left, toRect.center.y)
        )
    }

    val path = Path().apply {
        moveTo(start.x, start.y)
        if (direction == DiagramDirection.TOP_TO_BOTTOM) {
            val midY = start.y + (end.y - start.y) / 2f
            cubicTo(start.x, midY, end.x, midY, end.x, end.y)
        } else {
            val midX = start.x + (end.x - start.x) / 2f
            cubicTo(midX, start.y, midX, end.y, end.x, end.y)
        }
    }

    val stroke = if (edge.dashed) {
        Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f))
    } else {
        Stroke(width = 2.dp.toPx())
    }

    drawPath(path = path, color = edgeColor, style = stroke)

    // Draw Arrowhead at target
    val arrowLength = 10.dp.toPx()
    val arrowAngle = Math.toRadians(25.0).toFloat()
    val arrivalAngle = if (direction == DiagramDirection.TOP_TO_BOTTOM) {
        Math.PI.toFloat() / 2f
    } else {
        0f
    }

    val arrowPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(
            end.x - arrowLength * cos(arrivalAngle - arrowAngle),
            end.y - arrowLength * sin(arrivalAngle - arrowAngle)
        )
        lineTo(
            end.x - arrowLength * cos(arrivalAngle + arrowAngle),
            end.y - arrowLength * sin(arrivalAngle + arrowAngle)
        )
        close()
    }
    drawPath(path = arrowPath, color = edgeColor, style = Fill)

    // Optional Edge Label
    if (!edge.label.isNullOrBlank()) {
        val midX = (start.x + end.x) / 2f
        val midY = (start.y + end.y) / 2f
        val textLayout = textMeasurer.measure(
            text = edge.label,
            style = TextStyle(fontSize = 9.sp, color = labelColor, fontWeight = FontWeight.Medium)
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(midX + 4f, midY - textLayout.size.height / 2f)
        )
    }
}

private fun DrawScope.drawDiagramNode(
    node: DiagramNode,
    bounds: androidx.compose.ui.geometry.Rect,
    surfaceColor: Color,
    primaryColor: Color,
    defaultBorderColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textColor: Color
) {
    val borderColor = when (node.status) {
        NodeStatus.SUCCESS -> Color(0xFF10B981)
        NodeStatus.IN_PROGRESS -> primaryColor
        NodeStatus.WARNING -> Color(0xFFF59E0B)
        NodeStatus.ERROR -> Color(0xFFEF4444)
        NodeStatus.NEUTRAL -> defaultBorderColor
        null -> defaultBorderColor
    }

    val borderWidth = if (node.status == NodeStatus.IN_PROGRESS) 2.5.dp.toPx() else 1.5.dp.toPx()

    when (node.shape) {
        NodeShape.ROUNDED_RECT -> {
            drawRoundRect(
                color = surfaceColor,
                topLeft = Offset(bounds.left, bounds.top),
                size = bounds.size,
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(bounds.left, bounds.top),
                size = bounds.size,
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = borderWidth)
            )
        }
        NodeShape.PILL -> {
            val radius = bounds.height / 2f
            drawRoundRect(
                color = surfaceColor,
                topLeft = Offset(bounds.left, bounds.top),
                size = bounds.size,
                cornerRadius = CornerRadius(radius, radius)
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(bounds.left, bounds.top),
                size = bounds.size,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = borderWidth)
            )
        }
        NodeShape.DIAMOND -> {
            val path = Path().apply {
                moveTo(bounds.center.x, bounds.top)
                lineTo(bounds.right, bounds.center.y)
                lineTo(bounds.center.x, bounds.bottom)
                lineTo(bounds.left, bounds.center.y)
                close()
            }
            drawPath(path = path, color = surfaceColor, style = Fill)
            drawPath(path = path, color = borderColor, style = Stroke(width = borderWidth))
        }
        NodeShape.CIRCLE -> {
            val center = bounds.center
            val radius = minOf(bounds.width, bounds.height) / 2f
            drawCircle(color = surfaceColor, radius = radius, center = center)
            drawCircle(color = borderColor, radius = radius, center = center, style = Stroke(width = borderWidth))
        }
    }

    // Node Label
    val textLayout = textMeasurer.measure(
        text = node.label,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    )
    val textTopLeft = Offset(
        bounds.center.x - textLayout.size.width / 2f,
        bounds.center.y - textLayout.size.height / 2f
    )
    drawText(textLayoutResult = textLayout, topLeft = textTopLeft)
}

