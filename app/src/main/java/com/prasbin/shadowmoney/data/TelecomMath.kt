package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_MONTHLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_QUARTERLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_WEEKLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_YEARLY
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription

const val RENEWAL_HORIZON_DAYS = 60L
const val MAX_UPCOMING_RENEWALS = 5
const val MILLIS_PER_DAY = 86_400_000L

object TelecomMath {

    /**
     * Normalizes a package price to an expected monthly cost using exact Long arithmetic.
     *
     * Assumptions (documented, deterministic):
     * - weekly:   price × 52 ÷ 12  (52 weeks per year, 12 months per year)
     * - monthly:  price as-is
     * - quarterly: price ÷ 3
     * - yearly:   price ÷ 12
     *
     * This is an EXPECTED monthly telecom cost derived from user-entered package prices.
     * It is NOT a carrier bill and never enters financial records.
     *
     * Edge behavior:
     * - non-positive price → 0
     * - unknown period → 0
     * - division truncates toward zero (integer division)
     * - weekly multiplication price × 52 can overflow only for values above ~1.77×10^17 minor units
     *   (≈ NPR 1.77×10^15/month), far beyond any real telecom price; values are validated upstream.
     */
    fun monthlyCost(priceMinor: Long, period: Int): Long {
        if (priceMinor <= 0L) return 0L
        return when (period) {
            BILLING_PERIOD_WEEKLY -> priceMinor * 52L / 12L
            BILLING_PERIOD_MONTHLY -> priceMinor
            BILLING_PERIOD_QUARTERLY -> priceMinor / 3L
            BILLING_PERIOD_YEARLY -> priceMinor / 12L
            else -> 0L
        }
    }

    fun subscriptionMonthlyCost(subscription: TelecomSubscription, pkg: TelecomPackage?): Long {
        if (subscription.monthlyCostMinor > 0L) return subscription.monthlyCostMinor
        return pkg?.let { monthlyCost(it.priceMinor, it.period) } ?: 0L
    }
}

data class UpcomingRenewal(
    val subscription: TelecomSubscription,
    val simLabel: String,
    val packageName: String,
    val renewalTimestamp: Long,
    val expectedMonthlyCostMinor: Long
)

data class TelecomSummary(
    val activeSimCount: Int,
    val activeSubscriptionCount: Int,
    val expectedMonthlyCostMinor: Long,
    val nextRenewal: UpcomingRenewal?,
    val upcomingRenewals: List<UpcomingRenewal>
)

object TelecomRenewals {

    fun upcoming(
        subscriptions: List<TelecomSubscription>,
        sims: List<TelecomSim>,
        packages: List<TelecomPackage>,
        now: Long
    ): List<UpcomingRenewal> {
        val horizon = now + RENEWAL_HORIZON_DAYS * MILLIS_PER_DAY
        val simLabels = sims.associate { it.id to it.label }
        val packageMap = packages.associateBy { it.id }
        return subscriptions
            .filter { it.isActive && it.renewalTimestamp > now && it.renewalTimestamp <= horizon }
            .sortedBy { it.renewalTimestamp }
            .take(MAX_UPCOMING_RENEWALS)
            .map { subscription ->
                val pkg = packageMap[subscription.packageId]
                UpcomingRenewal(
                    subscription = subscription,
                    simLabel = simLabels[subscription.simId] ?: "Unknown SIM",
                    packageName = pkg?.name ?: "Unknown package",
                    renewalTimestamp = subscription.renewalTimestamp,
                    expectedMonthlyCostMinor = TelecomMath.subscriptionMonthlyCost(subscription, pkg)
                )
            }
    }
}
