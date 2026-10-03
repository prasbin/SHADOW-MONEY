package com.prasbin.shadowmoney.data.imports

import java.io.IOException
import java.io.InputStream

/**
 * Bounded reader for user-selected CSV documents (Android SAF) and any other
 * stream source. Reads at most [MAX_IMPORT_BYTES] and never persists the raw
 * contents anywhere.
 */
object CsvStreamReader {

    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

    sealed interface ReadResult {
        data class Ok(val text: String) : ReadResult
        data class TooLarge(val limitBytes: Int) : ReadResult
        data class ReadError(val reason: String) : ReadResult
    }

    /** Result of reading raw document bytes (statement ingestion path). */
    sealed interface BytesReadResult {
        data class Bytes(val bytes: ByteArray) : BytesReadResult {
            override fun equals(other: Any?): Boolean =
                other is Bytes && bytes.contentEquals(other.bytes)
            override fun hashCode(): Int = bytes.contentHashCode()
        }
        data class TooLarge(val limitBytes: Int) : BytesReadResult
        data class ReadError(val reason: String) : BytesReadResult
    }

    fun readBounded(input: InputStream, maxBytes: Int = MAX_IMPORT_BYTES): ReadResult {
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

    /**
     * Reads raw document bytes (CSV or PDF) under the same bound, for the
     * statement ingestion path where content — not the filename — decides the
     * format. Bytes are processed in memory only and never persisted.
     */
    fun readBytesBounded(
        input: InputStream,
        maxBytes: Int = MAX_IMPORT_BYTES
    ): BytesReadResult {
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
                        return BytesReadResult.TooLarge(maxBytes)
                    }
                    output.write(buffer, 0, read)
                }
            }
        } catch (e: IOException) {
            return BytesReadResult.ReadError("Could not read the selected file")
        } catch (e: SecurityException) {
            return BytesReadResult.ReadError("Access to the selected file was denied")
        }
        return BytesReadResult.Bytes(output.toByteArray())
    }
}
