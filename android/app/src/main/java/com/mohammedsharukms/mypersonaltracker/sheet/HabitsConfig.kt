package com.mohammedsharukms.mypersonaltracker.sheet

// Your habits (29_Habits.gs): the five habit slots, each with your own name, icon
// and on/off, and the trackers you can turn off. The Daily Score counts only
// what's on. Mirrors HabitsCustomize.swift.

val Habit.slot: Int get() = ordinal + 1
val Habit.defaultIcon: String get() = listOf("office", "breakfast", "lunch", "protein", "workout")[ordinal]

fun Habit.name(s: SheetSettings): String = s.habitNames.getOrNull(ordinal)?.takeIf { it.isNotBlank() } ?: title
fun Habit.isOn(s: SheetSettings): Boolean = s.habitOn.getOrNull(ordinal) != "N"
fun Habit.iconKey(s: SheetSettings): String = s.habitIcons.getOrNull(ordinal)?.takeIf { it.isNotBlank() } ?: defaultIcon

/** The habits that are on, in column order. */
fun activeHabits(s: SheetSettings): List<Habit> = Habit.entries.filter { it.isOn(s) }

/** The 5th habit can be ticked by the phone (Health Connect) only while it's a workout habit and on. */
fun healthFillsWorkout(s: SheetSettings): Boolean =
    Habit.WORKOUT.isOn(s) && Regex("workout|exercise|gym|run|walk", RegexOption.IGNORE_CASE).containsMatchIn(Habit.WORKOUT.name(s))

enum class Tracker(val title: String, val key: String) {
    WATER("Water", "WATER_ON"), SLEEP("Sleep", "SLEEP_ON"), STEPS("Steps", "STEPS_ON"), WEIGHT("Weight", "WEIGHT_ON"), SCREEN("Screen time", "SCREEN_ON");

    fun isOn(s: SheetSettings): Boolean = s.trackersOn[key] != "N"
}

/** What the Daily Score is out of, worked out the way the sheet does. */
val SheetSettings.scoreDenominator: Double
    get() = scoreOutOf?.takeIf { it > 0 }
        ?: (activeHabits(this).size + (if (Tracker.WATER.isOn(this)) 1 else 0) + (if (Tracker.SLEEP.isOn(this)) 1 else 0)).toDouble()
