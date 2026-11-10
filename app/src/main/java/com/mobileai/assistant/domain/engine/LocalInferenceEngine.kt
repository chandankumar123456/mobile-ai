package com.mobileai.assistant.domain.engine

import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for local LLM inference engines.
 * Isolates presentation and business logic from the specific llama.cpp native integration.
 */
interface LocalInferenceEngine {
    /**
     * Current state of the inference engine.
     */
    val state: StateFlow<EngineState>

    /**
     * Currently loaded model metadata, or null if no model is loaded.
     */
    val loadedModel: StateFlow<ModelInfo?>

    /**
     * Initialize the native engine backends and verify library availability.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Load a GGUF model from the specified file metadata.
     */
    suspend fun loadModel(modelInfo: ModelInfo): Result<Unit>

    /**
     * Load a GGUF model applying the supplied runtime configuration.
     */
    suspend fun loadModel(modelInfo: ModelInfo, params: GenerationParams): Result<Unit>

    /**
     * Unload the active model and release its context, kv cache, and weights from memory.
     */
    suspend fun unloadModel(): Result<Unit>

    /**
     * Reload the currently loaded model with a new runtime configuration.
     */
    suspend fun reload(params: GenerationParams): Result<Unit>

    /**
     * Send a prompt to the loaded model and stream generated token strings back.
     * Tokens are emitted on a background thread.
     */
    fun generate(prompt: String, settings: InferenceSettings): Flow<String>

    /**
     * Generate a response for a full request, streaming text, reasoning and runtime metrics.
     */
    fun generate(request: GenerationRequest): Flow<GenerationEvent>

    /**
     * Cancel the ongoing token generation safely.
     */
    fun cancelGeneration()

    /**
     * Metrics captured from the most recent local generation.
     */
    fun runtimeMetrics(): RuntimeMetrics

    /**
     * Whether changing between [old] and [new] requires reloading the model.
     */
    fun requiresReload(old: GenerationParams, new: GenerationParams): Boolean =
        old.contextSize != new.contextSize || old.threads != new.threads

    /**
     * Release all native backend resources when the engine is destroyed.
     */
    fun release()
}
