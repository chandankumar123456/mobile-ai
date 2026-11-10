package com.mobileai.assistant.domain.artifact

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing workspace document artifacts and their version histories.
 */
interface ArtifactRepository {
    val artifacts: Flow<List<WorkspaceArtifact>>

    fun getArtifactsForDot(dotId: String): Flow<List<WorkspaceArtifact>>
    fun getArtifactsForConversation(conversationId: String): Flow<List<WorkspaceArtifact>>
    fun getArtifact(id: String): Flow<WorkspaceArtifact?>
    suspend fun getArtifactById(id: String): WorkspaceArtifact?

    suspend fun saveArtifact(artifact: WorkspaceArtifact): Result<Unit>
    suspend fun updateArtifact(id: String, content: String, changeSummary: String? = null): Result<WorkspaceArtifact>
    suspend fun deleteArtifact(id: String): Result<Unit>

    fun getArtifactVersions(artifactId: String): Flow<List<ArtifactVersion>>
    suspend fun getArtifactVersionsList(artifactId: String): List<ArtifactVersion>
}
