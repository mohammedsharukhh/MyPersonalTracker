package com.mohammedsharukms.mypersonaltracker.sheet

import java.time.LocalDate

/**
 * Debug builds only: launch with the `sheetDemo` extra to fill every screen with
 * a realistic snapshot (shaped like the real API response) without a key — for
 * design work and screenshots. Mirrors SheetDemoData.swift.
 */
object SheetDemoData {
    /** [main]/[home] show the sample as a sheet kept in other currencies. */
    fun json(main: String = "QAR", home: String = "INR"): String =
        org.json.JSONObject(
            rawJson().replace("(QAR)", "($main)").replace("(INR)", "($home)")
                .replace("\"SAVINGS_GOAL\"", "\"MAIN_CURRENCY\":\"$main\",\"HOME_CURRENCY\":\"$home\",\"RATE_FOR\":\"QAR/INR\",\"SAVINGS_GOAL\""),
        ).put("food", food()).put("study", study()).put("shopping", shopping()).put("setAside", setAside()).put("savingsGoals", savingsGoals()).put("planner", planner()).put("lifeGoals", lifeGoals()).put("renewals", renewals()).put("loans", loans()).put("myInputs", myInputs()).put("parents", parents()).put("parentMedicines", parentMedicines()).put("parentDates", parentDates()).put("takeHome", takeHome()).put("parentHealth", parentHealth())
            .also { it.getJSONObject("settings").put("EMERGENCY_GOAL", 6000) }.toString()

    /** A habit and two health inputs of your own, logged today and the days before. */
    private fun myInputs(): org.json.JSONObject {
        val today = LocalDate.now()
        val inputs = org.json.JSONArray()
            .put(MyInput("in1", InputSection.HABIT, "Read 20 pages", "book", order = 1.0).toJson())
            .put(MyInput("in2", InputSection.HEALTH, "Meditation", "meditate", "min", 10.0, 5.0, order = 2.0).toJson())
            .put(MyInput("in3", InputSection.HEALTH, "Tea", "fruit", "cups", 2.0, 1.0, order = 3.0).toJson())
        fun log(daysAgo: Long, id: String, value: String) =
            org.json.JSONObject().put("date", today.minusDays(daysAgo).toString()).put("id", id).put("value", value)
        val log = org.json.JSONArray().put(log(0, "in1", "Y")).put(log(0, "in2", "10")).put(log(1, "in1", "N")).put(log(2, "in1", "Y"))
        return org.json.JSONObject().put("inputs", inputs).put("log", log)
    }

    /** This month: a SIP and some emergency money put aside. */
    private fun setAside(): org.json.JSONArray {
        val month = LocalDate.now().toString().take(7)
        return org.json.JSONArray()
            .put(SetAsideEntry("a1", "$month-02", "Savings / SIP", 500.0, "Nifty 50 SIP").toJson())
            .put(SetAsideEntry("a2", "$month-02", "Emergency fund", 300.0).toJson())
            .put(SetAsideEntry("a3", "$month-03", "Savings / SIP", 400.0, "Flights fund", goalId = "g1").toJson())
            .put(SetAsideEntry("a4", "$month-03", "Savings / SIP", 650.0, goalId = "g2").toJson())
    }

