package com.prasbin.shadowmoney

import com.prasbin.shadowmoney.data.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun toMinorUnits_convertsCorrectly() {
        assertEquals(10000L, Money.toMinorUnits(100.00))
        assertEquals(2550L, Money.toMinorUnits(25.50))
        assertEquals(0L, Money.toMinorUnits(0.00))
        assertEquals(100L, Money.toMinorUnits(1.00))
    }

    @Test
    fun toNpr_convertsCorrectly() {
        assertEquals(100.00, Money.toNpr(10000L), 0.001)
        assertEquals(25.50, Money.toNpr(2550L), 0.001)
        assertEquals(0.0, Money.toNpr(0L), 0.001)
    }

    @Test
    fun balanceMinor_calculatesCorrectly() {
        assertEquals(5000L, Money.balanceMinor(10000L, 3000L, 8000L))
        assertEquals(0L, Money.balanceMinor(0L, 0L, 0L))
        assertEquals(15000L, Money.balanceMinor(10000L, 10000L, 5000L))
    }

    @Test
    fun arithmeticOperations_workWithLong() {
        assertEquals(20000L, Money.add(10000L, 10000L))
        assertEquals(5000L, Money.subtract(10000L, 5000L))
        assertEquals(1000000L, Money.multiply(1000L, 1000L))
    }

    @Test
    fun formatNpr_producesCorrectString() {
        assertEquals("NPR 100.00", Money.formatNpr(10000L))
        assertEquals("NPR 0.00", Money.formatNpr(0L))
    }
}