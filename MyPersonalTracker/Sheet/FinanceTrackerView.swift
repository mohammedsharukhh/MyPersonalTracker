//
//  FinanceTrackerView.swift
//  MyPersonalTracker
//
//  The Finance Tracker sheet: the expense ledger (one row per purchase) and
//  the sheet's day-by-day budget control beside it.
//

import SwiftUI

struct FinanceTrackerView: View {
    let store: SheetStore

    private enum Tab: String, CaseIterable { case ledger = "Ledger", budget = "Budget", savings = "Savings", people = "People" }
    @State private var tab: Tab = {
        #if DEBUG
        // `-financeTab Budget` opens that section (screenshots).
        if let raw = UserDefaults.standard.string(forKey: "financeTab"), let t = Tab(rawValue: raw) { return t }
        #endif
        return .ledger
    }()
    @State private var editing: SheetExpense?
    @State private var adding = false
    @State private var puttingAside: SplitBucket?
    @State private var converting = false
    @State private var search = ""
    @State private var categoryFilter: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    if let error = store.errorMessage, store.isConfigured {
                        SheetErrorBanner(message: error) { Task { await store.refresh() } }
                    }
                    SyncStatusBar(store: store)
                    if let snapshot = store.snapshot {
                        MonthSummaryCard(settings: snapshot.settings, expenses: snapshot.expenses)

                        Picker("View", selection: $tab) {
                            ForEach(Tab.allCases, id: \.self) { Text($0.rawValue) }
                        }
                        .pickerStyle(.segmented)
                        .padding(.vertical, 4)

                        switch tab {
                        case .ledger: ledger(snapshot)
                        case .budget:
                            SalarySplitCard(store: store, snapshot: snapshot)
                            LoansSection(store: store, snapshot: snapshot)
                            BudgetList(table: snapshot.budget, cap: snapshot.settings.dailyLimit)
                        case .savings: SavingsContent(store: store, snapshot: snapshot)
                        case .people: PeopleContent(store: store, snapshot: snapshot)
                        }
                    } else if store.needsConnection {
                        NotConnectedHint()
                    } else {
                        SheetLoadingView(progress: store.loadProgress).padding(.top, 60)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 90)
            }
            .containerBackground(SheetTheme.pageWash, for: .navigation)
            .navigationTitle("Finance")
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search expenses")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) { MoreButton(); SettingsButton() }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Add expense", systemImage: "cart.badge.plus") { adding = true }
                        Button("Add to Savings / SIP", systemImage: SplitBucket.savings.symbol) { puttingAside = .savings }
                        Button("Add to Emergency fund", systemImage: SplitBucket.emergency.symbol) { puttingAside = .emergency }
                    } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Add")
                    .disabled(store.snapshot == nil)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if !Money.single {
                        Button("Currency converter", systemImage: "dollarsign.arrow.circlepath") { converting = true }
                    }
                }
            }
            .sheet(isPresented: $converting) { CurrencyConverterView() }
            .refreshable { await store.refresh() }
            .sheet(isPresented: $adding) { SheetExpenseEditor(store: store, expense: nil) }
            .sheet(item: $puttingAside) { PutAsideSheet(store: store, bucket: $0) }
            .sheet(item: $editing) { SheetExpenseEditor(store: store, expense: $0) }
        }
    }

    private func matches(_ expense: SheetExpense) -> Bool {
        if let categoryFilter, expense.category != categoryFilter { return false }
        let query = search.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return true }
        return [expense.description, expense.category, expense.method, expense.notes, SheetDate.short(expense.date)]
            .compactMap { $0 }
            .contains { $0.localizedCaseInsensitiveContains(query) }
    }

    @ViewBuilder
    private func ledger(_ snapshot: SheetSnapshot) -> some View {
        let used = Array(Set(snapshot.expenses.compactMap(\.category))).sorted()
        if used.count > 1 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    FilterChip(title: "All", symbol: "line.3.horizontal.decrease", tint: SheetTheme.navy, selected: categoryFilter == nil) {
                        categoryFilter = nil
                    }
                    ForEach(used, id: \.self) { category in
                        FilterChip(title: category, symbol: SheetTheme.categoryIcon(category), tint: SheetTheme.categoryColor(category),
                                   selected: categoryFilter == category) {
                            categoryFilter = categoryFilter == category ? nil : category
                        }
                    }
                }
            }
        }
        let filtered = snapshot.expenses.filter(matches)
        let isFiltering = categoryFilter != nil || !search.trimmingCharacters(in: .whitespaces).isEmpty
        if isFiltering, !filtered.isEmpty {
            HStack {
                Text("\(filtered.count) \(filtered.count == 1 ? "expense" : "expenses")")
                Spacer()
                Text("\(SheetFormat.qar(filtered.reduce(0) { $0 + ($1.qar ?? 0) })) \(Money.main)").monospacedDigit()
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(SheetTheme.navy)
            .padding(12)
            .background(SheetTheme.navy.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        let grouped = Dictionary(grouping: filtered, by: { $0.date ?? "" })
        let dates = grouped.keys.sorted(by: >)
        if dates.isEmpty {
            SheetCard {
                Label(isFiltering ? "No expenses match." : "No expenses yet. Tap + to log one.",
                      systemImage: isFiltering ? "magnifyingglass" : "tray")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        ForEach(dates, id: \.self) { date in
            let items = grouped[date] ?? []
            let total = items.reduce(0) { $0 + ($1.qar ?? 0) }
            let over = total > (snapshot.settings.dailyLimit ?? .infinity)
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(SheetDate.weekdayDay(date)).font(.subheadline.weight(.semibold))
                    Spacer()
                    Text("\(SheetFormat.qar(total)) \(Money.main)").font(.subheadline.weight(.semibold)).monospacedDigit()
                    if !isFiltering { StatusPill(text: over ? "OVER" : "OK") }
                }
                .padding(.horizontal, 4)
                .padding(.top, 6)
                VStack(spacing: 0) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, expense in
                        if index > 0 { Divider().padding(.leading, 62) }
                        Button { editing = expense } label: { ExpenseRow(expense: expense) }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button("Delete", systemImage: "trash", role: .destructive) {
                                    Task { _ = await store.deleteExpense(row: expense.row) }
                                }
                            }
                    }
                }
                .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
    }
}

