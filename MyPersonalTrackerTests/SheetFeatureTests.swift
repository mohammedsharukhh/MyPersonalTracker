//
//  SheetFeatureTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import Testing
@testable import MyPersonalTracker

@Suite("Receipt reading")
struct ReceiptReaderTests {
    @Test func takesTheTotalLineNotSubtotalOrVat() {
        let lines = ["LULU HYPERMARKET", "Doha, Qatar", "Milk 1L        6.50", "Bread          4.00",
                     "Subtotal      10.50", "VAT 5%         0.53", "TOTAL         11.03", "Cash          20.00", "Change         8.97"]
        #expect(ReceiptReader.parse(lines: lines).amount == 11.03)
        #expect(ReceiptReader.parse(lines: lines).merchant == "Lulu Hypermarket")
    }

    @Test func totalOnTheNextLineAndThousands() {
        let lines = ["Grand Total", "QAR 1,234.50"]
        #expect(ReceiptReader.parse(lines: lines).amount == 1234.50)
    }

    @Test func fallsBackToTheLargestAmount() {
        #expect(ReceiptReader.parse(lines: ["Coffee 14.00", "Cake 18.50"]).amount == 18.50)
    }

    @Test func findsARecentDateOnly() {
        let now = SheetDate.date("2026-10-06")!
        let parsed = ReceiptReader.parse(lines: ["Date: 04/10/2026 13:22", "Valid until 31/12/2029"], now: now)
        #expect(parsed.date.map { Calendar.current.component(.year, from: $0) } == 2026)
    }
}

@Suite("Offline estimates")
struct OptimisticTests {
    private func snapshot() throws -> SheetSnapshot {
        let json = """
        {"settings":{"START_DATE":"2026-10-01","END_DATE":"2026-10-31","RATE":26.1,"DAILY_LIMIT":20,"WATER_TARGET":4,"SLEEP_TARGET":7.5,"GREAT_DAY":0.85},
         "lists":{"categories":["Groceries"],"methods":["Cash"]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[{"date":"2026-10-06"}],"expenses":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """
        return try JSONDecoder().decode(SheetSnapshot.self, from: Data(json.utf8))
    }

    @Test func aQueuedDayIsScoredLikeTheSheet() throws {
        var s = try snapshot()
        let write = PendingWrite(id: "1", action: "saveDay", payload: [
            "date": "2026-10-06", "office": "Y", "breakfast": "Y", "lunch": "Y", "protein": "N", "workout": "",
            "water": 4, "sleep": 7, "steps": "", "weight": "", "screen": "", "notes": ""
        ], summary: "", createdAt: .now, notBefore: nil, tempRow: nil)
        SheetStore.applyLocally(write, to: &s)
        // 3 Y + water met + sleep missed = 4 of 7.
        #expect(s.days[0].habitsMet == 4)
        #expect(s.days[0].status == "Needs Work")
        #expect(s.days[0].isLogged)
    }

    @Test func aQueuedExpenseShowsWithItsHomeAmount() throws {
        var s = try snapshot()
        let write = PendingWrite(id: "2", action: "addExpense", payload: [
            "date": "2026-10-06", "description": "Coffee", "category": "Groceries", "method": "Cash", "qar": 10, "notes": ""
        ], summary: "", createdAt: .now, notBefore: nil, tempRow: -1)
        SheetStore.applyLocally(write, to: &s)
        #expect(s.expenses.count == 1)
        #expect(s.expenses[0].isPending)
        #expect(s.expenses[0].inr == 261)
    }

    @Test func theQueueSurvivesEncoding() throws {
        let write = PendingWrite(id: "3", action: "deleteExpense", payload: ["row": 12], summary: "x", createdAt: .now, notBefore: nil, tempRow: nil)
        let back = try JSONDecoder().decode(PendingWrite.self, from: JSONEncoder().encode(write))
        #expect((back.payload["row"] as? NSNumber)?.intValue == 12)
    }
}

@Suite("Insights")
struct InsightTests {
    @Test func flagsAMonthOverItsLimit() throws {
        let json = """
        {"settings":{"START_DATE":"2026-10-01","END_DATE":"2026-10-31","MONTHLY_LIMIT":100,"DAILY_LIMIT":20},
         "lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],
         "expenses":[{"row":5,"date":"2026-10-02","category":"Rent & Utilities","qar":150},
                     {"row":6,"date":"2026-10-03","category":"Groceries","qar":20}],
         "salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """
        let s = try JSONDecoder().decode(SheetSnapshot.self, from: Data(json.utf8))
        let insights = InsightEngine.insights(for: s, today: SheetDate.date("2026-10-06")!)
        #expect(insights.contains { $0.id == "pace" && $0.tone == .bad })
        #expect(insights.contains { $0.id == "category" && $0.text.hasPrefix("Rent & Utilities") })
    }
}

@Suite("Data export")
struct DataExportTests {
    @Test func exportsEveryTableAsAZip() throws {
        let tables: [(name: String, rows: [[String]])] = [
            (name: "Expenses", rows: [["Date", "Description", "Amount"], ["2026-10-06", "Lunch, with \"friends\"", "28"]]),
            (name: "Food Log", rows: [["Date", "Food"], ["2026-10-06", "Dal tadka"]])
        ]
        let url = try DataExport.zip(tables)
        #expect(url.pathExtension == "zip")
        let size = (try FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.intValue ?? 0
        #expect(size > 100)
    }

    @Test func fallsBackToWhatIsOnThePhone() throws {
        let json = """
        {"settings":{"MAIN_CURRENCY":"AED","HOME_CURRENCY":"PKR"},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[{"date":"2026-10-06"}],
         "expenses":[{"row":5,"date":"2026-10-06","description":"Coffee","qar":12}],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """
        let s = try JSONDecoder().decode(SheetSnapshot.self, from: Data(json.utf8))
        let tables = DataExport.tables(from: s)
        let expenses = try #require(tables.first { $0.name == "Expenses" })
        #expect(expenses.rows[0].contains("Amount (AED)"))
        #expect(expenses.rows[1][1] == "Coffee")
    }
}

@Suite("Food nutrition")
struct FoodNutritionTests {
    @Test func yourNumbersScaleWithTheAmount() {
        var roti = FoodItem(food: "Roti", amount: 1, unit: "piece", calories: 120, protein: 3.1, carbs: 18, fat: 3.7)
        roti.setNutrition(calories: 100, protein: 4)       // my roti is smaller
        roti.setAmount(3)
        #expect(roti.calories == 300)
        #expect(roti.protein == 12)
        #expect(abs(roti.carbs - 54) < 0.001)               // untouched values still scale
    }
}

@Suite("Currency and people")
struct CurrencyPeopleTests {
    private func settings(_ json: String) throws -> SheetSettings {
        try JSONDecoder().decode(SheetSettings.self, from: Data(json.utf8))
    }

