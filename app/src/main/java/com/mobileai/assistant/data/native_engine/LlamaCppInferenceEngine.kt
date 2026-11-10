package com.mobileai.assistant.data.native_engine

import android.content.Context
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine as UpstreamEngine
import com.arm.aichat.NativeGenerationConfig
import com.mobileai.assistant.data.remote.StreamingTagParser
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Adapter integrating the official llama.cpp Android bridge with the domain [LocalInferenceEngine].
 */
class LlamaCppInferenceEngine(
    private val context: Context
) : LocalInferenceEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _loadedModel = MutableStateFlow<ModelInfo?>(null)
    override val loadedModel: StateFlow<ModelInfo?> = _loadedModel.asStateFlow()

    private var upstreamEngine: UpstreamEngine? = null
    private var activeGenerationJob: Job? = null

    @Volatile
    private var lastParams: GenerationParams = GenerationParams()

    @Volatile
    private var loadStartedAtMs: Long = 0L

    @Volatile
    private var loadTimeMs: Long? = null

    @Volatile
    private var lastMetrics: RuntimeMetrics = RuntimeMetrics.EMPTY

    init {
        scope.launch {
            initialize()
        }
    }

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = EngineState.Initializing
            Log.i(TAG, "Initializing native llama.cpp engine...")
            val engine = AiChat.getInferenceEngine(context.applicationContext)
            upstreamEngine = engine

            // Observe upstream state changes and map to domain states
            scope.launch {
                engine.state.collect { upstreamState ->
                    Log.d(TAG, "Upstream engine state changed: ${upstreamState.javaClass.simpleName}")
                    val currentModel = _loadedModel.value
                    _state.value = when (upstreamState) {
                        is UpstreamEngine.State.Uninitialized -> EngineState.Uninitialized
                        is UpstreamEngine.State.Initializing -> EngineState.Initializing
                        is UpstreamEngine.State.Initialized -> {
                            if (currentModel != null) EngineState.ModelReady(currentModel)
                            else EngineState.Ready
                        }
                        is UpstreamEngine.State.LoadingModel -> {
                            EngineState.LoadingModel(currentModel?.displayName ?: "Model")
                        }
                        is UpstreamEngine.State.UnloadingModel -> EngineState.Unloading
                        is UpstreamEngine.State.ModelReady -> {
                            if (currentModel != null) EngineState.ModelReady(currentModel)
                            else EngineState.Ready
                        }
                        is UpstreamEngine.State.Generating,
                        is UpstreamEngine.State.ProcessingUserPrompt,
                        is UpstreamEngine.State.ProcessingSystemPrompt -> {
                            EngineState.Generating(currentModel?.displayName ?: "Model")
                        }
                        is UpstreamEngine.State.Benchmarking -> EngineState.Generating("Benchmarking")
                        is UpstreamEngine.State.Error -> {
                            EngineState.Error(
                                message = upstreamState.exception.message ?: "Unknown native engine error",
                                throwable = upstreamState.exception
                            )
                        }
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native engine", e)
            _state.value = EngineState.Error("Failed to initialize llama.cpp native library: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun loadModel(modelInfo: ModelInfo): Result<Unit> =
        loadModel(modelInfo, lastParams)

    override suspend fun loadModel(modelInfo: ModelInfo, params: GenerationParams): Result<Unit> =
        loadInternal(modelInfo, params)

    override suspend fun reload(params: GenerationParams): Result<Unit> = withContext(Dispatchers.IO) {
        val model = _loadedModel.value
            ?: return@withContext Result.failure(IllegalStateException("No model loaded to reload"))

        loadInternal(model, params)
    }

    private suspend fun loadInternal(
        modelInfo: ModelInfo,
        params: GenerationParams
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val engine = upstreamEngine
            ?: return@withContext Result.failure(IllegalStateException("Inference engine not initialized"))

        try {
            val file = File(modelInfo.filePath)
            if (!file.exists() || !file.canRead()) {
                throw IOException("Model file cannot be read at ${modelInfo.filePath}")
            }

            // If upstream is in error state or a model is already loaded, reset it first
            if (engine.state.value is UpstreamEngine.State.Error || _loadedModel.value != null) {
                Log.i(TAG, "Resetting engine state before loading...")
                try {
                    engine.cleanUp()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up previous state: ${e.message}")
                }
            }

            _loadedModel.value = modelInfo
            _state.value = EngineState.LoadingModel(modelInfo.displayName, null)
            loadStartedAtMs = SystemClock.elapsedRealtime()
            Log.i(TAG, "Loading model via llama.cpp: ${modelInfo.filePath}")

            val capabilities = modelInfo.capabilities ?: ModelCapabilitiesResolver.fromLocal(modelInfo)
            val nCtx = effectiveContextSize(params, capabilities)
            engine.loadModel(
                modelInfo.filePath,
                NativeGenerationConfig(
                    nCtx = nCtx,
                    nThreads = params.threads,
                    nBatch = if (params.batchSize > 0) params.batchSize else DEFAULT_BATCH_SIZE
                )
            )

            lastParams = params
            loadTimeMs = SystemClock.elapsedRealtime() - loadStartedAtMs

            _loadedModel.value = modelInfo.copy(isLoaded = true)
            _state.value = EngineState.ModelReady(modelInfo)
            Log.i(TAG, "Model successfully loaded: ${modelInfo.displayName}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model ${modelInfo.fileName}", e)
            _loadedModel.value = null
            try {
                engine.cleanUp()
            } catch (cleanupEx: Exception) {
                Log.w(TAG, "Notice during cleanup on load failure: ${cleanupEx.message}")
            }
            _state.value = EngineState.Error("Model load failure: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun unloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        val engine = upstreamEngine
            ?: return@withContext Result.failure(IllegalStateException("Inference engine not initialized"))

        try {
            engine.requestCancel()
            activeGenerationJob?.cancel()
            activeGenerationJob = null
            _state.value = EngineState.Unloading
            Log.i(TAG, "Unloading model and freeing native resources...")
            engine.cleanUp()
            _loadedModel.value = null
            _state.value = EngineState.Ready
            Log.i(TAG, "Model unloaded successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
            _loadedModel.value = null
            _state.value = EngineState.Ready
            Result.failure(e)
        }
    }

    override fun generate(prompt: String, settings: InferenceSettings): Flow<String> = flow {
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }

        emitAll(
            generate(
                GenerationRequest(
                    messages = listOf(ChatMessage(content = prompt, isUser = true)),
                    params = settings.toGenerationParams(),
                    capabilities = _loadedModel.value?.capabilities ?: ModelCapabilities.DEFAULT
                )
            ).transform { event ->
                when (event) {
                    is GenerationEvent.Delta -> emit(event.text)
                    // Legacy callers parse <think> tags themselves, so keep the raw-tag contract.
                    is GenerationEvent.Reasoning -> emit("<think>${event.text}</think>")
                    is GenerationEvent.Metrics -> Unit
                }
            }
        )
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        val engine = upstreamEngine
            ?: throw IllegalStateException("Inference engine is not initialized")

        val model = _loadedModel.value
            ?: throw IllegalStateException("No model loaded. Please load a GGUF model first.")

        val build = LocalRequestFactory.buildNativeMessages(request)
        if (build.messages.none { it.role == "user" || it.role == "assistant" }) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }

        val capabilities = resolveCapabilities(model, request.capabilities)
        val nCtx = effectiveContextSize(request.params, capabilities)
        val config = NativeGenerationConfig(
            temperature = request.params.temperature.toFloat(),
            topK = request.params.topK,
            topP = request.params.topP.toFloat(),
            minP = request.params.minP.toFloat(),
            repeatPenalty = request.params.repeatPenalty.toFloat(),
            frequencyPenalty = request.params.frequencyPenalty.toFloat(),
            presencePenalty = request.params.presencePenalty.toFloat(),
            seed = request.params.seed,
            predictLength = request.params.maxTokens,
            stopSequences = request.params.stopSequences,
            enableThinking = request.params.thinkingEnabled,
            continueFinalMessage = build.continueFinalMessage,
            nCtx = nCtx,
            nThreads = request.params.threads,
            nBatch = DEFAULT_BATCH_SIZE
        )

        Log.i(TAG, "Starting token generation for model: ${model.displayName}")
        _state.value = EngineState.Generating(model.displayName)

        val generationJob = currentCoroutineContext()[Job]
        activeGenerationJob = generationJob

        val startedAt = SystemClock.elapsedRealtime()
        var firstTokenAt: Long? = null
        var completionTokens = 0
        val parser = StreamingTagParser()

        try {
            engine.generate(build.messages, config).collect { token ->
                if (token.isEmpty()) return@collect
                if (firstTokenAt == null) {
                    firstTokenAt = SystemClock.elapsedRealtime()
                }
                completionTokens++
                parser.process(token).forEach { output ->
                    toEvents(output).forEach { emit(it) }
                }
            }

            parser.finish().forEach { output ->
                toEvents(output).forEach { emit(it) }
            }

            val finishedAt = SystemClock.elapsedRealtime()
            val totalMs = (finishedAt - startedAt).coerceAtLeast(0L)
            val ttftMs = firstTokenAt?.let { (it - startedAt).coerceAtLeast(0L) }
            val stats = runCatching { engine.runtimeStats() }.getOrNull()
            val promptTokens = stats?.promptTokens ?: 0
            val metrics = RuntimeMetrics(
                timeToFirstTokenMs = ttftMs,
                totalDurationMs = totalMs,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = promptTokens + completionTokens,
                tokensPerSecond = if (totalMs > 0L) completionTokens * 1000.0 / totalMs else null,
                contextUsed = stats?.contextUsed ?: (promptTokens + completionTokens),
                contextSize = stats?.contextSize?.takeIf { it > 0 } ?: nCtx,
                loadTimeMs = loadTimeMs,
                nativeMemoryBytes = nativeRssBytes(),
                javaHeapBytes = javaHeapBytes(),
                source = "LOCAL"
            )
            lastMetrics = metrics

            emit(GenerationEvent.Metrics(metrics))
            Log.i(TAG, "Token generation completed.")
            _state.value = EngineState.ModelReady(model)
        } catch (e: CancellationException) {
            Log.i(TAG, "Generation cancelled by user.")
            engine.requestCancel()
            _state.value = EngineState.ModelReady(model)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            _state.value = EngineState.Error("Generation error: ${e.message}", e)
            throw e
        } finally {
            if (activeGenerationJob === generationJob) {
                activeGenerationJob = null
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun cancelGeneration() {
        Log.i(TAG, "Cancelling generation...")
        upstreamEngine?.requestCancel()
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        val model = _loadedModel.value
        _state.value = if (model != null) EngineState.ModelReady(model) else EngineState.Ready
    }

    override fun runtimeMetrics(): RuntimeMetrics = lastMetrics

    override fun requiresReload(old: GenerationParams, new: GenerationParams): Boolean =
        old.contextSize != new.contextSize || old.threads != new.threads

    override fun release() {
        Log.i(TAG, "Releasing native engine and backend resources...")
        cancelGeneration()
        try {
            upstreamEngine?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying engine", e)
        }
        _loadedModel.value = null
        _state.value = EngineState.Uninitialized
    }

    private fun toEvents(output: StreamingTagParser.Output): List<GenerationEvent> = buildList {
        if (output.text.isNotEmpty()) {
            add(GenerationEvent.Delta(output.text))
        }
        output.reasoning?.takeIf { it.isNotEmpty() }?.let {
            add(GenerationEvent.Reasoning(it))
        }
    }

    private fun resolveCapabilities(
        modelInfo: ModelInfo,
        requested: ModelCapabilities
    ): ModelCapabilities = if (requested.contextLength != null) {
        requested
    } else {
        modelInfo.capabilities ?: ModelCapabilitiesResolver.fromLocal(modelInfo)
    }

    private fun effectiveContextSize(params: GenerationParams, capabilities: ModelCapabilities): Int {
        val requested = params.contextSize.coerceAtLeast(1)
        val modelLimit = capabilities.contextLength?.takeIf { it > 0 }
        return if (modelLimit != null) minOf(requested, modelLimit) else requested
    }

    private fun javaHeapBytes(): Long =
        Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun nativeRssBytes(): Long? = try {
        val residentPages = File(PROC_STAT_M)
            .readText()
            .trim()
            .split(' ')
            .getOrNull(1)
            ?.toLongOrNull()
        residentPages?.let { it * Os.sysconf(OsConstants._SC_PAGESIZE) }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TAG = "LlamaCppInferenceEngine"
        private const val DEFAULT_BATCH_SIZE = 512
        private const val PROC_STAT_M = "/proc/self/statm"
    }
}
