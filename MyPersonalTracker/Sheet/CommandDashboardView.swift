//
//  CommandDashboardView.swift
//  MyPersonalTracker
//
//  The Dashboard sheet, redesigned for the phone: a hero summary, grouped KPI
//  tiles, habit success bars, charts, category spend and the two key-value
//  tables. Every figure is still the sheet's own formula result.
//

import Charts
import SwiftUI

struct CommandDashboardView: View {
    let store: SheetStore
    let onOpenSettings: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if store.needsConnection {
                        ConnectSheetCard(onOpenSettings: onOpenSettings)
                    }
                    if let error = store.errorMessage, store.isConfigured {
                        SheetErrorBanner(message: error) { Task { await store.refresh() } }
                    }
                    SyncStatusBar(store: store)
                    if let snapshot = store.snapshot {
                        if NewPeriodCard.isDue(snapshot.settings) {
                            NewPeriodCard(store: store, settings: snapshot.settings)
                        }
                        DashboardContent(store: store, snapshot: snapshot, lastSynced: store.lastSynced)
                    } else if !store.needsConnection {
                        SheetLoadingView(progress: store.loadProgress).padding(.top, 80)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .containerBackground(SheetTheme.pageWash, for: .navigation)
            .navigationTitle("Dashboard")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if store.isLoading { ProgressView() }
                }
                ToolbarItemGroup(placement: .topBarTrailing) { MoreButton(); SettingsButton() }
            }
            .refreshable { await store.refresh() }
        }
    }
}

/// The four things people do most, one tap from the first screen.
private struct QuickActions: View {
    let store: SheetStore
    let snapshot: SheetSnapshot

    private enum Action: String, Identifiable { case day, expense, income, convert, people; var id: Self { self } }
    @State private var open: Action?

    var body: some View {
        let today = snapshot.days.first { $0.date == SheetDate.today }
        let month = snapshot.salary.first { $0.month == String(SheetDate.today.prefix(7)) }
        HStack(spacing: 10) {
            tile(today?.isLogged == true ? "Edit today" : "Log today", "checklist", .day, enabled: today != nil)
            tile("Add expense", "plus.circle.fill", .expense)
            tile("Income", "arrow.down.circle.fill", .income, enabled: month != nil)
            if Money.single {
                tile("People", "person.2.fill", .people)
            } else {
                tile("Convert", "dollarsign.arrow.circlepath", .convert)
            }
        }
        .sheet(item: $open) { action in
            switch action {
            case .day: if let today { DayLogSheet(store: store, day: today) }
            case .expense: SheetExpenseEditor(store: store, expense: nil)
            case .income: if let month { SalaryEditor(store: store, month: month) }
            case .convert: CurrencyConverterView()
            case .people: PeopleView(store: store)
            }
        }
        #if DEBUG
        // `-sheetOpen day|expense|income|convert` opens that screen on launch (screenshots).
        .task {
            if let raw = UserDefaults.standard.string(forKey: "sheetOpen"), let action = Action(rawValue: raw) { open = action }
        }
        #endif
    }

