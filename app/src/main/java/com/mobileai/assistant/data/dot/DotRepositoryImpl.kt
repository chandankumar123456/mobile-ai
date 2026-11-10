package com.mobileai.assistant.data.dot

import android.content.Context
import com.mobileai.assistant.domain.dot.DotMemory
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.dot.DotRepository
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
data class DotStoreEnvelope(
    val version: Int = 1,
    val activeDotId: String? = null,
    val dots: List<DotProfile> = emptyList()
)

@Serializable
data class DotMemoryStoreEnvelope(
    val version: Int = 1,
    val memories: List<DotMemory> = emptyList()
)

/**
 * Local-first, file-backed atomic implementation of [DotRepository].
 * Stores profiles in `dots_v1.json` and memories in `dot_memories_v1.json`.
 */
class DotRepositoryImpl(
    private val context: Context? = null,
    rootDirOverride: File? = null
) : DotRepository {

    internal constructor(rootDir: File) : this(null, rootDir)

    private val rootDir: File? = rootDirOverride ?: context?.filesDir

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val saveLock = Any()

    private val _dots = MutableStateFlow<List<DotProfile>>(emptyList())
    override val dots: StateFlow<List<DotProfile>> = _dots.asStateFlow()

    private val _activeDot = MutableStateFlow<DotProfile?>(null)
    override val activeDot: StateFlow<DotProfile?> = _activeDot.asStateFlow()

    private val _memories = MutableStateFlow<List<DotMemory>>(emptyList())

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        synchronized(saveLock) {
            loadDotsLocked()
            loadMemoriesLocked()
        }
    }

    private fun loadDotsLocked() {
        val dir = rootDir
        val file = dir?.let { File(it, FILE_DOTS) }
        var loadedDots: List<DotProfile> = emptyList()
        var preferredActiveId: String? = null

        if (file != null && file.exists() && file.length() > 0) {
            try {
                val envelope = json.decodeFromString<DotStoreEnvelope>(file.readText())
                loadedDots = envelope.dots
                preferredActiveId = envelope.activeDotId
            } catch (_: Exception) {
                // Corrupted file, fallback to default seeding
            }
        }

        if (loadedDots.isEmpty()) {
            loadedDots = DotProfile.STARTER_PROFILES
            preferredActiveId = DotProfile.BUILTIN_GENERAL_ID
            _dots.value = loadedDots
            _activeDot.value = loadedDots.firstOrNull { it.id == preferredActiveId } ?: loadedDots.firstOrNull()
            saveDotsLocked()
        } else {
            // Guarantee that any missing built-in starter profiles are preserved
            val existingIds = loadedDots.map { it.id }.toSet()
            val missingStarters = DotProfile.STARTER_PROFILES.filter { it.id !in existingIds }
            val completeList = if (missingStarters.isNotEmpty()) loadedDots + missingStarters else loadedDots

            _dots.value = completeList
            _activeDot.value = completeList.firstOrNull { it.id == preferredActiveId }
                ?: completeList.firstOrNull { it.id == DotProfile.BUILTIN_GENERAL_ID }
                ?: completeList.firstOrNull()

            if (missingStarters.isNotEmpty() || preferredActiveId != _activeDot.value?.id) {
                saveDotsLocked()
            }
        }
    }

    private fun loadMemoriesLocked() {
        val dir = rootDir
        val file = dir?.let { File(it, FILE_MEMORIES) }
        if (file != null && file.exists() && file.length() > 0) {
            try {
                val envelope = json.decodeFromString<DotMemoryStoreEnvelope>(file.readText())
                _memories.value = envelope.memories
            } catch (_: Exception) {
                _memories.value = emptyList()
            }
        } else {
            _memories.value = emptyList()
        }
    }

    private fun saveDotsLocked() {
        val dir = rootDir ?: return
        try {
            if (!dir.exists()) dir.mkdirs()
            val serialized = json.encodeToString(
                DotStoreEnvelope(
                    version = 1,
                    activeDotId = _activeDot.value?.id,
                    dots = _dots.value
                )
            )
            val file = File(dir, FILE_DOTS)
            val tempFile = File(dir, "$FILE_DOTS.tmp")
            tempFile.writeText(serialized)
            if (!tempFile.renameTo(file)) {
                file.writeText(serialized)
                tempFile.delete()
            }
        } catch (_: Exception) {
            // Non-critical persistence failure
        }
    }

    private fun saveMemoriesLocked() {
        val dir = rootDir ?: return
        try {
            if (!dir.exists()) dir.mkdirs()
            val serialized = json.encodeToString(
                DotMemoryStoreEnvelope(
                    version = 1,
                    memories = _memories.value
                )
            )
            val file = File(dir, FILE_MEMORIES)
            val tempFile = File(dir, "$FILE_MEMORIES.tmp")
            tempFile.writeText(serialized)
            if (!tempFile.renameTo(file)) {
                file.writeText(serialized)
                tempFile.delete()
            }
        } catch (_: Exception) {
            // Non-critical persistence failure
        }
    }

    override fun getDot(id: String): Flow<DotProfile?> {
        return _dots.map { list -> list.find { it.id == id } }
    }

    override suspend fun getDotById(id: String): DotProfile? {
        return _dots.value.find { it.id == id }
    }

    override suspend fun getActiveDot(): DotProfile? {
        return _activeDot.value
    }

    override suspend fun selectActiveDot(id: String) {
        synchronized(saveLock) {
            val target = _dots.value.find { it.id == id } ?: return
            _activeDot.value = target
            saveDotsLocked()
        }
    }

    override suspend fun saveDot(dot: DotProfile): Result<Unit> {
        return try {
            synchronized(saveLock) {
                val current = _dots.value
                val existingIndex = current.indexOfFirst { it.id == dot.id }
                val updatedDot = dot.copy(updatedAt = System.currentTimeMillis())

                val updatedList = if (existingIndex >= 0) {
                    current.toMutableList().apply { set(existingIndex, updatedDot) }
                } else {
                    current + updatedDot
                }
                _dots.value = updatedList

                if (_activeDot.value?.id == dot.id) {
                    _activeDot.value = updatedDot
                }
                saveDotsLocked()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDot(id: String): Result<Unit> {
        return try {
            synchronized(saveLock) {
                val current = _dots.value
                val target = current.find { it.id == id }
                    ?: return Result.failure(IllegalArgumentException("Dot with id $id does not exist"))

                if (target.isBuiltIn) {
                    return Result.failure(IllegalStateException("Cannot delete built-in Dot profile: ${target.name}"))
                }

                _dots.value = current.filterNot { it.id == id }

                // Cascade delete associated memories
                _memories.value = _memories.value.filterNot { it.dotId == id }

                // If active dot was deleted, fallback to builtin-general or first available
                if (_activeDot.value?.id == id) {
                    _activeDot.value = _dots.value.firstOrNull { it.id == DotProfile.BUILTIN_GENERAL_ID }
                        ?: _dots.value.firstOrNull()
                }

                saveDotsLocked()
                saveMemoriesLocked()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMemories(dotId: String): Flow<List<DotMemory>> {
        return _memories.map { list -> list.filter { it.dotId == dotId } }
    }

    override suspend fun getMemoriesList(dotId: String): List<DotMemory> {
        return _memories.value.filter { it.dotId == dotId }
    }

    override suspend fun saveMemory(memory: DotMemory): Result<Unit> {
        return try {
            synchronized(saveLock) {
                val current = _memories.value
                val isNamedKey = memory.key.isNotBlank()
                val existingIndex = current.indexOfFirst { existing ->
                    existing.id == memory.id || (
                        isNamedKey &&
                        existing.dotId == memory.dotId &&
                        existing.key.trim().equals(memory.key.trim(), ignoreCase = true)
                    )
                }
                val updatedMemory = memory.copy(updatedAt = System.currentTimeMillis())

                val updatedList = if (existingIndex >= 0) {
                    current.toMutableList().apply { set(existingIndex, updatedMemory) }
                } else {
                    current + updatedMemory
                }
                _memories.value = updatedList
                saveMemoriesLocked()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMemory(id: String): Result<Unit> {
        return try {
            synchronized(saveLock) {
                val current = _memories.value
                _memories.value = current.filterNot { it.id == id }
                saveMemoriesLocked()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearMemories(dotId: String): Result<Unit> {
        return try {
            synchronized(saveLock) {
                _memories.value = _memories.value.filterNot { it.dotId == dotId }
                saveMemoriesLocked()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val FILE_DOTS = "dots_v1.json"
        const val FILE_MEMORIES = "dot_memories_v1.json"
    }
}
