//
//  SheetModels.swift
//  MyPersonalTracker
//
//  The "Life & Finance Command Center" Google Sheet as the app sees it: one
//  snapshot returned by the Apps Script API (10_Api.gs). Every calculated
//  figure here — daily score, streak, INR, savings, summaries — is the sheet's
//  own formula result; the app only reads it.
//

import Foundation

struct SheetSnapshot: Codable {
    var generatedAt: String?
    var settings: SheetSettings
    var lists: Lists
    var dashboard: [DashboardCard]
    /// Habit success rates, spend by category, at a glance, money snapshot.
    var dashboardTables: [SheetTable]
    /// Finance sheet's day-by-day budget control.
    var budget: SheetTable
    var days: [SheetDay]
    var expenses: [SheetExpense]
    var salary: [SalaryMonth]
    var weekly: SheetTable
    var monthly: SheetTable
    /// Monthly bills from the Recurring tab (absent on older sheets).
    var recurring: [RecurringExpense]?
    /// Labels of archived periods, e.g. "15 Aug 26–30 Sep 26".
    var archives: [String]?
    /// Food Log, Meal Plan and My Foods (absent on older sheets).
    var food: FoodData?
    /// Money with friends & family (absent on older sheets).
    var people: [PeopleEntry]?
    /// Subjects, concepts, sessions and goals (absent on older sheets).
    var study: StudyData?
    /// The grocery list and shopping trips (absent on older sheets).
    var shopping: ShoppingData?
    /// Money put into the emergency fund or savings / SIP (absent on older sheets).
    var setAside: [SetAsideEntry]?
    /// Named savings goals (absent on older sheets).
    var savingsGoals: [SavingsGoal]?
    /// Week planner, outfits and wardrobe (absent on older sheets).
    var planner: PlannerData?
    /// Short-term and long-term goals with their steps (absent on older sheets).
    var lifeGoals: LifeGoalData?
    /// Documents and contracts that expire (absent on older sheets).
    var renewals: [Renewal]?
    /// Loans and EMIs (absent on older sheets).
    var loans: [Loan]?
    /// Habits and health inputs you added, and what you logged this period (absent on older sheets).
    var myInputs: MyInputData?
    /// What Mom and Dad asked you to do (absent on older sheets).
    var parents: [ParentAsk]?
    /// Their medicines, counted down to the next refill (absent on older sheets).
    var parentMedicines: [ParentMedicine]?
    /// Their birthdays, anniversaries and remembrance days (absent on older sheets).
    var parentDates: [ParentDate]?
    /// What to bring on the next trip home, and when it is (absent on older sheets).
    var takeHome: TakeHomeData?
    /// Readings Mom and Dad tell you: blood pressure, sugar… (absent on older sheets).
    var parentHealth: [ParentReading]?

    struct Lists: Codable {
        var categories: [String]
        var methods: [String]
    }
}

