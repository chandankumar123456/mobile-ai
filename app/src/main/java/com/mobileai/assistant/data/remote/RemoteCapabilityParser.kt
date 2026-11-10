package com.mobileai.assistant.data.remote

import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Optional metadata advertised by an OpenAI-compatible `/v1/models` endpoint.
 */
data class RemoteModelInfo(
    val id: String,
    val contextLength: Int? = null,
    val inputModalities: List<String> = emptyList(),
    val supportedParameters: List<String> = emptyList(),
    val outputModalities: List<String> = emptyList(),
    val maxCompletionTokens: Int? = null
)

/**
 * Tolerant parser for `/v1/models` payloads. Providers expose wildly different
 * subsets of these fields, so every lookup is optional and malformed entries are
 * simply skipped instead of failing the whole list.
 */
object RemoteCapabilityParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseModels(jsonString: String): List<RemoteModelInfo> {
        return try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val data = root["data"] as? JsonArray ?: return emptyList()
            data.mapNotNull { element -> parseModel(element as? JsonObject ?: return@mapNotNull null) }
                .sortedBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun capabilitiesFor(info: RemoteModelInfo): ModelCapabilities =
        ModelCapabilitiesResolver.fromRemote(
            modelId = info.id,
            contextLength = info.contextLength,
            inputModalities = info.inputModalities,
            supportedParameters = info.supportedParameters
        )

    private fun parseModel(model: JsonObject): RemoteModelInfo? {
        val id = model.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val architecture = model["architecture"] as? JsonObject
        val topProvider = model["top_provider"] as? JsonObject

        return RemoteModelInfo(
            id = id,
            contextLength = model.int("context_length")
                ?: model.int("context_window")
                ?: model.int("max_context_length")
                ?: architecture?.int("context_length")
                ?: topProvider?.int("context_length"),
            inputModalities = architecture?.stringList("input_modalities") ?: emptyList(),
            supportedParameters = model.stringList("supported_parameters") ?: emptyList(),
            outputModalities = architecture?.stringList("output_modalities") ?: emptyList(),
            maxCompletionTokens = topProvider?.int("max_completion_tokens")
                ?: model.int("max_completion_tokens")
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        val content = primitive.contentOrNull ?: return null
        return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt()
    }

    private fun JsonObject.stringList(name: String): List<String>? =
        (this[name] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
}
