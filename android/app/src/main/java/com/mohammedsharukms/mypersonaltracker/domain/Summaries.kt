package com.mohammedsharukms.mypersonaltracker.domain

import com.mohammedsharukms.mypersonaltracker.data.DayWithSessions
import com.mohammedsharukms.mypersonaltracker.data.Expense
import com.mohammedsharukms.mypersonaltracker.data.ExpenseCategory
import com.mohammedsharukms.mypersonaltracker.data.LearningTopic
import com.mohammedsharukms.mypersonaltracker.data.SectionWithTopics
import com.mohammedsharukms.mypersonaltracker.data.StudySession
import com.mohammedsharukms.mypersonaltracker.data.TodoItem
import com.mohammedsharukms.mypersonaltracker.data.TopicStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

fun Instant.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()

fun TodoItem.wasCompletedOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    isDone && completedAt?.localDate(zone) == day

/** Unfinished to-dos carry over to the next day; finished ones stay visible only on the day they were ticked. */
fun TodoItem.isVisibleOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    !isDone || completedAt?.localDate(zone) == day

fun TodoItem.toggled(now: Instant = Instant.now()): TodoItem =
    copy(isDone = !isDone, completedAt = if (isDone) null else now)

/**
 * Sticky-note items grouped by day: each item appears on the day it was added, and also on the day it was
 * ticked if that was a different day. Items still open on that day come first.
 */
fun List<TodoItem>.groupedByDay(zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<TodoItem>> {
    val result = mutableMapOf<LocalDate, MutableList<TodoItem>>()
    for (item in sortedBy { it.createdAt }) {
        val added = item.createdAt.localDate(zone)
        result.getOrPut(added) { mutableListOf() } += item
        val ticked = item.completedAt?.takeIf { item.isDone }?.localDate(zone)
        if (ticked != null && ticked != added) result.getOrPut(ticked) { mutableListOf() } += item
    }
    return result.mapValues { (day, items) ->
        items.filterNot { it.wasCompletedOn(day, zone) } + items.filter { it.wasCompletedOn(day, zone) }
    }
}

/** Whether a day has anything worth showing in History. */
fun hasContent(day: DayWithSessions?, todos: List<TodoItem>): Boolean {
    if (todos.isNotEmpty()) return true
    val log = day?.log ?: return false
    return log.showedUp || log.selfiePath != null || day.sessions.isNotEmpty() ||
        log.whatIDid.isNotBlank() || log.whatILearned.isNotBlank()
}

/** Minutes as "45m", "2h", "1h 30m". */
val Int.durationText: String
    get() {
        val hours = this / 60
        val minutes = this % 60
        return when {
            hours == 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

/** Stopwatch time rounded to the nearest minute, at least 1. */
fun loggedMinutes(elapsedMillis: Long): Int = maxOf(1, (elapsedMillis / 60_000.0).roundToInt())

object Money {
    val currency: Currency = runCatching { Currency.getInstance(Locale.getDefault()) }.getOrElse { Currency.getInstance("INR") }
    val symbol: String get() = currency.getSymbol(Locale.getDefault())
    private val fractionDigits: Int get() = currency.defaultFractionDigits.coerceAtLeast(0)

    fun format(amountMinor: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { currency = this@Money.currency }
        return format.format(amountMinor / Math.pow(10.0, fractionDigits.toDouble()))
    }

    /** Accepts "12.5" and "12,5". Returns null for empty, invalid or non-positive input. */
    fun parseMinor(text: String): Long? {
        val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        return value.movePointRight(fractionDigits).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    fun toInput(amountMinor: Long): String =
        java.math.BigDecimal.valueOf(amountMinor).movePointLeft(fractionDigits).stripTrailingZeros().toPlainString()
}

object Mood {
    val range = 1..5
    fun emoji(value: Int): String = when (value) {
        1 -> "😞"; 2 -> "😕"; 3 -> "😐"; 4 -> "🙂"; else -> "😄"
    }
}

object ActivitySuggestions {
    /** Recently used names, then Learning sections, then topics being learned; case-insensitively unique. */
    fun make(pastSessions: List<StudySession>, sections: List<SectionWithTopics>, limit: Int = 12): List<String> {
        val recent = pastSessions.sortedByDescending { it.createdAt }.map { it.title }
        val sectionNames = sections.map { it.section.name }
        val learning = sections.flatMap(SectionWithTopics::topics).filter { it.status == TopicStatus.LEARNING }.map(LearningTopic::title)
        val seen = mutableSetOf<String>()
        return (recent + sectionNames + learning).filter { seen.add(it.lowercase()) }.take(limit)
    }
}
