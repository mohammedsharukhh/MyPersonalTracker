//
//  Briefing.swift
//  MyPersonalTracker
//
//  What the sheet-driven notifications say: the morning briefing, bills due
//  tomorrow, renewals coming up, and the Sunday "week in review" nudge — and
//  the week's numbers behind the review screen. Plain functions of the
//  snapshot, so they're easy to test; Reminders.swift schedules them.
//

import Foundation

enum Briefing {
    /// The morning briefing for `date`. `includeLive` adds the things that are
    /// only right on the day it's written (money left, yesterday's steps).
    static func text(_ s: SheetSnapshot, on date: Date, includeLive: Bool) -> (title: String, body: String) {
        let key = SheetDate.string(date)
        var lines: [String] = []

        let planner = s.plannerData
        let plans = planner.plans(on: key).filter { !$0.done }
        if !plans.isEmpty {
            let shown = plans.prefix(2).map { p in [PlanTime.display(p.time), p.title].compactMap { $0 }.joined(separator: " ") }
            lines.append("📅 " + shown.joined(separator: ", ") + (plans.count > 2 ? " +\(plans.count - 2) more" : ""))
        }
        let asks = s.parentAsksDue(by: key)
        if !asks.isEmpty {
            lines.append("👪 " + asks.prefix(2).map { "\($0.who.label): \($0.task)" }.joined(separator: ", ")
                         + (asks.count > 2 ? " +\(asks.count - 2) more" : ""))
        }
        if let outfit = planner.outfit(on: key) {
            let pieces = planner.pieces(of: outfit)
            lines.append("👕 " + (pieces.isEmpty ? (outfit.note ?? "Outfit planned") : pieces.joined(separator: " + ")))
        }
        let due = s.studyData.dueTopics(today: key).count
        if due > 0 { lines.append("📚 \(due) concept\(due == 1 ? "" : "s") to revise") }
        let bills = Bills.due(s, on: date)
        if !bills.isEmpty {
            lines.append("🧾 Due today: " + bills.map { "\($0.description ?? "Bill") \(SheetFormat.qar($0.amount))" }.joined(separator: ", "))
        }
        for r in s.renewalList where r.daysLeft(today: date) <= 7 {
            lines.append("⚠️ \(r.item) \(RenewalText.left(r.daysLeft(today: date)))")
        }
        let goal = s.lifeGoalData.active(.short).first { g in
            guard let d = g.due, let gd = SheetDate.date(d) else { return false }
            return (Calendar.current.dateComponents([.day], from: date, to: gd).day ?? 99) <= 7
        }
        if let goal, let next = s.lifeGoalData.nextStep(of: goal) { lines.append("🎯 \(goal.title): \(next.step)") }
        if includeLive {
            let month = String(key.prefix(7))
            let spent = s.expenses.filter { ($0.date ?? "").hasPrefix(month) }.reduce(0) { $0 + ($1.qar ?? 0) }
            if let limit = s.settings.monthlyLimit, limit > 0 {
                let left = limit - spent
                lines.append(left >= 0 ? "💰 \(SheetFormat.qar(left)) \(Money.main) left this month" : "💰 \(SheetFormat.qar(-left)) \(Money.main) over this month")
            }
            if let y = Calendar.current.date(byAdding: .day, value: -1, to: date),
               let steps = s.days.first(where: { $0.date == SheetDate.string(y) })?.steps, steps > 0 {
                let streak = s.stepStreak(today: key)
                lines.append("👣 \(Int(steps).formatted()) steps yesterday" + (streak > 1 ? " · \(streak)-day streak" : ""))
            }
        }
        let weekday = date.formatted(.dateTime.weekday(.wide))
        let title = Calendar.current.isDateInToday(date) ? "Good morning ☀️" : "Good morning ☀️ — \(weekday)"
        return (title, lines.isEmpty ? "Nothing planned — a good day to get ahead." : lines.joined(separator: "\n"))
    }
}

