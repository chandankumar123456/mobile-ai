package com.mobileai.assistant.domain.model

import java.text.DecimalFormat

/**
 * Domain model representing metadata and disk status of a local GGUF model.
 */
data class ModelInfo(
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long = 0L,
    val architecture: String? = null,
    val quantization: String? = null,
    val contextLength: Int? = null,
    val tensorCount: Long? = null,
    val kvCount: Long? = null,
    val author: String? = null,
    val description: String? = null,
    val isLoaded: Boolean = false,
    val chatTemplate: String? = null,
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val hasVisionProjector: Boolean = false,
    val capabilities: ModelCapabilities? = null,
    val lastUsedAt: Long? = null
) {
    val id: String
        get() = filePath.ifBlank { fileName }

    val name: String
        get() = displayName

    val formattedSize: String
        get() {
            if (fileSizeBytes <= 0) return "Unknown size"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = fileSizeBytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return "${DecimalFormat("#.##").format(size)} ${units[unitIndex]}"
        }

    val displayName: String
        get() = fileName.removeSuffix(".gguf")
}

