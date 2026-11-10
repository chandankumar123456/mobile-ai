package com.mobileai.assistant.data.repository

import android.content.Context
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import com.mobileai.assistant.domain.repository.ChatRepository
import com.mobileai.assistant.domain.repository.ConversationSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Persisted schema v2 envelope. The v1 file remains untouched so an older build
 * can still read it if the user downgrades.
 */
@Serializable
data class ConversationStoreEnvelope(
    val version: Int = 2,
    val activeConversationId: String? = null,
    val conversations: List<ConversationSession> = emptyList()
)

class ChatRepositoryImpl(
    private val context: Context? = null,
    rootDirOverride: File? = null,
    attachmentStore: AttachmentStore? = null
) : ChatRepository {

    internal constructor(rootDir: File) : this(null, rootDir, null)
    internal constructor(rootDir: File, attachmentStore: AttachmentStore?) : this(null, rootDir, attachmentStore)

    private val rootDir: File? = rootDirOverride ?: context?.filesDir
    private val attachmentStore: AttachmentStore? = attachmentStore
        ?: (rootDirOverride?.let { AttachmentStore(it) } ?: context?.let { AttachmentStore(it) })

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val saveLock = Any()
    private var lastCheckpointAt: Long = 0L
    private var streamingChunkCounter: Int = 0

    private val _conversations = MutableStateFlow<List<ConversationSession>>(emptyList())
    override val conversations: StateFlow<List<ConversationSession>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String>("")
    override val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        val loaded = loadFromDisk()
        val sorted = loaded.conversations.sortedByDescending { it.updatedAt }
        if (sorted.isNotEmpty()) {
            _conversations.value = sorted
            val preferredId = loaded.activeConversationId
                ?.takeIf { id -> sorted.any { it.id == id } }
                ?: sorted.first().id
            _activeConversationId.value = preferredId
            _messages.value = sorted.first { it.id == preferredId }.messages
            if (loaded.migrated || preferredId != loaded.activeConversationId) {
                saveToDisk()
            }
        } else {
            val initial = ConversationSession(id = UUID.randomUUID().toString(), title = "New Chat")
            _conversations.value = listOf(initial)
            _activeConversationId.value = initial.id
            _messages.value = emptyList()
            saveToDisk()
        }
    }

    private data class LoadedState(
        val conversations: List<ConversationSession>,
        val activeConversationId: String?,
        val migrated: Boolean
    )

    private fun loadFromDisk(): LoadedState {
        val dir = rootDir ?: return LoadedState(emptyList(), null, false)

        val v2 = File(dir, FILE_V2)
        if (v2.exists() && v2.length() > 0) {
            try {
                val envelope = json.decodeFromString<ConversationStoreEnvelope>(v2.readText())
                return LoadedState(
                    conversations = envelope.conversations,
                    activeConversationId = envelope.activeConversationId,
                    migrated = false
                )
            } catch (_: Exception) {
                // Corrupt v2: fall through and try to recover from v1.
            }
        }

        val v1 = File(dir, FILE_V1)
        if (v1.exists() && v1.length() > 0) {
            try {
                val content = v1.readText()
                val legacy = decodeLegacy(content)
                return LoadedState(legacy.first, legacy.second, true)
            } catch (_: Exception) {
                // Corrupt legacy file: start fresh rather than crashing.
            }
        }

        return LoadedState(emptyList(), null, false)
    }

    private fun decodeLegacy(content: String): Pair<List<ConversationSession>, String?> {
        return try {
            json.decodeFromString<List<ConversationSession>>(content) to null
        } catch (_: Exception) {
            val envelope = json.decodeFromString<ConversationStoreEnvelope>(content)
            envelope.conversations to envelope.activeConversationId
        }
    }

    private fun saveToDisk() {
        synchronized(saveLock) {
            saveToDiskLocked()
        }
    }

    /**
     * Must be called while holding [saveLock].
     */
    private fun saveToDiskLocked() {
        val dir = rootDir ?: return
        try {
            if (!dir.exists()) dir.mkdirs()
            val serialized = json.encodeToString(
                ConversationStoreEnvelope(
                    version = 2,
                    activeConversationId = _activeConversationId.value.takeIf { it.isNotBlank() },
                    conversations = _conversations.value
                )
            )
            val file = File(dir, FILE_V2)
            val tempFile = File(dir, "$FILE_V2.tmp")
            tempFile.writeText(serialized)
            if (!tempFile.renameTo(file)) {
                file.writeText(serialized)
                tempFile.delete()
            }
            lastCheckpointAt = System.currentTimeMillis()
            streamingChunkCounter = 0
        } catch (_: Exception) {
            // Non-critical persistence failure.
        }
    }

    /**
     * Must be called while holding [saveLock].
     */
    private fun maybeCheckpointLocked() {
        val now = System.currentTimeMillis()
        streamingChunkCounter++
        if (now - lastCheckpointAt >= CHECKPOINT_INTERVAL_MS || streamingChunkCounter >= CHECKPOINT_CHUNK_THRESHOLD) {
            saveToDiskLocked()
        }
    }

    override fun checkpoint() {
        synchronized(saveLock) {
            saveToDiskLocked()
        }
    }

    private fun syncActiveSessionWithMessages(newMessages: List<ChatMessage>, autoTitlePrompt: String? = null) {
        val activeId = _activeConversationId.value
        _conversations.value = _conversations.value.map { session ->
            if (session.id == activeId) {
                val updatedTitle = if (autoTitlePrompt != null && session.title == "New Chat") {
                    generateTitleFromPrompt(autoTitlePrompt)
                } else {
                    session.title
                }
                session.copy(
                    title = updatedTitle,
                    messages = newMessages,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                session
            }
        }
    }

    private fun generateTitleFromPrompt(prompt: String): String {
        val cleaned = prompt.lineSequence().firstOrNull()?.trim() ?: "New Chat"
        return if (cleaned.length > 36) {
            cleaned.take(33).trimEnd() + "..."
        } else if (cleaned.isBlank()) {
            "New Chat"
        } else {
            cleaned
        }
    }

    override fun addMessage(message: ChatMessage) {
        synchronized(saveLock) {
            val current = _messages.value
            val autoTitle = if (message.isUser && current.isEmpty()) message.content else null
            val updated = current + message
            _messages.value = updated
            syncActiveSessionWithMessages(updated, autoTitle)
            if (message.isStreaming) {
                lastCheckpointAt = 0L
            } else {
                saveToDiskLocked()
            }
        }
    }

    override fun updateLastMessage(
        content: String,
        reasoning: String?,
        isStreaming: Boolean,
        isError: Boolean,
        isThinking: Boolean
    ) {
        synchronized(saveLock) {
            val current = _messages.value
            if (current.isEmpty()) return
            val last = current.last()
            val updatedLast = last.copy(
                content = content,
                reasoning = reasoning ?: last.reasoning,
                isStreaming = isStreaming,
                isError = isError,
                isThinking = isThinking
            )
            val updated = current.dropLast(1) + updatedLast
            _messages.value = updated
            syncActiveSessionWithMessages(updated)
            if (isStreaming) {
                maybeCheckpointLocked()
            } else {
                saveToDiskLocked()
            }
        }
    }

    override fun appendToLastMessage(chunk: String) {
        synchronized(saveLock) {
            val current = _messages.value
            if (current.isEmpty()) return
            val last = current.last()
            val updated = current.dropLast(1) + last.copy(
                content = last.content + chunk,
                isStreaming = true,
                isThinking = false
            )
            _messages.value = updated
            syncActiveSessionWithMessages(updated)
            maybeCheckpointLocked()
        }
    }

    override fun appendReasoningToLastMessage(chunk: String) {
        synchronized(saveLock) {
            val current = _messages.value
            if (current.isEmpty()) return
            val last = current.last()
            val currentReasoning = last.reasoning ?: ""
            val updated = current.dropLast(1) + last.copy(
                reasoning = currentReasoning + chunk,
                isStreaming = true,
                isThinking = true
            )
            _messages.value = updated
            syncActiveSessionWithMessages(updated)
            maybeCheckpointLocked()
        }
    }

    override fun setLastMessageThinking(isThinking: Boolean) {
        synchronized(saveLock) {
            val current = _messages.value
            if (current.isEmpty()) return
            val last = current.last()
            val updated = current.dropLast(1) + last.copy(isThinking = isThinking)
            _messages.value = updated
            syncActiveSessionWithMessages(updated)
        }
    }

    override fun clearMessages() {
        synchronized(saveLock) {
            if (attachmentStore != null) {
                _messages.value.forEach { msg ->
                    try {
                        attachmentStore.deleteAttachmentsForMessage(msg)
                    } catch (_: Exception) {}
                }
            }
            _messages.value = emptyList()
            val activeId = _activeConversationId.value
            _conversations.value = _conversations.value.map { session ->
                if (session.id == activeId) {
                    session.copy(
                        title = "New Chat",
                        messages = emptyList(),
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    session
                }
            }
            saveToDiskLocked()
        }
    }

    override fun createNewSession(title: String, dotId: String?): ConversationSession {
        synchronized(saveLock) {
            val activeId = _activeConversationId.value
            val activeSession = _conversations.value.find { it.id == activeId }
            if (activeSession != null && activeSession.messages.isEmpty() &&
                activeSession.title == "New Chat" && title == "New Chat" &&
                activeSession.dotId == dotId
            ) {
                return activeSession
            }

            val newSession = ConversationSession(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                messages = emptyList(),
                dotId = dotId
            )
            _conversations.value = listOf(newSession) + _conversations.value
            _activeConversationId.value = newSession.id
            _messages.value = emptyList()
            saveToDiskLocked()
            return newSession
        }
    }

    override fun getConversationsByDot(dotId: String?): Flow<List<ConversationSession>> {
        return _conversations.map { list ->
            if (dotId == null) {
                list.filter { it.dotId == null || it.dotId.isBlank() }
            } else if (dotId == "builtin-general") {
                list.filter { it.dotId == "builtin-general" || it.dotId == null || it.dotId.isBlank() }
            } else {
                list.filter { it.dotId == dotId }
            }
        }
    }

    override fun selectSession(sessionId: String) {
        synchronized(saveLock) {
            val session = _conversations.value.find { it.id == sessionId } ?: return
            _activeConversationId.value = sessionId
            _messages.value = session.messages
            saveToDiskLocked()
        }
    }

    override fun renameSession(sessionId: String, newTitle: String) {
        synchronized(saveLock) {
            val trimmed = newTitle.trim().ifBlank { "Untitled Chat" }
            _conversations.value = _conversations.value.map {
                if (it.id == sessionId) it.copy(title = trimmed, updatedAt = System.currentTimeMillis()) else it
            }
            saveToDiskLocked()
        }
    }

    override fun deleteSession(sessionId: String) {
        synchronized(saveLock) {
            val currentList = _conversations.value
            val targetSession = currentList.find { it.id == sessionId }
            if (targetSession != null && attachmentStore != null) {
                targetSession.messages.forEach { msg ->
                    try {
                        attachmentStore.deleteAttachmentsForMessage(msg)
                    } catch (_: Exception) {}
                }
            }
            val remaining = currentList.filter { it.id != sessionId }
            if (remaining.isEmpty()) {
                val freshSession = ConversationSession(id = UUID.randomUUID().toString(), title = "New Chat")
                _conversations.value = listOf(freshSession)
                _activeConversationId.value = freshSession.id
                _messages.value = emptyList()
            } else {
                _conversations.value = remaining
                if (_activeConversationId.value == sessionId) {
                    val next = remaining.first()
                    _activeConversationId.value = next.id
                    _messages.value = next.messages
                }
            }
            saveToDiskLocked()
        }
    }

    override fun removeMessagesFrom(messageId: String): List<ChatMessage> {
        synchronized(saveLock) {
            val current = _messages.value
            val index = current.indexOfFirst { it.id == messageId }
            if (index == -1) return current

            if (attachmentStore != null) {
                val toDelete = current.drop(index)
                toDelete.forEach { msg ->
                    try {
                        attachmentStore.deleteAttachmentsForMessage(msg)
                    } catch (_: Exception) {}
                }
            }

            val kept = current.take(index)
            _messages.value = kept
            if (kept.isEmpty()) {
                val activeId = _activeConversationId.value
                _conversations.value = _conversations.value.map { session ->
                    if (session.id == activeId) {
                        session.copy(
                            title = "New Chat",
                            messages = emptyList(),
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        session
                    }
                }
            } else {
                syncActiveSessionWithMessages(kept)
            }
            saveToDiskLocked()
            return kept
        }
    }

    override fun deleteMessage(messageId: String) {
        synchronized(saveLock) {
            val current = _messages.value
            val target = current.find { it.id == messageId }
            if (target != null && attachmentStore != null) {
                try {
                    attachmentStore.deleteAttachmentsForMessage(target)
                } catch (_: Exception) {}
            }
            val updated = current.filter { it.id != messageId }
            _messages.value = updated
            syncActiveSessionWithMessages(updated)
            saveToDiskLocked()
        }
    }

    override fun replaceMessages(messages: List<ChatMessage>) {
        synchronized(saveLock) {
            _messages.value = messages
            syncActiveSessionWithMessages(messages)
            saveToDiskLocked()
        }
    }

    override fun getActiveSessionTitle(): String {
        val activeId = _activeConversationId.value
        return _conversations.value.find { it.id == activeId }?.title ?: "Chat"
    }

    override fun updateMessage(messageId: String, content: String) {
        updateMessage(messageId) { it.copy(content = content) }
    }

    override fun updateMessage(messageId: String, transform: (ChatMessage) -> ChatMessage) {
        updateMessageInternal(messageId, forcePersist = false, transform = transform)
    }

    override fun setMessageIncomplete(messageId: String, incomplete: Boolean, reason: String?) {
        updateMessageInternal(messageId, forcePersist = true) { message ->
            message.copy(
                isIncomplete = incomplete,
                stopReason = reason
            )
        }
    }

    override fun setMessageMetrics(messageId: String, metrics: RuntimeMetrics) {
        updateMessageInternal(messageId, forcePersist = true) { message ->
            message.copy(metrics = metrics)
        }
    }

    private fun updateMessageInternal(
        messageId: String,
        forcePersist: Boolean,
        transform: (ChatMessage) -> ChatMessage
    ) {
        synchronized(saveLock) {
            val current = _messages.value
            val index = current.indexOfFirst { it.id == messageId }
            if (index == -1) return
            val updatedMessage = transform(current[index])
            val updated = current.toMutableList().also { it[index] = updatedMessage }
            _messages.value = updated
            syncActiveSessionWithMessages(updated)
            if (forcePersist || !updatedMessage.isStreaming) {
                saveToDiskLocked()
            }
        }
    }

    override fun getSession(sessionId: String): ConversationSession? {
        synchronized(saveLock) {
            return _conversations.value.find { it.id == sessionId }
        }
    }

    override fun getConversation(sessionId: String): ConversationSession? = getSession(sessionId)

    override fun setConversationTarget(sessionId: String, targetId: String?) {
        mutateSession(sessionId) { it.copy(targetId = targetId) }
    }

    override fun setConversationLanguage(sessionId: String, language: ResponseLanguage) {
        mutateSession(sessionId) { it.copy(responseLanguage = language) }
    }

    override fun setConversationSystemPrompt(sessionId: String, prompt: String?) {
        mutateSession(sessionId) { it.copy(systemPromptOverride = prompt?.takeIf { value -> value.isNotBlank() }) }
    }

    override fun setConversationParams(sessionId: String, params: GenerationParams?) {
        mutateSession(sessionId) { it.copy(paramOverrides = params) }
    }

    override fun setConversationPinned(sessionId: String, pinned: Boolean) {
        mutateSession(sessionId) { it.copy(pinned = pinned) }
    }

    override fun setConversationDot(sessionId: String, dotId: String?) {
        mutateSession(sessionId) { it.copy(dotId = dotId) }
    }

    private fun mutateSession(sessionId: String, transform: (ConversationSession) -> ConversationSession) {
        synchronized(saveLock) {
            var found = false
            _conversations.value = _conversations.value.map { session ->
                if (session.id == sessionId) {
                    found = true
                    transform(session)
                } else {
                    session
                }
            }
            if (!found) return
            saveToDiskLocked()
        }
    }

    override fun search(query: String): List<ConversationSearchResult> {
        synchronized(saveLock) {
            val needle = query.trim()
            val sessions = _conversations.value.sortedByDescending { it.updatedAt }
            if (needle.isEmpty()) {
                return sessions.map { session ->
                    ConversationSearchResult(
                        sessionId = session.id,
                        title = session.title,
                        snippet = "",
                        messageId = null
                    )
                }
            }

            val results = mutableListOf<ConversationSearchResult>()
            for (session in sessions) {
                val titleMatch = session.title.contains(needle, ignoreCase = true)
                if (titleMatch) {
                    results += ConversationSearchResult(
                        sessionId = session.id,
                        title = session.title,
                        snippet = snippetAround(session.title, needle),
                        messageId = null
                    )
                    continue
                }

                val message = session.messages.firstOrNull {
                    it.content.contains(needle, ignoreCase = true)
                }
                if (message != null) {
                    results += ConversationSearchResult(
                        sessionId = session.id,
                        title = session.title,
                        snippet = snippetAround(message.content, needle),
                        messageId = message.id
                    )
                }
            }
            return results.take(MAX_SEARCH_RESULTS)
        }
    }

    override fun importSession(session: ConversationSession) {
        synchronized(saveLock) {
            val existingIndex = _conversations.value.indexOfFirst { it.id == session.id }
            _conversations.value = if (existingIndex >= 0) {
                _conversations.value.toMutableList().also { it[existingIndex] = session }
            } else {
                listOf(session) + _conversations.value
            }
            if (_activeConversationId.value == session.id) {
                _messages.value = session.messages
            }
            saveToDiskLocked()
        }
    }

    override fun exportCache(): List<ConversationSession> {
        synchronized(saveLock) {
            return _conversations.value.toList()
        }
    }

    private fun snippetAround(text: String, query: String): String {
        if (text.isBlank()) return ""
        val index = text.indexOf(query, ignoreCase = true)
        if (index < 0) return text.take(SNIPPET_LENGTH).trim()
        val half = SNIPPET_LENGTH / 2
        var start = (index - half).coerceAtLeast(0)
        val end = (start + SNIPPET_LENGTH).coerceAtMost(text.length)
        if (end - start < SNIPPET_LENGTH) {
            start = (end - SNIPPET_LENGTH).coerceAtLeast(0)
        }
        return text.substring(start, end).trim()
    }

    private companion object {
        const val FILE_V1 = "conversations_v1.json"
        const val FILE_V2 = "conversations_v2.json"
        const val CHECKPOINT_INTERVAL_MS = 2_000L
        const val CHECKPOINT_CHUNK_THRESHOLD = 200
        const val SNIPPET_LENGTH = 80
        const val MAX_SEARCH_RESULTS = 20
    }
}
