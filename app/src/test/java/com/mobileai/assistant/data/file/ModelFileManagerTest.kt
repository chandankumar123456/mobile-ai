package com.mobileai.assistant.data.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var fileManager: ModelFileManager

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("files")
        fileManager = ModelFileManager(context = null, rootDir = rootDir)
    }

    @Test
    fun testEmptyStorageReturnsZero() {
        assertEquals(0L, fileManager.getTotalModelStorageBytes())
        assertEquals(0L, fileManager.getAttachmentStorageBytes())
        assertTrue(fileManager.getAvailableStorageBytes() >= 0L)
    }

    @Test
    fun testTotalModelStorageSumsOnlyGgufFiles() {
        val modelsDir = File(rootDir, "models")
        modelsDir.mkdirs()

        val model1 = File(modelsDir, "llama3-8b.gguf")
        model1.writeBytes(ByteArray(1024))

        val model2 = File(modelsDir, "qwen2.5-7b.GGUF")
        model2.writeBytes(ByteArray(2048))

        val nonModel = File(modelsDir, "readme.txt")
        nonModel.writeBytes(ByteArray(4096))

        val total = fileManager.getTotalModelStorageBytes()
        assertEquals(3072L, total)
    }

    @Test
    fun testAttachmentStorageSumsAllFilesRecursively() {
        val attachmentsDir = File(rootDir, "attachments")
        attachmentsDir.mkdirs()

        val file1 = File(attachmentsDir, "img1.png")
        file1.writeBytes(ByteArray(500))

        val subDir = File(attachmentsDir, "subfolder")
        subDir.mkdirs()
        val file2 = File(subDir, "doc.pdf")
        file2.writeBytes(ByteArray(1500))

        val total = fileManager.getAttachmentStorageBytes()
        assertEquals(2000L, total)
    }
}
