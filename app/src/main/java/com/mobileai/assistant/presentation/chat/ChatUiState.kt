package com.mobileai.assistant.presentation.chat

import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.EngineState
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.model.ModelCapabilities
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.ThinkingEffort
import com.mobileai.assistant.domain.model.VoiceState
import com.mobileai.assistant.domain.task.DotTask
import com.mobileai.assistant.domain.tool.ToolApprovalRequest

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<ConversationSession> = emptyList(),
    val activeConversationId: String = "",
    val activeConversationTitle: String = "New Chat",
    val searchQuery: String = "",
    val inputText: String = "",
    val engineState: EngineState = EngineState.Uninitialized,
    val loadedModel: ModelInfo? = null,
    val activeEndpoint: RemoteEndpointConfig? = null,
    val activeTarget: InferenceTarget? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val isThinkingEnabled: Boolean = false,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val voiceState: VoiceState = VoiceState.Idle,
    val isVoiceModeActive: Boolean = false,
    val isListening: Boolean = false,
    val partialVoiceInput: String = "",
    val autoSpeakEnabled: Boolean = false,
    val speakingMessageId: String? = null,
    val isSpeaking: Boolean = false,
    val isSpeechPaused: Boolean = false,
    val isThinkingSupported: Boolean = true,
    val isThinkingEffortSupported: Boolean = true,
    val capabilities: ModelCapabilities = ModelCapabilities.FALLBACK,
    val profiles: Map<String, ModelSettingsProfile> = emptyMap(),
    val responseLanguage: ResponseLanguage = ResponseLanguage.AUTO,
    val pendingAttachments: List<Attachment> = emptyList(),
    val isContinuing: Boolean = false,
    val exportNotification: String? = null,
    val conversationTarget: InferenceTarget? = null,
    val conversationSystemPrompt: String? = null,
    val isModelSettingsSheetVisible: Boolean = false,
    val isSystemPromptDialogVisible: Boolean = false,
    val isLanguageSelectorVisible: Boolean = false,
    val effectiveParams: GenerationParams = GenerationParams(),
    val activeDot: DotProfile = DotProfile.STARTER_PROFILES.first(),
    val availableDots: List<DotProfile> = DotProfile.STARTER_PROFILES,
    val selectedDotFilterId: String? = DotProfile.BUILTIN_GENERAL_ID,
    val activeTasks: List<DotTask> = emptyList(),
    val pendingApprovals: List<ToolApprovalRequest> = emptyList(),
    val isTaskProgressExpanded: Boolean = true
) {
    val isModelAvailable: Boolean
        get() = loadedModel != null || (activeEndpoint != null && activeEndpoint.isConfigured) || activeTarget != null || conversationTarget != null

    val canSendMessage: Boolean
        get() = (inputText.isNotBlank() || pendingAttachments.isNotEmpty()) && isModelAvailable && !isGenerating && !engineState.isBusy

    val filteredConversations: List<ConversationSession>
        get() {
            val sorted = conversations.sortedByDescending { it.updatedAt }
            val dotFiltered = if (selectedDotFilterId == null) {
                sorted
            } else if (selectedDotFilterId == DotProfile.BUILTIN_GENERAL_ID) {
                sorted.filter { it.dotId == DotProfile.BUILTIN_GENERAL_ID || it.dotId.isNullOrBlank() }
            } else {
                sorted.filter { it.dotId == selectedDotFilterId }
            }

            return if (searchQuery.isBlank()) {
                dotFiltered
            } else {
                val q = searchQuery.trim()
                dotFiltered.filter { session ->
                    session.title.contains(q, ignoreCase = true) ||
                        session.messages.any { it.content.contains(q, ignoreCase = true) }
                }
            }
        }

    val activeTargetName: String
        get() = when {
            conversationTarget is InferenceTarget.Local -> "Local: ${conversationTarget.model.displayName}"
            conversationTarget is InferenceTarget.Remote -> "Remote: ${conversationTarget.endpoint.name} (${conversationTarget.modelId})"
            activeTarget is InferenceTarget.Local -> "Local: ${activeTarget.model.displayName}"
            activeTarget is InferenceTarget.Remote -> "Remote: ${activeTarget.endpoint.name} (${activeTarget.modelId})"
            loadedModel != null -> "Local: ${loadedModel.displayName}"
            activeEndpoint != null -> "Remote: ${activeEndpoint.name} (${activeEndpoint.selectedModel})"
            else -> "No Model Selected"
        }
}
