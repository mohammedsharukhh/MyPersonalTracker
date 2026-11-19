package com.mohammedsharukms.mypersonaltracker.domain

import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.Expense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Derived numbers for the Progress tab. Build it from loaded data, read the results. */
class ProgressStats(
    days: List<DayWithSessions>,
    expenses: List<Expense>,
    weekCount: Int = 8,
    today: LocalDate = LocalDate.now(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) {
    data class WeekTotals(
        val weekStart: LocalDate,
        val minutes: Int = 0,
        val minutesByActivity: Map<String, Int> = emptyMap(),
        val spentMinor: Long = 0,
        val daysShownUp: Int = 0,
    ) {
        val studyHours: Double get() = minutes / 60.0

        /** The activity with the most time this week. */
        val topActivity: Pair<String, Int>? get() = minutesByActivity.maxByOrNull { it.value }?.toPair()
    }

    /** Hours for one activity in one week, for the stacked bar chart. */
    data class ActivityPoint(val weekStart: LocalDate, val activity: String, val hours: Double)

    val currentStreak: Int
    val longestStreak: Int
    /** Day → heatmap intensity 0…4. */
    val heatLevels: Map<LocalDate, Int>
    /** Oldest first; the last element is the current week. */
    val weeks: List<WeekTotals>
    /** The most-used activities get their own colour; the rest are grouped as "Other". */
    val activityPoints: List<ActivityPoint>

    val thisWeek: WeekTotals get() = weeks.last()
    val lastWeek: WeekTotals? get() = weeks.getOrNull(weeks.size - 2)

    init {
        val shownUp = days.filter { it.log.showedUp }.map { it.log.date }.toSet()
        currentStreak = currentStreak(shownUp, today)
        longestStreak = longestStreak(shownUp)
        heatLevels = days.filter { it.log.showedUp }.associate { it.log.date to heatLevel(it.studyHours) }

        val thisWeekStart = startOfWeek(today, firstDayOfWeek)
        val starts = (weekCount - 1 downTo 0).map { thisWeekStart.minusWeeks(it.toLong()) }
        val totals = starts.associateWith { WeekTotals(it) }.toMutableMap()
        for (day in days) {
            val key = startOfWeek(day.log.date, firstDayOfWeek)
            val week = totals[key] ?: continue
            val byActivity = week.minutesByActivity.toMutableMap()
            day.sessions.forEach { byActivity.merge(it.title, it.minutes, Int::plus) }
            totals[key] = week.copy(
                minutes = week.minutes + day.totalMinutes,
                minutesByActivity = byActivity,
                daysShownUp = week.daysShownUp + if (day.log.showedUp) 1 else 0,
            )
        }
        for (expense in expenses) {
            val key = startOfWeek(expense.date.atZone(zone).toLocalDate(), firstDayOfWeek)
            val week = totals[key] ?: continue
            totals[key] = week.copy(spentMinor = week.spentMinor + expense.amountMinor)
        }
        weeks = starts.map { totals.getValue(it) }
        activityPoints = activityPoints(weeks, maxActivities = 4)
    }

    companion object {
        const val OTHER_ACTIVITY = "Other"

        /**
         * Consecutive shown-up days ending today. If today isn't logged yet the streak still
         * counts from yesterday — it only breaks once a whole day is missed.
         */
        fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
            var day = if (today in days) today else today.minusDays(1)
            var count = 0
            while (day in days) {
                count++
                day = day.minusDays(1)
            }
            return count
        }

        fun longestStreak(days: Set<LocalDate>): Int {
            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            for (day in days.sorted()) {
                run = if (previous != null && ChronoUnit.DAYS.between(previous, day) == 1L) run + 1 else 1
                longest = maxOf(longest, run)
                previous = day
            }
            return longest
        }

        /** GitHub-style intensity for a day you showed up: more study hours → darker. */
        fun heatLevel(hours: Double): Int = when {
            hours < 1 -> 1
            hours < 2 -> 2
            hours < 4 -> 3
            else -> 4
        }

        fun startOfWeek(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

        fun activityPoints(weeks: List<WeekTotals>, maxActivities: Int): List<ActivityPoint> {
            val totals = mutableMapOf<String, Int>()
            weeks.forEach { week -> week.minutesByActivity.forEach { (k, v) -> totals.merge(k, v, Int::plus) } }
            val top = totals.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(maxActivities)
                .map { it.key }
                .toSet()
            return weeks.flatMap { week ->
                val grouped = mutableMapOf<String, Int>()
                week.minutesByActivity.forEach { (k, v) -> grouped.merge(if (k in top) k else OTHER_ACTIVITY, v, Int::plus) }
                grouped.map { (activity, minutes) -> ActivityPoint(week.weekStart, activity, minutes / 60.0) }
            }
        }
    }
}
