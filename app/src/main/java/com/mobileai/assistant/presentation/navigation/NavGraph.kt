package com.mobileai.assistant.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mobileai.assistant.presentation.chat.ChatScreen
import com.mobileai.assistant.presentation.chat.ChatViewModel
import com.mobileai.assistant.presentation.endpoints.EndpointManagementScreen
import com.mobileai.assistant.presentation.endpoints.EndpointManagementViewModel
import com.mobileai.assistant.presentation.model.ModelSelectionScreen
import com.mobileai.assistant.presentation.model.ModelSelectionViewModel
import com.mobileai.assistant.presentation.settings.SettingsScreen
import com.mobileai.assistant.presentation.settings.SettingsViewModel
import com.mobileai.assistant.presentation.voice.VoiceConversationScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    chatViewModel: ChatViewModel,
    modelSelectionViewModel: ModelSelectionViewModel,
    endpointManagementViewModel: EndpointManagementViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToModelSelection = { navController.navigate(Screen.ModelSelection.route) },
                onNavigateToEndpoints = { navController.navigate(Screen.Endpoints.route) },
                onNavigateToVoiceMode = { navController.navigate(Screen.VoiceMode.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.ModelSelection.route) {
            ModelSelectionScreen(
                viewModel = modelSelectionViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Endpoints.route) {
            EndpointManagementScreen(
                viewModel = endpointManagementViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.VoiceMode.route) {
            VoiceConversationScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