    /** A week with a weekend plan, a wardrobe and a few outfits. */
    private fun planner(): org.json.JSONObject {
        val monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        fun day(n: Long) = monday.plusDays(n).toString()
        val wardrobe = listOf(
            Clothing("w1", "Oxford shirt", ClothingType.TOP, "Light blue"), Clothing("w2", "Polo", ClothingType.TOP, "Black"),
            Clothing("w3", "Linen shirt", ClothingType.TOP, "White"), Clothing("w4", "Chinos", ClothingType.BOTTOM, "Beige"),
            Clothing("w5", "Jeans", ClothingType.BOTTOM, "Dark"), Clothing("w6", "Kurta", ClothingType.ETHNIC, "Maroon", "For functions"),
            Clothing("w7", "Sneakers", ClothingType.SHOES, "White"), Clothing("w8", "Loafers", ClothingType.SHOES, "Brown"),
            Clothing("w9", "Watch", ClothingType.ACCESSORY, "Silver"),
        )
        val outfits = listOf(
            Outfit(day(-6), listOf("w3", "w4", "w8")), Outfit(day(0), listOf("w1", "w4", "w8")),
            Outfit(day(2), listOf("w1", "w5", "w7"), "Iron the shirt"), Outfit(day(5), listOf("w6", "w8", "w9"), "Cousin's engagement"),
            Outfit(day(6), listOf("w2", "w5", "w7")),
        )
        val plans = listOf(
            DayPlan("p1", day(0), "07:00", "Gym — legs"), DayPlan("p2", day(2), "19:30", "Dinner with Arun", "Souq Waqif"),
            DayPlan("p3", day(3), null, "Pay electricity bill"), DayPlan("p4", day(4), "20:00", "Call home"),
            DayPlan("p5", day(5), "11:00", "Cousin's engagement", "Al Wakra", "Take the gift"),
            DayPlan("p6", day(5), "18:00", "Grocery run", "Lulu"), DayPlan("p7", day(6), "16:00", "Beach with friends", "Katara"),
        )
        return org.json.JSONObject()
            .put("plans", org.json.JSONArray(plans.map { it.toJson() }))
            .put("outfits", org.json.JSONArray(outfits.map { it.toJson() }))
            .put("wardrobe", org.json.JSONArray(wardrobe.map { it.toJson() }))
    }

    /** Two long-term goals, three short-term ones (two building toward a long-term goal), one reached. */
    private fun lifeGoals(): org.json.JSONObject {
        fun day(n: Long) = LocalDate.now().plusDays(n).toString()
        val goals = listOf(
            LifeGoal("g1", "Become a senior iOS developer", GoalHorizon.LONG, GoalArea.CAREER, "Lead projects and earn enough to support the family comfortably", day(540), created = day(-60), updated = day(-2)),
            LifeGoal("g2", "Buy a home in Kerala", GoalHorizon.LONG, GoalArea.MONEY, "A place for Amma and Appa", day(1800), progress = 0.1, created = day(-90), updated = day(-20)),
            LifeGoal("g3", "Ship my tracker app to TestFlight", GoalHorizon.SHORT, GoalArea.CAREER, null, day(20), "g1", created = day(-30), updated = day(-1)),
            LifeGoal("g4", "Finish the advanced SwiftUI course", GoalHorizon.SHORT, GoalArea.LEARNING, null, day(55), "g1", created = day(-25), updated = day(-16)),
            LifeGoal("g5", "Run 5 km without stopping", GoalHorizon.SHORT, GoalArea.HEALTH, "Feel fit again", day(40), created = day(-14), updated = day(-3)),
            LifeGoal("g6", "IELTS band 7.5", GoalHorizon.SHORT, GoalArea.LEARNING, status = GoalStatus.REACHED, created = day(-120), doneOn = day(-3)),
        )
        val steps = listOf(
            GoalStep("s1", "g3", "Finish the widgets", true, day(-6), 1.0), GoalStep("s2", "g3", "Add the opening animation", true, day(-1), 2.0),
            GoalStep("s3", "g3", "App Store screenshots", order = 3.0), GoalStep("s4", "g3", "Upload a build", order = 4.0),
            GoalStep("s5", "g4", "Layout & animation modules", true, day(-16), 1.0), GoalStep("s6", "g4", "Data flow modules", order = 2.0),
            GoalStep("s7", "g4", "Final project", order = 3.0), GoalStep("s8", "g5", "Run 2 km", true, day(-10), 1.0),
            GoalStep("s9", "g5", "Run 3 km", true, day(-3), 2.0), GoalStep("s10", "g5", "Run 4 km", order = 3.0),
            GoalStep("s11", "g5", "Run 5 km", order = 4.0), GoalStep("s12", "g1", "Lead a feature end to end at work", order = 1.0),
        )
        return org.json.JSONObject()
            .put("goals", org.json.JSONArray(goals.map { it.toJson().put("updated", it.updated ?: "") }))
            .put("steps", org.json.JSONArray(steps.map { it.toJson() }))
    }

    /** Things that expire: one soon, others later. */
    private fun renewals(): org.json.JSONArray {
        fun day(n: Long) = LocalDate.now().plusDays(n).toString()
        return org.json.JSONArray(listOf(
            Renewal("r1", "QID", RenewalKind.QID, day(12)),
            Renewal("r2", "Car registration (Istimara)", RenewalKind.CAR, day(48), cost = 150.0),
            Renewal("r3", "Passport", RenewalKind.PASSPORT, day(610), listOf(180, 90, 30)),
            Renewal("r4", "Flat rent contract", RenewalKind.RENT, day(160)),
        ).map { it.toJson() })
    }

