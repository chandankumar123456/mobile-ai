package com.mobileai.assistant.domain.model

/**
 * High-level state representing current voice recognition and speech synthesis activities.
 */
sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val rmsDb: Float = 0f) : VoiceState
    data object Processing : VoiceState
    data object Generating : VoiceState
    data class Speaking(val utteranceId: String, val messageId: String? = null) : VoiceState
    data object Paused : VoiceState
    data class Error(val message: String) : VoiceState

    val isListening: Boolean
        get() = this is Listening

    val isSpeaking: Boolean
        get() = this is Speaking

    val isBusy: Boolean
        get() = this !is Idle && this !is Paused && this !is Error
}

