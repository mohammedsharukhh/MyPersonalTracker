package com.mohammedsharukms.mypersonaltracker.sheet

import org.json.JSONArray
import org.json.JSONObject

// The "Life & Finance Command Center" Google Sheet as the app sees it: one
// snapshot returned by the Apps Script API (10_Api.gs). Every calculated figure
// — daily score, streak, INR, savings, summaries — is the sheet's own formula
// result; the app only reads it. Mirrors SheetModels.swift.

data class SheetSnapshot(
    val settings: SheetSettings,
    val lists: SheetLists,
    val dashboard: List<DashboardCard>,
    /** Habit success rates, spend by category, at a glance, money snapshot. */
    val dashboardTables: List<SheetTable>,
    /** Finance sheet's day-by-day budget control. */
    val budget: SheetTable,
    val days: List<SheetDay>,
    val expenses: List<SheetExpense>,
    val salary: List<SalaryMonth>,
    val weekly: SheetTable,
    val monthly: SheetTable,
    /** Monthly bills from the Recurring tab. */
    val recurring: List<RecurringExpense> = emptyList(),
    /** Labels of archived periods, e.g. "15 Aug 26–30 Sep 26". */
    val archives: List<String> = emptyList(),
    /** Food Log, Meal Plan and My Foods. */
    val food: FoodData? = null,
    /** Money with friends & family. */
    val people: List<PeopleEntry> = emptyList(),
    /** Subjects, concepts, sessions and goals. */
    val study: StudyData? = null,
    /** The grocery list and shopping trips. */
    val shopping: ShoppingData? = null,
    /** Money put into the emergency fund or savings / SIP. */
    val setAside: List<SetAsideEntry> = emptyList(),
    /** Named things you're saving for. */
    val savingsGoals: List<SavingsGoal> = emptyList(),
    /** Week planner, outfits and wardrobe. */
    val planner: PlannerData? = null,
    /** Short-term and long-term goals with their steps. */
    val lifeGoals: LifeGoalData? = null,
    /** Documents and contracts that expire. */
    val renewals: List<Renewal> = emptyList(),
    /** Loans and EMIs. */
    val loans: List<Loan> = emptyList(),
    /** Habits and health inputs you added, and what you logged this period. */
    val myInputs: MyInputData? = null,
    /** What Mom and Dad asked you to do. */
    val parents: List<ParentAsk> = emptyList(),
    /** Their medicines, counted down to the next refill. */
    val parentMedicines: List<ParentMedicine> = emptyList(),
    /** Their birthdays, anniversaries and remembrance days. */
    val parentDates: List<ParentDate> = emptyList(),
    /** What to bring on the next trip home, and when it is. */
    val takeHome: TakeHomeData? = null,
    /** Readings Mom and Dad tell you: blood pressure, sugar… */
    val parentHealth: List<ParentReading> = emptyList(),
)

/** Money lent, borrowed, paid back or given to someone. */
data class PeopleEntry(
    val id: String, val date: String, val person: String, val type: Kind, val amount: Double, val note: String?,
    /** Shown with a clock until the sheet confirms it. */
    val pending: Boolean = false,
) {
    enum class Kind(val label: String, val explanation: String, val sign: Int) {
        LENT("Lent", "You gave money they'll pay back", 1),
        GOT_BACK("Got back", "They paid you back", -1),
        BORROWED("Borrowed", "They gave you money you'll pay back", -1),
        PAID_BACK("Paid back", "You paid them back", 1),
        GIFT_GIVEN("Gift given", "A gift from you — counts as spending", 0),
        GIFT_GOT("Gift got", "A gift to you", 0);

        companion object { fun of(label: String?) = entries.firstOrNull { it.label == label } }
    }
}

/** One person's balance (positive: they owe you) and history. */
data class PersonSummary(val name: String, val entries: List<PeopleEntry>) {
    val balance: Double get() = entries.sumOf { it.type.sign * it.amount }
    val gifted: Double get() = entries.filter { it.type == PeopleEntry.Kind.GIFT_GIVEN }.sumOf { it.amount }

