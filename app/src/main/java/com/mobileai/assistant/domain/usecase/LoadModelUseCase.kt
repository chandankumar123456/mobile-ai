package com.mobileai.assistant.domain.usecase

import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.ModelInfo

class LoadModelUseCase(
    private val engine: LocalInferenceEngine
) {
    suspend operator fun invoke(modelInfo: ModelInfo): Result<Unit> {
        return engine.loadModel(modelInfo)
    }
}

