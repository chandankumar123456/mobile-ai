package com.mobileai.assistant.di

import android.content.Context
import com.mobileai.assistant.data.native_engine.LlamaCppInferenceEngine
import com.mobileai.assistant.data.remote.RemoteInferenceEngine
import com.mobileai.assistant.domain.engine.LocalInferenceEngine

/**
 * Dependency injection module for AI inference engines.
 * Manages singleton lifecycle of the native llama.cpp JNI engine.
 */
class EngineModule(private val context: Context) {

    @Volatile
    private var localEngineInstance: LocalInferenceEngine? = null

    @Volatile
    private var remoteEngineInstance: RemoteInferenceEngine? = null

    fun provideLocalEngine(): LocalInferenceEngine {
        return localEngineInstance ?: synchronized(this) {
            localEngineInstance ?: LlamaCppInferenceEngine(context).also { localEngineInstance = it }
        }
    }

    fun provideRemoteEngine(): RemoteInferenceEngine {
        return remoteEngineInstance ?: synchronized(this) {
            remoteEngineInstance ?: RemoteInferenceEngine().also { remoteEngineInstance = it }
        }
    }
}
