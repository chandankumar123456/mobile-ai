package com.mobileai.assistant.data.repository

import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.AttachmentKind
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.RuntimeMetrics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ChatRepositoryV2Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var attachmentStore: AttachmentStore

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("chat_repo_v2_test")
        attachmentStore = AttachmentStore(rootDir)
    }

    @Test
    fun testSchemaV2PersistenceFileEnvelope() {
        val repo = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        repo.addMessage(ChatMessage(content = "Hello V2", isUser = true))
        repo.checkpoint()

        val v2File = File(rootDir, "conversations_v2.json")
        assertTrue(v2File.exists())

        val json = Json { ignoreUnknownKeys = true }
        val envelope = json.decodeFromString<ConversationStoreEnvelope>(v2File.readText())
        assertEquals(2, envelope.version)
        assertEquals(repo.activeConversationId.value, envelope.activeConversationId)
        assertEquals(1, envelope.conversations.size)
        assertEquals("Hello V2", envelope.conversations[0].messages[0].content)
    }

    @Test
    fun testActiveConversationIdRestoredAcrossInstances() {
        val repo1 = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        val session1Id = repo1.activeConversationId.value
        repo1.addMessage(ChatMessage(content = "Session 1 message", isUser = true))

        val session2 = repo1.createNewSession("Second Session")
        repo1.addMessage(ChatMessage(content = "Session 2 message", isUser = true))
        assertEquals(session2.id, repo1.activeConversationId.value)
        repo1.checkpoint()

        // Create new repo instance on the same directory
        val repo2 = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        assertEquals(session2.id, repo2.activeConversationId.value)
        assertEquals(1, repo2.messages.value.size)
        assertEquals("Session 2 message", repo2.messages.value[0].content)

        // Select session 1 and check restore again
        repo2.selectSession(session1Id)
        repo2.checkpoint()

        val repo3 = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        assertEquals(session1Id, repo3.activeConversationId.value)
        assertEquals("Session 1 message", repo3.messages.value[0].content)
    }

    @Test
    fun testSearchConversationsByTitleAndSnippet() {
        val repo = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        val session1 = repo.createNewSession("Android Architecture")
        repo.addMessage(ChatMessage(content = "Discussing Clean Architecture with MVVM", isUser = true))

        val session2 = repo.createNewSession("Kotlin Coroutines")
        repo.addMessage(ChatMessage(content = "How does StateFlow differ from SharedFlow?", isUser = true))

        // Search by title
        val titleHits = repo.search("Architecture")
        assertEquals(1, titleHits.size)
        assertEquals(session1.id, titleHits[0].sessionId)
        assertEquals("Android Architecture", titleHits[0].title)

        // Search by message content
        val contentHits = repo.search("StateFlow")
        assertEquals(1, contentHits.size)
        assertEquals(session2.id, contentHits[0].sessionId)
        assertNotNull(contentHits[0].messageId)
        assertTrue(contentHits[0].snippet.contains("StateFlow"))

        // Empty search returns all sessions
        val allHits = repo.search("")
        assertEquals(repo.conversations.value.size, allHits.size)
    }

    @Test
    fun testMessageMutationsAndReplaceMessages() {
        val repo = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        val msg1 = ChatMessage(content = "Original Content", isUser = true)
        repo.addMessage(msg1)

        // Update message content
        repo.updateMessage(msg1.id, "Updated Content")
        assertEquals("Updated Content", repo.messages.value[0].content)

        // Set incomplete with reason
        repo.setMessageIncomplete(msg1.id, true, "cancelled_by_user")
        assertTrue(repo.messages.value[0].isIncomplete)
        assertEquals("cancelled_by_user", repo.messages.value[0].stopReason)

        // Set metrics
        val metrics = RuntimeMetrics(
            completionTokens = 15,
            tokensPerSecond = 20.0
        )
        repo.setMessageMetrics(msg1.id, metrics)
        assertEquals(15, repo.messages.value[0].metrics?.completionTokens)
        assertEquals(20.0, repo.messages.value[0].metrics?.tokensPerSecond)

        // Replace messages
        val replacement = listOf(
            ChatMessage(content = "Brand new message", isUser = false)
        )
        repo.replaceMessages(replacement)
        assertEquals(1, repo.messages.value.size)
        assertEquals("Brand new message", repo.messages.value[0].content)
    }

    @Test
    fun testConversationMetadataUpdates() {
        val repo = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        val activeId = repo.activeConversationId.value

        // Target
        repo.setConversationTarget(activeId, "remote:openai/gpt-4o")
        assertEquals("remote:openai/gpt-4o", repo.getSession(activeId)?.targetId)
        assertEquals("remote:openai/gpt-4o", repo.getConversation(activeId)?.targetId)

        // Language
        repo.setConversationLanguage(activeId, ResponseLanguage.SPANISH)
        assertEquals(ResponseLanguage.SPANISH, repo.getSession(activeId)?.responseLanguage)

        // System Prompt
        repo.setConversationSystemPrompt(activeId, "Eres un asistente amigable.")
        assertEquals("Eres un asistente amigable.", repo.getSession(activeId)?.systemPromptOverride)

        // Params
        val customParams = GenerationParams(temperature = 0.5, maxTokens = 512)
        repo.setConversationParams(activeId, customParams)
        assertEquals(0.5, repo.getSession(activeId)?.paramOverrides?.temperature ?: 0.0, 0.001)
        assertEquals(512, repo.getSession(activeId)?.paramOverrides?.maxTokens)

        // Pinned
        repo.setConversationPinned(activeId, true)
        assertTrue(repo.getSession(activeId)?.pinned == true)
    }

    @Test
    fun testDeleteMessageDeletesAttachments() {
        val repo = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        val stream = ByteArrayInputStream("attachment payload".toByteArray(Charsets.UTF_8))
        val att = attachmentStore.saveAttachment(
            name = "doc.txt",
            mimeType = "text/plain",
            sizeBytes = 18,
            inputStream = stream
        )
        val file = attachmentStore.getAttachmentFile(att)
        assertTrue(file.exists())

        val msg = ChatMessage(
            content = "Here is my doc",
            isUser = true,
            attachments = listOf(att)
        )
        repo.addMessage(msg)
        assertEquals(1, repo.messages.value.size)

        repo.deleteMessage(msg.id)
        assertEquals(0, repo.messages.value.size)
        assertFalse(file.exists())
    }

    @Test
    fun testMigrateV1ToV2() {
        val legacySessions = listOf(
            ConversationSession(
                id = "legacy-session-1",
                title = "Legacy Chat",
                createdAt = 1000L,
                updatedAt = 2000L,
                messages = listOf(
                    ChatMessage(content = "Legacy User Question", isUser = true),
                    ChatMessage(content = "Legacy Assistant Reply", isUser = false)
                )
            )
        )

        val v1File = File(rootDir, "conversations_v1.json")
        val json = Json { prettyPrint = true; encodeDefaults = true }
        v1File.writeText(json.encodeToString(legacySessions))

        // Initializing repo should load v1 and write v2
        val repo = ChatRepositoryImpl(rootDir = rootDir, attachmentStore = attachmentStore)
        assertEquals(1, repo.conversations.value.size)
        assertEquals("legacy-session-1", repo.activeConversationId.value)
        assertEquals(2, repo.messages.value.size)
        assertEquals("Legacy User Question", repo.messages.value[0].content)

        val v2File = File(rootDir, "conversations_v2.json")
        assertTrue(v2File.exists())
    }
}
