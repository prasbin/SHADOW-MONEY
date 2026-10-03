package com.prasbin.shadowmoney.data.statements

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

/**
 * Synthetic, clearly-fictional PDFs for tests. Every fixture generated here
 * contains made-up data — no real statement is ever used in tests.
 */
object SyntheticPdf {

    fun simple(content: String, extraObjects: String = "", encrypt: Boolean = false): ByteArray =
        assemble(
            contentBytes = content.toByteArray(Charsets.ISO_8859_1),
            contentFilter = null,
            extraObjects = extraObjects,
            encrypt = encrypt
        )

    fun flateCompressed(content: String, extraObjects: String = "", encrypt: Boolean = false): ByteArray =
        assemble(
            contentBytes = deflate(content),
            contentFilter = "/FlateDecode",
            extraObjects = extraObjects,
            encrypt = encrypt
        )

    fun imageOnly(): ByteArray =
        assemble(
            contentBytes = byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x42, 0x99.toByte()
            ),
            contentFilter = "/DCTDecode",
            extraObjects = "",
            encrypt = false
        )

    fun notAPdf(): ByteArray = "this is not a pdf document".toByteArray(Charsets.ISO_8859_1)

    fun cmapObject(cmap: String): String =
        "6 0 obj\n<< /Length ${cmap.length} >>\nstream\n$cmap\nendstream\nendobj\n"

    fun simpleToUnicodeCmap(): String = """
        /CIDInit /ProcSet findresource begin
        12 dict begin
        begincmap
        1 begincodespacerange
        <00> <FF>
        endcodespacerange
        1 beginbfchar
        <41> <005A>
        endbfchar
        endcmap
    """.trimIndent()

    private fun assemble(
        contentBytes: ByteArray,
        contentFilter: String?,
        extraObjects: String,
        encrypt: Boolean
    ): ByteArray {
        val filter = contentFilter?.let { " /Filter $it" } ?: ""
        val encryptTrailer = if (encrypt) " /Encrypt 8 0 R" else ""
        val out = ByteArrayOutputStream()
        out.write(("%PDF-1.4\n").toByteArray(Charsets.ISO_8859_1))
        out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        out.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        out.write(
            ("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>\nendobj\n")
                .toByteArray(Charsets.ISO_8859_1)
        )
        out.write("4 0 obj\n<< /Length ${contentBytes.size}$filter >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
        out.write(contentBytes)
        out.write("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        out.write(extraObjects.toByteArray(Charsets.ISO_8859_1))
        out.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        out.write("trailer\n<< /Root 1 0 R /Size 9$encryptTrailer >>\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    private fun deflate(text: String): ByteArray {
        val bos = ByteArrayOutputStream()
        DeflaterOutputStream(bos).use { it.write(text.toByteArray(Charsets.ISO_8859_1)) }
        return bos.toByteArray()
    }
}
