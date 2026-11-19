package com.mohammedsharukms.mypersonaltracker.sheet

import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetDate
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

// Parents ▸ Trip (31_Parents.gs ▸ Take Home: what to bring on the next trip home,
// with a countdown), Parents ▸ Health (Parent Health: the readings they tell you
// on calls), and what went to them this month. Mirrors ParentHomeModels.swift.

data class TakeHomeItem(
    val id: String = UUID.randomUUID().toString(),
    val item: String = "",
    val who: ParentWho = ParentWho.MOM,
    val got: Boolean = false,
    val notes: String? = null,
    val added: String? = LocalDate.now().toString(),
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("item", item).put("who", who.raw).put("got", got)
        .put("notes", notes ?: "").put("added", added ?: "")

    companion object {
        fun from(o: JSONObject): TakeHomeItem? = TakeHomeItem(
            o.str("id") ?: return null, o.str("item") ?: return null, ParentWho.of(o.str("who")),
            o.opt("got").let { it == true || it?.toString() == "Y" || it?.toString() == "true" }, o.str("notes"), o.str("added"),
        )
    }
}

data class TakeHomeData(
    /** The next trip home, "yyyy-MM-dd" (null = none planned). */
    val trip: String? = null,
    val items: List<TakeHomeItem> = emptyList(),
) {
    fun daysToTrip(from: LocalDate = LocalDate.now()): Int? =
        trip?.let { runCatching { ChronoUnit.DAYS.between(from, LocalDate.parse(it.take(10))).toInt() }.getOrNull() }?.takeIf { it >= 0 }
    val sorted: List<TakeHomeItem> get() = items.sortedWith(compareBy({ it.got }, { it.added ?: "" }))
    val toGet: Int get() = items.count { !it.got }
    fun countdown(from: LocalDate = LocalDate.now()): String? = when (val d = daysToTrip(from)) {
        null -> null
        0 -> "Trip home today"
        1 -> "Trip home tomorrow"
        else -> "Trip home in $d days"
    }

    companion object {
        fun parse(o: JSONObject?): TakeHomeData? = o?.let {
            val items = it.optJSONArray("items")
            TakeHomeData(it.str("trip"), if (items == null) emptyList() else (0 until items.length()).mapNotNull { i -> items.optJSONObject(i)?.let(TakeHomeItem::from) })
        }
    }
}

enum class ReadingKind(val label: String, val unit: String, val short: String) {
    BLOOD_PRESSURE("Blood pressure", "mmHg", "BP"),
    SUGAR_FASTING("Sugar (fasting)", "mg/dL", "Sugar fasting"),
    SUGAR_AFTER_FOOD("Sugar (after food)", "mg/dL", "Sugar after food"),
    WEIGHT("Weight", "kg", "Weight"),
    PULSE("Pulse", "bpm", "Pulse");

    val hasSecond: Boolean get() = this == BLOOD_PRESSURE
    companion object { fun of(s: String?) = entries.firstOrNull { it.label == s } ?: BLOOD_PRESSURE }
}

data class ParentReading(
    val id: String = UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val time: String? = null,
    val who: ParentWho = ParentWho.DAD,
    val kind: ReadingKind = ReadingKind.BLOOD_PRESSURE,
    val value: Double = 0.0,
    /** Diastolic, for blood pressure. */
    val value2: Double? = null,
    val note: String? = null,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("date", date).put("time", time ?: "").put("who", who.raw)
        .put("kind", kind.label).put("value", value).put("value2", value2 ?: "").put("note", note ?: "")

    /** "138/88 mmHg", "112 mg/dL". */
    val display: String get() = (if (kind.hasSecond) "${SheetFormat.plain(value, 1)}/${SheetFormat.plain(value2 ?: 0.0, 1)}" else SheetFormat.plain(value, 1)) + " ${kind.unit}"

    companion object {
        fun from(o: JSONObject): ParentReading? = ParentReading(
            o.str("id") ?: return null, o.str("date") ?: return null, o.str("time"), ParentWho.of(o.str("who")).let { if (it == ParentWho.BOTH) ParentWho.DAD else it },
            ReadingKind.of(o.str("kind")), o.dbl("value") ?: return null, o.dbl("value2"), o.str("note"),
        )
    }
}

val SheetSnapshot.takeHomeData: TakeHomeData get() = takeHome ?: TakeHomeData()
/** Newest first. */
val SheetSnapshot.readingList: List<ParentReading> get() = parentHealth.sortedWith(compareByDescending<ParentReading> { it.date }.thenByDescending { it.time ?: "" })
/** One kind for one person, oldest first (for the chart). */
fun SheetSnapshot.readings(kind: ReadingKind, who: ParentWho) = readingList.filter { it.kind == kind && it.who == who }.reversed()

data class ParentMoney(val sent: Double, val sentHome: Double, val spent: Double) { val total: Double get() = sent + spent }

/** What went to Mom & Dad in [month]: money sent home (Remittance) and what you spent for them from the Parents tab. */
fun SheetSnapshot.parentMoney(month: String = LocalDate.now().toString().take(7)): ParentMoney {
    val sent = expenses.filter { (it.date ?: "").startsWith(month) && it.category == "Remittance" }
    return ParentMoney(sent.sumOf { it.qar ?: 0.0 }, sent.sumOf { it.inr ?: 0.0 }, parentSpend(month))
}

/** Their readings as text, for the doctor: the latest first, with the average. */
object ParentHealthShare {
    fun text(s: SheetSnapshot, who: ParentWho, days: Int = 30, today: LocalDate = LocalDate.now()): String {
        val since = today.minusDays(days.toLong()).toString()
        val lines = mutableListOf("${who.raw}'s readings — last $days days")
        ReadingKind.entries.forEach { kind ->
            val list = s.readings(kind, who).filter { it.date >= since }
            if (list.isEmpty()) return@forEach
            lines += ""
            lines += "${kind.label} (${kind.unit})"
            list.reversed().forEach { r -> lines += "• ${SheetDate.weekdayDay(r.date)}${r.time?.let { " $it" } ?: ""}: ${r.display}${r.note?.let { " — $it" } ?: ""}" }
            val avg = list.map { it.value }.average()
            val second = list.mapNotNull { it.value2 }
            val avg2 = if (kind.hasSecond && second.isNotEmpty()) "/" + SheetFormat.plain(second.average(), 0) else ""
            lines += "Average: ${SheetFormat.plain(avg, 0)}$avg2 over ${list.size} reading${if (list.size == 1) "" else "s"}"
        }
        if (lines.size == 1) lines += "No readings yet."
        return lines.joinToString("\n")
    }
}
