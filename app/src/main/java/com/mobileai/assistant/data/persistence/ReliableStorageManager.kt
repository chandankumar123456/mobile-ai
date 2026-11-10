package com.mobileai.assistant.data.persistence

import com.mobileai.assistant.domain.error.AppError
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Enterprise-grade atomic persistence helper providing:
 * 1. Atomic temp-file write + fsync + rename
 * 2. Automated backup rotation (.bak) prior to destructive overwrite
 * 3. SHA-256 cryptographic checksum generation and verification
 * 4. Automatic corruption detection and failover recovery to .bak
 */
object ReliableStorageManager {

    private val lock = Any()

    /**
     * Atomically writes [content] to [targetFile].
     * Creates a `.bak` copy of any existing valid file before replacing.
     * Generates a `.sha256` checksum file for integrity checks.
     */
    fun writeAtomic(targetFile: File, content: String): Result<Unit> {
        return synchronized(lock) {
            try {
                val parentDir = targetFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }

                val tempFile = File(parentDir, "${targetFile.name}.tmp")
                val backupFile = File(parentDir, "${targetFile.name}.bak")
                val checksumFile = File(parentDir, "${targetFile.name}.sha256")

                // 1. Write to temporary file and force fsync
                FileOutputStream(tempFile).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.fd.sync()
                }

                // 2. Rotate current valid file to .bak if it exists and has content
                if (targetFile.exists() && targetFile.length() > 0) {
                    try {
                        targetFile.copyTo(backupFile, overwrite = true)
                    } catch (_: Exception) {
                        // Backup rotation best-effort
                    }
                }

                // 3. Atomic rename temp -> target
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    // Fallback for filesystems where rename fails across handles
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                // 4. Compute and write SHA-256 checksum
                val hash = computeSha256(content.toByteArray(Charsets.UTF_8))
                checksumFile.writeText(hash, Charsets.UTF_8)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.PersistenceError.WriteFailed(targetFile.name, e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Reads [targetFile] with integrity verification.
     * If the file is missing, empty, or fails checksum, automatically attempts
     * failover recovery from [targetFile].bak.
     */
    fun readSafe(targetFile: File, verifyChecksum: Boolean = true): String? {
        return synchronized(lock) {
            val parentDir = targetFile.parentFile
            val backupFile = parentDir?.let { File(it, "${targetFile.name}.bak") }
            val checksumFile = parentDir?.let { File(it, "${targetFile.name}.sha256") }

            // 1. Try reading primary target file
            if (targetFile.exists() && targetFile.length() > 0) {
                try {
                    val content = targetFile.readText(Charsets.UTF_8)
                    if (verifyChecksum && checksumFile != null && checksumFile.exists()) {
                        val expectedHash = checksumFile.readText(Charsets.UTF_8).trim()
                        val actualHash = computeSha256(content.toByteArray(Charsets.UTF_8))
                        if (expectedHash == actualHash) {
                            return content
                        }
                        // Checksum mismatch -> Primary file corrupted, fall through to backup
                    } else {
                        return content
                    }
                } catch (_: Exception) {
                    // Read failure, fall through to backup
                }
            }

            // 2. Failover to backup file if available
            if (backupFile != null && backupFile.exists() && backupFile.length() > 0) {
                try {
                    return backupFile.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    // Backup also failed
                }
            }

            null
        }
    }

    /**
     * Computes hexadecimal SHA-256 digest of [bytes].
     */
    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
