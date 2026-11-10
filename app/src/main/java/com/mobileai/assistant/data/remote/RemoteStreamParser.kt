package com.mobileai.assistant.data.remote

import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class RemoteUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class RemoteSseChunk(
    val content: String? = null,
    val reasoning: String? = null,
    val usage: RemoteUsage? = null
)

/**
 * Pure SSE chunk parsing shared by the client and unit tests.
 */
object RemoteSseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseChunkData(data: String): RemoteSseChunk? {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            val delta = choice?.get("delta") as? JsonObject

            val reasoning = delta?.string("reasoning_content") ?: delta?.string("thought")
            val content = delta?.string("content")

            val usageObject = root["usage"] as? JsonObject
            val usage = usageObject?.let {
                RemoteUsage(
                    promptTokens = it.int("prompt_tokens") ?: 0,
                    completionTokens = it.int("completion_tokens") ?: 0,
                    totalTokens = it.int("total_tokens") ?: 0
                )
            }

            if (reasoning.isNullOrEmpty() && content.isNullOrEmpty() && usage == null) {
                null
            } else {
                RemoteSseChunk(
                    content = content?.takeIf { it.isNotEmpty() },
                    reasoning = reasoning?.takeIf { it.isNotEmpty() },
                    usage = usage
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? {
        val content = (this[name] as? JsonPrimitive)?.contentOrNull ?: return null
        return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt()
    }
}

/**
 * Accumulates token usage reported by an OpenAI-compatible stream. Providers may
 * send usage once at the end or repeatedly; taking the maximum of each field
 * avoids double counting either style.
 */
class UsageAccumulator {
    var promptTokens: Int = 0
        private set
    var completionTokens: Int = 0
        private set
    var totalTokens: Int = 0
        private set

    fun add(usage: RemoteUsage?) {
        if (usage == null) return
        promptTokens = maxOf(promptTokens, usage.promptTokens)
        completionTokens = maxOf(completionTokens, usage.completionTokens)
        totalTokens = maxOf(totalTokens, usage.totalTokens)
    }

    fun toMetrics(
        timeToFirstTokenMs: Long?,
        totalDurationMs: Long,
        contextSize: Int = 0,
        source: String = "REMOTE"
    ): RuntimeMetrics {
        val effectiveTotal = if (totalTokens > 0) totalTokens else promptTokens + completionTokens
        val generationMs = if (timeToFirstTokenMs != null) {
            (totalDurationMs - timeToFirstTokenMs).coerceAtLeast(0L)
        } else {
            totalDurationMs
        }
        val tokensPerSecond = if (completionTokens > 0 && generationMs > 0) {
            completionTokens * 1000.0 / generationMs
        } else {
            null
        }

        return RuntimeMetrics(
            timeToFirstTokenMs = timeToFirstTokenMs,
            totalDurationMs = totalDurationMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = effectiveTotal,
            tokensPerSecond = tokensPerSecond,
            contextUsed = effectiveTotal,
            contextSize = contextSize,
            source = source
        )
    }
}
