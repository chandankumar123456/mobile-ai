package com.mobileai.assistant.dot

import com.mobileai.assistant.data.artifact.ArtifactRepositoryImpl
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.domain.artifact.ArtifactType
import com.mobileai.assistant.domain.artifact.WorkspaceArtifact
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
import java.util.UUID

/**
 * Empirical stress test suite targeting Workspace Artifact versioning,
 * disk recovery, concurrency, and conversation session isolation.
 */
class ArtifactAndSessionStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var artifactDir: File
    private lateinit var chatDir: File
    private lateinit var artifactRepo: ArtifactRepositoryImpl
    private lateinit var chatRepo: ChatRepositoryImpl

    @Before
    fun setUp() {
        artifactDir = tempFolder.newFolder("stress_artifacts_${UUID.randomUUID()}")
        chatDir = tempFolder.newFolder("stress_chat_${UUID.randomUUID()}")
        artifactRepo = ArtifactRepositoryImpl(artifactDir)
        chatRepo = ChatRepositoryImpl(chatDir)
    }

    @Test
    fun testRapidSequentialUpdatesMonotonicVersioning() = runBlocking {
        val artifact = WorkspaceArtifact(
            id = "art-rapid-seq",
            dotId = DotProfile.BUILTIN_CODER_ID,
            conversationId = "conv-stress-01",
            title = "SequentialEngine.kt",
            type = ArtifactType.CODE,
            language = "kotlin",
            content = "fun init() = 0",
            version = 1
        )
        val saveResult = artifactRepo.saveArtifact(artifact)
        assertTrue("Initial save must succeed", saveResult.isSuccess)

        // Perform 50 rapid sequential updates
        val totalUpdates = 50
        for (i in 1..totalUpdates) {
            val content = "fun update() = $i"
            val updateResult = artifactRepo.updateArtifact(
                id = "art-rapid-seq",
                content = content,
                changeSummary = "Iteration $i"
            )
            assertTrue("Update $i must succeed", updateResult.isSuccess)
            val updated = updateResult.getOrNull()
            assertNotNull(updated)
            assertEquals(i + 1, updated?.version)
            assertEquals(content, updated?.content)
        }

        // Verify in-memory state
        val finalArtifact = artifactRepo.getArtifactById("art-rapid-seq")
        assertNotNull(finalArtifact)
        assertEquals(51, finalArtifact?.version)
        assertEquals("fun update() = 50", finalArtifact?.content)

        val versions = artifactRepo.getArtifactVersionsList("art-rapid-seq")
        assertEquals(51, versions.size)
        for (v in 1..51) {
            assertEquals("Version index must match monotonic sequence", v, versions[v - 1].version)
            if (v == 1) {
                assertEquals("fun init() = 0", versions[0].content)
            } else {
                assertEquals("fun update() = ${v - 1}", versions[v - 1].content)
            }
        }

        // Verify disk persistence after 50 rapid updates
        val reloadedRepo = ArtifactRepositoryImpl(artifactDir)
        val diskArtifact = reloadedRepo.getArtifactById("art-rapid-seq")
        assertNotNull("Artifact must persist on disk", diskArtifact)
        assertEquals(51, diskArtifact?.version)
        assertEquals("fun update() = 50", diskArtifact?.content)

        val diskVersions = reloadedRepo.getArtifactVersionsList("art-rapid-seq")
        assertEquals(51, diskVersions.size)
        for (v in 1..51) {
            assertEquals(v, diskVersions[v - 1].version)
        }
    }

    @Test
    fun testConcurrentArtifactUpdatesThreadSafety() = runBlocking {
        val artifact = WorkspaceArtifact(
            id = "art-concurrent",
            dotId = "builtin-coder",
            conversationId = "conv-concurrent",
            title = "ConcurrencyStress.kt",
            type = ArtifactType.CODE,
            content = "initial",
            version = 1
        )
        artifactRepo.saveArtifact(artifact)

        val coroutineCount = 10
        val updatesPerCoroutine = 5
        val jobs = List(coroutineCount) { coroutineIdx ->
            launch(Dispatchers.Default) {
                for (step in 1..updatesPerCoroutine) {
                    val summary = "Worker $coroutineIdx step $step"
                    val content = "content from $coroutineIdx at $step"
                    val res = artifactRepo.updateArtifact("art-concurrent", content, summary)
                    assertTrue(res.isSuccess)
                }
            }
        }
        jobs.joinAll()

        val expectedTotalVersions = 1 + (coroutineCount * updatesPerCoroutine)
        val finalArt = artifactRepo.getArtifactById("art-concurrent")
        assertNotNull(finalArt)
        assertEquals(expectedTotalVersions, finalArt?.version)

        val versions = artifactRepo.getArtifactVersionsList("art-concurrent")
        assertEquals(expectedTotalVersions, versions.size)

        // Check strictly unique, monotonic versions from 1 to 51
        val versionNumbers = versions.map { it.version }
        assertEquals(expectedTotalVersions, versionNumbers.distinct().size)
        assertEquals((1..expectedTotalVersions).toList(), versionNumbers.sorted())

        // Verify reloaded from disk matches
        val reloaded = ArtifactRepositoryImpl(artifactDir)
        val reloadedArt = reloaded.getArtifactById("art-concurrent")
        assertEquals(expectedTotalVersions, reloadedArt?.version)
        assertEquals(expectedTotalVersions, reloaded.getArtifactVersionsList("art-concurrent").size)
    }

    @Test
    fun testConcurrentDistinctArtifactsAcrossDots() = runBlocking {
        val dots = listOf("builtin-general", "builtin-coder", "builtin-researcher", "builtin-writer")
        val artifactsPerDot = 10

        val jobs = dots.flatMap { dotId ->
            (1..artifactsPerDot).map { idx ->
                launch(Dispatchers.Default) {
                    val art = WorkspaceArtifact(
                        id = "art-$dotId-$idx",
                        dotId = dotId,
                        conversationId = "conv-$dotId-${idx % 3}",
                        title = "Doc $dotId $idx",
                        type = ArtifactType.MARKDOWN,
                        content = "Initial content for $dotId $idx"
                    )
                    val saveRes = artifactRepo.saveArtifact(art)
                    assertTrue(saveRes.isSuccess)

                    // Immediate update
                    val updateRes = artifactRepo.updateArtifact(art.id, "Updated $dotId $idx", "rev 2")
                    assertTrue(updateRes.isSuccess)
                }
            }
        }
        jobs.joinAll()

        // Verify total artifacts
        val all = artifactRepo.artifacts.first()
        assertEquals(dots.size * artifactsPerDot, all.size)

        // Verify dot isolation
        for (dotId in dots) {
            val dotArtifacts = artifactRepo.getArtifactsForDot(dotId).first()
            assertEquals(artifactsPerDot, dotArtifacts.size)
            assertTrue(dotArtifacts.all { it.dotId == dotId })
            assertTrue(dotArtifacts.all { it.version == 2 })
        }
    }

    @Test
    fun testLargeDocumentArtifactPersistenceAndIntegrity() = runBlocking {
        // Build 500KB text payload
        val segment = "The quick brown fox jumps over the lazy dog. 1234567890!@#\$%^&*() \n"
        val builder = java.lang.StringBuilder()
        while (builder.length < 500_000) {
            builder.append(segment)
        }
        val largeContentV1 = builder.toString()

        val artifact = WorkspaceArtifact(
            id = "art-large-doc",
            dotId = "builtin-writer",
            conversationId = "conv-large-01",
            title = "EnormousManuscript.md",
            type = ArtifactType.MARKDOWN,
            language = "markdown",
            content = largeContentV1,
            version = 1
        )
        val saveRes = artifactRepo.saveArtifact(artifact)
        assertTrue("Large artifact save must succeed", saveRes.isSuccess)

        // Now update with 1MB payload containing Unicode, emojis, and special chars
        val unicodeSegment = "🤖🚀⚡ Testing Unicode: 日本語, العربية, 한국어, <html><script>alert('x')</script></html>\n"
        val builderV2 = java.lang.StringBuilder()
        while (builderV2.length < 1_000_000) {
            builderV2.append(unicodeSegment)
        }
        val largeContentV2 = builderV2.toString()

        val updateRes = artifactRepo.updateArtifact("art-large-doc", largeContentV2, "Updated to 1MB Unicode")
        assertTrue("Large artifact update must succeed", updateRes.isSuccess)

        // Reload from disk in a fresh repository instance
        val reloadedRepo = ArtifactRepositoryImpl(artifactDir)
        val reloaded = reloadedRepo.getArtifactById("art-large-doc")
        assertNotNull(reloaded)
        assertEquals(2, reloaded?.version)
        assertEquals(largeContentV2.length, reloaded?.content?.length)
        assertEquals(largeContentV2, reloaded?.content)

        val versions = reloadedRepo.getArtifactVersionsList("art-large-doc")
        assertEquals(2, versions.size)
        assertEquals(1, versions[0].version)
        assertEquals(largeContentV1.length, versions[0].content.length)
        assertEquals(2, versions[1].version)
        assertEquals(largeContentV2.length, versions[1].content.length)
    }

    @Test
    fun testDiskRecoveryCorruptedAndMissingFiles() = runBlocking {
        // 1. Missing file: repo initializes with empty list without crashing
        val freshDir = tempFolder.newFolder("missing_file_test")
        val freshRepo = ArtifactRepositoryImpl(freshDir)
        assertEquals(0, freshRepo.artifacts.first().size)
        assertNull(freshRepo.getArtifactById("non-existent"))

        // 2. 0-byte file: repo initializes safely
        val zeroByteDir = tempFolder.newFolder("zero_byte_test")
        val zeroByteFile = File(zeroByteDir, ArtifactRepositoryImpl.FILE_ARTIFACTS)
        zeroByteFile.writeText("")
        val zeroByteRepo = ArtifactRepositoryImpl(zeroByteDir)
        assertEquals(0, zeroByteRepo.artifacts.first().size)

        // 3. Corrupted malformed JSON: repo recovers gracefully without throwing exception
        val corruptDir = tempFolder.newFolder("corrupt_json_test")
        val corruptFile = File(corruptDir, ArtifactRepositoryImpl.FILE_ARTIFACTS)
        corruptFile.writeText("{ \"version\": 1, \"artifacts\": [ { \"id\": \"broken\"")
        val corruptRepo = ArtifactRepositoryImpl(corruptDir)
        assertEquals(0, corruptRepo.artifacts.first().size)

        // 4. Save after corruption overwrites with valid data cleanly
        val recoverArt = WorkspaceArtifact(
            id = "art-recovered",
            dotId = "builtin-coder",
            conversationId = "conv-rec",
            title = "Recovered.kt",
            type = ArtifactType.CODE,
            content = "fun recovered() = true"
        )
        val saveRes = corruptRepo.saveArtifact(recoverArt)
        assertTrue(saveRes.isSuccess)
        assertEquals(1, corruptRepo.artifacts.first().size)

        // 5. Verify file is now valid JSON
        val recheckRepo = ArtifactRepositoryImpl(corruptDir)
        assertEquals(1, recheckRepo.artifacts.first().size)
        assertEquals("art-recovered", recheckRepo.artifacts.first().first().id)
    }

    @Test
    fun testConversationSessionIsolationUnderHighVolume() = runBlocking {
        val totalSessionsPerDot = 20
        val dots = listOf(
            DotProfile.BUILTIN_CODER_ID,
            DotProfile.BUILTIN_RESEARCHER_ID,
            DotProfile.BUILTIN_WRITER_ID
        )

        // Account for initial default "New Chat" session created on fresh repository startup (dotId = null)
        val initialUnassignedCount = chatRepo.getConversationsByDot(null).first().size // 1

        // Create sessions for specific dots
        for (dotId in dots) {
            for (i in 1..totalSessionsPerDot) {
                chatRepo.createNewSession(title = "$dotId Session $i", dotId = dotId)
            }
        }

        // Create 20 unassigned sessions (dotId = null)
        for (i in 1..20) {
            chatRepo.createNewSession(title = "Unassigned Session $i", dotId = null)
        }

        // Create 10 sessions explicitly for general dot
        for (i in 1..10) {
            chatRepo.createNewSession(title = "General Dot Session $i", dotId = DotProfile.BUILTIN_GENERAL_ID)
        }

        // Create 5 sessions with blank dotId ("   ")
        for (i in 1..5) {
            chatRepo.createNewSession(title = "Blank Dot Session $i", dotId = "   ")
        }

        // Verify Coder sessions: strictly 20 sessions, all dotId == builtin-coder
        val coderSessions = chatRepo.getConversationsByDot(DotProfile.BUILTIN_CODER_ID).first()
        assertEquals(20, coderSessions.size)
        assertTrue(coderSessions.all { it.dotId == DotProfile.BUILTIN_CODER_ID })

        // Verify Researcher sessions: strictly 20 sessions
        val researcherSessions = chatRepo.getConversationsByDot(DotProfile.BUILTIN_RESEARCHER_ID).first()
        assertEquals(20, researcherSessions.size)
        assertTrue(researcherSessions.all { it.dotId == DotProfile.BUILTIN_RESEARCHER_ID })

        // Verify Writer sessions: strictly 20 sessions
        val writerSessions = chatRepo.getConversationsByDot(DotProfile.BUILTIN_WRITER_ID).first()
        assertEquals(20, writerSessions.size)
        assertTrue(writerSessions.all { it.dotId == DotProfile.BUILTIN_WRITER_ID })

        // Verify Unassigned sessions query (dotId = null): 20 (null) + 5 (blank) + initialUnassignedCount = 26
        val unassignedSessions = chatRepo.getConversationsByDot(null).first()
        assertEquals(25 + initialUnassignedCount, unassignedSessions.size)
        assertTrue(unassignedSessions.all { it.dotId == null || it.dotId?.isBlank() == true })

        // Verify General dot sessions query: 10 (general) + 20 (null) + 5 (blank) + initialUnassignedCount = 36
        val generalSessions = chatRepo.getConversationsByDot(DotProfile.BUILTIN_GENERAL_ID).first()
        assertEquals(35 + initialUnassignedCount, generalSessions.size)

        // Verify across restarts
        val reloadedChat = ChatRepositoryImpl(chatDir)
        assertEquals(20, reloadedChat.getConversationsByDot(DotProfile.BUILTIN_CODER_ID).first().size)
        assertEquals(35 + initialUnassignedCount, reloadedChat.getConversationsByDot(DotProfile.BUILTIN_GENERAL_ID).first().size)
    }

    @Test
    fun testConversationDeletionAndArtifactRelationship() = runBlocking {
        val session1 = chatRepo.createNewSession("Session To Delete", dotId = DotProfile.BUILTIN_CODER_ID)
        chatRepo.addMessage(ChatMessage(content = "Message in deleted session", isUser = true))

        val session2 = chatRepo.createNewSession("Session To Keep", dotId = DotProfile.BUILTIN_CODER_ID)
        chatRepo.addMessage(ChatMessage(content = "Message in kept session", isUser = true))

        // Create artifact tagged with session1
        val art1 = WorkspaceArtifact(
            id = "art-session1",
            dotId = DotProfile.BUILTIN_CODER_ID,
            conversationId = session1.id,
            title = "FileFromSession1.kt",
            type = ArtifactType.CODE,
            content = "code from session 1"
        )
        artifactRepo.saveArtifact(art1)

        // Create artifact tagged with session2
        val art2 = WorkspaceArtifact(
            id = "art-session2",
            dotId = DotProfile.BUILTIN_CODER_ID,
            conversationId = session2.id,
            title = "FileFromSession2.kt",
            type = ArtifactType.CODE,
            content = "code from session 2"
        )
        artifactRepo.saveArtifact(art2)

        // Delete session 1
        chatRepo.deleteSession(session1.id)

        // Verify session 1 is deleted from chatRepo
        assertNull(chatRepo.getSession(session1.id))
        val coderConversations = chatRepo.getConversationsByDot(DotProfile.BUILTIN_CODER_ID).first()
        assertEquals(1, coderConversations.size)
        assertEquals(session2.id, coderConversations.first().id)

        // Verify artifacts: art1 still exists in artifact repo with historical reference to session1
        val fetchedArt1 = artifactRepo.getArtifactById("art-session1")
        assertNotNull("Artifact repository preserves workspace documents even when session is deleted", fetchedArt1)
        assertEquals(session1.id, fetchedArt1?.conversationId)

        // Artifact querying by conversation returns it if asked
        val session1Artifacts = artifactRepo.getArtifactsForConversation(session1.id).first()
        assertEquals(1, session1Artifacts.size)

        // Deleting artifact explicitly removes it and its version history
        artifactRepo.deleteArtifact("art-session1")
        assertNull(artifactRepo.getArtifactById("art-session1"))
        assertEquals(0, artifactRepo.getArtifactVersionsList("art-session1").size)

        // art2 remains intact
        assertNotNull(artifactRepo.getArtifactById("art-session2"))
    }

    @Test
    fun testSpecialCharactersAndCodeBlockIntegrity() = runBlocking {
        val complexCode = """
            package com.example.stress
            
            // Special characters: < > & " ' \ / \n \t \r \u0000 \b \f
            fun sanitize(input: String): String {
                val xml = "<tag attr=\"value\">Hello & Welcome</tag>"
                val regex = Regex("^[a-zA-Z0-9_+\$]+$")
                val json = "{\"key\": \"value with \\\"quotes\\\" and \\nnewlines\"}"
                return input + xml + json
            }
        """.trimIndent()

        val artifact = WorkspaceArtifact(
            id = "art-special-chars",
            dotId = DotProfile.BUILTIN_CODER_ID,
            conversationId = "conv-special",
            title = "Special<Chars>&\"Test\".kt",
            type = ArtifactType.CODE,
            language = "kotlin",
            content = complexCode
        )
        val saveRes = artifactRepo.saveArtifact(artifact)
        assertTrue(saveRes.isSuccess)

        val reloaded = ArtifactRepositoryImpl(artifactDir)
        val loaded = reloaded.getArtifactById("art-special-chars")
        assertNotNull(loaded)
        assertEquals(complexCode, loaded?.content)
        assertEquals("Special<Chars>&\"Test\".kt", loaded?.title)
    }

    @Test
    fun testSaveArtifactIdempotencyAndExistingArtifactHandling() = runBlocking {
        val initial = WorkspaceArtifact(
            id = "art-idempotent",
            dotId = DotProfile.BUILTIN_RESEARCHER_ID,
            conversationId = "conv-idem",
            title = "Initial Title",
            type = ArtifactType.TEXT,
            content = "initial content",
            version = 1
        )
        artifactRepo.saveArtifact(initial)
        assertEquals(1, artifactRepo.getArtifactVersionsList("art-idempotent").size)

        // Calling saveArtifact again on existing ID updates the artifact entry but does not create redundant versions
        val updatedMetadata = initial.copy(title = "Updated Title")
        val saveAgainRes = artifactRepo.saveArtifact(updatedMetadata)
        assertTrue(saveAgainRes.isSuccess)

        val fetched = artifactRepo.getArtifactById("art-idempotent")
        assertEquals("Updated Title", fetched?.title)
        assertEquals(1, artifactRepo.getArtifactVersionsList("art-idempotent").size)

        // updateArtifact creates version 2
        val updateRes = artifactRepo.updateArtifact("art-idempotent", "version 2 content", "revised")
        assertTrue(updateRes.isSuccess)
        assertEquals(2, artifactRepo.getArtifactVersionsList("art-idempotent").size)
        assertEquals(2, artifactRepo.getArtifactById("art-idempotent")?.version)
    }
}