    /// One colour for all four: they're equal shortcuts, and red would read as a warning.
    private func tile(_ title: String, _ symbol: String, _ action: Action, enabled: Bool = true) -> some View {
        let tint = SheetTheme.navy
        return Button { open = action } label: {
            VStack(spacing: 8) {
                Image(systemName: symbol)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(tint)
                    .frame(width: 46, height: 46)
                    .background(tint.opacity(0.14), in: Circle())
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
    }
}

private struct DashboardContent: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    let lastSynced: Date?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    /// A card's value by its label on the Dashboard sheet.
    private func card(_ label: String) -> String {
        snapshot.dashboard.first { $0.label.localizedCaseInsensitiveContains(label) }?.value ?? "—"
    }

    var body: some View {
        let period = PeriodInfo(snapshot.settings)

        HeroCard(period: period, settings: snapshot.settings,
                 score: SheetFormat.parse(card("habit score")) ?? 0,
                 scoreText: card("habit score"), streak: card("streak"))

        QuickActions(store: store, snapshot: snapshot)
        PlannerTodayCard(snapshot: snapshot) {
            AppNavigation.shared.dailySection = "Planner"
            withAnimation(.snappy) { AppNavigation.shared.tab = AppTab.daily }
        }
        ParentsCard(store: store, snapshot: snapshot) { withAnimation(.snappy) { AppNavigation.shared.tab = AppTab.parents } }
        RenewalsDueCard(snapshot: snapshot) { AppNavigation.shared.showRenewals = true }
        WeeklyReviewCard { AppNavigation.shared.showWeeklyReview = true }
        GoalsFocusCard(snapshot: snapshot) {
            AppNavigation.shared.dailySection = "Goals"
            withAnimation(.snappy) { AppNavigation.shared.tab = AppTab.daily }
        }
        StudyTodayCard(snapshot: snapshot)
        FoodTodayCard(snapshot: snapshot)
        InsightsCard(snapshot: snapshot)

        SectionTitle(title: "Habits")
        LazyVGrid(columns: columns, spacing: 12) {
            SheetStatTile(symbol: "calendar.badge.checkmark", tint: SheetTheme.sky, title: "Days logged", value: card("days logged"))
            SheetStatTile(symbol: "star.fill", tint: SheetTheme.purple, title: "Excellent days", value: card("excellent"))
            SheetStatTile(symbol: "drop.fill", tint: SheetTheme.sky, title: "Avg water", value: card("water") + " L",
                     caption: snapshot.settings.waterTarget.map { "Target \(SheetFormat.number($0)) L" })
            SheetStatTile(symbol: "bed.double.fill", tint: SheetTheme.indigo, title: "Avg sleep", value: card("sleep") + " h",
                     caption: snapshot.settings.sleepTarget.map { "Target \(SheetFormat.number($0)) h" })
        }

        if let rates = snapshot.dashboardTables[safe: 0], !rates.rows.isEmpty {
            HabitRatesCard(table: rates)
        }

        ChartCard(title: "Daily habit score", subtitle: "Your score for each logged day") {
            HabitScoreChart(days: snapshot.days, greatDay: snapshot.settings.greatDay)
        }

        SectionTitle(title: "Spending", subtitle: "Daily cap \(SheetFormat.qar(snapshot.settings.dailyLimit)) \(Money.main)")
        LazyVGrid(columns: columns, spacing: 12) {
            SheetStatTile(symbol: "banknote.fill", tint: SheetTheme.teal, title: "Total spend", value: card("total spend (\(Money.main.lowercased()))") + " \(Money.main)",
                     caption: Money.single ? nil : Money.homeText(card("total spend (\(Money.home.lowercased()))")))
            SheetStatTile(symbol: "chart.line.uptrend.xyaxis", tint: SheetTheme.warn, title: "Avg per day", value: card("avg daily spend") + " \(Money.main)")
            SheetStatTile(symbol: "exclamationmark.triangle.fill", tint: SheetTheme.bad, title: "Days over limit", value: card("days over limit"))
            let left = card("budget left")
            SheetStatTile(symbol: "wallet.pass.fill", tint: SheetTheme.good, title: "Budget left", value: left + " \(Money.main)",
                     caption: "This month", valueTint: left.hasPrefix("(") ? SheetTheme.bad : .primary)
        }

        ChartCard(title: "Daily spend", subtitle: "Red bars went over your daily cap") {
            DailySpendChart(expenses: snapshot.expenses, days: snapshot.days, cap: snapshot.settings.dailyLimit)
        }

        if let categories = snapshot.dashboardTables[safe: 1] {
            CategoryCard(table: categories)
        }

        SectionTitle(title: "Money")
        LazyVGrid(columns: columns, spacing: 12) {
            // Saved = what you put aside; spending inside your budget only lowers "left over".
            let saved = SalarySplit.putAside(snapshot)
            let leftOver = snapshot.salary.reduce(0) { $0 + ($1.netSavings ?? 0) } - saved
            SheetStatTile(symbol: "building.columns.fill", tint: SheetTheme.good, title: "Saved", value: SheetFormat.qar(saved) + " \(Money.main)",
                     caption: "Put aside · emergency + SIP")
            SheetStatTile(symbol: "wallet.pass.fill", tint: SheetTheme.teal, title: "Left over", value: SheetFormat.qar(leftOver) + " \(Money.main)",
                     caption: "Not spent or put aside yet", valueTint: leftOver < 0 ? SheetTheme.bad : .primary)
        }
        if let money = snapshot.dashboardTables[safe: 3] {
            KeyValueCard(title: "Money snapshot", table: money)
        }
        if let glance = snapshot.dashboardTables[safe: 2] {
            KeyValueCard(title: "This period at a glance", table: glance)
        }

        ChartCard(title: "Weekly habit score", subtitle: "Average score per week") {
            WeeklyScoreChart(table: snapshot.weekly)
        }

        if let lastSynced {
            Label("Synced \(lastSynced.formatted(.relative(presentation: .named)))", systemImage: "arrow.triangle.2.circlepath")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.top, 4)
        }
    }
}

