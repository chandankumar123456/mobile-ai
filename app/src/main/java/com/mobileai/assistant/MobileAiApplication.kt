package com.mobileai.assistant

import android.app.Application
import com.mobileai.assistant.di.AppContainer
import com.mobileai.assistant.di.AppContainerImpl
import com.mobileai.assistant.di.ViewModelFactory

/**
 * Application class for the Mobile AI Assistant platform.
 * Initializes and manages top-level dependency container.
 */
class MobileAiApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    lateinit var viewModelFactory: ViewModelFactory
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainerImpl(this)
        viewModelFactory = ViewModelFactory(appContainer)
    }
}
