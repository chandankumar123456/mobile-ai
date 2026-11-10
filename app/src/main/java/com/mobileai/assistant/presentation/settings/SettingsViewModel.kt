package com.mobileai.assistant.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.data.voice.TextToSpeechManager
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.ThinkingEffort
import com.mobileai.assistant.domain.model.VoiceSettings
import com.mobileai.assistant.domain.repository.VoiceSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    initialSettings: InferenceSettings = InferenceSettings.DEFAULT,
    private val voiceSettingsRepository: VoiceSettingsRepository? = null,
    private val ttsManager: TextToSpeechManager? = null,
    private val fileManager: ModelFileManager? = null,
    private val onSettingsSaved: (InferenceSettings) -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            settings = initialSettings,
            voiceSettings = voiceSettingsRepository?.settings?.value ?: VoiceSettings.DEFAULT,
            availableVoices = ttsManager?.availableVoices?.value ?: emptyList()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStorage()

        voiceSettingsRepository?.let { repo ->
            viewModelScope.launch {
                repo.settings.collect { vs ->
                    _uiState.update { it.copy(voiceSettings = vs) }
                }
            }
        }

        ttsManager?.let { tts ->
            viewModelScope.launch {
                tts.availableVoices.collect { voices ->
                    _uiState.update { it.copy(availableVoices = voices) }
                }
            }
        }
    }

    fun selectTab(tab: SettingsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refreshStorage() {
        fileManager?.let { fm ->
            _uiState.update {
                it.copy(
                    modelStorageBytes = fm.getTotalModelStorageBytes(),
                    attachmentStorageBytes = fm.getAttachmentStorageBytes(),
                    availableStorageBytes = fm.getAvailableStorageBytes()
                )
            }
        }
    }

    fun updateInferenceSettings(newSettings: InferenceSettings) {
        _uiState.update { it.copy(settings = newSettings) }
    }

    // --- General Settings ---

    fun updateResponseLanguage(language: ResponseLanguage) {
        _uiState.update { it.copy(settings = it.settings.copy(responseLanguage = language)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateSystemPrompt(prompt: String) {
        _uiState.update { it.copy(settings = it.settings.copy(systemPrompt = prompt)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateThinkingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(thinkingEnabled = enabled)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateThinkingEffort(effort: ThinkingEffort) {
        _uiState.update { it.copy(settings = it.settings.copy(thinkingEffort = effort)) }
        onSettingsSaved(_uiState.value.settings)
    }

    // --- Sampling Parameters ---

    fun updateTemperature(temp: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(temperature = temp)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateTopP(topP: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(topP = topP)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateTopK(topK: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(topK = topK)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateMinP(minP: Double) {
        _uiState.update { it.copy(settings = it.settings.copy(minP = minP)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateRepeatPenalty(repeatPenalty: Double) {
        _uiState.update { it.copy(settings = it.settings.copy(repeatPenalty = repeatPenalty)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updatePresencePenalty(presencePenalty: Double) {
        _uiState.update { it.copy(settings = it.settings.copy(presencePenalty = presencePenalty)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateFrequencyPenalty(frequencyPenalty: Double) {
        _uiState.update { it.copy(settings = it.settings.copy(frequencyPenalty = frequencyPenalty)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateSeed(seed: Long) {
        _uiState.update { it.copy(settings = it.settings.copy(seed = seed)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateMaxTokens(maxTokens: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(maxTokens = maxTokens)) }
        onSettingsSaved(_uiState.value.settings)
    }

    // --- Runtime Configuration ---

    fun updateContextSize(contextSize: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(contextSize = contextSize)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateThreads(threads: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(threads = threads)) }
        onSettingsSaved(_uiState.value.settings)
    }

    fun updateBatchSize(batchSize: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(batchSize = batchSize)) }
        onSettingsSaved(_uiState.value.settings)
    }

    // --- Voice & Speech ---

    fun updateAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            voiceSettingsRepository?.setAutoSpeak(enabled)
        }
    }

    fun updateSpeechRate(rate: Float) {
        viewModelScope.launch {
            voiceSettingsRepository?.setSpeechRate(rate)
        }
    }

    fun updateSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            voiceSettingsRepository?.setSpeechPitch(pitch)
        }
    }

    fun updateBackgroundVoice(enabled: Boolean) {
        viewModelScope.launch {
            voiceSettingsRepository?.setBackgroundEnabled(enabled)
        }
    }

    fun updateSelectedVoice(voiceName: String?) {
        viewModelScope.launch {
            voiceSettingsRepository?.setSelectedVoice(voiceName)
            ttsManager?.selectedVoiceName = voiceName
        }
    }

    fun resetToDefaults() {
        _uiState.update { it.copy(settings = InferenceSettings.DEFAULT) }
        onSettingsSaved(_uiState.value.settings)
        viewModelScope.launch {
            voiceSettingsRepository?.updateSettings { VoiceSettings.DEFAULT }
        }
        refreshStorage()
    }

    fun dismissSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }
}
