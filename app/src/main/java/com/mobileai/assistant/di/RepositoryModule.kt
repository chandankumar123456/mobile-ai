package com.mobileai.assistant.di

import android.content.Context
import com.mobileai.assistant.data.artifact.ArtifactRepositoryImpl
import com.mobileai.assistant.data.dot.DotRepositoryImpl
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.data.repository.ChatRepositoryImpl
import com.mobileai.assistant.data.repository.EndpointRepositoryImpl
import com.mobileai.assistant.data.repository.ModelRepositoryImpl
import com.mobileai.assistant.data.repository.VoiceSettingsRepositoryImpl
import com.mobileai.assistant.data.settings.AppSettingsRepository
import com.mobileai.assistant.data.settings.JsonFileSettingsRepository
import com.mobileai.assistant.domain.artifact.ArtifactRepository
import com.mobileai.assistant.domain.dot.DotRepository
import com.mobileai.assistant.domain.repository.ChatRepository
import com.mobileai.assistant.domain.repository.EndpointRepository
import com.mobileai.assistant.domain.repository.ModelRepository
import com.mobileai.assistant.domain.repository.VoiceSettingsRepository

/**
 * Dependency injection module for domain and data repositories.
 */
class RepositoryModule(
    private val context: Context,
    private val attachmentStore: AttachmentStore,
    private val fileManager: ModelFileManager
) {
    val modelRepository: ModelRepository by lazy { ModelRepositoryImpl(fileManager) }
    val chatRepository: ChatRepository by lazy { ChatRepositoryImpl(context, attachmentStore = attachmentStore) }
    val endpointRepository: EndpointRepository by lazy { EndpointRepositoryImpl(context) }
    val voiceSettingsRepository: VoiceSettingsRepository by lazy { VoiceSettingsRepositoryImpl(context) }
    val settingsRepository: AppSettingsRepository by lazy { JsonFileSettingsRepository(context) }
    val dotRepository: DotRepository by lazy { DotRepositoryImpl(context) }
    val artifactRepository: ArtifactRepository by lazy { ArtifactRepositoryImpl(context) }
}
