package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

// Study as the sheet keeps it (18_Study.gs): subjects you're studying, the
// concepts in each, every session you put in, and your goals. The figures the
// Study tab shows are worked out here from those rows. Mirrors StudyModels.swift.

data class StudyData(
    val subjects: List<StudySubject> = emptyList(),
    val topics: List<StudyTopic> = emptyList(),
    val sessions: List<StudyEntry> = emptyList(),
    val goals: List<StudyGoal> = emptyList(),
)

val SheetSnapshot.studyData: StudyData get() = study ?: StudyData()

private fun today() = LocalDate.now().toString()
private fun newId() = UUID.randomUUID().toString()

/** A subject or course: "Swift", "IELTS", "AWS Solutions Architect". */
data class StudySubject(
    val id: String = newId(),
    val name: String = "",
    /** Why you're learning it, or what for. */
    val why: String? = null,
    /** Book, course or link you're following. */
    val resource: String? = null,
    val weeklyHours: Double? = null,
    /** Exam or deadline, "yyyy-MM-dd". */
    val targetDate: String? = null,
    val status: Status = Status.ACTIVE,
    /** Index into the study palette. */
    val color: Int? = null,
    val started: String? = today(),
) {
    enum class Status(val label: String) {
        ACTIVE("Active"), PAUSED("Paused"), COMPLETED("Completed");
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).opt("why", why).opt("resource", resource)
        .opt("weeklyHours", weeklyHours).opt("targetDate", targetDate).put("status", status.label).opt("color", color).opt("started", started)
}

