package com.mobileai.assistant.data.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class VoiceInfo(
    val name: String,
    val displayName: String,
    val locale: String
)

/**
 * Manages Text-to-Speech playback using Android's native [TextToSpeech] engine.
 * Supports play, pause, resume, stop, speech rate, pitch, and voice selection.
 */
class TextToSpeechManager(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentSpeakingMessageId = MutableStateFlow<String?>(null)
    val currentSpeakingMessageId: StateFlow<String?> = _currentSpeakingMessageId.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<VoiceInfo>> = _availableVoices.asStateFlow()

    private var activeUtteranceId: String? = null
    private var onSpeechDoneCallback: (() -> Unit)? = null

    // Track active spoken content for pause/resume
    private var currentFullText: String = ""
    private var lastSpokenCharOffset: Int = 0

    var speechRate: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setSpeechRate(field)
        }

    var speechPitch: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 1.5f)
            tts?.setPitch(field)
        }

    var selectedVoiceName: String? = null
        set(value) {
            field = value
            applySelectedVoice()
        }

    var languageBcp47: String? = null
        private set

    fun setLanguage(bcp47: String): Boolean {
        languageBcp47 = bcp47
        if (!isInitialized) {
            Log.w(TAG, "TextToSpeech is not initialized yet. Language $bcp47 will be stored.")
            return false
        }
        val locale = Locale.forLanguageTag(bcp47)
        val availability = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return if (availability >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = locale
            true
        } else {
            Log.w(TAG, "Language '$bcp47' ($locale) is not available (code: $availability). Falling back to default or Locale.US.")
            val defaultLocale = Locale.getDefault()
            val fallbackResult = tts?.setLanguage(defaultLocale)
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            false
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            val langResult = tts?.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)

            refreshVoices()
            applySelectedVoice()
            languageBcp47?.let { setLanguage(it) }
            setupProgressListener()
            Log.i(TAG, "TTS initialized successfully.")
        } else {
            Log.e(TAG, "TTS initialization failed with status $status")
        }
    }

    private fun refreshVoices() {
        try {
            val systemVoices = tts?.voices
            if (!systemVoices.isNullOrEmpty()) {
                val list = systemVoices.map { voice ->
                    val langName = voice.locale.displayLanguage.ifBlank { voice.locale.language }
                    val country = voice.locale.displayCountry
                    val label = if (country.isNotBlank()) "$langName ($country) - ${voice.name.takeLast(10)}" else "$langName - ${voice.name.takeLast(10)}"
                    VoiceInfo(
                        name = voice.name,
                        displayName = label,
                        locale = voice.locale.toString()
                    )
                }.sortedBy { it.displayName }
                _availableVoices.value = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query voices: ${e.message}")
        }
    }

    private fun applySelectedVoice() {
        val voiceName = selectedVoiceName ?: return
        if (!isInitialized) return
        try {
            val matchedVoice = tts?.voices?.find { it.name == voiceName }
            if (matchedVoice != null) {
                tts?.voice = matchedVoice
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply voice $voiceName: ${e.message}")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _isPaused.value = false
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    _isSpeaking.value = false
                    _isPaused.value = false
                    _currentSpeakingMessageId.value = null
                    currentFullText = ""
                    lastSpokenCharOffset = 0
                    onSpeechDoneCallback?.invoke()
                    onSpeechDoneCallback = null
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    _isSpeaking.value = false
                    _isPaused.value = false
                    _currentSpeakingMessageId.value = null
                    onSpeechDoneCallback?.invoke()
                    onSpeechDoneCallback = null
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId == activeUtteranceId) {
                    lastSpokenCharOffset = start
                }
            }
        })
    }

    fun speak(
        text: String,
        messageId: String? = null,
        languageBcp47: String? = null,
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = TextSanitizer.cleanForSpeech(text)
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        val effectiveLanguage = languageBcp47
            ?: if (messageId != null && (messageId.length <= 8 && !messageId.contains("-") || messageId.matches(Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]+)?$")))) {
                messageId
            } else {
                null
            }

        val effectiveMessageId = if (effectiveLanguage == messageId) null else messageId

        if (!effectiveLanguage.isNullOrBlank() && !effectiveLanguage.equals("auto", ignoreCase = true)) {
            setLanguage(effectiveLanguage)
        }

        stop()

        currentFullText = cleanText
        lastSpokenCharOffset = 0
        activeUtteranceId = UUID.randomUUID().toString()
        _currentSpeakingMessageId.value = effectiveMessageId
        onSpeechDoneCallback = onDone
        _isPaused.value = false

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, activeUtteranceId)
        }

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, activeUtteranceId)
    }

    fun pause() {
        if (_isSpeaking.value || tts?.isSpeaking == true) {
            tts?.stop()
            _isSpeaking.value = false
            _isPaused.value = true
        }
    }

    fun resume() {
        if (!_isPaused.value || currentFullText.isBlank()) return

        val remainingText = if (lastSpokenCharOffset in 0 until currentFullText.length) {
            currentFullText.substring(lastSpokenCharOffset)
        } else {
            currentFullText
        }

        if (remainingText.isBlank()) {
            stop()
            return
        }

        activeUtteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, activeUtteranceId)
        }

        _isPaused.value = false
        _isSpeaking.value = true
        tts?.speak(remainingText, TextToSpeech.QUEUE_FLUSH, params, activeUtteranceId)
    }

    fun stop() {
        if (_isSpeaking.value || _isPaused.value || tts?.isSpeaking == true) {
            tts?.stop()
        }
        _isSpeaking.value = false
        _isPaused.value = false
        _currentSpeakingMessageId.value = null
        activeUtteranceId = null
        currentFullText = ""
        lastSpokenCharOffset = 0
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
    }
}
