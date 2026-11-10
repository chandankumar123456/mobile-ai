package com.mobileai.assistant.di

import android.content.Context
import com.mobileai.assistant.data.export.ConversationExportService
import com.mobileai.assistant.data.file.AttachmentStore
import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.data.task.TaskManagerImpl
import com.mobileai.assistant.data.task.TaskRecoveryCoordinator
import com.mobileai.assistant.data.task.ToolAuditLoggerImpl
import com.mobileai.assistant.data.voice.SpeechToTextManager
import com.mobileai.assistant.data.voice.TextToSpeechManager
import com.mobileai.assistant.domain.task.TaskManager
import com.mobileai.assistant.domain.tool.DefaultToolRegistry
import com.mobileai.assistant.domain.tool.ToolAuditLogger
import com.mobileai.assistant.domain.tool.ToolRegistry

/**
 * Dependency injection module for application and system services.
 */
class ServiceModule(private val context: Context) {
    val fileManager: ModelFileManager by lazy { ModelFileManager(context) }
    val attachmentStore: AttachmentStore by lazy { AttachmentStore(context) }
    val toolRegistry: ToolRegistry by lazy { DefaultToolRegistry() }
    val toolAuditLogger: ToolAuditLogger by lazy { ToolAuditLoggerImpl(context) }
    val taskManager: TaskManager by lazy {
        TaskManagerImpl(
            context = context,
            toolRegistry = toolRegistry,
            auditLogger = toolAuditLogger
        )
    }
    val taskRecoveryCoordinator: TaskRecoveryCoordinator by lazy {
        TaskRecoveryCoordinator(taskManager as? TaskManagerImpl)
    }
    val sttManager: SpeechToTextManager by lazy { SpeechToTextManager(context) }
    val ttsManager: TextToSpeechManager by lazy { TextToSpeechManager(context) }
    val exportService: ConversationExportService by lazy { ConversationExportService() }
    val mcpServerManager: com.mobileai.assistant.data.mcp.McpServerManager by lazy {
        com.mobileai.assistant.data.mcp.McpServerManager(context = context, toolRegistry = toolRegistry)
    }
}
