//
//  LifeGoalModels.swift
//  MyPersonalTracker
//
//  Short-term and long-term goals (24_LifeGoals.gs), each with a checklist of
//  steps. A short-term goal can build toward a long-term one, and counts
//  toward its progress.
//

import Foundation

struct LifeGoalData: Codable {
    var goals: [LifeGoal] = []
    var steps: [GoalStep] = []

    init() {}

    enum CodingKeys: String, CodingKey { case goals, steps }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        goals = (try? c.decodeIfPresent([LifeGoal].self, forKey: .goals)) ?? []
        steps = (try? c.decodeIfPresent([GoalStep].self, forKey: .steps)) ?? []
    }
}

extension SheetSnapshot {
    var lifeGoalData: LifeGoalData { lifeGoals ?? LifeGoalData() }
}

struct LifeGoal: Codable, Hashable, Identifiable {
    enum Horizon: String, Codable, CaseIterable {
        case short = "Short term", long = "Long term"
        var hint: String { self == .short ? "The next few months" : "A year or more" }
        var symbol: String { self == .short ? "flag.fill" : "mountain.2.fill" }
    }

    enum Status: String, Codable, CaseIterable { case active = "Active", reached = "Reached", dropped = "Dropped" }

    var id: String
    var title: String
    var horizon: Horizon
    var area: GoalArea
    var why: String?
    var due: String?
    /// The long-term goal this short-term one builds toward.
    var parentId: String?
    var status: Status
    /// Typed progress (0…1), used when the goal has no steps or short-term goals.
    var progress: Double?
    var created: String?
    var doneOn: String?
    var updated: String?
    var notes: String?
    var pending = false

    init(id: String = UUID().uuidString, title: String = "", horizon: Horizon = .short, area: GoalArea = .personal, why: String? = nil,
         due: String? = nil, parentId: String? = nil, status: Status = .active, progress: Double? = nil, created: String? = SheetDate.today,
         doneOn: String? = nil, notes: String? = nil) {
        self.id = id; self.title = title; self.horizon = horizon; self.area = area; self.why = why; self.due = due
        self.parentId = parentId; self.status = status; self.progress = progress; self.created = created; self.doneOn = doneOn
        self.updated = SheetDate.today; self.notes = notes
    }

    enum CodingKeys: String, CodingKey { case id, title, horizon, area, why, due, parentId, status, progress, created, doneOn, updated, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        title = c.flexString(.title) ?? ""
        horizon = c.flexString(.horizon).flatMap(Horizon.init(rawValue:)) ?? .short
        area = c.flexString(.area).flatMap(GoalArea.init(rawValue:)) ?? .other
        why = c.flexString(.why)
        due = c.flexString(.due)
        parentId = c.flexString(.parentId)
        status = c.flexString(.status).flatMap(Status.init(rawValue:)) ?? .active
        progress = c.flexDouble(.progress)
        created = c.flexString(.created)
        doneOn = c.flexString(.doneOn)
        updated = c.flexString(.updated)
        notes = c.flexString(.notes)
    }
}

enum GoalArea: String, Codable, CaseIterable, Identifiable {
    case health = "Health", career = "Career", money = "Money", learning = "Learning", personal = "Personal",
         relationships = "Family & friends", home = "Home", travel = "Travel", other = "Other"
    var id: String { rawValue }

    var symbol: String {
        switch self {
        case .health: "heart.fill"
        case .career: "briefcase.fill"
        case .money: "banknote.fill"
        case .learning: "book.fill"
        case .personal: "leaf.fill"
        case .relationships: "person.2.fill"
        case .home: "house.fill"
        case .travel: "airplane"
        case .other: "star.fill"
        }
    }
}

struct GoalStep: Codable, Hashable, Identifiable {
    var id: String
    var goalId: String
    var step: String
    var done: Bool
    var doneOn: String?
    var order: Double?
    var due: String?
    var pending = false

    init(id: String = UUID().uuidString, goalId: String, step: String, done: Bool = false, doneOn: String? = nil, order: Double? = nil, due: String? = nil) {
        self.id = id; self.goalId = goalId; self.step = step; self.done = done; self.doneOn = doneOn; self.order = order; self.due = due
    }

