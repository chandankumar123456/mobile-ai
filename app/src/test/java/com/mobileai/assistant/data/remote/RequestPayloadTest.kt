package com.mobileai.assistant.data.remote

import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.AttachmentKind
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ReasoningSupport
import com.mobileai.assistant.domain.model.ThinkingEffort
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class RequestPayloadTest {

    private fun request(
        params: GenerationParams = GenerationParams(systemPrompt = ""),
        capabilities: ModelCapabilities = ModelCapabilities.DEFAULT,
        messages: List<ChatMessage> = listOf(ChatMessage(content = "hello", isUser = true))
    ) = GenerationRequest(
        messages = messages,
        params = params,
        capabilities = capabilities,
        modelId = "test-model"
    )

    private fun payload(model: String = "test-model", request: GenerationRequest) =
        RemoteRequestBuilder.buildPayload(model, request)

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

    @Test
    fun topKAndMinPSentOnlyWhenNativeSampling() {
        val params = GenerationParams(systemPrompt = "", topK = 55, minP = 0.12)

        val plain = payload(request = request(params = params, capabilities = ModelCapabilities.DEFAULT))
        assertFalse(plain.containsKey("top_k"))
        assertFalse(plain.containsKey("min_p"))

        val native = payload(
            request = request(
                params = params,
                capabilities = ModelCapabilities.DEFAULT.copy(nativeSampling = true)
            )
        )
        assertEquals(55, native.getValue("top_k").jsonPrimitive.int)
        assertEquals(0.12, native.getValue("min_p").jsonPrimitive.content.toDouble(), 0.0001)
    }

    @Test
    fun reasoningEffortOnlyForEffortCapabilityWhenThinkingEnabled() {
        val enabledParams = GenerationParams(
            systemPrompt = "",
            thinkingEnabled = true,
            thinkingEffort = ThinkingEffort.HIGH
        )

        val effort = payload(
            request = request(
                params = enabledParams,
                capabilities = ModelCapabilities.DEFAULT.copy(reasoning = ReasoningSupport.EFFORT)
            )
        )
        assertEquals("high", effort.text("reasoning_effort"))

        val disabled = payload(
            request = request(
                params = enabledParams.copy(thinkingEnabled = false),
                capabilities = ModelCapabilities.DEFAULT.copy(reasoning = ReasoningSupport.EFFORT)
            )
        )
        assertFalse(disabled.containsKey("reasoning_effort"))

        val toggleOnly = payload(
            request = request(
                params = enabledParams,
                capabilities = ModelCapabilities.DEFAULT.copy(reasoning = ReasoningSupport.TOGGLE)
            )
        )
        assertFalse(toggleOnly.containsKey("reasoning_effort"))
    }

    @Test
    fun includesStopSeedAndPenalties() {
        val params = GenerationParams(
            systemPrompt = "",
            seed = 42L,
            stopSequences = listOf("\n\n", "END"),
            frequencyPenalty = 0.5,
            presencePenalty = 0.25
        )

        val built = payload(request = request(params = params))

        assertEquals(listOf("\n\n", "END"), built.array("stop").map { it.jsonPrimitive.content })
        assertEquals(42L, built.getValue("seed").jsonPrimitive.content.toLong())
        assertEquals(0.5, built.getValue("frequency_penalty").jsonPrimitive.content.toDouble(), 0.0001)
        assertEquals(0.25, built.getValue("presence_penalty").jsonPrimitive.content.toDouble(), 0.0001)

        val noSeed = payload(request = request(params = params.copy(seed = -1L)))
        assertFalse(noSeed.containsKey("seed"))
    }

    @Test
    fun mergesExtraBodyWithoutOverwritingCoreKeys() {
        val params = GenerationParams(
            systemPrompt = "",
            extraBody = mapOf(
                "model" to "hacked-model",
                "messages" to "hacked-messages",
                "stream" to "false",
                "response_format" to "json",
                "custom_field" to "custom-value"
            )
        )

        val built = payload(
            model = "real-model",
            request = request(
                params = params,
                capabilities = ModelCapabilities.DEFAULT.copy(jsonMode = true)
            )
        )

        assertEquals("real-model", built.text("model"))
        assertTrue(built["messages"] is JsonArray)
        assertEquals("true", built.text("stream"))
        assertEquals("custom-value", built.text("custom_field"))
        assertEquals("json_object", built.obj("response_format").text("type"))
    }

    @Test
    fun buildsVisionContentArrayFromImageAttachment() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val file = File.createTempFile("request-payload-test", ".png").apply {
            deleteOnExit()
            writeBytes(bytes)
        }
        val attachment = Attachment(
            uri = file.toURI().toString(),
            fileName = "picture.png",
            mimeType = "image/png",
            sizeBytes = bytes.size.toLong(),
            kind = AttachmentKind.IMAGE
        )
        val message = ChatMessage(content = "describe", isUser = true, attachments = listOf(attachment))

        val built = payload(
            request = request(
                capabilities = ModelCapabilities.DEFAULT.copy(vision = true),
                messages = listOf(message)
            )
        )

        val content = built.array("messages").single().jsonObject.array("content")
        assertEquals(2, content.size)

        val textPart = content[0].jsonObject
        assertEquals("text", textPart.text("type"))
        assertEquals("describe", textPart.text("text"))

        val imagePart = content[1].jsonObject
        assertEquals("image_url", imagePart.text("type"))
        val expected = "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
        assertEquals(expected, imagePart.obj("image_url").text("url"))
    }

    @Test
    fun skipsImageWhenVisionUnsupported() {
        val attachment = Attachment(
            uri = "/does/not/exist.png",
            fileName = "picture.png",
            mimeType = "image/png",
            sizeBytes = 10,
            kind = AttachmentKind.IMAGE
        )
        val message = ChatMessage(content = "describe", isUser = true, attachments = listOf(attachment))

        val built = payload(
            request = request(
                capabilities = ModelCapabilities.DEFAULT.copy(vision = false),
                messages = listOf(message)
            )
        )

        val content = built.array("messages").single().jsonObject.getValue("content")
        assertTrue(content is JsonPrimitive)
        assertEquals("describe", content.jsonPrimitive.content)
    }

    @Test
    fun appendsExtractedDocumentText() {
        val attachment = Attachment(
            uri = "content://document/1",
            fileName = "notes.txt",
            mimeType = "text/plain",
            sizeBytes = 5,
            kind = AttachmentKind.DOCUMENT,
            extractedText = "hello world"
        )
        val message = ChatMessage(content = "summarize", isUser = true, attachments = listOf(attachment))

        val built = payload(request = request(messages = listOf(message)))
        val content = built.array("messages").single().jsonObject.text("content")

        assertEquals("summarize\n\n--- Attachment: notes.txt ---\nhello world", content)
    }

    @Test
    fun filtersSystemErrorBlankAndTrailingStreamingMessages() {
        val messages = listOf(
            ChatMessage(content = "Earlier", isUser = true),
            ChatMessage(content = "error", isUser = false, isError = true),
            ChatMessage(content = "   ", isUser = true),
            ChatMessage(content = "partial", isUser = false),
            ChatMessage(content = "", isUser = false, isStreaming = true)
        )

        val built = payload(
            request = request(
                params = GenerationParams(systemPrompt = "You are helpful."),
                messages = messages
            )
        )

        val roles = built.array("messages").map { it.jsonObject.text("role") }
        val contents = built.array("messages").map { it.jsonObject.text("content") }

        assertEquals(listOf("system", "user", "assistant"), roles)
        assertEquals(listOf("You are helpful.", "Earlier", "partial"), contents)
        assertEquals(1, roles.count { it == "system" })
    }
}
