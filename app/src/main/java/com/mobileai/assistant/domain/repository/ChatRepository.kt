package com.mobileai.assistant.domain.repository

import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Search hit for a conversation. [messageId] is present when the match was found
 * in a message rather than the conversation title.
 */
data class ConversationSearchResult(
    val sessionId: String,
    val title: String,
    val snippet: String,
    val messageId: String? = null
)

interface ChatRepository {
    val messages: StateFlow<List<ChatMessage>>
    val conversations: StateFlow<List<ConversationSession>>
    val activeConversationId: StateFlow<String>

    fun addMessage(message: ChatMessage)
    fun updateLastMessage(
        content: String,
        reasoning: String? = null,
        isStreaming: Boolean = false,
        isError: Boolean = false,
        isThinking: Boolean = false
    )
    fun appendToLastMessage(chunk: String)
    fun appendReasoningToLastMessage(chunk: String)
    fun setLastMessageThinking(isThinking: Boolean)
    fun clearMessages()

    // Multi-conversation session management
    fun createNewSession(title: String = "New Chat", dotId: String? = null): ConversationSession
    fun selectSession(sessionId: String)
    fun renameSession(sessionId: String, newTitle: String)
    fun deleteSession(sessionId: String)
    fun getConversationsByDot(dotId: String?): Flow<List<ConversationSession>>

    // Message actions
    fun removeMessagesFrom(messageId: String): List<ChatMessage>
    fun deleteMessage(messageId: String)
    fun getActiveSessionTitle(): String

    // Per-message mutations
    fun updateMessage(messageId: String, transform: (ChatMessage) -> ChatMessage)
    fun updateMessage(messageId: String, content: String) =
        updateMessage(messageId) { it.copy(content = content) }
    fun setMessageIncomplete(messageId: String, incomplete: Boolean, reason: String? = null)
    fun setMessageMetrics(messageId: String, metrics: RuntimeMetrics)
    fun replaceMessages(messages: List<ChatMessage>)

    // Session lookup and per-conversation configuration
    fun getSession(sessionId: String): ConversationSession?
    fun getConversation(sessionId: String): ConversationSession? = getSession(sessionId)
    fun setConversationTarget(sessionId: String, targetId: String?)
    fun setConversationLanguage(sessionId: String, language: ResponseLanguage)
    fun setConversationSystemPrompt(sessionId: String, prompt: String?)
    fun setConversationParams(sessionId: String, params: GenerationParams?)
    fun setConversationPinned(sessionId: String, pinned: Boolean)
    fun setConversationDot(sessionId: String, dotId: String?)

    // Search, import/export and checkpointing
    fun search(query: String): List<ConversationSearchResult>
    fun importSession(session: ConversationSession)
    fun exportCache(): List<ConversationSession>
    fun checkpoint()
}
