package com.mobileai.assistant.dot

import com.mobileai.assistant.domain.dot.DotAvatarPreset
import com.mobileai.assistant.domain.dot.DotModelPreference
import com.mobileai.assistant.domain.dot.DotProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DotProfileSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun testStarterProfilesSanity() {
        assertEquals(4, DotProfile.STARTER_PROFILES.size)

        val general = DotProfile.STARTER_PROFILES.find { it.id == DotProfile.BUILTIN_GENERAL_ID }
        assertNotNull(general)
        assertEquals("General Assistant", general?.name)
        assertEquals(DotAvatarPreset.ROBOT, general?.avatarPreset)
        assertEquals("#4F46E5", general?.colorHex)
        assertTrue(general?.isBuiltIn == true)

        val coder = DotProfile.STARTER_PROFILES.find { it.id == DotProfile.BUILTIN_CODER_ID }
        assertNotNull(coder)
        assertEquals("Code Architect", coder?.name)
        assertEquals(DotAvatarPreset.CODE, coder?.avatarPreset)
        assertEquals("#10B981", coder?.colorHex)
        assertTrue(coder?.isBuiltIn == true)

        val researcher = DotProfile.STARTER_PROFILES.find { it.id == DotProfile.BUILTIN_RESEARCHER_ID }
        assertNotNull(researcher)
        assertEquals("Research Analyst", researcher?.name)
        assertEquals(DotAvatarPreset.ANALYTICS, researcher?.avatarPreset)
        assertEquals("#0EA5E9", researcher?.colorHex)
        assertTrue(researcher?.isBuiltIn == true)

        val writer = DotProfile.STARTER_PROFILES.find { it.id == DotProfile.BUILTIN_WRITER_ID }
        assertNotNull(writer)
        assertEquals("Technical Writer", writer?.name)
        assertEquals(DotAvatarPreset.WRITER, writer?.avatarPreset)
        assertEquals("#F59E0B", writer?.colorHex)
        assertTrue(writer?.isBuiltIn == true)
    }

    @Test
    fun testDotProfileFullRoundTrip() {
        val original = DotProfile(
            id = "custom-dot-123",
            name = "Security Auditor",
            role = "Penetration Tester",
            description = "Analyzes source code for CVEs and security flaws.",
            avatarPreset = DotAvatarPreset.TERMINAL,
            customAvatarUri = "content://media/external/images/security.png",
            colorHex = "#DC2626",
            systemPrompt = "You are an elite penetration tester.",
            modelPreference = DotModelPreference(
                targetIdOverride = "local-deepseek-coder",
                temperatureOverride = 0.2f,
                topPOverride = 0.95f,
                systemPromptSuffix = "Respond in OWASP format."
            ),
            isBuiltIn = false,
            isArchived = true,
            createdAt = 1700000000000L,
            updatedAt = 1700000050000L
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<DotProfile>(serialized)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.role, deserialized.role)
        assertEquals(original.description, deserialized.description)
        assertEquals(original.avatarPreset, deserialized.avatarPreset)
        assertEquals(original.customAvatarUri, deserialized.customAvatarUri)
        assertEquals(original.colorHex, deserialized.colorHex)
        assertEquals(original.systemPrompt, deserialized.systemPrompt)
        assertEquals(original.isBuiltIn, deserialized.isBuiltIn)
        assertEquals(original.isArchived, deserialized.isArchived)
        assertEquals(original.createdAt, deserialized.createdAt)
        assertEquals(original.updatedAt, deserialized.updatedAt)

        assertNotNull(deserialized.modelPreference)
        assertEquals("local-deepseek-coder", deserialized.modelPreference?.targetIdOverride)
        assertEquals(0.2f, deserialized.modelPreference?.temperatureOverride ?: 0f, 0.001f)
        assertEquals(0.95f, deserialized.modelPreference?.topPOverride ?: 0f, 0.001f)
        assertEquals("Respond in OWASP format.", deserialized.modelPreference?.systemPromptSuffix)
    }

    @Test
    fun testDotProfileMinimalDefaultsRoundTrip() {
        val minimalJson = """
            {
                "id": "minimal-dot",
                "name": "Minimalist",
                "role": "Helper",
                "description": "Short",
                "systemPrompt": "Help user"
            }
        """.trimIndent()

        val parsed = json.decodeFromString<DotProfile>(minimalJson)
        assertEquals("minimal-dot", parsed.id)
        assertEquals("Minimalist", parsed.name)
        assertEquals(DotAvatarPreset.ROBOT, parsed.avatarPreset)
        assertEquals("#4F46E5", parsed.colorHex)
        assertNull(parsed.customAvatarUri)
        assertNull(parsed.modelPreference)
        assertFalse(parsed.isBuiltIn)
        assertFalse(parsed.isArchived)
    }

    @Test
    fun testAvatarPresetsCoverage() {
        for (preset in DotAvatarPreset.values()) {
            val profile = DotProfile(
                name = "Test ${preset.name}",
                role = "Tester",
                description = "Desc",
                avatarPreset = preset,
                systemPrompt = "Prompt"
            )
            val encoded = json.encodeToString(profile)
            val decoded = json.decodeFromString<DotProfile>(encoded)
            assertEquals(preset, decoded.avatarPreset)
        }
    }
}
