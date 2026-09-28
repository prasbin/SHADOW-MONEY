package com.prasbin.shadowmoney.data.backup

/**
 * Converts backup DTOs to and from the on-disk document.
 *
 * [write] emits the deterministic canonical form (sorted keys, no whitespace,
 * integer-only numbers). [read] performs the full intake pipeline in the
 * mandated order: strict JSON parse -> format -> format version -> installed
 * schema compatibility -> SHA-256 checksum over the canonical payload bytes
 * -> record decoding. It never touches the database and performs no writes.
 */
object BackupSerializer {

    fun payloadTree(payload: BackupPayload): JsonValue.JsonObject = JsonValue.JsonObject(
        linkedMapOf(
            "accounts" to JsonValue.JsonArray(payload.accounts.map(::accountTree)),
            "budgets" to JsonValue.JsonArray(payload.budgets.map(::budgetTree)),
            "categories" to JsonValue.JsonArray(payload.categories.map(::categoryTree)),
            "goals" to JsonValue.JsonArray(payload.goals.map(::goalTree)),
            "opportunities" to JsonValue.JsonArray(payload.opportunities.map(::opportunityTree)),
            "telecomPackages" to JsonValue.JsonArray(payload.telecomPackages.map(::packageTree)),
            "telecomSims" to JsonValue.JsonArray(payload.telecomSims.map(::simTree)),
            "telecomSubscriptions" to JsonValue.JsonArray(payload.telecomSubscriptions.map(::subscriptionTree)),
            "transactions" to JsonValue.JsonArray(payload.transactions.map(::transactionTree)),
            "workItems" to JsonValue.JsonArray(payload.workItems.map(::workItemTree))
        )
    )

    fun checksumOf(payload: BackupPayload): String =
        BackupChecksum.ofCanonicalJson(BackupJson.write(payloadTree(payload)))

    fun write(envelope: BackupEnvelope): String {
        val document = JsonValue.JsonObject(
            linkedMapOf(
                "appSchemaVersion" to JsonValue.JsonLong(envelope.appSchemaVersion.toLong()),
                "checksum" to JsonValue.JsonObject(
                    linkedMapOf(
                        "algorithm" to JsonValue.JsonString(envelope.checksum.algorithm),
                        "value" to JsonValue.JsonString(envelope.checksum.value)
                    )
                ),
                "createdAtEpochMillis" to JsonValue.JsonLong(envelope.createdAtEpochMillis),
                "format" to JsonValue.JsonString(envelope.format),
                "formatVersion" to JsonValue.JsonLong(envelope.formatVersion.toLong()),
                "payload" to payloadTree(envelope.payload)
            )
        )
        return BackupJson.write(document)
    }

