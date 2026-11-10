package com.mobileai.assistant.dot

import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DotConversationIsolationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var chatRepository: ChatRepositoryImpl

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("dot_conv_test")
        chatRepository = ChatRepositoryImpl(rootDir)
    }

    @Test
    fun testConversationSessionDotIdBackwardCompatibility() {
        // Legacy JSON without dotId
        val legacyJson = """
            {
                "id": "legacy-session-1",
                "title": "Old Chat",
                "createdAt": 1700000000000,
                "updatedAt": 1700000000000,
                "messages": [],
                "pinned": false
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ConversationSession>(legacyJson)
        assertEquals("legacy-session-1", parsed.id)
        assertNull(parsed.dotId)

        // Reserialize includes dotId or preserves null
        val serialized = json.encodeToString(parsed)
        assertTrue(serialized.contains("legacy-session-1"))
    }

    @Test
    fun testConversationCreationWithDotIdAndFiltering() = runBlocking {
        val coderSession = chatRepository.createNewSession(
            title = "Refactor Network Layer",
            dotId = DotProfile.BUILTIN_CODER_ID
        )
        chatRepository.addMessage(ChatMessage(content = "How do I refactor OkHttp?", isUser = true))

        val writerSession = chatRepository.createNewSession(
            title = "Release Notes v2.0",
            dotId = DotProfile.BUILTIN_WRITER_ID
        )
        chatRepository.addMessage(ChatMessage(content = "Draft v2.0 release notes", isUser = true))

        val generalSession = chatRepository.createNewSession(
            title = "General Planning",
            dotId = DotProfile.BUILTIN_GENERAL_ID
        )
        chatRepository.addMessage(ChatMessage(content = "What should I eat?", isUser = true))

        // Query by Coder Dot
        val coderList = chatRepository.getConversationsByDot(DotProfile.BUILTIN_CODER_ID).first()
        assertEquals(1, coderList.size)
        assertEquals(coderSession.id, coderList.first().id)
        assertEquals(DotProfile.BUILTIN_CODER_ID, coderList.first().dotId)

        // Query by Writer Dot
        val writerList = chatRepository.getConversationsByDot(DotProfile.BUILTIN_WRITER_ID).first()
        assertEquals(1, writerList.size)
        assertEquals(writerSession.id, writerList.first().id)
        assertEquals(DotProfile.BUILTIN_WRITER_ID, writerList.first().dotId)

        // Query by General Dot
        val generalList = chatRepository.getConversationsByDot(DotProfile.BUILTIN_GENERAL_ID).first()
        assertTrue(generalList.any { it.id == generalSession.id })
    }

    @Test
    fun testLegacySessionsIncludedInGeneralDot() = runBlocking {
        // Create an unassigned session (dotId = null)
        val unassigned = chatRepository.createNewSession(title = "Unassigned Session", dotId = null)
        chatRepository.addMessage(ChatMessage(content = "Hello world", isUser = true))

        val generalList = chatRepository.getConversationsByDot(DotProfile.BUILTIN_GENERAL_ID).first()
        assertTrue(generalList.any { it.id == unassigned.id })

        val unassignedList = chatRepository.getConversationsByDot(null).first()
        assertTrue(unassignedList.any { it.id == unassigned.id })
    }

    @Test
    fun testReassignConversationToAnotherDot() = runBlocking {
        val session = chatRepository.createNewSession(title = "Multi-purpose Session", dotId = DotProfile.BUILTIN_GENERAL_ID)

        // Initially in general list
        val inGeneralBefore = chatRepository.getConversationsByDot(DotProfile.BUILTIN_GENERAL_ID).first()
        assertTrue(inGeneralBefore.any { it.id == session.id })

        // Reassign to researcher
        chatRepository.setConversationDot(session.id, DotProfile.BUILTIN_RESEARCHER_ID)

        val inResearcher = chatRepository.getConversationsByDot(DotProfile.BUILTIN_RESEARCHER_ID).first()
        assertTrue(inResearcher.any { it.id == session.id })

        val fetched = chatRepository.getSession(session.id)
        assertNotNull(fetched)
        assertEquals(DotProfile.BUILTIN_RESEARCHER_ID, fetched?.dotId)
    }

    @Test
    fun testSessionPersistenceWithDotIdAcrossRestarts() = runBlocking {
        val session = chatRepository.createNewSession(
            title = "Persistent Dot Chat",
            dotId = DotProfile.BUILTIN_CODER_ID
        )
        chatRepository.addMessage(ChatMessage(content = "Message 1", isUser = true))

        // Reload repository from same disk directory
        val reloadedRepo = ChatRepositoryImpl(rootDir)
        val loadedSession = reloadedRepo.getSession(session.id)
        assertNotNull(loadedSession)
        assertEquals(DotProfile.BUILTIN_CODER_ID, loadedSession?.dotId)

        val coderSessions = reloadedRepo.getConversationsByDot(DotProfile.BUILTIN_CODER_ID).first()
        assertTrue(coderSessions.any { it.id == session.id })
    }
}
