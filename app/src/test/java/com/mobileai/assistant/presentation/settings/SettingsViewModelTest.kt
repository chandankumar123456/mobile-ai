package com.mobileai.assistant.presentation.settings

import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ResponseLanguage
import com.mobileai.assistant.domain.model.ThinkingEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var fileManager: ModelFileManager
    private var lastSavedSettings: InferenceSettings? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("files")
        fileManager = ModelFileManager(context = null, rootDir = rootDir)
        lastSavedSettings = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        initialSettings: InferenceSettings = InferenceSettings.DEFAULT
    ): SettingsViewModel {
        return SettingsViewModel(
            initialSettings = initialSettings,
            voiceSettingsRepository = null,
            ttsManager = null,
            fileManager = fileManager,
            onSettingsSaved = { lastSavedSettings = it }
        )
    }

    @Test
    fun testUpdateGeneralSettings() {
        val viewModel = createViewModel()

        viewModel.updateResponseLanguage(ResponseLanguage.HINDI)
        assertEquals(ResponseLanguage.HINDI, viewModel.uiState.value.settings.responseLanguage)
        assertEquals(ResponseLanguage.HINDI, lastSavedSettings?.responseLanguage)

        viewModel.updateSystemPrompt("Custom prompt for testing")
        assertEquals("Custom prompt for testing", viewModel.uiState.value.settings.systemPrompt)
        assertEquals("Custom prompt for testing", lastSavedSettings?.systemPrompt)

        viewModel.updateThinkingEnabled(true)
        assertTrue(viewModel.uiState.value.settings.thinkingEnabled)
        assertTrue(lastSavedSettings?.thinkingEnabled == true)

        viewModel.updateThinkingEffort(ThinkingEffort.MAX)
        assertEquals(ThinkingEffort.MAX, viewModel.uiState.value.settings.thinkingEffort)
        assertEquals(ThinkingEffort.MAX, lastSavedSettings?.thinkingEffort)
    }

    @Test
    fun testUpdateSamplingParameters() {
        val viewModel = createViewModel()

        viewModel.updateTemperature(1.4f)
        assertEquals(1.4f, viewModel.uiState.value.settings.temperature, 0.001f)

        viewModel.updateTopP(0.85f)
        assertEquals(0.85f, viewModel.uiState.value.settings.topP, 0.001f)

        viewModel.updateTopK(60)
        assertEquals(60, viewModel.uiState.value.settings.topK)

        viewModel.updateMinP(0.08)
        assertEquals(0.08, viewModel.uiState.value.settings.minP, 0.0001)

        viewModel.updateRepeatPenalty(1.25)
        assertEquals(1.25, viewModel.uiState.value.settings.repeatPenalty, 0.0001)

        viewModel.updatePresencePenalty(0.3)
        assertEquals(0.3, viewModel.uiState.value.settings.presencePenalty, 0.0001)

        viewModel.updateFrequencyPenalty(0.4)
        assertEquals(0.4, viewModel.uiState.value.settings.frequencyPenalty, 0.0001)

        viewModel.updateSeed(42L)
        assertEquals(42L, viewModel.uiState.value.settings.seed)

        viewModel.updateMaxTokens(2048)
        assertEquals(2048, viewModel.uiState.value.settings.maxTokens)
    }

    @Test
    fun testUpdateRuntimeParameters() {
        val viewModel = createViewModel()

        viewModel.updateContextSize(8192)
        assertEquals(8192, viewModel.uiState.value.settings.contextSize)

        viewModel.updateThreads(6)
        assertEquals(6, viewModel.uiState.value.settings.threads)

        viewModel.updateBatchSize(1024)
        assertEquals(1024, viewModel.uiState.value.settings.batchSize)
    }

    @Test
    fun testSelectTab() {
        val viewModel = createViewModel()
        assertEquals(SettingsTab.GENERAL, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(SettingsTab.SAMPLING)
        assertEquals(SettingsTab.SAMPLING, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(SettingsTab.RUNTIME)
        assertEquals(SettingsTab.RUNTIME, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testStorageSummaryCalculation() {
        val modelsDir = File(rootDir, "models").apply { mkdirs() }
        File(modelsDir, "test.gguf").writeBytes(ByteArray(1024 * 1024)) // 1MB

        val attachmentsDir = File(rootDir, "attachments").apply { mkdirs() }
        File(attachmentsDir, "photo.jpg").writeBytes(ByteArray(512 * 1024)) // 512KB

        val viewModel = createViewModel()
        viewModel.refreshStorage()

        assertEquals(1024L * 1024L, viewModel.uiState.value.modelStorageBytes)
        assertEquals(512L * 1024L, viewModel.uiState.value.attachmentStorageBytes)
        assertTrue(viewModel.uiState.value.availableStorageBytes >= 0L)
    }

    @Test
    fun testResetToDefaults() {
        val viewModel = createViewModel()

        viewModel.updateTemperature(1.9f)
        viewModel.updateTopK(90)
        viewModel.updateThinkingEnabled(true)

        viewModel.resetToDefaults()

        assertEquals(InferenceSettings.DEFAULT.temperature, viewModel.uiState.value.settings.temperature, 0.001f)
        assertEquals(InferenceSettings.DEFAULT.topK, viewModel.uiState.value.settings.topK)
        assertFalse(viewModel.uiState.value.settings.thinkingEnabled)
        assertEquals(InferenceSettings.DEFAULT, lastSavedSettings)
    }
}
