package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

// The week planner (23_Planner.gs): plans for each day, what to wear each day,
// and the wardrobe the outfits are picked from. Mirrors PlannerModels.swift.

data class PlannerData(
    val plans: List<DayPlan> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val wardrobe: List<Clothing> = emptyList(),
)

val SheetSnapshot.plannerData: PlannerData get() = planner ?: PlannerData()

/** Something on a day: "Cinema with Arun", 18:30, at City Centre. */
data class DayPlan(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    /** As typed, "18:30"; null = any time that day. */
    val time: String? = null,
    val title: String = "",
    val place: String? = null,
    val notes: String? = null,
    val done: Boolean = false,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("date", date).put("time", time ?: "").put("title", title)
        .put("place", place ?: "").put("notes", notes ?: "").put("done", done)
}

/** What to wear on a day: pieces from the wardrobe and a note. One per day. */
data class Outfit(
    val date: String,
    /** Wardrobe ids. */
    val items: List<String> = emptyList(),
    val note: String? = null,
    /** The names as the sheet last wrote them (kept for pieces since removed). */
    val names: String? = null,
    val id: String = idFor(date),
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("date", date).put("items", JSONArray(items)).put("note", note ?: "")

    companion object {
        fun idFor(date: String) = "outfit-$date"
    }
}

/** A piece of clothing you own. */
data class Clothing(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: ClothingType = ClothingType.TOP,
    val colour: String? = null,
    val notes: String? = null,
    val added: String? = LocalDate.now().toString(),
    val pending: Boolean = false,
) {
    /** "Navy Oxford shirt". */
    val label: String get() = listOfNotNull(colour, name).joinToString(" ")

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("type", type.label).put("colour", colour ?: "")
        .put("notes", notes ?: "").put("added", added ?: "")
}

enum class ClothingType(val label: String, val emoji: String) {
    TOP("Top", "👕"), BOTTOM("Bottom", "👖"), DRESS("Dress", "👗"), ETHNIC("Ethnic", "🥻"), OUTERWEAR("Outerwear", "🧥"),
    SHOES("Shoes", "👟"), ACCESSORY("Accessory", "⌚"), SPORTS("Sportswear", "🏃"), OTHER("Other", "🧺");

    companion object {
        fun of(label: String?): ClothingType = entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: OTHER
    }
}

object PlannerJson {
    fun parse(o: JSONObject?): PlannerData? {
        if (o == null) return null
        return PlannerData(
            plans = o.optJSONArray("plans").objs().mapNotNull(::plan),
            outfits = o.optJSONArray("outfits").objs().mapNotNull(::outfit),
            wardrobe = o.optJSONArray("wardrobe").objs().mapNotNull(::clothing),
        )
    }

    fun plan(p: JSONObject): DayPlan? = DayPlan(
        id = p.str("id") ?: return null, date = p.str("date") ?: return null, time = p.str("time"),
        title = p.str("title") ?: return null, place = p.str("place"), notes = p.str("notes"), done = flag(p.opt("done")),
    )

    fun outfit(p: JSONObject): Outfit? {
        val date = p.str("date") ?: return null
        val items = when (val v = p.opt("items")) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it).trim().ifEmpty { null } }
            is String -> v.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
        return Outfit(date, items, p.str("note"), p.str("names"), p.str("id") ?: Outfit.idFor(date))
    }

    fun clothing(p: JSONObject): Clothing? = Clothing(
        id = p.str("id") ?: return null, name = p.str("name") ?: return null, type = ClothingType.of(p.str("type")),
        colour = p.str("colour"), notes = p.str("notes"), added = p.str("added"),
    )

    private fun flag(v: Any?): Boolean = v == true || v?.toString() == "Y" || v?.toString() == "true"

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/** A day's plans: timed ones in order, then "any time". */
fun PlannerData.plansOn(date: String): List<DayPlan> =
    plans.filter { it.date == date }.sortedWith(compareBy<DayPlan>({ it.time == null }, { it.time ?: "" }, { it.title }))

fun PlannerData.outfitOn(date: String): Outfit? = outfits.firstOrNull { it.date == date }

fun PlannerData.clothing(id: String): Clothing? = wardrobe.firstOrNull { it.id == id }

/** The pieces of an outfit, by name; pieces since removed fall back to the sheet's text. */
fun PlannerData.pieces(outfit: Outfit): List<String> {
    val found = outfit.items.mapNotNull { clothing(it)?.label }
    if (found.size == outfit.items.size || outfit.names == null) return found
    return outfit.names.split(" + ")
}

/** The last day before [date] this piece was worn. */
fun PlannerData.lastWorn(id: String, before: String = LocalDate.now().toString()): String? =
    outfits.filter { it.date < before && id in it.items }.maxOfOrNull { it.date }

/** Times worn up to today (outfits planned for later don't count yet). */
fun PlannerData.timesWorn(id: String, today: String = LocalDate.now().toString()): Int = outfits.count { it.date <= today && id in it.items }

/** The next day it's planned to be worn, from today on. */
fun PlannerData.nextPlanned(id: String, today: String = LocalDate.now().toString()): String? =
    outfits.filter { it.date >= today && id in it.items }.minOfOrNull { it.date }

/** Pieces in this outfit also worn within [days] before it (shoes and accessories don't count). */
fun PlannerData.recentRepeats(outfit: Outfit, days: Int = 3): List<Pair<Clothing, Int>> {
    val day = runCatching { LocalDate.parse(outfit.date) }.getOrNull() ?: return emptyList()
    return outfit.items.mapNotNull { id ->
        val piece = clothing(id) ?: return@mapNotNull null
        if (piece.type == ClothingType.SHOES || piece.type == ClothingType.ACCESSORY) return@mapNotNull null
        val last = lastWorn(id, outfit.date)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        val gap = ChronoUnit.DAYS.between(last, day).toInt()
        if (gap <= days) piece to gap else null
    }
}
