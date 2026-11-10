package com.mobileai.assistant.domain

import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.domain.model.ChatMessage
import com.mobileai.assistant.domain.model.ConversationSession
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.model.ThinkingEffort
import com.mobileai.assistant.presentation.chat.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    @Test
    fun testAddAndAppendMessages() {
        val repo = ChatRepositoryImpl()
        assertEquals(0, repo.messages.value.size)

        repo.addMessage(ChatMessage(content = "Hello", isUser = true))
        assertEquals(1, repo.messages.value.size)
        assertTrue(repo.messages.value[0].isUser)

        repo.addMessage(ChatMessage(content = "Hi", isUser = false, isStreaming = true))
        assertEquals(2, repo.messages.value.size)

        repo.appendToLastMessage(" there!")
        assertEquals("Hi there!", repo.messages.value[1].content)
        assertTrue(repo.messages.value[1].isStreaming)

        repo.updateLastMessage("Hi there!", isStreaming = false)
        assertFalse(repo.messages.value[1].isStreaming)

        repo.clearMessages()
        assertEquals(0, repo.messages.value.size)
    }

    @Test
    fun testReasoningAndThinkingUpdates() {
        val repo = ChatRepositoryImpl()
        repo.addMessage(ChatMessage(content = "", isUser = false, isStreaming = true))

        repo.appendReasoningToLastMessage("Analyzing user query step 1...")
        assertEquals("Analyzing user query step 1...", repo.messages.value[0].reasoning)
        assertTrue(repo.messages.value[0].isThinking)

        repo.appendReasoningToLastMessage("\nStep 2: verifying facts.")
        assertEquals("Analyzing user query step 1...\nStep 2: verifying facts.", repo.messages.value[0].reasoning)

        repo.appendToLastMessage("Here is the answer.")
        assertEquals("Here is the answer.", repo.messages.value[0].content)
        assertFalse(repo.messages.value[0].isThinking) // When content arrives, thinking flag is reset

        repo.updateLastMessage(
            content = "Here is the answer.",
            reasoning = repo.messages.value[0].reasoning,
            isStreaming = false,
            isThinking = false
        )
        assertFalse(repo.messages.value[0].isStreaming)
    }

    @Test
    fun testMultiConversationSessions() {
        val repo = ChatRepositoryImpl()
        assertEquals(1, repo.conversations.value.size)
        val initialId = repo.activeConversationId.value

        // First prompt auto-titles conversation
        repo.addMessage(ChatMessage(content = "How does photosynthesis work in plants?", isUser = true))
        repo.addMessage(ChatMessage(content = "Photosynthesis is the process...", isUser = false))

        assertTrue(repo.getActiveSessionTitle().contains("photosynthesis", ignoreCase = true))

        // Create new session
        val session2 = repo.createNewSession("Quantum Computing")
        assertEquals(2, repo.conversations.value.size)
        assertEquals(session2.id, repo.activeConversationId.value)
        assertEquals(0, repo.messages.value.size)

        repo.addMessage(ChatMessage(content = "What is a qubit?", isUser = true))
        assertEquals(1, repo.messages.value.size)

        // Switch back to initial session
        repo.selectSession(initialId)
        assertEquals(initialId, repo.activeConversationId.value)
        assertEquals(2, repo.messages.value.size)
        assertEquals("How does photosynthesis work in plants?", repo.messages.value[0].content)

        // Rename session
        repo.renameSession(initialId, "Biology Study")
        assertEquals("Biology Study", repo.getActiveSessionTitle())

        // Delete session2
        repo.deleteSession(session2.id)
        assertEquals(1, repo.conversations.value.size)
        assertEquals(initialId, repo.activeConversationId.value)

        // Delete last remaining session recreates an empty session
        repo.deleteSession(initialId)
        assertEquals(1, repo.conversations.value.size)
        assertNotEquals(initialId, repo.activeConversationId.value)
        assertEquals(0, repo.messages.value.size)
    }

    @Test
    fun testRemoveMessagesFromTruncation() {
        val repo = ChatRepositoryImpl()
        val msg1 = ChatMessage(content = "User 1", isUser = true)
        val msg2 = ChatMessage(content = "Assistant 1", isUser = false)
        val msg3 = ChatMessage(content = "User 2", isUser = true)
        val msg4 = ChatMessage(content = "Assistant 2", isUser = false)

        repo.addMessage(msg1)
        repo.addMessage(msg2)
        repo.addMessage(msg3)
        repo.addMessage(msg4)

        assertEquals(4, repo.messages.value.size)

        // Remove from msg3 onwards (for edit and resend)
        val remaining = repo.removeMessagesFrom(msg3.id)
        assertEquals(2, remaining.size)
        assertEquals(2, repo.messages.value.size)
        assertEquals(msg1.id, repo.messages.value[0].id)
        assertEquals(msg2.id, repo.messages.value[1].id)
    }

    @Test
    fun testSearchAndFilterConversations() {
        val session1 = ConversationSession(
            title = "Android Architecture",
            messages = listOf(ChatMessage(content = "Explain MVVM", isUser = true))
        )
        val session2 = ConversationSession(
            title = "Kotlin Coroutines",
            messages = listOf(ChatMessage(content = "How does Flow work?", isUser = true))
        )
        val session3 = ConversationSession(
            title = "General Questions",
            messages = listOf(ChatMessage(content = "Tell me a joke", isUser = true))
        )

        val state = ChatUiState(
            conversations = listOf(session1, session2, session3),
            searchQuery = "Architecture"
        )
        assertEquals(1, state.filteredConversations.size)
        assertEquals("Android Architecture", state.filteredConversations[0].title)

        // Search by message content
        val stateByContent = state.copy(searchQuery = "Flow")
        assertEquals(1, stateByContent.filteredConversations.size)
        assertEquals("Kotlin Coroutines", stateByContent.filteredConversations[0].title)

        // Empty search returns all
        val stateAll = state.copy(searchQuery = "")
        assertEquals(3, stateAll.filteredConversations.size)
    }

    @Test
    fun testExportChatFormatting() {
        val repo = ChatRepositoryImpl()
        repo.renameSession(repo.activeConversationId.value, "Quantum Computing")
        repo.addMessage(ChatMessage(content = "What is superposition?", isUser = true))
        repo.addMessage(
            ChatMessage(
                content = "Superposition allows a quantum system to exist in multiple states simultaneously.",
                reasoning = "Defining fundamental quantum principle.",
                isUser = false
            )
        )

        val title = repo.getActiveSessionTitle()
        val messages = repo.messages.value
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n")
        sb.append("---\n\n")
        for (msg in messages) {
            val role = if (msg.isUser) "**User**" else "**Assistant**"
            sb.append(role).append(":\n\n")
            if (!msg.reasoning.isNullOrBlank()) {
                sb.append("> **Reasoning / Thought Process:**\n")
                msg.reasoning.lines().forEach { line ->
                    sb.append("> ").append(line).append("\n")
                }
                sb.append("\n")
            }
            sb.append(msg.content).append("\n\n---\n\n")
        }
        val export = sb.toString()

        assertTrue(export.contains("# Quantum Computing"))
        assertTrue(export.contains("What is superposition?"))
        assertTrue(export.contains("Superposition allows a quantum system"))
        assertTrue(export.contains("Reasoning / Thought Process:"))
        assertTrue(export.contains("Defining fundamental quantum principle."))
    }

    @Test
    fun testPreventDuplicateEmptyNewChatSessions() {
        val repo = ChatRepositoryImpl()
        assertEquals(1, repo.conversations.value.size)
        val initialSession = repo.conversations.value.first()

        // Calling createNewSession while the active session is already empty and default titled
        val sessionAgain = repo.createNewSession("New Chat")
        assertEquals(1, repo.conversations.value.size)
        assertEquals(initialSession.id, sessionAgain.id)

        // Only after sending a message does a new session get created
        repo.addMessage(ChatMessage(content = "Hello there", isUser = true))
        val secondSession = repo.createNewSession("New Chat")
        assertEquals(2, repo.conversations.value.size)
        assertNotEquals(initialSession.id, secondSession.id)
    }

    @Test
    fun testClearMessagesResetsTitleToNewChat() {
        val repo = ChatRepositoryImpl()
        repo.addMessage(ChatMessage(content = "Explain black holes in deep space", isUser = true))
        repo.addMessage(ChatMessage(content = "A black hole is a region of spacetime...", isUser = false))

        assertTrue(repo.getActiveSessionTitle().contains("black holes", ignoreCase = true))

        repo.clearMessages()
        assertEquals(0, repo.messages.value.size)
        assertEquals("New Chat", repo.getActiveSessionTitle())

        // Next prompt properly auto-titles
        repo.addMessage(ChatMessage(content = "What is the speed of light?", isUser = true))
        assertTrue(repo.getActiveSessionTitle().contains("speed of light", ignoreCase = true))
    }

    @Test
    fun testEditFirstMessageUpdatesTitle() {
        val repo = ChatRepositoryImpl()
        val msg1 = ChatMessage(content = "What is gravity?", isUser = true)
        repo.addMessage(msg1)
        repo.addMessage(ChatMessage(content = "Gravity is a fundamental interaction...", isUser = false))

        assertTrue(repo.getActiveSessionTitle().contains("gravity", ignoreCase = true))

        // Truncate from first message
        repo.removeMessagesFrom(msg1.id)
        assertEquals(0, repo.messages.value.size)

        // Resend with edited question
        repo.addMessage(ChatMessage(content = "Explain general relativity instead", isUser = true))
        assertTrue(repo.getActiveSessionTitle().contains("general relativity", ignoreCase = true))
    }

    @Test
    fun testDeleteSingleMessage() {
        val repo = ChatRepositoryImpl()
        val msg1 = ChatMessage(content = "Keep me 1", isUser = true)
        val msg2 = ChatMessage(content = "Delete me", isUser = false)
        val msg3 = ChatMessage(content = "Keep me 2", isUser = true)

        repo.addMessage(msg1)
        repo.addMessage(msg2)
        repo.addMessage(msg3)
        assertEquals(3, repo.messages.value.size)

        repo.deleteMessage(msg2.id)
        assertEquals(2, repo.messages.value.size)
        assertEquals(msg1.id, repo.messages.value[0].id)
        assertEquals(msg3.id, repo.messages.value[1].id)
    }

    @Test
    fun testFilteredConversationsSortedByRecency() {
        val olderSession = ConversationSession(
            title = "Older Chat",
            updatedAt = 1000L,
            messages = listOf(ChatMessage(content = "Old query", isUser = true))
        )
        val newerSession = ConversationSession(
            title = "Newer Chat",
            updatedAt = 5000L,
            messages = listOf(ChatMessage(content = "New query", isUser = true))
        )

        // Even if passed in older first
        val state = ChatUiState(conversations = listOf(olderSession, newerSession))
        assertEquals("Newer Chat", state.filteredConversations[0].title)
        assertEquals("Older Chat", state.filteredConversations[1].title)
    }

    @Test
    fun testSettingsViewModelSyncsInferenceSettings() {
        val initial = InferenceSettings(systemPrompt = "Default prompt", temperature = 0.7f)
        var savedSettings = initial
        val viewModel = com.mobileai.assistant.presentation.settings.SettingsViewModel(
            initialSettings = initial,
            onSettingsSaved = { savedSettings = it }
        )

        assertEquals("Default prompt", viewModel.uiState.value.settings.systemPrompt)

        // When external update occurs (e.g. from ChatScreen System Instructions dialog)
        val updatedFromChat = initial.copy(systemPrompt = "You are a master programmer")
        viewModel.updateInferenceSettings(updatedFromChat)

        assertEquals("You are a master programmer", viewModel.uiState.value.settings.systemPrompt)

        // If user adjusts temperature in settings afterwards, the updated prompt is NOT overwritten
        viewModel.updateTemperature(0.85f)
        assertEquals("You are a master programmer", savedSettings.systemPrompt)
        assertEquals(0.85f, savedSettings.temperature, 0.01f)
    }

    @Test
    fun testThinkingEffortMultipliers() {
        assertEquals("low", ThinkingEffort.LOW.apiValue)
        assertEquals("medium", ThinkingEffort.MEDIUM.apiValue)
        assertEquals("high", ThinkingEffort.HIGH.apiValue)
        assertEquals("max", ThinkingEffort.MAX.apiValue)

        assertTrue(ThinkingEffort.HIGH.tokenBudgetMultiplier > ThinkingEffort.LOW.tokenBudgetMultiplier)
    }

    @Test
    fun testModelInfoFormatting() {
        val model = ModelInfo(
            fileName = "qwen2.5-0.5b-instruct-q4_0.gguf",
            filePath = "/data/user/0/com.mobileai.assistant/files/models/qwen2.5-0.5b-instruct-q4_0.gguf",
            fileSizeBytes = 350L * 1024L * 1024L,
            architecture = "qwen2",
            quantization = "Q4_0"
        )

        assertEquals("qwen2.5-0.5b-instruct-q4_0", model.displayName)
        assertTrue(model.formattedSize.contains("MB"))
    }

    @Test
    fun testInferenceSettingsDefaults() {
        val settings = InferenceSettings.DEFAULT
        assertEquals(0.7f, settings.temperature, 0.01f)
        assertEquals(0.9f, settings.topP, 0.01f)
        assertEquals(40, settings.topK)
        assertEquals(1024, settings.maxTokens)
        assertEquals(4096, settings.contextSize)
        assertEquals(4, settings.threads)
    }
}
