package com.prasbin.shadowmoney.data.backup

import com.prasbin.shadowmoney.data.ShadowMoneyDatabase

/**
 * Orchestrates the local backup flow: load -> build -> checksum -> serialize
 * -> write through the SAF boundary, and the restore flow: read -> parse ->
 * version check -> checksum verification -> validation -> preview (read-only)
 * -> confirmed atomic full replacement. No database write happens before the
 * caller confirms a ready preview.
 */
class BackupRepository(
    private val database: ShadowMoneyDatabase,
    private val fileIo: BackupFileIo,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val engine = RestoreEngine(database)

    fun writeBackupToUri(uriString: String, content: String): FileWriteResult =
        fileIo.write(uriString, content)

    suspend fun loadCounts(): BackupRecordCounts {
        val dao = database.backupDao()
        return BackupRecordCounts(
            accounts = dao.countAccounts(),
            categories = dao.countCategories(),
            transactions = dao.countTransactions(),
            goals = dao.countGoals(),
            budgets = dao.countBudgets(),
            workItems = dao.countWorkItems(),
            telecomSims = dao.countSims(),
            telecomPackages = dao.countPackages(),
            telecomSubscriptions = dao.countSubscriptions(),
            opportunities = dao.countOpportunities()
        )
    }

    suspend fun createBackupJson(): BackupCreateOutcome {
        return try {
            val payload = loadPayload()
            val counts = BackupValidator.countsOf(payload)
            val createdAt = clock()
            val envelope = BackupBuilder.build(payload, createdAt, installedSchemaVersion())
            val json = BackupSerializer.write(envelope)
            if (json.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
                BackupCreateOutcome.Failed(
                    BackupError(
                        BackupErrorCode.FILE_TOO_LARGE,
                        "backup exceeds the ${MAX_BACKUP_BYTES / (1024 * 1024)} MB limit"
                    )
                )
            } else {
                BackupCreateOutcome.Success(
                    json = json,
                    checksum = envelope.checksum.value,
                    counts = counts,
                    createdAtEpochMillis = createdAt
                )
            }
        } catch (error: Exception) {
            BackupCreateOutcome.Failed(
                BackupError(BackupErrorCode.UNEXPECTED, error.message ?: "could not build the backup")
            )
        }
    }

    suspend fun prepareRestoreFromUri(uriString: String): RestorePreviewOutcome {
        return when (val read = fileIo.read(uriString)) {
            is FileReadResult.Ok -> prepareRestoreText(read.text)
            is FileReadResult.TooLarge -> RestorePreviewOutcome.Rejected(
                BackupError(
                    BackupErrorCode.FILE_TOO_LARGE,
                    "limit is ${read.limitBytes / (1024 * 1024)} MB"
                )
            )
            is FileReadResult.Failed -> RestorePreviewOutcome.Rejected(
                BackupError(BackupErrorCode.STORAGE_ERROR, read.reason)
            )
        }
    }

    suspend fun prepareRestoreText(text: String): RestorePreviewOutcome {
        if (text.isBlank()) {
            return RestorePreviewOutcome.Rejected(BackupError(BackupErrorCode.NO_DATA))
        }
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            return RestorePreviewOutcome.Rejected(
                BackupError(
                    BackupErrorCode.FILE_TOO_LARGE,
                    "limit is ${MAX_BACKUP_BYTES / (1024 * 1024)} MB"
                )
            )
        }
        val envelope = when (val read = BackupSerializer.read(text, installedSchemaVersion())) {
            is EnvelopeReadOutcome.Rejected -> return RestorePreviewOutcome.Rejected(read.error)
            is EnvelopeReadOutcome.Ok -> read.envelope
        }
        return when (val validation = BackupValidator.validate(envelope.payload)) {
            is ValidationOutcome.Invalid -> RestorePreviewOutcome.Rejected(validation.error)
            is ValidationOutcome.Valid -> RestorePreviewOutcome.Ready(
                RestoreCandidate(envelope = envelope, counts = validation.counts)
            )
        }
    }

    suspend fun restore(candidate: RestoreCandidate): RestoreWriteOutcome {
        return when (val validation = BackupValidator.validate(candidate.envelope.payload)) {
            is ValidationOutcome.Invalid -> RestoreWriteOutcome.Failed(validation.error)
            is ValidationOutcome.Valid -> {
                try {
                    engine.restore(candidate.envelope.payload)
                    RestoreWriteOutcome.Success(validation.counts)
                } catch (error: Exception) {
                    RestoreWriteOutcome.Failed(
                        BackupError(
                            BackupErrorCode.RESTORE_FAILED,
                            error.message ?: "the transaction was rolled back"
                        )
                    )
                }
            }
        }
    }

    suspend fun restoreFromText(text: String): RestoreWriteOutcome {
        return when (val preview = prepareRestoreText(text)) {
            is RestorePreviewOutcome.Rejected -> RestoreWriteOutcome.Failed(preview.error)
            is RestorePreviewOutcome.Ready -> restore(preview.candidate)
        }
    }

    private suspend fun loadPayload(): BackupPayload {
        val dao = database.backupDao()
        return BackupPayload(
            accounts = dao.getAllAccounts().map { it.toBackup() },
            categories = dao.getAllCategories().map { it.toBackup() },
            transactions = dao.getAllTransactions().map { it.toBackup() },
            goals = dao.getAllGoals().map { it.toBackup() },
            budgets = dao.getAllBudgets().map { it.toBackup() },
            workItems = dao.getAllWorkItems().map { it.toBackup() },
            telecomSims = dao.getAllSims().map { it.toBackup() },
            telecomPackages = dao.getAllPackages().map { it.toBackup() },
            telecomSubscriptions = dao.getAllSubscriptions().map { it.toBackup() },
            opportunities = dao.getAllOpportunities().map { it.toBackup() }
        )
    }

    private fun installedSchemaVersion(): Int = try {
        database.openHelper.readableDatabase.version
    } catch (error: Exception) {
        APP_SCHEMA_VERSION
    }
}
