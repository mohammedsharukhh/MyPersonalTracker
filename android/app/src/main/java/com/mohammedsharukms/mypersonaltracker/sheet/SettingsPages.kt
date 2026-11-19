package com.mohammedsharukms.mypersonaltracker.sheet

import androidx.compose.runtime.mutableStateMapOf
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetAlertPrefs
import com.mohammedsharukms.mypersonaltracker.sheet.ui.Money
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat

// Settings is one screen (SettingsScreens.kt): your sheet, then pages that save to
// it, then what's only on this phone. Mirrors SettingsModels.swift.

/** A target on a Settings page: its Settings key, and percentages shown as 95 but kept as 0.95. */
data class SettingsField(val key: String, val title: String, val unit: String, val percent: Boolean = false)

/** The Settings pages that save to the sheet, each with its own Save. */
enum class SettingsPage(val title: String, val subtitle: String) {
    MONEY("Money", "Currency, salary, spend limits, salary split"),
    HEALTH_FOOD("Health & food", "Water, sleep, steps, calories, protein"),
    STUDY("Study", "How long to study each day and week");

    fun fields(single: Boolean): List<SettingsField> = when (this) {
        MONEY -> spending(single) + monthlyLimit + split
        HEALTH_FOOD -> health + nutrition
        STUDY -> study
    }

    fun keys(single: Boolean): Set<String> = fields(single).map { it.key }.toSet()

    companion object {
        fun spending(single: Boolean) = (if (single) emptyList() else listOf(SettingsField("RATE", "Exchange rate", "${Money.home} per ${Money.main}"))) +
            listOf(SettingsField("DAILY_LIMIT", "Daily spend limit", Money.main), SettingsField("SALARY", "Usual monthly salary", Money.main))
        /** The switch (1 / 0) and your own amount when it's off. */
        val monthlyLimit get() = listOf(SettingsField("LIMIT_FROM_SPLIT", "Monthly limit from salary split", ""), SettingsField("CUSTOM_LIMIT", "Monthly spend limit", Money.main))
        val split get() = SplitBucket.entries.map { SettingsField(it.key, it.label, "%", percent = true) } +
            SettingsField("EMERGENCY_GOAL", "Emergency fund goal", Money.main)
        val health = listOf(SettingsField("WATER_TARGET", "Water", "L/day"), SettingsField("SLEEP_TARGET", "Sleep", "h/night"),
            SettingsField("STEPS_TARGET", "Steps", "/day"), SettingsField("SCREEN_LIMIT", "Screen time limit", "h/day"),
            SettingsField("GREAT_DAY", "Great day threshold", "%", percent = true))
        val nutrition get() = listOf(SettingsField("CALORIE_TARGET", "Calories", "kcal/day"), SettingsField("PROTEIN_TARGET", "Protein", "g/day"),
            SettingsField("CARB_TARGET", "Carbs", "g/day"), SettingsField("FAT_TARGET", "Fat", "g/day"), SettingsField("FOOD_BUDGET", "Monthly food budget", Money.main))
        val study = listOf(SettingsField("STUDY_DAILY_TARGET", "Study every day", "min"), SettingsField("STUDY_WEEKLY_TARGET", "Study every week", "hours"))

        /** What the sheet has now, by Settings key. */
        fun current(s: SheetSettings): Map<String, Double?> = mapOf(
            "RATE" to s.rate, "DAILY_LIMIT" to s.dailyLimit, "WATER_TARGET" to s.waterTarget, "SLEEP_TARGET" to s.sleepTarget,
            "STEPS_TARGET" to s.stepsTarget, "SCREEN_LIMIT" to s.screenLimit, "SALARY" to s.salary,
            "GREAT_DAY" to s.greatDay, "CALORIE_TARGET" to s.calorieTarget, "PROTEIN_TARGET" to s.proteinTarget,
            "CARB_TARGET" to s.carbTarget, "FAT_TARGET" to s.fatTarget, "FOOD_BUDGET" to s.foodBudget,
            "STUDY_DAILY_TARGET" to s.studyDailyTarget, "STUDY_WEEKLY_TARGET" to s.studyWeeklyTarget,
            "EMERGENCY_GOAL" to s.emergencyGoal,
            "LIMIT_FROM_SPLIT" to (if (s.limitFollowsSplit) 1.0 else 0.0), "CUSTOM_LIMIT" to s.customLimit,
            *SplitBucket.entries.map { it.key to s.share(it) }.toTypedArray(),
        )
    }
}