    @Test func aRateSetForAnotherPairIsStale() throws {
        // Switched to AED/PKR but the 26.1 rate was set for QAR → INR: don't trust it.
        let stale = try settings(#"{"MAIN_CURRENCY":"AED","HOME_CURRENCY":"PKR","RATE":26.1,"RATE_FOR":"QAR/INR"}"#)
        #expect(!stale.rateIsCurrent)
        let current = try settings(#"{"MAIN_CURRENCY":"AED","HOME_CURRENCY":"PKR","RATE":75.4,"RATE_FOR":"AED/PKR"}"#)
        #expect(current.rateIsCurrent)
    }

    @Test func balancesPerPerson() {
        let entries = [
            PeopleEntry(date: "2026-10-01", person: "Rahul", type: .lent, amount: 500, note: nil),
            PeopleEntry(date: "2026-10-04", person: "rahul", type: .gotBack, amount: 200, note: nil),
            PeopleEntry(date: "2026-10-02", person: "Ammi", type: .giftGiven, amount: 300, note: nil),
            PeopleEntry(date: "2026-10-03", person: "Sam", type: .borrowed, amount: 100, note: nil)
        ]
        let people = PersonSummary.all(entries)
        #expect(people.first { $0.id == "rahul" }?.balance == 300)    // Rahul owes you 300
        #expect(people.first { $0.id == "sam" }?.balance == -100)     // you owe Sam 100
        #expect(people.first { $0.id == "ammi" }?.balance == 0)       // gifts don't create debt
        #expect(people.first { $0.id == "ammi" }?.gifted == 300)
    }
}

@Suite("Study")
struct StudyTests {
    private let today = SheetDate.date("2026-10-06")!

    private func data() -> StudyData {
        let topics = [
            StudyTopic(id: "a", subjectId: "s", topic: "Closures", stage: .mastered, nextReview: "2026-10-20"),
            StudyTopic(id: "b", subjectId: "s", topic: "Actors", stage: .learning, lastStudied: "2026-10-05", nextReview: "2026-10-04"),
            StudyTopic(id: "c", subjectId: "s", topic: "Macros", stage: .toLearn),
            StudyTopic(id: "d", subjectId: "p", topic: "Paused topic", stage: .learning, nextReview: "2026-10-01")
        ]
        let sessions = [
            StudyEntry(date: "2026-10-06", subjectId: "s", minutes: 30), StudyEntry(date: "2026-10-05", subjectId: "s", minutes: 60),
            StudyEntry(date: "2026-10-04", subjectId: "s", minutes: 45), StudyEntry(date: "2026-10-01", subjectId: "s", minutes: 90)
        ]
        return StudyData(subjects: [StudySubject(id: "s", name: "Swift"), StudySubject(id: "p", name: "Old", status: .paused)],
                         topics: topics, sessions: sessions, goals: [])
    }

    @Test func revisionSpacesOutAndIsSoonerWhenShaky() {
        #expect(StudySchedule.days(confidence: 1, reviews: 1) == 1)
        #expect(StudySchedule.days(confidence: 3, reviews: 1) == 4)
        #expect(StudySchedule.days(confidence: 3, reviews: 3) == 8)
        #expect(StudySchedule.days(confidence: 5, reviews: 20) == 90)
    }

    @Test func studyingAConceptMovesItOnAndSchedulesIt() {
        let t = StudyTopic(subjectId: "s", topic: "Actors").studied(on: "2026-10-06", confidence: 2)
        #expect(t.stage == .learning)
        #expect(t.reviews == 1)
        #expect(t.lastStudied == "2026-10-06")
        #expect(t.nextReview == "2026-10-08")
    }

    @Test func streakTodayAndWeek() {
        let d = data()
        #expect(d.streak(today: today) == 3)
        #expect(d.minutes(on: "2026-10-06") == 30)
        #expect(d.weekMinutes(today) == 90)   // Mon 5 – Sun 11 Oct
    }

    @Test func dueSkipsPausedSubjectsAndProgressIsWeighted() {
        let d = data()
        #expect(d.dueTopics(today: "2026-10-06").map(\.id) == ["b"])
        #expect(abs(d.progress(of: "s") - (1 + 0.35) / 3) < 0.001)
        #expect(d.currentTopic?.id == "b")
    }

    @Test func goalProgress() {
        let d = data()
        let hours = StudyGoal(title: "4h", kind: .hours, subjectId: "s", target: 4, start: "2026-10-04", due: "2026-10-31")
        let p = d.progress(of: hours, today: today)
        #expect(p.done == 2.25)
        #expect(p.state == .onTrack)
        let topics = StudyGoal(title: "Master 2", kind: .topics, target: 2, start: "2026-09-01", due: "2026-10-05")
        #expect(d.progress(of: topics, today: today).state == .overdue)
    }

    @Test func aQueuedSessionAndTopicShowAtOnce() throws {
        var s = try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"expenses":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "study":{"subjects":[{"id":"s","name":"Swift","status":"Active"}],"topics":[],"sessions":[],"goals":[]}}
        """.utf8))
        #expect(s.settings.studyDailyTarget == 60)
        func write(_ action: String, _ payload: [String: Any]) -> PendingWrite {
            PendingWrite(id: UUID().uuidString, action: action, payload: payload, summary: "", createdAt: .now, notBefore: nil, tempRow: nil)
        }
        SheetStore.applyLocally(write("saveStudyTopic", ["id": "t", "subjectId": "s", "topic": "Actors", "stage": "Learning", "reviews": 1]), to: &s)
        SheetStore.applyLocally(write("saveStudySession", ["id": "e", "date": "2026-10-06", "subjectId": "s", "topicId": "t", "minutes": 25, "kind": "Revise"]), to: &s)
        #expect(s.studyData.topics.first?.stage == .learning)
        #expect(s.studyData.sessions.first?.topic == "Actors")
        #expect(s.studyData.sessions.first?.subject == "Swift")
        #expect(s.studyData.sessions.first?.kind == .revise)
        SheetStore.applyLocally(write("deleteStudySubject", ["id": "s"]), to: &s)
        #expect(s.studyData.subjects.isEmpty && s.studyData.topics.isEmpty)
        #expect(s.studyData.sessions.count == 1)   // history stays
    }
}

@Suite("Targets from weight")
struct BodyCalcTests {
    @Test func mifflinStJeorWithGoalAndMacros() {
        // Man, 30, 175 cm, 75 kg, lightly active, losing fat at 2 g/kg protein.
        let r = BodyCalc.targets(weight: 75, height: 175, age: 30, sex: .male, activity: .light, goal: .lose, proteinPerKg: 2)
        #expect(r.bmr == 1699)          // 750 + 1093.75 − 150 + 5
        #expect(r.maintenance == 2336)  // × 1.375
        #expect(r.calories == 1840)     // − 500, to the nearest 10
        #expect(r.protein == 150)
        #expect(r.fat == 51)
        #expect(r.carbs == 195)
    }

    @Test func neverBelowASafeFloor() {
        let r = BodyCalc.targets(weight: 45, height: 150, age: 60, sex: .female, activity: .sedentary, goal: .lose, proteinPerKg: 1.6)
        #expect(r.calories == 1200)
    }
}

@Suite("Food amounts")
struct FoodAmountTests {
    @Test func readsTheAmountTypedWithTheFood() {
        #expect(FoodQuantity.split("milk 250ml").name == "milk")
        #expect(FoodQuantity.split("milk 250ml").quantity == FoodQuantity(amount: 250, unit: "ml"))
        #expect(FoodQuantity.split("250 ml milk").quantity == FoodQuantity(amount: 250, unit: "ml"))
        #expect(FoodQuantity.split("rice 1.5 cups").quantity == FoodQuantity(amount: 1.5, unit: "cup"))
        #expect(FoodQuantity.split("half litre milk").quantity == nil)
        #expect(FoodQuantity.split("milk 1L").quantity == FoodQuantity(amount: 1000, unit: "ml"))
        let eggs = FoodQuantity.split("2 eggs")
        #expect(eggs.name == "eggs")
        #expect(eggs.quantity == FoodQuantity(amount: 2, unit: nil))
    }

    @Test func switchesBetweenServingAndMillilitres() {
        // Milk, full fat: 1 cup = 240 g, 149 kcal, 7.7 g protein.
        var milk = FoodItem(food: "Milk", amount: 1, unit: "cup", calories: 149, protein: 7.7, carbs: 11.7, fat: 7.9, grams: 240)
        #expect(milk.unitChoices == ["cup", "g", "ml"])
        milk.apply(FoodQuantity(amount: 250, unit: "ml"))
        #expect(milk.unit == "ml")
        #expect(milk.amount == 250)
        #expect(abs(milk.calories - 155.2) < 0.1)
        #expect(abs(milk.protein - 8.02) < 0.01)
        milk.setUnit("cup")
        #expect(abs(milk.amount - 1.04) < 0.001)
    }

    @Test func aPlainNumberIsServings() {
        var egg = FoodItem(food: "Egg", amount: 1, unit: "piece", calories: 78, protein: 6.3, carbs: 0.6, fat: 5.3, grams: 50)
        egg.apply(FoodQuantity(amount: 3, unit: nil))
        #expect(egg.amount == 3)
        #expect(abs(egg.protein - 18.9) < 0.01)
    }
}

@Suite("Shopping")
struct ShoppingTests {
    @Test func quickAddReadsTheQuantityAsWritten() {
        #expect(ShoppingParse.split("milk 2L") == ("Milk", "2 L"))
        #expect(ShoppingParse.split("rice 5 kg") == ("Rice", "5 kg"))
        #expect(ShoppingParse.split("30 eggs") == ("Eggs", "30"))
        #expect(ShoppingParse.split("dishwash liquid") == ("Dishwash liquid", nil))
    }

    @Test func guessesTheAisle() {
        #expect(ShoppingAisle.guess("Milk") == "Dairy & Eggs")
        #expect(ShoppingAisle.guess("Onion") == "Vegetables")
        #expect(ShoppingAisle.guess("Basmati rice") == "Grains & Bread")
        #expect(ShoppingAisle.guess("Toothpaste") == "Personal care")
        #expect(ShoppingAisle.guess("Batteries AA") == "Household")
        #expect(ShoppingAisle.guess("Gift wrap") == "Other")
    }

    @Test func finishingATripMovesTheCartToHistory() throws {
        var s = try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"expenses":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "shopping":{"items":[{"id":"a","item":"Milk","quantity":"2 L","category":"Dairy & Eggs","status":"In cart","price":11},
                              {"id":"b","item":"Onion","category":"Vegetables","status":"Need"}],"trips":[]}}
        """.utf8))
        let write = PendingWrite(id: "1", action: "checkoutShopping", payload: [
            "tripId": "t", "date": "2026-10-07", "store": "Lulu", "total": 11, "method": "Cash", "addToFinance": true, "notes": "",
            "items": [["id": "a", "price": 11]]
        ], summary: "", createdAt: .now, notBefore: nil, tempRow: nil)
        SheetStore.applyLocally(write, to: &s)
        #expect(s.shoppingData.inCart.isEmpty)
        #expect(s.shoppingData.toBuy.map(\.id) == ["b"])
        #expect(s.shoppingData.bought(on: "t").map(\.item) == ["Milk"])
        #expect(s.shoppingData.spent(month: "2026-10").total == 11)
        #expect(s.shoppingData.buyAgain == ["Milk"])
    }
}

