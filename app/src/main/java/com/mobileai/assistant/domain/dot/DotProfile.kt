package com.mobileai.assistant.domain.dot

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Avatar preset representations for Dots.
 */
enum class DotAvatarPreset {
    ROBOT, CODE, ANALYTICS, WRITER, SEARCH, BRAIN, TERMINAL, CUSTOM
}

/**
 * Configuration for model preferences specific to a Dot.
 */
@Serializable
data class DotModelPreference(
    val targetIdOverride: String? = null,
    val temperatureOverride: Float? = null,
    val topPOverride: Float? = null,
    val systemPromptSuffix: String? = null
)

/**
 * Persistent specialized AI assistant profile.
 */
@Serializable
data class DotProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val description: String,
    val avatarPreset: DotAvatarPreset = DotAvatarPreset.ROBOT,
    val customAvatarUri: String? = null,
    val colorHex: String = "#4F46E5", // Default Indigo
    val systemPrompt: String,
    val modelPreference: DotModelPreference? = null,
    val isBuiltIn: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val BUILTIN_GENERAL_ID = "builtin-general"
        const val BUILTIN_CODER_ID = "builtin-coder"
        const val BUILTIN_RESEARCHER_ID = "builtin-researcher"
        const val BUILTIN_WRITER_ID = "builtin-writer"

        val STARTER_PROFILES: List<DotProfile> = listOf(
            DotProfile(
                id = BUILTIN_GENERAL_ID,
                name = "General Assistant",
                role = "Conversational Partner",
                description = "Versatile AI companion for general conversation, brainstorming, and daily assistance.",
                avatarPreset = DotAvatarPreset.ROBOT,
                colorHex = "#4F46E5",
                systemPrompt = "You are a helpful, accurate, and concise AI assistant capable of answering questions, drafting content, and organizing thoughts.",
                isBuiltIn = true
            ),
            DotProfile(
                id = BUILTIN_CODER_ID,
                name = "Code Architect",
                role = "Full-Stack Software Engineer",
                description = "Specialist in systems architecture, algorithms, idiomatic code, and test-driven development.",
                avatarPreset = DotAvatarPreset.CODE,
                colorHex = "#10B981",
                systemPrompt = "You are an expert software engineer and systems architect. You write idiomatic, safe, and maintainable code, analyze software patterns, and produce structured code artifacts with complete error handling.",
                isBuiltIn = true
            ),
            DotProfile(
                id = BUILTIN_RESEARCHER_ID,
                name = "Research Analyst",
                role = "Deep Research & Data Synthesis",
                description = "Methodical investigator for deep literature, factual synthesis, data analysis, and comparative studies.",
                avatarPreset = DotAvatarPreset.ANALYTICS,
                colorHex = "#0EA5E9",
                systemPrompt = "You are a methodical research analyst. You break complex questions into logical queries, verify facts, structure findings into organized tables and reports, and cite observations with rigorous clarity.",
                isBuiltIn = true
            ),
            DotProfile(
                id = BUILTIN_WRITER_ID,
                name = "Technical Writer",
                role = "Documentation & Content Specialist",
                description = "Crafts publication-ready technical documentation, guides, specifications, and long-form prose.",
                avatarPreset = DotAvatarPreset.WRITER,
                colorHex = "#F59E0B",
                systemPrompt = "You are a precise technical writer. You specialize in generating clear, well-structured documentation, markdown articles, specifications, and instructional guides.",
                isBuiltIn = true
            )
        )
    }
}
