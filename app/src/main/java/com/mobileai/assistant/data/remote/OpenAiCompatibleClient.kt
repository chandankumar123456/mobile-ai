package com.mobileai.assistant.data.remote

import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.GenerationEvent
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.model.ThinkingEffort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class TestConnectionResult(
    val isSuccessful: Boolean,
    val message: String,
    val latencyMs: Long = 0L,
    val models: List<String> = emptyList()
)

data class StreamChunk(
    val text: String = "",
    val reasoning: String? = null,
    val isThinking: Boolean = false
)

class OpenAiCompatibleClient {

    fun normalizeUrl(baseUrl: String): String {
        var url = baseUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    suspend fun testConnection(baseUrl: String, apiKey: String?): TestConnectionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val normalized = normalizeUrl(baseUrl)
        val modelsUrl = if (normalized.endsWith("/v1")) "$normalized/models" else "$normalized/v1/models"

        try {
            val url = URL(modelsUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - start

            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val models = parseModelList(responseText)
                TestConnectionResult(
                    isSuccessful = true,
                    message = "Connected successfully! (${models.size} models found)",
                    latencyMs = latency,
                    models = models
                )
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val errorMsg = when (responseCode) {
                    401 -> "Authentication failed: Invalid API Key."
                    403 -> "Forbidden: You do not have permission to access this endpoint."
                    404 -> "Endpoint not found ($responseCode). Verify base URL."
                    else -> "HTTP $responseCode: $errorBody"
                }
                TestConnectionResult(
                    isSuccessful = false,
                    message = errorMsg,
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            TestConnectionResult(
                isSuccessful = false,
                message = "Connection error: ${e.message ?: e.javaClass.simpleName}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String?): Result<List<String>> =
        fetchModelInfos(baseUrl, apiKey).map { infos -> infos.map { it.id } }

    suspend fun fetchModelInfos(baseUrl: String, apiKey: String?): Result<List<RemoteModelInfo>> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl)
        val modelsUrl = if (normalized.endsWith("/v1")) "$normalized/models" else "$normalized/v1/models"

        try {
            val url = URL(modelsUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            if (connection.responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                Result.success(RemoteCapabilityParser.parseModels(responseText))
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${connection.responseCode}"
                Result.failure(Exception("Failed to fetch models: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseModelList(jsonString: String): List<String> =
        RemoteCapabilityParser.parseModels(jsonString).map { it.id }

    fun streamChatCompletion(
        endpoint: RemoteEndpointConfig,
        request: GenerationRequest
    ): Flow<GenerationEvent> = flow {
        val normalized = normalizeUrl(endpoint.baseUrl)
        val completionsUrl = if (normalized.endsWith("/v1")) "$normalized/chat/completions" else "$normalized/v1/chat/completions"
        val model = request.modelId.ifBlank { endpoint.selectedModel }
        val payload = RemoteRequestBuilder.buildPayload(model, request)

        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null
        val startNanos = System.nanoTime()
        var firstTokenNanos: Long? = null

        try {
            val url = URL(completionsUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                if (!endpoint.apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${endpoint.apiKey.trim()}")
                }
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                throw Exception("Remote inference failed ($responseCode): $errorBody")
            }

            reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
            var line: String?
            val tagParser = StreamingTagParser()
            val usage = UsageAccumulator()

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.isBlank()) continue
                if (!currentLine.startsWith("data:")) continue

                val data = currentLine.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val chunk = RemoteSseParser.parseChunkData(data) ?: continue
                usage.add(chunk.usage)

                if (!chunk.reasoning.isNullOrEmpty()) {
                    if (firstTokenNanos == null) firstTokenNanos = System.nanoTime()
                    emit(GenerationEvent.Reasoning(chunk.reasoning))
                }

                if (!chunk.content.isNullOrEmpty()) {
                    val parsed = tagParser.process(chunk.content)
                    for (out in parsed) {
                        if (out.text.isEmpty() && out.reasoning.isNullOrEmpty()) continue
                        if (firstTokenNanos == null) firstTokenNanos = System.nanoTime()
                        if (!out.reasoning.isNullOrEmpty()) {
                            emit(GenerationEvent.Reasoning(out.reasoning))
                        }
                        if (out.text.isNotEmpty()) {
                            emit(GenerationEvent.Delta(out.text))
                        }
                    }
                }
            }

            val remaining = tagParser.finish()
            for (out in remaining) {
                if (out.text.isEmpty() && out.reasoning.isNullOrEmpty()) continue
                if (firstTokenNanos == null) firstTokenNanos = System.nanoTime()
                if (!out.reasoning.isNullOrEmpty()) {
                    emit(GenerationEvent.Reasoning(out.reasoning))
                }
                if (out.text.isNotEmpty()) {
                    emit(GenerationEvent.Delta(out.text))
                }
            }

            val totalDurationMs = (System.nanoTime() - startNanos) / 1_000_000
            val ttftMs = firstTokenNanos?.let { (it - startNanos) / 1_000_000 }
            emit(
                GenerationEvent.Metrics(
                    usage.toMetrics(
                        timeToFirstTokenMs = ttftMs,
                        totalDurationMs = totalDurationMs,
                        contextSize = request.capabilities.contextLength ?: 0,
                        source = "REMOTE"
                    )
                )
            )
        } catch (e: CancellationException) {
            connection?.disconnect()
            throw e
        } finally {
            try {
                reader?.close()
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Legacy entry point retained for existing callers. Builds an equivalent
     * generation request and exposes only text/reasoning deltas.
     */
    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String?,
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
        val endpoint = RemoteEndpointConfig(
            name = "remote",
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = model
        )

        return streamChatCompletion(endpoint, request).mapNotNull { event ->
            when (event) {
                is GenerationEvent.Delta -> StreamChunk(text = event.text)
                is GenerationEvent.Reasoning -> StreamChunk(reasoning = event.text, isThinking = true)
                is GenerationEvent.Metrics -> null
            }
        }
    }
}
