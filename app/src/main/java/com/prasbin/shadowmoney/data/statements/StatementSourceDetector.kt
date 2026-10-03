package com.prasbin.shadowmoney.data.statements

/**
 * Content-based statement source detection.
 *
 * Rules (tested):
 * - Only the document's own text is inspected. A filename is never an input,
 *   so a downloaded "sanima_statement.pdf" that contains no Sanima markers is
 *   never identified as Sanima.
 * - A single provider marker set yields that provider as a candidate.
 * - Multiple or zero markers yield UNKNOWN; the UI asks the user instead of
 *   guessing. The stored source always comes from the user's confirmation.
 */
object StatementSourceDetector {

    private const val DETECTION_HEAD_CHARS = 8_192

    private val markers: Map<StatementSource, List<String>> = mapOf(
        StatementSource.SANIMA to listOf("sanima"),
        StatementSource.GLOBAL_IME to listOf("global ime", "globalime", "global smart"),
        StatementSource.ESEWA to listOf("esewa", "e-sewa")
    )

    fun detect(documentText: String): StatementDetection {
        val head = documentText.take(DETECTION_HEAD_CHARS).lowercase()
        val matched = markers.filter { (_, tokens) -> tokens.any { it in head } }.keys

        return when {
            matched.size == 1 -> {
                val source = matched.first()
                StatementDetection(
                    candidate = source,
                    evidence = "document content mentions ${source.label}"
                )
            }
            matched.size > 1 -> StatementDetection(
                candidate = StatementSource.UNKNOWN,
                evidence = "multiple provider names found in the document — confirm the source"
            )
            else -> StatementDetection(
                candidate = StatementSource.UNKNOWN,
                evidence = "no provider markers found in the document — confirm the source"
            )
        }
    }
}
