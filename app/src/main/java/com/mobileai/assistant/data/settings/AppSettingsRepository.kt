package com.mobileai.assistant.data.settings

import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

interface AppSettingsRepository {

    val settings: StateFlow<InferenceSettings>
    val profiles: StateFlow<Map<String, ModelSettingsProfile>>
    val lastLocalModelPath: StateFlow<String?>

    fun update(settings: InferenceSettings)
    fun profileFor(key: String): ModelSettingsProfile?
    fun saveProfile(key: String, profile: ModelSettingsProfile)
    fun deleteProfile(key: String)
    fun resolveParams(key: String): GenerationParams
    fun setLastLocalModel(path: String?)

    companion object {
        fun modelKeyLocal(path: String): String {
            val normalized = path.trim().replace('\\', '/')
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return "local:$hex"
        }

        fun modelKeyRemote(endpointId: String, modelId: String): String =
            "remote:$endpointId:$modelId"
    }
}
