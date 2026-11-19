package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.MorningSlot
import com.mohammedsharukms.mypersonaltracker.sheet.NotificationSummary
import com.mohammedsharukms.mypersonaltracker.sheet.PlanState
import com.mohammedsharukms.mypersonaltracker.sheet.SettingsDraft
import com.mohammedsharukms.mypersonaltracker.sheet.SettingsPage
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStatus
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings is one screen: your sheet, then pages that save to it (Money, Health & food, Study),
 * then what's only on this phone (Notifications…). Mirrors SettingsTests.swift.
 */
class SettingsTest {
    private fun settings(dailyLimit: Double? = 50.0, salary: Double? = 9000.0, water: Double? = 3.0, studyDaily: Double? = 60.0) =
        SheetSettings(null, null, 3.7, dailyLimit, null, water, 7.5, 8000.0, 3.0, salary, 0.95, 0.85,
            studyDailyTarget = studyDaily, splitShares = mapOf("SPLIT_ESSENTIALS" to 0.45))

    @Test fun everyTargetIsOnExactlyOnePage() {
        // Everything the old single Settings screen let you change, so nothing went missing.
        val editable = setOf(
            "RATE", "DAILY_LIMIT", "SALARY", "LIMIT_FROM_SPLIT", "CUSTOM_LIMIT", "EMERGENCY_GOAL",
            "SPLIT_ESSENTIALS", "SPLIT_EMERGENCY", "SPLIT_SAVINGS", "SPLIT_FAMILY", "SPLIT_LIFESTYLE",
            "WATER_TARGET", "SLEEP_TARGET", "STEPS_TARGET", "SCREEN_LIMIT", "GREAT_DAY",
            "CALORIE_TARGET", "PROTEIN_TARGET", "CARB_TARGET", "FAT_TARGET", "FOOD_BUDGET",
            "STUDY_DAILY_TARGET", "STUDY_WEEKLY_TARGET",
        )
        val pages = SettingsPage.entries.map { it.keys(single = false) }
        assertEquals(editable, pages.flatten().toSet())
        assertEquals("a key is on two pages", editable.size, pages.sumOf { it.size })
        // One currency: no exchange rate to set.
        assertFalse("RATE" in SettingsPage.MONEY.keys(single = true))
        assertTrue(SettingsPage.entries.all { it.title.isNotBlank() && it.subtitle.isNotBlank() })
    }

    @Test fun aPageSavesOnlyWhatChangedOnIt() {
        val draft = SettingsDraft(SettingsPage.MONEY, single = true)
        draft.seed(settings())
        assertFalse(draft.hasChanges)
        // Shown as 45 (%), saved as 0.45, as the sheet keeps it.
        assertEquals("45", draft.values["SPLIT_ESSENTIALS"])

        draft.values["DAILY_LIMIT"] = "60"
        draft.values["SPLIT_ESSENTIALS"] = "50"
        // Typing the same number back isn't a change; a comma works as the decimal point.
        draft.values["SALARY"] = "9000,0"
        // A key from another page can't sneak into this page's save.
        draft.values["WATER_TARGET"] = "4"

        assertTrue(draft.hasChanges)
        assertEquals(mapOf("DAILY_LIMIT" to 60.0, "SPLIT_ESSENTIALS" to 0.5), draft.changes)
    }

    @Test fun clearingAFieldIsNotSaved() {
        val draft = SettingsDraft(SettingsPage.STUDY, single = true)
        draft.seed(settings())
        draft.values["STUDY_DAILY_TARGET"] = ""
        assertFalse(draft.hasChanges)
    }

    @Test fun morningAndEveningRemindersAreSeparate() {
        // With the sheet, the morning slot is the briefing; without it, the daily smile.
        assertEquals(MorningSlot.BRIEFING, MorningSlot.of(sheetConnected = true, briefingOn = true, morningOn = false))
        assertEquals(MorningSlot.OFF, MorningSlot.of(sheetConnected = true, briefingOn = false, morningOn = true))
        assertEquals(MorningSlot.SMILE, MorningSlot.of(sheetConnected = false, briefingOn = true, morningOn = true))
        assertEquals(MorningSlot.OFF, MorningSlot.of(sheetConnected = false, briefingOn = true, morningOn = false))
        // Before, the morning reminder only came with Daily reminders on: upgrading keeps that.
        assertFalse(MorningSlot.upgraded(remindersOn = false, morningOn = true))
        assertTrue(MorningSlot.upgraded(remindersOn = true, morningOn = true))
        assertFalse(MorningSlot.upgraded(remindersOn = true, morningOn = false))
    }

