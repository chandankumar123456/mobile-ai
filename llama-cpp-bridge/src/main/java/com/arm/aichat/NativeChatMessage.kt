package com.arm.aichat

/**
 * A single chat message passed to the native chat-template prefill.
 */
data class NativeChatMessage(
    val role: String,
    val content: String,
)
