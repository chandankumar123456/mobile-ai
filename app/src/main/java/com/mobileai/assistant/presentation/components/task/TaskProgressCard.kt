package com.mobileai.assistant.presentation.components.task

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.task.DotTaskStep
import com.mobileai.assistant.domain.task.StepStatus
import com.mobileai.assistant.domain.task.TaskStatus

/**
 * Native Jetpack Compose card displaying multi-step task progress, step timeline, and controls.
 */
@Composable
fun TaskProgressCard(
    task: DotTask,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (task.goalDescription.isNotBlank()) {
                        Text(
                            text = task.goalDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                TaskStatusChip(status = task.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar and fraction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { task.progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "${task.completedSteps}/${task.totalSteps}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Steps timeline
            if (task.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    task.steps.forEachIndexed { index, step ->
                        TaskStepRow(
                            stepNumber = index + 1,
                            step = step,
                            isCurrent = index == task.currentStepIndex && task.status == TaskStatus.RUNNING
                        )
                    }
                }
            }

            // Action Buttons
            if (!task.isTerminal) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onCancel != null) {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                    }

                    if (task.status == TaskStatus.RUNNING && onPause != null) {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause")
                        }
                    } else if (task.status == TaskStatus.PAUSED && onResume != null) {
                        OutlinedButton(
                            onClick = onResume,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskStatusChip(status: TaskStatus) {
    val (bgColor, textColor, label) = when (status) {
        TaskStatus.CREATED -> Triple(Color(0xFFEDE7F6), Color(0xFF5E35B1), "Created")
        TaskStatus.PLANNING -> Triple(Color(0xFFE1F5FE), Color(0xFF0288D1), "Planning")
        TaskStatus.QUEUED -> Triple(Color(0xFFE8EAF6), Color(0xFF3949AB), "Queued")
        TaskStatus.RUNNING -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), "Running")
        TaskStatus.PAUSED -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "Paused")
        TaskStatus.AWAITING_APPROVAL -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Approval Needed")
        TaskStatus.COMPLETED -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Completed")
        TaskStatus.FAILED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Failed")
        TaskStatus.CANCELLED -> Triple(Color(0xFFECEFF1), Color(0xFF546E7A), "Cancelled")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun TaskStepRow(
    stepNumber: Int,
    step: DotTaskStep,
    isCurrent: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step Status Icon
        StepStatusIcon(status = step.status, isCurrent = isCurrent)

        Spacer(modifier = Modifier.width(10.dp))

        // Step Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$stepNumber. ${step.title}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (step.toolName != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = step.toolName,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (step.description.isNotBlank()) {
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StepStatusIcon(status: StepStatus, isCurrent: Boolean) {
    val (icon, tint, bgColor) = when (status) {
        StepStatus.COMPLETED -> Triple(Icons.Default.Check, Color(0xFF2E7D32), Color(0xFFE8F5E9))
        StepStatus.RUNNING -> Triple(Icons.Default.PlayArrow, Color(0xFF0369A1), Color(0xFFE0F2FE))
        StepStatus.AWAITING_APPROVAL -> Triple(Icons.Default.Warning, Color(0xFFE65100), Color(0xFFFFF3E0))
        StepStatus.FAILED -> Triple(Icons.Default.Close, Color(0xFFC62828), Color(0xFFFFEBEE))
        StepStatus.PENDING -> Triple(Icons.Default.Schedule, Color(0xFF78909C), Color(0xFFECEFF1))
        StepStatus.SKIPPED -> Triple(Icons.Default.Close, Color(0xFF9E9E9E), Color(0xFFF5F5F5))
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}
