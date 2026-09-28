package com.prasbin.shadowmoney.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BackupFileIoTest {

    @Test
    fun boundedReader_readsWithinLimit() {
        val text = """{"format":"$BACKUP_FORMAT_NAME"}"""
        val result = BackupTextReader.readBounded(ByteArrayInputStream(text.toByteArray()))
        assertTrue(result is BackupTextReader.ReadResult.Ok)
        assertEquals(text, (result as BackupTextReader.ReadResult.Ok).text)
    }

    @Test
    fun boundedReader_rejectsOversizedStream() {
        val oversized = ByteArray(MAX_BACKUP_BYTES + 1) { 'a'.code.toByte() }
        val result = BackupTextReader.readBounded(ByteArrayInputStream(oversized))
        assertTrue(result is BackupTextReader.ReadResult.TooLarge)
        assertEquals(MAX_BACKUP_BYTES, (result as BackupTextReader.ReadResult.TooLarge).limitBytes)
    }

    @Test
    fun boundedReader_acceptsExactlyTheLimit() {
        val exact = ByteArray(MAX_BACKUP_BYTES) { 'b'.code.toByte() }
        val result = BackupTextReader.readBounded(ByteArrayInputStream(exact))
        assertTrue(result is BackupTextReader.ReadResult.Ok)
    }

    @Test
    fun boundedReader_handlesUnreadableStreamSafely() {
        val failing = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("broken")
        }
        val result = BackupTextReader.readBounded(failing)
        assertTrue(result is BackupTextReader.ReadResult.ReadError)
    }

    @Test
    fun fakeFileIo_roundtripsWrittenBackupContent() {
        val io = FakeBackupFileIo()
        assertEquals(FileWriteResult.Success, io.write("doc://1", "{\"a\":1}"))
        assertEquals(FileReadResult.Ok("{\"a\":1}"), io.read("doc://1"))
        assertTrue(io.writtenUris.contains("doc://1"))
    }

    @Test
    fun fakeFileIo_reportsMissingDocumentsAsReadFailure() {
        val io = FakeBackupFileIo()
        val result = io.read("doc://missing")
        assertTrue(result is FileReadResult.Failed)
    }

    @Test
    fun fakeFileIo_reportsConfiguredWriteFailureWithoutStoring() {
        val io = FakeBackupFileIo()
        io.writeResult = FileWriteResult.Failed("denied")
        assertEquals(FileWriteResult.Failed("denied"), io.write("doc://2", "content"))
        assertTrue(io.files.isEmpty())
        assertTrue(io.writtenUris.isEmpty())
    }
}
