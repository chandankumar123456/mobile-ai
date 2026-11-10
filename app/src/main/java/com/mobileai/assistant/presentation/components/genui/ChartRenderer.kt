package com.mobileai.assistant.presentation.components.genui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.genui.ChartPoint
import com.mobileai.assistant.domain.genui.ChartSeries
import com.mobileai.assistant.domain.genui.ChartType
import com.mobileai.assistant.domain.genui.GenUiChart
import kotlin.math.cos
import kotlin.math.sin

private val DEFAULT_CHART_PALETTE = listOf(
    Color(0xFF2563EB),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
    Color(0xFFF97316),
    Color(0xFF6366F1)
)

private fun parseColorSafely(hex: String?, defaultColor: Color): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        val clean = hex.trim().removePrefix("#")
        val longVal = clean.toLong(16)
        when (clean.length) {
            6 -> Color(longVal or 0xFF000000)
            8 -> Color(longVal)
            else -> defaultColor
        }
    } catch (_: Exception) {
        defaultColor
    }
}

@Composable
fun ChartRenderer(
    chart: GenUiChart,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(if (isStreaming) 1f else 0f) }
    LaunchedEffect(chart.series, isStreaming) {
        if (!isStreaming && animProgress.value < 1f) {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        } else if (isStreaming) {
            animProgress.snapTo(1f)
        }
    }

    var selectedPointInfo by remember { mutableStateOf<String?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

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
            if (!chart.title.isNullOrBlank()) {
                Text(
                    text = chart.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Legend
            if (chart.series.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    chart.series.forEachIndexed { i, s ->
                        val col = parseColorSafely(s.color, DEFAULT_CHART_PALETTE[i % DEFAULT_CHART_PALETTE.size])
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(col)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = s.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }

            // Interactive Tooltip Callout
            if (selectedPointInfo != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        text = selectedPointInfo!!,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Canvas Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(chart) {
                        detectTapGestures(
                            onTap = { offset ->
                                selectedPointInfo = resolveTouchInfo(offset, size.width.toFloat(), size.height.toFloat(), chart)
                            }
                        )
                    }
                    .pointerInput(chart) {
                        detectDragGestures { change, _ ->
                            selectedPointInfo = resolveTouchInfo(change.position, size.width.toFloat(), size.height.toFloat(), chart)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height

                    when (chart.chartType) {
                        ChartType.LINE -> drawLineChart(
                            chart = chart,
                            anim = animProgress.value,
                            textMeasurer = textMeasurer,
                            gridColor = outlineVariant.copy(alpha = 0.35f),
                            labelColor = onSurfaceVariant,
                            isArea = false
                        )
                        ChartType.AREA -> drawLineChart(
                            chart = chart,
                            anim = animProgress.value,
                            textMeasurer = textMeasurer,
                            gridColor = outlineVariant.copy(alpha = 0.35f),
                            labelColor = onSurfaceVariant,
                            isArea = true
                        )
                        ChartType.BAR -> drawBarChart(
                            chart = chart,
                            anim = animProgress.value,
                            textMeasurer = textMeasurer,
                            gridColor = outlineVariant.copy(alpha = 0.35f),
                            labelColor = onSurfaceVariant
                        )
                        ChartType.PIE -> drawPieChart(
                            chart = chart,
                            anim = animProgress.value,
                            isDonut = false
                        )
                        ChartType.DONUT -> drawPieChart(
                            chart = chart,
                            anim = animProgress.value,
                            isDonut = true
                        )
                    }
                }
            }
        }
    }
}

private fun resolveTouchInfo(offset: Offset, width: Float, height: Float, chart: GenUiChart): String? {
    if (chart.series.isEmpty()) return null
    val firstSeries = chart.series.firstOrNull() ?: return null
    val points = firstSeries.data
    if (points.isEmpty()) return null

    val leftMargin = 40f
    val rightMargin = 20f
    val plotWidth = (width - leftMargin - rightMargin).coerceAtLeast(1f)

    val relativeX = offset.x - leftMargin
    if (relativeX < 0 || relativeX > plotWidth) return null

    val index = ((relativeX / plotWidth) * points.size).toInt().coerceIn(0, points.size - 1)
    val pt = points[index]

    val details = chart.series.joinToString(" | ") { s ->
        val matchingPt = s.data.getOrNull(index)
        "${s.name}: ${matchingPt?.y ?: "-"}"
    }
    return "${pt.x} (${details})"
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineChart(
    chart: GenUiChart,
    anim: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    gridColor: Color,
    labelColor: Color,
    isArea: Boolean
) {
    val leftMargin = 42.dp.toPx()
    val rightMargin = 16.dp.toPx()
    val topMargin = 16.dp.toPx()
    val bottomMargin = 28.dp.toPx()

    val plotW = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
    val plotH = (size.height - topMargin - bottomMargin).coerceAtLeast(1f)

    val allPoints = chart.series.flatMap { it.data }
    val minY = minOf(0.0, allPoints.minOfOrNull { it.y } ?: 0.0)
    val maxY = (allPoints.maxOfOrNull { it.y } ?: 1.0).coerceAtLeast(minY + 1.0)

    // Horizontal grid lines (4 levels)
    val gridCount = 4
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    for (i in 0..gridCount) {
        val y = topMargin + plotH - (i.toFloat() / gridCount) * plotH
        val valAtLine = minY + (i.toDouble() / gridCount) * (maxY - minY)
        drawLine(
            color = gridColor,
            start = Offset(leftMargin, y),
            end = Offset(leftMargin + plotW, y),
            pathEffect = dashEffect,
            strokeWidth = 1.dp.toPx()
        )
        val textLayout = textMeasurer.measure(
            text = String.format("%.0f", valAtLine),
            style = TextStyle(fontSize = 9.sp, color = labelColor)
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(4.dp.toPx(), y - textLayout.size.height / 2f)
        )
    }

    // Series lines and area
    chart.series.forEachIndexed { sIdx, s ->
        val color = parseColorSafely(s.color, DEFAULT_CHART_PALETTE[sIdx % DEFAULT_CHART_PALETTE.size])
        val points = s.data
        if (points.isEmpty()) return@forEachIndexed

        val path = Path()
        val areaPath = Path()

        points.forEachIndexed { i, pt ->
            val x = leftMargin + (i.toFloat() / (points.size - 1).coerceAtLeast(1)) * plotW
            val normalizedY = ((pt.y - minY) / (maxY - minY)).toFloat().coerceIn(0f, 1f)
            val y = topMargin + plotH - normalizedY * plotH * anim

            if (i == 0) {
                path.moveTo(x, y)
                if (isArea) {
                    areaPath.moveTo(x, topMargin + plotH)
                    areaPath.lineTo(x, y)
                }
            } else {
                val prevPt = points[i - 1]
                val prevX = leftMargin + ((i - 1).toFloat() / (points.size - 1).coerceAtLeast(1)) * plotW
                val prevNormalizedY = ((prevPt.y - minY) / (maxY - minY)).toFloat().coerceIn(0f, 1f)
                val prevY = topMargin + plotH - prevNormalizedY * plotH * anim

                val ctrlX1 = prevX + (x - prevX) / 2f
                val ctrlX2 = ctrlX1
                path.cubicTo(ctrlX1, prevY, ctrlX2, y, x, y)
                if (isArea) {
                    areaPath.cubicTo(ctrlX1, prevY, ctrlX2, y, x, y)
                }
            }

            if (i == points.size - 1 && isArea) {
                areaPath.lineTo(x, topMargin + plotH)
                areaPath.close()
            }
        }

        if (isArea) {
            val gradient = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.03f)),
                startY = topMargin,
                endY = topMargin + plotH
            )
            drawPath(path = areaPath, brush = gradient)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Draw point dots
        points.forEachIndexed { i, pt ->
            val x = leftMargin + (i.toFloat() / (points.size - 1).coerceAtLeast(1)) * plotW
            val normalizedY = ((pt.y - minY) / (maxY - minY)).toFloat().coerceIn(0f, 1f)
            val y = topMargin + plotH - normalizedY * plotH * anim
            drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(x, y))

            // X-axis label
            if (sIdx == 0 && (points.size <= 8 || i % (points.size / 4).coerceAtLeast(1) == 0)) {
                val xLayout = textMeasurer.measure(
                    text = pt.x,
                    style = TextStyle(fontSize = 9.sp, color = labelColor)
                )
                drawText(
                    textLayoutResult = xLayout,
                    topLeft = Offset(x - xLayout.size.width / 2f, topMargin + plotH + 4.dp.toPx())
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBarChart(
    chart: GenUiChart,
    anim: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    gridColor: Color,
    labelColor: Color
) {
    val leftMargin = 42.dp.toPx()
    val rightMargin = 16.dp.toPx()
    val topMargin = 16.dp.toPx()
    val bottomMargin = 28.dp.toPx()

    val plotW = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
    val plotH = (size.height - topMargin - bottomMargin).coerceAtLeast(1f)

    val allPoints = chart.series.flatMap { it.data }
    val minY = 0.0
    val maxY = (allPoints.maxOfOrNull { it.y } ?: 1.0).coerceAtLeast(1.0)

    // Grid lines
    val gridCount = 4
    for (i in 0..gridCount) {
        val y = topMargin + plotH - (i.toFloat() / gridCount) * plotH
        val valAtLine = minY + (i.toDouble() / gridCount) * (maxY - minY)
        drawLine(
            color = gridColor,
            start = Offset(leftMargin, y),
            end = Offset(leftMargin + plotW, y),
            strokeWidth = 1.dp.toPx()
        )
        val textLayout = textMeasurer.measure(
            text = String.format("%.0f", valAtLine),
            style = TextStyle(fontSize = 9.sp, color = labelColor)
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(4.dp.toPx(), y - textLayout.size.height / 2f)
        )
    }

    val numCategories = chart.series.firstOrNull()?.data?.size ?: 0
    if (numCategories == 0) return
    val numSeries = chart.series.size
    val slotW = plotW / numCategories
    val barW = (slotW * 0.7f) / numSeries.coerceAtLeast(1)

    for (cIdx in 0 until numCategories) {
        val catX = leftMargin + cIdx * slotW + (slotW * 0.15f)
        chart.series.forEachIndexed { sIdx, s ->
            val color = parseColorSafely(s.color, DEFAULT_CHART_PALETTE[sIdx % DEFAULT_CHART_PALETTE.size])
            val pt = s.data.getOrNull(cIdx) ?: return@forEachIndexed
            val normalizedY = (pt.y / maxY).toFloat().coerceIn(0f, 1f)
            val barHeight = normalizedY * plotH * anim
            val barTop = topMargin + plotH - barHeight
            val barLeft = catX + sIdx * barW

            drawRoundRect(
                color = color,
                topLeft = Offset(barLeft, barTop),
                size = Size(barW - 2f, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Category Label under first series
            if (sIdx == 0) {
                val xLayout = textMeasurer.measure(
                    text = pt.x,
                    style = TextStyle(fontSize = 9.sp, color = labelColor)
                )
                drawText(
                    textLayoutResult = xLayout,
                    topLeft = Offset(catX + (slotW * 0.7f) / 2f - xLayout.size.width / 2f, topMargin + plotH + 4.dp.toPx())
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPieChart(
    chart: GenUiChart,
    anim: Float,
    isDonut: Boolean
) {
    val series = chart.series.firstOrNull() ?: return
    val points = series.data
    val total = points.sumOf { it.y.coerceAtLeast(0.0) }
    if (total <= 0.0) return

    val diameter = minOf(size.width, size.height) * 0.85f
    val radius = diameter / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val pieSize = Size(diameter, diameter)

    var currentAngle = -90f
    points.forEachIndexed { i, pt ->
        val sweepAngle = ((pt.y.coerceAtLeast(0.0) / total) * 360f * anim).toFloat()
        val color = parseColorSafely(null, DEFAULT_CHART_PALETTE[i % DEFAULT_CHART_PALETTE.size])

        if (isDonut) {
            drawArc(
                color = color,
                startAngle = currentAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = pieSize,
                style = Stroke(width = 28.dp.toPx())
            )
        } else {
            drawArc(
                color = color,
                startAngle = currentAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = topLeft,
                size = pieSize
            )
        }
        currentAngle += sweepAngle
    }
}

