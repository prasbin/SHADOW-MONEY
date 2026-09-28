package com.prasbin.shadowmoney.data.backup

/**
 * Assembles a complete, checksummed backup envelope from a payload. Pure and
 * deterministic: the same payload, timestamp and schema version always yield
 * byte-identical output.
 */
object BackupBuilder {

    fun build(
        payload: BackupPayload,
        createdAtEpochMillis: Long,
        schemaVersion: Int = APP_SCHEMA_VERSION
    ): BackupEnvelope = BackupEnvelope(
        format = BACKUP_FORMAT_NAME,
        formatVersion = BACKUP_FORMAT_VERSION,
        appSchemaVersion = schemaVersion,
        createdAtEpochMillis = createdAtEpochMillis,
        checksum = BackupChecksumValue(
            algorithm = BACKUP_CHECKSUM_ALGORITHM,
            value = BackupSerializer.checksumOf(payload)
        ),
        payload = payload
    )
}
