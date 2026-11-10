package com.mobileai.assistant.di

import com.mobileai.assistant.domain.engine.LocalInferenceEngine
import com.mobileai.assistant.domain.repository.ModelRepository
import com.mobileai.assistant.domain.usecase.CancelGenerationUseCase
import com.mobileai.assistant.domain.usecase.DeleteModelUseCase
import com.mobileai.assistant.domain.usecase.GenerateResponseUseCase
import com.mobileai.assistant.domain.usecase.GetImportedModelsUseCase
import com.mobileai.assistant.domain.usecase.ImportModelUseCase
import com.mobileai.assistant.domain.usecase.LoadModelUseCase
import com.mobileai.assistant.domain.usecase.UnloadModelUseCase

/**
 * Dependency injection module for domain use cases.
 */
class UseCaseModule(
    private val localEngine: LocalInferenceEngine,
    private val modelRepository: ModelRepository
) {
    val generateResponseUseCase: GenerateResponseUseCase by lazy { GenerateResponseUseCase(localEngine) }
    val cancelGenerationUseCase: CancelGenerationUseCase by lazy { CancelGenerationUseCase(localEngine) }
    val loadModelUseCase: LoadModelUseCase by lazy { LoadModelUseCase(localEngine) }
    val unloadModelUseCase: UnloadModelUseCase by lazy { UnloadModelUseCase(localEngine) }
    val importModelUseCase: ImportModelUseCase by lazy { ImportModelUseCase(modelRepository) }
    val getImportedModelsUseCase: GetImportedModelsUseCase by lazy { GetImportedModelsUseCase(modelRepository) }
    val deleteModelUseCase: DeleteModelUseCase by lazy { DeleteModelUseCase(modelRepository) }
}