    companion object {
        fun all(entries: List<PeopleEntry>): List<PersonSummary> = entries.groupBy { it.person.trim().lowercase() }
            .map { (_, e) -> val sorted = e.sortedByDescending { it.date }; PersonSummary(sorted.first().person, sorted) }
            .sortedWith(compareByDescending<PersonSummary> { kotlin.math.abs(it.balance) }.thenBy { it.name })
    }
}

/** A monthly bill on the Recurring tab, posted to the ledger automatically on [day]. */
data class RecurringExpense(
    val row: Int? = null,
    val description: String? = null,
    val category: String? = null,
    val method: String? = null,
    val amount: Double? = null,
    val day: Int? = 1,
    val active: Boolean = true,
    /** Newest month already posted, "yyyy-MM". */
    val lastAdded: String? = null,
    val notes: String? = null,
)

data class SheetLists(val categories: List<String>, val methods: List<String>)

/** The Settings sheet's core parameters (keys match `CFG` in 00_Config.gs). */
data class SheetSettings(
    val startDate: String?,
    val endDate: String?,
    val rate: Double?,
    val dailyLimit: Double?,
    val monthlyLimit: Double?,
    val waterTarget: Double?,
    val sleepTarget: Double?,
    val stepsTarget: Double?,
    val screenLimit: Double?,
    val salary: Double?,
    /** Fraction, e.g. 0.95. */
    val savingsGoal: Double?,
    /** Fraction, e.g. 0.85. */
    val greatDay: Double?,
    /** What every amount in the sheet is kept in (Settings ▸ Currency), e.g. "QAR". */
    val mainCurrency: String = "QAR",
    /** What money sent home is shown in; `rate` is 1 main = rate home. */
    val homeCurrency: String = "INR",
    /** Daily nutrition targets and the monthly food budget (Settings ▸ Nutrition). */
    val calorieTarget: Double? = 2200.0,
    val proteinTarget: Double? = 120.0,
    val carbTarget: Double? = 250.0,
    val fatTarget: Double? = 70.0,
    val foodBudget: Double? = null,
    /** The pair the exchange rate was set for, "QAR/INR". A different pair means the rate is stale. */
    val rateFor: String? = null,
    /** Study targets (Settings ▸ Study): minutes a day, hours a week. */
    val studyDailyTarget: Double? = 60.0,
    val studyWeeklyTarget: Double? = 7.0,
    /** Body & goal (Settings ▸ Body): what the calorie and protein targets are worked out from. */
    val sex: String? = null,
    val birthYear: Double? = null,
    val heightCm: Double? = null,
    val bodyWeight: Double? = null,
    val activity: String? = null,
    val bodyGoal: String? = null,
    val proteinPerKg: Double? = null,
    /** The Nutrition targets follow the newest logged weight. */
    val followWeight: Boolean? = null,
    /** Salary split (Settings ▸ Salary Split): each bucket's share as a fraction, by Settings key. */
    val splitShares: Map<String, Double> = emptyMap(),
    val emergencyGoal: Double? = null,
    /** "Y": the monthly spend limit is this month's income × (Essentials + Lifestyle); "N": the custom limit. */
    val limitFromSplit: String? = null,
    val customLimit: Double? = null,
    /** Your five habits (HABITS block on Settings): names, "Y"/"N", icon keys — in column order. */
    val habitNames: List<String?> = List(5) { null },
    val habitOn: List<String?> = List(5) { null },
    val habitIcons: List<String?> = List(5) { null },
    /** Trackers you can turn off ("N"), by Settings key: WATER_ON, SLEEP_ON, STEPS_ON, WEIGHT_ON, SCREEN_ON. */
    val trackersOn: Map<String, String?> = emptyMap(),
    /** What the Daily Score is out of. */
    val scoreOutOf: Double? = null,
) {
    val limitFollowsSplit: Boolean get() = limitFromSplit != "N"
    val rateIsCurrent: Boolean get() = rateFor == null || rateFor == "$mainCurrency/$homeCurrency"
}

/** A KPI card on the Dashboard sheet, as the sheet formats it. */
data class DashboardCard(val label: String, val value: String, val caption: String)