/// Period progress, habit score ring and streak.
private struct HeroCard: View {
    let period: PeriodInfo
    let settings: SheetSettings
    let score: Double
    let scoreText: String
    let streak: String

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .center, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Life & Finance").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    if let d = period.dayOfPeriod {
                        Text("Day \(d.day) of \(d.total)").font(.system(.title, design: .rounded, weight: .bold)).foregroundStyle(.white)
                    }
                    Text("\(SheetDate.short(settings.startDate)) – \(SheetDate.short(settings.endDate))")
                        .font(.caption).foregroundStyle(.white.opacity(0.75))
                }
                Spacer()
                ZStack {
                    ScoreRing(fraction: score, lineWidth: 9)
                    VStack(spacing: 0) {
                        Text(scoreText).font(.system(.headline, design: .rounded, weight: .bold)).foregroundStyle(.white)
                            .minimumScaleFactor(0.7)
                        Text("habits").font(.caption2).foregroundStyle(.white.opacity(0.75))
                    }
                    .padding(8)
                }
                .frame(width: 84, height: 84)
            }
            if let d = period.dayOfPeriod, d.total > 0 {
                ProgressView(value: Double(d.day), total: Double(d.total))
                    .tint(.white)
            }
            HStack(spacing: 8) {
                HeroChip(symbol: "flame.fill", text: "\(streak) day streak")
                if !Money.single {
                    HeroChip(symbol: "arrow.left.arrow.right", text: "1 \(Money.main) = \(SheetFormat.number(settings.rate, digits: 2)) \(Money.home)")
                }
            }
        }
        .padding(20)
        .background(SheetTheme.heroGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.top, 4)
    }
}

/// Where today falls in the period set on the Settings sheet.
struct PeriodInfo {
    let settings: SheetSettings

    init(_ settings: SheetSettings) { self.settings = settings }

    var dayOfPeriod: (day: Int, total: Int)? {
        guard let start = SheetDate.date(settings.startDate), let end = SheetDate.date(settings.endDate) else { return nil }
        let cal = Calendar.current
        let total = (cal.dateComponents([.day], from: start, to: end).day ?? 0) + 1
        let today = min(cal.startOfDay(for: .now), end)
        let day = max(0, (cal.dateComponents([.day], from: start, to: today).day ?? 0) + 1)
        return (day, total)
    }
}

private struct HabitRatesCard: View {
    let table: SheetTable

    private func symbol(_ habit: String) -> String {
        let h = habit.lowercased()
        if h.contains("office") { return "building.2.fill" }
        if h.contains("breakfast") { return "sunrise.fill" }
        if h.contains("protein") { return "bolt.heart.fill" }
        if h.contains("lunch") { return "fork.knife" }
        if h.contains("workout") { return "figure.run" }
        if h.contains("water") { return "drop.fill" }
        if h.contains("sleep") { return "bed.double.fill" }
        if h.contains("steps") { return "figure.walk" }
        if h.contains("screen") { return "iphone" }
        return "checkmark.circle.fill"
    }

    var body: some View {
        SheetCard {
            VStack(alignment: .leading, spacing: 14) {
                Text("Habit success rates").font(.headline)
                ForEach(Array(table.rows.enumerated()), id: \.offset) { _, row in
                    let rate = SheetFormat.parse(row.last ?? "") ?? 0
                    ProgressRow(title: row.first ?? "", detail: row.last ?? "", fraction: rate,
                                tint: SheetTheme.tint(forRate: rate), symbol: symbol(row.first ?? ""))
                }
            }
        }
    }
}

private struct CategoryCard: View {
    let table: SheetTable

    var body: some View {
        // Rows: Category, QAR, INR, % — only categories with spend, largest first.
        let rows = table.rows
            .filter { $0.count >= 4 && $0[0].uppercased() != "TOTAL" && (SheetFormat.parse($0[1]) ?? 0) > 0 }
            .sorted { (SheetFormat.parse($0[1]) ?? 0) > (SheetFormat.parse($1[1]) ?? 0) }
        let total = table.rows.first { $0.first?.uppercased() == "TOTAL" }
        SheetCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Spend by category").font(.headline)
                    Spacer()
                    if let total, total.count > 1 {
                        Text("\(total[1]) \(Money.main)").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                    }
                }
                if rows.isEmpty {
                    Text("No spending logged yet.").font(.subheadline).foregroundStyle(.secondary)
                }
                ForEach(rows, id: \.self) { row in
                    ProgressRow(title: row[0], detail: "\(row[1]) · \(row[3])",
                                fraction: SheetFormat.parse(row[3]) ?? 0,
                                tint: SheetTheme.categoryColor(row[0]), symbol: SheetTheme.categoryIcon(row[0]))
                }
            }
        }
    }
}

