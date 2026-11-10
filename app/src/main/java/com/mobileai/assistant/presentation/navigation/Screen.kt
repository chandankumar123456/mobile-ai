package com.mobileai.assistant.presentation.navigation

sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object ModelSelection : Screen("model_selection")
    data object Endpoints : Screen("endpoints")
    data object Settings : Screen("settings")
    data object VoiceMode : Screen("voice_mode")
}

