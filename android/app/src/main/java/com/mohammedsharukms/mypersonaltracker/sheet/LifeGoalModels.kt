package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// Short-term and long-term goals (24_LifeGoals.gs), each with a checklist of
// steps. A short-term goal can build toward a long-term one, and counts toward
// its progress. Mirrors LifeGoalModels.swift.

data class LifeGoalData(val goals: List<LifeGoal> = emptyList(), val steps: List<GoalStep> = emptyList())

val SheetSnapshot.lifeGoalData: LifeGoalData get() = lifeGoals ?: LifeGoalData()

enum class GoalHorizon(val label: String, val hint: String) {
    SHORT("Short term", "The next few months"), LONG("Long term", "A year or more");

    companion object { fun of(s: String?) = if (s == LONG.label) LONG else SHORT }
}

enum class GoalStatus(val label: String) {
    ACTIVE("Active"), REACHED("Reached"), DROPPED("Dropped");

    companion object { fun of(s: String?) = entries.firstOrNull { it.label == s } ?: ACTIVE }
}

enum class GoalArea(val label: String) {
    HEALTH("Health"), CAREER("Career"), MONEY("Money"), LEARNING("Learning"), PERSONAL("Personal"),
    RELATIONSHIPS("Family & friends"), HOME("Home"), TRAVEL("Travel"), OTHER("Other");

    companion object { fun of(s: String?) = entries.firstOrNull { it.label == s } ?: OTHER }
}

data class LifeGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val horizon: GoalHorizon = GoalHorizon.SHORT,
    val area: GoalArea = GoalArea.PERSONAL,
    val why: String? = null,
    val due: String? = null,
    /** The long-term goal this short-term one builds toward. */
    val parentId: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    /** Typed progress (0…1), used when the goal has no steps or short-term goals. */
    val progress: Double? = null,
    val created: String? = LocalDate.now().toString(),
    val doneOn: String? = null,
    val updated: String? = LocalDate.now().toString(),
    val notes: String? = null,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("horizon", horizon.label).put("area", area.label)
        .put("why", why ?: "").put("due", due ?: "").put("parentId", parentId ?: "").put("status", status.label)
        .put("progress", progress ?: "").put("created", created ?: "").put("doneOn", doneOn ?: "").put("notes", notes ?: "")
}

data class GoalStep(
    val id: String = UUID.randomUUID().toString(),
    val goalId: String,
    val step: String,
    val done: Boolean = false,
    val doneOn: String? = null,
    val order: Double? = null,
    val due: String? = null,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("goalId", goalId).put("step", step).put("done", done)
        .put("doneOn", doneOn ?: "").put("order", order ?: "").put("due", due ?: "")
}

object LifeGoalJson {
    fun parse(o: JSONObject?): LifeGoalData? {
        if (o == null) return null
        return LifeGoalData(o.optJSONArray("goals").objs().mapNotNull(::goal), o.optJSONArray("steps").objs().mapNotNull(::step))
    }

    fun goal(p: JSONObject): LifeGoal? = LifeGoal(
        id = p.str("id") ?: return null, title = p.str("title") ?: return null, horizon = GoalHorizon.of(p.str("horizon")),
        area = GoalArea.of(p.str("area")), why = p.str("why"), due = p.str("due"), parentId = p.str("parentId"),
        status = GoalStatus.of(p.str("status")), progress = p.dbl("progress"), created = p.str("created"), doneOn = p.str("doneOn"),
        updated = p.str("updated"), notes = p.str("notes"),
    )

    fun step(p: JSONObject): GoalStep? = GoalStep(
        id = p.str("id") ?: return null, goalId = p.str("goalId") ?: return null, step = p.str("step") ?: return null,
        done = p.opt("done").let { it == true || it?.toString() == "Y" || it?.toString() == "true" }, doneOn = p.str("doneOn"),
        order = p.dbl("order"), due = p.str("due"),
    )

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

fun LifeGoalData.goal(id: String?): LifeGoal? = goals.firstOrNull { it.id == id }

/** A goal's steps in their order (then oldest first). */
fun LifeGoalData.stepsOf(goalId: String): List<GoalStep> =
    steps.filter { it.goalId == goalId }.withIndex().sortedBy { it.value.order ?: it.index.toDouble() }.map { it.value }

/** The short-term goals building toward a long-term one (dropped ones left out). */
fun LifeGoalData.childrenOf(goalId: String): List<LifeGoal> = goals.filter { it.parentId == goalId && it.status != GoalStatus.DROPPED }

/** 0…1: steps done and short-term goals under it (each by its own progress); with neither, the typed progress. */
fun LifeGoalData.progressOf(goal: LifeGoal): Double {
    if (goal.status == GoalStatus.REACHED) return 1.0
    val own = stepsOf(goal.id)
    val kids = childrenOf(goal.id)
    val parts = own.size + kids.size
    if (parts == 0) return (goal.progress ?: 0.0).coerceIn(0.0, 1.0)
    return (own.count { it.done } + kids.sumOf { progressOf(it) }) / parts
}

fun LifeGoalData.nextStep(goal: LifeGoal): GoalStep? = stepsOf(goal.id).firstOrNull { !it.done }

val LifeGoalData.active: List<LifeGoal> get() = goals.filter { it.status == GoalStatus.ACTIVE }

/** Active goals of a horizon: dated ones soonest first, then undated, by title. */
fun LifeGoalData.active(horizon: GoalHorizon): List<LifeGoal> =
    active.filter { it.horizon == horizon }.sortedWith(compareBy<LifeGoal>({ it.due == null }, { it.due ?: "" }, { it.title }))

fun LifeGoalData.lastActivity(goal: LifeGoal): String? = (listOf(goal.updated, goal.created) + stepsOf(goal.id).map { it.doneOn }).filterNotNull().maxOrNull()

/** No step ticked or edit for [days] — time to look at it again. */
fun LifeGoalData.isStale(goal: LifeGoal, today: LocalDate = LocalDate.now(), days: Long = 14): Boolean {
    if (goal.status != GoalStatus.ACTIVE) return false
    val last = lastActivity(goal)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return false
    return ChronoUnit.DAYS.between(last, today) >= days
}

fun LifeGoalData.reachedInMonth(today: LocalDate = LocalDate.now()): List<LifeGoal> =
    goals.filter { it.status == GoalStatus.REACHED && (it.doneOn ?: "").startsWith(today.toString().take(7)) }

object GoalTime {
    /** "by 31 Dec · 84 days", "by 31 Dec 2027 · 1 yr 2 mo", "3 days overdue". */
    fun due(date: String?, today: LocalDate = LocalDate.now()): String? {
        val d = date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return null
        val days = ChronoUnit.DAYS.between(today, d)
        if (days < 0) return "${-days} day${if (days == -1L) "" else "s"} overdue"
        if (days == 0L) return "due today"
        val whenText = d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy"))
        if (days <= 60) return "by $whenText · $days day${if (days == 1L) "" else "s"}"
        val months = ChronoUnit.MONTHS.between(today, d)
        val span = if (months >= 12) "${months / 12} yr" + (if (months % 12 > 0) " ${months % 12} mo" else "") else "$months mo"
        return "by $whenText · $span"
    }

    /** Short term when the date is within about four months. */
    fun suggestedHorizon(date: LocalDate, today: LocalDate = LocalDate.now()) =
        if (ChronoUnit.MONTHS.between(today, date) < 4) GoalHorizon.SHORT else GoalHorizon.LONG
}
