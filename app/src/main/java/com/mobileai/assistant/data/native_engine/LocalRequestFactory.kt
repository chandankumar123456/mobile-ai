package com.mobileai.assistant.data.native_engine

import com.arm.aichat.NativeChatMessage
import com.mobileai.assistant.domain.model.GenerationRequest
import com.mobileai.assistant.domain.model.PromptBuilder

data class NativeMessageBuild(
    val messages: List<NativeChatMessage>,
    val continueFinalMessage: Boolean
)

object LocalRequestFactory {

    fun buildNativeMessages(request: GenerationRequest): NativeMessageBuild {
        val lastUserText = request.messages.lastOrNull { it.isUser }?.content.orEmpty()
        val systemPrompt = PromptBuilder.buildEffectiveSystemPrompt(
            basePrompt = request.params.systemPrompt,
            language = request.params.language,
            userText = lastUserText
        )

        val usable = request.messages.filterNot { it.isError }
        val trailingEmptyAssistant = usable.lastOrNull()?.let { !it.isUser && it.content.isBlank() } == true
        val candidates = if (trailingEmptyAssistant) usable.dropLast(1) else usable
        val included = candidates.filter { it.content.isNotBlank() }

        val messages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(NativeChatMessage(role = "system", content = systemPrompt))
            }
            included.forEach { message ->
                add(
                    NativeChatMessage(
                        role = if (message.isUser) "user" else "assistant",
                        content = message.content
                    )
                )
            }
        }

        val lastIncluded = included.lastOrNull()
        val continueFinalMessage =
            lastIncluded != null && !lastIncluded.isUser && lastIncluded.isIncomplete

        return NativeMessageBuild(messages, continueFinalMessage)
    }
}
