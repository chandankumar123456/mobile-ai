package com.arm.aichat

import com.arm.aichat.gguf.FileType
import com.arm.aichat.gguf.GgufMetadataReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class GgufMetadataReaderTest {

    @Test
    fun testFileTypeEnumResolvesCorrectly() {
        assertEquals("Q4_0", FileType.fromCode(2).label)
        assertEquals("F16", FileType.fromCode(1).label)
        assertEquals("Q8_0", FileType.fromCode(7).label)
        assertEquals("unknown", FileType.fromCode(9999).label)
    }

    @Test(expected = IOException::class)
    fun testInvalidGgufHeaderThrowsException() {
        runBlocking {
            val reader = GgufMetadataReader.create()
            val invalidBytes = "NOT_GGUF_HEADER".toByteArray()
            reader.readStructuredMetadata(ByteArrayInputStream(invalidBytes))
        }
    }
}