/** One concept inside a subject, with how well you know it and when to revise it. */
data class StudyTopic(
    val id: String = newId(),
    val subjectId: String,
    val topic: String = "",
    val stage: Stage = Stage.TO_LEARN,
    /** 1 (shaky) … 5 (solid). */
    val confidence: Int? = null,
    val lastStudied: String? = null,
    val nextReview: String? = null,
    val reviews: Int = 0,
    val notes: String? = null,
    val link: String? = null,
    val added: String? = today(),
) {
    enum class Stage(val label: String, val explanation: String, val weight: Double) {
        TO_LEARN("To learn", "Not started yet", 0.0),
        LEARNING("Learning", "Working through it now", 0.35),
        REVISING("Revising", "Learnt it — revising so it sticks", 0.7),
        MASTERED("Mastered", "You know it well", 1.0);

        val next: Stage get() = entries[(ordinal + 1) % entries.size]
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }

    fun isDue(on: String = today()): Boolean = nextReview != null && nextReview <= on

    /** After studying it on [date]: moves it on from "To learn", counts the review and schedules the next one. */
    fun studied(date: String, confidence: Int?): StudyTopic {
        val c = confidence ?: this.confidence
        val reviews = this.reviews + 1
        val next = LocalDate.parse(date).plusDays(StudySchedule.days(c ?: 3, reviews).toLong()).toString()
        return copy(
            lastStudied = if (lastStudied == null || date >= lastStudied) date else lastStudied,
            confidence = c, stage = if (stage == Stage.TO_LEARN) Stage.LEARNING else stage,
            reviews = reviews, nextReview = next,
        )
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("subjectId", subjectId).put("topic", topic).put("stage", stage.label)
        .opt("confidence", confidence).opt("lastStudied", lastStudied).opt("nextReview", nextReview).put("reviews", reviews)
        .opt("notes", notes).opt("link", link).opt("added", added)
}

/** When to revise next: sooner when it's shaky, further apart each time you come back to it. */
object StudySchedule {
    /** Days until the next review for confidence 1…5, before spacing out. */
    private val baseDays = listOf(1, 2, 4, 7, 14)

    fun days(confidence: Int, reviews: Int): Int {
        val c = confidence.coerceIn(1, 5)
        val base = baseDays[c - 1]
        // Forgot it or shaky: start the spacing over, so it comes back soon.
        if (c <= 2) return base
        val spacing = 1 + maxOf(reviews - 1, 0) * 0.5
        return minOf(90, (base * spacing).roundToInt())
    }
}

/** One study session. */
data class StudyEntry(
    val id: String = newId(),
    val date: String = today(),
    val subjectId: String,
    /** The subject's name as the sheet copied it (kept after the subject is deleted). */
    val subject: String? = null,
    val topicId: String? = null,
    val topic: String? = null,
    val minutes: Double = 30.0,
    val kind: Kind = Kind.LEARN,
    /** 1 (distracted) … 5 (deep focus). */
    val focus: Int? = null,
    val notes: String? = null,
    /** A test or quiz result in %. */
    val score: Double? = null,
    val pending: Boolean = false,
) {
    enum class Kind(val label: String) {
        LEARN("Learn"), PRACTICE("Practice"), REVISE("Revise"), TEST("Test"), PROJECT("Project");
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("date", date).put("subjectId", subjectId).opt("subject", subject)
        .opt("topicId", topicId).opt("topic", topic).put("minutes", minutes).put("kind", kind.label).opt("focus", focus)
        .opt("notes", notes).opt("score", score)
}

/** Something you're aiming for: hours to put in, concepts to master, or a milestone. */
data class StudyGoal(
    val id: String = newId(),
    val title: String = "",
    val kind: Kind = Kind.HOURS,
    /** Null: counts every subject. */
    val subjectId: String? = null,
    val target: Double? = null,
    val start: String? = today(),
    val due: String? = null,
    val done: Boolean = false,
    val doneOn: String? = null,
    val notes: String? = null,
) {
    enum class Kind(val label: String, val title: String, val explanation: String) {
        HOURS("Hours", "Study hours", "e.g. 40 hours of DSA by December"),
        TOPICS("Topics", "Master concepts", "e.g. master 15 SwiftUI concepts"),
        MILESTONE("Milestone", "Milestone", "e.g. pass the AWS exam, finish the course");
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("kind", kind.label).opt("subjectId", subjectId)
        .opt("target", target).opt("start", start).opt("due", due).put("done", done).opt("doneOn", doneOn).opt("notes", notes)
}

/** A goal's progress, worked out from the log and the topics. */
data class GoalProgress(val done: Double, val target: Double, val fraction: Double, val state: State, val detail: String) {
    enum class State(val label: String) { DONE("Done"), ON_TRACK("On track"), BEHIND("Behind"), OVERDUE("Overdue"), OPEN("In progress") }
}

// MARK: - Figures

fun StudyData.subject(id: String?): StudySubject? = subjects.firstOrNull { it.id == id }
fun StudyData.topic(id: String?): StudyTopic? = if (id == null) null else topics.firstOrNull { it.id == id }
val StudyData.activeSubjects: List<StudySubject> get() = subjects.filter { it.status == StudySubject.Status.ACTIVE }

/** A subject's concepts in the order they were added (a pasted syllabus keeps its order). */
fun StudyData.topicsOf(subjectId: String): List<StudyTopic> =
    topics.filter { it.subjectId == subjectId }.sortedBy { it.added ?: "" }

/** Mock tests and quizzes with a score, oldest first. */
fun StudyData.testScores(subjectId: String): List<StudyEntry> =
    sessions.filter { it.subjectId == subjectId && it.score != null }.sortedBy { it.date }

/** How a subject with a target date is paced: what's left, and what it takes to finish. */
data class StudyPace(val left: Int, val total: Int, val daysLeft: Int, val perWeekNeeded: Double, val perWeekRecent: Double, val state: State) {
    enum class State { ON_TRACK, BEHIND, DONE, PASSED }

    val summary: String get() = when (state) {
        State.DONE -> "Every concept mastered"
        State.PASSED -> "Target date passed · $left left"
        else -> "$left left · ${StudyFormat.countdown(daysLeft)} · ${if (perWeekNeeded < 1) "under 1" else "about ${Math.ceil(perWeekNeeded).toInt()}"} a week"
    }
}

fun StudyData.pace(subject: StudySubject, today: LocalDate = LocalDate.now()): StudyPace? {
    if (subject.status != StudySubject.Status.ACTIVE) return null
    val days = daysUntil(subject.targetDate, today) ?: return null
    val list = topics.filter { it.subjectId == subject.id }
    if (list.isEmpty()) return null
    val left = list.count { it.stage != StudyTopic.Stage.MASTERED }
    // Concepts you've got through lately (moved past "Learning" and touched in the last 2 weeks).
    val since = today.minusDays(14).toString()
    val recent = list.count { (it.stage == StudyTopic.Stage.REVISING || it.stage == StudyTopic.Stage.MASTERED) && (it.lastStudied ?: "") >= since } / 2.0
    val needed = left / maxOf(days / 7.0, 1.0 / 7)
    val state = when {
        left == 0 -> StudyPace.State.DONE
        days < 0 -> StudyPace.State.PASSED
        recent + 0.25 >= needed -> StudyPace.State.ON_TRACK
        else -> StudyPace.State.BEHIND
    }
    return StudyPace(left, list.size, days, needed, recent, state)
}

/** One concept per line, without bullets or numbering ("1.", "a)", "-", "•"). */
object SyllabusParse {
    private val marker = Regex("""^\s*(?:[-•*·▪◦–]+|\(?\d{1,3}[.)]|\(?[a-zA-Z][.)])\s+""")
    fun lines(text: String): List<String> {
        val seen = mutableSetOf<String>()
        return text.lines().mapNotNull { raw ->
            val line = raw.replace(marker, "").trim()
            if (line.isEmpty() || !seen.add(line.lowercase())) null else line.take(200)
        }
    }
}

fun StudyData.sessionsOf(subjectId: String): List<StudyEntry> = sessions.filter { it.subjectId == subjectId }.sortedByDescending { it.date }

/** Minutes studied between two dates (inclusive), optionally for one subject. */
fun StudyData.minutes(from: String, to: String, subjectId: String? = null): Double =
    sessions.filter { it.date in from..to && (subjectId == null || it.subjectId == subjectId) }.sumOf { it.minutes }

fun StudyData.minutesOn(date: String): Double = minutes(date, date)

/** Days in a row with some study, up to today (or yesterday, if today has none yet). */
fun StudyData.streak(today: LocalDate = LocalDate.now()): Int {
    val days = sessions.filter { it.minutes > 0 }.map { it.date }.toSet()
    var day = if (today.toString() in days) today else today.minusDays(1)
    var count = 0
    while (day.toString() in days) { count++; day = day.minusDays(1) }
    return count
}

fun weekStart(date: LocalDate = LocalDate.now()): LocalDate = date.with(DayOfWeek.MONDAY)

fun StudyData.weekMinutes(date: LocalDate = LocalDate.now(), subjectId: String? = null): Double {
    val start = weekStart(date)
    return minutes(start.toString(), start.plusDays(6).toString(), subjectId)
}

/** 0…1: how far through its concepts a subject is (stages weighted). */
fun StudyData.progress(subjectId: String): Double {
    val list = topics.filter { it.subjectId == subjectId }
    return if (list.isEmpty()) 0.0 else list.sumOf { it.stage.weight } / list.size
}

fun StudyData.mastered(subjectId: String?): Int =
    topics.count { it.stage == StudyTopic.Stage.MASTERED && (subjectId == null || it.subjectId == subjectId) }

/** Concepts due for revision today or earlier, most overdue first (paused subjects left out). */
fun StudyData.dueTopics(today: String = LocalDate.now().toString()): List<StudyTopic> {
    val inactive = subjects.filter { it.status != StudySubject.Status.ACTIVE }.map { it.id }.toSet()
    return topics.filter { it.isDue(today) && it.subjectId !in inactive }.sortedBy { it.nextReview }
}

/** What you're on right now: the concept you're learning that you touched last, else the next one to learn. */
val StudyData.currentTopic: StudyTopic?
    get() {
        val active = activeSubjects.map { it.id }.toSet()
        return topics.filter { it.stage == StudyTopic.Stage.LEARNING && it.subjectId in active }.maxByOrNull { it.lastStudied ?: "" }
            ?: topics.filter { it.stage == StudyTopic.Stage.TO_LEARN && it.subjectId in active }.minByOrNull { it.added ?: "" }
    }

fun StudyData.progress(goal: StudyGoal, today: LocalDate = LocalDate.now()): GoalProgress {
    val todayText = today.toString()
    val target = maxOf(goal.target ?: 1.0, 0.0001)
    val (done, detail) = when (goal.kind) {
        StudyGoal.Kind.HOURS -> {
            val h = minutes(goal.start ?: "0000-00-00", goal.doneOn ?: todayText, goal.subjectId) / 60
            h to "${StudyFormat.number(h)} of ${StudyFormat.number(goal.target ?: 0.0, 0)} h"
        }
        StudyGoal.Kind.TOPICS -> mastered(goal.subjectId).toDouble().let { it to "${it.toInt()} of ${(goal.target ?: 0.0).toInt()} mastered" }
        StudyGoal.Kind.MILESTONE -> (if (goal.done) 1.0 else 0.0) to (if (goal.done) "Done" else "Not yet")
    }
    val fraction = if (goal.kind == StudyGoal.Kind.MILESTONE) done else minOf(done / target, 1.0)
    val start = goal.start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val due = goal.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val state = when {
        goal.done || (goal.kind != StudyGoal.Kind.MILESTONE && fraction >= 1) -> GoalProgress.State.DONE
        goal.due != null && goal.due < todayText -> GoalProgress.State.OVERDUE
        start != null && due != null && due > start && goal.kind != StudyGoal.Kind.MILESTONE -> {
            val elapsed = ChronoUnit.DAYS.between(start, today).toDouble() / ChronoUnit.DAYS.between(start, due)
            if (fraction + 0.05 >= elapsed.coerceIn(0.0, 1.0)) GoalProgress.State.ON_TRACK else GoalProgress.State.BEHIND
        }
        else -> GoalProgress.State.OPEN
    }
    return GoalProgress(done, goal.target ?: 1.0, fraction, state, detail)
}

/** Days until a date ("yyyy-MM-dd"), negative when past. */
fun daysUntil(date: String?, today: LocalDate = LocalDate.now()): Int? =
    date?.let { runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)).toInt() }.getOrNull() }

