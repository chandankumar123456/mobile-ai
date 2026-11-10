package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelSettingsProfile(
    val modelId: String,
    val displayName: String = "",
    val params: GenerationParams = GenerationParams(),
    val capabilitiesOverride: ModelCapabilities? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