/// The Settings sheet's core parameters (keys match `CFG` in 00_Config.gs).
struct SheetSettings: Codable {
    var startDate: String?
    var endDate: String?
    var rate: Double?
    var dailyLimit: Double?
    var monthlyLimit: Double?
    var waterTarget: Double?
    var sleepTarget: Double?
    var stepsTarget: Double?
    var screenLimit: Double?
    var salary: Double?
    /// Fraction, e.g. 0.95.
    var savingsGoal: Double?
    /// Fraction, e.g. 0.85.
    var greatDay: Double?
    /// What every amount in the sheet is kept in (Settings ▸ Currency), e.g. "QAR".
    var mainCurrency: String
    /// What money sent home is shown in; `rate` is 1 main = rate home.
    var homeCurrency: String
    /// Daily nutrition targets and the monthly food budget (Settings ▸ Nutrition).
    var calorieTarget: Double?
    var proteinTarget: Double?
    var carbTarget: Double?
    var fatTarget: Double?
    var foodBudget: Double?
    /// The pair the exchange rate was set for, "QAR/INR". A different pair means the rate is stale.
    var rateFor: String?
    /// Study targets (Settings ▸ Study): minutes a day, hours a week.
    var studyDailyTarget: Double?
    var studyWeeklyTarget: Double?
    /// Body & goal (Settings ▸ Body): what the calorie and protein targets are worked out from.
    var sex: String?
    var birthYear: Double?
    var heightCm: Double?
    var bodyWeight: Double?
    var activity: String?
    var bodyGoal: String?
    var proteinPerKg: Double?
    /// The Nutrition targets follow the newest logged weight.
    var followWeight: Bool?
    /// Salary split (Settings ▸ Salary Split): each bucket's share as a fraction.
    var splitEssentials: Double?
    var splitEmergency: Double?
    var splitSavings: Double?
    var splitFamily: Double?
    var splitLifestyle: Double?
    var emergencyGoal: Double?
    /// "Y": the monthly spend limit is this month's income × (Essentials + Lifestyle); "N": the custom limit.
    var limitFromSplit: String?
    var customLimit: Double?
    /// Your five habits (HABITS block on Settings): name, "Y"/"N", icon key.
    var habit1Name: String?, habit2Name: String?, habit3Name: String?, habit4Name: String?, habit5Name: String?
    var habit1On: String?, habit2On: String?, habit3On: String?, habit4On: String?, habit5On: String?
    var habit1Icon: String?, habit2Icon: String?, habit3Icon: String?, habit4Icon: String?, habit5Icon: String?
    /// Trackers you can turn off ("N").
    var waterOn: String?, sleepOn: String?, stepsOn: String?, weightOn: String?, screenOn: String?
    /// What the Daily Score is out of: habits on + water and sleep, if on.
    var scoreOutOf: Double?
    var limitFollowsSplit: Bool { limitFromSplit != "N" }
    /// The shares by Settings key.
    var splitShares: [String: Double] {
        ["SPLIT_ESSENTIALS": splitEssentials, "SPLIT_EMERGENCY": splitEmergency, "SPLIT_SAVINGS": splitSavings,
         "SPLIT_FAMILY": splitFamily, "SPLIT_LIFESTYLE": splitLifestyle].compactMapValues { $0 }
    }
    var rateIsCurrent: Bool { rateFor == nil || rateFor == "\(mainCurrency)/\(homeCurrency)" }