@Suite("Study plan")
struct StudyPlanTests {
    @Test func pastedSyllabusLosesBulletsAndKeepsOrder() {
        let text = """
        1. Arrays and strings
        2) Two pointers
          - Sliding window
        • Binary search
        a) Recursion

        Two pointers
        """
        #expect(SyllabusParse.lines(text) == ["Arrays and strings", "Two pointers", "Sliding window", "Binary search", "Recursion"])
    }

    @Test func conceptsKeepTheOrderTheyWereAdded() {
        let topics = ["Zebra", "Apple", "Mango"].map { StudyTopic(subjectId: "s", topic: $0, added: "2026-10-07") }
        let data = StudyData(subjects: [StudySubject(id: "s", name: "S")], topics: topics)
        #expect(data.topics(of: "s").map(\.topic) == ["Zebra", "Apple", "Mango"])
    }

    @Test func pacingToTheTargetDate() {
        let today = SheetDate.date("2026-10-07")!
        var topics = (0..<10).map { StudyTopic(subjectId: "s", topic: "T\($0)") }
        topics[0].stage = .mastered
        topics[1].stage = .revising; topics[1].lastStudied = "2026-10-05"
        let data = StudyData(subjects: [StudySubject(id: "s", name: "S", targetDate: "2026-11-04")], topics: topics)
        let pace = data.pace(of: data.subjects[0], today: today)!
        #expect(pace.left == 9)
        #expect(pace.daysLeft == 28)
        #expect(abs(pace.perWeekNeeded - 2.25) < 0.01)   // 9 left over 4 weeks
        #expect(pace.state == .behind)                   // about 0.5 a week lately
    }

