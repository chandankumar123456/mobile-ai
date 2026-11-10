package com.mobileai.assistant.data.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReliableStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAtomicWriteAndRead() {
        val file = File(tempFolder.root, "test_file.json")
        val content = """{"version": 1, "data": "hello world"}"""

        val result = ReliableStorageManager.writeAtomic(file, content)
        assertTrue(result.isSuccess)
        assertTrue(file.exists())

        val read = ReliableStorageManager.readSafe(file)
        assertEquals(content, read)

        val checksumFile = File(tempFolder.root, "test_file.json.sha256")
        assertTrue(checksumFile.exists())
        assertEquals(
            ReliableStorageManager.computeSha256(content.toByteArray(Charsets.UTF_8)),
            checksumFile.readText(Charsets.UTF_8).trim()
        )
    }

    @Test
    fun testBackupRotationOnOverwrite() {
        val file = File(tempFolder.root, "profile.json")
        val backup = File(tempFolder.root, "profile.json.bak")

        val v1 = "version 1 content"
        ReliableStorageManager.writeAtomic(file, v1)
        assertEquals(v1, file.readText())

        val v2 = "version 2 content"
        ReliableStorageManager.writeAtomic(file, v2)
        assertEquals(v2, file.readText())
        assertTrue(backup.exists())
        assertEquals(v1, backup.readText())
    }

    @Test
    fun testCorruptionRecoveryFromBackup() {
        val file = File(tempFolder.root, "store.json")
        val backup = File(tempFolder.root, "store.json.bak")
        val checksum = File(tempFolder.root, "store.json.sha256")

        val validContent = "valid data backup"
        ReliableStorageManager.writeAtomic(file, validContent)

        // Write a second version so .bak contains validContent
        val v2Content = "second version data"
        ReliableStorageManager.writeAtomic(file, v2Content)

        // Corrupt primary file with garbage while checksum expects v2
        file.writeText("corrupted garbage that fails hash")

        // readSafe should detect checksum mismatch on primary and recover from backup
        val recovered = ReliableStorageManager.readSafe(file, verifyChecksum = true)
        assertNotNull(recovered)
        assertEquals(validContent, recovered)
    }
}

