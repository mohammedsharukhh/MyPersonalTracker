//
//  Insights.swift
//  MyPersonalTracker
//
//  Plain-language observations worked out from the sheet's data ("Food is up
//  40% on last week"), and a one-page monthly report that can be shared as a PDF.
//

import SwiftUI

struct Insight: Identifiable, Hashable {
    let id: String
    let symbol: String
    let tone: Tone
    let text: String

    enum Tone { case good, bad, neutral }
}

/// Works the insights out from one snapshot. Pure, so it's easy to test.
enum InsightEngine {
    static func insights(for s: SheetSnapshot, today: Date = .now, calendar: Calendar = .current) -> [Insight] {
        var out: [Insight] = []
        let main = s.settings.mainCurrency
        let todayKey = SheetDate.string(today)
        func spend(_ from: Date, _ to: Date) -> [SheetExpense] {
            let a = SheetDate.string(from), b = SheetDate.string(to)
            return s.expenses.filter { ($0.date ?? "") >= a && ($0.date ?? "") <= b }
        }
        func total(_ items: [SheetExpense]) -> Double { items.reduce(0) { $0 + ($1.qar ?? 0) } }

        // 1. This week so far against the same days last week.
        let weekday = (calendar.component(.weekday, from: today) + 5) % 7          // Monday = 0
        if let weekStart = calendar.date(byAdding: .day, value: -weekday, to: calendar.startOfDay(for: today)),
           let lastStart = calendar.date(byAdding: .day, value: -7, to: weekStart),
           let lastSameDay = calendar.date(byAdding: .day, value: -7, to: today) {
            let now = total(spend(weekStart, today))
            let before = total(spend(lastStart, lastSameDay))
            if before > 0, now > 0 {
                let change = (now - before) / before
                if abs(change) >= 0.15 {
                    out.append(Insight(id: "week", symbol: change > 0 ? "arrow.up.right.circle.fill" : "arrow.down.right.circle.fill",
                                       tone: change > 0 ? .bad : .good,
                                       text: "Spending is \(change > 0 ? "up" : "down") \(Int((abs(change) * 100).rounded()))% on this point last week (\(SheetFormat.qar(now)) vs \(SheetFormat.qar(before)) \(main))."))
                }
            }
        }

        // 2. Where this month's money went.
        let month = String(todayKey.prefix(7))
        let monthItems = s.expenses.filter { ($0.date ?? "").hasPrefix(month) }
        let monthTotal = total(monthItems)
        if monthTotal > 0 {
            let byCategory = Dictionary(grouping: monthItems, by: { $0.category ?? "Other" }).mapValues(total)
            if let top = byCategory.max(by: { $0.value < $1.value }), byCategory.count > 1 {
                out.append(Insight(id: "category", symbol: SheetTheme.categoryIcon(top.key), tone: .neutral,
                                   text: "\(top.key) is your biggest spend this month — \(SheetFormat.qar(top.value)) \(main), \(Int((top.value / monthTotal * 100).rounded()))% of the total."))
            }
        }

        // 3. Month on pace against the monthly limit.
        if let limit = s.settings.monthlyLimit, limit > 0, monthTotal > 0,
           let range = calendar.range(of: .day, in: .month, for: today) {
            let day = calendar.component(.day, from: today)
            let projected = monthTotal / Double(day) * Double(range.count)
            if monthTotal > limit {
                out.append(Insight(id: "pace", symbol: "exclamationmark.triangle.fill", tone: .bad,
                                   text: "You're \(SheetFormat.qar(monthTotal - limit)) \(main) over this month's limit already."))
            } else if projected > limit * 1.05 {
                out.append(Insight(id: "pace", symbol: "gauge.with.dots.needle.67percent", tone: .bad,
                                   text: "At this pace you'll spend about \(SheetFormat.qar(projected)) \(main) this month — over the \(SheetFormat.number(limit)) limit."))
            } else {
                out.append(Insight(id: "pace", symbol: "checkmark.seal.fill", tone: .good,
                                   text: "On pace for about \(SheetFormat.qar(projected)) \(main) this month — inside your \(SheetFormat.number(limit)) limit."))
            }
        }

        // 4. Strongest and weakest habit (the sheet's success-rate table).
        if let rates = s.dashboardTables.first, rates.rows.count > 1 {
            let scored = rates.rows.compactMap { row -> (String, Double)? in
                guard let name = row.first, let rate = SheetFormat.parse(row.last ?? "") else { return nil }
                return (name, rate)
            }
            if let best = scored.max(by: { $0.1 < $1.1 }), let worst = scored.min(by: { $0.1 < $1.1 }), best.1 - worst.1 >= 0.2 {
                out.append(Insight(id: "habits", symbol: "target", tone: .neutral,
                                   text: "\(worst.0) is the habit to work on (\(SheetFormat.percent(worst.1, 0))). \(best.0) is your strongest (\(SheetFormat.percent(best.1, 0)))."))
            }
        }

        // 5. Unlogged days in the last week.
        let weekAgo = SheetDate.string(calendar.date(byAdding: .day, value: -7, to: today) ?? today)
        let missed = s.days.filter { $0.date >= weekAgo && $0.date < todayKey && !$0.isLogged }.count
        if missed >= 2 {
            out.append(Insight(id: "missed", symbol: "calendar.badge.exclamationmark", tone: .bad,
                               text: "\(missed) of the last 7 days aren't logged — the Daily tab's “Not logged” filter finds them."))
        }

        // 6. Days over the cap this week.
        if let cap = s.settings.dailyLimit, cap > 0 {
            let byDay = Dictionary(grouping: s.expenses.filter { ($0.date ?? "") >= weekAgo && ($0.date ?? "") <= todayKey }, by: { $0.date ?? "" }).mapValues(total)
            let over = byDay.values.filter { $0 > cap }.count
            if over == 0, !byDay.isEmpty {
                out.append(Insight(id: "cap", symbol: "hand.thumbsup.fill", tone: .good, text: "No days over your daily cap in the last week."))
            } else if over >= 3 {
                out.append(Insight(id: "cap", symbol: "flame.fill", tone: .bad, text: "\(over) days over your \(SheetFormat.number(cap)) \(main) daily cap in the last week."))
            }
        }
        return Array(out.prefix(5))
    }
}

