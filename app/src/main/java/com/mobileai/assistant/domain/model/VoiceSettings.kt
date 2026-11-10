package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VoiceSettings(
    val autoSpeakResponses: Boolean = false,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val backgroundSessionEnabled: Boolean = true,
    val continuousConversation: Boolean = true,
    val selectedVoiceName: String? = null
) {
    companion object {
        val DEFAULT = VoiceSettings()

        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f

        const val MIN_SPEECH_PITCH = 0.5f
        const val MAX_SPEECH_PITCH = 1.5f
    }
}

