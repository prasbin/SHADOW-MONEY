package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_MONTHLY
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import org.junit.Test
import org.junit.Assert.*

class TelecomRenewalsTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun sim(id: Long, label: String, status: Int = SIM_STATUS_ACTIVE) = TelecomSim(
        id = id,
        label = label,
        status = status
    )

    private fun pkg(id: Long, name: String, price: Long = 1_000L) = TelecomPackage(
        id = id,
        name = name,
        priceMinor = price,
        period = BILLING_PERIOD_MONTHLY
    )

    private fun sub(
        id: Long,
        simId: Long,
        packageId: Long,
        renewal: Long,
        active: Boolean = true
    ) = TelecomSubscription(
        id = id,
        simId = simId,
        packageId = packageId,
        renewalTimestamp = renewal,
        isActive = active
    )

    @Test
    fun upcoming_onlyActiveWithFutureRenewalsInHorizon() {
        val sims = listOf(sim(1, "Personal"))
        val packages = listOf(pkg(1, "Data 1GB"))
        val subscriptions = listOf(
            sub(1, 1, 1, now + 10 * day),
            sub(2, 1, 1, now - 5 * day),
            sub(3, 1, 1, now + 90 * day),
            sub(4, 1, 1, now + 20 * day, active = false)
        )
        val upcoming = TelecomRenewals.upcoming(subscriptions, sims, packages, now)
        assertEquals(1, upcoming.size)
        assertEquals(1L, upcoming.first().subscription.id)
    }

    @Test
    fun upcoming_sortedByRenewalDate() {
        val sims = listOf(sim(1, "A"), sim(2, "B"))
        val packages = listOf(pkg(1, "P1"), pkg(2, "P2"))
        val subscriptions = listOf(
            sub(1, 1, 1, now + 30 * day),
            sub(2, 2, 2, now + 5 * day),
            sub(3, 1, 2, now + 15 * day)
        )
        val upcoming = TelecomRenewals.upcoming(subscriptions, sims, packages, now)
        assertEquals(3, upcoming.size)
        assertEquals(2L, upcoming[0].subscription.id)
        assertEquals(3L, upcoming[1].subscription.id)
        assertEquals(1L, upcoming[2].subscription.id)
    }

    @Test
    fun upcoming_cappedAtFive() {
        val sims = listOf(sim(1, "A"))
        val packages = listOf(pkg(1, "P"))
        val subscriptions = (1L..8L).map { id ->
            sub(id, 1, 1, now + id * day)
        }
        val upcoming = TelecomRenewals.upcoming(subscriptions, sims, packages, now)
        assertEquals(5, upcoming.size)
        assertEquals(1L, upcoming.first().subscription.id)
    }

    @Test
    fun upcoming_excludesRenewalsExactlyAtNow() {
        val sims = listOf(sim(1, "A"))
        val packages = listOf(pkg(1, "P"))
        val subscriptions = listOf(sub(1, 1, 1, now))
        assertTrue(TelecomRenewals.upcoming(subscriptions, sims, packages, now).isEmpty())
    }

    @Test
    fun upcoming_includesRenewalAtHorizonBoundary() {
        val sims = listOf(sim(1, "A"))
        val packages = listOf(pkg(1, "P"))
        val subscriptions = listOf(sub(1, 1, 1, now + 60 * day))
        val upcoming = TelecomRenewals.upcoming(subscriptions, sims, packages, now)
        assertEquals(1, upcoming.size)
    }

    @Test
    fun upcoming_excludesRenewalsBeyondHorizon() {
        val sims = listOf(sim(1, "A"))
        val packages = listOf(pkg(1, "P"))
        val subscriptions = listOf(sub(1, 1, 1, now + 61 * day))
        assertTrue(TelecomRenewals.upcoming(subscriptions, sims, packages, now).isEmpty())
    }

    @Test
    fun upcoming_includesExpectedMonthlyCost() {
        val sims = listOf(sim(1, "A"))
        val packages = listOf(pkg(1, "P", price = 9_000L))
        val subscriptions = listOf(sub(1, 1, 1, now + 10 * day))
        val upcoming = TelecomRenewals.upcoming(subscriptions, sims, packages, now)
        assertEquals(9_000L, upcoming.first().expectedMonthlyCostMinor)
    }

    @Test
    fun upcoming_archivedSimStillShownIfSubscriptionActive() {
        val sims = listOf(sim(1, "Old SIM", status = SIM_STATUS_ARCHIVED))
        val packages = listOf(pkg(1, "P"))
        val subscriptions = listOf(sub(1, 1, 1, now + 10 * day))
        val upcoming = TelecomRenewals.upcoming(subscriptions, sims, packages, now)
        assertEquals(1, upcoming.size)
        assertEquals("Old SIM", upcoming.first().simLabel)
    }

    @Test
    fun upcoming_emptyWhenNoSubscriptions() {
        assertTrue(TelecomRenewals.upcoming(emptyList(), emptyList(), emptyList(), now).isEmpty())
    }
}
