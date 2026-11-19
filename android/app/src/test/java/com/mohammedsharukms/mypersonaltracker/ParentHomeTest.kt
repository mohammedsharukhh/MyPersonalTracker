package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.ParentDigest
import com.mohammedsharukms.mypersonaltracker.sheet.ParentHealthShare
import com.mohammedsharukms.mypersonaltracker.sheet.ParentMoney
import com.mohammedsharukms.mypersonaltracker.sheet.ParentWho
import com.mohammedsharukms.mypersonaltracker.sheet.PendingWrite
import com.mohammedsharukms.mypersonaltracker.sheet.ReadingKind
import com.mohammedsharukms.mypersonaltracker.sheet.SheetJson
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.parentMoney
import com.mohammedsharukms.mypersonaltracker.sheet.readingList
import com.mohammedsharukms.mypersonaltracker.sheet.readings
import com.mohammedsharukms.mypersonaltracker.sheet.takeHomeData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParentHomeTest {
    private fun d(text: String) = LocalDate.parse(text)
    private fun snapshot(extra: String) = SheetJson.snapshot(JSONObject("""{"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
        "budget":{"headers":[],"rows":[]},"days":[],"salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}$extra}"""))

    @Test fun tripCountsDownAndNudges() {
        var s = snapshot(""","expenses":[],"takeHome":{"trip":"2026-10-24","items":[{"id":"1","item":"Dates","who":"Mom","got":false},{"id":"2","item":"Perfume","who":"Mom","got":"Y"}]}""")
        val home = s.takeHomeData
        assertEquals(7, home.daysToTrip(d("2026-10-17")))
        assertEquals("Trip home tomorrow", home.countdown(d("2026-10-23")))
        assertNull(home.daysToTrip(d("2026-10-25")))
        assertEquals(1, home.toGet); assertEquals("Dates", home.sorted.first().item)
        assertEquals(listOf("🧳 Trip home in 7 days — 1 thing still to get"), ParentDigest.lines(s, d("2026-10-17"), emptyMap(), emptyMap()))
        assertTrue(ParentDigest.lines(s, d("2026-10-18"), emptyMap(), emptyMap()).isEmpty())
        s = SheetStore.applyLocally(PendingWrite("w", "saveTakeHomeTrip", """{"date":""}""", "", 0, null, null), s)
        assertNull(s.takeHomeData.trip)
    }

    @Test fun readingsChartAndDoctorSummary() {
        val s = snapshot(""","expenses":[],"parentHealth":[{"id":"1","date":"2026-10-10","who":"Dad","kind":"Blood pressure","value":140,"value2":90},
            {"id":"2","date":"2026-10-16","time":"08:30","who":"Dad","kind":"Blood pressure","value":130,"value2":84},
            {"id":"3","date":"2026-10-16","who":"Mom","kind":"Sugar (fasting)","value":"110"}]""")
        assertEquals(listOf(140.0, 130.0), s.readings(ReadingKind.BLOOD_PRESSURE, ParentWho.DAD).map { it.value })
        assertEquals("130/84 mmHg", s.readingList.first().display)
        val text = ParentHealthShare.text(s, ParentWho.DAD, today = d("2026-10-17"))
        assertTrue(text, text.startsWith("Dad's readings — last 30 days") && text.contains("Average: 135/87 over 2 readings") && !text.contains("Sugar"))
    }

    @Test fun moneyForThemThisMonth() {
        val s = snapshot(""","expenses":[{"row":5,"date":"2026-10-02","category":"Remittance","qar":1500,"inr":39150},
            {"row":6,"date":"2026-10-03","category":"Gifts & Family","qar":85,"notes":"Mom & Dad"},
            {"row":7,"date":"2026-09-28","category":"Remittance","qar":1000,"inr":26100}]""")
        assertEquals(ParentMoney(1500.0, 39150.0, 85.0), s.parentMoney("2026-10"))
        assertFalse(s.parentMoney("2026-08").total > 0)
    }
}