    /** What Mom and Dad asked: one late, one today, a weekly call, monthly tablets, one done. */
    private fun parents(): org.json.JSONArray {
        fun day(n: Long) = LocalDate.now().plusDays(n).toString()
        return org.json.JSONArray(listOf(
            ParentAsk("p1", "Book Dad's eye check-up", ParentWho.DAD, day(-2), "10:00", asked = day(-5)),
            ParentAsk("p2", "Send Mom the wedding photos", ParentWho.MOM, day(0), "19:30", asked = day(-1)),
            ParentAsk("p3", "Video call Grandma", ParentWho.BOTH, day(2), "18:00", ParentRepeat.WEEKLY, nag = false, done = day(-5)),
            ParentAsk("p4", "Refill Dad's BP tablets", ParentWho.DAD, day(9), repeats = ParentRepeat.MONTHLY, notes = "Telmisartan 40 mg", cost = 85.0, finance = true),
            ParentAsk("p5", "Pay the house electricity bill", ParentWho.MOM, day(-3), asked = day(-6), done = day(-3)),
        ).map { it.toJson() })
    }

    /** Dad's BP tablets running low, Mom's weekly vitamin and her diabetes tablets. */
    private fun parentMedicines(): org.json.JSONArray {
        fun day(n: Long) = LocalDate.now().plusDays(n).toString()
        return org.json.JSONArray(listOf(
            ParentMedicine("m1", ParentWho.DAD, "Telmisartan 40 mg", "1 in the morning", 1.0, 9.0, day(-4), 7, 30.0, 85.0, finance = true),
            ParentMedicine("m2", ParentWho.MOM, "Vitamin D3", "1 a week", 1.0 / 7, 8.0, day(0), pack = 8.0),
            ParentMedicine("m3", ParentWho.MOM, "Metformin 500 mg", "1 morning, 1 night", 2.0, 50.0, day(-3), pack = 60.0),
        ).map { it.toJson() })
    }

    /** Mom's birthday in five days, their anniversary, a remembrance day. */
    private fun parentDates(): org.json.JSONArray {
        val birthday = LocalDate.now().plusDays(5)
        return org.json.JSONArray(listOf(
            ParentDate("d1", "Mom's birthday", ParentWho.MOM, ParentDateKind.BIRTHDAY, "%02d-%02d".format(birthday.monthValue, birthday.dayOfMonth), 1966),
            ParentDate("d2", "Their wedding anniversary", ParentWho.BOTH, ParentDateKind.ANNIVERSARY, "02-14", 1990),
            ParentDate("d3", "Grandpa's remembrance day", ParentWho.DAD, ParentDateKind.REMEMBRANCE, "08-03", remind = listOf(1, 0)),
        ).map { it.toJson() })
    }

    /** A trip home in 12 days with two things still to get. */
    private fun takeHome(): org.json.JSONObject {
        fun day(n: Long) = LocalDate.now().plusDays(n).toString()
        return org.json.JSONObject().put("trip", day(12)).put("items", org.json.JSONArray(listOf(
            TakeHomeItem("t1", "Dates and saffron", ParentWho.MOM, added = day(-6)),
            TakeHomeItem("t2", "Reading glasses (+2.0)", ParentWho.DAD, notes = "From the optician near work", added = day(-4)),
            TakeHomeItem("t3", "Perfume for Mom", ParentWho.MOM, got = true, added = day(-9)),
        ).map { it.toJson() }))
    }

    /** Dad's blood pressure over two weeks and two of Mom's sugar readings. */
    private fun parentHealth(): org.json.JSONArray {
        fun day(n: Long) = LocalDate.now().plusDays(n).toString()
        val bp = listOf(Triple(-12L, 142.0, 92.0), Triple(-9L, 138.0, 90.0), Triple(-6L, 135.0, 88.0), Triple(-4L, 140.0, 89.0), Triple(-2L, 132.0, 86.0), Triple(0L, 130.0, 84.0))
        return org.json.JSONArray(bp.mapIndexed { i, (d, a, b) -> ParentReading("h$i", day(d), "08:30", ParentWho.DAD, ReadingKind.BLOOD_PRESSURE, a, b).toJson() } + listOf(
            ParentReading("s1", day(-3), null, ParentWho.MOM, ReadingKind.SUGAR_FASTING, 112.0).toJson(),
            ParentReading("s2", day(-1), null, ParentWho.MOM, ReadingKind.SUGAR_FASTING, 106.0).toJson(),
        ))
    }