struct InsightsCard: View {
    let snapshot: SheetSnapshot

    var body: some View {
        let insights = InsightEngine.insights(for: snapshot)
        if !insights.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 14) {
                    Label("Insights", systemImage: "sparkles").font(.headline)
                    ForEach(insights) { insight in
                        HStack(alignment: .top, spacing: 12) {
                            IconBadge(symbol: insight.symbol, tint: tint(insight.tone), size: 30)
                            Text(insight.text).font(.subheadline).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
        }
    }

    private func tint(_ tone: Insight.Tone) -> Color {
        switch tone {
        case .good: SheetTheme.good
        case .bad: SheetTheme.bad
        case .neutral: SheetTheme.indigo
        }
    }
}

// MARK: - Monthly report

struct MonthlyReport {
    let month: SalaryMonth?
    let label: String
    let currency: String
    let spent: Double
    let categories: [(String, Double)]
    let daysLogged: Int
    let daysInData: Int
    let averageScore: Double?
    let excellentDays: Int
    let overCapDays: Int
    let biggest: SheetExpense?

    init(snapshot s: SheetSnapshot, month key: String) {
        month = s.salary.first { $0.month == key }
        label = month?.label ?? key
        currency = s.settings.mainCurrency
        let items = s.expenses.filter { ($0.date ?? "").hasPrefix(key) }
        spent = items.reduce(0) { $0 + ($1.qar ?? 0) }
        categories = Dictionary(grouping: items, by: { $0.category ?? "Other" })
            .mapValues { $0.reduce(0) { $0 + ($1.qar ?? 0) } }
            .sorted { $0.value > $1.value }
            .map { ($0.key, $0.value) }
        let days = s.days.filter { $0.date.hasPrefix(key) && $0.date <= SheetDate.today }
        daysInData = days.count
        let logged = days.filter(\.isLogged)
        daysLogged = logged.count
        averageScore = logged.isEmpty ? nil : logged.reduce(0) { $0 + ($1.score ?? 0) } / Double(logged.count)
        excellentDays = logged.filter { $0.status == "Excellent" }.count
        let cap = s.settings.dailyLimit ?? .infinity
        overCapDays = Dictionary(grouping: items, by: { $0.date ?? "" }).values
            .filter { $0.reduce(0) { $0 + ($1.qar ?? 0) } > cap }.count
        biggest = items.max { ($0.qar ?? 0) < ($1.qar ?? 0) }
    }
}

/// The report page itself — also what's rendered into the PDF.
struct MonthlyReportPage: View {
    let report: MonthlyReport

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Life & Finance").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                Text(report.label).font(.system(size: 30, weight: .bold, design: .rounded)).foregroundStyle(.white)
                Text("Monthly report · \(report.currency)").font(.caption).foregroundStyle(.white.opacity(0.75))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
            .background(SheetTheme.heroGradient, in: RoundedRectangle(cornerRadius: 20, style: .continuous))

