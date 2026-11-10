package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Domain model representing a conversation session with its history.
 */
@Serializable
data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val targetId: String? = null,
    val responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
    val systemPromptOverride: String? = null,
    val paramOverrides: GenerationParams? = null,
    val pinned: Boolean = false,
    val dotId: String? = null
) {
    val messageCount: Int get() = messages.size
    val previewText: String
        get() = messages.lastOrNull()?.content?.take(60) ?: "No messages"
}
