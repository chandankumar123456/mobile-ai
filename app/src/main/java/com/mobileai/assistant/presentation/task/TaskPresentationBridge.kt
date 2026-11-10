package com.mobileai.assistant.presentation.task

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.tool.ToolApprovalRequest
import com.mobileai.assistant.domain.tool.ToolSensitivity
import com.mobileai.assistant.presentation.components.task.TaskProgressCard as ComponentTaskProgressCard
import com.mobileai.assistant.presentation.components.task.ToolApprovalCard as ComponentToolApprovalCard

@Composable
fun ToolApprovalCard(
    request: ToolApprovalRequest,
    sensitivity: ToolSensitivity = ToolSensitivity.SENSITIVE,
    onApprove: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onReject: (reason: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    ComponentToolApprovalCard(
        request = request,
        sensitivity = sensitivity,
        onApprove = onApprove,
        onAlwaysAllow = onAlwaysAllow,
        onReject = onReject,
        modifier = modifier
    )
}

@Composable
fun TaskProgressCard(
    task: DotTask,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ComponentTaskProgressCard(
        task = task,
        onPause = onPause,
        onResume = onResume,
        onCancel = onCancel,
        modifier = modifier
    )
}