    enum CodingKeys: String, CodingKey {
        case mainCurrency = "MAIN_CURRENCY", homeCurrency = "HOME_CURRENCY"
        case calorieTarget = "CALORIE_TARGET", proteinTarget = "PROTEIN_TARGET", carbTarget = "CARB_TARGET"
        case fatTarget = "FAT_TARGET", foodBudget = "FOOD_BUDGET", rateFor = "RATE_FOR"
        case studyDailyTarget = "STUDY_DAILY_TARGET", studyWeeklyTarget = "STUDY_WEEKLY_TARGET"
        case sex = "SEX", birthYear = "BIRTH_YEAR", heightCm = "HEIGHT_CM", bodyWeight = "BODY_WEIGHT", activity = "ACTIVITY"
        case bodyGoal = "BODY_GOAL", proteinPerKg = "PROTEIN_PER_KG", followWeight = "FOLLOW_WEIGHT"
        case splitEssentials = "SPLIT_ESSENTIALS", splitEmergency = "SPLIT_EMERGENCY", splitSavings = "SPLIT_SAVINGS"
        case splitFamily = "SPLIT_FAMILY", splitLifestyle = "SPLIT_LIFESTYLE", emergencyGoal = "EMERGENCY_GOAL"
        case limitFromSplit = "LIMIT_FROM_SPLIT", customLimit = "CUSTOM_LIMIT"
        case startDate = "START_DATE", endDate = "END_DATE", rate = "RATE"
        case dailyLimit = "DAILY_LIMIT", monthlyLimit = "MONTHLY_LIMIT"
        case waterTarget = "WATER_TARGET", sleepTarget = "SLEEP_TARGET"
        case stepsTarget = "STEPS_TARGET", screenLimit = "SCREEN_LIMIT"
        case salary = "SALARY", savingsGoal = "SAVINGS_GOAL", greatDay = "GREAT_DAY"
        case habit1Name = "HABIT1_NAME", habit2Name = "HABIT2_NAME", habit3Name = "HABIT3_NAME", habit4Name = "HABIT4_NAME", habit5Name = "HABIT5_NAME"
        case habit1On = "HABIT1_ON", habit2On = "HABIT2_ON", habit3On = "HABIT3_ON", habit4On = "HABIT4_ON", habit5On = "HABIT5_ON"
        case habit1Icon = "HABIT1_ICON", habit2Icon = "HABIT2_ICON", habit3Icon = "HABIT3_ICON", habit4Icon = "HABIT4_ICON", habit5Icon = "HABIT5_ICON"
        case waterOn = "WATER_ON", sleepOn = "SLEEP_ON", stepsOn = "STEPS_ON", weightOn = "WEIGHT_ON", screenOn = "SCREEN_ON"
        case scoreOutOf = "SCORE_OUT_OF"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        startDate = c.flexString(.startDate)
        endDate = c.flexString(.endDate)
        rate = c.flexDouble(.rate)
        dailyLimit = c.flexDouble(.dailyLimit)
        monthlyLimit = c.flexDouble(.monthlyLimit)
        waterTarget = c.flexDouble(.waterTarget)
        sleepTarget = c.flexDouble(.sleepTarget)
        stepsTarget = c.flexDouble(.stepsTarget)
        screenLimit = c.flexDouble(.screenLimit)
        salary = c.flexDouble(.salary)
        savingsGoal = c.flexDouble(.savingsGoal)
        greatDay = c.flexDouble(.greatDay)
        // Sheets from before the Currency setting existed are in QAR and INR.
        mainCurrency = c.flexString(.mainCurrency)?.uppercased() ?? "QAR"
        homeCurrency = c.flexString(.homeCurrency)?.uppercased() ?? "INR"
        calorieTarget = c.flexDouble(.calorieTarget) ?? 2200
        proteinTarget = c.flexDouble(.proteinTarget) ?? 120
        carbTarget = c.flexDouble(.carbTarget) ?? 250
        fatTarget = c.flexDouble(.fatTarget) ?? 70
        foodBudget = c.flexDouble(.foodBudget)
        rateFor = c.flexString(.rateFor)?.uppercased()
        studyDailyTarget = c.flexDouble(.studyDailyTarget) ?? 60
        studyWeeklyTarget = c.flexDouble(.studyWeeklyTarget) ?? 7
        sex = c.flexString(.sex)
        birthYear = c.flexDouble(.birthYear)
        heightCm = c.flexDouble(.heightCm)
        bodyWeight = c.flexDouble(.bodyWeight)
        activity = c.flexString(.activity)
        bodyGoal = c.flexString(.bodyGoal)
        proteinPerKg = c.flexDouble(.proteinPerKg)
        followWeight = c.flexString(.followWeight).map { $0 == "Y" }
        splitEssentials = c.flexDouble(.splitEssentials)
        splitEmergency = c.flexDouble(.splitEmergency)
        splitSavings = c.flexDouble(.splitSavings)
        splitFamily = c.flexDouble(.splitFamily)
        splitLifestyle = c.flexDouble(.splitLifestyle)
        emergencyGoal = c.flexDouble(.emergencyGoal)
        limitFromSplit = c.flexString(.limitFromSplit)?.uppercased()
        customLimit = c.flexDouble(.customLimit)
        habit1Name = c.flexString(.habit1Name); habit2Name = c.flexString(.habit2Name); habit3Name = c.flexString(.habit3Name)
        habit4Name = c.flexString(.habit4Name); habit5Name = c.flexString(.habit5Name)
        habit1On = c.flexString(.habit1On); habit2On = c.flexString(.habit2On); habit3On = c.flexString(.habit3On)
        habit4On = c.flexString(.habit4On); habit5On = c.flexString(.habit5On)
        habit1Icon = c.flexString(.habit1Icon); habit2Icon = c.flexString(.habit2Icon); habit3Icon = c.flexString(.habit3Icon)
        habit4Icon = c.flexString(.habit4Icon); habit5Icon = c.flexString(.habit5Icon)
        waterOn = c.flexString(.waterOn); sleepOn = c.flexString(.sleepOn); stepsOn = c.flexString(.stepsOn)
        weightOn = c.flexString(.weightOn); screenOn = c.flexString(.screenOn)
        scoreOutOf = c.flexDouble(.scoreOutOf)
    }
}

/// A KPI card on the Dashboard sheet, as the sheet formats it.
struct DashboardCard: Codable, Identifiable, Hashable {
    var label: String
    var value: String
    var caption: String
    var id: String { label }
}

/// The five Y/N habits, in Daily Tracker column order.
enum Habit: String, CaseIterable, Identifiable {
    case office, breakfast, lunch, protein, workout
    var id: Self { self }

    var title: String {
        switch self {
        case .office: "In Office by 9:00 AM"
        case .breakfast: "Breakfast Done"
        case .lunch: "Lunch Taken"
        case .protein: "Protein in Lunch"
        case .workout: "Workout Done"
        }
    }

    var short: String {
        switch self {
        case .office: "Office"
        case .breakfast: "Breakfast"
        case .lunch: "Lunch"
        case .protein: "Protein"
        case .workout: "Workout"
        }
    }

