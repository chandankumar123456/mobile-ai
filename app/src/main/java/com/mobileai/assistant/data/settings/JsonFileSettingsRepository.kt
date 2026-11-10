package com.mobileai.assistant.data.settings

import android.content.Context
import com.mobileai.assistant.domain.model.GenerationParams
import com.mobileai.assistant.domain.model.InferenceSettings
import com.mobileai.assistant.domain.model.ModelSettingsProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class JsonFileSettingsRepository internal constructor(
    private val rootDir: File,
    private val legacySettingsJson: String? = null
) : AppSettingsRepository {

    constructor(context: Context) : this(
        rootDir = context.filesDir,
        legacySettingsJson = readLegacySettings(context)
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val file = File(rootDir, FILE_NAME)
    private val tempFile = File(rootDir, "$FILE_NAME.tmp")
    private val saveLock = Any()

    private val _settings = MutableStateFlow(InferenceSettings.DEFAULT)
    override val settings: StateFlow<InferenceSettings> = _settings.asStateFlow()

    private val _profiles = MutableStateFlow<Map<String, ModelSettingsProfile>>(emptyMap())
    override val profiles: StateFlow<Map<String, ModelSettingsProfile>> = _profiles.asStateFlow()

    private val _lastLocalModelPath = MutableStateFlow<String?>(null)
    override val lastLocalModelPath: StateFlow<String?> = _lastLocalModelPath.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        rootDir.mkdirs()
        val loaded = readEnvelope()
        if (loaded != null) {
            _settings.value = loaded.settings
            _profiles.value = loaded.profiles
            _lastLocalModelPath.value = loaded.lastLocalModelPath
            return
        }

        _settings.value = decodeLegacy() ?: InferenceSettings.DEFAULT
        _profiles.value = emptyMap()
        _lastLocalModelPath.value = null
        persist()
    }

    private fun readEnvelope(): SettingsEnvelope? = try {
        if (file.exists() && file.length() > 0L) {
            json.decodeFromString<SettingsEnvelope>(file.readText())
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun decodeLegacy(): InferenceSettings? {
        val raw = legacySettingsJson ?: return null
        return try {
            if (raw.isBlank()) null else json.decodeFromString<InferenceSettings>(raw)
        } catch (_: Exception) {
            null
        }
    }

    override fun update(settings: InferenceSettings) {
        _settings.value = settings
        persist()
    }

    override fun profileFor(key: String): ModelSettingsProfile? = _profiles.value[key]

    override fun saveProfile(key: String, profile: ModelSettingsProfile) {
        _profiles.value = _profiles.value + (key to profile)
        persist()
    }

    override fun deleteProfile(key: String) {
        _profiles.value = _profiles.value - key
        persist()
    }

    override fun resolveParams(key: String): GenerationParams =
        profileFor(key)?.params ?: _settings.value.toGenerationParams()

    override fun setLastLocalModel(path: String?) {
        _lastLocalModelPath.value = path
        persist()
    }

    private fun persist() {
        val envelope = SettingsEnvelope(
            settings = _settings.value,
            profiles = _profiles.value,
            lastLocalModelPath = _lastLocalModelPath.value
        )
        synchronized(saveLock) {
            writeEnvelope(envelope)
        }
    }

    private fun writeEnvelope(envelope: SettingsEnvelope) {
        try {
            rootDir.mkdirs()
            val serialized = json.encodeToString(envelope)
            tempFile.writeText(serialized)
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            try {
                file.writeText(json.encodeToString(envelope))
            } catch (_: Exception) {
                // Non-critical persistence failure
            } finally {
                tempFile.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "settings_v2.json"
        const val LEGACY_PREFS_NAME = "app_inference_settings"
        const val LEGACY_KEY = "inference_settings"

        fun readLegacySettings(context: Context): String? = try {
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(LEGACY_KEY, null)
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
internal data class SettingsEnvelope(
    val version: Int = 2,
    val settings: InferenceSettings = InferenceSettings.DEFAULT,
    val profiles: Map<String, ModelSettingsProfile> = emptyMap(),
    val lastLocalModelPath: String? = null
)
