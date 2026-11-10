package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.repository.ChatRepository
import com.mobileai.assistant.domain.repository.ConversationSearchResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinator responsible for conversation session lifecycle, session switching,
 * message persistence, search indexing, and export operations.
 */
class ChatSessionCoordinator(
    private val chatRepository: ChatRepository,
    private val exportService: ConversationExportService = ConversationExportService()
) {
    val conversations: StateFlow<List<ConversationSession>> = chatRepository.conversations
    val activeConversationId: StateFlow<String> = chatRepository.activeConversationId
    val messages: StateFlow<List<ChatMessage>> = chatRepository.messages

    fun selectConversation(conversationId: String) {
        chatRepository.selectSession(conversationId)
    }

    fun createNewConversation(title: String = "New Chat", dotId: String? = null): ConversationSession {
        return chatRepository.createNewSession(title = title, dotId = dotId)
    }

    fun deleteConversation(conversationId: String) {
        chatRepository.deleteSession(conversationId)
    }

    fun updateActiveConversationTitle(newTitle: String) {
        val currentId = activeConversationId.value
        if (currentId.isNotBlank()) {
            chatRepository.renameSession(currentId, newTitle)
        }
    }

    fun clearActiveConversation() {
        chatRepository.clearMessages()
    }

    fun searchConversations(query: String): List<ConversationSearchResult> {
        return chatRepository.search(query)
    }

    fun exportActiveConversationMarkdown(): String? {
        val currentId = activeConversationId.value
        val session = conversations.value.find { it.id == currentId } ?: return null
        return exportService.exportMarkdown(session)
    }

    fun exportActiveConversationJson(): String? {
        val currentId = activeConversationId.value
        val session = conversations.value.find { it.id == currentId } ?: return null
        return exportService.exportJson(session)
    }
}