    fun read(text: String, expectedSchemaVersion: Int): EnvelopeReadOutcome {
        if (text.isBlank()) {
            return EnvelopeReadOutcome.Rejected(BackupError(BackupErrorCode.NO_DATA))
        }
        val root = when (val parsed = BackupJson.parse(text)) {
            is BackupJson.ParseResult.Malformed ->
                return rejected(BackupErrorCode.INVALID_JSON, parsed.reason)
            is BackupJson.ParseResult.Ok -> parsed.value
        }
        val rootObject = root as? JsonValue.JsonObject
            ?: return rejected(BackupErrorCode.INVALID_JSON, "The document root must be an object")

        val format = rootObject.fields["format"]
        if (format !is JsonValue.JsonString || format.value != BACKUP_FORMAT_NAME) {
            return rejected(BackupErrorCode.UNSUPPORTED_FORMAT, "Unknown backup format")
        }
        val formatVersion = rootObject.fields["formatVersion"]
        if (formatVersion == null) {
            return rejected(BackupErrorCode.INVALID_JSON, "Missing formatVersion")
        }
        if (formatVersion !is JsonValue.JsonLong) {
            return rejected(BackupErrorCode.INVALID_JSON, "formatVersion must be a whole number")
        }
        if (formatVersion.value != BACKUP_FORMAT_VERSION.toLong()) {
            return rejected(
                BackupErrorCode.UNSUPPORTED_FORMAT,
                "Backup format version ${formatVersion.value} is not supported"
            )
        }
        val schemaVersion = rootObject.fields["appSchemaVersion"]
            as? JsonValue.JsonLong
            ?: return rejected(BackupErrorCode.INVALID_JSON, "Missing appSchemaVersion")
        if (schemaVersion.value != expectedSchemaVersion.toLong()) {
            return rejected(
                BackupErrorCode.INCOMPATIBLE_SCHEMA,
                "backup schema ${schemaVersion.value}, installed schema $expectedSchemaVersion"
            )
        }
        val createdAt = rootObject.fields["createdAtEpochMillis"]
            as? JsonValue.JsonLong
            ?: return rejected(BackupErrorCode.INVALID_JSON, "Missing createdAtEpochMillis")

        val checksumObject = rootObject.fields["checksum"] as? JsonValue.JsonObject
            ?: return rejected(BackupErrorCode.INVALID_JSON, "Missing checksum")
        val algorithm = checksumObject.fields["algorithm"]
        if (algorithm !is JsonValue.JsonString || algorithm.value != BACKUP_CHECKSUM_ALGORITHM) {
            return rejected(
                BackupErrorCode.UNSUPPORTED_FORMAT,
                "Unsupported checksum algorithm"
            )
        }
        val declaredChecksum = checksumObject.fields["value"]
        if (declaredChecksum !is JsonValue.JsonString ||
            !declaredChecksum.value.matches(HEX_64)
        ) {
            return rejected(BackupErrorCode.CHECKSUM_MISMATCH, "Malformed checksum value")
        }
        val payloadObject = rootObject.fields["payload"] as? JsonValue.JsonObject
            ?: return rejected(BackupErrorCode.INVALID_JSON, "Missing payload")

        val actualChecksum = BackupChecksum.ofCanonicalJson(BackupJson.write(payloadObject))
        if (!BackupChecksum.matches(declaredChecksum.value, actualChecksum)) {
            return rejected(BackupErrorCode.CHECKSUM_MISMATCH, "Payload hash does not match")
        }

        val decoder = PayloadDecoder(payloadObject)
        val payload = decoder.decode()
            ?: return rejected(BackupErrorCode.MALFORMED_RECORDS, decoder.error!!)

        return EnvelopeReadOutcome.Ok(
            BackupEnvelope(
                format = format.value,
                formatVersion = formatVersion.value.toInt(),
                appSchemaVersion = schemaVersion.value.toInt(),
                createdAtEpochMillis = createdAt.value,
                checksum = BackupChecksumValue(algorithm.value, declaredChecksum.value),
                payload = payload
            )
        )
    }

    private fun rejected(code: BackupErrorCode, detail: String): EnvelopeReadOutcome.Rejected =
        EnvelopeReadOutcome.Rejected(BackupError(code, detail))

    private val HEX_64 = Regex("[0-9a-fA-F]{64}")

