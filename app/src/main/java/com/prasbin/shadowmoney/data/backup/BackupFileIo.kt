package com.prasbin.shadowmoney.data.backup

import android.content.ContentResolver
import androidx.core.net.toUri
import java.io.IOException
import java.io.InputStream

/**
 * Bounded reader for backup documents (SAF streams). Reads at most
 * [MAX_BACKUP_BYTES] and never persists the raw contents anywhere.
 */
object BackupTextReader {

    sealed interface ReadResult {
        data class Ok(val text: String) : ReadResult
        data class TooLarge(val limitBytes: Int) : ReadResult
        data class ReadError(val reason: String) : ReadResult
    }

    fun readBounded(input: InputStream, maxBytes: Int = MAX_BACKUP_BYTES): ReadResult {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        try {
            input.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        return ReadResult.TooLarge(maxBytes)
                    }
                    output.write(buffer, 0, read)
                }
            }
        } catch (e: IOException) {
            return ReadResult.ReadError("Could not read the selected file")
        } catch (e: SecurityException) {
            return ReadResult.ReadError("Access to the selected file was denied")
        }
        return ReadResult.Ok(output.toString(Charsets.UTF_8.name()))
    }
}

sealed interface FileReadResult {
    data class Ok(val text: String) : FileReadResult
    data class TooLarge(val limitBytes: Int) : FileReadResult
    data class Failed(val reason: String) : FileReadResult
}

sealed interface FileWriteResult {
    data object Success : FileWriteResult
    data class Failed(val reason: String) : FileWriteResult
}

/**
 * File I/O boundary for backups. The export/import pipeline depends only on
 * this interface; the concrete SAF implementation resolves document URIs
 * through the platform picker, which is why the app needs no storage
 * permissions.
 */
interface BackupFileIo {
    fun read(uriString: String): FileReadResult
    fun write(uriString: String, content: String): FileWriteResult
}

class SafBackupFileIo(private val contentResolver: ContentResolver) : BackupFileIo {

    override fun read(uriString: String): FileReadResult {
        val stream = try {
            contentResolver.openInputStream(uriString.toUri())
        } catch (e: SecurityException) {
            return FileReadResult.Failed("Access to the selected file was denied")
        } catch (e: IllegalArgumentException) {
            return FileReadResult.Failed("The selected file could not be opened")
        }
        if (stream == null) {
            return FileReadResult.Failed("Could not open the selected file")
        }
        return when (val result = BackupTextReader.readBounded(stream)) {
            is BackupTextReader.ReadResult.Ok -> FileReadResult.Ok(result.text)
            is BackupTextReader.ReadResult.TooLarge -> FileReadResult.TooLarge(result.limitBytes)
            is BackupTextReader.ReadResult.ReadError -> FileReadResult.Failed(result.reason)
        }
    }

    override fun write(uriString: String, content: String): FileWriteResult {
        val stream = try {
            contentResolver.openOutputStream(uriString.toUri(), "wt")
        } catch (e: SecurityException) {
            return FileWriteResult.Failed("Access to the selected location was denied")
        } catch (e: IllegalArgumentException) {
            return FileWriteResult.Failed("The selected location could not be opened")
        }
        if (stream == null) {
            return FileWriteResult.Failed("Could not open the selected location")
        }
        return try {
            stream.use { sink ->
                sink.write(content.toByteArray(Charsets.UTF_8))
                sink.flush()
            }
            FileWriteResult.Success
        } catch (e: IOException) {
            FileWriteResult.Failed("Could not write the backup file")
        } catch (e: SecurityException) {
            FileWriteResult.Failed("Access to the selected location was denied")
        }
    }
}
