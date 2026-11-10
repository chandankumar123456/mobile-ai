package com.mobileai.assistant.domain.repository

import com.mobileai.assistant.domain.model.VoiceSettings
import kotlinx.coroutines.flow.StateFlow

interface VoiceSettingsRepository {
    val settings: StateFlow<VoiceSettings>
    suspend fun updateSettings(transform: (VoiceSettings) -> VoiceSettings)
    suspend fun setAutoSpeak(enabled: Boolean)
    suspend fun setSpeechRate(rate: Float)
    suspend fun setSpeechPitch(pitch: Float)
    suspend fun setBackgroundEnabled(enabled: Boolean)
    suspend fun setSelectedVoice(voiceName: String?)
}

