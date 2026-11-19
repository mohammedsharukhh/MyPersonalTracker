package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

// Where each month's salary is meant to go (21_Split.gs): Essentials, Emergency
// fund, Savings / SIP, Family support and Lifestyle, each a percentage set in
// Settings. Worked out the same way as the sheet's Salary Split tab, so it
// updates the moment you add an expense. Mirrors SalarySplit.swift.

/** Money moved into the emergency fund or savings / SIP — saving, not spending. */
data class SetAsideEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val bucket: String,
    val amount: Double,
    val note: String? = null,
    /** The savings goal it's for (22_Goals.gs), if any. */
    val goalId: String? = null,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("date", date).put("bucket", bucket).put("amount", amount).put("note", note ?: "")
        .put("goalId", goalId ?: "")

    companion object {
        fun from(o: JSONObject): SetAsideEntry? = SetAsideEntry(
            o.str("id") ?: return null, o.str("date") ?: return null, o.str("bucket") ?: return null, o.dbl("amount") ?: 0.0, o.str("note"),
            o.str("goalId"),
        )
    }
}

/** Something you're saving for: a target, an optional date, and the money put aside for it. */
data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val target: Double = 0.0,
    val due: String? = null,
    val created: String? = LocalDate.now().toString(),
    val done: Boolean = false,
    val doneOn: String? = null,
    val notes: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("target", target).put("due", due ?: "")
        .put("created", created ?: "").put("done", done).put("doneOn", doneOn ?: "").put("notes", notes ?: "")

    companion object {
        fun from(o: JSONObject): SavingsGoal? {
            val name = o.str("name") ?: return null
            val done = when (val d = o.opt("done")) { is Boolean -> d; else -> d?.toString() == "Y" || d?.toString() == "true" }
            return SavingsGoal(o.str("id") ?: UUID.randomUUID().toString(), name, o.dbl("target") ?: 0.0, o.str("due"), o.str("created"),
                done, o.str("doneOn"), o.str("notes"))
        }
    }
}

/** A goal's progress, from the money put aside for it. */
data class SavingsGoalProgress(val saved: Double, val target: Double, val perMonth: Double?, val monthsLeft: Int?) {
    val left: Double get() = maxOf(0.0, target - saved)
    val fraction: Double get() = if (target > 0) saved / target else 0.0
}

fun SheetSnapshot.progress(goal: SavingsGoal, today: LocalDate = LocalDate.now()): SavingsGoalProgress {
    val saved = setAside.filter { it.goalId == goal.id }.sumOf { it.amount }
    val due = goal.due?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    if (due == null || goal.done || saved >= goal.target) return SavingsGoalProgress(saved, goal.target, null, null)
    // Months left, counting this one: Oct 7 → Mar 15 is 6 (Oct … Mar).
    val months = maxOf(1, java.time.Period.between(today, due).let { it.years * 12 + it.months } + 1)
    return SavingsGoalProgress(saved, goal.target, (goal.target - saved) / months, months)
}

enum class SplitBucket(val label: String, val key: String, val defaultShare: Double, val categories: Set<String>, val explanation: String) {
    ESSENTIALS("Essentials", "SPLIT_ESSENTIALS", 0.45,
        setOf("Groceries", "Rent & Utilities", "Transport", "Mobile & Internet", "Health & Pharmacy", "Household", "Education"),
        "Monthly bills, rent, groceries, transport, health"),
    EMERGENCY("Emergency fund", "SPLIT_EMERGENCY", 0.10, emptySet(), "Put aside until you reach your goal"),
    SAVINGS("Savings / SIP", "SPLIT_SAVINGS", 0.20, emptySet(), "Investments, SIP, deposits"),
    FAMILY("Family support", "SPLIT_FAMILY", 0.15, setOf("Remittance", "Gifts & Family"), "Money sent home, gifts, remittance"),
    LIFESTYLE("Lifestyle", "SPLIT_LIFESTYLE", 0.10,
        setOf("Food & Dining", "Personal Care", "Clothing", "Entertainment", "Travel", "Other"), "Eating out, shopping, fun, travel");

