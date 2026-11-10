package com.mobileai.assistant.domain.usecase

import com.mobileai.assistant.domain.engine.LocalInferenceEngine

class CancelGenerationUseCase(
    private val engine: LocalInferenceEngine
) {
    operator fun invoke() {
        engine.cancelGeneration()
    }
}