    private fun accountTree(value: AccountBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "id" to JsonValue.JsonLong(value.id),
            "isActive" to JsonValue.JsonBool(value.isActive),
            "name" to JsonValue.JsonString(value.name),
            "openingBalanceMinor" to JsonValue.JsonLong(value.openingBalanceMinor),
            "type" to JsonValue.JsonLong(value.type.toLong())
        )
    )

    private fun categoryTree(value: CategoryBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "direction" to JsonValue.JsonLong(value.direction.toLong()),
            "id" to JsonValue.JsonLong(value.id),
            "isActive" to JsonValue.JsonBool(value.isActive),
            "isSystem" to JsonValue.JsonBool(value.isSystem),
            "name" to JsonValue.JsonString(value.name)
        )
    )

    private fun transactionTree(value: TransactionBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "accountId" to JsonValue.JsonLong(value.accountId),
            "amountMinor" to JsonValue.JsonLong(value.amountMinor),
            "categoryId" to nullableLong(value.categoryId),
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "direction" to JsonValue.JsonLong(value.direction.toLong()),
            "externalRef" to nullableString(value.externalRef),
            "id" to JsonValue.JsonLong(value.id),
            "note" to JsonValue.JsonString(value.note),
            "source" to JsonValue.JsonString(value.source),
            "transactionTimestamp" to JsonValue.JsonLong(value.transactionTimestamp),
            "workItemId" to nullableLong(value.workItemId)
        )
    )

    private fun goalTree(value: GoalBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "accountId" to nullableLong(value.accountId),
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "deadlineTimestamp" to JsonValue.JsonLong(value.deadlineTimestamp),
            "id" to JsonValue.JsonLong(value.id),
            "isActive" to JsonValue.JsonBool(value.isActive),
            "isCompleted" to JsonValue.JsonBool(value.isCompleted),
            "name" to JsonValue.JsonString(value.name),
            "targetAmountMinor" to JsonValue.JsonLong(value.targetAmountMinor),
            "updatedTimestamp" to JsonValue.JsonLong(value.updatedTimestamp)
        )
    )

    private fun budgetTree(value: BudgetBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "amountMinor" to JsonValue.JsonLong(value.amountMinor),
            "categoryId" to nullableLong(value.categoryId),
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "id" to JsonValue.JsonLong(value.id),
            "monthKey" to JsonValue.JsonString(value.monthKey),
            "updatedTimestamp" to JsonValue.JsonLong(value.updatedTimestamp)
        )
    )

    private fun workItemTree(value: WorkItemBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "client" to JsonValue.JsonString(value.client),
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "deadlineTimestamp" to JsonValue.JsonLong(value.deadlineTimestamp),
            "description" to JsonValue.JsonString(value.description),
            "expectedAmountMinor" to JsonValue.JsonLong(value.expectedAmountMinor),
            "id" to JsonValue.JsonLong(value.id),
            "status" to JsonValue.JsonLong(value.status.toLong()),
            "title" to JsonValue.JsonString(value.title),
            "updatedTimestamp" to JsonValue.JsonLong(value.updatedTimestamp)
        )
    )

    private fun simTree(value: TelecomSimBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "carrier" to JsonValue.JsonString(value.carrier),
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "id" to JsonValue.JsonLong(value.id),
            "label" to JsonValue.JsonString(value.label),
            "notes" to JsonValue.JsonString(value.notes),
            "phoneNumber" to JsonValue.JsonString(value.phoneNumber),
            "status" to JsonValue.JsonLong(value.status.toLong()),
            "updatedTimestamp" to JsonValue.JsonLong(value.updatedTimestamp)
        )
    )

    private fun packageTree(value: TelecomPackageBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "carrier" to JsonValue.JsonString(value.carrier),
            "category" to JsonValue.JsonString(value.category),
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "id" to JsonValue.JsonLong(value.id),
            "isActive" to JsonValue.JsonBool(value.isActive),
            "name" to JsonValue.JsonString(value.name),
            "notes" to JsonValue.JsonString(value.notes),
            "period" to JsonValue.JsonLong(value.period.toLong()),
            "priceMinor" to JsonValue.JsonLong(value.priceMinor),
            "updatedTimestamp" to JsonValue.JsonLong(value.updatedTimestamp)
        )
    )

    private fun subscriptionTree(value: TelecomSubscriptionBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "id" to JsonValue.JsonLong(value.id),
            "isActive" to JsonValue.JsonBool(value.isActive),
            "monthlyCostMinor" to JsonValue.JsonLong(value.monthlyCostMinor),
            "packageId" to JsonValue.JsonLong(value.packageId),
            "renewalTimestamp" to JsonValue.JsonLong(value.renewalTimestamp),
            "simId" to JsonValue.JsonLong(value.simId),
            "startTimestamp" to JsonValue.JsonLong(value.startTimestamp),
            "updatedTimestamp" to JsonValue.JsonLong(value.updatedTimestamp)
        )
    )

    private fun opportunityTree(value: OpportunityBackup): JsonValue = JsonValue.JsonObject(
        linkedMapOf(
            "client" to JsonValue.JsonString(value.client),
            "createdTimestamp" to JsonValue.JsonLong(value.createdTimestamp),
            "deadlineTimestamp" to JsonValue.JsonLong(value.deadlineTimestamp),
            "description" to JsonValue.JsonString(value.description),
            "expectedAmountMinor" to nullableLong(value.expectedAmountMinor),
            "id" to JsonValue.JsonLong(value.id),
            "source" to JsonValue.JsonString(value.source),
            "sourceUrl" to JsonValue.JsonString(value.sourceUrl),
            "status" to JsonValue.JsonLong(value.status.toLong()),
            "title" to JsonValue.JsonString(value.title),
            "type" to JsonValue.JsonLong(value.type.toLong()),
            "updatedTimestamp" to JsonValue.JsonLong(value.updatedTimestamp)
        )
    )

    private fun nullableLong(value: Long?): JsonValue =
        value?.let { JsonValue.JsonLong(it) } ?: JsonValue.JsonNull

    private fun nullableString(value: String?): JsonValue =
        value?.let { JsonValue.JsonString(it) } ?: JsonValue.JsonNull

    private class PayloadDecoder(private val root: JsonValue.JsonObject) {
        var error: String? = null
            private set

        fun decode(): BackupPayload? {
            val accounts = array("accounts")?.objects()?.mapIndexedNotNull { index, item ->
                decodeAccount(item, "accounts[$index]")
            } ?: return null
            val categories = array("categories")?.objects()?.mapIndexedNotNull { index, item ->
                decodeCategory(item, "categories[$index]")
            } ?: return null
            val transactions = array("transactions")?.objects()?.mapIndexedNotNull { index, item ->
                decodeTransaction(item, "transactions[$index]")
            } ?: return null
            val goals = array("goals")?.objects()?.mapIndexedNotNull { index, item ->
                decodeGoal(item, "goals[$index]")
            } ?: return null
            val budgets = array("budgets")?.objects()?.mapIndexedNotNull { index, item ->
                decodeBudget(item, "budgets[$index]")
            } ?: return null
            val workItems = array("workItems")?.objects()?.mapIndexedNotNull { index, item ->
                decodeWorkItem(item, "workItems[$index]")
            } ?: return null
            val sims = array("telecomSims")?.objects()?.mapIndexedNotNull { index, item ->
                decodeSim(item, "telecomSims[$index]")
            } ?: return null
            val packages = array("telecomPackages")?.objects()?.mapIndexedNotNull { index, item ->
                decodePackage(item, "telecomPackages[$index]")
            } ?: return null
            val subscriptions = array("telecomSubscriptions")?.objects()?.mapIndexedNotNull { index, item ->
                decodeSubscription(item, "telecomSubscriptions[$index]")
            } ?: return null
            val opportunities = array("opportunities")?.objects()?.mapIndexedNotNull { index, item ->
                decodeOpportunity(item, "opportunities[$index]")
            } ?: return null
            if (error != null) return null
            return BackupPayload(
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                goals = goals,
                budgets = budgets,
                workItems = workItems,
                telecomSims = sims,
                telecomPackages = packages,
                telecomSubscriptions = subscriptions,
                opportunities = opportunities
            )
        }

        private fun fail(path: String, message: String): Nothing {
            error = "$path $message"
            throw DecodeException()
        }

        private class DecodeException : Exception()

        private fun array(name: String): List<JsonValue>? = try {
            when (val value = root.fields[name]) {
                is JsonValue.JsonArray -> value.items
                null -> fail("payload.$name", "is missing")
                else -> fail("payload.$name", "must be an array")
            }
        } catch (e: DecodeException) {
            null
        }

        private fun List<JsonValue>.objects(): List<JsonValue.JsonObject>? = try {
            mapIndexed { index, item ->
                item as? JsonValue.JsonObject ?: fail("[$index]", "must be an object")
            }
        } catch (e: DecodeException) {
            null
        }

        private fun string(obj: JsonValue.JsonObject, field: String, path: String): String =
            when (val value = obj.fields[field]) {
                is JsonValue.JsonString -> value.value
                null -> fail("$path.$field", "is missing")
                else -> fail("$path.$field", "must be text")
            }

        private fun long(obj: JsonValue.JsonObject, field: String, path: String): Long =
            when (val value = obj.fields[field]) {
                is JsonValue.JsonLong -> value.value
                null -> fail("$path.$field", "is missing")
                else -> fail("$path.$field", "must be a whole number")
            }

        private fun bool(obj: JsonValue.JsonObject, field: String, path: String): Boolean =
            when (val value = obj.fields[field]) {
                is JsonValue.JsonBool -> value.value
                null -> fail("$path.$field", "is missing")
                else -> fail("$path.$field", "must be true or false")
            }

        private fun nullableLongField(
            obj: JsonValue.JsonObject,
            field: String,
            path: String
        ): Long? = when (val value = obj.fields[field]) {
            is JsonValue.JsonLong -> value.value
            is JsonValue.JsonNull -> null
            null -> null
            else -> fail("$path.$field", "must be a whole number")
        }

        private fun nullableStringField(
            obj: JsonValue.JsonObject,
            field: String,
            path: String
        ): String? = when (val value = obj.fields[field]) {
            is JsonValue.JsonString -> value.value
            is JsonValue.JsonNull -> null
            null -> null
            else -> fail("$path.$field", "must be text")
        }

        private fun decodeAccount(obj: JsonValue.JsonObject, path: String): AccountBackup? = try {
            AccountBackup(
                id = long(obj, "id", path),
                name = string(obj, "name", path),
                type = long(obj, "type", path).toInt(),
                openingBalanceMinor = long(obj, "openingBalanceMinor", path),
                isActive = bool(obj, "isActive", path),
                createdTimestamp = long(obj, "createdTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeCategory(obj: JsonValue.JsonObject, path: String): CategoryBackup? = try {
            CategoryBackup(
                id = long(obj, "id", path),
                name = string(obj, "name", path),
                direction = long(obj, "direction", path).toInt(),
                isActive = bool(obj, "isActive", path),
                isSystem = bool(obj, "isSystem", path),
                createdTimestamp = long(obj, "createdTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeTransaction(
            obj: JsonValue.JsonObject,
            path: String
        ): TransactionBackup? = try {
            TransactionBackup(
                id = long(obj, "id", path),
                accountId = long(obj, "accountId", path),
                categoryId = nullableLongField(obj, "categoryId", path),
                workItemId = nullableLongField(obj, "workItemId", path),
                amountMinor = long(obj, "amountMinor", path),
                direction = long(obj, "direction", path).toInt(),
                transactionTimestamp = long(obj, "transactionTimestamp", path),
                note = string(obj, "note", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                source = string(obj, "source", path),
                externalRef = nullableStringField(obj, "externalRef", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeGoal(obj: JsonValue.JsonObject, path: String): GoalBackup? = try {
            GoalBackup(
                id = long(obj, "id", path),
                name = string(obj, "name", path),
                targetAmountMinor = long(obj, "targetAmountMinor", path),
                accountId = nullableLongField(obj, "accountId", path),
                deadlineTimestamp = long(obj, "deadlineTimestamp", path),
                isActive = bool(obj, "isActive", path),
                isCompleted = bool(obj, "isCompleted", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                updatedTimestamp = long(obj, "updatedTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeBudget(obj: JsonValue.JsonObject, path: String): BudgetBackup? = try {
            BudgetBackup(
                id = long(obj, "id", path),
                amountMinor = long(obj, "amountMinor", path),
                monthKey = string(obj, "monthKey", path),
                categoryId = nullableLongField(obj, "categoryId", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                updatedTimestamp = long(obj, "updatedTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeWorkItem(obj: JsonValue.JsonObject, path: String): WorkItemBackup? = try {
            WorkItemBackup(
                id = long(obj, "id", path),
                title = string(obj, "title", path),
                description = string(obj, "description", path),
                status = long(obj, "status", path).toInt(),
                expectedAmountMinor = long(obj, "expectedAmountMinor", path),
                deadlineTimestamp = long(obj, "deadlineTimestamp", path),
                client = string(obj, "client", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                updatedTimestamp = long(obj, "updatedTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeSim(obj: JsonValue.JsonObject, path: String): TelecomSimBackup? = try {
            TelecomSimBackup(
                id = long(obj, "id", path),
                label = string(obj, "label", path),
                carrier = string(obj, "carrier", path),
                phoneNumber = string(obj, "phoneNumber", path),
                status = long(obj, "status", path).toInt(),
                notes = string(obj, "notes", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                updatedTimestamp = long(obj, "updatedTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodePackage(
            obj: JsonValue.JsonObject,
            path: String
        ): TelecomPackageBackup? = try {
            TelecomPackageBackup(
                id = long(obj, "id", path),
                name = string(obj, "name", path),
                carrier = string(obj, "carrier", path),
                category = string(obj, "category", path),
                priceMinor = long(obj, "priceMinor", path),
                period = long(obj, "period", path).toInt(),
                notes = string(obj, "notes", path),
                isActive = bool(obj, "isActive", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                updatedTimestamp = long(obj, "updatedTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeSubscription(
            obj: JsonValue.JsonObject,
            path: String
        ): TelecomSubscriptionBackup? = try {
            TelecomSubscriptionBackup(
                id = long(obj, "id", path),
                simId = long(obj, "simId", path),
                packageId = long(obj, "packageId", path),
                startTimestamp = long(obj, "startTimestamp", path),
                renewalTimestamp = long(obj, "renewalTimestamp", path),
                monthlyCostMinor = long(obj, "monthlyCostMinor", path),
                isActive = bool(obj, "isActive", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                updatedTimestamp = long(obj, "updatedTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }

        private fun decodeOpportunity(
            obj: JsonValue.JsonObject,
            path: String
        ): OpportunityBackup? = try {
            OpportunityBackup(
                id = long(obj, "id", path),
                title = string(obj, "title", path),
                description = string(obj, "description", path),
                type = long(obj, "type", path).toInt(),
                source = string(obj, "source", path),
                sourceUrl = string(obj, "sourceUrl", path),
                expectedAmountMinor = nullableLongField(obj, "expectedAmountMinor", path),
                status = long(obj, "status", path).toInt(),
                deadlineTimestamp = long(obj, "deadlineTimestamp", path),
                client = string(obj, "client", path),
                createdTimestamp = long(obj, "createdTimestamp", path),
                updatedTimestamp = long(obj, "updatedTimestamp", path)
            )
        } catch (e: DecodeException) {
            null
        }
    }
}
