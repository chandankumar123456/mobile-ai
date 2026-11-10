package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.data.dot.DotRepositoryImpl
import com.mobileai.assistant.domain.dot.DotMemory
import com.mobileai.assistant.domain.dot.DotMemoryCategory
import com.mobileai.assistant.domain.dot.DotProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DotSessionCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testDotSelectionAndMemoryScoping() = runTest(testDispatcher) {
        val repo = DotRepositoryImpl(tempFolder.root)
        val coordinator = DotSessionCoordinator(repo, testScope)
        advanceUntilIdle()

        val dotA = DotProfile.STARTER_PROFILES.first()
        val dotB = DotProfile.STARTER_PROFILES[1]

        coordinator.selectDot(dotA)
        coordinator.saveMemory(
            DotMemory(
                dotId = dotA.id,
                key = "pref_lang",
                content = "User prefers Kotlin",
                category = DotMemoryCategory.USER_PREFERENCE
            )
        )
        advanceUntilIdle()

        val memoriesA = coordinator.getActiveDotMemories()
        assertEquals(1, memoriesA.size)
        assertEquals("User prefers Kotlin", memoriesA.first().content)

        // Switching to Dot B must isolate memory (zero leakage)
        coordinator.selectDot(dotB)
        advanceUntilIdle()

        val memoriesB = coordinator.getActiveDotMemories()
        assertTrue(memoriesB.isEmpty())

        // Active prompt must inject memories
        coordinator.selectDot(dotA)
        advanceUntilIdle()
        val promptA = coordinator.buildActiveSystemPrompt()
        assertTrue(promptA.contains("<dot_memory>"))
        assertTrue(promptA.contains("User prefers Kotlin"))
    }
}
