package com.mobileai.assistant.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AttachmentKind { IMAGE, DOCUMENT, TEXT }

@Serializable
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: AttachmentKind = AttachmentKind.DOCUMENT,
    val extractedText: String? = null
)
