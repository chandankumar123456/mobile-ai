package com.mobileai.assistant.domain.usecase

import com.mobileai.assistant.domain.engine.LocalInferenceEngine

class UnloadModelUseCase(
    private val engine: LocalInferenceEngine
) {
    suspend operator fun invoke(): Result<Unit> {
        return engine.unloadModel()
    }
}

