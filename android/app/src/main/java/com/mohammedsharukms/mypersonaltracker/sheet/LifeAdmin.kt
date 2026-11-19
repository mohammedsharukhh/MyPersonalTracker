package com.mohammedsharukms.mypersonaltracker.sheet

import com.mohammedsharukms.mypersonaltracker.sheet.ui.Money
import com.mohammedsharukms.mypersonaltracker.sheet.ui.SheetFormat
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.UUID

// Renewals (26_Renewals.gs), loans & EMIs (27_Loans.gs), bills due, the morning
// briefing, the week in review, the step streak and the year's numbers.
// Mirrors RenewalsLoansModels.swift, Briefing.swift and ReviewViews.swift.

// MARK: - Renewals

enum class RenewalKind(val label: String, val usualYears: Long) {
    QID("QID / residence permit", 1), PASSPORT("Passport", 10), VISA("Visa", 1), LICENSE("Driving licence", 5),
    CAR("Car registration", 1), INSURANCE("Insurance", 1), RENT("Rent contract", 1), SUBSCRIPTION("Subscription", 1), OTHER("Other", 1);

    /** The expense category a renewal's cost goes under in Finance. */
    val category: String get() = when (this) {
        CAR, LICENSE -> "Transport"
        RENT -> "Rent & Utilities"
        SUBSCRIPTION -> "Entertainment"
        else -> "Other"
    }

    companion object { fun of(s: String?) = entries.firstOrNull { it.label == s } ?: OTHER }
}

data class Renewal(
    val id: String = UUID.randomUUID().toString(),
    val item: String = "",
    val kind: RenewalKind = RenewalKind.QID,
    val expires: String = LocalDate.now().plusYears(1).toString(),
    /** Days before to remind, highest first. */
    val remind: List<Int> = listOf(30, 7, 1),
    val cost: Double? = null,
    val lastRenewed: String? = null,
    val notes: String? = null,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("item", item).put("kind", kind.label).put("expires", expires)
        .put("remind", remind.joinToString(",")).put("cost", cost ?: "").put("lastRenewed", lastRenewed ?: "").put("notes", notes ?: "")

    fun daysLeft(today: LocalDate = LocalDate.now()): Long =
        runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(expires.take(10))) }.getOrDefault(0)

    val suggestedNext: String get() = runCatching { LocalDate.parse(expires.take(10)).plusYears(kind.usualYears).toString() }.getOrDefault(expires)

    companion object {
        fun days(text: String?): List<Int> {
            val list = (text ?: "30,7,1").split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.distinct().sortedDescending()
            return list.ifEmpty { listOf(30, 7, 1) }
        }

        fun from(o: JSONObject): Renewal? = Renewal(
            o.str("id") ?: return null, o.str("item") ?: return null, RenewalKind.of(o.str("kind")), o.str("expires") ?: return null,
            days(o.str("remind")), o.dbl("cost"), o.str("lastRenewed"), o.str("notes"),
        )

        fun left(days: Long): String = when {
            days < -1 -> "expired ${-days} days ago"
            days == -1L -> "expired yesterday"
            days == 0L -> "expires today"
            days == 1L -> "expires tomorrow"
            days <= 60 -> "in $days days"
            else -> "in ${days / 30} months"
        }
    }
}

val SheetSnapshot.renewalList: List<Renewal> get() = renewals.sortedBy { it.expires }
fun SheetSnapshot.renewalsDue(within: Int = 30) = renewalList.filter { it.daysLeft() <= within }

// MARK: - Loans

