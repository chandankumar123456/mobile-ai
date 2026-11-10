package com.mobileai.assistant.domain.repository

import com.mobileai.assistant.domain.model.RemoteEndpointConfig
import kotlinx.coroutines.flow.StateFlow

interface EndpointRepository {
    val endpoints: StateFlow<List<RemoteEndpointConfig>>
    val activeEndpoint: StateFlow<RemoteEndpointConfig?>

    suspend fun saveEndpoint(endpoint: RemoteEndpointConfig)
    suspend fun deleteEndpoint(id: String)
    suspend fun setActiveEndpoint(endpoint: RemoteEndpointConfig?)
    suspend fun getEndpoints(): List<RemoteEndpointConfig>
}

