package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Domain model representing a message in a conversation.
 */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val tokenCount: Int? = null,
    val reasoning: String? = null,
    val isThinking: Boolean = false,
    val modelId: String? = null,
    val modelName: String? = null,
    val targetType: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val metrics: RuntimeMetrics? = null,
    val isIncomplete: Boolean = false,
    val stopReason: String? = null,
    val language: ResponseLanguage? = null,
    val editedAt: Long? = null,
    // GenUI Extensions (100% backward compatible with default = null)
    val genUiPayload: String? = null,
    val genUiState: String? = null,
    val genUiSubmittedValues: Map<String, String>? = null
)
