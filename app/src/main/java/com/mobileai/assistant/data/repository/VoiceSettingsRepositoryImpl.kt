package com.mobileai.assistant.data.repository

import android.content.Context
import com.mobileai.assistant.domain.model.VoiceSettings
import com.mobileai.assistant.domain.repository.VoiceSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VoiceSettingsRepositoryImpl(
    context: Context
) : VoiceSettingsRepository {

    private val prefs = context.getSharedPreferences("voice_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        VoiceSettings(
            autoSpeakResponses = prefs.getBoolean(KEY_AUTO_SPEAK, false),
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f),
            speechPitch = prefs.getFloat(KEY_SPEECH_PITCH, 1.0f),
            backgroundSessionEnabled = prefs.getBoolean(KEY_BG_ENABLED, true),
            continuousConversation = prefs.getBoolean(KEY_CONTINUOUS, true),
            selectedVoiceName = prefs.getString(KEY_SELECTED_VOICE, null)
        )
    )
    override val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    override suspend fun updateSettings(transform: (VoiceSettings) -> VoiceSettings) {
        _settings.update { current ->
            val updated = transform(current)
            save(updated)
            updated
        }
    }

    override suspend fun setAutoSpeak(enabled: Boolean) {
        updateSettings { it.copy(autoSpeakResponses = enabled) }
    }

    override suspend fun setSpeechRate(rate: Float) {
        updateSettings { it.copy(speechRate = rate) }
    }

    override suspend fun setSpeechPitch(pitch: Float) {
        updateSettings { it.copy(speechPitch = pitch) }
    }

    override suspend fun setBackgroundEnabled(enabled: Boolean) {
        updateSettings { it.copy(backgroundSessionEnabled = enabled) }
    }

    override suspend fun setSelectedVoice(voiceName: String?) {
        updateSettings { it.copy(selectedVoiceName = voiceName) }
    }

    private fun save(settings: VoiceSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTO_SPEAK, settings.autoSpeakResponses)
            .putFloat(KEY_SPEECH_RATE, settings.speechRate)
            .putFloat(KEY_SPEECH_PITCH, settings.speechPitch)
            .putBoolean(KEY_BG_ENABLED, settings.backgroundSessionEnabled)
            .putBoolean(KEY_CONTINUOUS, settings.continuousConversation)
            .putString(KEY_SELECTED_VOICE, settings.selectedVoiceName)
            .apply()
    }

    companion object {
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_BG_ENABLED = "bg_enabled"
        private const val KEY_CONTINUOUS = "continuous"
        private const val KEY_SELECTED_VOICE = "selected_voice"
    }
}

