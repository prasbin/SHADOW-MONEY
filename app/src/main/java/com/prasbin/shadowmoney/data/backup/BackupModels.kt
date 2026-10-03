package com.prasbin.shadowmoney.data.backup

const val BACKUP_FORMAT_NAME = "shadow-money-backup"
const val BACKUP_FORMAT_VERSION = 1
const val APP_SCHEMA_VERSION = 10
const val BACKUP_CHECKSUM_ALGORITHM = "SHA-256"
const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

const val RESTORE_WARNING_TEXT =
    "Restoring this backup will replace the current SHADOW MONEY financial records."

enum class BackupErrorCode(val defaultMessage: String) {
    NO_DATA("The selected backup file is empty"),
    INVALID_JSON("The file is not valid SHADOW MONEY backup JSON"),
    UNSUPPORTED_FORMAT("The file is not a supported SHADOW MONEY backup"),
    INCOMPATIBLE_SCHEMA("The backup was created by an incompatible app version"),
    CHECKSUM_MISMATCH("The backup checksum does not match; the file was modified or damaged"),
    MALFORMED_RECORDS("The backup contains malformed records"),
    MISSING_REFERENCE("The backup contains references to missing records"),
    FILE_TOO_LARGE("The backup file is larger than the allowed limit"),
    STORAGE_ERROR("The selected file could not be read or written"),
    RESTORE_FAILED("The restore could not be completed"),
    UNEXPECTED("An unexpected error occurred")
}

data class BackupError(
    val code: BackupErrorCode,
    val detail: String = ""
) {
    val message: String
        get() = if (detail.isEmpty()) code.defaultMessage else "${code.defaultMessage}: $detail"
}

data class BackupRecordCounts(
    val accounts: Int = 0,
    val categories: Int = 0,
    val transactions: Int = 0,
    val goals: Int = 0,
    val budgets: Int = 0,
    val workItems: Int = 0,
    val telecomSims: Int = 0,
    val telecomPackages: Int = 0,
    val telecomSubscriptions: Int = 0,
    val opportunities: Int = 0
) {
    val total: Int
        get() = accounts + categories + transactions + goals + budgets + workItems +
            telecomSims + telecomPackages + telecomSubscriptions + opportunities

    val isEmpty: Boolean
        get() = total == 0
}

data class AccountBackup(
    val id: Long,
    val name: String,
    val type: Int,
    val openingBalanceMinor: Long,
    val isActive: Boolean,
    val createdTimestamp: Long
)

data class CategoryBackup(
    val id: Long,
    val name: String,
    val direction: Int,
    val isActive: Boolean,
    val isSystem: Boolean,
    val createdTimestamp: Long
)

data class TransactionBackup(
    val id: Long,
    val accountId: Long,
    val categoryId: Long?,
    val workItemId: Long?,
    val amountMinor: Long,
    val direction: Int,
    val transactionTimestamp: Long,
    val note: String,
    val createdTimestamp: Long,
    val source: String,
    val externalRef: String?
)

data class GoalBackup(
    val id: Long,
    val name: String,
    val targetAmountMinor: Long,
    val accountId: Long?,
    val deadlineTimestamp: Long,
    val isActive: Boolean,
    val isCompleted: Boolean,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)

data class BudgetBackup(
    val id: Long,
    val amountMinor: Long,
    val monthKey: String,
    val categoryId: Long?,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)

data class WorkItemBackup(
    val id: Long,
    val title: String,
    val description: String,
    val status: Int,
    val expectedAmountMinor: Long,
    val deadlineTimestamp: Long,
    val client: String,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)

data class TelecomSimBackup(
    val id: Long,
    val label: String,
    val carrier: String,
    val phoneNumber: String,
    val status: Int,
    val notes: String,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)

data class TelecomPackageBackup(
    val id: Long,
    val name: String,
    val carrier: String,
    val category: String,
    val priceMinor: Long,
    val period: Int,
    val notes: String,
    val isActive: Boolean,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)

data class TelecomSubscriptionBackup(
    val id: Long,
    val simId: Long,
    val packageId: Long,
    val startTimestamp: Long,
    val renewalTimestamp: Long,
    val monthlyCostMinor: Long,
    val isActive: Boolean,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)

data class OpportunityBackup(
    val id: Long,
    val title: String,
    val description: String,
    val type: Int,
    val source: String,
    val sourceUrl: String,
    val expectedAmountMinor: Long?,
    val status: Int,
    val deadlineTimestamp: Long,
    val client: String,
    val createdTimestamp: Long,
    val updatedTimestamp: Long
)

data class BackupPayload(
    val accounts: List<AccountBackup> = emptyList(),
    val categories: List<CategoryBackup> = emptyList(),
    val transactions: List<TransactionBackup> = emptyList(),
    val goals: List<GoalBackup> = emptyList(),
    val budgets: List<BudgetBackup> = emptyList(),
    val workItems: List<WorkItemBackup> = emptyList(),
    val telecomSims: List<TelecomSimBackup> = emptyList(),
    val telecomPackages: List<TelecomPackageBackup> = emptyList(),
    val telecomSubscriptions: List<TelecomSubscriptionBackup> = emptyList(),
    val opportunities: List<OpportunityBackup> = emptyList()
)

data class BackupChecksumValue(
    val algorithm: String,
    val value: String
)

data class BackupEnvelope(
    val format: String,
    val formatVersion: Int,
    val appSchemaVersion: Int,
    val createdAtEpochMillis: Long,
    val checksum: BackupChecksumValue,
    val payload: BackupPayload
)

data class RestoreCandidate(
    val envelope: BackupEnvelope,
    val counts: BackupRecordCounts
)

sealed interface EnvelopeReadOutcome {
    data class Ok(val envelope: BackupEnvelope) : EnvelopeReadOutcome
    data class Rejected(val error: BackupError) : EnvelopeReadOutcome
}

sealed interface ValidationOutcome {
    data class Valid(val counts: BackupRecordCounts) : ValidationOutcome
    data class Invalid(val error: BackupError) : ValidationOutcome
}

sealed interface RestorePreviewOutcome {
    data class Ready(val candidate: RestoreCandidate) : RestorePreviewOutcome
    data class Rejected(val error: BackupError) : RestorePreviewOutcome
}

sealed interface BackupCreateOutcome {
    data class Success(
        val json: String,
        val checksum: String,
        val counts: BackupRecordCounts,
        val createdAtEpochMillis: Long
    ) : BackupCreateOutcome

    data class Failed(val error: BackupError) : BackupCreateOutcome
}

sealed interface RestoreWriteOutcome {
    data class Success(val counts: BackupRecordCounts) : RestoreWriteOutcome
    data class Failed(val error: BackupError) : RestoreWriteOutcome
}