data class Loan(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val lender: String? = null,
    val emi: Double = 0.0,
    val firstPayment: String = LocalDate.now().toString(),
    val months: Int = 12,
    /** Typed count of instalments paid; null = worked out from the dates. */
    val paid: Int? = null,
    val principal: Double? = null,
    val rate: Double? = null,
    val closed: Boolean = false,
    val notes: String? = null,
    /** Has its own monthly bill, posting the instalment to Finance (Essentials) each month. */
    val bill: Boolean = true,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("lender", lender ?: "").put("emi", emi)
        .put("firstPayment", firstPayment).put("months", months).put("paid", paid ?: "").put("principal", principal ?: "")
        .put("rate", rate ?: "").put("closed", closed).put("notes", notes ?: "").put("bill", bill)

    private val first: LocalDate? get() = runCatching { LocalDate.parse(firstPayment.take(10)) }.getOrNull()

    /** Instalments paid: as typed, else every payment date on or before today. */
    fun paidCount(today: LocalDate = LocalDate.now()): Int {
        if (closed) return months
        paid?.let { return it.coerceIn(0, months) }
        val f = first ?: return 0
        if (f.isAfter(today)) return 0
        return minOf(ChronoUnit.MONTHS.between(f, today).toInt() + 1, months)
    }

    fun left(today: LocalDate = LocalDate.now()) = months - paidCount(today)
    fun remaining(today: LocalDate = LocalDate.now()) = left(today) * emi
    fun fraction(today: LocalDate = LocalDate.now()) = if (months > 0) paidCount(today).toDouble() / months else 0.0
    val lastPayment: String get() = first?.plusMonths((months - 1).toLong())?.toString() ?: firstPayment
    fun nextPayment(today: LocalDate = LocalDate.now()): String? =
        if (closed || left(today) <= 0) null else first?.plusMonths(paidCount(today).toLong())?.toString()

    companion object {
        fun from(o: JSONObject): Loan? = Loan(
            o.str("id") ?: return null, o.str("name") ?: return null, o.str("lender"), o.dbl("emi") ?: 0.0,
            o.str("firstPayment") ?: LocalDate.now().toString(), o.dbl("months")?.toInt() ?: 12, o.dbl("paid")?.toInt(),
            o.dbl("principal"), o.dbl("rate"), o.opt("closed").let { it == true || it?.toString() == "Y" || it?.toString() == "true" }, o.str("notes"),
            bill = o.optBoolean("bill", false),
        )
    }
}

val SheetSnapshot.activeLoans: List<Loan> get() = loans.filter { !it.closed && it.left() > 0 }
val SheetSnapshot.monthlyEMI: Double get() = activeLoans.sumOf { it.emi }

// MARK: - Bills

object Bills {
    /** Active monthly bills that fall on [date] (a day-31 bill falls on the month's last day). */
    fun due(s: SheetSnapshot, date: LocalDate): List<RecurringExpense> = s.recurring.filter { b ->
        val d = b.day ?: return@filter false
        b.active && (b.amount ?: 0.0) > 0 && minOf(d, date.lengthOfMonth()) == date.dayOfMonth
    }
}

// MARK: - Steps

/** Days in a row at or over the steps target, up to yesterday, plus today once reached. */
fun SheetSnapshot.stepStreak(todaySteps: Int? = null, today: LocalDate = LocalDate.now()): Int {
    val target = settings.stepsTarget ?: 8000.0
    val byDate = days.groupBy { it.date }.mapValues { (_, d) -> d.maxOf { it.steps ?: 0.0 } }
    var streak = if (maxOf((todaySteps ?: 0).toDouble(), byDate[today.toString()] ?: 0.0) >= target) 1 else 0
    var day = today.minusDays(1)
    while ((byDate[day.toString()] ?: 0.0) >= target) { streak++; day = day.minusDays(1) }
    return streak
}

/** Steps for the last seven days, oldest first, ending today. */
fun SheetSnapshot.stepsWeek(todaySteps: Int? = null, today: LocalDate = LocalDate.now()): List<Int> {
    val byDate = days.groupBy { it.date }.mapValues { (_, d) -> d.maxOf { (it.steps ?: 0.0).toInt() } }
    return (6 downTo 0).map { back ->
        val key = today.minusDays(back.toLong()).toString()
        if (back == 0) maxOf(todaySteps ?: 0, byDate[key] ?: 0) else byDate[key] ?: 0
    }
}

// MARK: - Morning briefing

