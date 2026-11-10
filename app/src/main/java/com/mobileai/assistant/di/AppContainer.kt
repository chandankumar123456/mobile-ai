package com.mobileai.assistant.di

import android.content.Context
import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.data.remote.RemoteInferenceEngine
import com.mobileai.assistant.data.task.TaskRecoveryCoordinator
import com.mobileai.assistant.data.voice.SpeechToTextManager
import com.mobileai.assistant.data.voice.TextToSpeechManager
import com.mobileai.assistant.domain.artifact.ArtifactRepository
import com.mobileai.assistant.domain.dot.DotRepository
import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.repository.ChatRepository
import com.mobileai.assistant.domain.repository.EndpointRepository
import com.mobileai.assistant.domain.repository.ModelRepository
import com.mobileai.assistant.domain.repository.VoiceSettingsRepository
import com.mobileai.assistant.data.settings.AppSettingsRepository
import com.mobileai.assistant.domain.task.TaskManager
import com.mobileai.assistant.domain.tool.ToolAuditLogger
import com.mobileai.assistant.domain.tool.ToolRegistry
import com.mobileai.assistant.domain.usecase.CancelGenerationUseCase
import com.mobileai.assistant.domain.usecase.DeleteModelUseCase
import com.mobileai.assistant.domain.usecase.GenerateResponseUseCase
import com.mobileai.assistant.domain.usecase.GetImportedModelsUseCase
import com.mobileai.assistant.domain.usecase.ImportModelUseCase
import com.mobileai.assistant.domain.usecase.LoadModelUseCase
import com.mobileai.assistant.domain.usecase.UnloadModelUseCase

/**
 * Top-level Dependency Injection container interface.
 * Implemented by [AppContainerImpl] in production and replaceable in tests.
 */
interface AppContainer {
    val localInferenceEngine: LocalInferenceEngine
    val remoteInferenceEngine: RemoteInferenceEngine

    val modelRepository: ModelRepository
    val chatRepository: ChatRepository
    val endpointRepository: EndpointRepository
    val voiceSettingsRepository: VoiceSettingsRepository
    val settingsRepository: AppSettingsRepository
    val dotRepository: DotRepository
    val artifactRepository: ArtifactRepository

    val fileManager: ModelFileManager
    val attachmentStore: AttachmentStore
    val toolRegistry: ToolRegistry
    val toolAuditLogger: ToolAuditLogger
    val taskManager: TaskManager
    val taskRecoveryCoordinator: TaskRecoveryCoordinator
    val sttManager: SpeechToTextManager
    val ttsManager: TextToSpeechManager
    val exportService: ConversationExportService
    val mcpServerManager: com.mobileai.assistant.data.mcp.McpServerManager

    val generateResponseUseCase: GenerateResponseUseCase
    val cancelGenerationUseCase: CancelGenerationUseCase
    val loadModelUseCase: LoadModelUseCase
    val unloadModelUseCase: UnloadModelUseCase
    val importModelUseCase: ImportModelUseCase
    val getImportedModelsUseCase: GetImportedModelsUseCase
    val deleteModelUseCase: DeleteModelUseCase
}

class AppContainerImpl(private val context: Context) : AppContainer {

    private val engineModule = EngineModule(context)
    private val serviceModule = ServiceModule(context)
    private val repositoryModule = RepositoryModule(
        context = context,
        attachmentStore = serviceModule.attachmentStore,
        fileManager = serviceModule.fileManager
    )
    private val useCaseModule = UseCaseModule(
        localEngine = engineModule.provideLocalEngine(),
        modelRepository = repositoryModule.modelRepository
    )

    override val localInferenceEngine: LocalInferenceEngine get() = engineModule.provideLocalEngine()
    override val remoteInferenceEngine: RemoteInferenceEngine get() = engineModule.provideRemoteEngine()

    override val modelRepository: ModelRepository get() = repositoryModule.modelRepository
    override val chatRepository: ChatRepository get() = repositoryModule.chatRepository
    override val endpointRepository: EndpointRepository get() = repositoryModule.endpointRepository
    override val voiceSettingsRepository: VoiceSettingsRepository get() = repositoryModule.voiceSettingsRepository
    override val settingsRepository: AppSettingsRepository get() = repositoryModule.settingsRepository
    override val dotRepository: DotRepository get() = repositoryModule.dotRepository
    override val artifactRepository: ArtifactRepository get() = repositoryModule.artifactRepository

    override val fileManager: ModelFileManager get() = serviceModule.fileManager
    override val attachmentStore: AttachmentStore get() = serviceModule.attachmentStore
    override val toolRegistry: ToolRegistry get() = serviceModule.toolRegistry
    override val toolAuditLogger: ToolAuditLogger get() = serviceModule.toolAuditLogger
    override val taskManager: TaskManager get() = serviceModule.taskManager
    override val taskRecoveryCoordinator: TaskRecoveryCoordinator get() = serviceModule.taskRecoveryCoordinator
    override val sttManager: SpeechToTextManager get() = serviceModule.sttManager
    override val ttsManager: TextToSpeechManager get() = serviceModule.ttsManager
    override val exportService: ConversationExportService get() = serviceModule.exportService
    override val mcpServerManager: com.mobileai.assistant.data.mcp.McpServerManager get() = serviceModule.mcpServerManager

    override val generateResponseUseCase: GenerateResponseUseCase get() = useCaseModule.generateResponseUseCase
    override val cancelGenerationUseCase: CancelGenerationUseCase get() = useCaseModule.cancelGenerationUseCase
    override val loadModelUseCase: LoadModelUseCase get() = useCaseModule.loadModelUseCase
    override val unloadModelUseCase: UnloadModelUseCase get() = useCaseModule.unloadModelUseCase
    override val importModelUseCase: ImportModelUseCase get() = useCaseModule.importModelUseCase
    override val getImportedModelsUseCase: GetImportedModelsUseCase get() = useCaseModule.getImportedModelsUseCase
    override val deleteModelUseCase: DeleteModelUseCase get() = useCaseModule.deleteModelUseCase
}
