//
//  ReviewViews.swift
//  MyPersonalTracker
//
//  The week in review (Monday–Sunday, any week of the period) and the year in
//  review (read across every archived period on the sheet).
//

import SwiftUI

// MARK: - Week

struct WeeklyReviewView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var monday = WeeklyReviewView.reviewWeek()

    /// Sunday and Monday look back at the week just finished; other days show this week so far.
    static func reviewWeek(_ now: Date = .now) -> Date {
        let thisMonday = FoodView.monday(of: now)
        let weekday = Calendar.current.component(.weekday, from: now)   // 1 = Sunday, 2 = Monday
        return weekday == 2 ? (Calendar.current.date(byAdding: .day, value: -7, to: thisMonday) ?? thisMonday) : thisMonday
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let s = store.snapshot {
                    let r = WeeklyReview(s, weekOf: monday)
                    LazyVStack(spacing: 12) {
                        weekPicker
                        hero(r)
                        ReviewTile(symbol: "checklist", tint: SheetTheme.navy, title: "Habits",
                                   value: r.averageScore.map { SheetFormat.percent($0, 0) } ?? "—",
                                   detail: "\(r.logged) of 7 days logged · \(r.greatDays) great day\(r.greatDays == 1 ? "" : "s")"
                                    + (r.bestDay.map { " · best \(SheetDate.weekdayDay($0.date))" } ?? ""))
                        ReviewTile(symbol: "banknote.fill", tint: r.budget.map { r.spent <= $0 } ?? true ? SheetTheme.good : SheetTheme.bad, title: "Money",
                                   value: "\(SheetFormat.qar(r.spent)) \(Money.main)",
                                   detail: [r.budget.map { b in r.spent <= b ? "\(SheetFormat.qar(b - r.spent)) under the week's budget" : "\(SheetFormat.qar(r.spent - b)) over the week's budget" },
                                            r.topCategory.map { "most on \($0.name) (\(SheetFormat.qar($0.amount)))" }].compactMap { $0 }.joined(separator: " · "))
                        ReviewTile(symbol: "graduationcap.fill", tint: SheetTheme.purple, title: "Study",
                                   value: StudyFormat.minutes(r.studyMinutes),
                                   detail: "\(r.studySessions) session\(r.studySessions == 1 ? "" : "s")" + (r.studyTarget.map { " · target \(StudyFormat.minutes($0))" } ?? ""),
                                   fraction: r.studyTarget.map { $0 > 0 ? r.studyMinutes / $0 : 0 })
                        ReviewTile(symbol: "figure.walk", tint: SheetTheme.good, title: "Steps",
                                   value: r.steps.formatted(),
                                   detail: r.stepDays > 0 ? "\((r.steps / r.stepDays).formatted()) a day · goal hit \(r.stepGoalDays) day\(r.stepGoalDays == 1 ? "" : "s")" : "No steps recorded"
                                    + (r.averageSleep.map { " · sleep \(String(format: "%.1f", $0)) h" } ?? ""))
                        ReviewTile(symbol: "flag.fill", tint: SheetTheme.indigo, title: "Goals",
                                   value: "\(r.goalStepsDone) step\(r.goalStepsDone == 1 ? "" : "s")",
                                   detail: r.goalsReached.isEmpty ? "ticked off toward your goals" : "Reached: " + r.goalsReached.joined(separator: ", ") + " 🎉")
                        if r.plansTotal > 0 {
                            ReviewTile(symbol: "calendar", tint: SheetTheme.teal, title: "Plans", value: "\(r.plansDone)/\(r.plansTotal)", detail: "plans done")
                        }
                        Button {
                            let next = Calendar.current.date(byAdding: .day, value: 7, to: monday) ?? monday
                            AppNavigation.shared.plannerWeek = max(next, FoodView.monday(of: .now))
                            AppNavigation.shared.dailySection = "Planner"
                            AppNavigation.shared.tab = AppTab.daily
                            dismiss()
                        } label: {
                            Label("Plan next week", systemImage: "calendar.badge.plus").font(.headline)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(SheetTheme.navy, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .foregroundStyle(.white)
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 4)
                    }
                    .padding(.horizontal, 16).padding(.bottom, 24)
                }
            }
            .background(SheetTheme.page)
            .navigationTitle("Week in review")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .tint(SheetTheme.navy)
    }

    private var weekPicker: some View {
        HStack {
            Button { monday = Calendar.current.date(byAdding: .day, value: -7, to: monday) ?? monday } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 36)
            }
            Spacer()
            let end = Calendar.current.date(byAdding: .day, value: 6, to: monday) ?? monday
            Text("\(monday.formatted(.dateTime.day().month())) – \(end.formatted(.dateTime.day().month()))").font(.headline)
            Spacer()
            Button { monday = Calendar.current.date(byAdding: .day, value: 7, to: monday) ?? monday } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 36)
            }
            .disabled(monday >= FoodView.monday(of: .now))
        }
        .foregroundStyle(SheetTheme.navy)
    }

    private func hero(_ r: WeeklyReview) -> some View {
        let headline: String = {
            guard let a = r.averageScore else { return "A quiet week" }
            return a >= 0.85 ? "A great week 🌟" : a >= 0.7 ? "A solid week 👍" : "A week to build on 💪"
        }()
        return VStack(alignment: .leading, spacing: 6) {
            Text(headline).font(.title2.weight(.bold)).foregroundStyle(.white)
            Text(r.summary).font(.subheadline).foregroundStyle(.white.opacity(0.85))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(SheetTheme.heroGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct ReviewTile: View {
    let symbol: String
    let tint: Color
    let title: String
    let value: String
    let detail: String
    var fraction: Double?

    var body: some View {
        SheetCard {
            HStack(alignment: .top, spacing: 12) {
                IconBadge(symbol: symbol, tint: tint, size: 38)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                        Spacer()
                        Text(value).font(.headline.monospacedDigit())
                    }
                    if let fraction { ProgressView(value: min(max(fraction, 0), 1)).tint(tint) }
                    if !detail.isEmpty { Text(detail).font(.caption).foregroundStyle(.secondary) }
                }
            }
        }
    }
}

/// On the Dashboard from Friday evening to Monday.
struct WeeklyReviewCard: View {
    let onOpen: () -> Void

    var body: some View {
        let now = Date.now
        let weekday = Calendar.current.component(.weekday, from: now)
        let show = weekday == 1 || weekday == 2 || (weekday == 7) || (weekday == 6 && Calendar.current.component(.hour, from: now) >= 17)
        if show {
            Button(action: onOpen) {
                SheetCard {
                    HStack(spacing: 12) {
                        IconBadge(symbol: "chart.bar.doc.horizontal.fill", tint: SheetTheme.indigo, size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Your week in review").font(.headline).foregroundStyle(Color(.label))
                            Text("Habits, money, study, steps and goals — then plan next week.").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                }
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Year

/// What the sheet adds up across periods (yearReview in 28_YearReview.gs).
struct YearNumbers {
    var year: Int
    var tracked = 0, logged = 0, greatDays = 0, bestStreak = 0, workouts = 0
    var averageScore: Double?
    var steps = 0.0, stepDays = 0
    var bestStepsDay: (date: String, steps: Double)?
    var averageSleep: Double?
    var weightFrom: Double?, weightTo: Double?
    var spent = 0.0, income = 0.0, remitted = 0.0, noSpendDays = 0
    var byCategory: [(String, Double)] = []
    var byMonth: [(String, Double)] = []
    var biggest: (description: String, amount: Double, date: String)?

    init(year: Int) { self.year = year }

    init(json: [String: Any]) {
        func d(_ v: Any?) -> Double? { (v as? NSNumber)?.doubleValue }
        year = (json["year"] as? NSNumber)?.intValue ?? Calendar.current.component(.year, from: .now)
        let days = json["days"] as? [String: Any] ?? [:]
        tracked = Int(d(days["tracked"]) ?? 0); logged = Int(d(days["logged"]) ?? 0)
        greatDays = Int(d(days["greatDays"]) ?? 0); bestStreak = Int(d(days["bestStreak"]) ?? 0); workouts = Int(d(days["workouts"]) ?? 0)
        averageScore = d(days["averageScore"]); steps = d(days["steps"]) ?? 0; stepDays = Int(d(days["stepDays"]) ?? 0)
        if let b = days["bestStepsDay"] as? [String: Any], let date = b["date"] as? String { bestStepsDay = (date, d(b["steps"]) ?? 0) }
        averageSleep = d(days["averageSleep"])
        weightFrom = d((days["weightFrom"] as? [String: Any])?["kg"]); weightTo = d((days["weightTo"] as? [String: Any])?["kg"])
        let money = json["money"] as? [String: Any] ?? [:]
        spent = d(money["spent"]) ?? 0; income = d(money["income"]) ?? 0; remitted = d(money["remitted"]) ?? 0
        noSpendDays = Int(d(money["noSpendDays"]) ?? 0)
        byCategory = ((money["byCategory"] as? [[String: Any]]) ?? []).map { ($0["category"] as? String ?? "Other", d($0["amount"]) ?? 0) }
        byMonth = ((money["byMonth"] as? [[String: Any]]) ?? []).map { ($0["month"] as? String ?? "", d($0["amount"]) ?? 0) }
        if let b = money["biggest"] as? [String: Any] {
            biggest = ((b["description"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? (b["category"] as? String ?? "Expense"), d(b["amount"]) ?? 0, b["date"] as? String ?? "")
        }
    }

    /// Worked out on the phone from the current period only (sample data, or no connection).
    init(local s: SheetSnapshot, year: Int) {
        self.year = year
        let prefix = String(year)
        let days = s.days.filter { $0.date.hasPrefix(prefix) && $0.date <= SheetDate.today }
        tracked = days.count
        let scored = days.filter(\.isLogged)
        logged = scored.count
        averageScore = scored.isEmpty ? nil : scored.reduce(0) { $0 + ($1.score ?? 0) } / Double(scored.count)
        greatDays = scored.filter { ($0.status ?? "") == "Excellent" }.count
        bestStreak = Int(days.compactMap(\.streak).max() ?? 0)
        workouts = days.filter { $0.workout == "Y" }.count
        let stepped = days.filter { ($0.steps ?? 0) > 0 }
        steps = stepped.reduce(0) { $0 + ($1.steps ?? 0) }; stepDays = stepped.count
        if let b = stepped.max(by: { ($0.steps ?? 0) < ($1.steps ?? 0) }) { bestStepsDay = (b.date, b.steps ?? 0) }
        let slept = days.compactMap(\.sleep).filter { $0 > 0 }
        averageSleep = slept.isEmpty ? nil : slept.reduce(0, +) / Double(slept.count)
        let weights = days.filter { ($0.weight ?? 0) > 0 }
        weightFrom = weights.first?.weight; weightTo = weights.last?.weight
        let lines = s.expenses.filter { ($0.date ?? "").hasPrefix(prefix) && ($0.qar ?? 0) > 0 }
        spent = lines.reduce(0) { $0 + ($1.qar ?? 0) }
        byCategory = Dictionary(grouping: lines, by: { $0.category ?? "Other" }).mapValues { $0.reduce(0) { $0 + ($1.qar ?? 0) } }
            .sorted { $0.value > $1.value }.map { ($0.key, $0.value) }
        byMonth = Dictionary(grouping: lines, by: { String(($0.date ?? "").dropFirst(5).prefix(2)) }).mapValues { $0.reduce(0) { $0 + ($1.qar ?? 0) } }
            .sorted { $0.key < $1.key }.map { ($0.key, $0.value) }
        if let b = lines.max(by: { ($0.qar ?? 0) < ($1.qar ?? 0) }) { biggest = (b.description ?? b.category ?? "Expense", b.qar ?? 0, b.date ?? "") }
        let spendDays = Set(lines.compactMap(\.date))
        noSpendDays = days.filter { !spendDays.contains($0.date) }.count
        let months = s.salary.filter { $0.month.hasPrefix(prefix) && $0.month <= String(SheetDate.today.prefix(7)) }
        income = months.reduce(0) { $0 + ($1.totalIncome ?? 0) }
        remitted = months.reduce(0) { $0 + ($1.remittanceQar ?? 0) }
    }
}

struct YearReviewView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var year = Calendar.current.component(.year, from: .now)
    @State private var numbers: YearNumbers?
    @State private var loading = false
    @State private var note: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    HStack {
                        Button { year -= 1 } label: { Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 36) }
                        Spacer()
                        Text(String(year)).font(.title2.weight(.bold))
                        Spacer()
                        Button { year += 1 } label: { Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 36) }
                            .disabled(year >= Calendar.current.component(.year, from: .now))
                    }
                    .foregroundStyle(SheetTheme.navy)
                    if loading { ProgressView("Adding up the year…").padding(40) }
                    if let note { Text(note).font(.caption).foregroundStyle(.secondary) }
                    if let n = numbers, let s = store.snapshot, !loading { YearCards(n: n, s: s) }
                }
                .padding(.horizontal, 16).padding(.bottom, 24)
            }
            .background(SheetTheme.page)
            .navigationTitle("Year in review")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task(id: year) { await load() }
        }
        .tint(SheetTheme.navy)
    }

    private func load() async {
        loading = true
        defer { loading = false }
        note = nil
        if let json = try? await store.yearReview(year: year) {
            numbers = YearNumbers(json: json)
        } else if let s = store.snapshot {
            numbers = YearNumbers(local: s, year: year)
            note = store.isDemo ? "Sample data: this period only." : "Couldn't reach the sheet — showing this period only."
        }
    }
}

private struct YearCards: View {
    let n: YearNumbers
    let s: SheetSnapshot

    var body: some View {
        let y = String(n.year)
        let study = s.studyData
        let studyMinutes = study.sessions.filter { $0.date.hasPrefix(y) }.reduce(0) { $0 + $1.minutes }
        let learned = study.topics.filter { ($0.lastStudied ?? "").hasPrefix(y) && ($0.stage == .mastered || $0.stage == .revising) }.count
        let saved = (s.setAside ?? []).filter { $0.date.hasPrefix(y) }.reduce(0) { $0 + $1.amount }
        let goalsReached = s.lifeGoalData.goals.filter { $0.status == .reached && ($0.doneOn ?? "").hasPrefix(y) }
        let savingsReached = s.goals.filter { $0.done && ($0.doneOn ?? "").hasPrefix(y) }
        let trips = s.shoppingData.trips.filter { $0.date.hasPrefix(y) }

        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Your \(y)").font(.title.weight(.bold)).foregroundStyle(.white)
                Text("\(n.logged) days logged · \(n.greatDays) great days · best streak \(n.bestStreak)").font(.subheadline).foregroundStyle(.white.opacity(0.85))
                if let a = n.averageScore { Text("Average habit score \(SheetFormat.percent(a, 0))").font(.subheadline.weight(.semibold)).foregroundStyle(.white) }
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(20)
            .background(LinearGradient(colors: [Color(hex: 0x4C1D95), Color(hex: 0x1F3864)], startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 24, style: .continuous))

            BigStat(symbol: "figure.walk", tint: SheetTheme.good, value: Int(n.steps).formatted(), label: "steps",
                    detail: [n.stepDays > 0 ? "\(Int(n.steps / Double(n.stepDays)).formatted()) a day" : nil,
                             n.bestStepsDay.map { "best \(Int($0.steps).formatted()) on \(SheetDate.short($0.date))" },
                             n.workouts > 0 ? "\(n.workouts) workouts" : nil].compactMap { $0 }.joined(separator: " · "))
            if n.averageSleep != nil || n.weightFrom != nil {
                BigStat(symbol: "bed.double.fill", tint: SheetTheme.indigo, value: n.averageSleep.map { String(format: "%.1f h", $0) } ?? "—", label: "average sleep",
                        detail: (n.weightFrom.flatMap { a in n.weightTo.map { b in abs(b - a) < 0.05 ? "Weight \(String(format: "%.1f", b)) kg"
                            : "Weight \(String(format: "%.1f", a)) → \(String(format: "%.1f", b)) kg (\(b - a >= 0 ? "+" : "")\(String(format: "%.1f", b - a)))" } }) ?? "")
            }
            BigStat(symbol: "banknote.fill", tint: SheetTheme.bad, value: "\(SheetFormat.qar(n.spent)) \(Money.main)", label: "spent",
                    detail: [n.byCategory.first.map { "most on \($0.0) (\(SheetFormat.qar($0.1)))" },
                             n.biggest.map { "biggest: \($0.description) \(SheetFormat.qar($0.amount))" },
                             "\(n.noSpendDays) no-spend days"].compactMap { $0 }.joined(separator: " · "))
            if !n.byMonth.isEmpty { MonthBars(byMonth: n.byMonth) }
            BigStat(symbol: "arrow.down.circle.fill", tint: SheetTheme.teal, value: "\(SheetFormat.qar(n.income)) \(Money.main)", label: "earned",
                    detail: "\(SheetFormat.qar(n.remitted)) sent home · \(SheetFormat.qar(saved)) put aside")
            BigStat(symbol: "graduationcap.fill", tint: SheetTheme.purple, value: StudyFormat.minutes(studyMinutes), label: "studied",
                    detail: "\(learned) concepts learned")
            BigStat(symbol: "flag.fill", tint: SheetTheme.indigo, value: "\(goalsReached.count + savingsReached.count)", label: "goals reached",
                    detail: (goalsReached.map(\.title) + savingsReached.map(\.name)).prefix(4).joined(separator: ", "))
            if !trips.isEmpty {
                BigStat(symbol: "cart.fill", tint: SheetTheme.good, value: "\(trips.count)", label: "shopping trips",
                        detail: "\(SheetFormat.qar(trips.reduce(0) { $0 + ($1.total ?? 0) })) \(Money.main) on groceries")
            }
        }
    }
}

private struct BigStat: View {
    let symbol: String
    let tint: Color
    let value: String
    let label: String
    let detail: String

    var body: some View {
        SheetCard {
            HStack(alignment: .top, spacing: 12) {
                IconBadge(symbol: symbol, tint: tint, size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(value).font(.title2.weight(.bold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.6)
                        Text(label).font(.subheadline).foregroundStyle(.secondary)
                    }
                    if !detail.isEmpty { Text(detail).font(.caption).foregroundStyle(.secondary) }
                }
                Spacer(minLength: 0)
            }
        }
    }
}

private struct MonthBars: View {
    let byMonth: [(String, Double)]

    var body: some View {
        let top = max(byMonth.map(\.1).max() ?? 1, 1)
        let names = Calendar.current.veryShortMonthSymbols
        SheetCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("Spending by month").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                HStack(alignment: .bottom, spacing: 6) {
                    ForEach(1...12, id: \.self) { m in
                        let v = byMonth.first { Int($0.0) == m }?.1 ?? 0
                        VStack(spacing: 4) {
                            Capsule().fill(v > 0 ? SheetTheme.bad.opacity(0.75) : Color(.tertiarySystemFill))
                                .frame(height: max(4, 80 * v / top))
                            Text(names[m - 1]).font(.caption2).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 100, alignment: .bottom)
            }
        }
    }
}