    /** A car loan part-way through and phone instalments. */
    private fun loans(): org.json.JSONArray {
        fun day(n: Long) = LocalDate.now().plusDays(n).toString()
        return org.json.JSONArray(listOf(
            Loan("l1", "Car loan", "QNB", 1450.0, day(-400), 48, principal = 60000.0, rate = 4.5),
            Loan("l2", "iPhone instalments", "Ooredoo", 210.0, day(-150), 12),
        ).map { it.toJson() })
    }

    /** Two things being saved for. */
    private fun savingsGoals(): org.json.JSONArray {
        val next = LocalDate.now().year + 1
        return org.json.JSONArray()
            .put(SavingsGoal("g1", "Trip home to Kerala", 3000.0, "$next-03-31", notes = "Flights + gifts").toJson())
            .put(SavingsGoal("g2", "New laptop", 4500.0, "$next-08-31").toJson())
    }

    /** A list half-way through a shop, and two past trips. */
    private fun shopping(): org.json.JSONObject {
        val now = LocalDate.now()
        val items = org.json.JSONArray()
        fun add(id: String, item: String, qty: String?, status: String = "Need", price: Double? = null, fromPlan: Boolean = false, boughtOn: String? = null, trip: String? = null) {
            items.put(org.json.JSONObject().put("id", id).put("item", item).put("quantity", qty ?: org.json.JSONObject.NULL)
                .put("category", ShoppingAisle.guess(item)).put("status", status).put("price", price ?: org.json.JSONObject.NULL)
                .put("fromPlan", fromPlan).put("boughtOn", boughtOn ?: org.json.JSONObject.NULL).put("tripId", trip ?: org.json.JSONObject.NULL))
        }
        add("s1", "Milk", "2 L"); add("s2", "Eggs", "30"); add("s3", "Onion", "2 kg"); add("s4", "Tomato", "1 kg")
        add("s5", "Basmati rice", "5 kg"); add("s6", "Chicken breast", "1 kg", fromPlan = true)
        add("s7", "Banana", "1 dozen", "In cart", 6.5); add("s8", "Curd", "1 kg", "In cart", 7.0); add("s9", "Dishwash liquid", null, "In cart")
        val week = now.minusDays(6).toString()
        listOf("Milk" to "2 L", "Eggs" to "30", "Atta" to "5 kg", "Sunflower oil" to "1 L", "Apple" to "1 kg", "Paneer" to "400 g")
            .zip(listOf(11.0, 18.0, 22.0, 14.0, 9.0, 12.0)).forEachIndexed { i, (it, price) -> add("b$i", it.first, it.second, "Bought", price, boughtOn = week, trip = "trip1") }
        val trips = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", "trip1").put("date", week).put("store", "Lulu").put("items", 6).put("total", 86).put("method", "Debit Card"))
            .put(org.json.JSONObject().put("id", "trip0").put("date", now.minusDays(13).toString()).put("store", "Carrefour").put("items", 9).put("total", 124.5).put("method", "Cash"))
        return org.json.JSONObject().put("items", items).put("trips", trips)
    }

