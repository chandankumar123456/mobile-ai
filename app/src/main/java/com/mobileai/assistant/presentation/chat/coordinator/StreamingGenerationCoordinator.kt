package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.data.remote.RemoteInferenceEngine
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.usecase.CancelGenerationUseCase
import com.mobileai.assistant.domain.usecase.GenerateResponseUseCase
import kotlinx.coroutines.flow.Flow

/**
 * Coordinator responsible for dispatching generation requests to either
 * local native llama.cpp or remote OpenAI-compatible endpoints,
 * streaming tokens, and handling cancellation.
 */
class StreamingGenerationCoordinator(
    private val localEngine: LocalInferenceEngine,
    private val remoteEngine: RemoteInferenceEngine,
    private val generateResponseUseCase: GenerateResponseUseCase? = null,
    private val cancelGenerationUseCase: CancelGenerationUseCase? = null
) {

    fun executeGeneration(
        target: InferenceTarget,
        request: GenerationRequest
    ): Flow<GenerationEvent> {
        return when (target) {
            is InferenceTarget.Local -> {
                generateResponseUseCase?.invoke(request) ?: localEngine.generate(request)
            }
            is InferenceTarget.Remote -> {
                remoteEngine.generate(request, target.endpoint, target.modelId)
            }
        }
    }

    fun cancelGeneration(target: InferenceTarget) {
        when (target) {
            is InferenceTarget.Local -> {
                cancelGenerationUseCase?.invoke() ?: localEngine.cancelGeneration()
            }
            is InferenceTarget.Remote -> {
                // Remote flow is cancelled by cancelling its collector job
            }
        }
    }
}
