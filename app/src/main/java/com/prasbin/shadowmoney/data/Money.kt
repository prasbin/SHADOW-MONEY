package com.prasbin.shadowmoney.data

object Money {
    const val PAISE_PER_NPR = 100L

    fun toMinorUnits(nprValue: Double): Long {
        return (nprValue * PAISE_PER_NPR).toLong()
    }

    fun toNpr(minorUnits: Long): Double {
        return minorUnits.toDouble() / PAISE_PER_NPR
    }

    fun formatNpr(minorUnits: Long): String {
        val npr = toNpr(minorUnits)
        return String.format("NPR %,.2f", npr)
    }

    fun add(a: Long, b: Long): Long = a + b
    fun subtract(a: Long, b: Long): Long = a - b
    fun multiply(a: Long, b: Long): Long = a * b

    fun balanceMinor(openingBalanceMinor: Long, incomeMinor: Long, outflowMinor: Long): Long {
        return openingBalanceMinor + incomeMinor - outflowMinor
    }
}
