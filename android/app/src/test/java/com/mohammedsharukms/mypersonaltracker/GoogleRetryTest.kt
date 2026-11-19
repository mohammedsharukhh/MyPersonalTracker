package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.google.GoogleRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Too many requests" (429) from Google: wait and try again. Mirrors GoogleRetryTests.swift. */
class GoogleRetryTest {
    @Test fun waitsLongerEachTimeThenGivesUp() {
        assertEquals(2.0, GoogleRetry.wait(429, 0, null)!!, 0.0)
        assertEquals(4.0, GoogleRetry.wait(429, 1, null)!!, 0.0)
        assertEquals(32.0, GoogleRetry.wait(503, 4, null)!!, 0.0)
        assertNull(GoogleRetry.wait(429, 5, null))
        // Google's own "try again in N seconds" wins, within reason.
        assertEquals(7.0, GoogleRetry.wait(429, 0, "7")!!, 0.0)
        assertEquals(60.0, GoogleRetry.wait(429, 0, "600")!!, 0.0)
        // A refusal isn't retried: it would only be refused again.
        assertNull(GoogleRetry.wait(400, 0, null))
        assertNull(GoogleRetry.wait(403, 0, null))
    }

    @Test fun aBusySheetWithNothingOnScreenSaysSo() {
        assertEquals(SheetStore.BUSY_MESSAGE, SheetStore.busyMessage(showingData = false))
        assertNull(SheetStore.busyMessage(showingData = true))
    }
}