/** What you typed on one Settings page, and what of it differs from the sheet. */
class SettingsDraft(val page: SettingsPage, private val single: Boolean = Money.single) {
    private val fields = page.fields(single)
    private var before: Map<String, Double?> = emptyMap()
    /** As typed, by Settings key. */
    val values = mutableStateMapOf<String, String>()

    fun seed(settings: SheetSettings?) {
        settings ?: return
        before = SettingsPage.current(settings)
        values.clear()
        fields.forEach { f -> values[f.key] = SheetFormat.plain(before[f.key]?.let { if (f.percent) it * 100 else it }, 4) }
    }

    /** Only this page's keys, and only numbers (a cleared field keeps the sheet's value). */
    val changes: Map<String, Double>
        get() = fields.mapNotNull { f ->
            val typed = SheetFormat.input(values[f.key].orEmpty())?.let { if (f.percent) it / 100 else it } ?: return@mapNotNull null
            if (before[f.key]?.let { Math.abs(it - typed) < 1e-9 } == true) null else f.key to typed
        }.toMap()

    val hasChanges: Boolean get() = changes.isNotEmpty()
}

/** Which morning notification comes. With the sheet it's the briefing; without, the daily smile. */
enum class MorningSlot {
    BRIEFING, SMILE, OFF;

    companion object {
        fun of(sheetConnected: Boolean, briefingOn: Boolean, morningOn: Boolean): MorningSlot = when {
            sheetConnected -> if (briefingOn) BRIEFING else OFF
            morningOn -> SMILE
            else -> OFF
        }

        /** The morning switch once it no longer hangs off Daily reminders: on only if it used to come. */
        fun upgraded(remindersOn: Boolean, morningOn: Boolean) = remindersOn && morningOn
    }
}

/** The Notifications row on Settings: how many are on. */
object NotificationSummary {
    const val EVENING = "remindersEnabled"
    const val MORNING = "morningReminderEnabled"

    /** The keys that apply: without a sheet, only the morning and evening reminders. */
    fun keys(sheetConnected: Boolean): List<String> =
        if (sheetConnected) listOf(SheetAlertPrefs.BRIEFING, EVENING, SheetAlertPrefs.BILLS, SheetAlertPrefs.RENEWALS,
            SheetAlertPrefs.PARENTS, BudgetAlerts.ENABLED_KEY, SheetAlertPrefs.WEEKLY)
        else listOf(MORNING, EVENING)

    fun count(isOn: (String) -> Boolean, sheetConnected: Boolean): Pair<Int, Int> =
        keys(sheetConnected).let { keys -> keys.count(isOn) to keys.size }

    fun text(on: Int, total: Int) = when (on) {
        total -> "All on"
        0 -> "All off"
        else -> "$on of $total on"
    }
}

/** The sheet row at the top of Settings. */
object SheetStatus {
    fun subtitle(connected: Boolean, account: String?, synced: String?): String = when {
        !connected -> "Not connected — tap to connect"
        account != null -> "Connected · $account"
        synced != null -> "Connected · synced $synced"
        else -> "Connected"
    }
}

/** What "Your plan" on Settings shows. Connecting happens only on the Your sheet row above it. */
sealed interface PlanState {
    data object Ready : PlanState
    data object Hidden : PlanState
    data object Loading : PlanState
    data class Failed(val message: String) : PlanState

    companion object {
        fun of(connected: Boolean, loaded: Boolean, loading: Boolean, error: String?): PlanState = when {
            !connected -> Hidden
            loaded -> Ready
            loading || error == null -> Loading
            else -> Failed(error)
        }
    }
}
