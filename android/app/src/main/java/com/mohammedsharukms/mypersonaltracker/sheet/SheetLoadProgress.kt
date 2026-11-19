package com.mohammedsharukms.mypersonaltracker.sheet

import kotlin.math.roundToInt

/**
 * What a sheet load is doing, in words, for the loading screens: the first load after
 * connecting can take a minute while the sheet is set up. Mirrors SheetLoadProgress.swift.
 */
data class SheetLoadProgress(
    val title: String,
    /** 0…1 while the sheet is being set up; null while reading (a spinner only). */
    val fraction: Double? = null,
    val note: String? = null,
) {
    companion object {
        val reading = SheetLoadProgress("Reading your sheet…")
        val checking = SheetLoadProgress("Checking what needs setting up…")
        val finishing = SheetLoadProgress("Almost done…")

        /** Writing the changes the sheet needs, named by the part of the sheet they're in. */
        fun writing(tab: String?, done: Int, total: Int): SheetLoadProgress {
            val fraction = if (total > 0) minOf(1.0, done.toDouble() / total) else 0.0
            return SheetLoadProgress(area(tab), fraction, "${(fraction * 100).roundToInt()}% done · keep the app open")
        }

        /** Google asked for a pause ("too many requests"): what was happening carries on after it. */
        fun waiting(seconds: Int, during: SheetLoadProgress?): SheetLoadProgress {
            val step = during ?: reading
            return SheetLoadProgress(step.title, step.fraction, "Google asked us to slow down · carrying on in ${seconds}s")
        }

        private val areas = listOf(
            "Building your habits…" to setOf("Daily Tracker", "Dashboard", "Weekly Summary", "Monthly Summary", "My Inputs", "Input Log"),
            "Setting up your money…" to setOf("Finance Tracker", "Salary & Savings", "Set Aside", "Salary Split", "Savings Goals", "Recurring", "Loans", "Shopping", "Shopping Trips"),
            "Setting up your meals…" to setOf("Food Log", "Meal Plan", "My Foods"),
            "Setting up your study plan…" to setOf("Study Subjects", "Study Topics", "Study Log", "Study Goals"),
            "Setting up Mom & Dad…" to setOf("Parents", "Parent Medicines", "Parent Dates", "Parent Health", "Take Home", "People"),
            "Setting up your planner…" to setOf("Planner", "Outfits", "Wardrobe"),
            "Setting up your goals…" to setOf("Life Goals", "Goal Steps"),
            "Setting up your renewals…" to setOf("Renewals"),
        )

        private fun area(tab: String?): String = areas.firstOrNull { tab in it.second }?.first ?: "Getting your sheet ready…"
    }
}
