package com.mobileai.assistant.domain.usecase

import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.repository.ModelRepository

class DeleteModelUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(modelInfo: ModelInfo): Result<Unit> {
        return repository.deleteModel(modelInfo)
    }
}