    @Test func testScoresOldestFirst() {
        let data = StudyData(sessions: [
            StudyEntry(date: "2026-10-05", subjectId: "s", kind: .test, score: 70),
            StudyEntry(date: "2026-09-28", subjectId: "s", kind: .test, score: 60),
            StudyEntry(date: "2026-10-06", subjectId: "s", kind: .learn)
        ])
        #expect(data.testScores(of: "s").compactMap(\.score) == [60, 70])
    }
}

@Suite("Revision spacing")
struct RevisionSpacingTests {
    @Test func forgettingStartsOver() {
        #expect(StudySchedule.days(confidence: 1, reviews: 5) == 1)
        #expect(StudySchedule.days(confidence: 2, reviews: 5) == 2)
        #expect(StudySchedule.days(confidence: 4, reviews: 3) == 14)
    }
}

@Suite("Salary split")
struct SalarySplitTests {
    @Test func dividesIncomeAndFillsEachBucket() throws {
        let s = try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{"SPLIT_ESSENTIALS":0.5,"SPLIT_EMERGENCY":0.1,"SPLIT_SAVINGS":0.2,"SPLIT_FAMILY":0.1,"SPLIT_LIFESTYLE":0.1},
         "lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],
         "expenses":[{"row":5,"date":"2026-10-03","category":"Groceries","qar":300},
                     {"row":6,"date":"2026-10-04","category":"Food & Dining","qar":120},
                     {"row":7,"date":"2026-10-05","category":"Gifts & Family","qar":50},
                     {"row":8,"date":"2026-09-30","category":"Groceries","qar":999}],
         "salary":[{"row":5,"month":"2026-10","label":"Oct 2026","salary":10000,"totalIncome":10000,"remittanceQar":800}],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "setAside":[{"id":"x","date":"2026-10-02","bucket":"Savings / SIP","amount":1500}]}
        """.utf8))
        let lines = Dictionary(uniqueKeysWithValues: SalarySplit.lines(s, month: "2026-10").map { ($0.bucket, $0) })
        #expect(lines[.essentials]?.planned == 5000)
        #expect(lines[.essentials]?.used == 300)          // September's groceries don't count
        #expect(lines[.lifestyle]?.used == 120)
        #expect(lines[.family]?.used == 850)               // sent home 800 + gifts 50
        #expect(lines[.savings]?.used == 1500)
        #expect(lines[.savings]?.left == 500)
        #expect(lines[.emergency]?.used == 0)
    }

    @Test func defaultsWhenTheSheetHasNoSplitYet() throws {
        let s = try JSONDecoder().decode(SheetSettings.self, from: Data("{}".utf8))
        #expect(SplitBucket.allCases.reduce(0) { $0 + s.share($1) } == 1)
    }
}

@Suite("Savings against the plan")
struct SavingsPlanTests {
    private func snapshot(spent: Double, putAside: Double) throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{"SPLIT_ESSENTIALS":0.5,"SPLIT_EMERGENCY":0.1,"SPLIT_SAVINGS":0.2,"SPLIT_FAMILY":0.1,"SPLIT_LIFESTYLE":0.1},
         "lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],"budget":{"headers":[],"rows":[]},"days":[],
         "expenses":[{"row":5,"date":"2026-10-03","category":"Groceries","qar":\(spent)}],
         "salary":[{"row":5,"month":"2026-10","label":"Oct 2026","totalIncome":10000,"netSavings":\(10000 - spent)}],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "setAside":[{"id":"x","date":"2026-10-02","bucket":"Savings / SIP","amount":\(putAside)}]}
        """.utf8))
    }

    @Test func spendingInsideTheBudgetDoesNotTouchSaved() throws {
        let s = try snapshot(spent: 4000, putAside: 3000)
        #expect(SalarySplit.putAside(s) == 3000)            // saved stays what you put aside
        #expect(SalarySplit.savingsShare(s.settings) == 0.3 || abs(SalarySplit.savingsShare(s.settings) - 0.3) < 0.0001)
        guard case .saved = SalarySplit.state(s, month: "2026-10") else { Issue.record("expected saved"); return }
    }

    @Test func showsWhatIsStillToPutAsideOrWhereItWentOver() throws {
        if case .toPutAside(let left) = SalarySplit.state(try snapshot(spent: 4000, putAside: 1000), month: "2026-10") {
            #expect(left == 2000)                           // 1,000 emergency + 1,000 SIP still to go
        } else { Issue.record("expected to put aside") }
        if case .overBudget(let over, let buckets) = SalarySplit.state(try snapshot(spent: 6000, putAside: 3000), month: "2026-10") {
            #expect(over == 1000)                           // essentials share is 5,000
            #expect(buckets == [.essentials])
        } else { Issue.record("expected over budget") }
    }
}

