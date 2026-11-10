package com.mobileai.assistant.data.repository

import android.net.Uri
import com.mobileai.assistant.data.file.ModelFileManager
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelRepositoryImpl(
    private val fileManager: ModelFileManager
) : ModelRepository {

    private val _importedModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val importedModels: StateFlow<List<ModelInfo>> = _importedModels.asStateFlow()

    override suspend fun refreshModels() {
        _importedModels.value = fileManager.listImportedModels()
    }

    override suspend fun importModelFromUri(
        uri: Uri,
        onProgress: (progressFraction: Float, statusMessage: String) -> Unit
    ): Result<ModelInfo> {
        val result = fileManager.importFromUri(uri, onProgress)
        if (result.isSuccess) {
            refreshModels()
        }
        return result
    }

    override suspend fun deleteModel(modelInfo: ModelInfo): Result<Unit> {
        val result = fileManager.deleteModelFile(modelInfo)
        if (result.isSuccess) {
            refreshModels()
        }
        return result
    }
}

