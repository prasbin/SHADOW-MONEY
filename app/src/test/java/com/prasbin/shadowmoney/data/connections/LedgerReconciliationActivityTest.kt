package com.prasbin.shadowmoney.data.connections

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Provenance-split ledger aggregation: one bounded SQL query grouped by source,
 * mapped with [provenanceOfTransactionSource]. Verified, imported, and manual net
 * movement are reported separately and rows before the window are excluded — the
 * aggregate never blends provenance or scans unbounded data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LedgerReconciliationActivityTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var activity: ReconciliationActivity

    private val since = 1_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShadowMoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activity = LedgerReconciliationActivity(database.transactionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun netActivitySince_splitsProvenanceAndExcludesRowsBeforeWindow() = runBlocking {
        val accountId = database.accountDao()
            .insert(Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L))

        fun tx(amount: Long, direction: Int, source: String, at: Long) = Transaction(
            accountId = accountId,
            amountMinor = amount,
            direction = direction,
            transactionTimestamp = at,
            createdTimestamp = at,
            source = source
        )

        database.transactionDao().insert(tx(50_000L, TRANSACTION_DIRECTION_INCOME, "", since + 100L))
        database.transactionDao().insert(tx(20_000L, TRANSACTION_DIRECTION_OUTFLOW, TRANSACTION_SOURCE_IMPORT_FILE, since + 200L))
        database.transactionDao().insert(tx(10_000L, TRANSACTION_DIRECTION_OUTFLOW, TRANSACTION_SOURCE_CONNECTED, since + 300L))
        database.transactionDao().insert(tx(5_000L, TRANSACTION_DIRECTION_OUTFLOW, TRANSACTION_SOURCE_CONNECTED, since - 1L))
        database.transactionDao().insert(tx(7_000L, TRANSACTION_DIRECTION_INCOME, "", since - 1L))

        val recorded = activity.netActivitySince(since)

        assertEquals(-10_000L, recorded.verifiedNetChangeMinor)
        assertEquals(-20_000L, recorded.importedNetChangeMinor)
        assertEquals(50_000L, recorded.manualNetChangeMinor)
    }

    @Test
    fun netActivitySince_noRows_reportsAllZeros() = runBlocking {
        val recorded = activity.netActivitySince(since)
        assertEquals(0L, recorded.verifiedNetChangeMinor)
        assertEquals(0L, recorded.importedNetChangeMinor)
        assertEquals(0L, recorded.manualNetChangeMinor)
    }

    @Test
    fun netActivitySince_unknownSourceStrings_treatedAsImported() = runBlocking {
        val accountId = database.accountDao()
            .insert(Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L))
        database.transactionDao().insert(
            Transaction(
                accountId = accountId,
                amountMinor = 12_000L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = since + 50L,
                createdTimestamp = since + 50L,
                source = "SOME_OTHER_FILE"
            )
        )

        val recorded = activity.netActivitySince(since)
        assertEquals(0L, recorded.verifiedNetChangeMinor)
        assertEquals(-12_000L, recorded.importedNetChangeMinor)
        assertEquals(0L, recorded.manualNetChangeMinor)
    }
}