@Suite("Monthly bills in the split")
struct MonthlyBillSplitTests {
    @Test func everyMonthlyBillCountsAsEssentials() throws {
        let s = try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],"budget":{"headers":[],"rows":[]},"days":[],
         "expenses":[{"row":5,"date":"2026-10-01","description":"Gym","category":"Entertainment","qar":200,"notes":"Recurring"},
                     {"row":6,"date":"2026-10-01","description":"Netflix","category":"Entertainment","qar":40,"notes":"family plan · Recurring"},
                     {"row":7,"date":"2026-10-04","description":"Cinema","category":"Entertainment","qar":60}],
         "salary":[{"row":5,"month":"2026-10","label":"Oct 2026","totalIncome":10000}],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """.utf8))
        let lines = Dictionary(uniqueKeysWithValues: SalarySplit.lines(s, month: "2026-10").map { ($0.bucket, $0.used) })
        #expect(lines[.essentials] == 240)   // both bills, though they're Entertainment
        #expect(lines[.lifestyle] == 60)     // only the one-off cinema
    }
}

@Suite("Savings goals")
struct SavingsGoalTests {
    private func snapshot() throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],"budget":{"headers":[],"rows":[]},"days":[],
         "expenses":[],"salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "savingsGoals":[{"id":"trip","name":"Kerala trip","target":3000,"due":"2027-03-15","done":"N"},
                         {"id":"phone","name":"Phone","target":2000,"done":"Y","doneOn":"2026-09-01"}],
         "setAside":[{"id":"a","date":"2026-09-02","bucket":"Savings / SIP","amount":500,"goalId":"trip"},
                     {"id":"b","date":"2026-10-02","bucket":"Savings / SIP","amount":700,"goalId":"trip"},
                     {"id":"c","date":"2026-10-03","bucket":"Savings / SIP","amount":900}]}
        """.utf8))
    }

    @Test func progressIsTheMoneyPutAsideForIt() throws {
        let s = try snapshot()
        let trip = try #require(s.goals.first { $0.id == "trip" })
        #expect(s.goals.last?.done == true)
        let p = s.progress(of: trip, today: try #require(SheetDate.date("2026-10-07")))
        #expect(p.saved == 1200)                 // the unlinked 900 doesn't count
        #expect(p.left == 1800)
        #expect(p.monthsLeft == 6)               // Oct … Mar
        #expect(p.perMonth == 300)
    }

    @Test func deletingAGoalKeepsTheMoney() throws {
        var s = try snapshot()
        let write = PendingWrite(id: "w", action: "deleteSavingsGoal", payload: ["id": "trip"], summary: "", createdAt: .now, notBefore: nil, tempRow: nil)
        SheetStore.applyLocally(write, to: &s)
        #expect(s.goals.map(\.id) == ["phone"])
        #expect(SalarySplit.putAside(s) == 2100)  // every entry stays
        #expect((s.setAside ?? []).allSatisfy { $0.goalId == nil })
    }
}

