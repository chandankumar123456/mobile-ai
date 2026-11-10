package com.mobileai.assistant.presentation.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.data.settings.AppSettingsRepository
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.usecase.DeleteModelUseCase
import com.mobileai.assistant.domain.usecase.GetImportedModelsUseCase
import com.mobileai.assistant.domain.usecase.ImportModelUseCase
import com.mobileai.assistant.domain.usecase.LoadModelUseCase
import com.mobileai.assistant.domain.usecase.UnloadModelUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelSelectionViewModel(
    private val engine: LocalInferenceEngine,
    private val importModelUseCase: ImportModelUseCase,
    private val getImportedModelsUseCase: GetImportedModelsUseCase,
    private val loadModelUseCase: LoadModelUseCase,
    private val unloadModelUseCase: UnloadModelUseCase,
    private val deleteModelUseCase: DeleteModelUseCase,
    private val fileManager: ModelFileManager? = null,
    private val settingsRepository: AppSettingsRepository? = null,
    private val onModelSelected: ((ModelInfo) -> Unit)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelSelectionUiState())
    val uiState: StateFlow<ModelSelectionUiState> = _uiState.asStateFlow()

    init {
        // Observe imported models list
        viewModelScope.launch {
            getImportedModelsUseCase.models.collect { models ->
                _uiState.update { it.copy(importedModels = models) }
                refreshStorageInfo()
            }
        }

        // Observe engine state
        viewModelScope.launch {
            engine.state.collect { state ->
                _uiState.update {
                    it.copy(
                        engineState = state,
                        isLoadingModel = state is EngineState.LoadingModel
                    )
                }
            }
        }

        // Observe currently loaded model
        viewModelScope.launch {
            engine.loadedModel.collect { model ->
                _uiState.update { it.copy(loadedModel = model) }
            }
        }

        // Initial scan of imported models directory
        viewModelScope.launch {
            getImportedModelsUseCase.refresh()
            refreshStorageInfo()
        }
    }

    fun refreshStorageInfo() {
        fileManager?.let { fm ->
            _uiState.update {
                it.copy(
                    modelStorageBytes = fm.getTotalModelStorageBytes(),
                    availableStorageBytes = fm.getAvailableStorageBytes()
                )
            }
        }
    }

    fun onModelFileSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importProgressFraction = 0f,
                    importStatusMessage = "Starting import...",
                    errorMessage = null
                )
            }

            val result = importModelUseCase(uri) { progress, status ->
                _uiState.update {
                    it.copy(
                        importProgressFraction = progress,
                        importStatusMessage = status
                    )
                }
            }

            result.fold(
                onSuccess = { importedModel ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            infoMessage = "Model imported successfully: ${importedModel.displayName}"
                        )
                    }
                    refreshStorageInfo()
                    // Automatically load the newly imported model
                    loadModel(importedModel)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = error.message ?: "Failed to import model"
                        )
                    }
                }
            )
        }
    }

    fun loadModel(modelInfo: ModelInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = loadModelUseCase(modelInfo)
            result.fold(
                onSuccess = {
                    onModelSelected?.invoke(modelInfo)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = "Failed to load model: ${error.message}")
                    }
                }
            )
        }
    }

    fun reloadModel(modelInfo: ModelInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            unloadModelUseCase()
            val result = loadModelUseCase(modelInfo)
            result.fold(
                onSuccess = {
                    onModelSelected?.invoke(modelInfo)
                    _uiState.update { it.copy(infoMessage = "Reloaded '${modelInfo.displayName}'.") }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = "Failed to reload model: ${error.message}")
                    }
                }
            )
        }
    }

    fun selectActiveModel(modelInfo: ModelInfo) {
        onModelSelected?.invoke(modelInfo)
        _uiState.update { it.copy(infoMessage = "Active model set to '${modelInfo.displayName}'.") }
    }

    fun unloadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = unloadModelUseCase()
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = "Failed to unload model: ${error.message}")
                }
            }
        }
    }

    fun deleteModel(modelInfo: ModelInfo) {
        viewModelScope.launch {
            if (_uiState.value.loadedModel?.filePath == modelInfo.filePath) {
                unloadModel()
            }
            deleteModelUseCase(modelInfo)
            refreshStorageInfo()
        }
    }

    fun openModelSettings(modelInfo: ModelInfo) {
        val key = AppSettingsRepository.modelKeyLocal(modelInfo.filePath)
        val existing = settingsRepository?.profileFor(key)
        val profile = existing ?: ModelSettingsProfile(
            modelId = modelInfo.filePath,
            displayName = modelInfo.displayName,
            params = settingsRepository?.settings?.value?.toGenerationParams() ?: GenerationParams()
        )
        _uiState.update {
            it.copy(
                configuredProfileModel = modelInfo,
                currentProfile = profile
            )
        }
    }

    fun updateConfiguredProfile(profile: ModelSettingsProfile) {
        _uiState.update { it.copy(currentProfile = profile) }
    }

    fun saveModelProfile(profile: ModelSettingsProfile) {
        val key = AppSettingsRepository.modelKeyLocal(profile.modelId)
        settingsRepository?.saveProfile(key, profile)
        _uiState.update {
            it.copy(
                configuredProfileModel = null,
                currentProfile = null,
                infoMessage = "Configuration saved for '${profile.displayName.ifBlank { "Model" }}'."
            )
        }
    }

    fun closeModelSettings() {
        _uiState.update {
            it.copy(
                configuredProfileModel = null,
                currentProfile = null
            )
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }
}
