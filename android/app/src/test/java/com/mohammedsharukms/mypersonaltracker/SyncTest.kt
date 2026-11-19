package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.RefreshPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a reload's answer is shown. Mirrors SyncLoopTests.swift. */
class SyncTest {
    @Test fun aFirstLoadIsShownEvenWhenAnotherHasStarted() {
        // Just connected: the load from signing in and the one from coming back to the app overlap.
        // The screen is empty, so the first answer is shown rather than thrown away for the newer one.
        assertTrue(RefreshPolicy.shows(overtaken = true, showingData = false))
        // With data on screen, an overtaken answer could undo a save that just landed: dropped.
        assertFalse(RefreshPolicy.shows(overtaken = true, showingData = true))
        assertTrue(RefreshPolicy.shows(overtaken = false, showingData = true))
        // Only an answer nothing overtook may forget the saves it already includes.
        assertTrue(RefreshPolicy.forgetsLanded(overtaken = false))
        assertFalse(RefreshPolicy.forgetsLanded(overtaken = true))
    }
}