@Suite("Budget alerts")
struct BudgetAlertTests {
    private func snapshot(groceries: Double, cinema: Double) throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{"SPLIT_ESSENTIALS":0.5,"SPLIT_EMERGENCY":0.1,"SPLIT_SAVINGS":0.2,"SPLIT_FAMILY":0.1,"SPLIT_LIFESTYLE":0.1},
         "lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],"budget":{"headers":[],"rows":[]},"days":[],
         "expenses":[{"row":5,"date":"2026-10-03","category":"Groceries","qar":\(groceries)},
                     {"row":6,"date":"2026-10-04","category":"Entertainment","qar":\(cinema)}],
         "salary":[{"row":5,"month":"2026-10","label":"Oct 2026","totalIncome":10000}],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """.utf8))
    }

    @Test func alertsAtEightyPercentAndWhenOver() throws {
        let alerts = BudgetAlerts.due(try snapshot(groceries: 4000, cinema: 1200), month: "2026-10", sent: [])
        #expect(alerts.map(\.bucket) == [.essentials, .lifestyle])
        #expect(alerts.map(\.level) == [.nearly, .over])        // 4,000 of 5,000; 1,200 of 1,000
        #expect(BudgetAlerts.due(try snapshot(groceries: 3900, cinema: 100), month: "2026-10", sent: []).isEmpty)
    }

    @Test func eachAlertOnlyOncePerMonth() throws {
        let s = try snapshot(groceries: 4500, cinema: 0)
        #expect(BudgetAlerts.due(s, month: "2026-10", sent: ["2026-10|Essentials|80"]).isEmpty)
        // Going over afterwards still sends the "over" one.
        let over = BudgetAlerts.due(try snapshot(groceries: 5200, cinema: 0), month: "2026-10", sent: ["2026-10|Essentials|80"])
        #expect(over.map(\.key) == ["2026-10|Essentials|100"])
        // No income entered: nothing to measure against.
        #expect(BudgetAlerts.due(s, month: "2026-11", sent: []).isEmpty)
    }
}

@Suite("Planner and wardrobe")
struct PlannerTests {
    private func planner() throws -> PlannerData {
        try JSONDecoder().decode(PlannerData.self, from: Data("""
        {"plans":[{"id":"a","date":"2026-10-10","title":"Grocery run"},
                  {"id":"b","date":"2026-10-10","time":"18:00","title":"Cinema","done":true},
                  {"id":"c","date":"2026-10-10","time":"09:30","title":"Gym"}],
         "outfits":[{"id":"outfit-2026-10-05","date":"2026-10-05","items":["shirt","jeans"],"names":"Shirt + Jeans"},
                    {"id":"outfit-2026-10-08","date":"2026-10-08","items":["shirt","shoes"]},
                    {"id":"outfit-2026-10-11","date":"2026-10-11","items":"kurta,gone","names":"Kurta + Old blazer"}],
         "wardrobe":[{"id":"shirt","name":"Oxford shirt","type":"Top","colour":"Blue"},
                     {"id":"jeans","name":"Jeans","type":"Bottom"},
                     {"id":"shoes","name":"Sneakers","type":"Shoes"},
                     {"id":"kurta","name":"Kurta","type":"Ethnic"}]}
        """.utf8))
    }

    @Test func plansRunInTimeOrderThenAnyTime() throws {
        let p = try planner()
        #expect(p.plans(on: "2026-10-10").map(\.id) == ["c", "b", "a"])
        #expect(p.plans.first { $0.id == "b" }?.done == true)
        #expect(PlanTime.text(try #require(PlanTime.date("18:05"))) == "18:05")
    }

    @Test func wornCountsOnlyUpToTodayAndFlagsRepeats() throws {
        let p = try planner()
        #expect(p.timesWorn("shirt", today: "2026-10-07") == 1)        // the 8th is still a plan
        #expect(p.lastWorn("shirt", before: "2026-10-08") == "2026-10-05")
        #expect(p.nextPlanned("kurta", today: "2026-10-07") == "2026-10-11")
        let thursday = try #require(p.outfit(on: "2026-10-08"))
        let repeats = p.recentRepeats(in: thursday)
        #expect(repeats.map(\.0.id) == ["shirt"])                       // shoes don't count as a repeat
        #expect(repeats.first?.1 == 3)
        // A piece since removed from the wardrobe keeps its name from the sheet.
        #expect(p.pieces(of: try #require(p.outfit(on: "2026-10-11"))) == ["Kurta", "Old blazer"])
        #expect(p.pieces(of: thursday) == ["Blue Oxford shirt", "Sneakers"])
    }
}

@Suite("Life goals")
struct LifeGoalTests {
    private func data() throws -> LifeGoalData {
        try JSONDecoder().decode(LifeGoalData.self, from: Data("""
        {"goals":[{"id":"L","title":"Senior iOS developer","horizon":"Long term","area":"Career","status":"Active"},
                  {"id":"A","title":"Ship the app","horizon":"Short term","area":"Career","parentId":"L","due":"2026-10-30","status":"Active"},
                  {"id":"B","title":"SwiftUI course","horizon":"Short term","area":"Learning","parentId":"L","status":"Reached"},
                  {"id":"C","title":"Old idea","horizon":"Short term","parentId":"L","status":"Dropped"},
                  {"id":"M","title":"Read 6 books","horizon":"Short term","area":"Learning","progress":0.5,"status":"Active","due":"2026-12-01"}],
         "steps":[{"id":"1","goalId":"A","step":"Widgets","done":true,"order":1},
                  {"id":"2","goalId":"A","step":"Screenshots","done":"N","order":3},
                  {"id":"3","goalId":"A","step":"Animation","done":"Y","order":2},
                  {"id":"4","goalId":"A","step":"Upload","order":4},
                  {"id":"5","goalId":"L","step":"Lead a feature","order":1}]}
        """.utf8))
    }

    @Test func progressComesFromStepsAndShortTermGoals() throws {
        let d = try data()
        let ship = try #require(d.goal("A"))
        #expect(d.progress(of: ship) == 0.5)                                   // 2 of 4 steps
        #expect(d.steps(of: "A").map(\.id) == ["1", "3", "2", "4"])           // in their order
        #expect(d.nextStep(of: ship)?.step == "Screenshots")
        // Long term: its own step (0) + Ship (0.5) + the reached course (1); the dropped one doesn't count.
        #expect(abs(d.progress(of: try #require(d.goal("L"))) - 0.5) < 0.0001)
        #expect(d.progress(of: try #require(d.goal("M"))) == 0.5)            // typed, no steps
        #expect(d.active(.short).map(\.id) == ["A", "M"])                     // soonest date first
    }

    @Test func staleAndDueText() throws {
        var goal = LifeGoal(title: "x", created: "2026-09-01")
        goal.updated = "2026-09-10"
        var d = LifeGoalData(); d.goals = [goal]
        #expect(d.isStale(goal, today: "2026-09-30"))
        #expect(!d.isStale(goal, today: "2026-09-20"))
        let today = try #require(SheetDate.date("2026-10-08"))
        #expect(GoalTime.due("2026-10-05", today: today) == "3 days overdue")
        #expect(GoalTime.suggestedHorizon(for: try #require(SheetDate.date("2026-12-01")), today: today) == .short)
        #expect(GoalTime.suggestedHorizon(for: try #require(SheetDate.date("2027-10-01")), today: today) == .long)
    }

    @Test func deletingALongTermGoalKeepsItsShortTermGoals() throws {
        var s = try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],"budget":{"headers":[],"rows":[]},"days":[],
         "expenses":[],"salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """.utf8))
        s.lifeGoals = try data()
        SheetStore.applyLocally(PendingWrite(id: "w", action: "deleteLifeGoal", payload: ["id": "L"], summary: "", createdAt: .now, notBefore: nil, tempRow: nil), to: &s)
        #expect(s.lifeGoalData.goal("L") == nil)
        #expect(s.lifeGoalData.steps.allSatisfy { $0.goalId != "L" })
        #expect(s.lifeGoalData.goal("A")?.parentId == nil)                    // kept, unlinked
        #expect(SheetQueueLabels.readable("deleteGoalStep") == "Deleted goal step")
        #expect(SheetQueueLabels.readable("saveShoppingItem") == "Shopping item")
    }
}

@Suite("Steps from Apple Health")
struct HealthStepsTests {
    private func snapshot() throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],"budget":{"headers":[],"rows":[]},
         "days":[{"date":"2026-10-06","steps":9000,"score":0.71},{"date":"2026-10-07","steps":3000},{"date":"2026-10-08"}],
         "expenses":[],"salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """.utf8))
    }

    @Test func onlyHigherCountsInsideThePeriodAreSent() throws {
        let counts = ["2026-10-05": 4000, "2026-10-06": 7500, "2026-10-07": 6200, "2026-10-08": 1800].mapValues { HealthDay(steps: $0) }
        let changes = HealthSteps.changes(counts, against: try snapshot())
        #expect(changes == ["2026-10-07": HealthDay(steps: 6200), "2026-10-08": HealthDay(steps: 1800)])   // 6th: typed 9,000 kept; 5th: outside the period
    }

    @Test func stepsAloneDoNotLogADay() throws {
        var s = try snapshot()
        let write = PendingWrite(id: "w", action: "saveHealth", payload: ["days": [["date": "2026-10-08", "steps": 5400], ["date": "2026-10-06", "steps": 100]]],
                                 summary: "", createdAt: .now, notBefore: nil, tempRow: nil)
        SheetStore.applyLocally(write, to: &s)
        #expect(s.days[2].steps == 5400)
        #expect(!s.days[2].isLogged)            // still "not logged" — the evening reminder still fires
        #expect(s.days[0].steps == 9000)        // never lowered
    }
}

