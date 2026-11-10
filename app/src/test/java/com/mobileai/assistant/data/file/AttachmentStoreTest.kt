package com.mobileai.assistant.data.file

import com.mobileai.assistant.domain.model.AttachmentKind
import com.mobileai.assistant.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class AttachmentStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var store: AttachmentStore
    private lateinit var rootDir: File

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("attachments_test_root")
        store = AttachmentStore(rootDir)
    }

    @Test
    fun testSaveAttachmentFromStream() {
        val content = "print('Hello world!')"
        val stream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        val attachment = store.saveAttachment(
            name = "hello.py",
            mimeType = "text/x-python",
            sizeBytes = content.length.toLong(),
            inputStream = stream
        )

        assertNotNull(attachment.id)
        assertEquals("hello.py", attachment.fileName)
        assertEquals("text/x-python", attachment.mimeType)
        assertEquals(content.length.toLong(), attachment.sizeBytes)
        assertEquals(AttachmentKind.TEXT, attachment.kind)
        assertEquals(content, attachment.extractedText)

        val file = store.getAttachmentFile(attachment)
        assertTrue(file.exists())
        assertEquals(content, file.readText())
    }

    @Test
    fun testCopyFileToAttachment() {
        val sourceFile = tempFolder.newFile("sample.json")
        sourceFile.writeText("{\"status\": \"ok\"}")

        val attachment = store.copyFileToAttachment(sourceFile)
        assertEquals("sample.json", attachment.fileName)
        assertEquals("application/json", attachment.mimeType)
        assertEquals(AttachmentKind.TEXT, attachment.kind)
        assertEquals("{\"status\": \"ok\"}", attachment.extractedText)

        val targetFile = store.getAttachmentFile(attachment)
        assertTrue(targetFile.exists())
        assertEquals(sourceFile.length(), targetFile.length())
    }

    @Test
    fun testImageAttachmentKind() {
        val dummyBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val stream = ByteArrayInputStream(dummyBytes)
        val attachment = store.saveAttachment(
            name = "image.png",
            mimeType = "image/png",
            sizeBytes = dummyBytes.size.toLong(),
            inputStream = stream
        )

        assertEquals(AttachmentKind.IMAGE, attachment.kind)
        assertNull(attachment.extractedText)
        val readText = store.readAttachmentText(attachment)
        assertNull(readText)
    }

    @Test
    fun testReadAttachmentTextLimitsAt64KB() {
        val largeText = "A".repeat(100 * 1024) // 100 KB
        val stream = ByteArrayInputStream(largeText.toByteArray(Charsets.UTF_8))
        val attachment = store.saveAttachment(
            name = "large.txt",
            mimeType = "text/plain",
            sizeBytes = largeText.length.toLong(),
            inputStream = stream
        )

        val text = store.readAttachmentText(attachment)
        assertNotNull(text)
        assertEquals(AttachmentStore.MAX_TEXT_EXTRACT_BYTES, text!!.length)
    }

    @Test
    fun testDeleteAttachment() {
        val stream = ByteArrayInputStream("test".toByteArray(Charsets.UTF_8))
        val attachment = store.saveAttachment("test.txt", "text/plain", 4, stream)
        val file = store.getAttachmentFile(attachment)
        assertTrue(file.exists())

        val deleted = store.deleteAttachment(attachment.id)
        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun testDeleteAttachmentsForMessage() {
        val stream1 = ByteArrayInputStream("att1".toByteArray(Charsets.UTF_8))
        val stream2 = ByteArrayInputStream("att2".toByteArray(Charsets.UTF_8))

        val att1 = store.saveAttachment("file1.txt", "text/plain", 4, stream1)
        val att2 = store.saveAttachment("file2.txt", "text/plain", 4, stream2)

        val message = ChatMessage(
            content = "Here are files",
            isUser = true,
            attachments = listOf(att1, att2)
        )

        val file1 = store.getAttachmentFile(att1)
        val file2 = store.getAttachmentFile(att2)
        assertTrue(file1.exists())
        assertTrue(file2.exists())

        store.deleteAttachmentsForMessage(message)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    @Test
    fun testGetTotalAttachmentSizeBytes() {
        assertEquals(0L, store.getTotalAttachmentSizeBytes())

        val s1 = ByteArrayInputStream("12345".toByteArray(Charsets.UTF_8))
        val s2 = ByteArrayInputStream("67890".toByteArray(Charsets.UTF_8))

        store.saveAttachment("a.txt", "text/plain", 5, s1)
        store.saveAttachment("b.txt", "text/plain", 5, s2)

        assertEquals(10L, store.getTotalAttachmentSizeBytes())
    }

    @Test
    fun testCleanupOrphanedAttachments() {
        val s1 = ByteArrayInputStream("keep me".toByteArray(Charsets.UTF_8))
        val s2 = ByteArrayInputStream("orphan me".toByteArray(Charsets.UTF_8))

        val att1 = store.saveAttachment("keep.txt", "text/plain", 7, s1)
        val att2 = store.saveAttachment("orphan.txt", "text/plain", 9, s2)

        val file1 = store.getAttachmentFile(att1)
        val file2 = store.getAttachmentFile(att2)
        assertTrue(file1.exists())
        assertTrue(file2.exists())

        val deleted = store.cleanupOrphanedAttachments(validAttachmentUris = setOf(att1.uri))
        assertEquals(1, deleted)
        assertTrue(file1.exists())
        assertFalse(file2.exists())
    }
}
