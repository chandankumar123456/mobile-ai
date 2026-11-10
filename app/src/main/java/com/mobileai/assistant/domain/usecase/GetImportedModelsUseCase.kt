package com.mobileai.assistant.domain.usecase

import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.repository.ModelRepository
import kotlinx.coroutines.flow.StateFlow

class GetImportedModelsUseCase(
    private val repository: ModelRepository
) {
    val models: StateFlow<List<ModelInfo>> = repository.importedModels

    suspend fun refresh() {
        repository.refreshModels()
    }
}