    var symbol: String {
        switch self {
        case .office: "building.2"
        case .breakfast: "sunrise"
        case .lunch: "fork.knife"
        case .protein: "bolt.heart"
        case .workout: "figure.run"
        }
    }
}

/// One row of the Daily Tracker. Inputs are optional (unlogged); `habitsMet`,
/// `score`, `status` and `streak` are the sheet's formulas.
struct SheetDay: Codable, Identifiable, Hashable {
    var date: String
    var office: String?
    var breakfast: String?
    var lunch: String?
    var protein: String?
    var workout: String?
    var water: Double?
    var sleep: Double?
    var steps: Double?
    var weight: Double?
    var screen: Double?
    var notes: String?
    var habitsMet: Double?
    /// Fraction 0…1.
    var score: Double?
    var status: String?
    var streak: Double?

    var id: String { date }

    /// A day counts as logged once the sheet has scored it.
    var isLogged: Bool { score != nil }

    func answer(_ habit: Habit) -> String? {
        switch habit {
        case .office: office
        case .breakfast: breakfast
        case .lunch: lunch
        case .protein: protein
        case .workout: workout
        }
    }

    mutating func setAnswer(_ value: String?, for habit: Habit) {
        switch habit {
        case .office: office = value
        case .breakfast: breakfast = value
        case .lunch: lunch = value
        case .protein: protein = value
        case .workout: workout = value
        }
    }

    enum CodingKeys: String, CodingKey {
        case date, office, breakfast, lunch, protein, workout, water, sleep, steps
        case weight, screen, notes, habitsMet, score, status, streak
    }

    init(date: String) { self.date = date }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        date = try c.decode(String.self, forKey: .date)
        office = c.flexString(.office)
        breakfast = c.flexString(.breakfast)
        lunch = c.flexString(.lunch)
        protein = c.flexString(.protein)
        workout = c.flexString(.workout)
        water = c.flexDouble(.water)
        sleep = c.flexDouble(.sleep)
        steps = c.flexDouble(.steps)
        weight = c.flexDouble(.weight)
        screen = c.flexDouble(.screen)
        notes = c.flexString(.notes)
        habitsMet = c.flexDouble(.habitsMet)
        score = c.flexDouble(.score)
        status = c.flexString(.status)
        streak = c.flexDouble(.streak)
    }
}

/// One Finance Tracker ledger row. `row` addresses it for edit and delete.
struct SheetExpense: Codable, Identifiable, Hashable {
    var row: Int
    var date: String?
    var description: String?
    var category: String?
    var method: String?
    var qar: Double?
    var inr: Double?
    var notes: String?

    var id: Int { row }

    enum CodingKeys: String, CodingKey {
        case row, date, description, category, method, qar, inr, notes
    }

    init(row: Int, date: String?, description: String?, category: String?, method: String?, qar: Double?, inr: Double?, notes: String?) {
        self.row = row
        self.date = date
        self.description = description
        self.category = category
        self.method = method
        self.qar = qar
        self.inr = inr
        self.notes = notes
    }

    /// Still waiting to reach the sheet (shown with a pending badge).
    var isPending: Bool { row < 0 }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        row = try c.decode(Int.self, forKey: .row)
        date = c.flexString(.date)
        description = c.flexString(.description)
        category = c.flexString(.category)
        method = c.flexString(.method)
        qar = c.flexDouble(.qar)
        inr = c.flexDouble(.inr)
        notes = c.flexString(.notes)
    }
}

/// One month on Salary & Savings. Salary, other income and remittance are
/// inputs; the rest are formulas.
struct SalaryMonth: Codable, Identifiable, Hashable {
    var row: Int
    var month: String
    var label: String
    var salary: Double?
    var otherIncome: Double?
    var totalIncome: Double?
    var expenses: Double?
    var remittanceQar: Double?
    var remittanceInr: Double?
    var netSavings: Double?
    /// Fraction.
    var savingsRate: Double?
    var goalSavings: Double?
    var cumulative: Double?
    var status: String?

    var id: String { month }

