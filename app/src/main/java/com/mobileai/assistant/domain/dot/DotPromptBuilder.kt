package com.mobileai.assistant.domain.dot

import java.util.Locale

/**
 * Builds system prompts customized for a specific [DotProfile] with dynamic
 * injection of active [DotMemory] entries in `<dot_memory>` tags.
 *
 * Implements strict sanitization against:
 * 1. XML tag breakout (neutralizing `</dot_memory>` and `<dot_memory>`).
 * 2. Category header spoofing (flattening newlines and neutralizing forged bracketed category headers).
 * 3. Cross-dot memory leakage (strictly scoping memories to [dot.id]).
 */
object DotPromptBuilder {

    private val XML_TAG_REGEX = Regex("(?i)</?\\s*dot_memory\\s*>")
    private val CATEGORY_HEADER_REGEX = Regex("\\[(?i)(CORE_INSTRUCTION|USER_PREFERENCE|PROJECT_FACT|SESSION_NOTE)\\]")

    /**
     * Builds the complete system prompt for [dot], injecting [memories]
     * into a `<dot_memory>` block and appending any [DotModelPreference.systemPromptSuffix].
     *
     * Defensively filters [memories] to only those where [DotMemory.dotId] matches [dot.id].
     */
    fun buildSystemPrompt(
        dot: DotProfile,
        memories: List<DotMemory> = emptyList()
    ): String {
        val memoryBlock = formatMemories(memories, targetDotId = dot.id)
        val basePrompt = dot.systemPrompt.trim()
        val suffix = dot.modelPreference?.systemPromptSuffix?.trim()

        val parts = ArrayList<String>()
        if (basePrompt.isNotEmpty()) {
            parts.add(basePrompt)
        }
        if (memoryBlock.isNotEmpty()) {
            parts.add(memoryBlock)
        }
        if (!suffix.isNullOrEmpty()) {
            parts.add(suffix)
        }

        return parts.joinToString("\n\n")
    }

    /**
     * Formats a list of [DotMemory] items into a structured `<dot_memory>` XML block.
     * Optionally filters by [targetDotId].
     * Returns an empty string if [memories] is empty or contains only blank entries.
     */
    fun formatMemories(
        memories: List<DotMemory>,
        targetDotId: String? = null
    ): String {
        val validMemories = memories.filter { memory ->
            memory.content.isNotBlank() && (targetDotId == null || memory.dotId == targetDotId)
        }
        if (validMemories.isEmpty()) return ""

        val lines = validMemories.map { memory ->
            val conf = String.format(Locale.US, "%.1f", memory.confidence)
            val sanitized = sanitizeMemoryContent(memory.content)
            "[${memory.category.name}] $sanitized (confidence: $conf)"
        }

        return buildString {
            append("<dot_memory>\n")
            append(lines.joinToString("\n"))
            append("\n</dot_memory>")
        }
    }

    /**
     * Sanitizes memory content to prevent prompt breakout and category spoofing:
     * 1. Replaces `<dot_memory>` / `</dot_memory>` tags with XML-escaped entities.
     * 2. Flattens newlines (`\r\n`, `\r`, `\n`) into single spaces to enforce single-line memory entries.
     * 3. Sanitizes embedded category bracket headers `[CATEGORY]` into `(CATEGORY)`.
     */
    fun sanitizeMemoryContent(content: String): String {
        // 1. Neutralize XML fence breakout tags
        val escapedXml = XML_TAG_REGEX.replace(content) { match ->
            if (match.value.contains('/')) "&lt;/dot_memory&gt;" else "&lt;dot_memory&gt;"
        }

        // 2. Normalize newlines into single spaces to enforce single-line memory entries
        val singleLine = escapedXml
            .replace("\r\n", " ")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()

        // 3. Neutralize any bracketed category headers inside the content
        return CATEGORY_HEADER_REGEX.replace(singleLine) { match ->
            "(${match.groupValues[1].uppercase(Locale.US)})"
        }
    }
}