    /** Filled by putting money aside rather than by spending. */
    val isSaving: Boolean get() = this == EMERGENCY || this == SAVINGS

    companion object {
        /** The bucket an expense counts toward: monthly bills are always Essentials, everything else goes by its category. */
        fun of(expense: SheetExpense): SplitBucket? =
            if (isMonthlyBill(expense)) ESSENTIALS else listOf(ESSENTIALS, FAMILY, LIFESTYLE).firstOrNull { (expense.category ?: "") in it.categories }

        /** Added from the Recurring tab (its note ends "Recurring"). */
        fun isMonthlyBill(expense: SheetExpense): Boolean = (expense.notes ?: "").contains("Recurring", ignoreCase = true)
    }
}

fun SheetSettings.share(bucket: SplitBucket): Double = splitShares[bucket.key] ?: bucket.defaultShare

/** One bucket for one month. */
data class SplitLine(val bucket: SplitBucket, val share: Double, val planned: Double, val used: Double) {
    val left: Double get() = planned - used
    val fraction: Double get() = if (planned > 0) used / planned else 0.0
}

object SalarySplit {
    fun income(s: SheetSnapshot, month: String): Double {
        val row = s.salary.firstOrNull { it.month == month }
        return row?.totalIncome ?: ((row?.salary ?: 0.0) + (row?.otherIncome ?: 0.0))
    }

    /** Each bucket for [month] ("yyyy-MM"): its share of that month's income and what has gone to it. */
    fun lines(s: SheetSnapshot, month: String): List<SplitLine> {
        val row = s.salary.firstOrNull { it.month == month }
        val income = income(s, month)
        val expenses = s.expenses.filter { (it.date ?: "").startsWith(month) }
        val setAside = s.setAside.filter { it.date.startsWith(month) }
        return SplitBucket.entries.map { b ->
            val share = s.settings.share(b)
            var used = if (b.isSaving) setAside.filter { it.bucket == b.label }.sumOf { it.amount }
                else expenses.filter { SplitBucket.of(it) == b }.sumOf { it.qar ?: 0.0 }
            if (b == SplitBucket.FAMILY) used += row?.remittanceQar ?: 0.0
            SplitLine(b, share, Math.round(income * share * 100) / 100.0, used)
        }
    }

    /** Put aside (emergency fund + savings / SIP), in one month ("yyyy-MM") or overall (null). */
    fun putAside(s: SheetSnapshot, month: String? = null): Double =
        s.setAside.filter { month == null || it.date.startsWith(month) }.sumOf { it.amount }

    /** The share of income planned to be saved: emergency fund + savings / SIP. */
    fun savingsShare(settings: SheetSettings): Double = settings.share(SplitBucket.EMERGENCY) + settings.share(SplitBucket.SAVINGS)

    /** How a month went against the plan. */
    sealed interface MonthState {
        data object None : MonthState
        data object Saved : MonthState
        data class ToPutAside(val amount: Double) : MonthState
        data class OverBudget(val amount: Double, val buckets: List<SplitBucket>) : MonthState
    }

    fun state(s: SheetSnapshot, month: String): MonthState {
        if (income(s, month) <= 0) return MonthState.None
        val lines = lines(s, month)
        val overLines = lines.filter { !it.bucket.isSaving && it.left < -0.005 }
        val over = overLines.sumOf { -it.left }
        if (over > 0.005) return MonthState.OverBudget(over, overLines.map { it.bucket })
        // The month's savings target as a whole (the split card shows each bucket).
        val saving = lines.filter { it.bucket.isSaving }
        val toGo = maxOf(0.0, saving.sumOf { it.planned } - saving.sumOf { it.used })
        return if (toGo > 0.005) MonthState.ToPutAside(toGo) else MonthState.Saved
    }

    /** Everything ever put in the emergency fund. */
    fun emergencySaved(s: SheetSnapshot): Double = s.setAside.filter { it.bucket == SplitBucket.EMERGENCY.label }.sumOf { it.amount }
}
