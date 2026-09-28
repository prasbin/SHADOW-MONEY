package com.prasbin.shadowmoney

import com.prasbin.shadowmoney.data.imports.CsvStreamReader
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Security boundaries for Phase 10 import:
 * - no storage permissions, no network permission
 * - no network code, no logging of imported financial contents
 * - enforced import size limit
 */
class ImportSecurityTest {

    private fun findProjectFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir"))
        var attempts = 0
        while (attempts < 6) {
            val candidate = File(dir, relativePath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: break
            attempts++
        }
        fail("Could not locate $relativePath from ${System.getProperty("user.dir")}")
        throw IllegalStateException("unreachable")
    }

    private fun importSourceFiles(): List<File> {
        val root = findProjectFile("app/src/main/java/com/prasbin/shadowmoney")
        val files = mutableListOf<File>()
        File(root, "data/imports").listFiles()?.forEach { if (it.isFile) files.add(it) }
        File(root, "presentation/screen/csvimport").listFiles()?.forEach { if (it.isFile) files.add(it) }
        assertTrue("expected import source files", files.isNotEmpty())
        return files
    }

    @Test
    fun manifest_declaresNoPermissions() {
        val manifest = findProjectFile("app/src/main/AndroidManifest.xml").readText()
        assertFalse(
            "manifest must not request any permission",
            manifest.contains("uses-permission")
        )
        for (forbidden in listOf(
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "MANAGE_EXTERNAL_STORAGE",
            "INTERNET"
        )) {
            assertFalse("manifest must not request $forbidden", manifest.contains(forbidden))
        }
    }

    @Test
    fun importCode_containsNoNetworkUsage() {
        val forbidden = listOf(
            "java.net", "HttpURLConnection", "okhttp", "Retrofit",
            "URL(", "openConnection", "Socket("
        )
        importSourceFiles().forEach { file ->
            val text = file.readText()
            forbidden.forEach { needle ->
                assertFalse("${file.name} must not use network API '$needle'", text.contains(needle))
            }
        }
    }

    @Test
    fun importCode_containsNoLoggingOfCsvContents() {
        importSourceFiles().forEach { file ->
            val text = file.readText()
            assertFalse(
                "${file.name} must not log imported financial contents",
                text.contains("android.util.Log") || text.contains("Log.d(") ||
                    text.contains("Log.i(") || text.contains("Log.e(")
            )
            assertFalse("${file.name} must not print CSV data", text.contains("println("))
        }
    }

    @Test
    fun importCode_containsNoCredentialOrAuthHandling() {
        val pattern = Regex("""\b(password|passwd|otp|cvv|pin|credential|credentials|token|tokens|secret|secrets)\b""")
        importSourceFiles().forEach { file ->
            val text = file.readText().lowercase()
            val match = pattern.find(text)
            assertNull(
                "${file.name} must not handle credential material (matched '${match?.value}')",
                match
            )
        }
    }

    @Test
    fun streamReader_enforcesFiveMbBound() {
        val overLimit = ByteArray(CsvStreamReader.MAX_IMPORT_BYTES + 1) { 'a'.code.toByte() }
        val result = CsvStreamReader.readBounded(ByteArrayInputStream(overLimit))
        assertTrue(result is CsvStreamReader.ReadResult.TooLarge)
    }

    @Test
    fun streamReader_readsWithinBound() {
        val text = "date,description\n2026-09-01,Coffee\n"
        val result = CsvStreamReader.readBounded(ByteArrayInputStream(text.toByteArray()))
        assertTrue(result is CsvStreamReader.ReadResult.Ok)
        assertEquals(text, (result as CsvStreamReader.ReadResult.Ok).text)
    }

    @Test
    fun streamReader_handlesUnreadableStreamSafely() {
        val failing = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("broken")
        }
        val result = CsvStreamReader.readBounded(failing)
        assertTrue(result is CsvStreamReader.ReadResult.ReadError)
    }
}