/// A selectable capsule used for filters.
struct FilterChip: View {
    let title: String
    let symbol: String
    let tint: Color
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .foregroundStyle(selected ? .white : tint)
                .background(selected ? AnyShapeStyle(tint) : AnyShapeStyle(SheetTheme.card), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// This month's spend against the monthly limit, plus rate and daily cap.
private struct MonthSummaryCard: View {
    let settings: SheetSettings
    let expenses: [SheetExpense]

    private var todaySpend: Double { expenses.filter { $0.date == SheetDate.today }.reduce(0) { $0 + ($1.qar ?? 0) } }
    private var todayChip: some View { HeroChip(symbol: "calendar", text: "Today \(SheetFormat.number(todaySpend, digits: 2))") }
    private var capChip: some View { HeroChip(symbol: "gauge.with.dots.needle.33percent", text: "Cap \(SheetFormat.number(settings.dailyLimit))/day") }
    @ViewBuilder private var rateChip: some View {
        if !Money.single {
            HeroChip(symbol: "arrow.left.arrow.right", text: "1 \(Money.main) = \(SheetFormat.number(settings.rate, digits: 2)) \(Money.home)")
        }
    }

    var body: some View {
        let month = String(SheetDate.today.prefix(7))
        let monthSpend = expenses.filter { ($0.date ?? "").hasPrefix(month) }.reduce(0) { $0 + ($1.qar ?? 0) }
        let limit = settings.monthlyLimit ?? 0
        let left = limit - monthSpend
        let fraction = limit > 0 ? monthSpend / limit : 0

        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Spent this month").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(SheetFormat.qar(monthSpend))
                            .font(.system(.largeTitle, design: .rounded, weight: .bold))
                            .monospacedDigit()
                        Text(Money.main).font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    }
                    .foregroundStyle(.white)
                    Text(Money.single ? " " : "≈ \(Money.homeAmount(monthSpend * (settings.rate ?? 0)))")
                        .font(.footnote).foregroundStyle(.white.opacity(0.8))
                }
                Spacer()
                ZStack {
                    ScoreRing(fraction: fraction, lineWidth: 7, tint: fraction > 1 ? Color(hex: 0xF87171) : Color(hex: 0x4ADE80))
                    Text(fraction.formatted(.percent.precision(.fractionLength(0))))
                        .font(.system(.subheadline, design: .rounded, weight: .bold))
                        .foregroundStyle(.white)
                }
                .frame(width: 62, height: 62)
            }

            VStack(alignment: .leading, spacing: 6) {
                Capsule().fill(.white.opacity(0.18)).frame(height: 8)
                    .overlay(alignment: .leading) {
                        GeometryReader { geo in
                            Capsule().fill(fraction > 1 ? Color(hex: 0xF87171) : Color(hex: 0x4ADE80))
                                .frame(width: geo.size.width * min(max(fraction, 0), 1))
                        }
                    }
                Text(left >= 0 ? "\(SheetFormat.qar(left)) \(Money.main) left of \(SheetFormat.qar(limit))"
                               : "\(SheetFormat.qar(-left)) \(Money.main) over the \(SheetFormat.qar(limit)) limit")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.9))
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: 8) { todayChip; capChip; rateChip }
                // Not all on one line: today and the cap together, the rate below.
                VStack(alignment: .leading, spacing: 8) { HStack(spacing: 8) { todayChip; capChip }; rateChip }
                VStack(alignment: .leading, spacing: 8) { todayChip; capChip; rateChip }
            }
        }
        .padding(20)
        .background(SheetTheme.heroGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.top, 4)
    }
}

