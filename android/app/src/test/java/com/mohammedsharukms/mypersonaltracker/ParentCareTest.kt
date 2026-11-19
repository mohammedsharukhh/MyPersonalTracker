package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.ParentDate
import com.mohammedsharukms.mypersonaltracker.sheet.ParentDateKind
import com.mohammedsharukms.mypersonaltracker.sheet.ParentDigest
import com.mohammedsharukms.mypersonaltracker.sheet.ParentMedicine
import com.mohammedsharukms.mypersonaltracker.sheet.ParentShare
import com.mohammedsharukms.mypersonaltracker.sheet.ParentWho
import com.mohammedsharukms.mypersonaltracker.sheet.SheetJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParentCareTest {
    private fun d(text: String) = LocalDate.parse(text)
    private fun snapshot(extra: String) = SheetJson.snapshot(JSONObject("""{"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
        "budget":{"headers":[],"rows":[]},"days":[],"expenses":[],"salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}$extra}"""))

    @Test fun medicinesCountDownAndRefill() {
        val m = ParentMedicine(who = ParentWho.DAD, name = "Telmisartan", perDay = 2.0, left = 30.0, counted = "2026-10-10", warn = 7, pack = 30.0)
        assertEquals(20.0, m.leftOn(d("2026-10-15")), 0.0)
        assertEquals(10, m.daysLeft(d("2026-10-15")))
        assertFalse(m.runsLow(d("2026-10-15")))
        assertTrue(m.runsLow(d("2026-10-18")))
        assertEquals("Ran out", m.status(d("2026-11-30")))
        val r = m.refilled(30.0, d("2026-10-18"))
        assertEquals(44.0, r.left, 0.0); assertEquals("2026-10-18", r.counted)
        assertNull(m.copy(stopped = true).daysLeft(d("2026-10-18")))
        assertEquals(m, ParentMedicine.from(m.toJson()))
    }

    @Test fun datesComeRoundEveryYear() {
        val b = ParentDate(title = "Mom's birthday", who = ParentWho.MOM, kind = ParentDateKind.BIRTHDAY, day = "10-24", year = 1966)
        assertEquals(7, b.daysUntil(d("2026-10-17")))
        assertEquals("Mom's birthday — turns 60", b.headline(d("2026-10-17")))
        assertEquals(364, b.daysUntil(d("2026-10-25")))
        assertEquals(d("2027-02-28"), ParentDate(title = "x", day = "02-29").next(d("2027-01-10")))
        assertTrue(ParentDate(title = "A", kind = ParentDateKind.ANNIVERSARY, day = "11-02", year = 1991).headline(d("2026-10-17")).endsWith("35th"))
        val sheet = ParentDate.from(JSONObject("""{"id":"1","title":"x","who":"Dad","kind":"Remembrance","day":"08-03","year":"","remind":"1,0"}"""))!!
        assertNull(sheet.year); assertEquals(listOf(1, 0), sheet.remind)
    }

    @Test fun theDailyNotificationSaysWhatsDue() {
        val s = snapshot(""","parentMedicines":[{"id":"m","who":"Dad","name":"Telmisartan","perDay":1,"left":5,"counted":"2026-10-17","warn":7}],
            "parentDates":[{"id":"d","title":"Mom's birthday","who":"Mom","kind":"Birthday","day":"10-24","year":1966,"remind":"7,1,0"}]""")
        val lines = ParentDigest.lines(s, d("2026-10-17"), mapOf(ParentWho.MOM to "2026-10-12"), mapOf(ParentWho.MOM to 3))
        assertTrue(lines.toString(), lines.any { it.startsWith("💊 Dad's Telmisartan") && it.contains("5 days") })
        assertTrue(lines.contains("🎂 Mom's birthday — turns 60 · in 7 days"))
        assertTrue(lines.contains("📞 You haven't talked to Mom in 5 days"))
        assertEquals("Mom & Dad · 3 things", ParentDigest.message(lines).first)
        assertFalse(ParentDigest.lines(s, d("2026-10-17"), mapOf(ParentWho.MOM to "2026-10-16"), mapOf(ParentWho.MOM to 3)).any { it.startsWith("📞") })
    }

    @Test fun shareSaysWhatsStillToDo() {
        val s = snapshot(""","parents":[{"id":"1","task":"Book eye check-up","who":"Dad","date":"2026-10-15","repeats":"Once"},
            {"id":"2","task":"Send photos","who":"Mom","date":"2026-10-17","repeats":"Once","done":"2026-10-17"}]""")
        val text = ParentShare.text(s, today = d("2026-10-17"))
        assertTrue(text, text.startsWith("Still to do for Mom & Dad:") && text.contains("• Book eye check-up — Dad, 2 days late (late)") && !text.contains("Send photos"))
    }
}
