package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.SheetLoadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** While a sheet loads, the screen says what's happening. Mirrors SheetLoadProgressTests.swift. */
class SheetLoadProgressTest {
    @Test fun eachStepSaysWhatItsDoing() {
        assertEquals("Reading your sheet…", SheetLoadProgress.reading.title)
        assertEquals("Checking what needs setting up…", SheetLoadProgress.checking.title)
        assertEquals("Almost done…", SheetLoadProgress.finishing.title)
        assertNull(SheetLoadProgress.reading.fraction)
    }

    @Test fun settingUpNamesThePartOfTheSheet() {
        val habits = SheetLoadProgress.writing("Daily Tracker", 390, 1560)
        assertEquals("Building your habits…", habits.title)
        assertEquals(0.25, habits.fraction!!, 0.0)
        assertEquals("25% done · keep the app open", habits.note)
        assertEquals("Setting up your money…", SheetLoadProgress.writing("Finance Tracker", 0, 10).title)
        assertEquals("Setting up your money…", SheetLoadProgress.writing("Salary Split", 0, 10).title)
        assertEquals("Setting up your meals…", SheetLoadProgress.writing("Food Log", 0, 10).title)
        assertEquals("Setting up your study plan…", SheetLoadProgress.writing("Study Topics", 0, 10).title)
        assertEquals("Setting up Mom & Dad…", SheetLoadProgress.writing("Parent Medicines", 0, 10).title)
        assertEquals("Setting up your planner…", SheetLoadProgress.writing("Wardrobe", 0, 10).title)
        assertEquals("Setting up your goals…", SheetLoadProgress.writing("Goal Steps", 0, 10).title)
        assertEquals("Setting up your renewals…", SheetLoadProgress.writing("Renewals", 0, 10).title)
        assertEquals("Getting your sheet ready…", SheetLoadProgress.writing("App Data", 0, 10).title)
        assertEquals(0.0, SheetLoadProgress.writing(null, 0, 0).fraction!!, 0.0)
    }

    @Test fun waitingForGoogleKeepsWhatItWasDoing() {
        val habits = SheetLoadProgress.writing("Daily Tracker", 780, 1560)
        val waiting = SheetLoadProgress.waiting(8, habits)
        assertEquals("Building your habits…", waiting.title)
        assertEquals(0.5, waiting.fraction!!, 0.0)
        assertEquals("Google asked us to slow down · carrying on in 8s", waiting.note)
        assertEquals("Reading your sheet…", SheetLoadProgress.waiting(2, null).title)
    }

    @Test fun theLoadingScreensShowTheProgress() {
        // Every tab and Settings: none is blank (or says "connect") while loading.
        for (file in listOf("DashboardScreen", "SettingsScreens", "DailyScreen", "FinanceScreen", "FoodScreen", "Parents", "StudyScreen", "GoogleSheetUi")) {
            val source = File("src/main/java/com/mohammedsharukms/mypersonaltracker/sheet/ui/$file.kt").readText()
            assertTrue("$file shows a bare spinner", source.contains("SheetLoadingCard("))
        }
    }
}
