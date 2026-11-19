package com.mohammedsharukms.mypersonaltracker.sheet

import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// The rest of the Parents tab: their medicines (31_Parents.gs ▸ Parent Medicines)
// counted down to the next refill, their important days (Parent Dates) every year,
// how long since you last talked, and the one daily "Mom & Dad" notification.
// Mirrors ParentCareModels.swift.

// MARK: - Medicines

data class ParentMedicine(
    val id: String = UUID.randomUUID().toString(),
    val who: ParentWho = ParentWho.DAD,
    val name: String = "",
    /** As they take it, e.g. "1 morning, 1 night". */
    val dose: String? = null,
    /** Tablets (or doses) a day. */
    val perDay: Double = 1.0,
    /** How many there were on [counted]. */
    val left: Double = 30.0,
    val counted: String = LocalDate.now().toString(),
    /** Warn this many days before they run out. */
    val warn: Int = 7,
    val pack: Double? = null,
    val cost: Double? = null,
    val finance: Boolean = false,
    val stopped: Boolean = false,
    val notes: String? = null,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("who", who.raw).put("name", name).put("dose", dose ?: "")
        .put("perDay", perDay).put("left", left).put("counted", counted).put("warn", warn).put("pack", pack ?: "")
        .put("cost", cost ?: "").put("finance", finance).put("stopped", stopped).put("notes", notes ?: "")

    /** How many are left on [day], counting down from the last count. */
    fun leftOn(day: LocalDate = LocalDate.now()): Double {
        val start = runCatching { LocalDate.parse(counted.take(10)) }.getOrNull() ?: return left
        return maxOf(0.0, left - perDay * maxOf(ChronoUnit.DAYS.between(start, day), 0))
    }

    /** Whole days they last from [day] (null when not counting down). */
    fun daysLeft(day: LocalDate = LocalDate.now()): Int? = if (stopped || perDay <= 0) null else Math.floor(leftOn(day) / perDay).toInt()
    fun runsLow(day: LocalDate = LocalDate.now()) = (daysLeft(day) ?: Int.MAX_VALUE) <= warn
    fun refilled(bought: Double, day: LocalDate = LocalDate.now()) = copy(left = leftOn(day) + bought, counted = day.toString())

    /** "18 left · 9 days", "Runs out tomorrow", "Ran out". */
    fun status(day: LocalDate = LocalDate.now()): String {
        val count = "${SheetFormat.plain(leftOn(day), 1)} left"
        val days = daysLeft(day) ?: return if (stopped) "Stopped" else count
        return when {
            days < 1 -> if (leftOn(day) <= 0) "Ran out" else "Runs out today"
            days == 1 -> "$count · runs out tomorrow"
            else -> "$count · $days days"
        }
    }

    companion object {
        private fun flag(v: Any?) = v == true || v?.toString() == "Y" || v?.toString() == "true"
        fun from(o: JSONObject): ParentMedicine? = ParentMedicine(
            o.str("id") ?: return null, ParentWho.of(o.str("who")), o.str("name") ?: return null, o.str("dose"),
            maxOf(o.dbl("perDay") ?: 1.0, 0.0), maxOf(o.dbl("left") ?: 0.0, 0.0), o.str("counted") ?: LocalDate.now().toString(),
            o.dbl("warn")?.toInt() ?: 7, o.dbl("pack"), o.dbl("cost"), flag(o.opt("finance")), flag(o.opt("stopped")), o.str("notes"),
        )
    }
}

// MARK: - Important dates

enum class ParentDateKind(val label: String, val emoji: String) {
    BIRTHDAY("Birthday", "🎂"), ANNIVERSARY("Anniversary", "💐"), REMEMBRANCE("Remembrance", "🪔"), OTHER("Other", "📅");
    companion object { fun of(s: String?) = entries.firstOrNull { it.label == s } ?: OTHER }
}

data class ParentDate(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val who: ParentWho = ParentWho.MOM,
    val kind: ParentDateKind = ParentDateKind.BIRTHDAY,
    /** "MM-dd", every year. */
    val day: String = LocalDate.now().toString().drop(5),
    /** The first one (birth year, wedding year), when known. */
    val year: Int? = null,
    /** Days before to remind. */
    val remind: List<Int> = listOf(7, 1, 0),
    val notes: String? = null,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("who", who.raw).put("kind", kind.label)
        .put("day", day).put("year", year ?: "").put("remind", remind.joinToString(",")).put("notes", notes ?: "")

    /** Its next day on or after [from] (29 Feb falls on 28 Feb in other years). */
    fun next(from: LocalDate = LocalDate.now()): LocalDate? {
        val parts = day.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2 || parts[0] !in 1..12) return null
        for (y in listOf(from.year, from.year + 1)) {
            val first = LocalDate.of(y, parts[0], 1)
            val date = first.withDayOfMonth(minOf(parts[1].coerceAtLeast(1), first.lengthOfMonth()))
            if (!date.isBefore(from)) return date
        }
        return null
    }

    fun daysUntil(from: LocalDate = LocalDate.now()): Int? = next(from)?.let { ChronoUnit.DAYS.between(from, it).toInt() }
    fun years(from: LocalDate = LocalDate.now()): Int? = year?.let { y -> next(from)?.let { it.year - y } }?.takeIf { it > 0 }

    /** "Mom's birthday — turns 60", "Anniversary — 35th", or the title. */
    fun headline(from: LocalDate = LocalDate.now()): String {
        val n = years(from) ?: return title
        return when (kind) {
            ParentDateKind.BIRTHDAY -> "$title — turns $n"
            ParentDateKind.ANNIVERSARY -> "$title — ${ordinal(n)}"
            ParentDateKind.REMEMBRANCE -> "$title — $n year${if (n == 1) "" else "s"}"
            ParentDateKind.OTHER -> title
        }
    }

    /** "Today", "Tomorrow", "In 7 days", "24 Oct". */
    fun whenText(from: LocalDate = LocalDate.now()): String {
        val days = daysUntil(from) ?: return day
        return when {
            days == 0 -> "Today"
            days == 1 -> "Tomorrow"
            days <= 30 -> "In $days days"
            else -> next(from)?.format(DateTimeFormatter.ofPattern("d MMM")) ?: day
        }
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    companion object {
        fun from(o: JSONObject): ParentDate? = ParentDate(
            o.str("id") ?: return null, o.str("title") ?: return null, ParentWho.of(o.str("who")), ParentDateKind.of(o.str("kind")),
            o.str("day") ?: return null, o.dbl("year")?.toInt()?.takeIf { it > 0 },
            (o.str("remind") ?: "7,1,0").split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.distinct().sortedDescending().ifEmpty { listOf(7, 1, 0) },
            o.str("notes"),
        )
    }
}

