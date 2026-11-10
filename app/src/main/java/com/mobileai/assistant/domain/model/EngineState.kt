package com.mobileai.assistant.domain.model

/**
 * High-level engine states visible to the domain and presentation layers.
 */
sealed interface EngineState {
    data object Uninitialized : EngineState
    data object Initializing : EngineState
    data object Ready : EngineState
    data class LoadingModel(val modelName: String, val progress: Float? = null) : EngineState
    data class ModelReady(val modelInfo: ModelInfo) : EngineState
    data class Generating(val modelName: String) : EngineState
    data object Unloading : EngineState
    data class Error(val message: String, val throwable: Throwable? = null) : EngineState

    val isModelLoaded: Boolean
        get() = this is ModelReady || this is Generating

    val isBusy: Boolean
        get() = this is Initializing || this is LoadingModel || this is Generating || this is Unloading
}

