package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.data.DailyLog
import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.Expense
import com.mohammedsharukms.mypersonaltracker.data.ExpenseCategory
import com.mohammedsharukms.mypersonaltracker.data.StudySession
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.domain.ProgressStats
import com.mohammedsharukms.mypersonaltracker.domain.durationText
import com.mohammedsharukms.mypersonaltracker.domain.groupedByDay
import com.mohammedsharukms.mypersonaltracker.domain.isVisibleOn
import com.mohammedsharukms.mypersonaltracker.domain.loggedMinutes
import com.mohammedsharukms.mypersonaltracker.domain.toggled
import com.mohammedsharukms.mypersonaltracker.domain.wasCompletedOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

private val zone: ZoneId = ZoneOffset.UTC
private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
private fun at(y: Int, m: Int, d: Int, hour: Int = 0): Instant = LocalDate.of(y, m, d).atTime(hour, 0).atZone(zone).toInstant()

private fun log(date: LocalDate, showedUp: Boolean = true, vararg sessions: Pair<String, Int>, id: Long = date.toEpochDay()) =
    DayWithSessions(
        DailyLog(id = id, date = date, showedUp = showedUp),
        sessions.map { (title, minutes) -> StudySession(logId = id, title = title, minutes = minutes) },
    )

class StreakTest {
    private val today = day(2026, 9, 25)

    @Test fun countsConsecutiveDaysEndingToday() =
        assertEquals(3, ProgressStats.currentStreak(setOf(day(2026, 9, 23), day(2026, 9, 24), today), today))

    @Test fun todayNotLoggedYetDoesNotBreakTheStreak() =
        assertEquals(2, ProgressStats.currentStreak(setOf(day(2026, 9, 23), day(2026, 9, 24)), today))

    @Test fun aMissedDayBreaksTheStreak() =
        assertEquals(1, ProgressStats.currentStreak(setOf(day(2026, 9, 22), day(2026, 9, 24)), today))

    @Test fun noRecentDaysMeansNoStreak() =
        assertEquals(0, ProgressStats.currentStreak(setOf(day(2026, 9, 20)), today))

    @Test fun streakContinuesAcrossMonthAndYearBoundaries() {
        val newYear = day(2027, 1, 1)
        assertEquals(3, ProgressStats.currentStreak(setOf(day(2026, 12, 30), day(2026, 12, 31), newYear), newYear))
    }

    @Test fun longestStreakAcrossMonthBoundary() {
        val days = setOf(day(2026, 8, 30), day(2026, 8, 31), day(2026, 9, 1), day(2026, 9, 2), day(2026, 9, 5))
        assertEquals(4, ProgressStats.longestStreak(days))
    }

    @Test fun longestStreakOfNothingIsZero() = assertEquals(0, ProgressStats.longestStreak(emptySet()))

    @Test fun heatLevels() {
        val cases = listOf(0.0 to 1, 0.5 to 1, 1.0 to 2, 1.9 to 2, 2.0 to 3, 3.9 to 3, 4.0 to 4, 12.0 to 4)
        cases.forEach { (hours, expected) -> assertEquals("hours=$hours", expected, ProgressStats.heatLevel(hours)) }
    }
}

