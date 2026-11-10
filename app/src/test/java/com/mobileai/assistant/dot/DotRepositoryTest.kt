package com.mobileai.assistant.dot

import com.mobileai.assistant.data.dot.DotRepositoryImpl
import com.mobileai.assistant.domain.dot.DotAvatarPreset
import com.mobileai.assistant.domain.dot.DotMemory
import com.mobileai.assistant.domain.dot.DotMemoryCategory
import com.mobileai.assistant.domain.dot.DotProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DotRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: DotRepositoryImpl

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("dot_repo_test")
        repository = DotRepositoryImpl(rootDir)
    }

    @Test
    fun testStarterProfilesSeededOnInit() = runBlocking {
        val initialDots = repository.dots.first()
        assertEquals(4, initialDots.size)

        val ids = initialDots.map { it.id }.toSet()
        assertTrue(ids.contains(DotProfile.BUILTIN_GENERAL_ID))
        assertTrue(ids.contains(DotProfile.BUILTIN_CODER_ID))
        assertTrue(ids.contains(DotProfile.BUILTIN_RESEARCHER_ID))
        assertTrue(ids.contains(DotProfile.BUILTIN_WRITER_ID))

        val active = repository.activeDot.first()
        assertNotNull(active)
        assertEquals(DotProfile.BUILTIN_GENERAL_ID, active?.id)

        // Verify dots_v1.json exists on disk
        val dotsFile = File(rootDir, DotRepositoryImpl.FILE_DOTS)
        assertTrue(dotsFile.exists())
        assertTrue(dotsFile.length() > 0)
    }

    @Test
    fun testPersistenceAcrossInstances() = runBlocking {
        val customDot = DotProfile(
            id = "custom-agent-007",
            name = "Special Agent",
            role = "Investigator",
            description = "Handles deep investigations",
            avatarPreset = DotAvatarPreset.SEARCH,
            colorHex = "#8B5CF6",
            systemPrompt = "Investigate thoroughly."
        )

        val saveResult = repository.saveDot(customDot)
        assertTrue(saveResult.isSuccess)

        repository.selectActiveDot(customDot.id)
        assertEquals(customDot.id, repository.getActiveDot()?.id)

        // Spin up second repository instance pointing to the same directory
        val secondRepo = DotRepositoryImpl(rootDir)
        val loadedDots = secondRepo.dots.first()
        assertEquals(5, loadedDots.size)

        val foundCustom = secondRepo.getDotById(customDot.id)
        assertNotNull(foundCustom)
        assertEquals("Special Agent", foundCustom?.name)
        assertEquals(DotAvatarPreset.SEARCH, foundCustom?.avatarPreset)

        val secondActive = secondRepo.getActiveDot()
        assertEquals(customDot.id, secondActive?.id)
    }

    @Test
    fun testUpdateExistingDot() = runBlocking {
        val customDot = DotProfile(
            id = "updatable-dot",
            name = "Original Name",
            role = "Role 1",
            description = "Desc 1",
            systemPrompt = "Prompt 1"
        )
        repository.saveDot(customDot)

        val updated = customDot.copy(
            name = "Updated Name",
            systemPrompt = "Updated Prompt",
            colorHex = "#22C55E"
        )
        val updateResult = repository.saveDot(updated)
        assertTrue(updateResult.isSuccess)

        val fetched = repository.getDotById("updatable-dot")
        assertNotNull(fetched)
        assertEquals("Updated Name", fetched?.name)
        assertEquals("Updated Prompt", fetched?.systemPrompt)
        assertEquals("#22C55E", fetched?.colorHex)
    }

    @Test
    fun testBuiltInDotsCannotBeDeleted() = runBlocking {
        val deleteBuiltIn = repository.deleteDot(DotProfile.BUILTIN_GENERAL_ID)
        assertFalse(deleteBuiltIn.isSuccess)
        assertTrue(deleteBuiltIn.exceptionOrNull() is IllegalStateException)

        val stillThere = repository.getDotById(DotProfile.BUILTIN_GENERAL_ID)
        assertNotNull(stillThere)
    }

    @Test
    fun testDeleteCustomDotAndCascadeMemories() = runBlocking {
        val customDot = DotProfile(
            id = "deletable-dot",
            name = "Temporary Dot",
            role = "Temp",
            description = "Temp",
            systemPrompt = "Temp"
        )
        repository.saveDot(customDot)
        repository.selectActiveDot(customDot.id)

        // Add memory tied to customDot
        val memory = DotMemory(
            dotId = customDot.id,
            key = "temp-fact",
            content = "This fact will be deleted with dot"
        )
        repository.saveMemory(memory)
        assertEquals(1, repository.getMemoriesList(customDot.id).size)

        // Delete custom dot
        val deleteResult = repository.deleteDot(customDot.id)
        assertTrue(deleteResult.isSuccess)

        assertNull(repository.getDotById(customDot.id))
        // Cascaded memory deletion check
        assertEquals(0, repository.getMemoriesList(customDot.id).size)

        // Active dot fell back to builtin-general
        val currentActive = repository.getActiveDot()
        assertNotNull(currentActive)
        assertEquals(DotProfile.BUILTIN_GENERAL_ID, currentActive?.id)
    }

    @Test
    fun testMissingBuiltInsRestoredOnStartup() = runBlocking {
        // Even if dots_v1.json had only 1 custom dot, startup restores all 4 starter profiles
        val dotsFile = File(rootDir, DotRepositoryImpl.FILE_DOTS)
        dotsFile.writeText("""
            {
                "version": 1,
                "activeDotId": "only-custom",
                "dots": [
                    {
                        "id": "only-custom",
                        "name": "Solo",
                        "role": "Single",
                        "description": "Sole survivor",
                        "systemPrompt": "Hi"
                    }
                ]
            }
        """.trimIndent())

        val repo = DotRepositoryImpl(rootDir)
        val dots = repo.dots.first()
        // 1 custom + 4 starters = 5
        assertEquals(5, dots.size)
        assertTrue(dots.any { it.id == "only-custom" })
        assertTrue(dots.any { it.id == DotProfile.BUILTIN_GENERAL_ID })
    }
}