private struct ExpenseRow: View {
    let expense: SheetExpense

    var body: some View {
        HStack(spacing: 12) {
            IconBadge(symbol: SheetTheme.categoryIcon(expense.category), tint: SheetTheme.categoryColor(expense.category), size: 38)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 5) {
                    Text(expense.description.flatMap { $0.isEmpty ? nil : $0 } ?? expense.category ?? "Expense")
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    if expense.isPending {
                        Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky)
                            .accessibilityLabel("Waiting to sync")
                    }
                }
                Text([expense.category, expense.method].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                if let notes = expense.notes, !notes.isEmpty {
                    Text(notes).font(.caption2).foregroundStyle(.tertiary).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 3) {
                Text(SheetFormat.qar(expense.qar)).font(.subheadline.weight(.bold)).monospacedDigit()
                if !Money.single {
                    Text("\(Money.homeAmount(expense.inr))").font(.caption).foregroundStyle(.secondary).monospacedDigit()
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .contentShape(Rectangle())
    }
}

/// The sheet's Daily Budget Control block, one card row per day.
/// The sheet's day-by-day check against the daily cap — only the days that matter:
/// up to today, with spending (plus today), newest first. OVER is the only flag.
private struct BudgetList: View {
    let table: SheetTable
    let cap: Double?
    @State private var showAll = false

    private struct Day: Identifiable {
        let id: Int
        let date: Date
        let label: String
        let spent: Double
        let over: Bool
        let monthToDate: String
        let left: String
    }

    private static let parser: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "dd-MMM-yy"
        return f
    }()

    private var days: [Day] {
        let today = Calendar.current.startOfDay(for: .now)
        return table.rows.enumerated().compactMap { i, row -> Day? in
            guard let date = Self.parser.date(from: row[safe: 0] ?? ""), date <= today else { return nil }
            let spent = SheetFormat.parse(row[safe: 1] ?? "") ?? 0
            guard spent > 0 || Calendar.current.isDateInToday(date) else { return nil }
            return Day(id: i, date: date,
                       label: Calendar.current.isDateInToday(date) ? "Today" : date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)),
                       spent: spent, over: (row[safe: 3] ?? "").uppercased() == "OVER",
                       monthToDate: row[safe: 4] ?? "—", left: row[safe: 5] ?? "")
        }
        .sorted { $0.date > $1.date }
    }

    var body: some View {
        let days = self.days
        let month = Calendar.current.dateComponents([.year, .month], from: .now)
        let overThisMonth = days.filter { $0.over && Calendar.current.dateComponents([.year, .month], from: $0.date) == month }.count
        let shown = showAll ? days : Array(days.prefix(14))

        VStack(alignment: .leading, spacing: 2) {
            Text("Daily spending").font(.headline)
            Text([cap.map { "Cap \(SheetFormat.qar($0)) a day" },
                  overThisMonth > 0 ? "\(overThisMonth) day\(overThisMonth == 1 ? "" : "s") over this month" : "No days over this month"]
                .compactMap { $0 }.joined(separator: " · "))
                .font(.caption).foregroundStyle(overThisMonth > 0 ? SheetTheme.bad : .secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)

        if days.isEmpty {
            SheetCard { Text("No spending yet.").font(.subheadline).foregroundStyle(.secondary) }
        } else {
            VStack(spacing: 0) {
                ForEach(shown) { day in
                    if day.id != shown.first?.id { Divider().padding(.leading, 16) }
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(day.label).font(.subheadline.weight(.semibold))
                            Text("Month so far \(day.monthToDate) · left \(day.left)").font(.caption)
                                .foregroundStyle(day.left.hasPrefix("(") ? SheetTheme.bad : .secondary)
                        }
                        Spacer()
                        Text(SheetFormat.qar(day.spent)).font(.subheadline.weight(.bold)).monospacedDigit()
                            .foregroundStyle(day.over ? SheetTheme.bad : .primary)
                        if day.over { StatusPill(text: "OVER", tint: SheetTheme.bad) }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                }
                if days.count > 14 {
                    Divider()
                    Button(showAll ? "Show fewer" : "Show all \(days.count) days") { withAnimation { showAll.toggle() } }
                        .font(.subheadline.weight(.semibold)).padding(.vertical, 12)
                }
            }
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            Text("Days you spent money, newest first. OVER means that day went above your daily cap; “left” is what remains of this month's spend limit.")
                .font(.footnote).foregroundStyle(.secondary)
                .padding(.horizontal, 4)
        }
    }
}