    /** Two subjects with concepts at every stage, two weeks of sessions and goals. */
    fun studyData(): StudyData {
        val now = LocalDate.now()
        fun day(offset: Int) = now.plusDays(offset.toLong()).toString()
        val swift = StudySubject("s-swift", "SwiftUI", "Move into iOS development", "https://developer.apple.com/tutorials/swiftui", 5.0, day(60), color = 0, started = day(-30))
        val dsa = StudySubject("s-dsa", "DSA", "Interview prep", "Grokking Algorithms", 4.0, day(25), color = 1, started = day(-20))
        val ielts = StudySubject("s-ielts", "IELTS", status = StudySubject.Status.PAUSED, color = 2, started = day(-60))
        val S = StudyTopic.Stage.entries
        val topics = listOf(
            StudyTopic("t1", swift.id, "State & Binding", S[3], 5, day(-9), day(12), 4, "@State owns, @Binding borrows.", added = day(-30)),
            StudyTopic("t2", swift.id, "Observation (@Observable)", S[2], 4, day(-4), day(0), 2, "Views track the properties they read.", added = day(-25)),
            StudyTopic("t3", swift.id, "Navigation stacks", S[1], 3, day(-1), day(3), 1, added = day(-20)),
            StudyTopic("t4", swift.id, "Animations", added = day(-18)),
            StudyTopic("t5", swift.id, "SwiftData", added = day(-18)),
            StudyTopic("t6", dsa.id, "Binary search", S[2], 2, day(-3), day(-1), 2, "lo <= hi; mid = lo + (hi - lo) / 2", added = day(-20)),
            StudyTopic("t7", dsa.id, "Two pointers", S[1], 3, day(0), day(4), 1, added = day(-15)),
            StudyTopic("t8", dsa.id, "Dynamic programming", added = day(-10)),
            StudyTopic("t9", ielts.id, "Writing Task 2", S[1], added = day(-50)),
        )
        val plan = listOf(Triple(0, dsa, "t7") to (45.0 to StudyEntry.Kind.PRACTICE), Triple(-1, swift, "t3") to (60.0 to StudyEntry.Kind.LEARN), Triple(-1, dsa, "t6") to (20.0 to StudyEntry.Kind.REVISE),
            Triple(-2, swift, "t3") to (40.0 to StudyEntry.Kind.LEARN), Triple(-3, dsa, "t6") to (50.0 to StudyEntry.Kind.LEARN), Triple(-4, swift, "t2") to (30.0 to StudyEntry.Kind.REVISE),
            Triple(-5, swift, null) to (75.0 to StudyEntry.Kind.PROJECT), Triple(-6, dsa, null) to (35.0 to StudyEntry.Kind.TEST), Triple(-8, swift, "t1") to (30.0 to StudyEntry.Kind.REVISE),
            Triple(-9, dsa, "t6") to (45.0 to StudyEntry.Kind.LEARN), Triple(-10, swift, "t2") to (60.0 to StudyEntry.Kind.LEARN), Triple(-12, dsa, null) to (40.0 to StudyEntry.Kind.PRACTICE))
        val sessions = plan.mapIndexed { i, (a, b) ->
            StudyEntry("e$i", day(a.first), a.second.id, a.second.name, a.third, topics.firstOrNull { it.id == a.third }?.topic, b.first, b.second, 4,
                if (b.second == StudyEntry.Kind.TEST) "Mock round: 3 of 4 solved" else null, if (b.second == StudyEntry.Kind.TEST) 75.0 else null)
        }
        val goals = listOf(
            StudyGoal("g1", "20 hours of SwiftUI this month", StudyGoal.Kind.HOURS, swift.id, 20.0, day(-14), day(16)),
            StudyGoal("g2", "Master the DSA basics", StudyGoal.Kind.TOPICS, dsa.id, 3.0, day(-20), day(25)),
            StudyGoal("g3", "Clear the coding interview", StudyGoal.Kind.MILESTONE, dsa.id, null, day(-20), day(40)),
        )
        return StudyData(listOf(swift, dsa, ielts), topics, sessions, goals)
    }

    private fun study(): org.json.JSONObject {
        val d = studyData()
        return org.json.JSONObject()
            .put("subjects", org.json.JSONArray(d.subjects.map { it.toJson() }))
            .put("topics", org.json.JSONArray(d.topics.map { it.toJson() }))
            .put("sessions", org.json.JSONArray(d.sessions.map { it.toJson() }))
            .put("goals", org.json.JSONArray(d.goals.map { it.toJson() }))
    }

