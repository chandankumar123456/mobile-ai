package com.mobileai.assistant.data.a2ui

import com.mobileai.assistant.domain.a2ui.A2UiComponentDefinition
import com.mobileai.assistant.domain.a2ui.A2UiMessage
import com.mobileai.assistant.domain.a2ui.A2UiResolvedNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * State representing an active Google A2UI Surface.
 */
data class A2UiSurfaceState(
    val surfaceId: String,
    val catalogId: String? = null,
    val rootId: String = "",
    val flatComponents: Map<String, A2UiComponentDefinition> = emptyMap(),
    val dataModel: Map<String, String> = emptyMap(),
    val resolvedTree: A2UiResolvedNode? = null
)

/**
 * Enterprise manager for Google A2UI (Agent-to-User Interface) surfaces,
 * JSONL streaming message ingestion, flat tree resolution, and data binding.
 */
class A2UiSurfaceManager {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _surfaces = MutableStateFlow<Map<String, A2UiSurfaceState>>(emptyMap())
    val surfaces: StateFlow<Map<String, A2UiSurfaceState>> = _surfaces.asStateFlow()

    /**
     * Parses a multi-line JSONL streaming string into discrete A2UI protocol messages.
     * Incomplete or trailing lines are safely ignored.
     */
    fun parseJsonl(jsonlString: String): List<A2UiMessage> {
        val messages = mutableListOf<A2UiMessage>()
        jsonlString.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    val root = json.parseToJsonElement(trimmed).jsonObject
                    val type = root["type"]?.jsonPrimitive?.content ?: ""
                    when (type) {
                        "surfaceUpdate" -> {
                            val surfaceId = root["surfaceId"]?.jsonPrimitive?.content ?: ""
                            val catalogId = root["catalogId"]?.jsonPrimitive?.content
                            val rootId = root["root"]?.jsonPrimitive?.content ?: ""
                            val compsArray = root["components"]?.jsonArray
                            val components = compsArray?.mapNotNull { el ->
                                val obj = el.jsonObject
                                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                val comp = obj["component"]?.jsonPrimitive?.content ?: "Card"
                                val props = obj["properties"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                                val children = obj["children"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                                A2UiComponentDefinition(id = id, component = comp, properties = props, children = children)
                            } ?: emptyList()

                            messages.add(A2UiMessage.SurfaceUpdate(surfaceId, catalogId, rootId, components))
                        }
                        "dataModelUpdate" -> {
                            val surfaceId = root["surfaceId"]?.jsonPrimitive?.content ?: ""
                            val path = root["path"]?.jsonPrimitive?.content
                            val data = root["data"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                            messages.add(A2UiMessage.DataModelUpdate(surfaceId, path, data))
                        }
                        "action" -> {
                            val surfaceId = root["surfaceId"]?.jsonPrimitive?.content ?: ""
                            val actionName = root["actionName"]?.jsonPrimitive?.content ?: ""
                            val params = root["parameters"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                            messages.add(A2UiMessage.Action(surfaceId, actionName, params))
                        }
                        "deleteSurface" -> {
                            val surfaceId = root["surfaceId"]?.jsonPrimitive?.content ?: ""
                            messages.add(A2UiMessage.DeleteSurface(surfaceId))
                        }
                    }
                } catch (_: Exception) {
                    // Tolerant parsing for partial JSONL line
                }
            }
        }
        return messages
    }

    /**
     * Ingests and processes an A2UI message, updating reactive surface state.
     */
    fun processMessage(message: A2UiMessage) {
        val current = _surfaces.value.toMutableMap()
        when (message) {
            is A2UiMessage.SurfaceUpdate -> {
                val surfaceId = message.surfaceId
                val existing = current[surfaceId] ?: A2UiSurfaceState(surfaceId = surfaceId)
                val newFlatMap = existing.flatComponents.toMutableMap()
                message.components.forEach { comp ->
                    newFlatMap[comp.id] = comp
                }

                val resolved = resolveFlatTree(message.root, newFlatMap, existing.dataModel)
                current[surfaceId] = existing.copy(
                    catalogId = message.catalogId ?: existing.catalogId,
                    rootId = message.root,
                    flatComponents = newFlatMap,
                    resolvedTree = resolved
                )
            }
            is A2UiMessage.DataModelUpdate -> {
                val surfaceId = message.surfaceId
                val existing = current[surfaceId] ?: return
                val updatedData = existing.dataModel + message.data
                val reResolved = resolveFlatTree(existing.rootId, existing.flatComponents, updatedData)
                current[surfaceId] = existing.copy(
                    dataModel = updatedData,
                    resolvedTree = reResolved
                )
            }
            is A2UiMessage.DeleteSurface -> {
                current.remove(message.surfaceId)
            }
            is A2UiMessage.Action -> {
                // Handled upstream by action dispatcher
            }
        }
        _surfaces.value = current
    }

    /**
     * Assembles the hierarchical tree from flat component definitions starting at [rootId].
     * Resolves reactive data bindings like `{{data.key}}` using [dataModel].
     */
    fun resolveFlatTree(
        rootId: String,
        flatComponents: Map<String, A2UiComponentDefinition>,
        dataModel: Map<String, String>,
        visited: Set<String> = emptySet()
    ): A2UiResolvedNode? {
        if (rootId.isBlank() || visited.contains(rootId)) return null // Cycle prevention
        val def = flatComponents[rootId] ?: return null

        // Apply data bindings to properties
        val boundProperties = def.properties.mapValues { (_, value) ->
            bindDataModel(value, dataModel)
        }

        val children = def.children.mapNotNull { childId ->
            resolveFlatTree(childId, flatComponents, dataModel, visited + rootId)
        }

        return A2UiResolvedNode(
            id = def.id,
            component = def.component,
            properties = boundProperties,
            children = children
        )
    }

    private fun bindDataModel(rawText: String, dataModel: Map<String, String>): String {
        if (!rawText.contains("{{") || !rawText.contains("}}")) return rawText
        var result = rawText
        dataModel.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
            result = result.replace("{{data.$key}}", value)
        }
        return result
    }

    fun getSurface(surfaceId: String): A2UiSurfaceState? = _surfaces.value[surfaceId]

    fun clearAll() {
        _surfaces.value = emptyMap()
    }
}

