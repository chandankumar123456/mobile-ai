package com.mobileai.assistant.presentation.chat.coordinator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinator responsible for OpenUI Lang state management,
 * progressive component rendering, and user mutation events.
 */
class OpenUiCoordinator {

    private val _componentStates = MutableStateFlow<Map<String, Map<String, Any>>>(emptyMap())
    val componentStates: StateFlow<Map<String, Map<String, Any>>> = _componentStates.asStateFlow()

    fun updateComponentState(componentId: String, key: String, value: Any) {
        val current = _componentStates.value.toMutableMap()
        val innerMap = current[componentId]?.toMutableMap() ?: mutableMapOf()
        innerMap[key] = value
        current[componentId] = innerMap
        _componentStates.value = current
    }

    fun handleAction(actionName: String, parameters: Map<String, String>, onActionTriggered: (String) -> Unit) {
        val formatted = if (parameters.isNotEmpty()) {
            val paramsStr = parameters.entries.joinToString(", ") { "${it.key}=${it.value}" }
            "$actionName($paramsStr)"
        } else {
            actionName
        }
        onActionTriggered(formatted)
    }

    fun reset() {
        _componentStates.value = emptyMap()
    }
}

