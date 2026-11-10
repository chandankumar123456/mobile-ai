package com.mobileai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.mobileai.assistant.data.artifact.ArtifactRepositoryImpl
import com.mobileai.assistant.data.dot.DotRepositoryImpl
import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.data.native_engine.LlamaCppInferenceEngine
import com.mobileai.assistant.data.remote.RemoteInferenceEngine
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.data.repository.EndpointRepositoryImpl
import com.mobileai.assistant.data.repository.ModelRepositoryImpl
import com.mobileai.assistant.data.repository.VoiceSettingsRepositoryImpl
import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.TaskRecoveryCoordinator
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.data.voice.SpeechToTextManager
import com.mobileai.assistant.data.voice.TextToSpeechManager
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.usecase.CancelGenerationUseCase
import com.mobileai.assistant.domain.usecase.DeleteModelUseCase
import com.mobileai.assistant.domain.usecase.GenerateResponseUseCase
import com.mobileai.assistant.domain.usecase.GetImportedModelsUseCase
import com.mobileai.assistant.domain.usecase.ImportModelUseCase
import com.mobileai.assistant.domain.usecase.LoadModelUseCase
import com.mobileai.assistant.domain.usecase.UnloadModelUseCase
import com.mobileai.assistant.presentation.chat.ChatViewModel
import com.mobileai.assistant.presentation.endpoints.EndpointManagementViewModel
import com.mobileai.assistant.presentation.model.ModelSelectionViewModel
import com.mobileai.assistant.presentation.navigation.AppNavGraph
import com.mobileai.assistant.presentation.settings.SettingsViewModel
import com.mobileai.assistant.presentation.theme.MobileAITheme

class MainActivity : ComponentActivity() {

    private lateinit var inferenceEngine: LlamaCppInferenceEngine
    private lateinit var remoteEngine: RemoteInferenceEngine
    private lateinit var sttManager: SpeechToTextManager
    private lateinit var ttsManager: TextToSpeechManager

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var modelSelectionViewModel: ModelSelectionViewModel
    private lateinit var endpointManagementViewModel: EndpointManagementViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Data Layer initialization
        val fileManager = ModelFileManager(applicationContext)
        val modelRepository = ModelRepositoryImpl(fileManager)
        val attachmentStore = com.mobileai.assistant.data.file.AttachmentStore(applicationContext)
        val chatRepository = ChatRepositoryImpl(applicationContext, attachmentStore = attachmentStore)
        val endpointRepository = EndpointRepositoryImpl(applicationContext)
        val voiceSettingsRepository = VoiceSettingsRepositoryImpl(applicationContext)
        val settingsRepository = JsonFileSettingsRepository(applicationContext)

        val dotRepository = DotRepositoryImpl(applicationContext)
        val artifactRepository = ArtifactRepositoryImpl(applicationContext)
        val toolRegistry = DefaultToolRegistry()
        val toolAuditLogger = ToolAuditLoggerImpl(applicationContext)
        val taskManager = TaskManagerImpl(
            context = applicationContext,
            toolRegistry = toolRegistry,
            auditLogger = toolAuditLogger
        )
        val taskRecoveryCoordinator = TaskRecoveryCoordinator(taskManager)

        inferenceEngine = LlamaCppInferenceEngine(applicationContext)
        remoteEngine = RemoteInferenceEngine()
        sttManager = SpeechToTextManager(applicationContext)
        ttsManager = TextToSpeechManager(applicationContext)

        // 2. Domain Layer Use Cases
        val generateResponseUseCase = GenerateResponseUseCase(inferenceEngine)
        val cancelGenerationUseCase = CancelGenerationUseCase(inferenceEngine)
        val loadModelUseCase = LoadModelUseCase(inferenceEngine)
        val unloadModelUseCase = UnloadModelUseCase(inferenceEngine)
        val importModelUseCase = ImportModelUseCase(modelRepository)
        val getImportedModelsUseCase = GetImportedModelsUseCase(modelRepository)
        val deleteModelUseCase = DeleteModelUseCase(modelRepository)

        // 3. Presentation ViewModels
        settingsViewModel = SettingsViewModel(
            initialSettings = settingsRepository.settings.value,
            voiceSettingsRepository = voiceSettingsRepository,
            ttsManager = ttsManager,
            fileManager = fileManager,
            onSettingsSaved = { updatedSettings ->
                settingsRepository.update(updatedSettings)
            }
        )

        chatViewModel = ChatViewModel(
            engine = inferenceEngine,
            chatRepository = chatRepository,
            generateResponseUseCase = generateResponseUseCase,
            cancelGenerationUseCase = cancelGenerationUseCase,
            remoteEngine = remoteEngine,
            endpointRepository = endpointRepository,
            voiceSettingsRepository = voiceSettingsRepository,
            sttManager = sttManager,
            ttsManager = ttsManager,
            context = applicationContext,
            getSettings = { settingsRepository.settings.value },
            onUpdateSettings = { updated ->
                settingsRepository.update(updated)
                settingsViewModel.updateInferenceSettings(updated)
            },
            settingsRepository = settingsRepository,
            attachmentStore = attachmentStore,
            dotRepository = dotRepository,
            artifactRepository = artifactRepository,
            taskManager = taskManager,
            toolRegistry = toolRegistry,
            toolAuditLogger = toolAuditLogger
        )

        endpointManagementViewModel = EndpointManagementViewModel(
            endpointRepository = endpointRepository,
            settingsRepository = settingsRepository,
            onEndpointSelected = { endpoint ->
                chatViewModel.selectTarget(InferenceTarget.Remote(endpoint, endpoint.selectedModel))
            }
        )

        modelSelectionViewModel = ModelSelectionViewModel(
            engine = inferenceEngine,
            importModelUseCase = importModelUseCase,
            getImportedModelsUseCase = getImportedModelsUseCase,
            loadModelUseCase = loadModelUseCase,
            unloadModelUseCase = unloadModelUseCase,
            deleteModelUseCase = deleteModelUseCase,
            fileManager = fileManager,
            settingsRepository = settingsRepository,
            onModelSelected = { model ->
                chatViewModel.selectTarget(InferenceTarget.Local(model))
            }
        )

        // Recover interrupted multi-step tasks from previous sessions
        lifecycleScope.launch {
            try {
                taskRecoveryCoordinator.recoverInterruptedTasks()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Task recovery failed on launch", e)
            }
        }

        // Auto-load last-used local model if available on disk
        lifecycleScope.launch {
            val lastPath = settingsRepository.lastLocalModelPath.value
            if (!lastPath.isNullOrBlank()) {
                val modelFile = File(lastPath)
                if (modelFile.exists() && modelFile.canRead()) {
                    val modelInfo = fileManager.readMetadataFromFile(modelFile) ?: com.mobileai.assistant.domain.model.ModelInfo(
                        fileName = modelFile.name,
                        filePath = modelFile.absolutePath,
                        fileSizeBytes = modelFile.length()
                    )
                    loadModelUseCase(modelInfo).onSuccess {
                        chatViewModel.selectTarget(InferenceTarget.Local(modelInfo))
                    }
                }
            }
        }

        setContent {
            MobileAITheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavGraph(
                        navController = navController,
                        chatViewModel = chatViewModel,
                        modelSelectionViewModel = modelSelectionViewModel,
                        endpointManagementViewModel = endpointManagementViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::inferenceEngine.isInitialized) {
            inferenceEngine.release()
        }
        if (::sttManager.isInitialized) {
            sttManager.cancel()
        }
        if (::ttsManager.isInitialized) {
            ttsManager.release()
        }
        super.onDestroy()
    }
}
