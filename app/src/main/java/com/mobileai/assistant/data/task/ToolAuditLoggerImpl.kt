package com.mobileai.assistant.data.task

import android.content.Context
import com.mobileai.assistant.domain.tool.ToolAuditLogEntry
import com.mobileai.assistant.domain.tool.ToolAuditLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ToolAuditLogStoreEnvelope(
    val version: Int = 1,
    val logs: List<ToolAuditLogEntry> = emptyList()
)

/**
 * Immutable, local-first audit logger storing tool invocations in `tool_audit_logs_v1.json`.
 */
class ToolAuditLoggerImpl(
    private val context: Context? = null,
    rootDirOverride: File? = null
) : ToolAuditLogger {

    internal constructor(rootDir: File) : this(null, rootDir)

    private val rootDir: File? = rootDirOverride ?: context?.filesDir

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val saveLock = Any()

    private val _logs = MutableStateFlow<List<ToolAuditLogEntry>>(emptyList())
    val logs: StateFlow<List<ToolAuditLogEntry>> = _logs.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        synchronized(saveLock) {
            val dir = rootDir ?: return
            val file = File(dir, FILE_LOGS)
            if (file.exists() && file.length() > 0) {
                try {
                    val envelope = json.decodeFromString<ToolAuditLogStoreEnvelope>(file.readText())
                    _logs.value = envelope.logs
                } catch (_: Exception) {
                    _logs.value = emptyList()
                }
            } else {
                _logs.value = emptyList()
            }
        }
    }

    override suspend fun log(entry: ToolAuditLogEntry) {
        synchronized(saveLock) {
            val updated = _logs.value + entry
            _logs.value = updated
            saveLogsLocked(updated)
        }
    }

    override fun getAuditLogs(): Flow<List<ToolAuditLogEntry>> = _logs.asStateFlow()

    override fun getLogsForDot(dotId: String): Flow<List<ToolAuditLogEntry>> =
        _logs.map { list -> list.filter { it.dotId == dotId } }

    override fun getLogsForTask(taskId: String): Flow<List<ToolAuditLogEntry>> =
        _logs.map { list -> list.filter { it.taskId == taskId } }

    override suspend fun clearLogs() {
        synchronized(saveLock) {
            _logs.value = emptyList()
            saveLogsLocked(emptyList())
        }
    }

    private fun saveLogsLocked(logsToSave: List<ToolAuditLogEntry>) {
        val dir = rootDir ?: return
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val targetFile = File(dir, FILE_LOGS)
        val tmpFile = File(dir, "$FILE_LOGS.tmp")
        try {
            val content = json.encodeToString(
                ToolAuditLogStoreEnvelope(
                    version = 1,
                    logs = logsToSave
                )
            )
            tmpFile.writeText(content)
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (_: Exception) {
            // Log persistence error handled gracefully
        }
    }

    companion object {
        const val FILE_LOGS = "tool_audit_logs_v1.json"
    }
}
