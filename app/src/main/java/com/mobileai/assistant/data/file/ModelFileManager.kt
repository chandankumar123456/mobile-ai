package com.mobileai.assistant.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.arm.aichat.gguf.FileType
import com.arm.aichat.gguf.GgufMetadata
import com.arm.aichat.gguf.GgufMetadataReader
import com.mobileai.assistant.domain.model.ModelCapabilitiesResolver
import com.mobileai.assistant.domain.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages model files in the app-private storage and interacts with the GGUF metadata parser.
 */
class ModelFileManager(
    private val context: Context? = null,
    private val rootDir: File = context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "mobileai_models")
) {

    private val reader: GgufMetadataReader = GgufMetadataReader.create()

    private val modelsDir: File
        get() = File(rootDir, MODELS_DIRECTORY_NAME).apply {
            if (exists() && !isDirectory) {
                delete()
            }
            if (!exists()) {
                mkdirs()
            }
        }

    /**
     * Calculates the total storage in bytes occupied by all imported GGUF models.
     */
    fun getTotalModelStorageBytes(): Long {
        val files = modelsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".gguf", ignoreCase = true)
        } ?: return 0L
        return files.sumOf { it.length() }
    }

    /**
     * Returns the available storage in bytes on internal storage.
     */
    fun getAvailableStorageBytes(): Long {
        return rootDir.usableSpace
    }

    /**
     * Calculates the total storage in bytes occupied by attachments.
     */
    fun getAttachmentStorageBytes(): Long {
        val attachmentsDir = File(rootDir, ATTACHMENTS_DIRECTORY_NAME)
        if (!attachmentsDir.exists() || !attachmentsDir.isDirectory) return 0L
        return attachmentsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Lists all GGUF models stored in app-private directory.
     */
    suspend fun listImportedModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val files = modelsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".gguf", ignoreCase = true)
        } ?: emptyArray()

        files.map { file ->
            readMetadataFromFile(file) ?: ModelInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeBytes = file.length()
            )
        }
    }

    /**
     * Reads GGUF metadata directly from an app-private file.
     */
    suspend fun readMetadataFromFile(file: File): ModelInfo? = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { stream ->
                val meta = reader.readStructuredMetadata(stream)
                mapMetadataToModelInfo(file.name, file.absolutePath, file.length(), meta)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse GGUF metadata from ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Inspects a GGUF file from a Content URI before importing.
     */
    suspend fun inspectUriMetadata(uri: Uri): GgufMetadata? = withContext(Dispatchers.IO) {
        try {
            val ctx = context ?: return@withContext null
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                reader.readStructuredMetadata(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GGUF from URI $uri", e)
            null
        }
    }

    /**
     * Imports a GGUF model from a SAF [Uri] into the app-private storage.
     * Reports copy progress and status updates.
     */
    suspend fun importFromUri(
        uri: Uri,
        onProgress: (progressFraction: Float, statusMessage: String) -> Unit
    ): Result<ModelInfo> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext Result.failure(IOException("Context is null"))
        try {
            onProgress(0.05f, "Inspecting model file...")

            // Extract file name and size from SAF ContentResolver
            val originalName = queryFileName(uri) ?: "model_${System.currentTimeMillis()}.gguf"
            val totalBytes = queryFileSize(uri)

            // Check usable internal storage space with a 100MB safety buffer
            if (totalBytes > 0) {
                val availableSpace = rootDir.usableSpace
                val requiredSpace = totalBytes + (100L * 1024L * 1024L)
                if (availableSpace < requiredSpace) {
                    val availableMB = availableSpace / (1024 * 1024)
                    val requiredMB = requiredSpace / (1024 * 1024)
                    return@withContext Result.failure(
                        IOException("Insufficient storage: requires ~$requiredMB MB, but only ~$availableMB MB available.")
                    )
                }
            }

            // Verify GGUF format magic bytes
            val isValidGguf = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                reader.ensureSourceFileFormat(ctx, uri)
            } ?: false

            if (!isValidGguf) {
                return@withContext Result.failure(
                    IOException("Selected file is not a valid GGUF model format.")
                )
            }

            onProgress(0.15f, "Reading model metadata...")
            val metadata = inspectUriMetadata(uri)

            val finalFileName = if (originalName.endsWith(".gguf", ignoreCase = true)) {
                originalName
            } else {
                "$originalName.gguf"
            }

            val targetFile = File(modelsDir, finalFileName)
            if (targetFile.exists()) {
                Log.i(TAG, "Model $finalFileName already exists, reusing existing file.")
                onProgress(1.0f, "Model already imported.")
                val info = mapMetadataToModelInfo(
                    finalFileName,
                    targetFile.absolutePath,
                    targetFile.length(),
                    metadata
                )
                return@withContext Result.success(info)
            }

            onProgress(0.20f, "Importing model into app storage...")

            // Copy with byte-level progress reporting
            val inputStream = ctx.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("Failed to open input stream for $uri"))

            val tempFile = File(modelsDir, "${finalFileName}.tmp")
            try {
                var bytesCopied = 0L
                val buffer = ByteArray(BUFFER_SIZE)

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalBytes > 0) {
                                val progress = 0.20f + (bytesCopied.toFloat() / totalBytes.toFloat()) * 0.75f
                                val copiedMB = bytesCopied / (1024 * 1024)
                                val totalMB = totalBytes / (1024 * 1024)
                                onProgress(progress.coerceAtMost(0.95f), "Importing: $copiedMB / $totalMB MB")
                            }
                        }
                        output.flush()
                    }
                }

                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Failed to rename temporary file to ${targetFile.name}")
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }

            onProgress(1.0f, "Model imported successfully!")
            val finalModelInfo = mapMetadataToModelInfo(
                targetFile.name,
                targetFile.absolutePath,
                targetFile.length(),
                metadata
            )
            Result.success(finalModelInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model from $uri", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a model file from app-private storage.
     */
    suspend fun deleteModelFile(modelInfo: ModelInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(modelInfo.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) Result.success(Unit)
                else Result.failure(IOException("Failed to delete ${file.name}"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapMetadataToModelInfo(
        fileName: String,
        filePath: String,
        fileSizeBytes: Long,
        meta: GgufMetadata?
    ): ModelInfo {
        if (meta == null) {
            val raw = ModelInfo(
                fileName = fileName,
                filePath = filePath,
                fileSizeBytes = fileSizeBytes
            )
            return raw.copy(capabilities = ModelCapabilitiesResolver.fromLocal(raw))
        }

        val quantType = meta.architecture?.fileType?.let { FileType.fromCode(it).label }
        val arch = meta.architecture?.architecture ?: meta.basic.name
        val contextLen = meta.dimensions?.contextLength ?: meta.rope?.originalContextLength
        val chatTemplate = meta.tokenizer?.chatTemplate
        val tags = meta.additional?.tags ?: emptyList()
        val languages = meta.additional?.languages ?: emptyList()

        val raw = ModelInfo(
            fileName = fileName,
            filePath = filePath,
            fileSizeBytes = fileSizeBytes,
            architecture = arch,
            quantization = quantType,
            contextLength = contextLen,
            tensorCount = meta.tensorCount,
            kvCount = meta.kvCount,
            author = meta.author?.author ?: meta.author?.organization,
            description = meta.additional?.description,
            chatTemplate = chatTemplate,
            tags = tags,
            languages = languages
        )

        val capabilities = ModelCapabilitiesResolver.fromLocal(
            modelInfo = raw,
            chatTemplate = chatTemplate,
            tags = tags,
            languages = languages
        )

        return raw.copy(capabilities = capabilities)
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val ctx = context ?: return uri.lastPathSegment
        if (uri.scheme == "content") {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun queryFileSize(uri: Uri): Long {
        var size: Long = -1L
        val ctx = context ?: return -1L
        if (uri.scheme == "content") {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return size
    }

    companion object {
        private const val TAG = "ModelFileManager"
        private const val MODELS_DIRECTORY_NAME = "models"
        private const val ATTACHMENTS_DIRECTORY_NAME = "attachments"
        private const val BUFFER_SIZE = 64 * 1024
    }
}

