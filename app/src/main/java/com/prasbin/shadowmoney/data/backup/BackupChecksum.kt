package com.prasbin.shadowmoney.data.backup

import java.security.MessageDigest

/**
 * SHA-256 integrity check for backup payloads.
 *
 * The hashed input is exactly the UTF-8 bytes of the canonical JSON
 * serialization of the `payload` subtree (keys sorted, no whitespace,
 * integer-only numbers), as produced by [BackupJson.write]. The envelope
 * itself (format, versions, timestamp, checksum field) is deliberately not
 * hashed, which avoids a circular dependency: the checksum can be computed
 * while building the file and re-computed independently during verification.
 *
 * This is a corruption/tamper detector, not encryption. Backup files are
 * plain JSON; the checksum never hides their contents.
 */
object BackupChecksum {

    const val ALGORITHM = BACKUP_CHECKSUM_ALGORITHM

    fun ofCanonicalJson(canonicalJson: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val bytes = digest.digest(canonicalJson.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    fun matches(expected: String, actual: String): Boolean {
        if (expected.length != actual.length) return false
        return expected.equals(actual, ignoreCase = true)
    }
}
