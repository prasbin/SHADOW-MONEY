package com.prasbin.shadowmoney.intelligence

const val ANALYSIS_WINDOW_DAYS = 400L
const val PREVIOUS_PERIOD_DAYS = 400L
const val MILLIS_PER_DAY = 86_400_000L

object AnalysisWindow {

    fun start(now: Long): Long = now - ANALYSIS_WINDOW_DAYS * MILLIS_PER_DAY

    fun previousStart(now: Long): Long = start(now) - PREVIOUS_PERIOD_DAYS * MILLIS_PER_DAY

    fun contains(timestamp: Long, now: Long): Boolean =
        timestamp in start(now)..now
}
