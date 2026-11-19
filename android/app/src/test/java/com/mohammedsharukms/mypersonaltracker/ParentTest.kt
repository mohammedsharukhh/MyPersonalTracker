package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.Briefing
import com.mohammedsharukms.mypersonaltracker.sheet.ParentAsk
import com.mohammedsharukms.mypersonaltracker.sheet.ParentContacts
import com.mohammedsharukms.mypersonaltracker.sheet.ParentPhotos
import com.mohammedsharukms.mypersonaltracker.sheet.parentSpend
import com.mohammedsharukms.mypersonaltracker.sheet.ParentReminders
import com.mohammedsharukms.mypersonaltracker.sheet.ParentRepeat
import com.mohammedsharukms.mypersonaltracker.sheet.ParentWho
import com.mohammedsharukms.mypersonaltracker.sheet.PendingWrite
import com.mohammedsharukms.mypersonaltracker.sheet.SheetJson
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.doneParentAsks
import com.mohammedsharukms.mypersonaltracker.sheet.openParentAsks
import com.mohammedsharukms.mypersonaltracker.sheet.parentAsksDue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParentTest {
    private fun d(text: String) = LocalDate.parse(text)

    @Test fun keepsRemindingEveryDayUntilDone() {
        val a = ParentAsk(task = "Book eye check-up", who = ParentWho.DAD, date = "2026-10-10", time = "09:00", nag = true)
        assertFalse(a.reminds(d("2026-10-09")))
        assertTrue(a.reminds(d("2026-10-10")))
        assertTrue(a.reminds(d("2026-10-14")))
        val done = a.markedDone(d("2026-10-14"))
        assertFalse(done.isOpen)
        assertFalse(done.reminds(d("2026-10-15")))
    }

    @Test fun withoutNaggingOnlyOnItsDates() {
        val weekly = ParentAsk(task = "Call Grandma", date = "2026-10-11", repeats = ParentRepeat.WEEKLY, nag = false)
        assertTrue(weekly.reminds(d("2026-10-18")))
        assertFalse(weekly.reminds(d("2026-10-17")))
        assertFalse(ParentAsk(task = "x", date = "2026-10-10", time = null).reminds(d("2026-10-10")))
    }

    @Test fun repeatingMovesToItsNextDate() {
        val weekly = ParentAsk(task = "Call Grandma", date = "2026-10-11", repeats = ParentRepeat.WEEKLY)
        assertEquals("2026-10-18", weekly.markedDone(d("2026-10-11")).date)
        assertEquals("2026-10-18", weekly.markedDone(d("2026-10-10")).date)
        assertEquals("2026-11-01", weekly.markedDone(d("2026-10-27")).date)
        assertTrue(weekly.markedDone(d("2026-10-11")).isOpen)
        val monthly = ParentAsk(task = "Tablets", date = "2026-01-31", repeats = ParentRepeat.MONTHLY)
        assertEquals("2026-02-28", monthly.markedDone(d("2026-01-31")).date)
        assertTrue(monthly.falls(d("2026-02-28")))
        assertFalse(monthly.falls(d("2026-02-27")))
    }

    @Test fun requestsAtTheSameTimeShareOneNotification() {
        val now = d("2026-10-17").atTime(8, 0)
        val asks = listOf(
            ParentAsk("a", "Pay electricity bill", ParentWho.MOM, "2026-10-17", "09:00", nag = false),
            ParentAsk("b", "Buy BP tablets", ParentWho.DAD, "2026-10-17", "09:00", nag = false),
            ParentAsk("c", "Send photos", ParentWho.MOM, "2026-10-17", "07:00", nag = false),
        )
        val slots = ParentReminders.slots(asks, now)
        assertEquals(1, slots.size)
        assertEquals(listOf("a", "b"), slots[0].second.map { it.id })
        val (title, body) = ParentReminders.message(slots[0].second, d("2026-10-17"))
        assertEquals("2 things Mom & Dad asked for", title)
        assertTrue(body.contains("Dad: Buy BP tablets"))
        assertEquals("Mom asked: Pay electricity bill", ParentReminders.message(listOf(asks[0]), d("2026-10-17")).first)
    }

    @Test fun readsTheSheetAndQueuesSaves() {
        val json = """
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"expenses":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "parents":[{"id":"1","task":"Later","who":"Mom","date":"2026-10-20","time":"09:00","repeats":"Once","nag":true},
                    {"id":"2","task":"Late","who":"Dad","date":"2026-10-12","time":null,"repeats":"Once","nag":"Y"},
                    {"id":"3","task":"Done","who":"Both","date":"2026-10-01","repeats":"Once","nag":false,"done":"2026-10-02"}]}
        """
        var s = SheetJson.snapshot(JSONObject(json))
        assertEquals(listOf("Late", "Later"), s.openParentAsks.map { it.task })
        assertEquals(listOf("Done"), s.doneParentAsks.map { it.task })
        assertTrue(s.parents.first { it.id == "2" }.nag)
        assertEquals(listOf("Late"), s.parentAsksDue("2026-10-17").map { it.task })
        assertTrue(Briefing.text(s, d("2026-10-17"), includeLive = false).second.contains("👪 Dad: Late"))
        val ask = ParentAsk("x", "Fix the fan", ParentWho.DAD, "2026-10-18")
        s = SheetStore.applyLocally(PendingWrite("w", "saveParentAsk", ask.toJson().toString(), "", 0, null, null), s)
        assertTrue(s.parents.first { it.id == "x" }.pending)
        s = SheetStore.applyLocally(PendingWrite("w2", "deleteParentAsk", """{"id":"x"}""", "", 0, null, null), s)
        assertFalse(s.parents.any { it.id == "x" })
    }

    @Test fun photosCostAndFinanceFromTheSheet() {
        val a = ParentAsk.from(JSONObject("""{"id":"1","task":"Tablets","date":"2026-10-17","photos":["a1","b2"],"cost":"85","finance":"Y"}"""))!!
        assertEquals(listOf("a1", "b2"), a.photos); assertEquals(85.0, a.cost!!, 0.0); assertTrue(a.finance)
        assertEquals(listOf("a1", "b2"), ParentAsk.from(JSONObject("""{"id":"1","task":"x","date":"2026-10-17","photos":"a1, b2"}"""))!!.photos)
        val old = ParentAsk.from(JSONObject("""{"id":"1","task":"x","date":"2026-10-17"}"""))!!
        assertTrue(old.photos.isEmpty() && old.cost == null && !old.finance)
        assertEquals(a, ParentAsk.from(a.toJson()))                       // round trip through the queue
        assertEquals("abc", ParentPhotos.localKey("local:abc"))
    }

    @Test fun tomorrowSkipsOneDayOnly() {
        val a = ParentAsk("a", "Call the plumber", ParentWho.MOM, "2026-10-15", "09:00", nag = true)
        val slots = ParentReminders.slots(listOf(a), d("2026-10-17").atTime(8, 0), days = 3, snoozed = mapOf("a" to "2026-10-18"))
        assertEquals(listOf("2026-10-18", "2026-10-19"), slots.map { it.first.toLocalDate().toString() })
    }

    @Test fun messagesLinksAndSpend() {
        val open = ParentAsk(task = "Book eye check-up", who = ParentWho.DAD, date = "2026-10-17")
        assertTrue(ParentContacts.message(open, ParentWho.DAD, "2026-10-17").startsWith("Hi Dad, about"))
        assertEquals("Hi Dad, done ✅ Book eye check-up", ParentContacts.message(open.markedDone(d("2026-10-17")), ParentWho.DAD, "2026-10-17"))
        assertEquals("https://wa.me/919876543210?text=Hi%20Dad", ParentContacts.whatsAppUri("+91 98765 43210", "Hi Dad"))
        assertEquals("tel:+919876543210", ParentContacts.callUri("+91 98765 43210"))
        val s = SheetJson.snapshot(JSONObject("""{"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
            "budget":{"headers":[],"rows":[]},"days":[],"salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
            "expenses":[{"row":5,"date":"2026-10-03","category":"Gifts & Family","qar":85,"notes":"Mom & Dad"},
                        {"row":6,"date":"2026-10-04","category":"Gifts & Family","qar":40,"notes":"Birthday"}]}"""))
        assertEquals(85.0, s.parentSpend("2026-10"), 0.0)
    }
}