    enum CodingKeys: String, CodingKey { case id, goalId, step, done, doneOn, order, due }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        goalId = c.flexString(.goalId) ?? ""
        step = c.flexString(.step) ?? ""
        done = (try? c.decodeIfPresent(Bool.self, forKey: .done)) ?? (c.flexString(.done) == "Y")
        doneOn = c.flexString(.doneOn)
        order = c.flexDouble(.order)
        due = c.flexString(.due)
    }
}

extension LifeGoalData {
    func goal(_ id: String?) -> LifeGoal? { goals.first { $0.id == id } }

    /// A goal's steps in their order (then oldest first).
    func steps(of goalId: String) -> [GoalStep] {
        steps.filter { $0.goalId == goalId }.enumerated()
            .sorted { ($0.element.order ?? Double($0.offset)) < ($1.element.order ?? Double($1.offset)) }
            .map(\.element)
    }

    /// The short-term goals building toward a long-term one (dropped ones left out).
    func children(of goalId: String) -> [LifeGoal] {
        goals.filter { $0.parentId == goalId && $0.status != .dropped }
    }

    /// 0…1: steps done and short-term goals under it (each counting as one, by its own progress);
    /// with neither, the typed progress. Reached is always 1.
    func progress(of goal: LifeGoal) -> Double {
        if goal.status == .reached { return 1 }
        let own = steps(of: goal.id)
        let kids = children(of: goal.id)
        let parts = own.count + kids.count
        guard parts > 0 else { return min(max(goal.progress ?? 0, 0), 1) }
        let done = Double(own.filter(\.done).count) + kids.reduce(0) { $0 + progress(of: $1) }
        return done / Double(parts)
    }

    /// The first step not done yet.
    func nextStep(of goal: LifeGoal) -> GoalStep? { steps(of: goal.id).first { !$0.done } }

    var active: [LifeGoal] { goals.filter { $0.status == .active } }

    /// Active goals of a horizon: dated ones soonest first, then undated, by title.
    func active(_ horizon: LifeGoal.Horizon) -> [LifeGoal] {
        active.filter { $0.horizon == horizon }.sorted { a, b in
            switch (a.due, b.due) {
            case let (x?, y?): x < y
            case (_?, nil): true
            case (nil, _?): false
            case (nil, nil): a.title < b.title
            }
        }
    }

    /// No step ticked or edit for `days` — time to look at it again.
    func isStale(_ goal: LifeGoal, today: String = SheetDate.today, days: Int = 14) -> Bool {
        guard goal.status == .active, let last = lastActivity(goal), let a = SheetDate.date(last), let b = SheetDate.date(today) else { return false }
        return (Calendar.current.dateComponents([.day], from: a, to: b).day ?? 0) >= days
    }

    func lastActivity(_ goal: LifeGoal) -> String? {
        ([goal.updated, goal.created] + steps(of: goal.id).map(\.doneOn)).compactMap { $0 }.max()
    }

    /// Goals reached in the month of `today` ("yyyy-MM").
    func reached(inMonthOf today: String = SheetDate.today) -> [LifeGoal] {
        goals.filter { $0.status == .reached && ($0.doneOn ?? "").hasPrefix(String(today.prefix(7))) }
    }
}

enum GoalTime {
    /// "by 31 Dec · 84 days", "by 31 Dec 2027 · 1 yr 2 mo", "3 days overdue".
    static func due(_ date: String?, today: Date = .now) -> String? {
        guard let date, let d = SheetDate.date(date) else { return nil }
        let days = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: today), to: d).day ?? 0
        if days < 0 { return "\(-days) day\(days == -1 ? "" : "s") overdue" }
        if days == 0 { return "due today" }
        let sameYear = Calendar.current.component(.year, from: d) == Calendar.current.component(.year, from: today)
        let when = d.formatted(sameYear ? .dateTime.day().month() : .dateTime.day().month().year())
        if days <= 60 { return "by \(when) · \(days) day\(days == 1 ? "" : "s")" }
        let months = Calendar.current.dateComponents([.month], from: today, to: d).month ?? 0
        let span = months >= 12 ? "\(months / 12) yr\(months % 12 > 0 ? " \(months % 12) mo" : "")" : "\(months) mo"
        return "by \(when) · \(span)"
    }

    /// Short term when the date is within about four months.
    static func suggestedHorizon(for date: Date, today: Date = .now) -> LifeGoal.Horizon {
        (Calendar.current.dateComponents([.month], from: today, to: date).month ?? 0) < 4 ? .short : .long
    }
}