@Suite("Health, briefing, renewals, loans, reviews")
struct LifeAdminTests {
    private func snapshot(_ extra: String = "") throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{"STEPS_TARGET":8000,"DAILY_LIMIT":20,"MONTHLY_LIMIT":600,"WATER_TARGET":3,"SLEEP_TARGET":7},
         "lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],"budget":{"headers":[],"rows":[]},
         "days":[{"date":"2026-10-05","steps":9000,"office":"Y","score":0.71},{"date":"2026-10-06","steps":8100,"sleep":7.5},
                 {"date":"2026-10-07","steps":3000},{"date":"2026-10-08"}],
         "expenses":[{"row":5,"date":"2026-10-06","category":"Groceries","qar":45},{"row":6,"date":"2026-10-07","category":"Food & Dining","qar":20}],
         "recurring":[{"row":4,"description":"Rent","category":"Rent & Utilities","amount":2500,"day":31,"active":true},
                      {"row":5,"description":"Gym","amount":200,"day":8,"active":true},{"row":6,"description":"Old","amount":50,"day":8,"active":false}],
         "salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}\(extra)}
        """.utf8))
    }

    @Test func healthFillsOnlyWhatIsMissingAndNeverLogsADay() throws {
        var s = try snapshot()
        let health = ["2026-10-06": HealthDay(steps: 7000, sleep: 6.2, weight: 61.3, workout: true),
                      "2026-10-07": HealthDay(steps: 6400, sleep: 7.8, weight: nil, workout: false)]
        let changes = HealthSteps.changes(health, against: s)
        #expect(changes["2026-10-06"] == HealthDay(steps: nil, sleep: nil, weight: 61.3, workout: true))   // typed sleep kept; steps not lower
        #expect(changes["2026-10-07"] == HealthDay(steps: 6400, sleep: 7.8, weight: nil, workout: false))
        let write = PendingWrite(id: "w", action: "saveHealth", payload: ["days": [["date": "2026-10-07", "steps": 6400, "sleep": 7.8, "workout": true]]],
                                 summary: "", createdAt: .now, notBefore: nil, tempRow: nil)
        SheetStore.applyLocally(write, to: &s)
        #expect(s.days[2].sleep == 7.8 && s.days[2].workout == "Y" && s.days[2].steps == 6400)
        #expect(!s.days[2].isLogged)                          // sleep + workout from the phone don't log the day
    }

    @Test func stepStreakAndWeek() throws {
        let s = try snapshot()
        #expect(s.stepStreak(today: "2026-10-07") == 2)       // 5th and 6th at 8,000+, today still open
        #expect(s.stepStreak(todaySteps: 8200, today: "2026-10-07") == 3)
        #expect(s.stepsWeek(today: "2026-10-07").suffix(3) == [9000, 8100, 3000])
    }

    @Test func billsOnTheirDayIncludingMonthEnd() throws {
        let s = try snapshot()
        #expect(Bills.due(s, on: try #require(SheetDate.date("2026-10-08"))).map(\.description) == ["Gym"])   // inactive one skipped
        #expect(Bills.due(s, on: try #require(SheetDate.date("2026-11-30"))).map(\.description) == ["Rent"])  // the 31st → 30 Nov
        #expect(Bills.due(s, on: try #require(SheetDate.date("2026-10-30"))).isEmpty)
    }

    @Test func briefingSaysWhatMatters() throws {
        var s = try snapshot()
        s.renewals = [Renewal(item: "QID", kind: .qid, expires: "2026-10-12")]
        var p = PlannerData()
        p.plans = [DayPlan(date: "2026-10-08", time: "19:30", title: "Dinner with Arun")]
        s.planner = p
        let b = Briefing.text(s, on: try #require(SheetDate.date("2026-10-08")), includeLive: true)
        #expect(b.body.contains("Dinner with Arun"))
        #expect(b.body.contains("Due today: Gym"))
        #expect(b.body.contains("QID in 4 days"))
        #expect(b.body.contains("3,000 steps yesterday"))
        #expect(b.body.contains("535.00 QAR left") || b.body.contains("535.00"))   // 600 − 65
    }

    @Test func renewalsAndLoans() throws {
        let r = Renewal(item: "Passport", kind: .passport, expires: "2026-10-18")
        #expect(r.daysLeft(today: try #require(SheetDate.date("2026-10-08"))) == 10)
        #expect(r.suggestedNext == "2036-10-18")
        #expect(Renewal.days("7, 30,1,x") == [30, 7, 1])
        let loan = Loan(name: "Car", emi: 1000, firstPayment: "2026-01-15", months: 12)
        let today = try #require(SheetDate.date("2026-10-08"))
        #expect(loan.paidCount(today: today) == 9)            // Jan … Sep
        #expect(loan.remaining(today: today) == 3000)
        #expect(loan.lastPayment == "2026-12-15")
        #expect(loan.nextPayment(today: today) == "2026-10-15")
        var typed = loan; typed.paid = 11
        #expect(typed.left(today: today) == 1)
    }

    @Test func weekInReview() throws {
        let s = try snapshot()
        let r = WeeklyReview(s, weekOf: try #require(SheetDate.date("2026-10-05")))
        #expect(r.logged == 1)
        #expect(r.spent == 65)
        #expect(r.budget == 140)
        #expect(r.steps == 20100 && r.stepGoalDays == 2)
        #expect(r.summary.contains("75.00 under budget"))
    }
}

@Suite("Money reaches Finance")
struct MoneyLinkTests {
    @Test func loanBillFlagAndRenewalCategories() throws {
        let loan = try JSONDecoder().decode(Loan.self, from: Data(#"{"id":"l","name":"Car","emi":1450,"firstPayment":"2026-01-05","months":48,"bill":true}"#.utf8))
        #expect(loan.bill)
        let old = try JSONDecoder().decode(Loan.self, from: Data(#"{"id":"l","name":"Car","emi":1450,"firstPayment":"2026-01-05","months":48}"#.utf8))
        #expect(!old.bill)                                        // a loan without a bill on the sheet says so
        #expect(Loan(name: "New").bill)                           // new loans post to Finance unless turned off
        #expect(Renewal.Kind.car.category == "Transport")
        #expect(Renewal.Kind.rent.category == "Rent & Utilities")
        #expect(Renewal.Kind.qid.category == "Other")
    }
}

@Suite("Your habits")
struct HabitCustomizeTests {
    private func snapshot(_ settings: String) throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{"WATER_TARGET":3,"SLEEP_TARGET":7,\(settings)},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[{"date":"2026-10-05","office":"Y","breakfast":"N","workout":"Y","water":3,"sleep":6,"score":0.43}],
         "expenses":[],"salary":[],"weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """.utf8))
    }

    @Test func scoreCountsOnlyWhatIsOn() throws {
        var s = try snapshot(#""HABIT1_NAME":"Read 20 pages","HABIT1_ICON":"book","HABIT3_ON":"N","HABIT4_ON":"N","SLEEP_ON":"N","SCORE_OUT_OF":4"#)
        #expect(Habit.office.name(s.settings) == "Read 20 pages")
        #expect(Habit.office.symbol(s.settings) == "book.fill")
        #expect(Habit.active(s.settings) == [.office, .breakfast, .workout])
        #expect(s.settings.scoreDenominator == 4)                  // 3 habits + water
        SheetStore.rescore(&s.days[0], settings: s.settings)
        #expect(s.days[0].habitsMet == 3)                           // office, workout, water
        #expect(s.days[0].score == 0.75)
    }

    @Test func savingHabitsRescoresAndHealthOnlyTicksAWorkoutHabit() throws {
        var s = try snapshot(#""SCORE_OUT_OF":7"#)
        #expect(Habit.healthFillsWorkout(s.settings))
        let write = PendingWrite(id: "w", action: "saveHabits", payload: [
            "habits": (0..<5).map { i in ["name": i == 4 ? "Pray Fajr" : "", "on": i != 1, "icon": "star"] as [String: Any] },
            "trackers": ["WATER_ON": true, "SLEEP_ON": false]], summary: "", createdAt: .now, notBefore: nil, tempRow: nil)
        SheetStore.applyLocally(write, to: &s)
        #expect(s.settings.habit5Name == "Pray Fajr")
        #expect(!Habit.healthFillsWorkout(s.settings))              // the 5th habit isn't a workout any more
        #expect(s.settings.scoreDenominator == 5)                   // 4 habits + water
        #expect(s.days[0].score == 0.6)                             // office, "Pray Fajr" (was workout Y), water
        let changes = HealthSteps.changes(["2026-10-05": HealthDay(steps: 9000, sleep: 7, weight: 60, workout: true)], against: s)
        #expect(changes["2026-10-05"]?.workout == false && changes["2026-10-05"]?.sleep == nil)   // sleep off; not a workout habit
    }

    @Test func yourOwnHabitsCountAndHealthInputsDont() throws {
        var s = try snapshot(#""SCORE_OUT_OF":7"#)
        func apply(_ action: String, _ payload: [String: Any]) {
            SheetStore.applyLocally(PendingWrite(id: UUID().uuidString, action: action, payload: payload, summary: "", createdAt: .now, notBefore: nil, tempRow: nil), to: &s)
        }
        apply("saveInput", ["id": "in-read", "section": "Habit", "name": "Read 20 pages", "icon": "book", "on": true, "order": 1])
        apply("saveInput", ["id": "in-tea", "section": "Health", "name": "Tea", "unit": "cups", "target": 2, "step": 1, "on": true, "order": 2])
        #expect(s.settings.scoreOutOf == 8)                          // 5 + water + sleep + Read
        apply("saveInputValues", ["date": "2026-10-05", "values": ["in-read": "Y", "in-tea": "3"]])
        #expect(s.inputData.values(on: "2026-10-05") == ["in-read": "Y", "in-tea": "3"])
        #expect(s.days[0].habitsMet == 4)                            // office, workout, water, Read
        #expect(s.days[0].score == 0.5)
        apply("saveInput", ["id": "in-read", "section": "Habit", "name": "Read 20 pages", "on": false, "order": 1])
        #expect(s.settings.scoreOutOf == 7)
        #expect(s.days[0].habitsMet == 3)
        apply("saveInputValues", ["date": "2026-10-05", "values": ["in-tea": ""]])
        #expect(s.inputData.values(on: "2026-10-05") == ["in-read": "Y"])
        apply("deleteInput", ["id": "in-read"])
        #expect(s.inputData.inputs.map(\.id) == ["in-tea"] && s.inputData.log.isEmpty)
    }

    @Test func aDayWithOnlyYourInputsIsLogged() throws {
        var s = try snapshot(#""SCORE_OUT_OF":8"#)
        s.days[0] = SheetDay(date: "2026-10-06")
        s.myInputs = MyInputData(inputs: [MyInput(id: "a", section: .habit, name: "Pray", on: true)],
                                 log: [MyInputLog(date: "2026-10-06", id: "a", value: "Y")])
        SheetStore.rescore(&s.days[0], settings: s.settings, inputs: s.myInputs)
        #expect(s.days[0].habitsMet == 1 && s.days[0].score == 0.125)
    }
}

struct UpiPayTests {
    @Test func readsAShopQRAndABareUpiId() throws {
        let scan = try #require(UpiScan.parse("upi://pay?pa=cafe.coffee@okicici&pn=Blue%20Tokai&am=180.50&tn=Latte&mc=5814&cu=INR"))
        #expect(scan.payee.vpa == "cafe.coffee@okicici")
        #expect(scan.payee.name == "Blue Tokai")
        #expect(scan.amount == 180.5)
        #expect(scan.note == "Latte")
        #expect(scan.payee.mcc == "5814")
        #expect(UpiScan.parse("ramesh99@ybl")?.payee.vpa == "ramesh99@ybl")
        #expect(UpiScan.parse("https://example.com") == nil)
        #expect(UpiScan.parse("upi://pay?pn=NoId") == nil)
        #expect(UpiScan.parse("upi://pay?pa=shop@paytm&am=0")?.amount == nil)   // "any amount" QR
    }

    @Test func buildsEachAppsPaymentLink() throws {
        let request = UpiRequest(payee: UpiPayee(vpa: "shop@okaxis", name: "Shop & Co", mcc: "5411"), amount: 250, note: "Veg", reference: "MPT1")
        let phonePe = try #require(UpiApp.phonePe.url(request)?.absoluteString)
        #expect(phonePe.hasPrefix("phonepe://pay?pa=shop@okaxis&pn=Shop%20%26%20Co&am=250.00&cu=INR&tr=MPT1&tn=Veg&mc=5411"))
        #expect(UpiApp.googlePay.url(request)?.absoluteString.hasPrefix("tez://upi/pay?") == true)
        #expect(UpiApp.anyApp.url(request)?.scheme == "upi")
        #expect(PayView.category(forMCC: "5411", among: ["Groceries", "Other"]) == "Groceries")
        #expect(PayView.category(forMCC: "9999", among: ["Groceries"]) == nil)
    }
}
