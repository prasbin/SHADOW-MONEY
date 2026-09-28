package com.prasbin.shadowmoney.data.backup

/**
 * In-memory [BackupFileIo] so the export/import pipeline can be tested
 * independently of the platform document picker (no device SAF claims).
 */
class FakeBackupFileIo : BackupFileIo {

    val files = mutableMapOf<String, String>()
    var writeResult: FileWriteResult = FileWriteResult.Success
    var readResultOverride: FileReadResult? = null
    val writtenUris = mutableListOf<String>()

    override fun read(uriString: String): FileReadResult {
        readResultOverride?.let { return it }
        return files[uriString]?.let { FileReadResult.Ok(it) }
            ?: FileReadResult.Failed("Could not open the selected file")
    }

    override fun write(uriString: String, content: String): FileWriteResult {
        if (writeResult is FileWriteResult.Success) {
            files[uriString] = content
            writtenUris.add(uriString)
        }
        return writeResult
    }
}
