package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.SheetConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Google sign-in sheet, or the Apps Script web app only when chosen. Mirrors SheetConnectionTests.swift. */
class SheetConnectionTest {
    private val url = "https://script.google.com/macros/s/x/exec"

    @Test fun appsScriptOnlyWhenChosen() {
        assertEquals(SheetConnection.GOOGLE, SheetConnection.of(googleSheet = true, signedIn = true, webAppChosen = false, webAppUrl = url, apiKey = "k"))
        // Signed out of Google: not connected — not the web app, even with its key saved.
        assertEquals(SheetConnection.NONE, SheetConnection.of(googleSheet = true, signedIn = false, webAppChosen = true, webAppUrl = url, apiKey = "k"))
        assertEquals(SheetConnection.NONE, SheetConnection.of(googleSheet = false, signedIn = false, webAppChosen = false, webAppUrl = url, apiKey = "k"))
        // Just signed in, sheet not found yet: still not connected.
        assertEquals(SheetConnection.NONE, SheetConnection.of(googleSheet = false, signedIn = true, webAppChosen = false, webAppUrl = url, apiKey = "k"))
        assertEquals(SheetConnection.WEB_APP, SheetConnection.of(googleSheet = false, signedIn = false, webAppChosen = true, webAppUrl = url, apiKey = "k"))
        assertEquals(SheetConnection.NONE, SheetConnection.of(googleSheet = false, signedIn = false, webAppChosen = true, webAppUrl = url, apiKey = ""))
    }

    @Test fun upgradingKeepsAWebAppThatWasInUse() {
        assertTrue(SheetConnection.chosenOnUpgrade(googleSheet = false, hasKey = true, hadData = true))
        assertFalse(SheetConnection.chosenOnUpgrade(googleSheet = false, hasKey = true, hadData = false))
        assertFalse(SheetConnection.chosenOnUpgrade(googleSheet = true, hasKey = true, hadData = true))
        assertFalse(SheetConnection.chosenOnUpgrade(googleSheet = false, hasKey = false, hadData = true))
    }

    @Test fun onlySettingsChoosesTheWebApp() {
        val store = File("src/main/java/com/mohammedsharukms/mypersonaltracker/sheet/SheetStore.kt").readText()
        assertTrue(store.contains("SheetConnection.of("))
        // Connecting a Google sheet ends the web app choice, so signing out later means "not connected".
        assertEquals(2, Regex("""chooseWebApp\(false\)""").findAll(store).count())
        assertEquals(1, Regex("""chooseWebApp\(true\)""").findAll(store).count())
    }
}