/** The five Y/N habits, in Daily Tracker column order. */
enum class Habit(val key: String, val title: String, val short: String) {
    OFFICE("office", "In Office by 9:00 AM", "Office"),
    BREAKFAST("breakfast", "Breakfast Done", "Breakfast"),
    LUNCH("lunch", "Lunch Taken", "Lunch"),
    PROTEIN("protein", "Protein in Lunch", "Protein"),
    WORKOUT("workout", "Workout Done", "Workout"),
}

/** One row of the Daily Tracker. Inputs are nullable (unlogged); habitsMet, score, status and streak are formulas. */
data class SheetDay(
    val date: String,
    val office: String? = null,
    val breakfast: String? = null,
    val lunch: String? = null,
    val protein: String? = null,
    val workout: String? = null,
    val water: Double? = null,
    val sleep: Double? = null,
    val steps: Double? = null,
    val weight: Double? = null,
    val screen: Double? = null,
    val notes: String? = null,
    val habitsMet: Double? = null,
    /** Fraction 0…1. */
    val score: Double? = null,
    val status: String? = null,
    val streak: Double? = null,
) {
    /** A day counts as logged once the sheet has scored it. */
    val isLogged: Boolean get() = score != null

    fun answer(habit: Habit): String? = when (habit) {
        Habit.OFFICE -> office
        Habit.BREAKFAST -> breakfast
        Habit.LUNCH -> lunch
        Habit.PROTEIN -> protein
        Habit.WORKOUT -> workout
    }

    fun withAnswer(habit: Habit, value: String?): SheetDay = when (habit) {
        Habit.OFFICE -> copy(office = value)
        Habit.BREAKFAST -> copy(breakfast = value)
        Habit.LUNCH -> copy(lunch = value)
        Habit.PROTEIN -> copy(protein = value)
        Habit.WORKOUT -> copy(workout = value)
    }
}

/** One Finance Tracker ledger row. `row` addresses it for edit and delete. */
data class SheetExpense(
    val row: Int,
    val date: String?,
    val description: String?,
    val category: String?,
    val method: String?,
    val qar: Double?,
    val inr: Double?,
    val notes: String?,
) {
    /** Still waiting to reach the sheet. */
    val isPending: Boolean get() = row < 0
}

/** One month on Salary & Savings. Salary, other income and remittance are inputs; the rest are formulas. */
data class SalaryMonth(
    val row: Int,
    val month: String,
    val label: String,
    val salary: Double?,
    val otherIncome: Double?,
    val totalIncome: Double?,
    val expenses: Double?,
    val remittanceQar: Double?,
    val remittanceInr: Double?,
    val netSavings: Double?,
    /** Fraction. */
    val savingsRate: Double?,
    val goalSavings: Double?,
    val cumulative: Double?,
    val status: String?,
)

/** A table exactly as the sheet displays it (formatted text, not numbers). */
data class SheetTable(val title: String?, val headers: List<String>, val rows: List<List<String>>) {
    companion object {
        val EMPTY = SheetTable(null, emptyList(), emptyList())
    }
}

// MARK: - Lenient parsing

/**
 * Sheet cells are loosely typed: a number column can hold typed text and an
 * empty cell arrives as null. One odd cell must not fail the whole snapshot,
 * so numbers accept numeric strings and anything unreadable becomes null.
 */
