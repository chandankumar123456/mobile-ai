package com.mobileai.assistant.presentation.endpoints

import com.mobileai.assistant.data.remote.TestConnectionResult
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.RemoteEndpointConfig

data class EndpointManagementUiState(
    val endpoints: List<RemoteEndpointConfig> = emptyList(),
    val activeEndpoint: RemoteEndpointConfig? = null,
    val isTesting: Boolean = false,
    val testResult: TestConnectionResult? = null,
    val endpointLatencies: Map<String, Long> = emptyMap(),
    val isEditing: Boolean = false,
    val editingEndpointId: String? = null,
    val nameInput: String = "",
    val urlInput: String = "",
    val apiKeyInput: String = "",
    val selectedModelInput: String = "",
    val availableModels: List<String> = emptyList(),
    val modelCapabilities: Map<String, ModelCapabilities> = emptyMap(),
    val isFetchingModels: Boolean = false,
    val configuringProfileEndpoint: RemoteEndpointConfig? = null,
    val configuringProfileModelId: String? = null,
    val currentProfile: ModelSettingsProfile? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
) {
    val canSave: Boolean
        get() = nameInput.isNotBlank() && urlInput.isNotBlank() && selectedModelInput.isNotBlank()
}
