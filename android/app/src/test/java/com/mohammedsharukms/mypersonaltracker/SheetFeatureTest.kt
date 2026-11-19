package com.mohammedsharukms.mypersonaltracker

import com.mohammedsharukms.mypersonaltracker.sheet.PendingWrite
import com.mohammedsharukms.mypersonaltracker.sheet.progress
import com.mohammedsharukms.mypersonaltracker.sheet.plansOn
import com.mohammedsharukms.mypersonaltracker.sheet.stepStreak
import com.mohammedsharukms.mypersonaltracker.sheet.goal
import com.mohammedsharukms.mypersonaltracker.sheet.progressOf
import com.mohammedsharukms.mypersonaltracker.sheet.stepsOf
import com.mohammedsharukms.mypersonaltracker.sheet.nextStep
import com.mohammedsharukms.mypersonaltracker.sheet.active
import com.mohammedsharukms.mypersonaltracker.sheet.timesWorn
import com.mohammedsharukms.mypersonaltracker.sheet.nextPlanned
import com.mohammedsharukms.mypersonaltracker.sheet.outfitOn
import com.mohammedsharukms.mypersonaltracker.sheet.recentRepeats
import com.mohammedsharukms.mypersonaltracker.sheet.pieces
import com.mohammedsharukms.mypersonaltracker.sheet.SheetDay
import com.mohammedsharukms.mypersonaltracker.sheet.SheetExpense
import com.mohammedsharukms.mypersonaltracker.sheet.SheetLists
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSettings
import com.mohammedsharukms.mypersonaltracker.sheet.SheetSnapshot
import com.mohammedsharukms.mypersonaltracker.sheet.SheetStore
import com.mohammedsharukms.mypersonaltracker.sheet.SheetTable
import com.mohammedsharukms.mypersonaltracker.sheet.ui.InsightEngine
import com.mohammedsharukms.mypersonaltracker.sheet.ui.Insight
import com.mohammedsharukms.mypersonaltracker.sheet.ui.ReceiptReader
import com.mohammedsharukms.mypersonaltracker.sheet.GoalProgress
import com.mohammedsharukms.mypersonaltracker.sheet.StudyData
import com.mohammedsharukms.mypersonaltracker.sheet.StudyEntry
import com.mohammedsharukms.mypersonaltracker.sheet.StudyGoal
import com.mohammedsharukms.mypersonaltracker.sheet.StudySchedule
import com.mohammedsharukms.mypersonaltracker.sheet.StudySubject
import com.mohammedsharukms.mypersonaltracker.sheet.StudyTopic
import com.mohammedsharukms.mypersonaltracker.sheet.currentTopic
import com.mohammedsharukms.mypersonaltracker.sheet.dueTopics
import com.mohammedsharukms.mypersonaltracker.sheet.minutesOn
import com.mohammedsharukms.mypersonaltracker.sheet.progress
import com.mohammedsharukms.mypersonaltracker.sheet.streak
import com.mohammedsharukms.mypersonaltracker.sheet.studyData
import com.mohammedsharukms.mypersonaltracker.sheet.weekMinutes
import com.mohammedsharukms.mypersonaltracker.sheet.FoodQuantity
import com.mohammedsharukms.mypersonaltracker.sheet.pace
import com.mohammedsharukms.mypersonaltracker.sheet.inputData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SheetFeatureTest {
    private fun snapshot(days: List<SheetDay> = emptyList(), expenses: List<SheetExpense> = emptyList(), monthlyLimit: Double? = null) = SheetSnapshot(
        settings = SheetSettings("2026-10-01", "2026-10-31", 26.1, 20.0, monthlyLimit, 4.0, 7.5, 8000.0, 3.0, 2500.0, 0.95, 0.85),
        lists = SheetLists(listOf("Groceries"), listOf("Cash")),
        dashboard = emptyList(), dashboardTables = emptyList(), budget = SheetTable.EMPTY,
        days = days, expenses = expenses, salary = emptyList(), weekly = SheetTable.EMPTY, monthly = SheetTable.EMPTY,
    )

    @Test fun receiptTakesTheTotalNotSubtotalOrVat() {
        val lines = listOf("LULU HYPERMARKET", "Milk 1L 6.50", "Subtotal 10.50", "VAT 5% 0.53", "TOTAL 11.03", "Cash 20.00", "Change 8.97")
        val result = ReceiptReader.parse(lines)
        assertEquals(11.03, result.amount!!, 0.001)
        assertEquals("Lulu Hypermarket", result.merchant)
    }

    @Test fun receiptTotalOnTheNextLineAndRecentDate() {
        val result = ReceiptReader.parse(listOf("Date 04/10/2026", "Grand Total", "QAR 1,234.50", "Expires 31/12/2029"), LocalDate.of(2026, 10, 6))
        assertEquals(1234.50, result.amount!!, 0.001)
        assertEquals(LocalDate.of(2026, 10, 4), result.date)
    }

    @Test fun aQueuedDayIsScoredLikeTheSheet() {
        val write = PendingWrite("1", "saveDay", JSONObject()
            .put("date", "2026-10-06").put("office", "Y").put("breakfast", "Y").put("lunch", "Y").put("protein", "N").put("workout", "")
            .put("water", 4).put("sleep", 7).toString(), "", 0, null, null)
        val day = SheetStore.applyLocally(write, snapshot(days = listOf(SheetDay("2026-10-06")))).days[0]
        assertEquals(4.0, day.habitsMet!!, 0.0)   // 3 Y + water met + sleep missed
        assertEquals("Needs Work", day.status)
    }

    @Test fun aQueuedExpenseShowsWithItsHomeAmount() {
        val write = PendingWrite("2", "addExpense", JSONObject()
            .put("date", "2026-10-06").put("description", "Coffee").put("category", "Groceries").put("method", "Cash").put("qar", 10).toString(),
            "", 0, null, -1)
        val expense = SheetStore.applyLocally(write, snapshot()).expenses.single()
        assertTrue(expense.isPending)
        assertEquals(261.0, expense.inr!!, 0.001)
    }

    @Test fun insightsFlagAMonthOverItsLimit() {
        val s = snapshot(monthlyLimit = 100.0, expenses = listOf(
            SheetExpense(5, "2026-10-02", null, "Rent & Utilities", null, 150.0, null, null),
            SheetExpense(6, "2026-10-03", null, "Groceries", null, 20.0, null, null),
        ))
        val insights = InsightEngine.insights(s, LocalDate.of(2026, 10, 6))
        assertTrue(insights.any { it.id == "pace" && it.tone == Insight.Tone.BAD })
        assertTrue(insights.any { it.id == "category" && it.text.startsWith("Rent & Utilities") })
    }

    @Test fun yourNutritionScalesWithTheAmount() {
        val roti = com.mohammedsharukms.mypersonaltracker.sheet.FoodItem.of("Roti", 1.0, "piece", 120.0, 3.1, 18.0, 3.7)
            .withNutrition(calories = 100.0, protein = 4.0)
            .withAmount(3.0)
        assertEquals(300.0, roti.calories, 0.001)
        assertEquals(12.0, roti.protein, 0.001)
        assertEquals(54.0, roti.carbs, 0.001)
    }

    @Test fun aRateSetForAnotherPairIsStale() {
        val stale = snapshot().settings.copy(mainCurrency = "AED", homeCurrency = "PKR", rateFor = "QAR/INR")
        assertTrue(!stale.rateIsCurrent)
        assertTrue(stale.copy(rateFor = "AED/PKR").rateIsCurrent)
    }

    @Test fun balancesPerPerson() {
        fun e(person: String, kind: com.mohammedsharukms.mypersonaltracker.sheet.PeopleEntry.Kind, amount: Double) =
            com.mohammedsharukms.mypersonaltracker.sheet.PeopleEntry(java.util.UUID.randomUUID().toString(), "2026-10-01", person, kind, amount, null)
        val people = com.mohammedsharukms.mypersonaltracker.sheet.PersonSummary.all(listOf(
            e("Rahul", com.mohammedsharukms.mypersonaltracker.sheet.PeopleEntry.Kind.LENT, 500.0),
            e("rahul", com.mohammedsharukms.mypersonaltracker.sheet.PeopleEntry.Kind.GOT_BACK, 200.0),
            e("Ammi", com.mohammedsharukms.mypersonaltracker.sheet.PeopleEntry.Kind.GIFT_GIVEN, 300.0),
            e("Sam", com.mohammedsharukms.mypersonaltracker.sheet.PeopleEntry.Kind.BORROWED, 100.0)))
        assertEquals(300.0, people.first { it.name.lowercase() == "rahul" }.balance, 0.001)
        assertEquals(-100.0, people.first { it.name == "Sam" }.balance, 0.001)
        assertEquals(0.0, people.first { it.name == "Ammi" }.balance, 0.001)
        assertEquals(300.0, people.first { it.name == "Ammi" }.gifted, 0.001)
    }

    // MARK: - Study

    private val studyToday = LocalDate.parse("2026-10-06")

    private fun studyData() = StudyData(
        subjects = listOf(StudySubject("s", "Swift"), StudySubject("p", "Old", status = StudySubject.Status.PAUSED)),
        topics = listOf(
            StudyTopic("a", "s", "Closures", StudyTopic.Stage.MASTERED, nextReview = "2026-10-20"),
            StudyTopic("b", "s", "Actors", StudyTopic.Stage.LEARNING, lastStudied = "2026-10-05", nextReview = "2026-10-04"),
            StudyTopic("c", "s", "Macros"),
            StudyTopic("d", "p", "Paused topic", StudyTopic.Stage.LEARNING, nextReview = "2026-10-01"),
        ),
        sessions = listOf(StudyEntry(date = "2026-10-06", subjectId = "s", minutes = 30.0), StudyEntry(date = "2026-10-05", subjectId = "s", minutes = 60.0),
            StudyEntry(date = "2026-10-04", subjectId = "s", minutes = 45.0), StudyEntry(date = "2026-10-01", subjectId = "s", minutes = 90.0)),
    )

    @Test fun revisionSpacesOutAndIsSoonerWhenShaky() {
        assertEquals(1, StudySchedule.days(1, 1))
        assertEquals(4, StudySchedule.days(3, 1))
        assertEquals(8, StudySchedule.days(3, 3))
        assertEquals(90, StudySchedule.days(5, 20))
    }

    @Test fun studyingAConceptMovesItOnAndSchedulesIt() {
        val t = StudyTopic(subjectId = "s", topic = "Actors").studied("2026-10-06", 2)
        assertEquals(StudyTopic.Stage.LEARNING, t.stage)
        assertEquals(1, t.reviews)
        assertEquals("2026-10-08", t.nextReview)
    }

    @Test fun studyStreakTodayAndWeek() {
        val d = studyData()
        assertEquals(3, d.streak(studyToday))
        assertEquals(30.0, d.minutesOn("2026-10-06"), 0.001)
        assertEquals(90.0, d.weekMinutes(studyToday), 0.001)
    }

    @Test fun dueSkipsPausedSubjectsAndProgressIsWeighted() {
        val d = studyData()
        assertEquals(listOf("b"), d.dueTopics("2026-10-06").map { it.id })
        assertEquals((1 + 0.35) / 3, d.progress("s"), 0.001)
        assertEquals("b", d.currentTopic?.id)
    }

    @Test fun studyGoalProgress() {
        val d = studyData()
        val hours = d.progress(StudyGoal(title = "4h", kind = StudyGoal.Kind.HOURS, subjectId = "s", target = 4.0, start = "2026-10-04", due = "2026-10-31"), studyToday)
        assertEquals(2.25, hours.done, 0.001)
        assertEquals(GoalProgress.State.ON_TRACK, hours.state)
        val topics = d.progress(StudyGoal(title = "Master 2", kind = StudyGoal.Kind.TOPICS, target = 2.0, start = "2026-09-01", due = "2026-10-05"), studyToday)
        assertEquals(GoalProgress.State.OVERDUE, topics.state)
    }

    @Test fun aQueuedStudySessionShowsAtOnceAndHistoryOutlivesTheSubject() {
        var s = snapshot().copy(study = StudyData(subjects = listOf(StudySubject("s", "Swift"))))
        fun write(action: String, payload: JSONObject) = PendingWrite(java.util.UUID.randomUUID().toString(), action, payload.toString(), "", 0, null, null)
        s = SheetStore.applyLocally(write("saveStudyTopic", StudyTopic("t", "s", "Actors", StudyTopic.Stage.LEARNING, reviews = 1).toJson()), s)
        s = SheetStore.applyLocally(write("saveStudySession", StudyEntry("e", "2026-10-06", "s", topicId = "t", minutes = 25.0, kind = StudyEntry.Kind.REVISE).toJson()), s)
        assertEquals("Actors", s.studyData.sessions.first().topic)
        assertEquals("Swift", s.studyData.sessions.first().subject)
        assertTrue(s.studyData.sessions.first().pending)
        s = SheetStore.applyLocally(write("deleteStudySubject", JSONObject().put("id", "s")), s)
        assertTrue(s.studyData.subjects.isEmpty() && s.studyData.topics.isEmpty())
        assertEquals(1, s.studyData.sessions.size)
    }

    // MARK: - Targets from weight

    @Test fun mifflinStJeorWithGoalAndMacros() {
        // Man, 30, 175 cm, 75 kg, lightly active, losing fat at 2 g/kg protein.
        val r = com.mohammedsharukms.mypersonaltracker.sheet.BodyCalc.targets(75.0, 175.0, 30,
            com.mohammedsharukms.mypersonaltracker.sheet.BodyCalc.Sex.MALE, com.mohammedsharukms.mypersonaltracker.sheet.BodyCalc.Activity.LIGHT,
            com.mohammedsharukms.mypersonaltracker.sheet.BodyCalc.Goal.LOSE, 2.0)
        assertEquals(1699.0, r.bmr, 0.0)
        assertEquals(2336.0, r.maintenance, 0.0)
        assertEquals(1840.0, r.calories, 0.0)
        assertEquals(150.0, r.protein, 0.0)
        assertEquals(51.0, r.fat, 0.0)
        assertEquals(195.0, r.carbs, 0.0)
    }

    // MARK: - Food amounts

    @Test fun readsTheAmountTypedWithTheFood() {
        assertEquals("milk" to FoodQuantity(250.0, "ml"), FoodQuantity.split("milk 250ml"))
        assertEquals(FoodQuantity(250.0, "ml"), FoodQuantity.split("250 ml milk").second)
        assertEquals(FoodQuantity(1.5, "cup"), FoodQuantity.split("rice 1.5 cups").second)
        assertEquals(FoodQuantity(1000.0, "ml"), FoodQuantity.split("milk 1L").second)
        assertEquals("eggs" to FoodQuantity(2.0, null), FoodQuantity.split("2 eggs"))
        assertEquals(null, FoodQuantity.split("half litre milk").second)
    }

    @Test fun switchesBetweenServingAndMillilitres() {
        // Milk, full fat: 1 cup = 240 g, 149 kcal, 7.7 g protein.
        val milk = com.mohammedsharukms.mypersonaltracker.sheet.FoodItem.of("Milk", 1.0, "cup", 149.0, 7.7, 11.7, 7.9, grams = 240.0)
        assertEquals(listOf("cup", "g", "ml"), milk.unitChoices)
        val ml = milk.withQuantity(com.mohammedsharukms.mypersonaltracker.sheet.FoodQuantity(250.0, "ml"))
        assertEquals("ml", ml.unit)
        assertEquals(250.0, ml.amount, 0.0)
        assertEquals(155.2, ml.calories, 0.1)
        assertEquals(8.02, ml.protein, 0.01)
        assertEquals(1.04, ml.withUnit("cup").amount, 0.001)
    }

    // MARK: - Shopping

    @Test fun shoppingQuickAddAndAisles() {
        val parse = com.mohammedsharukms.mypersonaltracker.sheet.ShoppingParse
        assertEquals("Milk" to "2 L", parse.split("milk 2L"))
        assertEquals("Rice" to "5 kg", parse.split("rice 5 kg"))
        assertEquals("Eggs" to "30", parse.split("30 eggs"))
        assertEquals("Dishwash liquid" to null, parse.split("dishwash liquid"))
        val aisle = com.mohammedsharukms.mypersonaltracker.sheet.ShoppingAisle
        assertEquals("Dairy & Eggs", aisle.guess("Milk"))
        assertEquals("Grains & Bread", aisle.guess("Basmati rice"))
        assertEquals("Household", aisle.guess("Batteries AA"))
        assertEquals("Other", aisle.guess("Gift wrap"))
    }

    @Test fun finishingATripMovesTheCartToHistory() {
        val shop = com.mohammedsharukms.mypersonaltracker.sheet.ShoppingData(items = listOf(
            com.mohammedsharukms.mypersonaltracker.sheet.ShoppingItem("a", "Milk", "2 L", status = com.mohammedsharukms.mypersonaltracker.sheet.ShoppingItem.Status.IN_CART, price = 11.0),
            com.mohammedsharukms.mypersonaltracker.sheet.ShoppingItem("b", "Onion"),
        ))
        var s = snapshot().copy(shopping = shop)
        val payload = JSONObject().put("tripId", "t").put("date", "2026-10-07").put("store", "Lulu").put("total", 11).put("method", "Cash")
            .put("addToFinance", true).put("items", org.json.JSONArray().put(JSONObject().put("id", "a").put("price", 11)))
        s = SheetStore.applyLocally(PendingWrite("1", "checkoutShopping", payload.toString(), "", 0, null, null), s)
        val data = s.shopping!!
        assertTrue(data.items.none { it.status == com.mohammedsharukms.mypersonaltracker.sheet.ShoppingItem.Status.IN_CART })
        assertEquals(listOf("Milk"), data.items.filter { it.tripId == "t" }.map { it.item })
        assertEquals(1, data.trips.size)
        assertEquals("Vegetables", data.items.first { it.id == "b" }.category)
    }

    // MARK: - Study plan

    @Test fun pastedSyllabusLosesBulletsAndKeepsOrder() {
        val text = "1. Arrays and strings\n2) Two pointers\n  - Sliding window\n• Binary search\na) Recursion\n\nTwo pointers"
        assertEquals(listOf("Arrays and strings", "Two pointers", "Sliding window", "Binary search", "Recursion"),
            com.mohammedsharukms.mypersonaltracker.sheet.SyllabusParse.lines(text))
    }

    @Test fun pacingToTheTargetDateAndForgettingStartsOver() {
        val topics = (0 until 10).map { StudyTopic(subjectId = "s", topic = "T$it") }.toMutableList()
        topics[0] = topics[0].copy(stage = StudyTopic.Stage.MASTERED)
        topics[1] = topics[1].copy(stage = StudyTopic.Stage.REVISING, lastStudied = "2026-10-05")
        val data = StudyData(subjects = listOf(StudySubject("s", "S", targetDate = "2026-11-04")), topics = topics)
        val pace = data.pace(data.subjects[0], LocalDate.parse("2026-10-07"))!!
        assertEquals(9, pace.left)
        assertEquals(2.25, pace.perWeekNeeded, 0.01)
        assertEquals(com.mohammedsharukms.mypersonaltracker.sheet.StudyPace.State.BEHIND, pace.state)
        assertEquals(1, StudySchedule.days(1, 5))
        assertEquals(2, StudySchedule.days(2, 5))
        assertEquals(14, StudySchedule.days(4, 3))
    }

    // MARK: - Salary split

    @Test fun salarySplitDividesIncomeAndFillsEachBucket() {
        val month = com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth(5, "2026-10", "Oct 2026", 10000.0, null, 10000.0, null, 800.0, null, null, null, null, null, null)
        val s = snapshot(expenses = listOf(
            SheetExpense(5, "2026-10-03", null, "Groceries", null, 300.0, null, null),
            SheetExpense(6, "2026-10-04", null, "Food & Dining", null, 120.0, null, null),
            SheetExpense(7, "2026-10-05", null, "Gifts & Family", null, 50.0, null, null),
            SheetExpense(8, "2026-09-30", null, "Groceries", null, 999.0, null, null),
        )).let { base ->
            base.copy(salary = listOf(month), setAside = listOf(com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry("x", "2026-10-02", "Savings / SIP", 1500.0)),
                settings = base.settings.copy(splitShares = mapOf("SPLIT_ESSENTIALS" to 0.5, "SPLIT_EMERGENCY" to 0.1, "SPLIT_SAVINGS" to 0.2,
                    "SPLIT_FAMILY" to 0.1, "SPLIT_LIFESTYLE" to 0.1)))
        }
        val lines = com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit.lines(s, "2026-10").associateBy { it.bucket }
        val B = com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.entries
        assertEquals(5000.0, lines[B[0]]!!.planned, 0.0)
        assertEquals(300.0, lines[B[0]]!!.used, 0.0)
        assertEquals(1500.0, lines[B[2]]!!.used, 0.0)
        assertEquals(500.0, lines[B[2]]!!.left, 0.0)
        assertEquals(850.0, lines[B[3]]!!.used, 0.0)
        assertEquals(120.0, lines[B[4]]!!.used, 0.0)
    }

    @Test fun spendingInsideTheBudgetDoesNotTouchSaved() {
        val split = com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit
        fun snap(spent: Double, putAside: Double) = snapshot(expenses = listOf(SheetExpense(5, "2026-10-03", null, "Groceries", null, spent, null, null))).let { base ->
            base.copy(salary = listOf(com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth(5, "2026-10", "Oct 2026", 10000.0, null, 10000.0, spent, null, null, 10000 - spent, null, null, null, null)),
                setAside = listOf(com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry("x", "2026-10-02", "Savings / SIP", putAside)),
                settings = base.settings.copy(splitShares = mapOf("SPLIT_ESSENTIALS" to 0.5, "SPLIT_EMERGENCY" to 0.1, "SPLIT_SAVINGS" to 0.2, "SPLIT_FAMILY" to 0.1, "SPLIT_LIFESTYLE" to 0.1)))
        }
        assertEquals(3000.0, split.putAside(snap(4000.0, 3000.0)), 0.0)
        assertEquals(com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit.MonthState.Saved, split.state(snap(4000.0, 3000.0), "2026-10"))
        assertEquals(com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit.MonthState.ToPutAside(2000.0), split.state(snap(4000.0, 1000.0), "2026-10"))
        val over = split.state(snap(6000.0, 3000.0), "2026-10") as com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit.MonthState.OverBudget
        assertEquals(1000.0, over.amount, 0.0)
        assertEquals(listOf(com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.ESSENTIALS), over.buckets)
    }

    @Test fun everyMonthlyBillCountsAsEssentials() {
        val s = snapshot(expenses = listOf(
            SheetExpense(5, "2026-10-01", "Gym", "Entertainment", null, 200.0, null, "Recurring"),
            SheetExpense(6, "2026-10-01", "Netflix", "Entertainment", null, 40.0, null, "family plan · Recurring"),
            SheetExpense(7, "2026-10-04", "Cinema", "Entertainment", null, 60.0, null, null),
        )).copy(salary = listOf(com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth(5, "2026-10", "Oct 2026", 10000.0, null, 10000.0, null, null, null, null, null, null, null, null)))
        val used = com.mohammedsharukms.mypersonaltracker.sheet.SalarySplit.lines(s, "2026-10").associate { it.bucket to it.used }
        assertEquals(240.0, used[com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.ESSENTIALS]!!, 0.0)
        assertEquals(60.0, used[com.mohammedsharukms.mypersonaltracker.sheet.SplitBucket.LIFESTYLE]!!, 0.0)
    }

    @Test fun savingsGoalProgressIsTheMoneyPutAsideForIt() {
        val goals = listOf(com.mohammedsharukms.mypersonaltracker.sheet.SavingsGoal("trip", "Kerala trip", 3000.0, "2027-03-15"),
            com.mohammedsharukms.mypersonaltracker.sheet.SavingsGoal("phone", "Phone", 2000.0, done = true))
        val s = snapshot().copy(savingsGoals = goals, setAside = listOf(
            com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry("a", "2026-09-02", "Savings / SIP", 500.0, goalId = "trip"),
            com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry("b", "2026-10-02", "Savings / SIP", 700.0, goalId = "trip"),
            com.mohammedsharukms.mypersonaltracker.sheet.SetAsideEntry("c", "2026-10-03", "Savings / SIP", 900.0)))
        val p = s.progress(goals[0], java.time.LocalDate.of(2026, 10, 7))
        assertEquals(1200.0, p.saved, 0.0)
        assertEquals(1800.0, p.left, 0.0)
        assertEquals(6, p.monthsLeft)
        assertEquals(300.0, p.perMonth!!, 0.001)
        // Parsed from the sheet: "done" comes as a boolean.
        val parsed = com.mohammedsharukms.mypersonaltracker.sheet.SavingsGoal.from(org.json.JSONObject("""{"id":"x","name":"Bike","target":"1,200","done":true}"""))!!
        assertEquals(1200.0, parsed.target, 0.0)
        assertEquals(true, parsed.done)

        // Deleting a goal keeps the money and unlinks it.
        val write = com.mohammedsharukms.mypersonaltracker.sheet.PendingWrite("w", "deleteSavingsGoal", """{"id":"trip"}""", "", 0, null, null)
        val after = com.mohammedsharukms.mypersonaltracker.sheet.SheetStore.applyLocally(write, s)
        assertEquals(listOf("phone"), after.savingsGoals.map { it.id })
        assertEquals(2100.0, after.setAside.sumOf { it.amount }, 0.0)
        assertTrue(after.setAside.all { it.goalId == null })
    }

    @Test fun budgetAlertsAtEightyPercentAndWhenOverOncePerMonth() {
        val alerts = com.mohammedsharukms.mypersonaltracker.sheet.BudgetAlerts
        fun snap(groceries: Double, cinema: Double) = snapshot(expenses = listOf(
            SheetExpense(5, "2026-10-03", null, "Groceries", null, groceries, null, null),
            SheetExpense(6, "2026-10-04", null, "Entertainment", null, cinema, null, null),
        )).let { base ->
            base.copy(salary = listOf(com.mohammedsharukms.mypersonaltracker.sheet.SalaryMonth(5, "2026-10", "Oct 2026", 10000.0, null, 10000.0, null, null, null, null, null, null, null, null)),
                settings = base.settings.copy(splitShares = mapOf("SPLIT_ESSENTIALS" to 0.5, "SPLIT_EMERGENCY" to 0.1, "SPLIT_SAVINGS" to 0.2, "SPLIT_FAMILY" to 0.1, "SPLIT_LIFESTYLE" to 0.1)))
        }
        val due = alerts.due(snap(4000.0, 1200.0), "2026-10", emptySet())
        assertEquals(listOf("2026-10|Essentials|80", "2026-10|Lifestyle|100"), due.map { it.key })
        assertTrue(alerts.due(snap(3900.0, 100.0), "2026-10", emptySet()).isEmpty())
        assertTrue(alerts.due(snap(4500.0, 0.0), "2026-10", setOf("2026-10|Essentials|80")).isEmpty())
        assertEquals(listOf("2026-10|Essentials|100"), alerts.due(snap(5200.0, 0.0), "2026-10", setOf("2026-10|Essentials|80")).map { it.key })
        assertTrue(alerts.due(snap(4500.0, 0.0), "2026-11", emptySet()).isEmpty())
    }

    @Test fun plannerOrdersPlansAndCountsOnlyPastWear() {
        val sheet = com.mohammedsharukms.mypersonaltracker.sheet.PlannerJson
        val p = sheet.parse(org.json.JSONObject("""
            {"plans":[{"id":"a","date":"2026-10-10","title":"Grocery run"},
                      {"id":"b","date":"2026-10-10","time":"18:00","title":"Cinema","done":true},
                      {"id":"c","date":"2026-10-10","time":"09:30","title":"Gym"}],
             "outfits":[{"id":"outfit-2026-10-05","date":"2026-10-05","items":["shirt","jeans"]},
                        {"id":"outfit-2026-10-08","date":"2026-10-08","items":["shirt","shoes"]},
                        {"id":"outfit-2026-10-11","date":"2026-10-11","items":"kurta,gone","names":"Kurta + Old blazer"}],
             "wardrobe":[{"id":"shirt","name":"Oxford shirt","type":"Top","colour":"Blue"},
                         {"id":"jeans","name":"Jeans","type":"Bottom"},
                         {"id":"shoes","name":"Sneakers","type":"Shoes"},
                         {"id":"kurta","name":"Kurta","type":"Ethnic"}]}"""))!!
        assertEquals(listOf("c", "b", "a"), p.plansOn("2026-10-10").map { it.id })
        assertTrue(p.plans.first { it.id == "b" }.done)
        assertEquals(1, p.timesWorn("shirt", "2026-10-07"))
        assertEquals("2026-10-11", p.nextPlanned("kurta", "2026-10-07"))
        val thursday = p.outfitOn("2026-10-08")!!
        val repeats = p.recentRepeats(thursday)
        assertEquals(listOf("shirt" to 3), repeats.map { it.first.id to it.second })
        assertEquals(listOf("Kurta", "Old blazer"), p.pieces(p.outfitOn("2026-10-11")!!))
        // Saving an outfit queues the clothes as a list.
        val json = com.mohammedsharukms.mypersonaltracker.sheet.Outfit("2026-10-12", listOf("shirt", "jeans")).toJson()
        assertEquals("outfit-2026-10-12", json.getString("id"))
        assertEquals(2, json.getJSONArray("items").length())
    }

    @Test fun lifeGoalProgressStepsAndShortTermGoals() {
        val d = com.mohammedsharukms.mypersonaltracker.sheet.LifeGoalJson.parse(org.json.JSONObject("""
            {"goals":[{"id":"L","title":"Senior iOS developer","horizon":"Long term","area":"Career","status":"Active"},
                      {"id":"A","title":"Ship the app","horizon":"Short term","area":"Career","parentId":"L","due":"2026-10-30","status":"Active"},
                      {"id":"B","title":"SwiftUI course","horizon":"Short term","area":"Learning","parentId":"L","status":"Reached"},
                      {"id":"C","title":"Old idea","horizon":"Short term","parentId":"L","status":"Dropped"},
                      {"id":"M","title":"Read 6 books","horizon":"Short term","area":"Learning","progress":0.5,"status":"Active","due":"2026-12-01"}],
             "steps":[{"id":"1","goalId":"A","step":"Widgets","done":true,"order":1},
                      {"id":"2","goalId":"A","step":"Screenshots","done":"N","order":3},
                      {"id":"3","goalId":"A","step":"Animation","done":"Y","order":2},
                      {"id":"4","goalId":"A","step":"Upload","order":4},
                      {"id":"5","goalId":"L","step":"Lead a feature","order":1}]}"""))!!
        val ship = d.goal("A")!!
        assertEquals(0.5, d.progressOf(ship), 0.0001)
        assertEquals(listOf("1", "3", "2", "4"), d.stepsOf("A").map { it.id })
        assertEquals("Screenshots", d.nextStep(ship)?.step)
        assertEquals(0.5, d.progressOf(d.goal("L")!!), 0.0001)
        assertEquals(0.5, d.progressOf(d.goal("M")!!), 0.0001)
        assertEquals(listOf("A", "M"), d.active(com.mohammedsharukms.mypersonaltracker.sheet.GoalHorizon.SHORT).map { it.id })
        assertEquals("3 days overdue", com.mohammedsharukms.mypersonaltracker.sheet.GoalTime.due("2026-10-05", java.time.LocalDate.of(2026, 10, 8)))
        // Deleting the long-term goal keeps its short-term goals, unlinked.
        val s = snapshot().copy(lifeGoals = d)
        val after = SheetStore.applyLocally(PendingWrite("w", "deleteLifeGoal", """{"id":"L"}""", "", 0, null, null), s)
        val left = after.lifeGoals!!
        assertEquals(null, left.goal("L"))
        assertTrue(left.steps.none { it.goalId == "L" })
        assertEquals(null, left.goal("A")?.parentId)
        assertEquals("Deleted goal step", com.mohammedsharukms.mypersonaltracker.sheet.ui.readable("deleteGoalStep"))
    }

    @Test fun stepsFromThePhoneOnlyRaiseCountsAndNeverLogADay() {
        val base = snapshot()
        val s = base.copy(days = listOf(
            SheetDay(date = "2026-10-06", steps = 9000.0, score = 0.71),
            SheetDay(date = "2026-10-07", steps = 3000.0),
            SheetDay(date = "2026-10-08"),
        ))
        val counts = mapOf("2026-10-05" to 4000, "2026-10-06" to 7500, "2026-10-07" to 6200, "2026-10-08" to 1800)
        val changes = com.mohammedsharukms.mypersonaltracker.health.HealthSteps.stepChanges(counts, s)
        assertEquals(mapOf("2026-10-07" to 6200, "2026-10-08" to 1800), changes)
        val write = PendingWrite("w", "saveHealth", """{"days":[{"date":"2026-10-08","steps":5400},{"date":"2026-10-06","steps":100}]}""", "", 0, null, null)
        val after = SheetStore.applyLocally(write, s)
        assertEquals(5400.0, after.days[2].steps!!, 0.0)
        assertTrue(!after.days[2].isLogged)
        assertEquals(9000.0, after.days[0].steps!!, 0.0)
    }

    @Test fun lifeAdminBriefingBillsRenewalsLoansAndReview() {
        val base = snapshot()
        val s = base.copy(
            days = listOf(SheetDay(date = "2026-10-05", steps = 9000.0, office = "Y", score = 0.71), SheetDay(date = "2026-10-06", steps = 8100.0, sleep = 7.5),
                SheetDay(date = "2026-10-07", steps = 3000.0), SheetDay(date = "2026-10-08")),
            expenses = listOf(SheetExpense(5, "2026-10-06", null, "Groceries", null, 45.0, null, null), SheetExpense(6, "2026-10-07", null, "Food & Dining", null, 20.0, null, null)),
            recurring = listOf(com.mohammedsharukms.mypersonaltracker.sheet.RecurringExpense(4, "Rent", amount = 2500.0, day = 31),
                com.mohammedsharukms.mypersonaltracker.sheet.RecurringExpense(5, "Gym", amount = 200.0, day = 8),
                com.mohammedsharukms.mypersonaltracker.sheet.RecurringExpense(6, "Old", amount = 50.0, day = 8, active = false)),
            renewals = listOf(com.mohammedsharukms.mypersonaltracker.sheet.Renewal(item = "QID", expires = "2026-10-12")),
            settings = base.settings.copy(stepsTarget = 8000.0, dailyLimit = 20.0, monthlyLimit = 600.0),
        )
        val bills = com.mohammedsharukms.mypersonaltracker.sheet.Bills
        assertEquals(listOf("Gym"), bills.due(s, java.time.LocalDate.of(2026, 10, 8)).map { it.description })
        assertEquals(listOf("Rent"), bills.due(s, java.time.LocalDate.of(2026, 11, 30)).map { it.description })
        assertEquals(2, s.stepStreak(today = java.time.LocalDate.of(2026, 10, 7)))
        val (_, body) = com.mohammedsharukms.mypersonaltracker.sheet.Briefing.text(s, java.time.LocalDate.of(2026, 10, 8), includeLive = true)
        assertTrue(body, body.contains("Due today: Gym") && body.contains("QID in 4 days") && body.contains("3,000 steps yesterday") && body.contains("535.00"))
        val loan = com.mohammedsharukms.mypersonaltracker.sheet.Loan(name = "Car", emi = 1000.0, firstPayment = "2026-01-15", months = 12)
        val today = java.time.LocalDate.of(2026, 10, 8)
        assertEquals(9, loan.paidCount(today)); assertEquals(3000.0, loan.remaining(today), 0.0)
        assertEquals("2026-12-15", loan.lastPayment); assertEquals("2026-10-15", loan.nextPayment(today))
        assertEquals("2036-10-18", com.mohammedsharukms.mypersonaltracker.sheet.Renewal(item = "P", kind = com.mohammedsharukms.mypersonaltracker.sheet.RenewalKind.PASSPORT, expires = "2026-10-18").suggestedNext)
        val r = com.mohammedsharukms.mypersonaltracker.sheet.WeeklyReview(s, java.time.LocalDate.of(2026, 10, 5))
        assertEquals(1, r.logged); assertEquals(65.0, r.spent, 0.0); assertEquals(20100, r.steps); assertEquals(2, r.stepGoalDays)
        assertTrue(r.summary.contains("75.00 under budget"))
        // Health: only fills gaps, never logs a day.
        val h = com.mohammedsharukms.mypersonaltracker.health.HealthSteps.changes(mapOf(
            "2026-10-06" to com.mohammedsharukms.mypersonaltracker.health.HealthDay(7000, 6.2, 61.3, true)), s)
        assertEquals(com.mohammedsharukms.mypersonaltracker.health.HealthDay(null, null, 61.3, true), h["2026-10-06"])
        val after = SheetStore.applyLocally(PendingWrite("w", "saveHealth", """{"days":[{"date":"2026-10-07","steps":6400,"sleep":7.8,"workout":true}]}""", "", 0, null, null), s)
        assertTrue(after.days[2].sleep == 7.8 && after.days[2].workout == "Y" && !after.days[2].isLogged)
    }

    @Test fun loanBillFlagAndRenewalCategories() {
        val loan = com.mohammedsharukms.mypersonaltracker.sheet.Loan.from(org.json.JSONObject("""{"id":"l","name":"Car","emi":1450,"firstPayment":"2026-01-05","months":48,"bill":true}"""))!!
        assertTrue(loan.bill && loan.toJson().getBoolean("bill"))
        val old = com.mohammedsharukms.mypersonaltracker.sheet.Loan.from(org.json.JSONObject("""{"id":"l","name":"Car","emi":1450,"firstPayment":"2026-01-05","months":48}"""))!!
        assertTrue(!old.bill)
        assertEquals("Transport", com.mohammedsharukms.mypersonaltracker.sheet.RenewalKind.CAR.category)
        assertEquals("Rent & Utilities", com.mohammedsharukms.mypersonaltracker.sheet.RenewalKind.RENT.category)
    }

    @Test fun yourOwnHabitsCountAndHealthInputsDont() {
        var s = snapshot(days = listOf(SheetDay("2026-10-05", office = "Y", breakfast = "N", workout = "Y", water = 4.0, sleep = 6.0, score = 0.43)))
        fun apply(action: String, payload: String) { s = SheetStore.applyLocally(PendingWrite(action + payload.length, action, payload, "", 0, null, null), s) }
        apply("saveInput", """{"id":"in-read","section":"Habit","name":"Read 20 pages","icon":"book","on":true,"order":1}""")
        apply("saveInput", """{"id":"in-tea","section":"Health","name":"Tea","unit":"cups","target":2,"step":1,"on":true,"order":2}""")
        assertEquals(8.0, s.settings.scoreOutOf!!, 0.0)                    // 5 + water + sleep + Read
        apply("saveInputValues", """{"date":"2026-10-05","values":{"in-read":"Y","in-tea":"3"}}""")
        assertEquals(mapOf("in-read" to "Y", "in-tea" to "3"), s.inputData.values("2026-10-05"))
        assertEquals(4.0, s.days[0].habitsMet!!, 0.0)                      // office, workout, water, Read
        assertEquals(0.5, s.days[0].score!!, 0.0)
        apply("saveInput", """{"id":"in-read","section":"Habit","name":"Read 20 pages","on":false,"order":1}""")
        assertEquals(7.0, s.settings.scoreOutOf!!, 0.0)
        assertEquals(3.0, s.days[0].habitsMet!!, 0.0)
        apply("saveInputValues", """{"date":"2026-10-05","values":{"in-tea":""}}""")
        assertEquals(mapOf("in-read" to "Y"), s.inputData.values("2026-10-05"))
        apply("deleteInput", """{"id":"in-read"}""")
        assertEquals(listOf("in-tea"), s.inputData.inputs.map { it.id }); assertTrue(s.inputData.log.isEmpty())
    }

    @Test fun aDayWithOnlyYourInputsIsLogged() {
        val s = snapshot(days = listOf(SheetDay("2026-10-06"))).let { it.copy(settings = it.settings.copy(scoreOutOf = 8.0)) }
        val inputs = com.mohammedsharukms.mypersonaltracker.sheet.MyInputData(
            listOf(com.mohammedsharukms.mypersonaltracker.sheet.MyInput("a", name = "Pray")),
            listOf(com.mohammedsharukms.mypersonaltracker.sheet.MyInputLog("2026-10-06", "a", "Y")))
        val day = SheetStore.rescore(s.days[0], s.settings, inputs)
        assertEquals(1.0, day.habitsMet!!, 0.0); assertEquals(0.125, day.score!!, 0.0)
        val parsed = com.mohammedsharukms.mypersonaltracker.sheet.MyInput.from(JSONObject("""{"id":"x","section":"Health","name":"Tea","on":false,"target":2}"""))!!
        assertTrue(!parsed.on && parsed.section == com.mohammedsharukms.mypersonaltracker.sheet.InputSection.HEALTH && parsed.stepSize == 1.0)
    }

    @Test fun readsUpiQrBuildsThePaymentLinkAndReadsTheAnswer() {
        val scan = com.mohammedsharukms.mypersonaltracker.sheet.UpiScan.parse("upi://pay?pa=cafe.coffee@okicici&pn=Blue%20Tokai&am=180.50&tn=Latte&mc=5814&cu=INR")!!
        assertEquals("cafe.coffee@okicici", scan.payee.vpa); assertEquals("Blue Tokai", scan.payee.name)
        assertEquals(180.5, scan.amount!!, 0.0); assertEquals("Latte", scan.note); assertEquals("5814", scan.payee.mcc)
        assertEquals("ramesh99@ybl", com.mohammedsharukms.mypersonaltracker.sheet.UpiScan.parse("ramesh99@ybl")!!.payee.vpa)
        assertTrue(com.mohammedsharukms.mypersonaltracker.sheet.UpiScan.parse("https://example.com") == null)
        assertTrue(com.mohammedsharukms.mypersonaltracker.sheet.UpiScan.parse("upi://pay?pa=shop@paytm&am=0")!!.amount == null)
        val request = com.mohammedsharukms.mypersonaltracker.sheet.UpiRequest(
            com.mohammedsharukms.mypersonaltracker.sheet.UpiPayee("shop@okaxis", "Shop & Co", "5411"), 250.0, "Veg", "MPT1")
        assertEquals("upi://pay?pa=shop@okaxis&pn=Shop%20%26%20Co&am=250.00&cu=INR&tr=MPT1&tn=Veg&mc=5411", request.uri)
        val outcome = com.mohammedsharukms.mypersonaltracker.sheet.UpiOutcome
        assertEquals(com.mohammedsharukms.mypersonaltracker.sheet.UpiOutcome.SUCCESS, outcome.parse("txnId=AB1&responseCode=00&Status=SUCCESS&txnRef=MPT1"))
        assertEquals(com.mohammedsharukms.mypersonaltracker.sheet.UpiOutcome.FAILURE, outcome.parse("Status=FAILURE"))
        assertEquals(com.mohammedsharukms.mypersonaltracker.sheet.UpiOutcome.UNKNOWN, outcome.parse(null))
        assertEquals("Groceries", com.mohammedsharukms.mypersonaltracker.sheet.ui.categoryForMcc("5411", listOf("Groceries", "Other")))
    }
}