val SheetSnapshot.medicineList: List<ParentMedicine>
    get() = parentMedicines.sortedWith(compareBy({ it.stopped }, { it.daysLeft() ?: Int.MAX_VALUE }, { it.name }))
val SheetSnapshot.parentDateList: List<ParentDate> get() = parentDates.sortedBy { it.daysUntil() ?: 999 }

// MARK: - Talking to them

object ParentTalk {
    /** Days since [last] as of [day] (null = never noted). */
    fun daysSince(last: String?, day: LocalDate): Int? =
        last?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), day).toInt() }.getOrNull() }

    fun text(last: String?, day: LocalDate = LocalDate.now()): String? = when (val d = daysSince(last, day)) {
        null -> null
        in Int.MIN_VALUE..0 -> "Talked today"
        1 -> "Talked yesterday"
        else -> "Talked $d days ago"
    }
}

// MARK: - The daily Mom & Dad notification

object ParentDigest {
    const val DEFAULT_MINUTES = 18 * 60

    /** What's worth saying on [day]: medicines running low, their days coming up, and a call that's due. */
    fun lines(s: SheetSnapshot, day: LocalDate, last: Map<ParentWho, String>, every: Map<ParentWho, Int>): List<String> {
        val lines = mutableListOf<String>()
        s.medicineList.filter { it.runsLow(day) }.forEach { m ->
            val who = if (m.who == ParentWho.BOTH) "" else "${m.who.raw}'s "
            lines += "💊 $who${m.name}: ${m.status(day).lowercase().replace(" · ", ", ")}"
        }
        s.parentDateList.forEach { d ->
            val days = d.daysUntil(day) ?: return@forEach
            if (days in d.remind) lines += "${d.kind.emoji} ${d.headline(day)} · ${d.whenText(day).lowercase()}"
        }
        val home = s.takeHomeData
        val toTrip = home.daysToTrip(day)
        if (toTrip != null && toTrip in listOf(14, 7, 3, 1, 0) && home.toGet > 0) {
            lines += "🧳 ${home.countdown(day)} — ${home.toGet} thing${if (home.toGet == 1) "" else "s"} still to get"
        }
        listOf(ParentWho.MOM, ParentWho.DAD).forEach { who ->
            val want = every[who] ?: 0
            if (want <= 0) return@forEach
            val since = ParentTalk.daysSince(last[who], day)
            if (since != null && since < want) return@forEach
            lines += if (since != null) "📞 You haven't talked to ${who.raw} in $since day${if (since == 1) "" else "s"}" else "📞 Give ${who.raw} a call"
        }
        return lines
    }

    fun message(lines: List<String>): Pair<String, String> =
        if (lines.size == 1) "Mom & Dad" to lines[0] else "Mom & Dad · ${lines.size} things" to lines.joinToString("\n")
}

/** What's still to do for them, to send to a sibling (or to them). */
object ParentShare {
    fun text(s: SheetSnapshot, who: ParentWho? = null, today: LocalDate = LocalDate.now()): String {
        fun matches(w: ParentWho) = who == null || w == who || w == ParentWho.BOTH
        val open = s.openParentAsks.filter { matches(it.who) }
        val lines = mutableListOf("Still to do for ${who?.label ?: "Mom & Dad"}:")
        if (open.isEmpty()) lines += "Nothing — all sorted ✅"
        open.forEach { a -> lines += "• ${a.task} — ${a.who.label}, ${ParentAsk.whenText(a, today).lowercase()}${if (a.isLate(today)) " (late)" else ""}" }
        val low = s.medicineList.filter { it.runsLow(today) && matches(it.who) }
        if (low.isNotEmpty()) {
            lines += ""
            lines += "Medicines to refill:"
            low.forEach { lines += "• ${it.name} (${it.who.label}) — ${it.status(today).lowercase()}" }
        }
        return lines.joinToString("\n")
    }
}
