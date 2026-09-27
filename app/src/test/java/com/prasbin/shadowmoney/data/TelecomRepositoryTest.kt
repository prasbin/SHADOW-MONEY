package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_MONTHLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_QUARTERLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_WEEKLY
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TelecomRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var telecomDao: TelecomDao
    private lateinit var repository: TelecomRepository

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        telecomDao = database.telecomDao()
        repository = TelecomRepository(telecomDao, clock = { now })
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun createSim_persists() = runBlocking {
        val id = repository.createSim("Personal", "Nepal Telecom", "98XXXXXXX", "Primary")
        assertTrue(id > 0)
        val stored = telecomDao.getSimById(id)
        assertNotNull(stored)
        assertEquals("Personal", stored!!.label)
        assertEquals("Nepal Telecom", stored.carrier)
        assertEquals(SIM_STATUS_ACTIVE, stored.status)
    }

    @Test
    fun createSim_blankLabel_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createSim("  ", "Carrier", "", "") }
        }
        Unit
    }

    @Test
    fun updateSim_persistsChanges() = runBlocking {
        val id = repository.createSim("Old", "Carrier", "", "")
        val sim = telecomDao.getSimById(id)!!
        repository.updateSim(sim, "New", "NewTel", "123", "notes")
        val updated = telecomDao.getSimById(id)!!
        assertEquals("New", updated.label)
        assertEquals("NewTel", updated.carrier)
        assertEquals("123", updated.phoneNumber)
    }

    @Test
    fun archiveAndActivateSim_work() = runBlocking {
        val id = repository.createSim("Sim", "Carrier", "", "")
        val sim = telecomDao.getSimById(id)!!
        repository.archiveSim(sim)
        assertEquals(SIM_STATUS_ARCHIVED, telecomDao.getSimById(id)!!.status)
        repository.activateSim(telecomDao.getSimById(id)!!)
        assertEquals(SIM_STATUS_ACTIVE, telecomDao.getSimById(id)!!.status)
    }

    @Test
    fun createPackage_persistsExactPrice() = runBlocking {
        val id = repository.createPackage("Data 1GB", "Nepal Telecom", "data", 1_500L, BILLING_PERIOD_WEEKLY, "")
        assertTrue(id > 0)
        val stored = telecomDao.getPackageById(id)
        assertNotNull(stored)
        assertEquals(1_500L, stored!!.priceMinor)
        assertEquals(BILLING_PERIOD_WEEKLY, stored.period)
        assertTrue(stored.isActive)
    }

    @Test
    fun createPackage_zeroOrNegativePrice_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createPackage("P", "C", "data", 0L, BILLING_PERIOD_MONTHLY, "") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createPackage("P", "C", "data", -100L, BILLING_PERIOD_MONTHLY, "") }
        }
        Unit
    }

    @Test
    fun createPackage_blankName_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createPackage(" ", "C", "data", 100L, BILLING_PERIOD_MONTHLY, "") }
        }
        Unit
    }

    @Test
    fun archiveAndActivatePackage_work() = runBlocking {
        val id = repository.createPackage("P", "C", "data", 100L, BILLING_PERIOD_MONTHLY, "")
        val pkg = telecomDao.getPackageById(id)!!
        repository.archivePackage(pkg)
        assertFalse(telecomDao.getPackageById(id)!!.isActive)
        repository.activatePackage(telecomDao.getPackageById(id)!!)
        assertTrue(telecomDao.getPackageById(id)!!.isActive)
    }

    @Test
    fun createSubscription_persistsWithRelationships() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        val packageId = repository.createPackage("P", "C", "data", 1_000L, BILLING_PERIOD_MONTHLY, "")
        val id = repository.createSubscription(simId, packageId, now - 10 * day, now + 20 * day, 0L)
        assertTrue(id > 0)
        val stored = telecomDao.getSubscriptionById(id)
        assertNotNull(stored)
        assertEquals(simId, stored!!.simId)
        assertEquals(packageId, stored.packageId)
        assertTrue(stored.isActive)
    }

    @Test
    fun createSubscription_missingSim_rejected() = runBlocking {
        val packageId = repository.createPackage("P", "C", "data", 1_000L, BILLING_PERIOD_MONTHLY, "")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createSubscription(99L, packageId, now, now + day, 0L) }
        }
        Unit
    }

    @Test
    fun createSubscription_missingPackage_rejected() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createSubscription(simId, 99L, now, now + day, 0L) }
        }
        Unit
    }

    @Test
    fun createSubscription_negativeCustomCost_rejected() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        val packageId = repository.createPackage("P", "C", "data", 1_000L, BILLING_PERIOD_MONTHLY, "")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createSubscription(simId, packageId, now, now + day, -5L) }
        }
        Unit
    }

    @Test
    fun deactivateAndActivateSubscription_work() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        val packageId = repository.createPackage("P", "C", "data", 1_000L, BILLING_PERIOD_MONTHLY, "")
        val id = repository.createSubscription(simId, packageId, now, now + day, 0L)
        val subscription = telecomDao.getSubscriptionById(id)!!
        repository.deactivateSubscription(subscription)
        assertFalse(telecomDao.getSubscriptionById(id)!!.isActive)
        repository.activateSubscription(telecomDao.getSubscriptionById(id)!!)
        assertTrue(telecomDao.getSubscriptionById(id)!!.isActive)
    }

    @Test
    fun deleteSubscription_removesIt() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        val packageId = repository.createPackage("P", "C", "data", 1_000L, BILLING_PERIOD_MONTHLY, "")
        val id = repository.createSubscription(simId, packageId, now, now + day, 0L)
        repository.deleteSubscription(telecomDao.getSubscriptionById(id)!!)
        assertNull(telecomDao.getSubscriptionById(id))
    }

    @Test
    fun summary_countsActiveAndExpectedMonthlyCost() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        val packageId = repository.createPackage("P", "C", "data", 9_000L, BILLING_PERIOD_QUARTERLY, "")
        repository.createSubscription(simId, packageId, now - 5 * day, now + 10 * day, 0L)

        val summary = repository.loadSummary()
        assertEquals(1, summary.activeSimCount)
        assertEquals(1, summary.activeSubscriptionCount)
        assertEquals(3_000L, summary.expectedMonthlyCostMinor)
        assertNotNull(summary.nextRenewal)
        assertEquals(1, summary.upcomingRenewals.size)
    }

    @Test
    fun summary_upcomingCappedAtFive() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        val packageId = repository.createPackage("P", "C", "data", 1_000L, BILLING_PERIOD_MONTHLY, "")
        repeat(7) { index ->
            repository.createSubscription(simId, packageId, now, now + (index + 1) * day, 0L)
        }
        val summary = repository.loadSummary()
        assertEquals(5, summary.upcomingRenewals.size)
        assertEquals(7, summary.activeSubscriptionCount)
    }

    @Test
    fun summary_inactiveSubscriptionsExcluded() = runBlocking {
        val simId = repository.createSim("Sim", "C", "", "")
        val packageId = repository.createPackage("P", "C", "data", 1_000L, BILLING_PERIOD_MONTHLY, "")
        repository.createSubscription(simId, packageId, now, now + 10 * day, 0L)
        val summary = repository.loadSummary()
        assertEquals(1, summary.activeSubscriptionCount)
    }

    @Test
    fun summary_emptyDatabase_zeroes() = runBlocking {
        val summary = repository.loadSummary()
        assertEquals(0, summary.activeSimCount)
        assertEquals(0, summary.activeSubscriptionCount)
        assertEquals(0L, summary.expectedMonthlyCostMinor)
        assertNull(summary.nextRenewal)
        assertTrue(summary.upcomingRenewals.isEmpty())
    }
}
