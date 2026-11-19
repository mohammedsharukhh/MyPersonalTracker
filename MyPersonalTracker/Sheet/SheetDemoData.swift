//
//  SheetDemoData.swift
//  MyPersonalTracker
//
//  Debug builds only: launch with `-sheetDemo` to fill every screen with a
//  realistic snapshot (shaped like the real API response) without a key —
//  for design work, previews and screenshots.
//

#if DEBUG
import Foundation

enum SheetDemoData {
    static var isEnabled: Bool { ProcessInfo.processInfo.arguments.contains("-sheetDemo") }

    static var snapshot: SheetSnapshot? {
        // `-demoMain AED -demoHome PKR` shows the sample as a sheet kept in other currencies.
        let main = UserDefaults.standard.string(forKey: "demoMain") ?? "QAR"
        let home = UserDefaults.standard.string(forKey: "demoHome") ?? "INR"
        let text = json
            .replacingOccurrences(of: "(QAR)", with: "(\(main))")
            .replacingOccurrences(of: "(INR)", with: "(\(home))")
            .replacingOccurrences(of: #""SAVINGS_GOAL""#, with: #""MAIN_CURRENCY":"\#(main)","HOME_CURRENCY":"\#(home)","RATE_FOR":"QAR/INR","SAVINGS_GOAL""#)
        guard var snapshot = try? JSONDecoder().decode(SheetSnapshot.self, from: Data(text.utf8)) else { return nil }
        snapshot.food = demoFood
        snapshot.study = demoStudy
        snapshot.shopping = demoShopping
        let month = String(SheetDate.today.prefix(7))
        let nextYear = (Int(month.prefix(4)) ?? 2026) + 1
        snapshot.savingsGoals = [SavingsGoal(id: "g1", name: "Trip home to Kerala", target: 3000, due: "\(nextYear)-03-31", notes: "Flights + gifts"),
                                 SavingsGoal(id: "g2", name: "New laptop", target: 4500, due: "\(nextYear)-08-31")]
        snapshot.setAside = [SetAsideEntry(id: "a1", date: "\(month)-02", bucket: "Savings / SIP", amount: 500, note: "Nifty 50 SIP"),
                             SetAsideEntry(id: "a2", date: "\(month)-02", bucket: "Emergency fund", amount: 300),
                             SetAsideEntry(id: "a3", date: "\(month)-03", bucket: "Savings / SIP", amount: 400, note: "Flights fund", goalId: "g1"),
                             SetAsideEntry(id: "a4", date: "\(month)-03", bucket: "Savings / SIP", amount: 650, goalId: "g2")]
        snapshot.settings.emergencyGoal = 6000
        snapshot.planner = demoPlanner
        snapshot.lifeGoals = demoLifeGoals
        func date(days: Int) -> String { SheetDate.string(Calendar.current.date(byAdding: .day, value: days, to: .now) ?? .now) }
        snapshot.renewals = [
            Renewal(id: "r1", item: "QID", kind: .qid, expires: date(days: 12)),
            Renewal(id: "r2", item: "Car registration (Istimara)", kind: .car, expires: date(days: 48), cost: 150),
            Renewal(id: "r3", item: "Passport", kind: .passport, expires: date(days: 610), remind: [180, 90, 30]),
            Renewal(id: "r4", item: "Flat rent contract", kind: .rent, expires: date(days: 160))
        ]
        snapshot.parents = [
            ParentAsk(id: "p1", task: "Book Dad's eye check-up", who: .dad, date: date(days: -2), time: "10:00", asked: date(days: -5)),
            ParentAsk(id: "p2", task: "Send Mom the wedding photos", who: .mom, date: SheetDate.today, time: "19:30", asked: date(days: -1)),
            ParentAsk(id: "p3", task: "Video call Grandma", who: .both, date: date(days: 2), time: "18:00", repeats: .weekly, nag: false,
                      done: date(days: -5)),
            ParentAsk(id: "p4", task: "Refill Dad's BP tablets", who: .dad, date: date(days: 9), repeats: .monthly, notes: "Telmisartan 40 mg",
                      cost: 85, finance: true),
            ParentAsk(id: "p5", task: "Pay the house electricity bill", who: .mom, date: date(days: -3), asked: date(days: -6), done: date(days: -3))
        ]
        snapshot.parentMedicines = [
            ParentMedicine(id: "m1", who: .dad, name: "Telmisartan 40 mg", dose: "1 in the morning", perDay: 1, left: 9, counted: date(days: -4),
                           warn: 7, pack: 30, cost: 85, finance: true),
            ParentMedicine(id: "m2", who: .mom, name: "Vitamin D3", dose: "1 a week", perDay: 1.0 / 7, left: 8, counted: SheetDate.today, pack: 8),
            ParentMedicine(id: "m3", who: .mom, name: "Metformin 500 mg", dose: "1 morning, 1 night", perDay: 2, left: 50, counted: date(days: -3), pack: 60)
        ]
        let birthday = SheetDate.date(date(days: 5)).map { Calendar.current.dateComponents([.month, .day], from: $0) }
        snapshot.parentDates = [
            ParentDate(id: "d1", title: "Mom's birthday", who: .mom, kind: .birthday,
                       day: String(format: "%02d-%02d", birthday?.month ?? 1, birthday?.day ?? 1), year: 1966),
            ParentDate(id: "d2", title: "Their wedding anniversary", who: .both, kind: .anniversary, day: "02-14", year: 1990),
            ParentDate(id: "d3", title: "Grandpa's remembrance day", who: .dad, kind: .remembrance, day: "08-03", remind: [1, 0])
        ]
        snapshot.takeHome = TakeHomeData(trip: date(days: 12), items: [
            TakeHomeItem(id: "t1", item: "Dates and saffron", who: .mom, added: date(days: -6)),
            TakeHomeItem(id: "t2", item: "Reading glasses (+2.0)", who: .dad, notes: "From the optician near work", added: date(days: -4)),
            TakeHomeItem(id: "t3", item: "Perfume for Mom", who: .mom, got: true, added: date(days: -9))
        ])
        snapshot.parentHealth = [(-12, 142, 92), (-9, 138, 90), (-6, 135, 88), (-4, 140, 89), (-2, 132, 86), (0, 130, 84)].enumerated().map { i, r in
            ParentReading(id: "h\(i)", date: date(days: r.0), time: "08:30", who: .dad, kind: .bloodPressure, value: Double(r.1), value2: Double(r.2))
        } + [ParentReading(id: "s1", date: date(days: -3), who: .mom, kind: .sugarFasting, value: 112),
             ParentReading(id: "s2", date: date(days: -1), who: .mom, kind: .sugarFasting, value: 106)]
        snapshot.loans = [
            Loan(id: "l1", name: "Car loan", lender: "QNB", emi: 1450, firstPayment: date(days: -400), months: 48, principal: 60000, rate: 4.5),
            Loan(id: "l2", name: "iPhone instalments", lender: "Ooredoo", emi: 210, firstPayment: date(days: -150), months: 12)
        ]
        snapshot.myInputs = MyInputData(inputs: [
            MyInput(id: "in1", section: .habit, name: "Read 20 pages", icon: "book", on: true, order: 1),
            MyInput(id: "in2", section: .health, name: "Meditation", icon: "meditate", unit: "min", target: 10, step: 5, on: true, order: 2),
            MyInput(id: "in3", section: .health, name: "Tea", icon: "fruit", unit: "cups", target: 2, step: 1, on: true, order: 3)
        ], log: [
            MyInputLog(date: SheetDate.today, id: "in1", value: "Y"), MyInputLog(date: SheetDate.today, id: "in2", value: "10"),
            MyInputLog(date: date(days: -1), id: "in1", value: "N"), MyInputLog(date: date(days: -2), id: "in1", value: "Y")
        ])
        snapshot.settings.scoreOutOf = snapshot.settings.scoreOutOf(with: snapshot.myInputs)
        for i in snapshot.days.indices where snapshot.days[i].date <= SheetDate.today && snapshot.days[i].score != nil {
            SheetStore.rescore(&snapshot.days[i], settings: snapshot.settings, inputs: snapshot.myInputs)
        }
        // A few weeks of steps for the streak, the widget and the reviews.
        for i in snapshot.days.indices where snapshot.days[i].date <= SheetDate.today {
            snapshot.days[i].steps = snapshot.days[i].steps ?? Double([9100, 8400, 10200, 6050, 8800, 11900, 8240][i % 7])
        }
        return snapshot
    }

    /// Two long-term goals, three short-term ones (two building toward a long-term goal), one reached.
    static var demoLifeGoals: LifeGoalData {
        func date(days: Int) -> String { SheetDate.string(Calendar.current.date(byAdding: .day, value: days, to: .now) ?? .now) }
        var d = LifeGoalData()
        var senior = LifeGoal(id: "g1", title: "Become a senior iOS developer", horizon: .long, area: .career,
                              why: "Lead projects and earn enough to support the family comfortably", due: date(days: 540), created: date(days: -60))
        senior.updated = date(days: -2)
        var home = LifeGoal(id: "g2", title: "Buy a home in Kerala", horizon: .long, area: .money, why: "A place for Amma and Appa",
                            due: date(days: 1800), progress: 0.1, created: date(days: -90))
        home.updated = date(days: -20)
        var ship = LifeGoal(id: "g3", title: "Ship my tracker app to TestFlight", horizon: .short, area: .career, due: date(days: 20),
                            parentId: "g1", created: date(days: -30))
        ship.updated = date(days: -1)
        var swiftUI = LifeGoal(id: "g4", title: "Finish the advanced SwiftUI course", horizon: .short, area: .learning, due: date(days: 55),
                               parentId: "g1", created: date(days: -25))
        swiftUI.updated = date(days: -16)
        var run = LifeGoal(id: "g5", title: "Run 5 km without stopping", horizon: .short, area: .health, why: "Feel fit again",
                           due: date(days: 40), created: date(days: -14))
        run.updated = date(days: -3)
        let ielts = LifeGoal(id: "g6", title: "IELTS band 7.5", horizon: .short, area: .learning, status: .reached,
                             created: date(days: -120), doneOn: date(days: -3))
        d.goals = [senior, home, ship, swiftUI, run, ielts]
        d.steps = [
            GoalStep(id: "s1", goalId: "g3", step: "Finish the widgets", done: true, doneOn: date(days: -6), order: 1),
            GoalStep(id: "s2", goalId: "g3", step: "Add the opening animation", done: true, doneOn: date(days: -1), order: 2),
            GoalStep(id: "s3", goalId: "g3", step: "App Store screenshots", order: 3),
            GoalStep(id: "s4", goalId: "g3", step: "Upload a build", order: 4),
            GoalStep(id: "s5", goalId: "g4", step: "Layout & animation modules", done: true, doneOn: date(days: -16), order: 1),
            GoalStep(id: "s6", goalId: "g4", step: "Data flow modules", order: 2),
            GoalStep(id: "s7", goalId: "g4", step: "Final project", order: 3),
            GoalStep(id: "s8", goalId: "g5", step: "Run 2 km", done: true, doneOn: date(days: -10), order: 1),
            GoalStep(id: "s9", goalId: "g5", step: "Run 3 km", done: true, doneOn: date(days: -3), order: 2),
            GoalStep(id: "s10", goalId: "g5", step: "Run 4 km", order: 3),
            GoalStep(id: "s11", goalId: "g5", step: "Run 5 km", order: 4),
            GoalStep(id: "s12", goalId: "g1", step: "Lead a feature end to end at work", order: 1)
        ]
        return d
    }

    /// A week with a weekend plan, a wardrobe and a few outfits.
    static var demoPlanner: PlannerData {
        let monday = FoodView.monday(of: .now)
        func day(_ offset: Int) -> String { SheetDate.string(Calendar.current.date(byAdding: .day, value: offset, to: monday) ?? monday) }
        var p = PlannerData()
        p.wardrobe = [
            Clothing(id: "w1", name: "Oxford shirt", type: .top, colour: "Light blue"),
            Clothing(id: "w2", name: "Polo", type: .top, colour: "Black"),
            Clothing(id: "w3", name: "Linen shirt", type: .top, colour: "White"),
            Clothing(id: "w4", name: "Chinos", type: .bottom, colour: "Beige"),
            Clothing(id: "w5", name: "Jeans", type: .bottom, colour: "Dark"),
            Clothing(id: "w6", name: "Kurta", type: .ethnic, colour: "Maroon", notes: "For functions"),
            Clothing(id: "w7", name: "Sneakers", type: .shoes, colour: "White"),
            Clothing(id: "w8", name: "Loafers", type: .shoes, colour: "Brown"),
            Clothing(id: "w9", name: "Watch", type: .accessory, colour: "Silver")
        ]
        p.outfits = [
            Outfit(date: day(-6), items: ["w3", "w4", "w8"]),
            Outfit(date: day(0), items: ["w1", "w4", "w8"]),
            Outfit(date: day(2), items: ["w1", "w5", "w7"], note: "Iron the shirt"),
            Outfit(date: day(5), items: ["w6", "w8", "w9"], note: "Cousin's engagement"),
            Outfit(date: day(6), items: ["w2", "w5", "w7"])
        ]
        p.plans = [
            DayPlan(id: "p1", date: day(0), time: "07:00", title: "Gym — legs"),
            DayPlan(id: "p2", date: day(2), time: "19:30", title: "Dinner with Arun", place: "Souq Waqif"),
            DayPlan(id: "p3", date: day(3), title: "Pay electricity bill"),
            DayPlan(id: "p4", date: day(4), time: "20:00", title: "Call home"),
            DayPlan(id: "p5", date: day(5), time: "11:00", title: "Cousin's engagement", place: "Al Wakra", notes: "Take the gift"),
            DayPlan(id: "p6", date: day(5), time: "18:00", title: "Grocery run", place: "Lulu"),
            DayPlan(id: "p7", date: day(6), time: "16:00", title: "Beach with friends", place: "Katara")
        ]
        return p
    }

    /// A list half-way through a shop, and two past trips.
    static var demoShopping: ShoppingData {
        let cal = Calendar.current
        func day(_ offset: Int) -> String { SheetDate.string(cal.date(byAdding: .day, value: offset, to: .now)!) }
        var items = [
            ShoppingItem(id: "s1", item: "Milk", quantity: "2 L"), ShoppingItem(id: "s2", item: "Eggs", quantity: "30"),
            ShoppingItem(id: "s3", item: "Onion", quantity: "2 kg"), ShoppingItem(id: "s4", item: "Tomato", quantity: "1 kg"),
            ShoppingItem(id: "s5", item: "Basmati rice", quantity: "5 kg"), ShoppingItem(id: "s6", item: "Chicken breast", quantity: "1 kg", fromPlan: true),
            ShoppingItem(id: "s7", item: "Banana", quantity: "1 dozen", status: .inCart, price: 6.5),
            ShoppingItem(id: "s8", item: "Curd", quantity: "1 kg", status: .inCart, price: 7),
            ShoppingItem(id: "s9", item: "Dishwash liquid", status: .inCart)
        ]
        for (i, name) in ["Milk", "Eggs", "Atta", "Sunflower oil", "Apple", "Paneer"].enumerated() {
            var bought = ShoppingItem(id: "b\(i)", item: name, quantity: ["2 L", "30", "5 kg", "1 L", "1 kg", "400 g"][i], status: .bought, price: [11, 18, 22, 14, 9, 12][i])
            bought.boughtOn = day(-6); bought.tripId = "trip1"
            items.append(bought)
        }
        let trips = [ShoppingTrip(id: "trip1", date: day(-6), store: "Lulu", items: 6, total: 86, method: "Debit Card", notes: nil),
                     ShoppingTrip(id: "trip0", date: day(-13), store: "Carrefour", items: 9, total: 124.5, method: "Cash", notes: nil)]
        return ShoppingData(items: items, trips: trips)
    }

    /// Two subjects with concepts at every stage, two weeks of sessions and goals.
    static var demoStudy: StudyData {
        let cal = Calendar.current
        func day(_ offset: Int) -> String { SheetDate.string(cal.date(byAdding: .day, value: offset, to: .now)!) }
        let swift = StudySubject(id: "s-swift", name: "SwiftUI", why: "Move into iOS development", resource: "https://developer.apple.com/tutorials/swiftui",
                                 weeklyHours: 5, targetDate: day(60), color: 0, started: day(-30))
        let dsa = StudySubject(id: "s-dsa", name: "DSA", why: "Interview prep", resource: "Grokking Algorithms",
                               weeklyHours: 4, targetDate: day(25), color: 1, started: day(-20))
        let ielts = StudySubject(id: "s-ielts", name: "IELTS", status: .paused, color: 2, started: day(-60))
        let topics = [
            StudyTopic(id: "t1", subjectId: swift.id, topic: "State & Binding", stage: .mastered, confidence: 5, lastStudied: day(-9), nextReview: day(12), reviews: 4, notes: "@State owns, @Binding borrows.", added: day(-30)),
            StudyTopic(id: "t2", subjectId: swift.id, topic: "Observation (@Observable)", stage: .revising, confidence: 4, lastStudied: day(-4), nextReview: day(0), reviews: 2, notes: "Views track the properties they read.", added: day(-25)),
            StudyTopic(id: "t3", subjectId: swift.id, topic: "Navigation stacks", stage: .learning, confidence: 3, lastStudied: day(-1), nextReview: day(3), reviews: 1, added: day(-20)),
            StudyTopic(id: "t4", subjectId: swift.id, topic: "Animations", stage: .toLearn, added: day(-18)),
            StudyTopic(id: "t5", subjectId: swift.id, topic: "SwiftData", stage: .toLearn, added: day(-18)),
            StudyTopic(id: "t6", subjectId: dsa.id, topic: "Binary search", stage: .revising, confidence: 2, lastStudied: day(-3), nextReview: day(-1), reviews: 2, notes: "lo <= hi; mid = lo + (hi - lo) / 2", added: day(-20)),
            StudyTopic(id: "t7", subjectId: dsa.id, topic: "Two pointers", stage: .learning, confidence: 3, lastStudied: day(0), nextReview: day(4), reviews: 1, added: day(-15)),
            StudyTopic(id: "t8", subjectId: dsa.id, topic: "Dynamic programming", stage: .toLearn, added: day(-10)),
            StudyTopic(id: "t9", subjectId: ielts.id, topic: "Writing Task 2", stage: .learning, added: day(-50))
        ]
        let plan: [(Int, StudySubject, String?, Double, StudyEntry.Kind)] = [
            (0, dsa, "t7", 45, .practice), (-1, swift, "t3", 60, .learn), (-1, dsa, "t6", 20, .revise), (-2, swift, "t3", 40, .learn),
            (-3, dsa, "t6", 50, .learn), (-4, swift, "t2", 30, .revise), (-5, swift, nil, 75, .project), (-6, dsa, nil, 35, .test),
            (-8, swift, "t1", 30, .revise), (-9, dsa, "t6", 45, .learn), (-10, swift, "t2", 60, .learn), (-12, dsa, nil, 40, .practice)
        ]
        let sessions = plan.enumerated().map { i, p in
            StudyEntry(id: "e\(i)", date: day(p.0), subjectId: p.1.id, subject: p.1.name, topicId: p.2,
                       topic: topics.first { $0.id == p.2 }?.topic, minutes: p.3, kind: p.4, focus: 4,
                       notes: p.4 == .test ? "Mock round: 3 of 4 solved" : nil, score: p.4 == .test ? 75 : nil)
        }
        let goals = [
            StudyGoal(id: "g1", title: "20 hours of SwiftUI this month", kind: .hours, subjectId: swift.id, target: 20, start: day(-14), due: day(16)),
            StudyGoal(id: "g2", title: "Master the DSA basics", kind: .topics, subjectId: dsa.id, target: 3, start: day(-20), due: day(25)),
            StudyGoal(id: "g3", title: "Clear the coding interview", kind: .milestone, subjectId: dsa.id, due: day(40))
        ]
        return StudyData(subjects: [swift, dsa, ielts], topics: topics, sessions: sessions, goals: goals)
    }

    /// A few days of meals and this week's plan.
    private static var demoFood: FoodData {
        let cal = Calendar.current
        func day(_ offset: Int) -> String { SheetDate.string(cal.date(byAdding: .day, value: offset, to: .now)!) }
        var log: [FoodEntry] = []
        var row = 4
        func meal(_ offset: Int, _ time: String, _ type: MealType, _ foods: [(String, Double, String, Double, Double, Double, Double)], cost: Double? = nil) {
            let id = "demo-\(offset)-\(type.rawValue)"
            for (n, f) in foods.enumerated() {
                log.append(FoodEntry(row: row, mealId: id, date: day(offset), time: time, meal: type.rawValue, food: f.0, amount: f.1, unit: f.2,
                                     calories: f.3, protein: f.4, carbs: f.5, fat: f.6, cost: n == 0 ? cost : nil, photo: nil,
                                     ledgerRow: n == 0 && cost != nil ? 20 : nil, notes: nil))
                row += 1
            }
        }
        for offset in -6...0 {
            meal(offset, "08:10", .breakfast, [("Oats with milk", 1, "bowl", 290, 12, 44, 8), ("Boiled egg", 2, "egg", 156, 12.6, 1.2, 10.6), ("Banana", 1, "medium", 105, 1.3, 27, 0.4)])
            meal(offset, "13:20", .lunch, offset % 2 == 0
                 ? [("Chicken biryani", 1, "plate", 600, 28, 75, 20), ("Curd / plain yogurt", 1, "cup", 150, 8.5, 11.4, 8)]
                 : [("Grilled chicken breast", 150, "g", 248, 46.5, 0, 5.4), ("Basmati rice, cooked", 1, "cup", 210, 4.4, 46, 0.5), ("Dal tadka", 1, "bowl", 260, 13, 30, 9)],
                 cost: offset % 2 == 0 ? 28 : nil)
            if offset < 0 {
                meal(offset, "20:30", .dinner, [("Roti / chapati", 3, "piece", 360, 9.3, 54, 11.1), ("Paneer butter masala", 1, "bowl", 450, 17, 15, 36)])
            }
        }
        meal(0, "16:45", .snack, [("Karak chai", 1, "cup", 130, 3.5, 18, 5), ("Almonds", 10, "nuts", 82, 3, 3, 7)], cost: 3)

        let monday = SheetDate.string(FoodView.monday(of: .now))
        var plan: [PlanItem] = []
        for offset in 0..<7 {
            let date = SheetDate.string(cal.date(byAdding: .day, value: offset, to: SheetDate.date(monday)!)!)
            plan.append(PlanItem(row: 4 + offset * 2, date: date, meal: "Lunch",
                                 item: FoodItem(food: "Grilled chicken breast", amount: 150, unit: "g", calories: 248, protein: 46.5, carbs: 0, fat: 5.4, cost: 9), eaten: false))
            plan.append(PlanItem(row: 5 + offset * 2, date: date, meal: "Dinner",
                                 item: FoodItem(food: "Dal tadka", amount: 1, unit: "bowl", calories: 260, protein: 13, carbs: 30, fat: 9, cost: 4), eaten: false))
        }
        let mine = [MyFood(row: 4, name: "Mum's chicken curry", amount: 1, unit: "bowl", calories: 320, protein: 28, carbs: 9, fat: 19, cost: 8, barcode: nil)]
        return FoodData(log: log, plan: plan, myFoods: mine, photoError: nil)
    }

    private static var days: String {
        let cal = Calendar.current
        let start = cal.date(byAdding: .day, value: -20, to: cal.startOfDay(for: .now))!
        return (0..<25).map { offset -> String in
            let date = SheetDate.string(cal.date(byAdding: .day, value: offset, to: start)!)
            guard offset <= 20, offset % 7 != 5 else {
                return #"{"date":"\#(date)"}"#
            }
            let yn = { (seed: Int) in (offset + seed) % 4 == 0 ? "N" : "Y" }
            let water = [3.0, 4.0, 3.5, 4.5][offset % 4]
            let sleep = [8.0, 7.0, 7.5, 6.5][offset % 4]
            let met = [yn(0), yn(1), yn(2), yn(3), yn(1)].filter { $0 == "Y" }.count + (water >= 4 ? 1 : 0) + (sleep >= 7.5 ? 1 : 0)
            let score = Double(met) / 7
            let status = score >= 0.85 ? "Excellent" : (score >= 0.6 ? "On Track" : "Needs Work")
            return """
            {"date":"\(date)","office":"\(yn(0))","breakfast":"\(yn(1))","lunch":"Y","protein":"\(yn(3))","workout":"\(yn(2))",
             "water":\(water),"sleep":\(sleep),"steps":\(4000 + offset * 350),"weight":61,"screen":\(1.2 + Double(offset % 3) * 0.6),
             "notes":"","habitsMet":\(met),"score":\(score),"status":"\(status)","streak":\(offset % 3)}
            """
        }.joined(separator: ",")
    }

    private static var expenses: String {
        let cal = Calendar.current
        let items: [(Int, String, String, String, Double)] = [
            (-18, "Hotel", "Rent & Utilities", "Cash", 500), (-17, "New phone", "Gifts & Family", "Credit Card", 750),
            (-15, "Uber trip", "Transport", "Apple Pay / Google Pay", 42), (-12, "Lunch out", "Food & Dining", "Debit Card", 35),
            (-9, "Groceries", "Groceries", "Debit Card", 86), (-6, "Recharge", "Mobile & Internet", "Cash", 65),
            (-3, "Pharmacy", "Health & Pharmacy", "Cash", 18), (-1, "Coffee", "Food & Dining", "Apple Pay / Google Pay", 14),
            (0, "Metro card", "Transport", "Cash", 12)
        ]
        return items.enumerated().map { index, item in
            let date = SheetDate.string(cal.date(byAdding: .day, value: item.0, to: .now)!)
            return #"{"row":\#(5 + index),"date":"\#(date)","description":"\#(item.1)","category":"\#(item.2)","method":"\#(item.3)","qar":\#(item.4),"inr":\#(item.4 * 26.1),"notes":""}"#
        }.joined(separator: ",")
    }

    private static var json: String {
        let cal = Calendar.current
        let start = SheetDate.string(cal.date(byAdding: .day, value: -20, to: .now)!)
        let end = SheetDate.string(cal.date(byAdding: .day, value: 4, to: .now)!)
        return """
        {
          "settings":{"START_DATE":"\(start)","END_DATE":"\(end)","RATE":26.1,"DAILY_LIMIT":20,"MONTHLY_LIMIT":620,
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
          "days":[\(days)],
          "expenses":[\(expenses)],
          "salary":[
            {"row":5,"month":"2026-08","label":"Aug 2026","salary":2500,"otherIncome":1000,"totalIncome":3500,"expenses":1322,"remittanceQar":1000,"remittanceInr":26100,"netSavings":1178,"savingsRate":0.337,"goalSavings":3325,"cumulative":1178,"status":"Below goal"},
            {"row":6,"month":"2026-09","label":"Sep 2026","salary":2500,"otherIncome":0,"totalIncome":2500,"expenses":200,"remittanceQar":0,"remittanceInr":0,"netSavings":2300,"savingsRate":0.92,"goalSavings":2375,"cumulative":3478,"status":"Below goal"},
            {"row":7,"month":"2026-10","label":"Oct 2026","salary":2500,"otherIncome":0,"totalIncome":2500,"expenses":0,"remittanceQar":0,"remittanceInr":0,"netSavings":2500,"savingsRate":1,"goalSavings":2375,"cumulative":5978,"status":"Goal met"}
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
#endif
