package com.mobileai.assistant.presentation.chat.coordinator

import com.mobileai.assistant.domain.dot.DotMemory
import com.mobileai.assistant.domain.dot.DotProfile
import com.mobileai.assistant.domain.dot.DotPromptBuilder
import com.mobileai.assistant.domain.dot.DotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinator responsible for Dot persona lifecycle, active Dot switching,
 * memory isolation, and dynamic context injection.
 */
class DotSessionCoordinator(
    private val dotRepository: DotRepository?,
    private val scope: CoroutineScope
) {
    private val _activeDot = MutableStateFlow<DotProfile>(DotProfile.STARTER_PROFILES.first())
    val activeDot: StateFlow<DotProfile> = _activeDot.asStateFlow()

    private val _availableDots = MutableStateFlow<List<DotProfile>>(DotProfile.STARTER_PROFILES)
    val availableDots: StateFlow<List<DotProfile>> = _availableDots.asStateFlow()

    init {
        if (dotRepository != null) {
            scope.launch {
                dotRepository.dots.collectLatest { dotsList ->
                    if (dotsList.isNotEmpty()) {
                        _availableDots.value = dotsList
                        // If current active dot was updated or deleted, reconcile
                        val current = _activeDot.value
                        val updated = dotsList.find { it.id == current.id } ?: dotsList.first()
                        _activeDot.value = updated
                    }
                }
            }
        }
    }

    fun selectDot(dot: DotProfile) {
        _activeDot.value = dot
        scope.launch {
            dotRepository?.selectActiveDot(dot.id)
        }
    }

    /**
     * Retrieves actual persisted memories scoped exclusively to the active Dot.
     * Prevents cross-Dot memory leakage and ensures active memories are injected into model context.
     */
    suspend fun getActiveDotMemories(): List<DotMemory> {
        val repo = dotRepository ?: return emptyList()
        val currentDotId = _activeDot.value.id
        return repo.getMemoriesList(currentDotId).filter { it.dotId == currentDotId }
    }

    /**
     * Builds the complete model prompt for the active Dot, including actual retrieved memories.
     */
    suspend fun buildActiveSystemPrompt(): String {
        val currentDot = _activeDot.value
        val memories = getActiveDotMemories()
        return DotPromptBuilder.buildSystemPrompt(currentDot, memories)
    }

    suspend fun saveMemory(memory: DotMemory): Result<Unit> {
        val repo = dotRepository ?: return Result.failure(IllegalStateException("DotRepository not initialized"))
        // Defensively enforce active Dot ID to prevent cross-Dot memory injection
        val scopedMemory = memory.copy(dotId = _activeDot.value.id)
        return repo.saveMemory(scopedMemory)
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        val repo = dotRepository ?: return Result.failure(IllegalStateException("DotRepository not initialized"))
        return repo.deleteMemory(memoryId)
    }
}
