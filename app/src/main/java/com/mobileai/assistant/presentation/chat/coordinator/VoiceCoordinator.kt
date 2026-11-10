package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.data.voice.SpeechToTextManager
import com.mobileai.assistant.data.voice.TextSanitizer
import com.mobileai.assistant.data.voice.TextToSpeechManager
import com.mobileai.assistant.domain.model.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Coordinator responsible for Speech-to-Text and Text-to-Speech interactions,
 * voice visualizer state, and speech sanitization.
 */
class VoiceCoordinator(
    private val sttManager: SpeechToTextManager?,
    private val ttsManager: TextToSpeechManager?
) {
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    fun startListening() {
        _voiceState.value = VoiceState.Listening()
        sttManager?.startListening()
    }

    fun stopListening() {
        _voiceState.value = VoiceState.Idle
        sttManager?.stopListening()
    }

    fun speak(text: String, messageId: String? = null) {
        val sanitized = TextSanitizer.cleanForSpeech(text)
        val utteranceId = UUID.randomUUID().toString()
        _speakingMessageId.value = messageId
        _isSpeaking.value = true
        _voiceState.value = VoiceState.Speaking(utteranceId = utteranceId, messageId = messageId)
        ttsManager?.speak(text = sanitized, messageId = messageId) {
            _isSpeaking.value = false
            _speakingMessageId.value = null
            _voiceState.value = VoiceState.Idle
        }
    }

    fun stopSpeaking() {
        ttsManager?.stop()
        _isSpeaking.value = false
        _speakingMessageId.value = null
        _voiceState.value = VoiceState.Idle
    }

    fun cleanup() {
        sttManager?.cancel()
        ttsManager?.stop()
    }
}
