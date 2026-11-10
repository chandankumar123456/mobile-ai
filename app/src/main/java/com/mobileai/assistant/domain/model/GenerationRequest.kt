package com.mobileai.assistant.domain.model

data class GenerationRequest(
    val messages: List<ChatMessage>,
    val params: GenerationParams = GenerationParams(),
    val capabilities: ModelCapabilities = ModelCapabilities.DEFAULT,
    val modelId: String = "",
    val modelName: String = "",
    val isRemote: Boolean = false
)

sealed interface GenerationEvent {
    data class Delta(val text: String) : GenerationEvent
    data class Reasoning(val text: String) : GenerationEvent
    data class Metrics(val metrics: RuntimeMetrics) : GenerationEvent
}
