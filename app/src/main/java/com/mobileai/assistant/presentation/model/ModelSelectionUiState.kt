package com.mobileai.assistant.presentation.model

import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.presentation.settings.SettingsUiState

data class ModelSelectionUiState(
    val importedModels: List<ModelInfo> = emptyList(),
    val loadedModel: ModelInfo? = null,
    val engineState: EngineState = EngineState.Uninitialized,
    val isImporting: Boolean = false,
    val importProgressFraction: Float = 0f,
    val importStatusMessage: String = "",
    val isLoadingModel: Boolean = false,
    val modelStorageBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val configuredProfileModel: ModelInfo? = null,
    val currentProfile: ModelSettingsProfile? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val formattedModelStorage: String
        get() = SettingsUiState.formatBytes(modelStorageBytes)

    val formattedAvailableStorage: String
        get() = SettingsUiState.formatBytes(availableStorageBytes)

    val storageUsedFraction: Float
        get() {
            val total = (modelStorageBytes + availableStorageBytes).coerceAtLeast(1L)
            return (modelStorageBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}