    /** A week of meals and this week's plan. */
    private fun food(): org.json.JSONObject {
        val log = org.json.JSONArray()
        var row = 4
        fun meal(offset: Int, time: String, type: String, foods: List<List<Any>>, cost: Double? = null) {
            val date = LocalDate.now().plusDays(offset.toLong()).toString()
            foods.forEachIndexed { n, f ->
                log.put(org.json.JSONObject().put("row", row++).put("mealId", "demo-$offset-$type").put("date", date).put("time", time).put("meal", type)
                    .put("food", f[0]).put("amount", f[1]).put("unit", f[2]).put("calories", f[3]).put("protein", f[4]).put("carbs", f[5]).put("fat", f[6])
                    .apply { if (n == 0 && cost != null) { put("cost", cost); put("ledgerRow", 20) } })
            }
        }
        for (offset in -6..0) {
            meal(offset, "08:10", "Breakfast", listOf(listOf("Oats with milk", 1, "bowl", 290, 12, 44, 8), listOf("Boiled egg", 2, "egg", 156, 12.6, 1.2, 10.6), listOf("Banana", 1, "medium", 105, 1.3, 27, 0.4)))
            meal(offset, "13:20", "Lunch", if (offset % 2 == 0) listOf(listOf("Chicken biryani", 1, "plate", 600, 28, 75, 20), listOf("Curd / plain yogurt", 1, "cup", 150, 8.5, 11.4, 8))
                else listOf(listOf("Grilled chicken breast", 150, "g", 248, 46.5, 0, 5.4), listOf("Basmati rice, cooked", 1, "cup", 210, 4.4, 46, 0.5), listOf("Dal tadka", 1, "bowl", 260, 13, 30, 9)),
                if (offset % 2 == 0) 28.0 else null)
            if (offset < 0) meal(offset, "20:30", "Dinner", listOf(listOf("Roti / chapati", 3, "piece", 360, 9.3, 54, 11.1), listOf("Paneer butter masala", 1, "bowl", 450, 17, 15, 36)))
        }
        meal(0, "16:45", "Snack", listOf(listOf("Karak chai", 1, "cup", 130, 3.5, 18, 5), listOf("Almonds", 10, "nuts", 82, 3, 3, 7)), 3.0)
        val plan = org.json.JSONArray()
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        for (d in 0..6) {
            val date = monday.plusDays(d.toLong()).toString()
            plan.put(org.json.JSONObject().put("row", 4 + d * 2).put("date", date).put("meal", "Lunch").put("food", "Grilled chicken breast").put("amount", 150).put("unit", "g")
                .put("calories", 248).put("protein", 46.5).put("carbs", 0).put("fat", 5.4).put("cost", 9).put("eaten", false))
            plan.put(org.json.JSONObject().put("row", 5 + d * 2).put("date", date).put("meal", "Dinner").put("food", "Dal tadka").put("amount", 1).put("unit", "bowl")
                .put("calories", 260).put("protein", 13).put("carbs", 30).put("fat", 9).put("cost", 4).put("eaten", false))
        }
        val mine = org.json.JSONArray().put(org.json.JSONObject().put("row", 4).put("name", "Mum's chicken curry").put("amount", 1).put("unit", "bowl")
            .put("calories", 320).put("protein", 28).put("carbs", 9).put("fat", 19).put("cost", 8))
        return org.json.JSONObject().put("log", log).put("plan", plan).put("myFoods", mine)
    }

