package com.mobileai.assistant.data.trace

import com.mobileai.assistant.domain.trace.TraceCategory
import com.mobileai.assistant.domain.trace.TraceEvent
import com.mobileai.assistant.domain.trace.TraceLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Local-first structured execution tracing engine with circular memory buffer.
 * Provides correlated tracing across inference, tool calls, MCP, agents, and GenUI.
 */
object TraceManager {

    private const val MAX_RING_BUFFER_SIZE = 500

    private val ringBuffer = ConcurrentLinkedDeque<TraceEvent>()
    private val _events = MutableStateFlow<List<TraceEvent>>(emptyList())
    val events: StateFlow<List<TraceEvent>> = _events.asStateFlow()

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun log(
        correlationId: String,
        category: TraceCategory,
        tag: String,
        message: String,
        level: TraceLevel = TraceLevel.INFO,
        durationMs: Long? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        val event = TraceEvent(
            correlationId = correlationId,
            category = category,
            level = level,
            tag = tag,
            message = message,
            durationMs = durationMs,
            attributes = attributes
        )

        ringBuffer.addLast(event)
        while (ringBuffer.size > MAX_RING_BUFFER_SIZE) {
            ringBuffer.pollFirst()
        }

        _events.value = ringBuffer.toList()
    }

    fun getEventsForCorrelation(correlationId: String): List<TraceEvent> {
        return ringBuffer.filter { it.correlationId == correlationId }
    }

    fun exportJson(): String {
        return json.encodeToString(ringBuffer.toList())
    }

    fun clear() {
        ringBuffer.clear()
        _events.value = emptyList()
    }
}

