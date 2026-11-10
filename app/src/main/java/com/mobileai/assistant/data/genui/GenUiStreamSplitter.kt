package com.mobileai.assistant.data.genui

sealed interface GenUiSegment {
    data class Text(val content: String) : GenUiSegment
    data class Ui(
        val rawPayload: String,
        val isComplete: Boolean,
        val startOffset: Int = 0
    ) : GenUiSegment
}

object GenUiStreamSplitter {

    private val OPEN_FENCE_REGEX = Regex("(?m)(?:^|\\n)[ \\t]*```(?:genui|openui)[ \\t]*(?:\\r?\\n|$)")
    private val CLOSE_FENCE_REGEX = Regex("(?m)\\n[ \\t]*```[ \\t]*(?:\\r?\\n|$)")
    private val FENCE_PREFIXES = listOf(
        "\n```genui", "\n```genu", "\n```gen", "\n```ge", "\n```g", "\n```", "\n``", "\n`",
        "```genui", "```genu", "```gen", "```ge", "```g", "```"
    )

    /**
     * Splits cumulative message content into ordered Text and GenUi segments.
     * Pure function, safe for Compose remember(content, isStreaming).
     */
    fun split(content: String, isStreaming: Boolean = false): List<GenUiSegment> {
        if (content.isEmpty()) return emptyList()

        val segments = mutableListOf<GenUiSegment>()
        var cursor = 0
        val length = content.length

        while (cursor < length) {
            val remaining = content.substring(cursor)
            val openMatch = OPEN_FENCE_REGEX.find(remaining)

            if (openMatch == null) {
                var textChunk = remaining
                if (isStreaming) {
                    val matchedPrefix = FENCE_PREFIXES.firstOrNull { textChunk.endsWith(it) }
                    if (matchedPrefix != null) {
                        textChunk = textChunk.dropLast(matchedPrefix.length)
                    }
                }
                if (textChunk.isNotEmpty()) {
                    segments.add(GenUiSegment.Text(textChunk))
                }
                break
            }

            val textBeforeLen = openMatch.range.first
            if (textBeforeLen > 0) {
                val textChunk = remaining.substring(0, textBeforeLen)
                segments.add(GenUiSegment.Text(textChunk))
            }

            val payloadStart = cursor + openMatch.range.last + 1
            if (payloadStart >= length) {
                segments.add(GenUiSegment.Ui(rawPayload = "", isComplete = false, startOffset = payloadStart))
                break
            }

            val payloadRemaining = content.substring(payloadStart)
            val closeMatch = CLOSE_FENCE_REGEX.find(payloadRemaining)

            if (closeMatch != null) {
                val payload = payloadRemaining.substring(0, closeMatch.range.first).trim()
                segments.add(GenUiSegment.Ui(rawPayload = payload, isComplete = true, startOffset = payloadStart))
                cursor = payloadStart + closeMatch.range.last + 1
            } else {
                val payload = payloadRemaining.trim()
                segments.add(GenUiSegment.Ui(rawPayload = payload, isComplete = !isStreaming, startOffset = payloadStart))
                break
            }
        }

        return segments
    }

    /**
     * Convenience function for splitting non-streaming or full content.
     */
    fun splitSegments(content: String): List<GenUiSegment> = split(content, isStreaming = false)
}

