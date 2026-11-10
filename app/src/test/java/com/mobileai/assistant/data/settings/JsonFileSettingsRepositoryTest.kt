package com.mobileai.assistant.data.settings

import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JsonFileSettingsRepositoryTest {

    private fun tempDir(): File = Files.createTempDirectory("mobile-ai-settings").toFile()

    @Test
    fun testDefaultsOnEmptyDirectory() {
        val repository = JsonFileSettingsRepository(tempDir())

        assertEquals(InferenceSettings.DEFAULT, repository.settings.value)
        assertTrue(repository.profiles.value.isEmpty())
        assertNull(repository.lastLocalModelPath.value)
    }

    @Test
    fun testSettingsRoundTrip() {
        val root = tempDir()
        val repository = JsonFileSettingsRepository(root)
        repository.update(
            repository.settings.value.copy(
                temperature = 0.42f,
                topK = 25,
                minP = 0.15,
                thinkingEnabled = true
            )
        )

        val reloaded = JsonFileSettingsRepository(root)

        assertEquals(0.42f, reloaded.settings.value.temperature, 0.001f)
        assertEquals(25, reloaded.settings.value.topK)
        assertEquals(0.15, reloaded.settings.value.minP, 0.0001)
        assertTrue(reloaded.settings.value.thinkingEnabled)
    }

    @Test
    fun testProfileSaveLoadAndDelete() {
        val root = tempDir()
        val repository = JsonFileSettingsRepository(root)
        val profile = ModelSettingsProfile(
            modelId = "model-1",
            displayName = "Model One",
            params = GenerationParams(temperature = 0.1, topK = 10)
        )

        repository.saveProfile("local:abc", profile)

        val reloaded = JsonFileSettingsRepository(root)
        assertEquals(profile, reloaded.profileFor("local:abc"))

        reloaded.deleteProfile("local:abc")

        val afterDelete = JsonFileSettingsRepository(root)
        assertNull(afterDelete.profileFor("local:abc"))
        assertTrue(afterDelete.profiles.value.isEmpty())
    }

    @Test
    fun testLastLocalModelPathPersists() {
        val root = tempDir()
        val repository = JsonFileSettingsRepository(root)
        repository.setLastLocalModel("/models/qwen.gguf")

        val reloaded = JsonFileSettingsRepository(root)

        assertEquals("/models/qwen.gguf", reloaded.lastLocalModelPath.value)

        reloaded.setLastLocalModel(null)
        assertNull(JsonFileSettingsRepository(root).lastLocalModelPath.value)
    }

    @Test
    fun testResolveParamsUsesProfileThenGlobal() {
        val repository = JsonFileSettingsRepository(tempDir())
        repository.update(repository.settings.value.copy(topK = 33))

        assertEquals(33, repository.resolveParams("missing").topK)

        repository.saveProfile(
            "local:abc",
            ModelSettingsProfile(modelId = "m", params = GenerationParams(topK = 7))
        )

        assertEquals(7, repository.resolveParams("local:abc").topK)
        assertEquals(33, repository.resolveParams("other").topK)
    }

    @Test
    fun testAtomicFileWrittenWithoutTempLeftover() {
        val root = tempDir()
        val repository = JsonFileSettingsRepository(root)
        repository.update(repository.settings.value.copy(temperature = 0.5f))

        val file = File(root, "settings_v2.json")
        assertTrue(file.exists())
        assertTrue(file.length() > 0L)
        assertFalse(File(root, "settings_v2.json.tmp").exists())
    }

    @Test
    fun testLegacySettingsMigrated() {
        val root = tempDir()
        val legacyJson = """{"temperature":0.25,"systemPrompt":"Legacy prompt","topK":12}"""

        val repository = JsonFileSettingsRepository(root, legacyJson)

        assertEquals(0.25f, repository.settings.value.temperature, 0.001f)
        assertEquals("Legacy prompt", repository.settings.value.systemPrompt)
        assertEquals(12, repository.settings.value.topK)

        val reloaded = JsonFileSettingsRepository(root)
        assertEquals(0.25f, reloaded.settings.value.temperature, 0.001f)
        assertEquals("Legacy prompt", reloaded.settings.value.systemPrompt)
    }

    @Test
    fun testLegacyCorruptJsonFallsBackToDefaults() {
        val repository = JsonFileSettingsRepository(tempDir(), "{not valid json")

        assertEquals(InferenceSettings.DEFAULT, repository.settings.value)
    }

    @Test
    fun testUnknownKeysAreIgnored() {
        val root = tempDir()
        File(root, "settings_v2.json").writeText(
            """{"version":2,"settings":{"temperature":0.33,"futureField":true},"profiles":{},"lastLocalModelPath":null,"futureTop":"x"}"""
        )

        val repository = JsonFileSettingsRepository(root)

        assertEquals(0.33f, repository.settings.value.temperature, 0.001f)
    }

    @Test
    fun testModelKeyLocalIsStableHash() {
        val key = AppSettingsRepository.modelKeyLocal("/data/models/test.gguf")
        val same = AppSettingsRepository.modelKeyLocal("/data/models/test.gguf")
        val other = AppSettingsRepository.modelKeyLocal("/data/models/other.gguf")

        assertEquals(key, same)
        assertNotEquals(key, other)
        assertTrue(key.startsWith("local:"))
        assertEquals(64, key.removePrefix("local:").length)
    }

    @Test
    fun testModelKeyLocalNormalizesSeparators() {
        assertEquals(
            AppSettingsRepository.modelKeyLocal("C:\\models\\test.gguf"),
            AppSettingsRepository.modelKeyLocal("C:/models/test.gguf")
        )
    }

    @Test
    fun testModelKeyRemote() {
        assertEquals(
            "remote:endpoint-1:gpt-4o",
            AppSettingsRepository.modelKeyRemote("endpoint-1", "gpt-4o")
        )
    }
}