class WeeklyTotalsTest {
    @Test fun splitsTimeAndSpendingIntoWeeks() {
        val today = day(2026, 9, 25) // a Friday
        val lastWeek = day(2026, 9, 20) // the Sunday before a Monday-start week
        val stats = ProgressStats(
            days = listOf(log(today, true, "DSA" to 60, "Compose" to 30), log(lastWeek, true, "DSA" to 45)),
            expenses = listOf(
                Expense(amountMinor = 12_000, category = ExpenseCategory.FOOD, date = at(2026, 9, 25, 12)),
                Expense(amountMinor = 8_000, category = ExpenseCategory.TRANSPORT, date = at(2026, 9, 20, 12)),
            ),
            weekCount = 8,
            today = today,
            zone = zone,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertEquals(8, stats.weeks.size)
        assertEquals(90, stats.thisWeek.minutes)
        assertEquals(12_000L, stats.thisWeek.spentMinor)
        assertEquals(1, stats.thisWeek.daysShownUp)
        assertEquals("DSA", stats.thisWeek.topActivity?.first)
        assertEquals(45, stats.lastWeek?.minutes)
        assertEquals(8_000L, stats.lastWeek?.spentMinor)
    }

    @Test fun groupsLessUsedActivitiesAsOther() {
        val week = ProgressStats.WeekTotals(
            weekStart = day(2026, 9, 21),
            minutesByActivity = mapOf("A" to 100, "B" to 90, "C" to 80, "D" to 70, "E" to 20, "F" to 10),
        )
        val points = ProgressStats.activityPoints(listOf(week), maxActivities = 4).associate { it.activity to it.hours }
        assertEquals(setOf("A", "B", "C", "D", ProgressStats.OTHER_ACTIVITY), points.keys)
        assertEquals(0.5, points.getValue(ProgressStats.OTHER_ACTIVITY), 1e-9)
    }
}

class TodoGroupingTest {
    private val monday = at(2026, 9, 21, 9)
    private val wednesday = at(2026, 9, 23, 18)

    private fun todo(id: Long, title: String, created: Instant, completed: Instant? = null) =
        TodoItem(id = id, title = title, createdAt = created, isDone = completed != null, completedAt = completed)

    @Test fun itemFinishedLaterShowsOnBothDays() {
        val finishedLater = todo(1, "Finished later", monday, completed = wednesday)
        val stillOpen = todo(2, "Still open", monday.plusSeconds(60))
        val doneSameDay = todo(3, "Done same day", monday.plusSeconds(120), completed = monday.plusSeconds(3600))

        val grouped = listOf(doneSameDay, stillOpen, finishedLater).groupedByDay(zone)

        assertEquals(listOf("Finished later", "Still open", "Done same day"), grouped.getValue(day(2026, 9, 21)).map { it.title })
        assertEquals(listOf("Finished later"), grouped.getValue(day(2026, 9, 23)).map { it.title })
        assertTrue(finishedLater.wasCompletedOn(day(2026, 9, 23), zone))
        assertFalse(finishedLater.wasCompletedOn(day(2026, 9, 21), zone))
    }

    @Test fun openItemsCarryOverAndDoneItemsDisappearNextDay() {
        val open = todo(1, "Open", monday)
        val done = todo(2, "Done", monday, completed = monday.plusSeconds(60))
        val tuesday = day(2026, 9, 22)
        assertTrue(open.isVisibleOn(tuesday, zone))
        assertTrue(done.isVisibleOn(day(2026, 9, 21), zone))
        assertFalse(done.isVisibleOn(tuesday, zone))
    }

    @Test fun toggleSetsAndClearsCompletionDate() {
        val item = TodoItem(title = "Toggle me")
        val done = item.toggled()
        assertTrue(done.isDone)
        assertNotNull(done.completedAt)
        val undone = done.toggled()
        assertFalse(undone.isDone)
        assertNull(undone.completedAt)
    }
}

class FormattingTest {
    @Test fun durationText() {
        listOf(0 to "0m", 5 to "5m", 45 to "45m", 60 to "1h", 90 to "1h 30m", 125 to "2h 5m")
            .forEach { (minutes, expected) -> assertEquals(expected, minutes.durationText) }
    }

    @Test fun focusTimerRoundsToNearestMinuteWithAMinimumOfOne() {
        listOf(20_000L to 1, 89_000L to 1, 90_000L to 2, 25 * 60_000L to 25, 2 * 3_600_000L to 120)
            .forEach { (millis, expected) -> assertEquals("millis=$millis", expected, loggedMinutes(millis)) }
    }
}