    @Test fun notificationsRowSaysHowManyAreOn() {
        val prefs = mutableMapOf<String, Boolean>()
        // The same defaults the phone uses: the sheet's alerts on, the evening one off.
        val isOn = { key: String -> prefs[key] ?: (key != NotificationSummary.EVENING) }
        assertEquals(6 to 7, NotificationSummary.count(isOn, sheetConnected = true))
        prefs[SheetAlertPrefs.BILLS] = false
        prefs[NotificationSummary.EVENING] = true
        assertEquals(6 to 7, NotificationSummary.count(isOn, sheetConnected = true))
        // Without a sheet only the morning and evening reminders apply.
        prefs[NotificationSummary.MORNING] = false
        assertEquals(1 to 2, NotificationSummary.count(isOn, sheetConnected = false))

        assertEquals("All on", NotificationSummary.text(7, 7))
        assertEquals("All off", NotificationSummary.text(0, 7))
        assertEquals("3 of 7 on", NotificationSummary.text(3, 7))
    }

    @Test fun sheetRowSaysWhetherItIsConnected() {
        assertEquals("Not connected — tap to connect", SheetStatus.subtitle(connected = false, account = null, synced = null))
        assertEquals("Connected · me@gmail.com", SheetStatus.subtitle(connected = true, account = "me@gmail.com", synced = null))
        assertEquals("Connected · synced just now", SheetStatus.subtitle(connected = true, account = null, synced = "just now"))
    }

    @Test fun yourPlanNeverAsksToConnectAnAlreadyConnectedSheet() {
        // Signed in but the sheet hasn't arrived yet: say so, don't offer to connect again.
        assertEquals(PlanState.Loading, PlanState.of(connected = true, loaded = false, loading = true, error = null))
        assertEquals(PlanState.Loading, PlanState.of(connected = true, loaded = false, loading = false, error = null))
        assertEquals(PlanState.Failed("Offline"), PlanState.of(connected = true, loaded = false, loading = false, error = "Offline"))
        assertEquals(PlanState.Ready, PlanState.of(connected = true, loaded = true, loading = true, error = "Offline"))
        // Not connected: the Your sheet row above is the one way to connect.
        assertEquals(PlanState.Hidden, PlanState.of(connected = false, loaded = false, loading = false, error = null))
        assertEquals(PlanState.Hidden, PlanState.of(connected = false, loaded = true, loading = false, error = null))
    }

    @Test fun thereIsOneSettingsScreen() {
        // The old reminders-only Settings (from Today) is gone; Today opens the same Settings.
        assertFalse(File("src/main/java/com/mohammedsharukms/mypersonaltracker/ui/settings/SettingsScreen.kt").exists())
        val main = File("src/main/java/com/mohammedsharukms/mypersonaltracker/MainActivity.kt").readText()
        assertFalse(main.contains("ui.settings.SettingsScreen"))
        // Every tab's top bar has its own Settings button, next to More (not inside it).
        val frame = File("src/main/java/com/mohammedsharukms/mypersonaltracker/sheet/ui/SheetScaffold.kt").readText()
        assertTrue(frame.contains("LocalOpenSettings.current"))
        assertTrue(frame.contains("contentDescription = \"Settings\""))
        assertTrue(frame.contains("contentDescription = \"More\""))
        assertTrue(main.contains("LocalOpenSettings provides"))
        // One way to connect: the Your sheet row, not a second "Connect your sheet" row under it.
        val hub = File("src/main/java/com/mohammedsharukms/mypersonaltracker/sheet/ui/SettingsScreens.kt").readText()
        assertFalse(hub.contains("\"Connect your sheet\""))
    }
}
