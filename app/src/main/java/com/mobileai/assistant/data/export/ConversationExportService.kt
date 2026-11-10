package com.mobileai.assistant.data.export

import com.mobileai.assistant.data.repository.ConversationStoreEnvelope
import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationExportService {

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val parserJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Exports a conversation session to formatted Markdown.
     */
    fun exportMarkdown(session: ConversationSession): String {
        val sb = StringBuilder()

        // Header
        sb.append("# ").append(session.title.ifBlank { "Conversation" }).append("\n\n")
        sb.append("- **Date:** ").append(formatDate(session.createdAt)).append("\n")
        if (!session.targetId.isNullOrBlank()) {
            sb.append("- **Target:** ").append(session.targetId).append("\n")
        }
        if (session.responseLanguage != ResponseLanguage.AUTO) {
            sb.append("- **Language:** ").append(session.responseLanguage.displayName).append("\n")
        }
        if (!session.systemPromptOverride.isNullOrBlank()) {
            sb.append("- **System Prompt:** ").append(session.systemPromptOverride).append("\n")
        }
        sb.append("\n---\n\n")

        // Messages
        session.messages.forEach { message ->
            appendMessageMarkdown(sb, message)
        }

        return sb.toString().trimEnd() + "\n"
    }

    /**
     * Exports a conversation session to pretty JSON.
     */
    fun exportJson(session: ConversationSession): String {
        return prettyJson.encodeToString(session)
    }

    /**
     * Exports multiple conversation sessions to pretty JSON.
     */
    fun exportAllJson(sessions: List<ConversationSession>): String {
        return prettyJson.encodeToString(sessions)
    }

    /**
     * Imports a conversation session from a JSON string.
     * Supports single session object, session array, or store envelope format.
     */
    fun importJson(jsonString: String): Result<ConversationSession> = runCatching {
        require(jsonString.isNotBlank()) { "JSON input is blank" }
        val trimmed = jsonString.trim()

        if (trimmed.startsWith("[")) {
            val sessions = parserJson.decodeFromString<List<ConversationSession>>(trimmed)
            require(sessions.isNotEmpty()) { "Session array is empty" }
            return@runCatching sessions.first()
        }

        // If it contains "conversations", it is an envelope with multiple sessions
        if (trimmed.contains("\"conversations\"")) {
            val envelope = parserJson.decodeFromString<ConversationStoreEnvelope>(trimmed)
            require(envelope.conversations.isNotEmpty()) { "No conversations found in envelope" }
            val active = envelope.activeConversationId?.let { activeId ->
                envelope.conversations.find { it.id == activeId }
            }
            return@runCatching active ?: envelope.conversations.first()
        }

        // Otherwise parse single session
        return@runCatching parserJson.decodeFromString<ConversationSession>(trimmed)
    }

    private fun appendMessageMarkdown(sb: StringBuilder, message: ChatMessage) {
        if (message.isUser) {
            sb.append("### User:\n\n")
        } else {
            val modelLabel = message.modelName ?: message.modelId
            if (!modelLabel.isNullOrBlank()) {
                sb.append("### Assistant (Model: ").append(modelLabel).append("):\n\n")
            } else {
                sb.append("### Assistant:\n\n")
            }
        }

        // Attachments
        if (message.attachments.isNotEmpty()) {
            sb.append("**Attachments:**\n")
            message.attachments.forEach { attachment ->
                sb.append("- ")
                    .append(attachment.fileName)
                    .append(" (")
                    .append(attachment.mimeType)
                    .append(", ")
                    .append(attachment.sizeBytes)
                    .append(" bytes)\n")
            }
            sb.append("\n")
        }

        // Thought process / reasoning
        if (!message.reasoning.isNullOrBlank()) {
            sb.append("<details><summary>Thought Process</summary>\n\n")
                .append(message.reasoning)
                .append("\n\n</details>\n\n")
        }

        // Message text
        if (message.content.isNotBlank()) {
            sb.append(message.content).append("\n\n")
        }

        // Metrics
        message.metrics?.let { metrics ->
            val metricsText = formatMetrics(metrics)
            if (metricsText.isNotBlank()) {
                sb.append("*Metrics: ").append(metricsText).append("*\n\n")
            }
        }
    }

    private fun formatMetrics(metrics: RuntimeMetrics): String {
        val parts = mutableListOf<String>()
        val tokenCount = if (metrics.completionTokens > 0) {
            metrics.completionTokens
        } else if (metrics.totalTokens > 0) {
            metrics.totalTokens
        } else {
            0
        }
        if (tokenCount > 0) {
            parts += "$tokenCount tokens"
        }
        metrics.tokensPerSecond?.let { tps ->
            parts += String.format(Locale.ROOT, "%.1f t/s", tps)
        }
        metrics.totalDurationMs?.let { duration ->
            parts += "${duration}ms"
        }
        return parts.joinToString(", ")
    }

    private fun formatDate(timestampMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(timestampMs))
    }
}
