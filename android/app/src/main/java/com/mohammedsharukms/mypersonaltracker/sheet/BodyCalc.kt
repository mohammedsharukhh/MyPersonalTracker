package com.mohammedsharukms.mypersonaltracker.sheet

import kotlin.math.roundToInt

/**
 * Calorie and protein targets from your weight — the Mifflin–St Jeor formula
 * the sheet uses (19_Body.gs), so the app's preview matches the sheet. Mirrors BodyTargets.swift.
 */
object BodyCalc {
    enum class Sex(val label: String) { MALE("Male"), FEMALE("Female");
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } } }

    enum class Activity(val label: String, val factor: Double, val explanation: String) {
        SEDENTARY("Sedentary", 1.2, "Desk job, little or no exercise"),
        LIGHT("Light", 1.375, "Exercise 1–3 days a week"),
        MODERATE("Moderate", 1.55, "Exercise 3–5 days a week"),
        ACTIVE("Active", 1.725, "Hard exercise 6–7 days a week"),
        VERY_ACTIVE("Very active", 1.9, "Physical job or training twice a day");
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }

    enum class Goal(val label: String, val adjustment: Double, val explanation: String, val proteinPerKg: Double) {
        LOSE("Lose fat", -500.0, "About 0.5 kg a week (−500 kcal a day)", 2.0),
        LOSE_SLOWLY("Lose slowly", -250.0, "About 0.25 kg a week (−250 kcal a day)", 2.0),
        MAINTAIN("Maintain", 0.0, "Stay where you are", 1.6),
        GAIN("Build muscle", 300.0, "Lean gain (+300 kcal a day)", 1.8);
        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }

    data class Result(val bmr: Double, val maintenance: Double, val calories: Double, val protein: Double, val fat: Double, val carbs: Double)

    /** Mifflin–St Jeor, then the goal, protein by weight, fat 25% of calories, carbs the rest. */
    fun targets(weight: Double, height: Double, age: Int, sex: Sex, activity: Activity, goal: Goal, proteinPerKg: Double): Result {
        val bmr = Math.round(10 * weight + 6.25 * height - 5 * age + if (sex == Sex.FEMALE) -161 else 5).toDouble()
        val maintenance = Math.round(bmr * activity.factor).toDouble()
        val calories = maxOf(if (sex == Sex.FEMALE) 1200.0 else 1500.0, ((maintenance + goal.adjustment) / 10).roundToInt() * 10.0)
        val protein = Math.round(weight * proteinPerKg).toDouble()
        val fat = Math.round(calories * 0.25 / 9).toDouble()
        val carbs = maxOf(0.0, Math.round((calories - 4 * protein - 9 * fat) / 4).toDouble())
        return Result(bmr, maintenance, calories, protein, fat, carbs)
    }
}

/** Newest weight logged on the Daily Tracker (with its date), else the one on the Body block. */
val SheetSnapshot.latestWeight: Pair<Double, String?>?
    get() = days.filter { it.weight != null }.maxByOrNull { it.date }?.let { it.weight!! to it.date }
        ?: settings.bodyWeight?.let { it to null }
