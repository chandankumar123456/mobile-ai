package com.mobileai.assistant.presentation.chat.coordinator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinator responsible for Google A2UI (Agent-to-User Interface) surfaces,
 * reactive data models, flat tree component updates, and user action events.
 */
class A2UiCoordinator {

    // Maps surfaceId -> Map<String, Any> (reactive data model for that surface)
    private val _surfaceDataModels = MutableStateFlow<Map<String, Map<String, Any>>>(emptyMap())
    val surfaceDataModels: StateFlow<Map<String, Map<String, Any>>> = _surfaceDataModels.asStateFlow()

    // Active surface IDs
    private val _activeSurfaces = MutableStateFlow<Set<String>>(emptySet())
    val activeSurfaces: StateFlow<Set<String>> = _activeSurfaces.asStateFlow()

    fun registerSurface(surfaceId: String) {
        _activeSurfaces.value = _activeSurfaces.value + surfaceId
    }

    fun deleteSurface(surfaceId: String) {
        _activeSurfaces.value = _activeSurfaces.value - surfaceId
        val updatedModels = _surfaceDataModels.value.toMutableMap()
        updatedModels.remove(surfaceId)
        _surfaceDataModels.value = updatedModels
    }

    fun updateDataModel(surfaceId: String, updates: Map<String, Any>) {
        val currentModels = _surfaceDataModels.value.toMutableMap()
        val currentSurfaceModel = currentModels[surfaceId]?.toMutableMap() ?: mutableMapOf()
        currentSurfaceModel.putAll(updates)
        currentModels[surfaceId] = currentSurfaceModel
        _surfaceDataModels.value = currentModels
    }

    fun handleUserAction(
        surfaceId: String,
        actionName: String,
        parameters: Map<String, String>,
        onActionDispatched: (String) -> Unit
    ) {
        val paramString = if (parameters.isNotEmpty()) {
            parameters.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        } else ""
        val payload = "A2UI Action [$actionName] on surface [$surfaceId]" + if (paramString.isNotEmpty()) " with ($paramString)" else ""
        onActionDispatched(payload)
    }

    fun clear() {
        _activeSurfaces.value = emptySet()
        _surfaceDataModels.value = emptyMap()
    }
}