            Grid(horizontalSpacing: 12, verticalSpacing: 12) {
                GridRow {
                    tile("Income", report.month?.totalIncome, SheetTheme.teal)
                    tile("Spent", report.spent, SheetTheme.bad)
                }
                GridRow {
                    tile("Sent home", report.month?.remittanceQar, SheetTheme.sky)
                    tile("Saved", report.month?.netSavings, SheetTheme.good)
                }
            }

            section("Habits") {
                KeyValueRow(key: "Days logged", value: "\(report.daysLogged) of \(report.daysInData)")
                KeyValueRow(key: "Average daily score", value: report.averageScore.map { SheetFormat.percent($0) } ?? "—")
                KeyValueRow(key: "Excellent days", value: "\(report.excellentDays)")
            }

            section("Spending") {
                KeyValueRow(key: "Savings rate", value: report.month?.savingsRate.map { SheetFormat.percent($0) } ?? "—")
                KeyValueRow(key: "Days over the daily cap", value: "\(report.overCapDays)",
                            valueTint: report.overCapDays > 0 ? SheetTheme.bad : .primary)
                if let biggest = report.biggest {
                    KeyValueRow(key: "Biggest expense", value: "\(biggest.description ?? biggest.category ?? "") · \(SheetFormat.qar(biggest.qar))")
                }
                ForEach(report.categories.prefix(6), id: \.0) { category, amount in
                    ProgressRow(title: category, detail: SheetFormat.qar(amount), fraction: report.spent > 0 ? amount / report.spent : 0,
                                tint: SheetTheme.categoryColor(category), symbol: SheetTheme.categoryIcon(category))
                }
                if report.categories.isEmpty { Text("No expenses this month.").font(.subheadline).foregroundStyle(.secondary) }
            }

            Text("Generated \(Date.now.formatted(date: .abbreviated, time: .shortened)) from your Google Sheet.")
                .font(.caption2).foregroundStyle(.secondary)
        }
        .padding(20)
    }

    private func tile(_ title: String, _ value: Double?, _ tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            Text(SheetFormat.qar(value)).font(.system(.title3, design: .rounded, weight: .bold)).foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct MonthlyReportView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var month: String = String(SheetDate.today.prefix(7))
    @State private var pdf: URL?

    var body: some View {
        NavigationStack {
            ScrollView {
                if let snapshot = store.snapshot {
                    MonthlyReportPage(report: MonthlyReport(snapshot: snapshot, month: month))
                } else {
                    NotConnectedHint().padding()
                }
            }
            .background(SheetTheme.page)
            .navigationTitle("Monthly report")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .principal) {
                    Picker("Month", selection: $month) {
                        ForEach(months, id: \.self) { key in
                            Text(store.snapshot?.salary.first { $0.month == key }?.label ?? key).tag(key)
                        }
                    }
                    .pickerStyle(.menu)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if let pdf {
                        ShareLink(item: pdf) { Label("Share PDF", systemImage: "square.and.arrow.up") }
                    }
                }
            }
            .task(id: month) { pdf = renderPDF() }
        }
        .tint(SheetTheme.navy)
    }

    /// Months that have any data, newest first.
    private var months: [String] {
        guard let s = store.snapshot else { return [month] }
        let keys = Set(s.expenses.compactMap { $0.date.map { String($0.prefix(7)) } } + s.days.filter(\.isLogged).map { String($0.date.prefix(7)) })
            .union([String(SheetDate.today.prefix(7))])
        return keys.sorted(by: >)
    }

    private func renderPDF() -> URL? {
        guard let snapshot = store.snapshot else { return nil }
        let page = MonthlyReportPage(report: MonthlyReport(snapshot: snapshot, month: month))
            .frame(width: 595)                   // A4 width in points
            .background(SheetTheme.page)
            .environment(\.colorScheme, .light)
        let renderer = ImageRenderer(content: page)
        renderer.proposedSize = ProposedViewSize(width: 595, height: nil)
        let label = snapshot.salary.first { $0.month == month }?.label ?? month
        let url = FileManager.default.temporaryDirectory.appending(path: "Life & Finance \(label).pdf")
        renderer.render { size, draw in
            var box = CGRect(origin: .zero, size: size)
            guard let context = CGContext(url as CFURL, mediaBox: &box, nil) else { return }
            context.beginPDFPage(nil)
            draw(context)
            context.endPDFPage()
            context.closePDF()
        }
        return url
    }
}
