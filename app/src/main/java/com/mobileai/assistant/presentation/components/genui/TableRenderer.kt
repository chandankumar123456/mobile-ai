package com.mobileai.assistant.presentation.components.genui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.genui.ColumnAlignment
import com.mobileai.assistant.domain.genui.GenUiTable

@Composable
fun TableRenderer(
    table: GenUiTable,
    modifier: Modifier = Modifier
) {
    var sortColumnKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    val sortedRows = remember(table.rows, sortColumnKey, sortAscending) {
        val key = sortColumnKey ?: return@remember table.rows
        table.rows.sortedWith { rowA, rowB ->
            val valA = rowA[key].orEmpty()
            val valB = rowB[key].orEmpty()
            val numA = valA.replace("$", "").replace("€", "").replace("£", "").replace(",", "").replace("%", "").trim().toDoubleOrNull()
            val numB = valB.replace("$", "").replace("€", "").replace("£", "").replace(",", "").replace("%", "").trim().toDoubleOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                valA.compareTo(valB, ignoreCase = true)
            }
            if (sortAscending) cmp else -cmp
        }
    }

    val pageSize = table.pageSize
    val totalPages = if (pageSize != null && pageSize > 0) {
        ((sortedRows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    } else 1

    val pagedRows = remember(sortedRows, currentPage, pageSize) {
        if (pageSize != null && pageSize > 0) {
            val start = (currentPage * pageSize).coerceIn(0, sortedRows.size)
            val end = (start + pageSize).coerceIn(0, sortedRows.size)
            sortedRows.subList(start, end)
        } else {
            sortedRows
        }
    }

    val scrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (!table.title.isNullOrBlank()) {
                Text(
                    text = table.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Scrollable Table Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .horizontalScroll(scrollState)
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        table.columns.forEach { col ->
                            val colWidth = (110.dp * col.widthWeight.coerceIn(0.6f, 3.0f))
                            val isSorted = sortColumnKey == col.key
                            val alignment = when (col.alignment) {
                                ColumnAlignment.LEFT -> Alignment.CenterStart
                                ColumnAlignment.CENTER -> Alignment.Center
                                ColumnAlignment.RIGHT -> Alignment.CenterEnd
                            }
                            val textAlign = when (col.alignment) {
                                ColumnAlignment.LEFT -> TextAlign.Start
                                ColumnAlignment.CENTER -> TextAlign.Center
                                ColumnAlignment.RIGHT -> TextAlign.End
                            }

                            Box(
                                modifier = Modifier
                                    .width(colWidth)
                                    .padding(horizontal = 6.dp)
                                    .clickable(enabled = table.sortable) {
                                        if (sortColumnKey == col.key) {
                                            if (sortAscending) {
                                                sortAscending = false
                                            } else {
                                                sortColumnKey = null
                                                sortAscending = true
                                            }
                                        } else {
                                            sortColumnKey = col.key
                                            sortAscending = true
                                        }
                                    },
                                contentAlignment = alignment
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = col.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = textAlign
                                    )
                                    if (isSorted) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Data Rows
                    if (pagedRows.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No data available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    } else {
                        pagedRows.forEachIndexed { rowIndex, rowMap ->
                            val rowBg = if (rowIndex % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            Row(
                                modifier = Modifier
                                    .background(rowBg)
                                    .padding(vertical = 9.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                table.columns.forEach { col ->
                                    val colWidth = (110.dp * col.widthWeight.coerceIn(0.6f, 3.0f))
                                    val textAlign = when (col.alignment) {
                                        ColumnAlignment.LEFT -> TextAlign.Start
                                        ColumnAlignment.CENTER -> TextAlign.Center
                                        ColumnAlignment.RIGHT -> TextAlign.End
                                    }
                                    val cellValue = rowMap[col.key]?.trim()

                                    Box(
                                        modifier = Modifier
                                            .width(colWidth)
                                            .padding(horizontal = 6.dp)
                                    ) {
                                        if (cellValue.isNullOrEmpty()) {
                                            Text(
                                                text = "—",
                                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                textAlign = textAlign,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Text(
                                                text = cellValue,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = textAlign,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowIndex < pagedRows.size - 1) {
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }
                        }
                    }
                }
            }

            // Pagination Footer
            if (pageSize != null && sortedRows.size > pageSize) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages (${sortedRows.size} total)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Page",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { if (currentPage < totalPages - 1) currentPage++ },
                            enabled = currentPage < totalPages - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Page",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

