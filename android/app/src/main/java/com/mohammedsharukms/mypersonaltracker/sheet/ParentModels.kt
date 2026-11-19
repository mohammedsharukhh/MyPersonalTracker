package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

// Parents (31_Parents.gs): what Mom and Dad asked you to do, the day to do it,
// and when to be reminded — every day until it's done, if you like. Something
// that comes back moves on to its next date when it's done. Mirrors ParentModels.swift.

enum class ParentWho(val raw: String, val label: String) {
    MOM("Mom", "Mom"), DAD("Dad", "Dad"), BOTH("Both", "Mom & Dad");

    companion object { fun of(s: String?) = entries.firstOrNull { it.raw == s } ?: BOTH }
}

enum class ParentRepeat(val label: String) {
    ONCE("Once"), DAILY("Every day"), WEEKLY("Every week"), MONTHLY("Every month");

    companion object { fun of(s: String?) = entries.firstOrNull { it.label == s } ?: ONCE }
}

data class ParentAsk(
    val id: String = UUID.randomUUID().toString(),
    val task: String = "",
    val who: ParentWho = ParentWho.MOM,
    /** The day to do it; for a repeating one, the next time it's due. */
    val date: String = LocalDate.now().toString(),
    /** "09:00" — when to be reminded; null = on the list, no notification. */
    val time: String? = DEFAULT_TIME,
    val repeats: ParentRepeat = ParentRepeat.ONCE,
    /** Remind every day at [time] from [date] on, until it's done. */
    val nag: Boolean = true,
    /** The day they asked. */
    val asked: String? = LocalDate.now().toString(),
    /** The day it was done (a repeating one: the last time). */
    val done: String? = null,
    val notes: String? = null,
    /** Up to three: Drive ids, or "local:<key>" for one not uploaded yet. */
    val photos: List<String> = emptyList(),
    /** What it cost (main currency), e.g. the medicines. */
    val cost: Double? = null,
    /** Logs [cost] in Finance (Gifts & Family) each time it's done. */
    val finance: Boolean = false,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("task", task).put("who", who.raw).put("date", date)
        .put("time", time ?: "").put("repeats", repeats.label).put("nag", nag).put("asked", asked ?: "")
        .put("done", done ?: "").put("notes", notes ?: "").put("photos", org.json.JSONArray(photos))
        .put("cost", cost ?: "").put("finance", finance)

    private val start: LocalDate? get() = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    /** Still to do: a one-off not done yet, or anything that repeats. */
    val isOpen: Boolean get() = repeats != ParentRepeat.ONCE || done == null

    fun daysLeft(today: LocalDate = LocalDate.now()): Long = start?.let { ChronoUnit.DAYS.between(today, it) } ?: 0
    fun isLate(today: LocalDate = LocalDate.now()) = isOpen && daysLeft(today) < 0

    /** Whether [day] is one of its dates: the first, then each repeat after it. */
    fun falls(day: LocalDate): Boolean {
        val s = start ?: return false
        val offset = ChronoUnit.DAYS.between(s, day)
        if (offset < 0) return false
        return when (repeats) {
            ParentRepeat.ONCE -> offset == 0L
            ParentRepeat.DAILY -> true
            ParentRepeat.WEEKLY -> offset % 7 == 0L
            // A 31st falls on the last day of shorter months.
            ParentRepeat.MONTHLY -> day.dayOfMonth == minOf(s.dayOfMonth, day.lengthOfMonth())
        }
    }

    /** Whether it sends a reminder on [day]: each of its dates, and with [nag] every day after until done. */
    fun reminds(day: LocalDate): Boolean {
        val s = start ?: return false
        if (!isOpen || time == null || day.isBefore(s)) return false
        return nag || falls(day)
    }

    /** A repeating one's next date after [today] (done early skips the coming one too). */
    fun nextDate(today: LocalDate = LocalDate.now()): String? {
        val s = start ?: return null
        if (repeats == ParentRepeat.ONCE) return null
        for (n in 1L..2000L) {
            val next = when (repeats) {
                ParentRepeat.DAILY -> s.plusDays(n)
                ParentRepeat.WEEKLY -> s.plusWeeks(n)
                else -> s.plusMonths(n)
            }
            if (next.isAfter(today)) return next.toString()
        }
        return null
    }

    /** Done on [today]: a one-off is finished, a repeating one moves on to its next date. */
    fun markedDone(today: LocalDate = LocalDate.now()) = copy(done = today.toString(), date = nextDate(today) ?: date)

    companion object {
        const val DEFAULT_TIME = "09:00"
        const val MAX_PHOTOS = 3

        private fun flag(v: Any?) = v == true || v?.toString() == "Y" || v?.toString() == "true"

        fun from(o: JSONObject): ParentAsk? = ParentAsk(
            o.str("id") ?: return null, o.str("task") ?: return null, ParentWho.of(o.str("who")), o.str("date") ?: return null,
            o.str("time"), ParentRepeat.of(o.str("repeats")), flag(o.opt("nag")),
            o.str("asked"), o.str("done"), o.str("notes"),
            photos = o.optJSONArray("photos")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }
                ?: (o.str("photos") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            cost = o.dbl("cost"), finance = flag(o.opt("finance")),
        )

        /** "6:30 PM" in the phone's own style. */
        fun timeText(t: String?): String? = t?.let {
            runCatching { LocalTime.parse(it.padStart(5, '0')).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }.getOrDefault(it)
        }

        /** "Today", "Tomorrow", "Thursday", "3 days late". */
        fun whenText(a: ParentAsk, today: LocalDate = LocalDate.now()): String {
            val days = a.daysLeft(today)
            return when {
                days < -1 -> "${-days} days late"
                days == -1L -> "Since yesterday"
                days == 0L -> "Today"
                days == 1L -> "Tomorrow"
                days <= 6 -> runCatching { LocalDate.parse(a.date.take(10)).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }.getOrDefault(a.date)
                else -> com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetDate.short(a.date)
            }
        }

        /** "Mom · Today · 9:00 AM · Every week" (or "Done 12 Oct 2026"). */
        fun detail(a: ParentAsk, today: LocalDate = LocalDate.now()): String =
            if (!a.isOpen) "${a.who.label} · Done ${com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetDate.short(a.done)}"
            else listOfNotNull(a.who.label, whenText(a, today), timeText(a.time), if (a.repeats == ParentRepeat.ONCE) null else a.repeats.label).joinToString(" · ")
    }
}