    enum CodingKeys: String, CodingKey {
        case row, month, label, salary, otherIncome, totalIncome, expenses
        case remittanceQar, remittanceInr, netSavings, savingsRate, goalSavings, cumulative, status
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        row = try c.decode(Int.self, forKey: .row)
        month = try c.decode(String.self, forKey: .month)
        label = c.flexString(.label) ?? month
        salary = c.flexDouble(.salary)
        otherIncome = c.flexDouble(.otherIncome)
        totalIncome = c.flexDouble(.totalIncome)
        expenses = c.flexDouble(.expenses)
        remittanceQar = c.flexDouble(.remittanceQar)
        remittanceInr = c.flexDouble(.remittanceInr)
        netSavings = c.flexDouble(.netSavings)
        savingsRate = c.flexDouble(.savingsRate)
        goalSavings = c.flexDouble(.goalSavings)
        cumulative = c.flexDouble(.cumulative)
        status = c.flexString(.status)
    }
}

/// A monthly bill on the Recurring tab, posted to the ledger automatically on `day`.
struct RecurringExpense: Codable, Identifiable, Hashable {
    var row: Int?
    var description: String?
    var category: String?
    var method: String?
    var amount: Double?
    var day: Int?
    var active: Bool = true
    /// Newest month already posted, "yyyy-MM".
    var lastAdded: String?
    var notes: String?

    var id: String { row.map(String.init) ?? UUID().uuidString }

    enum CodingKeys: String, CodingKey {
        case row, description, category, method, amount, day, active, lastAdded, notes
    }

    init(row: Int? = nil, description: String? = nil, category: String? = nil, method: String? = nil,
         amount: Double? = nil, day: Int? = nil, active: Bool = true, lastAdded: String? = nil, notes: String? = nil) {
        self.row = row; self.description = description; self.category = category; self.method = method
        self.amount = amount; self.day = day; self.active = active; self.lastAdded = lastAdded; self.notes = notes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        row = c.flexDouble(.row).map { Int($0) }
        description = c.flexString(.description)
        category = c.flexString(.category)
        method = c.flexString(.method)
        amount = c.flexDouble(.amount)
        day = c.flexDouble(.day).map { Int($0) }
        active = (try? c.decodeIfPresent(Bool.self, forKey: .active)) ?? true
        lastAdded = c.flexString(.lastAdded)
        notes = c.flexString(.notes)
    }
}

/// A table exactly as the sheet displays it (formatted text, not numbers).
struct SheetTable: Codable, Hashable {
    var title: String?
    var headers: [String]
    var rows: [[String]]
}

// MARK: - Lenient decoding

/// Sheet cells are loosely typed: a number column can hold typed text and an
/// empty cell arrives as null. One odd cell must not fail the whole snapshot,
/// so numbers accept numeric strings and anything unreadable becomes nil.
extension KeyedDecodingContainer {
    func flexDouble(_ key: Key) -> Double? {
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return d }
        if let s = try? decodeIfPresent(String.self, forKey: key) {
            return Double(s.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces))
        }
        return nil
    }

    func flexString(_ key: Key) -> String? {
        if let s = try? decodeIfPresent(String.self, forKey: key) {
            let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        if let d = try? decodeIfPresent(Double.self, forKey: key) {
            return d.rounded() == d ? String(Int(d)) : String(d)
        }
        return nil
    }
}

// MARK: - Steps

extension SheetSnapshot {
    /// Days in a row at or over the steps target, up to yesterday, plus today once reached.
    /// A day with no steps recorded ends the run.
    func stepStreak(todaySteps: Int? = nil, today: String = SheetDate.today) -> Int {
        let target = settings.stepsTarget ?? 8000
        let byDate = Dictionary(days.map { ($0.date, $0.steps ?? 0) }, uniquingKeysWith: max)
        let calendar = Calendar.current
        guard var day = SheetDate.date(today) else { return 0 }
        var streak = 0
        let now = max(Double(todaySteps ?? 0), byDate[today] ?? 0)
        if now >= target { streak = 1 }
        while let previous = calendar.date(byAdding: .day, value: -1, to: day), (byDate[SheetDate.string(previous)] ?? 0) >= target {
            streak += 1
            day = previous
        }
        return streak
    }

    /// Steps for the last seven days, oldest first, ending today.
    func stepsWeek(todaySteps: Int? = nil, today: String = SheetDate.today) -> [Int] {
        let byDate = Dictionary(days.map { ($0.date, Int($0.steps ?? 0)) }, uniquingKeysWith: max)
        guard let end = SheetDate.date(today) else { return [] }
        return (0..<7).reversed().compactMap { back in
            guard let d = Calendar.current.date(byAdding: .day, value: -back, to: end) else { return nil }
            let key = SheetDate.string(d)
            return key == today ? max(todaySteps ?? 0, byDate[key] ?? 0) : (byDate[key] ?? 0)
        }
    }
}
