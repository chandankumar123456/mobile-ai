package com.mobileai.assistant.domain.usecase

import android.net.Uri
import com.mobileai.assistant.domain.model.ModelInfo
import com.mobileai.assistant.domain.repository.ModelRepository

class ImportModelUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(
        uri: Uri,
        onProgress: (progressFraction: Float, statusMessage: String) -> Unit
    ): Result<ModelInfo> {
        return repository.importModelFromUri(uri, onProgress)
    }
}