object Briefing {
    private fun time(t: String?): String? = t?.let {
        runCatching { LocalTime.parse(it.padStart(5, '0')).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }.getOrDefault(it)
    }

    /** The morning briefing for [date]; [includeLive] adds money left and yesterday's steps (right only on the day). */
    fun text(s: SheetSnapshot, date: LocalDate, includeLive: Boolean): Pair<String, String> {
        val key = date.toString()
        val lines = mutableListOf<String>()
        val planner = s.plannerData
        val plans = planner.plansOn(key).filter { !it.done }
        if (plans.isNotEmpty()) {
            lines += "📅 " + plans.take(2).joinToString(", ") { listOfNotNull(time(it.time), it.title).joinToString(" ") } +
                if (plans.size > 2) " +${plans.size - 2} more" else ""
        }
        val asks = s.parentAsksDue(key)
        if (asks.isNotEmpty()) lines += "👪 " + asks.take(2).joinToString(", ") { "${it.who.label}: ${it.task}" } +
            if (asks.size > 2) " +${asks.size - 2} more" else ""
        planner.outfitOn(key)?.let { o ->
            val pieces = planner.pieces(o)
            lines += "👕 " + if (pieces.isEmpty()) o.note ?: "Outfit planned" else pieces.joinToString(" + ")
        }
        val due = s.studyData.dueTopics(key).size
        if (due > 0) lines += "📚 $due concept${if (due == 1) "" else "s"} to revise"
        val bills = Bills.due(s, date)
        if (bills.isNotEmpty()) lines += "🧾 Due today: " + bills.joinToString(", ") { "${it.description ?: "Bill"} ${SheetFormat.qar(it.amount)}" }
        s.renewalList.filter { it.daysLeft(date) <= 7 }.forEach { lines += "⚠️ ${it.item} ${Renewal.left(it.daysLeft(date))}" }
        val goals = s.lifeGoalData
        goals.active(GoalHorizon.SHORT).firstOrNull { g ->
            g.due?.let { runCatching { ChronoUnit.DAYS.between(date, LocalDate.parse(it.take(10))) <= 7 }.getOrDefault(false) } == true
        }?.let { g -> goals.nextStep(g)?.let { lines += "🎯 ${g.title}: ${it.step}" } }
        if (includeLive) {
            val spent = s.expenses.filter { (it.date ?: "").startsWith(key.take(7)) }.sumOf { it.qar ?: 0.0 }
            s.settings.monthlyLimit?.takeIf { it > 0 }?.let { limit ->
                val left = limit - spent
                lines += if (left >= 0) "💰 ${SheetFormat.qar(left)} ${Money.main} left this month" else "💰 ${SheetFormat.qar(-left)} ${Money.main} over this month"
            }
            val y = date.minusDays(1).toString()
            s.days.firstOrNull { it.date == y }?.steps?.takeIf { it > 0 }?.let { steps ->
                val streak = s.stepStreak(today = date)
                lines += "👣 ${"%,d".format(steps.toInt())} steps yesterday" + if (streak > 1) " · $streak-day streak" else ""
            }
        }
        val title = if (date == LocalDate.now()) "Good morning ☀️" else "Good morning ☀️ — ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}"
        return title to (if (lines.isEmpty()) "Nothing planned — a good day to get ahead." else lines.joinToString("\n"))
    }
}

// MARK: - Week in review

/** One week's numbers, Monday to Sunday. */
class WeeklyReview(s: SheetSnapshot, val start: LocalDate) {
    val days: List<String> = (0L until 7L).map { start.plusDays(it).toString() }
    val logged: Int
    val averageScore: Double?
    val greatDays: Int
    val bestDay: Pair<String, Double>?
    val spent: Double
    val budget: Double?
    val topCategory: Pair<String, Double>?
    val studyMinutes: Double
    val studyTarget: Double?
    val studySessions: Int
    val steps: Int
    val stepDays: Int
    val stepGoalDays: Int
    val averageSleep: Double?
    val goalStepsDone: Int
    val goalsReached: List<String>
    val plansDone: Int
    val plansTotal: Int