    private fun rawJson(): String {
        val today = LocalDate.now()
        val start = today.minusDays(20)
        val end = today.plusDays(4)

        val days = (0 until 25).joinToString(",") { offset ->
            val date = start.plusDays(offset.toLong()).toString()
            if (offset > 20 || offset % 7 == 5) return@joinToString """{"date":"$date"}"""
            fun yn(seed: Int) = if ((offset + seed) % 4 == 0) "N" else "Y"
            val water = listOf(3.0, 4.0, 3.5, 4.5)[offset % 4]
            val sleep = listOf(8.0, 7.0, 7.5, 6.5)[offset % 4]
            val met = listOf(yn(0), yn(1), yn(2), yn(3), yn(1)).count { it == "Y" } + (if (water >= 4) 1 else 0) + (if (sleep >= 7.5) 1 else 0)
            val score = met / 7.0
            val status = if (score >= 0.85) "Excellent" else if (score >= 0.6) "On Track" else "Needs Work"
            """{"date":"$date","office":"${yn(0)}","breakfast":"${yn(1)}","lunch":"Y","protein":"${yn(3)}","workout":"${yn(2)}",
               "water":$water,"sleep":$sleep,"steps":${4000 + offset * 350},"weight":61,"screen":${1.2 + (offset % 3) * 0.6},
               "notes":"","habitsMet":$met,"score":$score,"status":"$status","streak":${offset % 3}}"""
        }

        val items = listOf(
            Triple(-18, "Hotel", "Rent & Utilities") to ("Cash" to 500.0),
            Triple(-17, "New phone", "Gifts & Family") to ("Credit Card" to 750.0),
            Triple(-15, "Uber trip", "Transport") to ("Apple Pay / Google Pay" to 42.0),
            Triple(-12, "Lunch out", "Food & Dining") to ("Debit Card" to 35.0),
            Triple(-9, "Groceries", "Groceries") to ("Debit Card" to 86.0),
            Triple(-6, "Recharge", "Mobile & Internet") to ("Cash" to 65.0),
            Triple(-3, "Pharmacy", "Health & Pharmacy") to ("Cash" to 18.0),
            Triple(-1, "Coffee", "Food & Dining") to ("Apple Pay / Google Pay" to 14.0),
            Triple(0, "Metro card", "Transport") to ("Cash" to 12.0),
        )
        val expenses = items.mapIndexed { index, (info, pay) ->
            val date = today.plusDays(info.first.toLong()).toString()
            """{"row":${5 + index},"date":"$date","description":"${info.second}","category":"${info.third}","method":"${pay.first}","qar":${pay.second},"inr":${pay.second * 26.1},"notes":""}"""
        }.joinToString(",")

        val m0 = today.minusMonths(1)
        val m1 = today
        val m2 = today.plusMonths(1)
        fun ym(d: LocalDate) = "%04d-%02d".format(d.year, d.monthValue)
        fun label(d: LocalDate) = d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " " + d.year

        return """
        {
          "settings":{"START_DATE":"$start","END_DATE":"$end","RATE":26.1,"DAILY_LIMIT":20,"MONTHLY_LIMIT":620,
                      "WATER_TARGET":4,"SLEEP_TARGET":7.5,"STEPS_TARGET":8000,"SCREEN_LIMIT":3,"SALARY":2500,"SAVINGS_GOAL":0.95,"GREAT_DAY":0.85},
          "lists":{"categories":["Groceries","Food & Dining","Transport","Rent & Utilities","Mobile & Internet","Health & Pharmacy","Personal Care","Household","Clothing","Remittance","Entertainment","Gifts & Family","Education","Travel","Other"],
                   "methods":["Cash","Debit Card","Credit Card","Apple Pay / Google Pay","Bank Transfer","Other"]},
          "dashboard":[
            {"label":"OVERALL HABIT SCORE","value":"74.6%","caption":"average of your daily scores"},
            {"label":"CURRENT STREAK","value":"2","caption":"consecutive great days"},
            {"label":"DAYS LOGGED","value":"18","caption":"days with entries"},
            {"label":"EXCELLENT DAYS","value":"6","caption":"days at or above the threshold"},
            {"label":"TOTAL SPEND (QAR)","value":"1,522.00","caption":"period to date"},
            {"label":"TOTAL SPEND (INR)","value":"39,724","caption":"at the rate in Settings"},
            {"label":"AVG DAILY SPEND","value":"72.48","caption":"QAR per elapsed day"},
            {"label":"DAYS OVER LIMIT","value":"6","caption":"elapsed days above the cap"},
            {"label":"AVG WATER (L / DAY)","value":"3.8","caption":"target set in Settings"},
            {"label":"AVG SLEEP (HOURS)","value":"7.3","caption":"target set in Settings"},
            {"label":"BUDGET LEFT (QAR)","value":"(144.00)","caption":"monthly limit minus spend"},
            {"label":"NET SAVINGS (QAR)","value":"27,493.00","caption":"all months combined"}
          ],
          "dashboardTables":[
            {"title":"HABIT SUCCESS RATES","headers":["Habit","Logged","Done","Rate"],"rows":[
              ["In Office by 9:00 AM","18","14","77.8%"],["Breakfast Done","18","13","72.2%"],["Lunch Taken","18","18","100.0%"],
              ["Protein in Lunch","18","13","72.2%"],["Workout Done","18","9","50.0%"],["Water Target Met","18","9","50.0%"],
              ["Sleep Target Met","18","9","50.0%"],["Steps Target Met","18","5","27.8%"],["Screen Time In Limit","18","18","100.0%"]]},
            {"title":"SPEND BY CATEGORY","headers":["Category","QAR","INR","% of Total"],"rows":[
              ["Groceries","86.00","2,245","5.7%"],["Food & Dining","49.00","1,279","3.2%"],["Transport","54.00","1,409","3.5%"],
              ["Rent & Utilities","500.00","13,050","32.9%"],["Mobile & Internet","65.00","1,697","4.3%"],["Health & Pharmacy","18.00","470","1.2%"],
              ["Gifts & Family","750.00","19,575","49.3%"],["TOTAL","1,522.00","39,724","100.0%"]]},
            {"title":"THIS PERIOD AT A GLANCE","headers":["Metric","Value"],"rows":[
              ["Best streak (days)","3"],["On track days","8"],["Needs work days","4"],["Average steps per day","7,150"],
              ["Average screen time (hrs)","1.8"],["Latest weight (kg)","61.0"],["Expense entries logged","9"],
              ["Zero-spend days so far","12"],["Highest single expense (QAR)","750.00"],["Biggest spend day (QAR)","750.00"]]},
            {"title":"MONEY SNAPSHOT","headers":["Metric","Value"],"rows":[
              ["Total income (QAR)","31,000.00"],["Total expenses (QAR)","1,522.00"],["Remittance sent (QAR)","1,000.00"],
              ["Remittance sent (INR)","26,100"],["Net savings (QAR)","27,493.00"],["Savings rate","88.7%"],["Savings goal","95.0%"],
              ["This month's allowance (QAR)","620.00"],["Spend vs limit","123.2%"],["Projected month-end (QAR)","801.00"]]}
          ],
          "budget":{"title":"","headers":["Date","Spent (QAR)","Spent (INR)","vs Daily Limit","Month-to-Date (QAR)","Budget Left (QAR)"],"rows":[
            ["01-Sep-26","14.00","365","OK","14.00","606.00"],["02-Sep-26","12.00","313","OK","26.00","594.00"],["03-Sep-26","86.00","2,245","OVER","112.00","508.00"]]},
          "days":[$days],
          "expenses":[$expenses],
          "salary":[
            {"row":5,"month":"${ym(m0)}","label":"${label(m0)}","salary":2500,"otherIncome":1000,"totalIncome":3500,"expenses":1322,"remittanceQar":1000,"remittanceInr":26100,"netSavings":1178,"savingsRate":0.337,"goalSavings":3325,"cumulative":1178,"status":"Below goal"},
            {"row":6,"month":"${ym(m1)}","label":"${label(m1)}","salary":2500,"otherIncome":0,"totalIncome":2500,"expenses":200,"remittanceQar":0,"remittanceInr":0,"netSavings":2300,"savingsRate":0.92,"goalSavings":2375,"cumulative":3478,"status":"Below goal"},
            {"row":7,"month":"${ym(m2)}","label":"${label(m2)}","salary":2500,"otherIncome":0,"totalIncome":2500,"expenses":0,"remittanceQar":0,"remittanceInr":0,"netSavings":2500,"savingsRate":1,"goalSavings":2375,"cumulative":5978,"status":"Goal met"}
          ],
          "weekly":{"headers":["Week","Week Start","Week End","Days Logged","Avg Habit Score","Avg Water (L)","Avg Sleep (hrs)","Total Steps","Workouts","Spend (QAR)","Spend (INR)","Weekly Budget (QAR)","Variance (QAR)"],"rows":[
            ["1","15-Aug-26","21-Aug-26","6","71.4%","3.7","7.6","31,200","3","1,292.00","33,721","140.00","(1,152.00)"],
            ["2","22-Aug-26","28-Aug-26","6","76.2%","3.9","7.2","44,800","4","35.00","914","140.00","105.00"],
            ["3","29-Aug-26","04-Sep-26","6","78.6%","3.8","7.3","52,100","2","195.00","5,090","140.00","(55.00)"]]},
          "monthly":{"headers":["Month","Days Logged","Avg Habit Score","Workout Days","Spend (QAR)","Income (QAR)","Net Savings (QAR)","Savings Rate"],"rows":[
            ["Aug 2026","14","73.5%","7","1,322.00","3,500.00","1,178.00","33.7%"],["Sep 2026","4","78.6%","2","200.00","2,500.00","2,300.00","92.0%"]]}
        }
        """
    }
}
