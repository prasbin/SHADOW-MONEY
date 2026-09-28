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
}
