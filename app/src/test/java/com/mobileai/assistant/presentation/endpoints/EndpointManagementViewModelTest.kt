package com.mobileai.assistant.presentation.endpoints

import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.repository.EndpointRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

@OptIn(ExperimentalCoroutinesApi::class)
class EndpointManagementViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeEndpointRepository
    private lateinit var settingsRepository: JsonFileSettingsRepository
    private var selectedEndpoint: RemoteEndpointConfig? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeEndpointRepository()
        val tempDir = tempFolder.newFolder("settings")
        settingsRepository = JsonFileSettingsRepository(tempDir)
        selectedEndpoint = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): EndpointManagementViewModel {
        return EndpointManagementViewModel(
            endpointRepository = fakeRepository,
            settingsRepository = settingsRepository,
            onEndpointSelected = { selectedEndpoint = it }
        )
    }

    @Test
    fun testFormInputAndValidation() {
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onNameChanged("Ollama Local")
        viewModel.onUrlChanged("http://localhost:11434/v1")
        viewModel.onSelectedModelChanged("llama3.2")
        viewModel.onApiKeyChanged("secret-key")

        assertTrue(viewModel.uiState.value.canSave)
        assertEquals("Ollama Local", viewModel.uiState.value.nameInput)
        assertEquals("http://localhost:11434/v1", viewModel.uiState.value.urlInput)
        assertEquals("llama3.2", viewModel.uiState.value.selectedModelInput)
        assertEquals("secret-key", viewModel.uiState.value.apiKeyInput)
    }

    @Test
    fun testSaveAndDeleteEndpoint() = runTest {
        val viewModel = createViewModel()
        viewModel.onNameChanged("OpenAI API")
        viewModel.onUrlChanged("https://api.openai.com/v1")
        viewModel.onSelectedModelChanged("gpt-4o")

        viewModel.saveEndpoint()
        advanceUntilIdle()

        assertEquals(1, fakeRepository.endpoints.value.size)
        val saved = fakeRepository.endpoints.value.first()
        assertEquals("OpenAI API", saved.name)
        assertEquals("gpt-4o", saved.selectedModel)

        viewModel.deleteEndpoint(saved)
        advanceUntilIdle()

        assertTrue(fakeRepository.endpoints.value.isEmpty())
    }

    @Test
    fun testSelectActiveEndpoint() = runTest {
        val viewModel = createViewModel()
        val config = RemoteEndpointConfig(
            id = "ep-1",
            name = "vLLM",
            baseUrl = "http://192.168.1.10:8000/v1",
            selectedModel = "qwen2.5"
        )
        fakeRepository.saveEndpoint(config)
        advanceUntilIdle()

        viewModel.selectActiveEndpoint(config)
        advanceUntilIdle()

        assertEquals(config.id, fakeRepository.activeEndpoint.value?.id)
        assertEquals(config.id, selectedEndpoint?.id)
    }

    @Test
    fun testPerModelProfileConfiguration() = runTest {
        val viewModel = createViewModel()
        val endpoint = RemoteEndpointConfig(
            id = "ep-test",
            name = "Test Endpoint",
            baseUrl = "http://test:8000/v1",
            selectedModel = "model-alpha"
        )

        viewModel.openModelProfile(endpoint, "model-alpha")
        assertNotNull(viewModel.uiState.value.configuringProfileEndpoint)
        assertNotNull(viewModel.uiState.value.currentProfile)

        val updated = viewModel.uiState.value.currentProfile!!.copy(
            displayName = "Custom Alpha",
            params = viewModel.uiState.value.currentProfile!!.params.copy(temperature = 0.25)
        )
        viewModel.updateConfiguredProfile(updated)
        viewModel.saveModelProfile(updated)

        assertNull(viewModel.uiState.value.configuringProfileEndpoint)
        assertNull(viewModel.uiState.value.currentProfile)

        val key = com.mobileai.assistant.data.settings.AppSettingsRepository.modelKeyRemote(endpoint.id, "model-alpha")
        val saved = settingsRepository.profileFor(key)
        assertNotNull(saved)
        assertEquals("Custom Alpha", saved?.displayName)
        assertEquals(0.25, saved?.params?.temperature ?: 0.0, 0.001)
    }

    private class FakeEndpointRepository : EndpointRepository {
        private val _endpoints = MutableStateFlow<List<RemoteEndpointConfig>>(emptyList())
        override val endpoints: StateFlow<List<RemoteEndpointConfig>> = _endpoints.asStateFlow()

        private val _activeEndpoint = MutableStateFlow<RemoteEndpointConfig?>(null)
        override val activeEndpoint: StateFlow<RemoteEndpointConfig?> = _activeEndpoint.asStateFlow()

        override suspend fun saveEndpoint(config: RemoteEndpointConfig) {
            val current = _endpoints.value.toMutableList()
            val index = current.indexOfFirst { it.id == config.id }
            if (index >= 0) {
                current[index] = config
            } else {
                current.add(config)
            }
            _endpoints.value = current
        }

        override suspend fun deleteEndpoint(id: String) {
            _endpoints.value = _endpoints.value.filterNot { it.id == id }
            if (_activeEndpoint.value?.id == id) {
                _activeEndpoint.value = null
            }
        }

        override suspend fun setActiveEndpoint(config: RemoteEndpointConfig?) {
            _activeEndpoint.value = config
        }

        override suspend fun getEndpoints(): List<RemoteEndpointConfig> {
            return _endpoints.value
        }
    }
}
