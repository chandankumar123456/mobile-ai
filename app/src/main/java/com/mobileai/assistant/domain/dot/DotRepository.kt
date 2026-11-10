package com.mobileai.assistant.domain.dot

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing persistent Dot profiles and their long-term memories.
 */
interface DotRepository {
    val dots: Flow<List<DotProfile>>
    val activeDot: Flow<DotProfile?>

    fun getDot(id: String): Flow<DotProfile?>
    suspend fun getDotById(id: String): DotProfile?
    suspend fun getActiveDot(): DotProfile?
    suspend fun selectActiveDot(id: String)

    suspend fun saveDot(dot: DotProfile): Result<Unit>
    suspend fun createDot(dot: DotProfile): Result<Unit> = saveDot(dot)
    suspend fun updateDot(dot: DotProfile): Result<Unit> = saveDot(dot)
    suspend fun deleteDot(id: String): Result<Unit>

    // Memory operations
    fun getMemories(dotId: String): Flow<List<DotMemory>>
    suspend fun getMemoriesList(dotId: String): List<DotMemory>
    suspend fun saveMemory(memory: DotMemory): Result<Unit>
    suspend fun addMemory(memory: DotMemory): Result<Unit> = saveMemory(memory)
    suspend fun deleteMemory(id: String): Result<Unit>
    suspend fun clearMemories(dotId: String): Result<Unit>
}