object SheetJson {
    fun snapshot(o: JSONObject): SheetSnapshot {
        val settings = o.optJSONObject("settings") ?: JSONObject()
        val lists = o.optJSONObject("lists") ?: JSONObject()
        return SheetSnapshot(
            settings = SheetSettings(
                startDate = settings.str("START_DATE"),
                endDate = settings.str("END_DATE"),
                rate = settings.dbl("RATE"),
                dailyLimit = settings.dbl("DAILY_LIMIT"),
                monthlyLimit = settings.dbl("MONTHLY_LIMIT"),
                waterTarget = settings.dbl("WATER_TARGET"),
                sleepTarget = settings.dbl("SLEEP_TARGET"),
                stepsTarget = settings.dbl("STEPS_TARGET"),
                screenLimit = settings.dbl("SCREEN_LIMIT"),
                salary = settings.dbl("SALARY"),
                savingsGoal = settings.dbl("SAVINGS_GOAL"),
                greatDay = settings.dbl("GREAT_DAY"),
                // Sheets from before the Currency setting existed are in QAR and INR.
                mainCurrency = settings.str("MAIN_CURRENCY")?.uppercase() ?: "QAR",
                homeCurrency = settings.str("HOME_CURRENCY")?.uppercase() ?: "INR",
                calorieTarget = settings.dbl("CALORIE_TARGET") ?: 2200.0,
                proteinTarget = settings.dbl("PROTEIN_TARGET") ?: 120.0,
                carbTarget = settings.dbl("CARB_TARGET") ?: 250.0,
                fatTarget = settings.dbl("FAT_TARGET") ?: 70.0,
                foodBudget = settings.dbl("FOOD_BUDGET"),
                rateFor = settings.str("RATE_FOR")?.uppercase(),
                studyDailyTarget = settings.dbl("STUDY_DAILY_TARGET") ?: 60.0,
                studyWeeklyTarget = settings.dbl("STUDY_WEEKLY_TARGET") ?: 7.0,
                sex = settings.str("SEX"), birthYear = settings.dbl("BIRTH_YEAR"), heightCm = settings.dbl("HEIGHT_CM"),
                bodyWeight = settings.dbl("BODY_WEIGHT"), activity = settings.str("ACTIVITY"), bodyGoal = settings.str("BODY_GOAL"),
                proteinPerKg = settings.dbl("PROTEIN_PER_KG"), followWeight = settings.str("FOLLOW_WEIGHT")?.let { it == "Y" },
                splitShares = SplitBucket.entries.mapNotNull { b -> settings.dbl(b.key)?.let { b.key to it } }.toMap(),
                emergencyGoal = settings.dbl("EMERGENCY_GOAL"),
                limitFromSplit = settings.str("LIMIT_FROM_SPLIT")?.uppercase(),
                customLimit = settings.dbl("CUSTOM_LIMIT"),
                habitNames = (1..5).map { settings.str("HABIT${it}_NAME") },
                habitOn = (1..5).map { settings.str("HABIT${it}_ON")?.uppercase() },
                habitIcons = (1..5).map { settings.str("HABIT${it}_ICON") },
                trackersOn = listOf("WATER_ON", "SLEEP_ON", "STEPS_ON", "WEIGHT_ON", "SCREEN_ON").associateWith { settings.str(it)?.uppercase() },
                scoreOutOf = settings.dbl("SCORE_OUT_OF"),
            ),
            lists = SheetLists(lists.optJSONArray("categories").strings(), lists.optJSONArray("methods").strings()),
            dashboard = o.optJSONArray("dashboard").objects().map {
                DashboardCard(it.str("label") ?: "", it.str("value") ?: "", it.str("caption") ?: "")
            },
            dashboardTables = o.optJSONArray("dashboardTables").objects().map(::table),
            budget = o.optJSONObject("budget")?.let(::table) ?: SheetTable.EMPTY,
            days = o.optJSONArray("days").objects().mapNotNull { d ->
                val date = d.str("date") ?: return@mapNotNull null
                SheetDay(
                    date = date,
                    office = d.str("office"), breakfast = d.str("breakfast"), lunch = d.str("lunch"),
                    protein = d.str("protein"), workout = d.str("workout"),
                    water = d.dbl("water"), sleep = d.dbl("sleep"), steps = d.dbl("steps"),
                    weight = d.dbl("weight"), screen = d.dbl("screen"), notes = d.str("notes"),
                    habitsMet = d.dbl("habitsMet"), score = d.dbl("score"), status = d.str("status"), streak = d.dbl("streak"),
                )
            },
            expenses = o.optJSONArray("expenses").objects().mapNotNull { e ->
                val row = e.dbl("row")?.toInt() ?: return@mapNotNull null
                SheetExpense(row, e.str("date"), e.str("description"), e.str("category"), e.str("method"), e.dbl("qar"), e.dbl("inr"), e.str("notes"))
            },
            salary = o.optJSONArray("salary").objects().mapNotNull { s ->
                val row = s.dbl("row")?.toInt() ?: return@mapNotNull null
                val month = s.str("month") ?: return@mapNotNull null
                SalaryMonth(
                    row = row, month = month, label = s.str("label") ?: month,
                    salary = s.dbl("salary"), otherIncome = s.dbl("otherIncome"), totalIncome = s.dbl("totalIncome"),
                    expenses = s.dbl("expenses"), remittanceQar = s.dbl("remittanceQar"), remittanceInr = s.dbl("remittanceInr"),
                    netSavings = s.dbl("netSavings"), savingsRate = s.dbl("savingsRate"), goalSavings = s.dbl("goalSavings"),
                    cumulative = s.dbl("cumulative"), status = s.str("status"),
                )
            },
            weekly = o.optJSONObject("weekly")?.let(::table) ?: SheetTable.EMPTY,
            monthly = o.optJSONObject("monthly")?.let(::table) ?: SheetTable.EMPTY,
            recurring = o.optJSONArray("recurring").objects().map { r ->
                RecurringExpense(
                    row = r.dbl("row")?.toInt(), description = r.str("description"), category = r.str("category"),
                    method = r.str("method"), amount = r.dbl("amount"), day = r.dbl("day")?.toInt(),
                    active = r.optBoolean("active", true), lastAdded = r.str("lastAdded"), notes = r.str("notes"),
                )
            },
            archives = o.optJSONArray("archives").strings(),
            food = FoodJson.parse(o.optJSONObject("food")),
            people = o.optJSONArray("people").objects().mapNotNull { e ->
                val type = PeopleEntry.Kind.of(e.str("type")) ?: return@mapNotNull null
                PeopleEntry(e.str("id") ?: "row${e.dbl("row")?.toInt()}", e.str("date") ?: "", e.str("person") ?: "", type, e.dbl("amount") ?: 0.0, e.str("note"))
            },
            study = StudyJson.parse(o.optJSONObject("study")),
            shopping = ShoppingJson.parse(o.optJSONObject("shopping")),
            setAside = o.optJSONArray("setAside").objects().mapNotNull(SetAsideEntry::from),
            savingsGoals = o.optJSONArray("savingsGoals").objects().mapNotNull(SavingsGoal::from),
            planner = PlannerJson.parse(o.optJSONObject("planner")),
            lifeGoals = LifeGoalJson.parse(o.optJSONObject("lifeGoals")),
            renewals = o.optJSONArray("renewals").objects().mapNotNull(Renewal::from),
            loans = o.optJSONArray("loans").objects().mapNotNull(Loan::from),
            myInputs = MyInputData.parse(o.optJSONObject("myInputs")),
            parents = o.optJSONArray("parents").objects().mapNotNull(ParentAsk::from),
            parentMedicines = o.optJSONArray("parentMedicines").objects().mapNotNull(ParentMedicine::from),
            parentDates = o.optJSONArray("parentDates").objects().mapNotNull(ParentDate::from),
            takeHome = TakeHomeData.parse(o.optJSONObject("takeHome")),
            parentHealth = o.optJSONArray("parentHealth").objects().mapNotNull(ParentReading::from),
        )
    }

    private fun table(o: JSONObject) = SheetTable(
        title = o.str("title"),
        headers = o.optJSONArray("headers").strings(),
        rows = o.optJSONArray("rows").let { rows ->
            if (rows == null) emptyList() else (0 until rows.length()).map { rows.optJSONArray(it).strings() }
        },
    )
}

internal fun JSONObject.dbl(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is Number -> v.toDouble()
        is String -> v.replace(",", "").trim().toDoubleOrNull()
        else -> null
    }
}

internal fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return when (val v = get(key)) {
        is String -> v.trim().ifEmpty { null }
        is Number -> v.toDouble().let { d -> if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString() }
        is Boolean -> v.toString()
        else -> null
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** Every cell as text, the way the sheet displays it. */
private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { i ->
        if (isNull(i)) "" else when (val v = get(i)) {
            is Number -> v.toDouble().let { d -> if (d == Math.floor(d)) d.toLong().toString() else d.toString() }
            else -> v.toString()
        }
    }
