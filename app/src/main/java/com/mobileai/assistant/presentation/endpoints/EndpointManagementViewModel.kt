package com.mobileai.assistant.presentation.endpoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileai.assistant.data.remote.OpenAiCompatibleClient
import com.mobileai.assistant.data.remote.RemoteCapabilityParser
import com.mobileai.assistant.data.settings.AppSettingsRepository
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.repository.EndpointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EndpointManagementViewModel(
    private val endpointRepository: EndpointRepository,
    private val client: OpenAiCompatibleClient = OpenAiCompatibleClient(),
    private val settingsRepository: AppSettingsRepository? = null,
    private val onEndpointSelected: ((RemoteEndpointConfig) -> Unit)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EndpointManagementUiState())
    val uiState: StateFlow<EndpointManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            endpointRepository.endpoints.collect { list ->
                _uiState.update { it.copy(endpoints = list) }
            }
        }

        viewModelScope.launch {
            endpointRepository.activeEndpoint.collect { active ->
                _uiState.update { it.copy(activeEndpoint = active) }
            }
        }
    }

    fun onNameChanged(name: String) = _uiState.update { it.copy(nameInput = name) }
    fun onUrlChanged(url: String) = _uiState.update { it.copy(urlInput = url) }
    fun onApiKeyChanged(key: String) = _uiState.update { it.copy(apiKeyInput = key) }
    fun onSelectedModelChanged(model: String) = _uiState.update { it.copy(selectedModelInput = model) }

    fun testConnection() {
        val url = _uiState.value.urlInput.trim()
        val key = _uiState.value.apiKeyInput.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter an endpoint URL.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null, errorMessage = null) }
            val result = client.testConnection(url, key)
            val capsMap = result.models.associateWith { ModelCapabilitiesResolver.fromRemote(it) }
            _uiState.update {
                it.copy(
                    isTesting = false,
                    testResult = result,
                    availableModels = if (result.models.isNotEmpty()) result.models else it.availableModels,
                    modelCapabilities = it.modelCapabilities + capsMap,
                    selectedModelInput = if (it.selectedModelInput.isBlank() && result.models.isNotEmpty()) result.models.first() else it.selectedModelInput
                )
            }
        }
    }

    fun testEndpointPing(endpoint: RemoteEndpointConfig) {
        viewModelScope.launch {
            val result = client.testConnection(endpoint.baseUrl, endpoint.apiKey)
            if (result.isSuccessful) {
                _uiState.update {
                    it.copy(
                        endpointLatencies = it.endpointLatencies + (endpoint.id to result.latencyMs),
                        infoMessage = "${endpoint.name}: Ping ${result.latencyMs} ms"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = "${endpoint.name} connection test failed: ${result.message}"
                    )
                }
            }
        }
    }

    fun fetchModels() {
        val url = _uiState.value.urlInput.trim()
        val key = _uiState.value.apiKeyInput.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingModels = true, errorMessage = null) }
            val result = client.fetchModelInfos(url, key)
            result.fold(
                onSuccess = { modelInfos ->
                    val modelNames = modelInfos.map { it.id }
                    val capsMap = modelInfos.associate { it.id to RemoteCapabilityParser.capabilitiesFor(it) }
                    _uiState.update {
                        it.copy(
                            isFetchingModels = false,
                            availableModels = modelNames,
                            modelCapabilities = it.modelCapabilities + capsMap,
                            selectedModelInput = if (it.selectedModelInput.isBlank() && modelNames.isNotEmpty()) modelNames.first() else it.selectedModelInput,
                            infoMessage = "Found ${modelNames.size} models from endpoint."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isFetchingModels = false,
                            errorMessage = error.message ?: "Failed to fetch models"
                        )
                    }
                }
            )
        }
    }

    fun saveEndpoint() {
        val state = _uiState.value
        if (!state.canSave) return

        val endpoint = RemoteEndpointConfig(
            id = state.editingEndpointId ?: java.util.UUID.randomUUID().toString(),
            name = state.nameInput.trim(),
            baseUrl = state.urlInput.trim(),
            apiKey = state.apiKeyInput.trim().ifBlank { null },
            selectedModel = state.selectedModelInput.trim(),
            availableModels = state.availableModels
        )

        viewModelScope.launch {
            endpointRepository.saveEndpoint(endpoint)
            resetForm()
            _uiState.update { it.copy(infoMessage = "Endpoint '${endpoint.name}' saved.") }
        }
    }

    fun startEditing(endpoint: RemoteEndpointConfig) {
        _uiState.update {
            it.copy(
                isEditing = true,
                editingEndpointId = endpoint.id,
                nameInput = endpoint.name,
                urlInput = endpoint.baseUrl,
                apiKeyInput = endpoint.apiKey ?: "",
                selectedModelInput = endpoint.selectedModel,
                availableModels = endpoint.availableModels,
                testResult = null
            )
        }
    }

    fun deleteEndpoint(endpoint: RemoteEndpointConfig) {
        viewModelScope.launch {
            endpointRepository.deleteEndpoint(endpoint.id)
            if (_uiState.value.editingEndpointId == endpoint.id) {
                resetForm()
            }
            _uiState.update { it.copy(infoMessage = "Deleted '${endpoint.name}'.") }
        }
    }

    fun selectActiveEndpoint(endpoint: RemoteEndpointConfig) {
        viewModelScope.launch {
            endpointRepository.setActiveEndpoint(endpoint)
            onEndpointSelected?.invoke(endpoint)
            _uiState.update { it.copy(infoMessage = "Active endpoint set to '${endpoint.name}'.") }
        }
    }

    fun openModelProfile(endpoint: RemoteEndpointConfig, modelId: String) {
        val key = AppSettingsRepository.modelKeyRemote(endpoint.id, modelId)
        val existing = settingsRepository?.profileFor(key)
        val profile = existing ?: ModelSettingsProfile(
            modelId = modelId,
            displayName = modelId,
            params = settingsRepository?.settings?.value?.toGenerationParams() ?: GenerationParams()
        )
        _uiState.update {
            it.copy(
                configuringProfileEndpoint = endpoint,
                configuringProfileModelId = modelId,
                currentProfile = profile
            )
        }
    }

    fun updateConfiguredProfile(profile: ModelSettingsProfile) {
        _uiState.update { it.copy(currentProfile = profile) }
    }

    fun saveModelProfile(profile: ModelSettingsProfile) {
        val endpoint = _uiState.value.configuringProfileEndpoint ?: return
        val key = AppSettingsRepository.modelKeyRemote(endpoint.id, profile.modelId)
        settingsRepository?.saveProfile(key, profile)
        _uiState.update {
            it.copy(
                configuringProfileEndpoint = null,
                configuringProfileModelId = null,
                currentProfile = null,
                infoMessage = "Saved profile for '${profile.displayName.ifBlank { profile.modelId }}'."
            )
        }
    }

    fun closeModelProfile() {
        _uiState.update {
            it.copy(
                configuringProfileEndpoint = null,
                configuringProfileModelId = null,
                currentProfile = null
            )
        }
    }

    fun resetForm() {
        _uiState.update {
            it.copy(
                isEditing = false,
                editingEndpointId = null,
                nameInput = "",
                urlInput = "",
                apiKeyInput = "",
                selectedModelInput = "",
                availableModels = emptyList(),
                testResult = null,
                errorMessage = null
            )
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}
