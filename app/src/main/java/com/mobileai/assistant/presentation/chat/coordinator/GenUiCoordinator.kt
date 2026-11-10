package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.data.genui.GenUiValidationResult
import com.mobileai.assistant.data.genui.GenUiValidator
import com.mobileai.assistant.domain.genui.GenUiEvent
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.repository.ChatRepository

/**
 * Coordinator responsible for Generative UI validation, state updates,
 * and user interaction event dispatching (form submits, button clicks, chart selection).
 */
class GenUiCoordinator(
    private val chatRepository: ChatRepository
) {

    /**
     * Dispatches a user interaction event from a GenUI component.
     * Updates local component state or sends an action message into the conversation.
     */
    suspend fun handleGenUiEvent(
        event: GenUiEvent,
        activeMessages: List<ChatMessage>,
        onSendMessage: (String) -> Unit
    ) {
        when (event) {
            is GenUiEvent.ButtonClick -> {
                val action = event.payload["action"] ?: event.actionId
                onSendMessage(action)
            }
            is GenUiEvent.FormSubmit -> {
                val formattedData = event.fieldValues.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                val summary = "Form Submitted (${event.formId}): [$formattedData]"
                onSendMessage(summary)
            }
            is GenUiEvent.SelectionChange -> {
                val label = event.selectedLabel ?: event.selectedKey
                onSendMessage("Selected: $label")
            }
            is GenUiEvent.DiagramNodeClick -> {
                val label = event.nodeLabel ?: event.nodeId
                onSendMessage("Clicked node: $label")
            }
        }
    }

    /**
     * Validates and optionally repairs raw GenUI JSON strings before rendering.
     */
    fun validateAndRepair(rawJson: String): GenUiValidationResult {
        return GenUiValidator.validate(rawJson)
    }
}
