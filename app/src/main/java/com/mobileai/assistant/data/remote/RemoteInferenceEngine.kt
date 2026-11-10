package com.mobileai.assistant.data.remote

import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.model.ThinkingEffort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Manages remote model inference sessions via OpenAI-compatible endpoints.
 */
class RemoteInferenceEngine(
    private val client: OpenAiCompatibleClient = OpenAiCompatibleClient()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<EngineState>(EngineState.Ready)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _activeEndpoint = MutableStateFlow<RemoteEndpointConfig?>(null)
    val activeEndpoint: StateFlow<RemoteEndpointConfig?> = _activeEndpoint.asStateFlow()

    private var activeJob: Job? = null

    fun setEndpoint(endpoint: RemoteEndpointConfig?) {
        _activeEndpoint.value = endpoint
        _state.value = EngineState.Ready
    }

    fun generate(
        request: GenerationRequest,
        endpoint: RemoteEndpointConfig,
        model: String
    ): Flow<GenerationEvent> {
        _state.value = EngineState.Generating("${endpoint.name} ($model)")
        val effectiveRequest = if (request.modelId == model) request else request.copy(modelId = model)

        return client.streamChatCompletion(endpoint, effectiveRequest)
            .onStart {
                _state.value = EngineState.Generating("${endpoint.name} ($model)")
            }
            .onCompletion { cause ->
                _state.value = EngineState.Ready
            }
            .catch { e ->
                _state.value = EngineState.Error("Remote inference error: ${e.message}", e)
                throw e
            }
    }

    /**
     * Legacy entry point retained for existing callers. Delegates to the full
     * request-based flow and exposes only text/reasoning chunks.
     */
    fun generate(
        endpoint: RemoteEndpointConfig,
        model: String,
        messages: List<ChatMessage>,
        settings: InferenceSettings,
        thinkingEnabled: Boolean,
        thinkingEffort: ThinkingEffort
    ): Flow<StreamChunk> {
        val request = GenerationRequest(
            messages = messages,
            params = settings.toGenerationParams().copy(
                thinkingEnabled = thinkingEnabled,
                thinkingEffort = thinkingEffort
            ),
            capabilities = ModelCapabilitiesResolver.fromRemote(model),
            modelId = model,
            modelName = model,
            isRemote = true
        )

        return generate(request, endpoint, model).mapNotNull { event ->
            when (event) {
                is GenerationEvent.Delta -> StreamChunk(text = event.text)
                is GenerationEvent.Reasoning -> StreamChunk(reasoning = event.text, isThinking = true)
                is GenerationEvent.Metrics -> null
            }
        }
    }

    fun cancelGeneration() {
        activeJob?.cancel()
        activeJob = null
        _state.value = EngineState.Ready
    }
}
