package com.mobileai.assistant.domain.repository

import android.net.Uri
import com.mobileai.assistant.domain.model.ModelInfo
import kotlinx.coroutines.flow.StateFlow

interface ModelRepository {
    val importedModels: StateFlow<List<ModelInfo>>

    suspend fun refreshModels()
    suspend fun importModelFromUri(
        uri: Uri,
        onProgress: (progressFraction: Float, statusMessage: String) -> Unit
    ): Result<ModelInfo>
    suspend fun deleteModel(modelInfo: ModelInfo): Result<Unit>
}

