package com.prasbin.shadowmoney.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Activity tab defect: opening the normal
 * Activity destination must never automatically request RECORD MONEY.
 *
 * The nav argument for direction defaults to an empty string when the
 * bare route is used; Screen.Transactions.normalizeDirection is the
 * semantic boundary that converts "no requested action" to a true null
 * before TransactionsScreen decides whether to open the entry dialog.
 */
class ActivityNavigationSemanticsTest {

    private fun entryDialogRequested(rawDirection: String?): Boolean =
        Screen.Transactions.normalizeDirection(rawDirection) != null

    @Test
    fun normalActivityRouteCarriesNoDirectionQuery() {
        assertEquals("transactions", Screen.Transactions.route)
        assertFalse(Screen.Transactions.route.contains("?"))
        assertFalse(Screen.Transactions.route.contains("direction"))
    }

    @Test
    fun absentDirectionDoesNotRequestEntryDialog() {
        assertFalse(entryDialogRequested(null))
        assertFalse(entryDialogRequested(""))
    }

    @Test
    fun unknownDirectionValueDoesNotRequestEntryDialog() {
        assertFalse(entryDialogRequested("bogus"))
        assertFalse(entryDialogRequested("IN"))
        assertFalse(entryDialogRequested(" "))
    }

    @Test
    fun explicitDirectionRequestsEntryDialog() {
        assertEquals("in", Screen.Transactions.normalizeDirection("in"))
        assertEquals("out", Screen.Transactions.normalizeDirection("out"))
        assertTrue(entryDialogRequested("in"))
        assertTrue(entryDialogRequested("out"))
    }

    @Test
    fun quickActionRoutesCarryExplicitDirection() {
        assertEquals("transactions?direction=in", Screen.Transactions.inDirection())
        assertEquals("transactions?direction=out", Screen.Transactions.outDirection())
    }

    @Test
    fun registeredDestinationRouteKeepsDirectionArgumentOptional() {
        assertEquals("transactions?direction={direction}", Screen.Transactions.directionRoute)
        assertNull(Screen.Transactions.normalizeDirection(""))
    }
}
