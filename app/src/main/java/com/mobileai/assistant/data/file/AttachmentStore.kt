package com.mobileai.assistant.data.file

import android.content.Context
import com.mobileai.assistant.domain.model.Attachment
import com.mobileai.assistant.domain.model.AttachmentKind
import com.mobileai.assistant.domain.model.ChatMessage
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.util.Locale
import java.util.UUID

/**
 * Storage manager for chat attachments (images, text, documents).
 * Persists files in `<baseDir>/attachments/<uuid>_<sanitizedName>`.
 */
class AttachmentStore(
    context: Context? = null,
    rootDirOverride: File? = null
) {
    constructor(rootDir: File) : this(null, rootDir)

    private val baseDir: File = rootDirOverride
        ?: context?.filesDir
        ?: File(System.getProperty("java.io.tmpdir"), "mobileai_attachments")

    val attachmentsDir: File = if (baseDir.name == "attachments") {
        baseDir
    } else {
        File(baseDir, "attachments")
    }.apply {
        if (!exists()) mkdirs()
    }

    private val lock = Any()

    /**
     * Saves content from [inputStream] into the attachments directory.
     */
    fun saveAttachment(
        name: String,
        mimeType: String,
        sizeBytes: Long,
        inputStream: InputStream
    ): Attachment = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val sanitized = sanitizeFileName(name)
        val targetFile = File(attachmentsDir, "${id}_$sanitized")

        var bytesWritten = 0L
        FileOutputStream(targetFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                bytesWritten += bytesRead
            }
            output.flush()
        }

        val effectiveSize = if (bytesWritten > 0L) bytesWritten else sizeBytes.coerceAtLeast(0L)
        val kind = determineKind(sanitized, mimeType)
        val extractedText = if (kind == AttachmentKind.TEXT) {
            readTextFromFile(targetFile)
        } else {
            null
        }

        Attachment(
            id = id,
            uri = targetFile.toURI().toString(),
            fileName = sanitized,
            mimeType = mimeType,
            sizeBytes = effectiveSize,
            kind = kind,
            extractedText = extractedText
        )
    }

    /**
     * Copies an existing local [sourceFile] into the attachment store.
     */
    fun copyFileToAttachment(
        sourceFile: File,
        originalName: String? = null,
        mimeType: String? = null
    ): Attachment {
        require(sourceFile.exists()) { "Source file does not exist: ${sourceFile.absolutePath}" }
        val name = originalName ?: sourceFile.name
        val mime = mimeType ?: guessMimeType(name)
        val size = sourceFile.length()
        return sourceFile.inputStream().use { stream ->
            saveAttachment(name, mime, size, stream)
        }
    }

    /**
     * Resolves the backing [File] for an [attachment].
     */
    fun getAttachmentFile(attachment: Attachment): File = synchronized(lock) {
        // 1. Try finding by UUID prefix in attachmentsDir first (immune to absolute path shifts)
        val prefix = "${attachment.id}_"
        val matched = attachmentsDir.listFiles()?.firstOrNull { it.name.startsWith(prefix) }
        if (matched != null) return matched

        // 2. Try fileName directly in attachmentsDir
        val directFile = File(attachmentsDir, attachment.fileName)
        if (directFile.exists()) return directFile

        // 3. Resolve from URI
        return try {
            if (attachment.uri.startsWith("file:")) {
                File(URI.create(attachment.uri))
            } else {
                val candidate = File(attachment.uri)
                if (candidate.isAbsolute) candidate else File(attachmentsDir, attachment.uri)
            }
        } catch (_: Exception) {
            val stripped = attachment.uri.removePrefix("file://").removePrefix("file:")
            val fallback = File(stripped)
            if (fallback.isAbsolute) fallback else File(attachmentsDir, stripped)
        }
    }

    /**
     * Reads text content from a text/code attachment up to [MAX_TEXT_EXTRACT_BYTES].
     */
    fun readAttachmentText(attachment: Attachment): String? = synchronized(lock) {
        if (!isTextAttachment(attachment)) return null
        if (!attachment.extractedText.isNullOrBlank()) {
            return attachment.extractedText
        }
        val file = getAttachmentFile(attachment)
        return readTextFromFile(file)
    }

    /**
     * Deletes the attachment file with the given ID.
     */
    fun deleteAttachment(attachmentId: String): Boolean = synchronized(lock) {
        val prefix = "${attachmentId}_"
        var deleted = false
        attachmentsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix) || file.name == attachmentId) {
                if (file.delete()) {
                    deleted = true
                }
            }
        }
        deleted
    }

    /**
     * Deletes all attachments belonging to [message].
     */
    fun deleteAttachmentsForMessage(message: ChatMessage) {
        message.attachments.forEach { attachment ->
            deleteAttachment(attachment.id)
            val file = getAttachmentFile(attachment)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * Computes the total size in bytes of all stored attachments.
     */
    fun getTotalAttachmentSizeBytes(): Long = synchronized(lock) {
        attachmentsDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    /**
     * Deletes attachment files that are not referenced in [validAttachmentUris].
     * [validAttachmentUris] can contain attachment URIs or IDs.
     */
    fun cleanupOrphanedAttachments(validAttachmentUris: Set<String>): Int = synchronized(lock) {
        val files = attachmentsDir.listFiles() ?: return 0
        var deletedCount = 0
        for (file in files) {
            if (!file.isFile) continue
            val id = file.name.substringBefore('_')
            val fileUri = file.toURI().toString()

            val isKept = validAttachmentUris.any { valid ->
                valid == fileUri ||
                    valid == id ||
                    valid == file.name ||
                    valid.contains(file.name) ||
                    valid.contains(id)
            }

            if (!isKept && file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }

    private fun readTextFromFile(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(MAX_TEXT_EXTRACT_BYTES)
                val read = input.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isTextAttachment(attachment: Attachment): Boolean {
        if (attachment.kind == AttachmentKind.TEXT) return true
        if (isTextMime(attachment.mimeType)) return true
        val ext = attachment.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return TEXT_EXTENSIONS.contains(ext)
    }

    private fun determineKind(fileName: String, mimeType: String): AttachmentKind {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        if (lowerMime.startsWith("image/") || IMAGE_EXTENSIONS.contains(ext)) {
            return AttachmentKind.IMAGE
        }
        if (isTextMime(lowerMime) || TEXT_EXTENSIONS.contains(ext)) {
            return AttachmentKind.TEXT
        }
        return AttachmentKind.DOCUMENT
    }

    private fun isTextMime(mimeType: String): Boolean {
        val lower = mimeType.lowercase(Locale.ROOT)
        return lower.startsWith("text/") ||
            lower == "application/json" ||
            lower == "application/xml" ||
            lower == "application/javascript" ||
            lower == "application/x-yaml" ||
            lower == "application/yaml" ||
            lower == "application/sql" ||
            lower == "application/csv"
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            IMAGE_EXTENSIONS.contains(ext) -> {
                if (ext == "jpg") "image/jpeg" else "image/$ext"
            }
            ext == "json" -> "application/json"
            ext == "xml" -> "application/xml"
            ext == "csv" -> "text/csv"
            ext == "pdf" -> "application/pdf"
            ext == "md" || ext == "markdown" -> "text/markdown"
            TEXT_EXTENSIONS.contains(ext) -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
        return if (cleaned.isBlank()) "attachment" else cleaned
    }

    companion object {
        const val MAX_TEXT_EXTRACT_BYTES = 64 * 1024 // 64 KB

        private val IMAGE_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico"
        )

        private val TEXT_EXTENSIONS = setOf(
            "txt", "text", "md", "markdown", "json", "csv", "tsv", "xml", "html", "htm",
            "kt", "kts", "java", "py", "c", "cpp", "h", "hpp", "rs", "go", "js", "ts",
            "sh", "bash", "zsh", "yaml", "yml", "sql", "gradle", "properties", "log",
            "ini", "toml", "css", "scss", "sass", "php", "rb", "swift"
        )
    }
}