object StudyFormat {
    /** "1h 25m", "40m", "0m". */
    fun minutes(value: Double): String {
        val total = value.roundToInt()
        val h = total / 60
        val m = total % 60
        return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    /** "in 12 days", "tomorrow", "today", "3 days ago". */
    fun countdown(days: Int): String = when {
        days == 0 -> "today"
        days == 1 -> "tomorrow"
        days == -1 -> "yesterday"
        days > 1 -> "in $days days"
        else -> "${-days} days ago"
    }

    fun number(value: Double, digits: Int = 1): String =
        java.math.BigDecimal(value).setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

// MARK: - JSON

object StudyJson {
    fun parse(o: JSONObject?): StudyData? {
        if (o == null) return null
        return StudyData(
            subjects = o.optJSONArray("subjects").objs().mapNotNull(::subject),
            topics = o.optJSONArray("topics").objs().mapNotNull(::topic),
            sessions = o.optJSONArray("sessions").objs().mapNotNull(::entry),
            goals = o.optJSONArray("goals").objs().mapNotNull(::goal),
        )
    }

    fun subject(s: JSONObject): StudySubject? = StudySubject(
        id = s.str("id") ?: return null, name = s.str("name") ?: return null, why = s.str("why"), resource = s.str("resource"),
        weeklyHours = s.dbl("weeklyHours"), targetDate = s.str("targetDate"), status = StudySubject.Status.of(s.str("status")) ?: StudySubject.Status.ACTIVE,
        color = s.dbl("color")?.toInt(), started = s.str("started"),
    )

    fun topic(t: JSONObject): StudyTopic? = StudyTopic(
        id = t.str("id") ?: return null, subjectId = t.str("subjectId") ?: "", topic = t.str("topic") ?: return null,
        stage = StudyTopic.Stage.of(t.str("stage")) ?: StudyTopic.Stage.TO_LEARN, confidence = t.dbl("confidence")?.toInt(),
        lastStudied = t.str("lastStudied"), nextReview = t.str("nextReview"), reviews = t.dbl("reviews")?.toInt() ?: 0,
        notes = t.str("notes"), link = t.str("link"), added = t.str("added"),
    )

    fun entry(e: JSONObject): StudyEntry? = StudyEntry(
        id = e.str("id") ?: return null, date = e.str("date") ?: return null, subjectId = e.str("subjectId") ?: "",
        subject = e.str("subject"), topicId = e.str("topicId"), topic = e.str("topic"), minutes = e.dbl("minutes") ?: 0.0,
        kind = StudyEntry.Kind.of(e.str("kind")) ?: StudyEntry.Kind.LEARN, focus = e.dbl("focus")?.toInt(),
        notes = e.str("notes"), score = e.dbl("score"),
    )

    fun goal(g: JSONObject): StudyGoal? = StudyGoal(
        id = g.str("id") ?: return null, title = g.str("title") ?: return null, kind = StudyGoal.Kind.of(g.str("kind")) ?: StudyGoal.Kind.MILESTONE,
        subjectId = g.str("subjectId"), target = g.dbl("target"), start = g.str("start"), due = g.str("due"),
        done = g.optBoolean("done", g.str("done") == "Y"), doneOn = g.str("doneOn"), notes = g.str("notes"),
    )

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Puts a value only when there is one, so a blank stays a blank cell. */
private fun JSONObject.opt(key: String, value: Any?): JSONObject = if (value == null) this else put(key, value)