val SheetSnapshot.openParentAsks: List<ParentAsk>
    get() = parents.filter { it.isOpen }.sortedWith(compareBy({ it.date }, { it.time ?: "99" }))
val SheetSnapshot.doneParentAsks: List<ParentAsk>
    get() = parents.filter { !it.isOpen }.sortedByDescending { it.done ?: "" }
/** Open ones due on or before [day]. */
fun SheetSnapshot.parentAsksDue(day: String = LocalDate.now().toString()) = openParentAsks.filter { it.date <= day }

/** When the notifications go out and what they say. Reminders.kt posts them. */
object ParentReminders {
    /** What a request's cost is logged with in Finance. */
    const val FINANCE_NOTE = "Mom & Dad"

    /** Each reminder time in the next [days] days with what's due then, soonest first (same time = one notification).
     *  [snoozed] maps a request to the first day it reminds again ("Tomorrow" on a notification). */
    fun slots(asks: List<ParentAsk>, now: LocalDateTime, days: Int = 7, snoozed: Map<String, String> = emptyMap()): List<Pair<LocalDateTime, List<ParentAsk>>> {
        val byTime = sortedMapOf<LocalDateTime, MutableList<ParentAsk>>()
        for (offset in 0 until days) {
            val day = now.toLocalDate().plusDays(offset.toLong())
            asks.filter { it.reminds(day) && day.toString() >= (snoozed[it.id] ?: "") }.forEach { a ->
                val t = runCatching { LocalTime.parse(a.time!!.padStart(5, '0')) }.getOrNull() ?: return@forEach
                val fire = day.atTime(t)
                if (fire.isAfter(now)) byTime.getOrPut(fire) { mutableListOf() }.add(a)
            }
        }
        return byTime.map { it.key to it.value.toList() }
    }

    /** What a notification for [asks] on [day] says. */
    fun message(asks: List<ParentAsk>, day: LocalDate): Pair<String, String> {
        if (asks.size == 1) {
            val a = asks[0]
            val whenLine = if (a.daysLeft(day) < 0) "Still to do — it was for ${com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetDate.weekdayDay(a.date)}." else "It's for today."
            return "${a.who.label} asked: ${a.task}" to listOfNotNull(a.notes, "$whenLine Tap Done once it's sorted.").joinToString("\n")
        }
        val people = asks.map { it.who }.toSet()
        val who = when (people) { setOf(ParentWho.MOM) -> "Mom"; setOf(ParentWho.DAD) -> "Dad"; else -> "Mom & Dad" }
        return "${asks.size} things $who asked for" to asks.joinToString("\n") { if (people.size > 1) "• ${it.who.label}: ${it.task}" else "• ${it.task}" }
    }
}

/** Spent for Mom & Dad in [month] ("yyyy-MM"): costs logged from requests. */
fun SheetSnapshot.parentSpend(month: String = LocalDate.now().toString().take(7)): Double =
    expenses.filter { (it.date ?: "").startsWith(month) && it.notes == ParentReminders.FINANCE_NOTE }.sumOf { it.qar ?: 0.0 }

/** Calling or messaging whoever asked. The numbers themselves are kept on this phone (ParentPrefs). */
object ParentContacts {
    /** Who to contact about a request: both for "Mom & Dad". */
    fun people(who: ParentWho) = if (who == ParentWho.BOTH) listOf(ParentWho.MOM, ParentWho.DAD) else listOf(who)

    /** "+91 98765-43210" → "919876543210" (what WhatsApp's link wants). */
    fun digits(phone: String) = phone.filter { it.isDigit() }

    /** "Hi Mom, done ✅ …" once it's done, else a question about it. */
    fun message(a: ParentAsk, to: ParentWho, today: String = LocalDate.now().toString()) =
        if (!a.isOpen || a.done == today) "Hi ${to.raw}, done ✅ ${a.task}" else "Hi ${to.raw}, about \"${a.task}\" — "

    fun callUri(phone: String) = "tel:" + (if (phone.trim().startsWith("+")) "+" else "") + digits(phone)
    fun whatsAppUri(phone: String, text: String) = "https://wa.me/" + digits(phone) + "?text=" + java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    fun smsUri(phone: String) = "smsto:" + (if (phone.trim().startsWith("+")) "+" else "") + digits(phone)
}