    init {
        val keys = days.toSet()
        val week = s.days.filter { it.date in keys }
        val scored = week.filter { it.isLogged }
        logged = scored.size
        averageScore = if (scored.isEmpty()) null else scored.sumOf { it.score ?: 0.0 } / scored.size
        greatDays = scored.count { (it.score ?: 0.0) >= (s.settings.greatDay ?: 0.85) }
        bestDay = scored.maxByOrNull { it.score ?: 0.0 }?.let { it.date to (it.score ?: 0.0) }
        val spending = s.expenses.filter { (it.date ?: "") in keys }
        spent = spending.sumOf { it.qar ?: 0.0 }
        budget = s.settings.dailyLimit?.let { it * 7 }
        topCategory = spending.groupBy { it.category ?: "Other" }.mapValues { (_, l) -> l.sumOf { it.qar ?: 0.0 } }.maxByOrNull { it.value }?.toPair()
        val study = s.studyData
        studyMinutes = study.minutes(days.first(), days.last())
        studyTarget = s.settings.studyWeeklyTarget?.let { it * 60 }
        studySessions = study.sessions.count { it.date in keys }
        val stepped = week.mapNotNull { it.steps }.filter { it > 0 }
        steps = stepped.sum().toInt()
        stepDays = stepped.size
        stepGoalDays = stepped.count { it >= (s.settings.stepsTarget ?: 8000.0) }
        val slept = week.mapNotNull { it.sleep }.filter { it > 0 }
        averageSleep = if (slept.isEmpty()) null else slept.average()
        val goals = s.lifeGoalData
        goalStepsDone = goals.steps.count { it.done && (it.doneOn ?: "") in keys }
        goalsReached = goals.goals.filter { it.status == GoalStatus.REACHED && (it.doneOn ?: "") in keys }.map { it.title }
        val plans = s.plannerData.plans.filter { it.date in keys }
        plansTotal = plans.size
        plansDone = plans.count { it.done }
    }

    /** One line for the Sunday notification. */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            averageScore?.let { parts += "habits ${SheetFormat.percent(it, 0)}" }
            budget?.takeIf { it > 0 }?.let { b -> parts += if (spent <= b) "${SheetFormat.qar(b - spent)} under budget" else "${SheetFormat.qar(spent - b)} over budget" }
            if (studyMinutes > 0) parts += "${StudyFormat.minutes(studyMinutes)} studied"
            if (steps > 0) parts += "${"%,d".format(steps)} steps"
            return if (parts.isEmpty()) "See how the week went and plan the next one." else parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
        }

    companion object {
        /** Sunday and Monday look back at the week just finished; other days show this week so far. */
        fun reviewWeek(today: LocalDate = LocalDate.now()): LocalDate {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            return if (today.dayOfWeek == java.time.DayOfWeek.MONDAY) monday.minusWeeks(1) else monday
        }
    }
}

// MARK: - Year in review

