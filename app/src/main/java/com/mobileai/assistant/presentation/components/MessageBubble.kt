package com.mobileai.assistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileai.assistant.data.voice.TextSanitizer
import com.mobileai.assistant.data.genui.GenUiSegment
import com.mobileai.assistant.data.genui.GenUiStreamSplitter
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.presentation.components.genui.GenUiView
import com.mobileai.assistant.presentation.components.markdown.MarkdownView
import com.mobileai.assistant.presentation.theme.AssistantBubbleDark
import com.mobileai.assistant.presentation.theme.AssistantBubbleLight
import com.mobileai.assistant.presentation.theme.OnUserBubble
import com.mobileai.assistant.presentation.theme.UserBubble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: ((String) -> Unit)? = null,
    onCopyMarkdown: ((String) -> Unit)? = null,
    onEdit: ((ChatMessage) -> Unit)? = null,
    onRegenerate: ((ChatMessage) -> Unit)? = null,
    onDelete: ((ChatMessage) -> Unit)? = null,
    onContinue: ((ChatMessage) -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    onPauseSpeak: (() -> Unit)? = null,
    onResumeSpeak: (() -> Unit)? = null,
    onStopSpeak: (() -> Unit)? = null,
    isSpeaking: Boolean = false,
    isPaused: Boolean = false,
    onGenUiEvent: ((String, GenUiEvent) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.isUser
    val isDark = isSystemInDarkTheme()
    var showCopyDropdown by remember { mutableStateOf(false) }

    val hasWideGenUi = remember(message.content) {
        message.content.contains("```genui") && (
            message.content.contains("\"table\"") ||
            message.content.contains("\"chart\"") ||
            message.content.contains("\"diagram\"")
        )
    }
    val maxBubbleWidth = if (hasWideGenUi) 620.dp else 380.dp

    val bubbleColor = when {
        isUser -> UserBubble
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isDark -> AssistantBubbleDark
        else -> AssistantBubbleLight
    }

    val textColor = when {
        isUser -> OnUserBubble
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Assistant Model Attribution Badge
                    if (!isUser && (!message.modelName.isNullOrBlank() || !message.modelId.isNullOrBlank())) {
                        val modelLabel = message.modelName ?: message.modelId ?: ""
                        val isRemote = message.targetType?.uppercase() == "REMOTE"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isRemote) Icons.Default.Cloud else Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Attachments inside Message Bubble
                    if (message.attachments.isNotEmpty()) {
                        MessageAttachmentsView(
                            attachments = message.attachments,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Reasoning / Thinking Process block if present
                    if (!message.reasoning.isNullOrBlank() || message.isThinking) {
                        ThoughtProcessCard(
                            reasoning = message.reasoning ?: "",
                            isThinking = message.isThinking,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Main Message Content
                    if (isUser) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 22.sp
                            )
                        }
                    } else {
                        if (message.content.isEmpty() && message.isStreaming && !message.isThinking) {
                            Text(
                                text = "...",
                                color = textColor.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else if (message.content.isNotEmpty()) {
                            val segments = remember(message.content, message.isStreaming) {
                                GenUiStreamSplitter.split(message.content, isStreaming = message.isStreaming)
                            }
                            val hasGenUi = remember(segments) { segments.any { it is GenUiSegment.Ui } }

                            if (hasGenUi) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    segments.forEach { segment ->
                                        when (segment) {
                                            is GenUiSegment.Text -> {
                                                if (segment.content.isNotBlank()) {
                                                    SelectionContainer {
                                                        MarkdownView(
                                                            markdown = segment.content,
                                                            textColor = textColor,
                                                            isStreaming = message.isStreaming
                                                        )
                                                    }
                                                }
                                            }
                                            is GenUiSegment.Ui -> {
                                                GenUiView(
                                                    rawPayload = segment.rawPayload,
                                                    isStreaming = message.isStreaming && !segment.isComplete,
                                                    isSubmitted = message.genUiState == "SUBMITTED",
                                                    onEvent = { event -> onGenUiEvent?.invoke(message.id, event) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                SelectionContainer {
                                    MarkdownView(
                                        markdown = message.content,
                                        textColor = textColor,
                                        isStreaming = message.isStreaming
                                    )
                                }
                            }
                        } else if (!message.isStreaming && message.reasoning.isNullOrBlank()) {
                            Text(
                                text = "(Empty response)",
                                color = textColor.copy(alpha = 0.5f),
                                fontStyle = FontStyle.Italic,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Streaming indicator
                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(10.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (message.isThinking) "thinking..." else "generating...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // "Continue" button for incomplete responses
                    if (!isUser && message.isIncomplete && !message.isStreaming) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!message.stopReason.isNullOrBlank()) "Length limit reached" else "Incomplete",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (onContinue != null) {
                                Button(
                                    onClick = { onContinue(message) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Continue", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    // Runtime Metrics Row (Inside assistant bubble)
                    if (!isUser && !message.isStreaming && message.metrics != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        RuntimeMetricsRow(metrics = message.metrics)
                    }
                }
            }
        }

        // Timestamp & Action Toolbar Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // Token / Word stats on assistant messages
            if (!isUser && !message.isStreaming && message.content.isNotBlank()) {
                val words = message.content.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• $words words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Copy Action with Dropdown for Plain Text vs Markdown
            if (message.content.isNotBlank() && onCopy != null) {
                Box {
                    IconButton(
                        onClick = {
                            if (isUser) {
                                onCopy(message.content)
                            } else {
                                showCopyDropdown = true
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showCopyDropdown,
                        onDismissRequest = { showCopyDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy as Markdown") },
                            leadingIcon = {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showCopyDropdown = false
                                (onCopyMarkdown ?: onCopy)(message.content)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy as Plain Text") },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showCopyDropdown = false
                                onCopy(TextSanitizer.cleanForSpeech(message.content))
                            }
                        )
                    }
                }
            }

            // Edit Action (User messages only)
            if (isUser && onEdit != null) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = { onEdit(message) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit and resend message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Regenerate Action (Assistant messages only)
            if (!isUser && !message.isStreaming && onRegenerate != null) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = { onRegenerate(message) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate response",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Delete Action
            if (onDelete != null && !message.isStreaming) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = { onDelete(message) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Text-to-Speech playback controls on assistant messages
            if (!isUser && !message.isStreaming && message.content.isNotBlank() && onSpeak != null) {
                Spacer(modifier = Modifier.width(4.dp))
                when {
                    isSpeaking -> {
                        IconButton(
                            onClick = { onPauseSpeak?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause Speech",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = { onStopSpeak?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Speech",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    isPaused -> {
                        IconButton(
                            onClick = { onResumeSpeak?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume Speech",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = { onStopSpeak?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Speech",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = { onSpeak() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak Aloud",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
