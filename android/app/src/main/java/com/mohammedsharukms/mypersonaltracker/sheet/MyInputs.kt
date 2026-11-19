package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Habits and health inputs you add yourself (30_MyInputs.gs): a habit is done /
// missed like the five and counts toward the Daily Score while it's on; a health
// input is a number with a unit and an optional daily target ("Pages · 20").
// Added with + on the day log; tap a name there to rename it. Mirrors MyInputs.swift.

enum class InputSection(val label: String) { HABIT("Habit"), HEALTH("Health") }

data class MyInput(
    val id: String = "in-" + UUID.randomUUID().toString().take(8),
    val section: InputSection = InputSection.HABIT,
    val name: String = "",
    val icon: String? = null,
    val unit: String? = null,
    val target: Double? = null,
    val step: Double? = null,
    val on: Boolean = true,
    val order: Double? = null,
) {
    val stepSize: Double get() = step?.takeIf { it > 0 } ?: 1.0

    fun toJson(): JSONObject = JSONObject().put("id", id).put("section", section.label).put("name", name).put("icon", icon ?: "")
        .put("unit", unit ?: "").put("target", target ?: "").put("step", step ?: "").put("on", on).put("order", order ?: "")

    companion object {
        fun new(section: InputSection, order: Int) = MyInput(section = section, icon = if (section == InputSection.HABIT) "checklist" else "star",
            unit = if (section == InputSection.HEALTH) "" else null, step = if (section == InputSection.HEALTH) 1.0 else null, order = order.toDouble())

        fun from(o: JSONObject): MyInput? = MyInput(
            o.str("id") ?: return null, if (o.str("section") == "Health") InputSection.HEALTH else InputSection.HABIT,
            o.str("name") ?: return null, o.str("icon"), o.str("unit"), o.dbl("target"), o.dbl("step"),
            !(o.has("on") && (o.opt("on") == false || o.opt("on") == "N")), o.dbl("order"),
        )
    }
}

data class MyInputLog(val date: String, val id: String, val value: String)

data class MyInputData(val inputs: List<MyInput> = emptyList(), val log: List<MyInputLog> = emptyList()) {
    /** In the order they were added. */
    fun list(section: InputSection, onlyOn: Boolean = true): List<MyInput> =
        inputs.filter { it.section == section && (!onlyOn || it.on) }.sortedBy { it.order ?: 0.0 }

    fun values(date: String): Map<String, String> = log.filter { it.date == date }.associate { it.id to it.value }

    /** Your habits that are on and done that day. */
    fun habitsMet(date: String): Int {
        val done = log.filter { it.date == date && it.value == "Y" }.map { it.id }.toSet()
        return list(InputSection.HABIT).count { it.id in done }
    }

    val habitsOn: Int get() = list(InputSection.HABIT).size

    companion object {
        fun parse(o: JSONObject?): MyInputData? {
            o ?: return null
            val inputs = o.optJSONArray("inputs") ?: JSONArray()
            val log = o.optJSONArray("log") ?: JSONArray()
            return MyInputData(
                (0 until inputs.length()).mapNotNull { inputs.optJSONObject(it)?.let(MyInput::from) },
                (0 until log.length()).mapNotNull { i ->
                    val l = log.optJSONObject(i) ?: return@mapNotNull null
                    MyInputLog(l.str("date") ?: return@mapNotNull null, l.str("id") ?: return@mapNotNull null, l.str("value") ?: return@mapNotNull null)
                },
            )
        }
    }
}

val SheetSnapshot.inputData: MyInputData get() = myInputs ?: MyInputData()

/** "Daily Score out of" as the sheet works it out: the five that are on, water and sleep if on, and your habits that are on. */
fun SheetSettings.scoreOutOf(inputs: MyInputData?): Double =
    (activeHabits(this).size + (if (Tracker.WATER.isOn(this)) 1 else 0) + (if (Tracker.SLEEP.isOn(this)) 1 else 0) + (inputs?.habitsOn ?: 0)).toDouble()
