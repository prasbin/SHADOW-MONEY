package com.prasbin.shadowmoney.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Phase 12 security boundaries: no permissions, no network, no logging of
 * backup contents, no credential material, size limits enforced, and the
 * machine-specific signing/config files stay ignored and absent.
 */
class BackupSecurityTest {

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

    private fun backupSourceFiles(): List<File> {
        val root = findProjectFile("app/src/main/java/com/prasbin/shadowmoney")
        val files = mutableListOf<File>()
        File(root, "data/backup").listFiles()?.forEach { if (it.isFile) files.add(it) }
        files.add(File(root, "presentation/screen/settings/BackupViewModel.kt"))
        assertTrue("expected backup source files", files.isNotEmpty())
        return files
    }

    @Test
    fun manifest_declaresNoPermissionsAtAll() {
        val manifest = findProjectFile("app/src/main/AndroidManifest.xml").readText()
        assertFalse("manifest must not request any permission", manifest.contains("uses-permission"))
        for (forbidden in listOf(
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "MANAGE_EXTERNAL_STORAGE",
            "READ_MEDIA_IMAGES",
            "INTERNET"
        )) {
            assertFalse("manifest must not request $forbidden", manifest.contains(forbidden))
        }
    }

    @Test
    fun backupCode_containsNoNetworkUsage() {
        val forbidden = listOf(
            "java.net", "HttpURLConnection", "okhttp", "Retrofit",
            "URL(", "openConnection", "Socket(", "WebSocket"
        )
        backupSourceFiles().forEach { file ->
            val text = file.readText()
            forbidden.forEach { needle ->
                assertFalse(
                    "${file.name} must not use network API '$needle'",
                    text.contains(needle)
                )
            }
        }
    }

    @Test
    fun backupCode_containsNoLoggingOfBackupContents() {
        backupSourceFiles().forEach { file ->
            val text = file.readText()
            assertFalse(
                "${file.name} must not log backup contents",
                text.contains("android.util.Log") || text.contains("Log.d(") ||
                    text.contains("Log.i(") || text.contains("Log.e(") ||
                    text.contains("Log.w(")
            )
            assertFalse("${file.name} must not print backup data", text.contains("println("))
        }
    }

    @Test
    fun backupCode_containsNoCredentialOrAuthHandling() {
        val pattern = Regex(
            """\b(password|passwd|otp|cvv|pin|credential|credentials|token|tokens|api[-_]?key|authorization)\b"""
        )
        backupSourceFiles().forEach { file ->
            val text = file.readText().lowercase()
            val match = pattern.find(text)
            assertTrue(
                "${file.name} must not handle credential material (matched '${match?.value}')",
                match == null
            )
        }
    }

    @Test
    fun backupCode_usesOnlyTheDocumentFrameworkForFileAccess() {
        val io = findProjectFile(
            "app/src/main/java/com/prasbin/shadowmoney/data/backup/BackupFileIo.kt"
        ).readText()
        assertTrue(io.contains("android.content.ContentResolver"))
        assertTrue(io.contains("openInputStream"))
        assertTrue(io.contains("openOutputStream"))
        assertFalse(io.contains("getExternalStorage"))
        assertFalse(io.contains("Environment."))
        assertFalse(io.contains("FileInputStream"))
        assertFalse(io.contains("FileOutputStream"))
    }

    @Test
    fun backupSizeLimit_isEnforcedInCode() {
        val source = findProjectFile(
            "app/src/main/java/com/prasbin/shadowmoney/data/backup/BackupModels.kt"
        ).readText()
        assertTrue(source.contains("MAX_BACKUP_BYTES = 10 * 1024 * 1024"))
        assertEqualsTenMegabytes()
    }

    private fun assertEqualsTenMegabytes() {
        assertTrue(MAX_BACKUP_BYTES == 10 * 1024 * 1024)
    }

    @Test
    fun gitignore_keepsSigningAndLocalConfigOutOfTheRepository() {
        val gitignore = findProjectFile(".gitignore").readText()
        for (entry in listOf("local.properties", "*.jks", "*.keystore")) {
            assertTrue(".gitignore must ignore $entry", gitignore.contains(entry))
        }
    }

    @Test
    fun noSigningKeysExistAnywhereInTheProject() {
        val root = findProjectFile(".gitignore").parentFile
        val offenders = mutableListOf<String>()
        root.walkTopDown().forEach { file ->
            if (file.isFile) {
                val name = file.name
                val inBuildDir = file.invariantSeparatorsPath.contains("/build/") ||
                    file.invariantSeparatorsPath.contains("\\build\\")
                if (!inBuildDir && (name.endsWith(".jks") || name.endsWith(".keystore"))) {
                    offenders.add(file.absolutePath)
                }
            }
        }
        assertTrue("unexpected signing files: $offenders", offenders.isEmpty())
    }

    @Test
    fun noBackupExportFilesArePresentInTheRepository() {
        val root = findProjectFile(".gitignore").parentFile
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.name.startsWith("shadow-money-backup") && it.extension == "json" }
            .map { it.absolutePath }
            .toList()
        assertTrue("no backup exports may exist in the repo: $offenders", offenders.isEmpty())
    }
}