enum Bills {
    /// Active monthly bills that fall on `date` (a day 31 bill falls on the month's last day).
    static func due(_ s: SheetSnapshot, on date: Date) -> [RecurringExpense] {
        let calendar = Calendar.current
        let day = calendar.component(.day, from: date)
        let last = calendar.range(of: .day, in: .month, for: date)?.count ?? 31
        return (s.recurring ?? []).filter { b in
            guard b.active, let d = b.day, (b.amount ?? 0) > 0 else { return false }
            return min(d, last) == day
        }
    }
}

/// One week's numbers, Monday to Sunday.
struct WeeklyReview {
    let start: Date
    var days: [String] { (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: start) }.map(SheetDate.string) }

    var logged = 0
    var averageScore: Double?
    var greatDays = 0
    var bestDay: (date: String, score: Double)?
    var spent = 0.0
    var budget: Double?
    var topCategory: (name: String, amount: Double)?
    var studyMinutes = 0.0
    var studyTarget: Double?
    var studySessions = 0
    var steps = 0
    var stepDays = 0
    var stepGoalDays = 0
    var averageSleep: Double?
    var goalStepsDone = 0
    var goalsReached: [String] = []
    var plansDone = 0
    var plansTotal = 0

    init(_ s: SheetSnapshot, weekOf start: Date) {
        self.start = start
        let keys = Set(days)
        let first = days.first ?? "", last = days.last ?? ""
        let week = s.days.filter { keys.contains($0.date) }
        let scored = week.filter(\.isLogged)
        logged = scored.count
        averageScore = scored.isEmpty ? nil : scored.reduce(0) { $0 + ($1.score ?? 0) } / Double(scored.count)
        greatDays = scored.filter { ($0.score ?? 0) >= (s.settings.greatDay ?? 0.85) }.count
        if let best = scored.max(by: { ($0.score ?? 0) < ($1.score ?? 0) }) { bestDay = (best.date, best.score ?? 0) }
        let spending = s.expenses.filter { keys.contains($0.date ?? "") }
        spent = spending.reduce(0) { $0 + ($1.qar ?? 0) }
        budget = s.settings.dailyLimit.map { $0 * 7 }
        let byCategory = Dictionary(grouping: spending, by: { $0.category ?? "Other" }).mapValues { $0.reduce(0) { $0 + ($1.qar ?? 0) } }
        if let top = byCategory.max(by: { $0.value < $1.value }) { topCategory = (top.key, top.value) }
        let study = s.studyData
        studyMinutes = study.minutes(from: first, to: last)
        studyTarget = s.settings.studyWeeklyTarget.map { $0 * 60 }
        studySessions = study.sessions.filter { keys.contains($0.date) }.count
        let stepped = week.compactMap(\.steps).filter { $0 > 0 }
        steps = Int(stepped.reduce(0, +))
        stepDays = stepped.count
        stepGoalDays = stepped.filter { $0 >= (s.settings.stepsTarget ?? 8000) }.count
        let slept = week.compactMap(\.sleep).filter { $0 > 0 }
        averageSleep = slept.isEmpty ? nil : slept.reduce(0, +) / Double(slept.count)
        let goals = s.lifeGoalData
        goalStepsDone = goals.steps.filter { $0.done && keys.contains($0.doneOn ?? "") }.count
        goalsReached = goals.goals.filter { $0.status == .reached && keys.contains($0.doneOn ?? "") }.map(\.title)
        let plans = s.plannerData.plans.filter { keys.contains($0.date) }
        plansTotal = plans.count
        plansDone = plans.filter(\.done).count
    }

    /// One line for the Sunday notification.
    var summary: String {
        var parts: [String] = []
        if let a = averageScore { parts.append("habits \(SheetFormat.percent(a, 0))") }
        if let b = budget, b > 0 { parts.append(spent <= b ? "\(SheetFormat.qar(b - spent)) under budget" : "\(SheetFormat.qar(spent - b)) over budget") }
        if studyMinutes > 0 { parts.append("\(StudyFormat.minutes(studyMinutes)) studied") }
        if steps > 0 { parts.append("\(steps.formatted()) steps") }
        return parts.isEmpty ? "See how the week went and plan the next one." : parts.joined(separator: " · ").prefix(1).uppercased() + parts.joined(separator: " · ").dropFirst()
    }
}
