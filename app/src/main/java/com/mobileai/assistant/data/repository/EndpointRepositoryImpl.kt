package com.mobileai.assistant.data.repository

import android.content.Context
import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import com.mobileai.assistant.domain.repository.EndpointRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class EndpointRepositoryImpl(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : EndpointRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val storageFile: File by lazy {
        File(context.filesDir, "remote_endpoints.json")
    }

    private val activePrefFile by lazy {
        context.getSharedPreferences("mobile_ai_endpoints", Context.MODE_PRIVATE)
    }

    private val _endpoints = MutableStateFlow<List<RemoteEndpointConfig>>(emptyList())
    override val endpoints: StateFlow<List<RemoteEndpointConfig>> = _endpoints.asStateFlow()

    private val _activeEndpoint = MutableStateFlow<RemoteEndpointConfig?>(null)
    override val activeEndpoint: StateFlow<RemoteEndpointConfig?> = _activeEndpoint.asStateFlow()

    init {
        loadEndpointsFromDisk()
    }

    private fun loadEndpointsFromDisk() {
        try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                if (content.isNotBlank()) {
                    val list = json.decodeFromString<List<RemoteEndpointConfig>>(content)
                    _endpoints.value = list
                    val activeId = activePrefFile.getString(KEY_ACTIVE_ID, null)
                    _activeEndpoint.value = list.firstOrNull { it.id == activeId }
                }
            }
        } catch (e: Exception) {
            _endpoints.value = emptyList()
        }
    }

    override suspend fun saveEndpoint(endpoint: RemoteEndpointConfig) = withContext(dispatcher) {
        _endpoints.update { current ->
            val index = current.indexOfFirst { it.id == endpoint.id }
            val updated = if (index != -1) {
                current.toMutableList().apply { set(index, endpoint) }
            } else {
                current + endpoint
            }
            saveToDisk(updated)
            updated
        }
        if (_activeEndpoint.value?.id == endpoint.id) {
            _activeEndpoint.value = endpoint
        }
    }

    override suspend fun deleteEndpoint(id: String) = withContext(dispatcher) {
        _endpoints.update { current ->
            val updated = current.filterNot { it.id == id }
            saveToDisk(updated)
            updated
        }
        if (_activeEndpoint.value?.id == id) {
            setActiveEndpoint(null)
        }
    }

    override suspend fun setActiveEndpoint(endpoint: RemoteEndpointConfig?) = withContext(dispatcher) {
        _activeEndpoint.value = endpoint
        activePrefFile.edit().putString(KEY_ACTIVE_ID, endpoint?.id).apply()
    }

    override suspend fun getEndpoints(): List<RemoteEndpointConfig> = withContext(dispatcher) {
        _endpoints.value
    }

    private fun saveToDisk(list: List<RemoteEndpointConfig>) {
        try {
            val content = json.encodeToString(list)
            storageFile.writeText(content)
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_ACTIVE_ID = "active_endpoint_id"
    }
}

