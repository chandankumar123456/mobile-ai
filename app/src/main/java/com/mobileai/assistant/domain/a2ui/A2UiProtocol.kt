package com.mobileai.assistant.domain.a2ui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Supported Google A2UI Protocol Version: v0.8 (Specification: https://github.com/a2ui-project/a2ui)
 */
object A2UiProtocol {
    const val VERSION = "v0.8"
}

/**
 * Flat component definition transmitted within an A2UI `surfaceUpdate` message.
 */
@Serializable
data class A2UiComponentDefinition(
    val id: String,
    val component: String,
    val properties: Map<String, String> = emptyMap(),
    val children: List<String> = emptyList()
)

/**
 * Resolved hierarchical tree node ready for Jetpack Compose rendering.
 */
data class A2UiResolvedNode(
    val id: String,
    val component: String,
    val properties: Map<String, String>,
    val children: List<A2UiResolvedNode> = emptyList()
)

/**
 * Root polymorphic sealed interface for JSONL streaming A2UI messages.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface A2UiMessage {

    /**
     * Updates or defines the component layout of a surface.
     */
    @Serializable
    @SerialName("surfaceUpdate")
    data class SurfaceUpdate(
        val surfaceId: String,
        val catalogId: String? = null,
        val root: String,
        val components: List<A2UiComponentDefinition> = emptyList()
    ) : A2UiMessage

    /**
     * Incrementally updates the reactive data model of a surface.
     */
    @Serializable
    @SerialName("dataModelUpdate")
    data class DataModelUpdate(
        val surfaceId: String,
        val path: String? = null,
        val data: Map<String, String> = emptyMap()
    ) : A2UiMessage

    /**
     * User interaction event dispatched from an A2UI component.
     */
    @Serializable
    @SerialName("action")
    data class Action(
        val surfaceId: String,
        val actionName: String,
        val parameters: Map<String, String> = emptyMap()
    ) : A2UiMessage

    /**
     * Deletes a surface and releases its resources.
     */
    @Serializable
    @SerialName("deleteSurface")
    data class DeleteSurface(
        val surfaceId: String
    ) : A2UiMessage
}

