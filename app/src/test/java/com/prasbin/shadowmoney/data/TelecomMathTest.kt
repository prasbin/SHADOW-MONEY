package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_MONTHLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_QUARTERLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_WEEKLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_YEARLY
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import org.junit.Test
import org.junit.Assert.*

class TelecomMathTest {

    @Test
    fun weekly_normalizesWith52WeeksPerYear() {
        val weekly = 1_000L
        val expected = weekly * 52L / 12L
        assertEquals(expected, TelecomMath.monthlyCost(weekly, BILLING_PERIOD_WEEKLY))
        assertEquals(4_333L, TelecomMath.monthlyCost(weekly, BILLING_PERIOD_WEEKLY))
    }

    @Test
    fun monthly_passthrough() {
        assertEquals(2_500L, TelecomMath.monthlyCost(2_500L, BILLING_PERIOD_MONTHLY))
    }

    @Test
    fun quarterly_dividesByThree() {
        assertEquals(3_000L, TelecomMath.monthlyCost(9_000L, BILLING_PERIOD_QUARTERLY))
        assertEquals(3_333L, TelecomMath.monthlyCost(10_000L, BILLING_PERIOD_QUARTERLY))
    }

    @Test
    fun yearly_dividesByTwelve() {
        assertEquals(12_000L, TelecomMath.monthlyCost(144_000L, BILLING_PERIOD_YEARLY))
        assertEquals(833L, TelecomMath.monthlyCost(10_000L, BILLING_PERIOD_YEARLY))
    }

    @Test
    fun nonPositivePrice_returnsZero() {
        assertEquals(0L, TelecomMath.monthlyCost(0L, BILLING_PERIOD_MONTHLY))
        assertEquals(0L, TelecomMath.monthlyCost(-500L, BILLING_PERIOD_MONTHLY))
        assertEquals(0L, TelecomMath.monthlyCost(-500L, BILLING_PERIOD_WEEKLY))
    }

    @Test
    fun unknownPeriod_returnsZero() {
        assertEquals(0L, TelecomMath.monthlyCost(5_000L, 99))
    }

    @Test
    fun exactIntegerArithmetic_noFloatingPoint() {
        val result = TelecomMath.monthlyCost(1_234L, BILLING_PERIOD_WEEKLY)
        assertEquals(1_234L * 52L / 12L, result)
    }

    @Test
    fun subscriptionCost_customMonthlyCostTakesPrecedence() {
        val subscription = TelecomSubscription(monthlyCostMinor = 7_000L)
        val pkg = TelecomPackage(priceMinor = 10_000L, period = BILLING_PERIOD_MONTHLY)
        assertEquals(7_000L, TelecomMath.subscriptionMonthlyCost(subscription, pkg))
    }

    @Test
    fun subscriptionCost_derivesFromPackageWhenNoCustomCost() {
        val subscription = TelecomSubscription(monthlyCostMinor = 0L)
        val pkg = TelecomPackage(priceMinor = 9_000L, period = BILLING_PERIOD_QUARTERLY)
        assertEquals(3_000L, TelecomMath.subscriptionMonthlyCost(subscription, pkg))
    }

    @Test
    fun subscriptionCost_noPackage_noCost() {
        val subscription = TelecomSubscription(monthlyCostMinor = 0L)
        assertEquals(0L, TelecomMath.subscriptionMonthlyCost(subscription, null))
    }
}
