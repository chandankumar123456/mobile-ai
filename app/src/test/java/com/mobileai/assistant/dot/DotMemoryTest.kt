package com.mobileai.assistant.dot

import com.mobileai.assistant.data.dot.DotRepositoryImpl
import com.mobileai.assistant.domain.dot.DotMemory
import com.mobileai.assistant.domain.dot.DotMemoryCategory
import com.mobileai.assistant.domain.dot.DotModelPreference
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.dot.DotPromptBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DotMemoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: DotRepositoryImpl

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("dot_memory_test")
        repository = DotRepositoryImpl(rootDir)
    }

    @Test
    fun testMemoryCrudAndIsolation() = runBlocking {
        val dotA = "dot-a"
        val dotB = "dot-b"

        val memA1 = DotMemory(
            dotId = dotA,
            key = "pref-theme",
            content = "User prefers dark mode",
            category = DotMemoryCategory.USER_PREFERENCE,
            confidence = 1.0f
        )
        val memA2 = DotMemory(
            dotId = dotA,
            key = "proj-sdk",
            content = "Target Android SDK is 35",
            category = DotMemoryCategory.PROJECT_FACT,
            confidence = 0.9f
        )
        val memB1 = DotMemory(
            dotId = dotB,
            key = "note-work",
            content = "Working on NetworkClient refactor",
            category = DotMemoryCategory.SESSION_NOTE,
            confidence = 0.8f
        )

        repository.saveMemory(memA1)
        repository.saveMemory(memA2)
        repository.saveMemory(memB1)

        val memoriesA = repository.getMemories(dotA).first()
        val memoriesB = repository.getMemories(dotB).first()

        assertEquals(2, memoriesA.size)
        assertEquals(1, memoriesB.size)
        assertTrue(memoriesA.any { it.key == "pref-theme" })
        assertTrue(memoriesA.any { it.key == "proj-sdk" })
        assertFalse(memoriesA.any { it.key == "note-work" })
        assertEquals("Working on NetworkClient refactor", memoriesB.first().content)

        // Test update by key
        val updatedA1 = memA1.copy(content = "User prefers system theme", confidence = 0.95f)
        repository.saveMemory(updatedA1)

        val updatedListA = repository.getMemoriesList(dotA)
        assertEquals(2, updatedListA.size)
        val fetchedUpdatedA1 = updatedListA.find { it.key == "pref-theme" }
        assertEquals("User prefers system theme", fetchedUpdatedA1?.content)
        assertEquals(0.95f, fetchedUpdatedA1?.confidence ?: 0f, 0.001f)

        // Test delete memory
        val deleteRes = repository.deleteMemory(memA2.id)
        assertTrue(deleteRes.isSuccess)
        assertEquals(1, repository.getMemoriesList(dotA).size)

        // Test clear memories for Dot
        val clearRes = repository.clearMemories(dotA)
        assertTrue(clearRes.isSuccess)
        assertEquals(0, repository.getMemoriesList(dotA).size)
        assertEquals(1, repository.getMemoriesList(dotB).size) // B untouched
    }

    @Test
    fun testMemoryPersistenceOnDisk() = runBlocking {
        val memory = DotMemory(
            dotId = DotProfile.BUILTIN_CODER_ID,
            key = "code-style",
            content = "Prefer functional programming and immutability",
            category = DotMemoryCategory.CORE_INSTRUCTION,
            confidence = 1.0f
        )
        repository.saveMemory(memory)

        val memoryFile = File(rootDir, DotRepositoryImpl.FILE_MEMORIES)
        assertTrue(memoryFile.exists())
        assertTrue(memoryFile.length() > 0)

        // Reload in new repo
        val newRepo = DotRepositoryImpl(rootDir)
        val loaded = newRepo.getMemoriesList(DotProfile.BUILTIN_CODER_ID)
        assertEquals(1, loaded.size)
        assertEquals("code-style", loaded.first().key)
        assertEquals(DotMemoryCategory.CORE_INSTRUCTION, loaded.first().category)
    }

    @Test
    fun testDotPromptBuilderDynamicInjection() {
        val coder = DotProfile.STARTER_PROFILES.first { it.id == DotProfile.BUILTIN_CODER_ID }
            .copy(
                modelPreference = DotModelPreference(
                    systemPromptSuffix = "Respond with code snippets first."
                )
            )

        val memories = listOf(
            DotMemory(
                dotId = coder.id,
                key = "pref-lang",
                content = "User writes Kotlin exclusively",
                category = DotMemoryCategory.USER_PREFERENCE,
                confidence = 1.0f
            ),
            DotMemory(
                dotId = coder.id,
                key = "arch-pattern",
                content = "Use MVI or Clean Architecture",
                category = DotMemoryCategory.CORE_INSTRUCTION,
                confidence = 0.9f
            )
        )

        val prompt = DotPromptBuilder.buildSystemPrompt(coder, memories)

        // Validate base prompt is present
        assertTrue(prompt.contains(coder.systemPrompt))

        // Validate <dot_memory> block is formed
        assertTrue(prompt.contains("<dot_memory>"))
        assertTrue(prompt.contains("</dot_memory>"))
        assertTrue(prompt.contains("[USER_PREFERENCE] User writes Kotlin exclusively (confidence: 1.0)"))
        assertTrue(prompt.contains("[CORE_INSTRUCTION] Use MVI or Clean Architecture (confidence: 0.9)"))

        // Validate systemPromptSuffix is appended
        assertTrue(prompt.endsWith("Respond with code snippets first."))
    }

    @Test
    fun testDotPromptBuilderEmptyMemoriesOmitted() {
        val general = DotProfile.STARTER_PROFILES.first { it.id == DotProfile.BUILTIN_GENERAL_ID }
        val prompt = DotPromptBuilder.buildSystemPrompt(general, emptyList())

        assertFalse(prompt.contains("<dot_memory>"))
        assertEquals(general.systemPrompt.trim(), prompt.trim())
    }

    @Test
    fun testDotPromptBuilderBlankMemoriesFiltered() {
        val general = DotProfile.STARTER_PROFILES.first { it.id == DotProfile.BUILTIN_GENERAL_ID }
        val blanks = listOf(
            DotMemory(dotId = general.id, key = "k1", content = "   "),
            DotMemory(dotId = general.id, key = "k2", content = "")
        )
        val prompt = DotPromptBuilder.buildSystemPrompt(general, blanks)
        assertFalse(prompt.contains("<dot_memory>"))
    }
}
