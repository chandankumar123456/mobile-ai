package com.mobileai.assistant.domain.usecase

import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import kotlinx.coroutines.flow.Flow

class GenerateResponseUseCase(
    private val engine: LocalInferenceEngine
) {
    operator fun invoke(prompt: String, settings: InferenceSettings): Flow<String> {
        return engine.generate(prompt, settings)
    }

    operator fun invoke(request: GenerationRequest): Flow<GenerationEvent> {
        return engine.generate(request)
    }
}

