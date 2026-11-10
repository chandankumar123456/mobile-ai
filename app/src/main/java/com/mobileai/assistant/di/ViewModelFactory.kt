package com.mobileai.assistant.di

import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.presentation.chat.ChatViewModel
import com.mobileai.assistant.presentation.endpoints.EndpointManagementViewModel
import com.mobileai.assistant.presentation.model.ModelSelectionViewModel
import com.mobileai.assistant.presentation.settings.SettingsViewModel

/**
 * Factory creating presentation ViewModels wired directly with [AppContainer] dependencies.
 */
class ViewModelFactory(private val container: AppContainer) {

    fun createChatViewModel(): ChatViewModel {
        return ChatViewModel(
            engine = container.localInferenceEngine,
            chatRepository = container.chatRepository,
            generateResponseUseCase = container.generateResponseUseCase,
            cancelGenerationUseCase = container.cancelGenerationUseCase,
            remoteEngine = container.remoteInferenceEngine,
            endpointRepository = container.endpointRepository,
            voiceSettingsRepository = container.voiceSettingsRepository,
            sttManager = container.sttManager,
            ttsManager = container.ttsManager,
            getSettings = { container.settingsRepository.settings.value },
            onUpdateSettings = { updated -> container.settingsRepository.update(updated) },
            settingsRepository = container.settingsRepository,
            attachmentStore = container.attachmentStore,
            exportService = container.exportService,
            dotRepository = container.dotRepository,
            artifactRepository = container.artifactRepository,
            taskManager = container.taskManager,
            toolRegistry = container.toolRegistry,
            toolAuditLogger = container.toolAuditLogger
        )
    }

    fun createSettingsViewModel(): SettingsViewModel {
        return SettingsViewModel(
            initialSettings = container.settingsRepository.settings.value,
            voiceSettingsRepository = container.voiceSettingsRepository,
            ttsManager = container.ttsManager,
            fileManager = container.fileManager,
            onSettingsSaved = { updated -> container.settingsRepository.update(updated) }
        )
    }

    fun createEndpointManagementViewModel(
        onEndpointSelected: (com.mobileai.assistant.domain.model.RemoteEndpointConfig) -> Unit
    ): EndpointManagementViewModel {
        return EndpointManagementViewModel(
            endpointRepository = container.endpointRepository,
            settingsRepository = container.settingsRepository,
            onEndpointSelected = onEndpointSelected
        )
    }

    fun createModelSelectionViewModel(
        onModelSelected: (com.mobileai.assistant.domain.model.ModelInfo) -> Unit
    ): ModelSelectionViewModel {
        return ModelSelectionViewModel(
            engine = container.localInferenceEngine,
            importModelUseCase = container.importModelUseCase,
            getImportedModelsUseCase = container.getImportedModelsUseCase,
            loadModelUseCase = container.loadModelUseCase,
            unloadModelUseCase = container.unloadModelUseCase,
            deleteModelUseCase = container.deleteModelUseCase,
            fileManager = container.fileManager,
            settingsRepository = container.settingsRepository,
            onModelSelected = onModelSelected
        )
    }
}

