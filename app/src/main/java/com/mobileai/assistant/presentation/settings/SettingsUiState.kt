package com.mobileai.assistant.presentation.settings

import com.mobileai.assistant.data.voice.VoiceInfo
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.VoiceSettings
import java.text.DecimalFormat

enum class SettingsTab(val title: String) {
    GENERAL("General"),
    SAMPLING("Sampling"),
    RUNTIME("Runtime"),
    VOICE("Voice"),
    STORAGE("Storage & System")
}

data class SettingsUiState(
    val settings: InferenceSettings = InferenceSettings.DEFAULT,
    val voiceSettings: VoiceSettings = VoiceSettings.DEFAULT,
    val availableVoices: List<VoiceInfo> = emptyList(),
    val availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    val modelStorageBytes: Long = 0L,
    val attachmentStorageBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val savedMessage: String? = null,
    val selectedTab: SettingsTab = SettingsTab.GENERAL
) {
    val formattedModelStorage: String
        get() = formatBytes(modelStorageBytes)

    val formattedAttachmentStorage: String
        get() = formatBytes(attachmentStorageBytes)

    val formattedAvailableStorage: String
        get() = formatBytes(availableStorageBytes)

    val totalAppStorageBytes: Long
        get() = modelStorageBytes + attachmentStorageBytes

    val formattedTotalAppStorage: String
        get() = formatBytes(totalAppStorageBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return "${DecimalFormat("#.##").format(size)} ${units[unitIndex]}"
        }
    }
}
