package com.mobileai.assistant.data.remote

import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.AttachmentKind
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.PromptBuilder
import com.mobileai.assistant.domain.model.ReasoningSupport
import com.mobileai.assistant.domain.tool.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.Base64

/**
 * Pure builders for OpenAI-compatible chat completion payloads. Kept free of
 * Android framework types so the request shape can be unit tested on the JVM.
 */
object RemoteRequestBuilder {

    private val CORE_KEYS = setOf("model", "messages", "stream", "tools", "tool_choice")

    private val Json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildPayload(
        model: String,
        request: GenerationRequest,
        tools: List<ToolDefinition> = emptyList()
    ): JsonObject {
        val params = request.params
        val capabilities = request.capabilities

        return buildJsonObject {
            put("model", model)
            put("messages", buildMessages(request))
            put("stream", true)
            put("temperature", params.temperature)
            put("top_p", params.topP)
            put("max_tokens", params.maxTokens)

            if (capabilities.nativeSampling) {
                put("top_k", params.topK)
                put("min_p", params.minP)
            }

            put("frequency_penalty", params.frequencyPenalty)
            put("presence_penalty", params.presencePenalty)

            if (params.stopSequences.isNotEmpty()) {
                put("stop", buildJsonArray {
                    params.stopSequences.forEach { add(JsonPrimitive(it)) }
                })
            }

            if (params.seed >= 0) {
                put("seed", params.seed)
            }

            if (capabilities.reasoning == ReasoningSupport.EFFORT && params.thinkingEnabled) {
                put("reasoning_effort", params.thinkingEffort.apiValue)
            }

            put("stream_options", buildJsonObject { put("include_usage", true) })

            applyResponseFormat(this, params, capabilities)

            if (tools.isNotEmpty()) {
                put("tools", formatOpenAiTools(tools))
                put("tool_choice", "auto")
            }

            params.extraBody.forEach { (key, value) ->
                if (key !in CORE_KEYS && key != "response_format") {
                    put(key, value)
                }
            }
        }
    }

    /**
     * Serializes a list of [ToolDefinition] into the OpenAI-compatible function calling tools schema.
     */
    fun formatOpenAiTools(tools: List<ToolDefinition>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            val parametersElement = try {
                if (tool.parameterSchemaJson.isNotBlank()) {
                    val parsed = Json.parseToJsonElement(tool.parameterSchemaJson)
                    if (parsed is JsonObject) {
                        if (!parsed.containsKey("type")) {
                            buildJsonObject {
                                put("type", "object")
                                parsed.forEach { (k, v) -> put(k, v) }
                            }
                        } else {
                            parsed
                        }
                    } else {
                        // Non-object JSON elements (arrays, primitives) gracefully degrade to safe empty object schema
                        buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {})
                        }
                    }
                } else {
                    buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                    }
                }
            } catch (_: Exception) {
                buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                }
            }

            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", tool.name.trim())
                    put("description", tool.description)
                    put("parameters", parametersElement)
                })
            })
        }
    }

    fun buildMessages(request: GenerationRequest): JsonArray = buildJsonArray {
        val lastUserText = request.messages.lastOrNull { it.isUser }?.content.orEmpty()
        val effectiveSystemPrompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = request.params.systemPrompt,
            language = request.params.language,
            userText = lastUserText
        )
        if (effectiveSystemPrompt.isNotBlank()) {
            add(buildJsonObject {
                put("role", "system")
                put("content", effectiveSystemPrompt)
            })
        }

        val lastIndex = request.messages.lastIndex
        request.messages.forEachIndexed { index, message ->
            if (message.isError) return@forEachIndexed
            if (index == lastIndex && !message.isUser && message.isStreaming && message.content.isBlank()) {
                return@forEachIndexed
            }

            val content = buildMessageContent(message, request.capabilities) ?: return@forEachIndexed
            add(buildJsonObject {
                put("role", if (message.isUser) "user" else "assistant")
                put("content", content)
            })
        }
    }

    fun buildMessageContent(message: ChatMessage, capabilities: ModelCapabilities): JsonElement? {
        val text = messageText(message)
        val imageParts = if (capabilities.vision) {
            message.attachments
                .filter { it.isImage() }
                .mapNotNull { imagePart(it) }
        } else {
            emptyList()
        }

        if (imageParts.isEmpty()) {
            return if (text.isBlank()) null else JsonPrimitive(text)
        }

        return buildJsonArray {
            if (text.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            }
            imageParts.forEach { add(it) }
        }
    }

    private fun applyResponseFormat(
        builder: JsonObjectBuilder,
        params: GenerationParams,
        capabilities: ModelCapabilities
    ) {
        if (!capabilities.jsonMode) return
        val raw = params.extraBody["response_format"]?.takeIf { it.isNotBlank() } ?: return

        val parsed = try {
            Json.parseToJsonElement(raw)
        } catch (_: Exception) {
            null
        }

        when {
            parsed is JsonObject -> builder.put("response_format", parsed)
            raw.equals("json", ignoreCase = true) ||
                raw.equals("json_object", ignoreCase = true) -> builder.put(
                "response_format",
                buildJsonObject { put("type", "json_object") }
            )
        }
    }

    private fun messageText(message: ChatMessage): String {
        val builder = StringBuilder(message.content.trim())
        message.attachments.forEach { attachment ->
            if (attachment.kind == AttachmentKind.IMAGE) return@forEach
            val extracted = attachment.extractedText?.takeIf { it.isNotBlank() } ?: return@forEach
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append("--- Attachment: ").append(attachment.fileName).append(" ---\n")
            builder.append(extracted)
        }
        return builder.toString()
    }

    private fun imagePart(attachment: Attachment): JsonObject? {
        val bytes = readAttachmentBytes(attachment.uri) ?: return null
        if (bytes.isEmpty()) return null
        val mime = attachment.mimeType.takeIf { it.startsWith("image/", ignoreCase = true) } ?: "image/jpeg"
        val dataUrl = "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
        return buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject { put("url", dataUrl) })
        }
    }

    private fun readAttachmentBytes(uri: String): ByteArray? {
        val file = attachmentFile(uri) ?: return null
        return try {
            if (file.isFile) file.readBytes() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun attachmentFile(uri: String): File? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("file:", ignoreCase = true) -> runCatching {
                val parsed = URI(trimmed)
                if (parsed.scheme.equals("file", ignoreCase = true)) {
                    Paths.get(parsed).toFile()
                } else {
                    null
                }
            }.getOrElse {
                File(trimmed.removePrefix("file://"))
            }
            trimmed.contains("://") -> null
            else -> File(trimmed)
        }
    }

    private fun Attachment.isImage(): Boolean =
        kind == AttachmentKind.IMAGE || mimeType.startsWith("image/", ignoreCase = true)
}
