package com.mobileai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mobileai.assistant.data.native_engine.LlamaCppInferenceEngine
import com.mobileai.assistant.data.voice.SpeechToTextManager
import com.mobileai.assistant.data.voice.TextToSpeechManager
import com.mobileai.assistant.domain.model.InferenceTarget
import com.mobileai.assistant.presentation.chat.ChatViewModel
import com.mobileai.assistant.presentation.endpoints.EndpointManagementViewModel
import com.mobileai.assistant.presentation.model.ModelSelectionViewModel
import com.mobileai.assistant.presentation.navigation.AppNavGraph
import com.mobileai.assistant.presentation.settings.SettingsViewModel
import com.mobileai.assistant.presentation.theme.MobileAITheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var modelSelectionViewModel: ModelSelectionViewModel
    private lateinit var endpointManagementViewModel: EndpointManagementViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MobileAiApplication
        val container = app.appContainer
        val factory = app.viewModelFactory

        // 1. Initialize ViewModels via DI factory
        settingsViewModel = factory.createSettingsViewModel()
        chatViewModel = factory.createChatViewModel()

        endpointManagementViewModel = factory.createEndpointManagementViewModel(
            onEndpointSelected = { endpoint ->
                chatViewModel.selectTarget(InferenceTarget.Remote(endpoint, endpoint.selectedModel))
            }
        )

        modelSelectionViewModel = factory.createModelSelectionViewModel(
            onModelSelected = { model ->
                chatViewModel.selectTarget(InferenceTarget.Local(model))
            }
        )

        // 2. Recover interrupted multi-step tasks from previous sessions
        lifecycleScope.launch {
            try {
                container.taskRecoveryCoordinator.recoverInterruptedTasks()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Task recovery failed on launch", e)
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
        val container = (application as? MobileAiApplication)?.appContainer
        if (container != null) {
            (container.localInferenceEngine as? LlamaCppInferenceEngine)?.release()
            container.sttManager.cancel()
            container.ttsManager.release()
        }
        super.onDestroy()
    }
}
