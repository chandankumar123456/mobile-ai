package com.mobileai.assistant.data.remote

/**
 * Robust streaming delimiter parser for extracting <think>...</think> reasoning traces
 * across arbitrary token boundaries without syntax leaking into response text.
 */
class StreamingTagParser(
    private val openTag: String = "<think>",
    private val closeTag: String = "</think>"
) {
    private val buffer = StringBuilder()
    var isInsideTag: Boolean = false
        private set

    data class Output(
        val text: String = "",
        val reasoning: String? = null,
        val isThinking: Boolean = false
    )

    fun process(chunk: String): List<Output> {
        if (chunk.isEmpty()) return emptyList()

        val outputs = mutableListOf<Output>()
        buffer.append(chunk)

        while (buffer.isNotEmpty()) {
            if (!isInsideTag) {
                val openIdx = buffer.indexOf(openTag)
                if (openIdx != -1) {
                    val before = buffer.substring(0, openIdx)
                    if (before.isNotEmpty()) {
                        outputs.add(Output(text = before, isThinking = false))
                    }
                    isInsideTag = true
                    buffer.delete(0, openIdx + openTag.length)
                } else {
                    // Check if tail of buffer matches a prefix of openTag
                    val prefixLen = maxPrefixMatch(buffer.toString(), openTag)
                    if (prefixLen > 0) {
                        val emitLen = buffer.length - prefixLen
                        if (emitLen > 0) {
                            outputs.add(Output(text = buffer.substring(0, emitLen), isThinking = false))
                            buffer.delete(0, emitLen)
                        }
                        break // Hold prefix candidate in buffer
                    } else {
                        outputs.add(Output(text = buffer.toString(), isThinking = false))
                        buffer.setLength(0)
                    }
                }
            } else {
                val closeIdx = buffer.indexOf(closeTag)
                if (closeIdx != -1) {
                    val thought = buffer.substring(0, closeIdx)
                    if (thought.isNotEmpty()) {
                        outputs.add(Output(reasoning = thought, isThinking = true))
                    }
                    isInsideTag = false
                    buffer.delete(0, closeIdx + closeTag.length)
                } else {
                    // Check if tail of buffer matches a prefix of closeTag
                    val prefixLen = maxPrefixMatch(buffer.toString(), closeTag)
                    if (prefixLen > 0) {
                        val emitLen = buffer.length - prefixLen
                        if (emitLen > 0) {
                            outputs.add(Output(reasoning = buffer.substring(0, emitLen), isThinking = true))
                            buffer.delete(0, emitLen)
                        }
                        break // Hold prefix candidate in buffer
                    } else {
                        outputs.add(Output(reasoning = buffer.toString(), isThinking = true))
                        buffer.setLength(0)
                    }
                }
            }
        }

        return outputs
    }

    fun finish(): List<Output> {
        val outputs = mutableListOf<Output>()
        if (buffer.isNotEmpty()) {
            val remaining = buffer.toString()
            buffer.setLength(0)
            if (isInsideTag) {
                outputs.add(Output(reasoning = remaining, isThinking = true))
            } else {
                outputs.add(Output(text = remaining, isThinking = false))
            }
        }
        return outputs
    }

    private fun maxPrefixMatch(text: String, target: String): Int {
        val maxLen = minOf(text.length, target.length - 1)
        for (len in maxLen downTo 1) {
            if (text.endsWith(target.substring(0, len))) {
                return len
            }
        }
        return 0
    }
}