/** What the sheet adds up across periods (28_YearReview.gs), or the current period worked out on the phone. */
data class YearNumbers(
    val year: Int,
    val tracked: Int = 0, val logged: Int = 0, val greatDays: Int = 0, val bestStreak: Int = 0, val workouts: Int = 0,
    val averageScore: Double? = null, val steps: Double = 0.0, val stepDays: Int = 0, val bestStepsDay: Pair<String, Double>? = null,
    val averageSleep: Double? = null, val weightFrom: Double? = null, val weightTo: Double? = null,
    val spent: Double = 0.0, val income: Double = 0.0, val remitted: Double = 0.0, val noSpendDays: Int = 0,
    val byCategory: List<Pair<String, Double>> = emptyList(), val byMonth: List<Pair<Int, Double>> = emptyList(),
    val biggest: Triple<String, Double, String>? = null,
) {
    companion object {
        fun from(o: JSONObject): YearNumbers {
            val d = o.optJSONObject("days") ?: JSONObject()
            val m = o.optJSONObject("money") ?: JSONObject()
            fun arr(key: String) = m.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
            return YearNumbers(
                year = o.optInt("year", LocalDate.now().year),
                tracked = d.optInt("tracked"), logged = d.optInt("logged"), greatDays = d.optInt("greatDays"),
                bestStreak = d.optInt("bestStreak"), workouts = d.optInt("workouts"), averageScore = d.dbl("averageScore"),
                steps = d.dbl("steps") ?: 0.0, stepDays = d.optInt("stepDays"),
                bestStepsDay = d.optJSONObject("bestStepsDay")?.let { (it.str("date") ?: "") to (it.dbl("steps") ?: 0.0) },
                averageSleep = d.dbl("averageSleep"), weightFrom = d.optJSONObject("weightFrom")?.dbl("kg"), weightTo = d.optJSONObject("weightTo")?.dbl("kg"),
                spent = m.dbl("spent") ?: 0.0, income = m.dbl("income") ?: 0.0, remitted = m.dbl("remitted") ?: 0.0, noSpendDays = m.optInt("noSpendDays"),
                byCategory = arr("byCategory").map { (it.str("category") ?: "Other") to (it.dbl("amount") ?: 0.0) },
                byMonth = arr("byMonth").mapNotNull { (it.str("month")?.toIntOrNull() ?: return@mapNotNull null) to (it.dbl("amount") ?: 0.0) },
                biggest = m.optJSONObject("biggest")?.let { Triple(it.str("description") ?: it.str("category") ?: "Expense", it.dbl("amount") ?: 0.0, it.str("date") ?: "") },
            )
        }

        fun local(s: SheetSnapshot, year: Int): YearNumbers {
            val prefix = year.toString()
            val today = LocalDate.now().toString()
            val days = s.days.filter { it.date.startsWith(prefix) && it.date <= today }
            val scored = days.filter { it.isLogged }
            val stepped = days.filter { (it.steps ?: 0.0) > 0 }
            val slept = days.mapNotNull { it.sleep }.filter { it > 0 }
            val weights = days.mapNotNull { it.weight }.filter { it > 0 }
            val lines = s.expenses.filter { (it.date ?: "").startsWith(prefix) && (it.qar ?: 0.0) > 0 }
            val spendDays = lines.mapNotNull { it.date }.toSet()
            val months = s.salary.filter { it.month.startsWith(prefix) && it.month <= today.take(7) }
            return YearNumbers(
                year = year, tracked = days.size, logged = scored.size, greatDays = scored.count { it.status == "Excellent" },
                bestStreak = (days.mapNotNull { it.streak }.maxOrNull() ?: 0.0).toInt(), workouts = days.count { it.workout == "Y" },
                averageScore = if (scored.isEmpty()) null else scored.sumOf { it.score ?: 0.0 } / scored.size,
                steps = stepped.sumOf { it.steps ?: 0.0 }, stepDays = stepped.size,
                bestStepsDay = stepped.maxByOrNull { it.steps ?: 0.0 }?.let { it.date to (it.steps ?: 0.0) },
                averageSleep = if (slept.isEmpty()) null else slept.average(), weightFrom = weights.firstOrNull(), weightTo = weights.lastOrNull(),
                spent = lines.sumOf { it.qar ?: 0.0 },
                byCategory = lines.groupBy { it.category ?: "Other" }.mapValues { (_, l) -> l.sumOf { it.qar ?: 0.0 } }.toList().sortedByDescending { it.second },
                byMonth = lines.groupBy { (it.date ?: "").drop(5).take(2).toIntOrNull() ?: 0 }.mapValues { (_, l) -> l.sumOf { it.qar ?: 0.0 } }.toList().sortedBy { it.first },
                biggest = lines.maxByOrNull { it.qar ?: 0.0 }?.let { Triple(it.description ?: it.category ?: "Expense", it.qar ?: 0.0, it.date ?: "") },
                noSpendDays = days.count { it.date !in spendDays },
                income = months.sumOf { it.totalIncome ?: 0.0 }, remitted = months.sumOf { it.remittanceQar ?: 0.0 },
            )
        }
    }
}