private struct KeyValueCard: View {
    let title: String
    let table: SheetTable

    var body: some View {
        SheetCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(title).font(.headline).padding(.bottom, 2)
                ForEach(Array(table.rows.enumerated()), id: \.offset) { index, row in
                    if index > 0 { Divider() }
                    let value = row.last ?? ""
                    KeyValueRow(key: row.first ?? "", value: value,
                                valueTint: value.hasPrefix("(") ? SheetTheme.bad : .primary)
                }
            }
        }
    }
}

private struct ChartCard<Content: View>: View {
    let title: String
    var subtitle: String?
    @ViewBuilder var content: Content

    var body: some View {
        SheetCard {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline)
                    if let subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
                }
                content.frame(height: 190)
            }
        }
    }
}

private struct HabitScoreChart: View {
    let days: [SheetDay]
    let greatDay: Double?

    var body: some View {
        let points = days.compactMap { day -> (Date, Double)? in
            guard let date = SheetDate.date(day.date), let score = day.score else { return nil }
            return (date, score)
        }
        if points.isEmpty {
            EmptyChart(text: "Log a day to see your score.")
        } else {
            Chart {
                ForEach(points, id: \.0) { point in
                    AreaMark(x: .value("Day", point.0, unit: .day), y: .value("Score", point.1))
                        .foregroundStyle(LinearGradient(colors: [SheetTheme.navy.opacity(0.35), SheetTheme.navy.opacity(0.02)],
                                                        startPoint: .top, endPoint: .bottom))
                        .interpolationMethod(.catmullRom)
                    LineMark(x: .value("Day", point.0, unit: .day), y: .value("Score", point.1))
                        .foregroundStyle(SheetTheme.navy)
                        .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
                        .interpolationMethod(.catmullRom)
                }
                if let greatDay {
                    RuleMark(y: .value("Great day", greatDay))
                        .foregroundStyle(SheetTheme.good.opacity(0.7))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                        .annotation(position: .top, alignment: .trailing) {
                            Text("Great day").font(.caption2).foregroundStyle(SheetTheme.good)
                        }
                }
            }
            .chartYScale(domain: 0...1)
            .chartYAxis { AxisMarks(format: FloatingPointFormatStyle<Double>.Percent().precision(.fractionLength(0)), values: [0, 0.5, 1]) }
        }
    }
}

private struct DailySpendChart: View {
    let expenses: [SheetExpense]
    let days: [SheetDay]
    let cap: Double?

    var body: some View {
        let byDay = Dictionary(grouping: expenses, by: { $0.date ?? "" }).mapValues { $0.reduce(0) { $0 + ($1.qar ?? 0) } }
        let points = days.compactMap { day -> (Date, Double)? in
            guard let date = SheetDate.date(day.date), date <= .now else { return nil }
            return (date, byDay[day.date] ?? 0)
        }
        if points.allSatisfy({ $0.1 == 0 }) {
            EmptyChart(text: "No spending logged yet.")
        } else {
            Chart {
                ForEach(points, id: \.0) { point in
                    BarMark(x: .value("Day", point.0, unit: .day), y: .value("Amount", point.1))
                        .foregroundStyle(point.1 > (cap ?? .infinity) ? SheetTheme.bad : SheetTheme.teal)
                        .cornerRadius(3)
                }
                if let cap {
                    RuleMark(y: .value("Daily cap", cap))
                        .foregroundStyle(.secondary)
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                }
            }
        }
    }
}

private struct WeeklyScoreChart: View {
    let table: SheetTable

    var body: some View {
        let scoreColumn = table.headers.firstIndex { $0.localizedCaseInsensitiveContains("habit score") }
        let rows = table.rows.compactMap { row -> (String, Double)? in
            guard let scoreColumn, row.count > scoreColumn, let score = SheetFormat.parse(row[scoreColumn]) else { return nil }
            return ("W\(row[0])", score)
        }
        if rows.isEmpty {
            EmptyChart(text: "Weeks appear as you log days.")
        } else {
            Chart(rows, id: \.0) { row in
                BarMark(x: .value("Week", row.0), y: .value("Score", row.1))
                    .foregroundStyle(SheetTheme.tint(forRate: row.1).gradient)
                    .cornerRadius(6)
                    .annotation(position: .top) {
                        Text(row.1.formatted(.percent.precision(.fractionLength(0)))).font(.caption2).foregroundStyle(.secondary)
                    }
            }
            .chartYScale(domain: 0...1.1)
            .chartYAxis(.hidden)
        }
    }
}

private struct EmptyChart: View {
    let text: String
    var body: some View {
        Text(text).font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
