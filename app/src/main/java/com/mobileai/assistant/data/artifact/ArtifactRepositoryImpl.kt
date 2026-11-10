package com.mobileai.assistant.data.artifact

import android.content.Context
import com.mobileai.assistant.domain.artifact.ArtifactRepository
import com.mobileai.assistant.domain.artifact.ArtifactVersion
import com.mobileai.assistant.domain.artifact.WorkspaceArtifact
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
data class ArtifactStoreEnvelope(
    val version: Int = 1,
    val artifacts: List<WorkspaceArtifact> = emptyList(),
    val versions: List<ArtifactVersion> = emptyList()
)

/**
 * Local-first, file-backed atomic implementation of [ArtifactRepository].
 * Stores artifacts and historical version snapshots in `artifacts_v1.json`.
 */
class ArtifactRepositoryImpl(
    private val context: Context? = null,
    rootDirOverride: File? = null
) : ArtifactRepository {

    internal constructor(rootDir: File) : this(null, rootDir)

    private val rootDir: File? = rootDirOverride ?: context?.filesDir

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val saveLock = Any()

    private val _artifacts = MutableStateFlow<List<WorkspaceArtifact>>(emptyList())
    override val artifacts: StateFlow<List<WorkspaceArtifact>> = _artifacts.asStateFlow()

    private val _versions = MutableStateFlow<List<ArtifactVersion>>(emptyList())

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        synchronized(saveLock) {
            val dir = rootDir
            val file = dir?.let { File(it, FILE_ARTIFACTS) }
            if (file != null && file.exists() && file.length() > 0) {
                try {
                    val envelope = json.decodeFromString<ArtifactStoreEnvelope>(file.readText())
                    _artifacts.value = envelope.artifacts
                    _versions.value = envelope.versions
                } catch (_: Exception) {
                    _artifacts.value = emptyList()
                    _versions.value = emptyList()
                }
            } else {
                _artifacts.value = emptyList()
                _versions.value = emptyList()
            }
        }
    }

    private fun saveToDiskLocked() {
        val dir = rootDir ?: return
        try {
            if (!dir.exists()) dir.mkdirs()
            val serialized = json.encodeToString(
                ArtifactStoreEnvelope(
                    version = 1,
                    artifacts = _artifacts.value,
                    versions = _versions.value
                )
            )
            val file = File(dir, FILE_ARTIFACTS)
            val tempFile = File(dir, "$FILE_ARTIFACTS.tmp")
            tempFile.writeText(serialized)
            if (!tempFile.renameTo(file)) {
                file.writeText(serialized)
                tempFile.delete()
            }
        } catch (_: Exception) {
            // Non-critical persistence failure
        }
    }

    override fun getArtifactsForDot(dotId: String): Flow<List<WorkspaceArtifact>> {
        return _artifacts.map { list ->
            list.filter { it.dotId == dotId }.sortedByDescending { it.updatedAt }
        }
    }

    override fun getArtifactsForConversation(conversationId: String): Flow<List<WorkspaceArtifact>> {
        return _artifacts.map { list ->
            list.filter { it.conversationId == conversationId }.sortedByDescending { it.updatedAt }
        }
    }

    override fun getArtifact(id: String): Flow<WorkspaceArtifact?> {
        return _artifacts.map { list -> list.find { it.id == id } }
    }

    override suspend fun getArtifactById(id: String): WorkspaceArtifact? {
        return _artifacts.value.find { it.id == id }
    }

    override suspend fun saveArtifact(artifact: WorkspaceArtifact): Result<Unit> {
        return try {
            synchronized(saveLock) {
                val current = _artifacts.value
                val existingIndex = current.indexOfFirst { it.id == artifact.id }

                if (existingIndex >= 0) {
                    val updated = artifact.copy(updatedAt = System.currentTimeMillis())
                    val updatedList = current.toMutableList().apply { set(existingIndex, updated) }
                    _artifacts.value = updatedList
                } else {
                    _artifacts.value = current + artifact
                    // Automatically record initial version if not yet recorded
                    val hasVersion = _versions.value.any { it.artifactId == artifact.id && it.version == artifact.version }
                    if (!hasVersion) {
                        val initialVersion = ArtifactVersion(
                            artifactId = artifact.id,
                            version = artifact.version,
                            content = artifact.content,
                            changeSummary = "Initial creation",
                            timestamp = artifact.createdAt
                        )
                        _versions.value = _versions.value + initialVersion
                    }
                }
                saveToDiskLocked()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateArtifact(
        id: String,
        content: String,
        changeSummary: String?
    ): Result<WorkspaceArtifact> {
        return try {
            synchronized(saveLock) {
                val current = _artifacts.value
                val existingIndex = current.indexOfFirst { it.id == id }
                if (existingIndex < 0) {
                    return Result.failure(IllegalArgumentException("Artifact not found with id: $id"))
                }

                val existing = current[existingIndex]
                val nextVersion = existing.version + 1
                val timestamp = System.currentTimeMillis()

                val updatedArtifact = existing.copy(
                    content = content,
                    version = nextVersion,
                    updatedAt = timestamp
                )

                val newVersionEntry = ArtifactVersion(
                    artifactId = id,
                    version = nextVersion,
                    content = content,
                    changeSummary = changeSummary,
                    timestamp = timestamp
                )

                _artifacts.value = current.toMutableList().apply { set(existingIndex, updatedArtifact) }
                _versions.value = _versions.value + newVersionEntry

                saveToDiskLocked()
                Result.success(updatedArtifact)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteArtifact(id: String): Result<Unit> {
        return try {
            synchronized(saveLock) {
                _artifacts.value = _artifacts.value.filterNot { it.id == id }
                _versions.value = _versions.value.filterNot { it.artifactId == id }
                saveToDiskLocked()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getArtifactVersions(artifactId: String): Flow<List<ArtifactVersion>> {
        return _versions.map { list ->
            list.filter { it.artifactId == artifactId }.sortedBy { it.version }
        }
    }

    override suspend fun getArtifactVersionsList(artifactId: String): List<ArtifactVersion> {
        return _versions.value.filter { it.artifactId == artifactId }.sortedBy { it.version }
    }

    companion object {
        const val FILE_ARTIFACTS = "artifacts_v1.json"
    }
}
