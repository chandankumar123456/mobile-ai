package com.mobileai.assistant.domain.dot

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Category classifications for persistent Dot memories.
 */
enum class DotMemoryCategory {
    USER_PREFERENCE,   // e.g. "User prefers Kotlin with Jetpack Compose"
    PROJECT_FACT,      // e.g. "Target Android SDK is 35"
    CORE_INSTRUCTION,  // e.g. "Always generate code with unit tests"
    SESSION_NOTE       // e.g. "Previous refactoring of NetworkClient was paused at step 3"
}

/**
 * Persistent memory unit tied to a specific Dot profile.
 */
@Serializable
data class DotMemory(
    val id: String = UUID.randomUUID().toString(),
    val dotId: String,
    val key: String,
    val content: String,
    val category: DotMemoryCategory = DotMemoryCategory.USER_PREFERENCE,
    val confidence: Float = 1.0f,
    val sourceMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
