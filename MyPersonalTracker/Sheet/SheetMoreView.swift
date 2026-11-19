//
//  SheetMoreView.swift
//  MyPersonalTracker
//
//  Everything that isn't a tab, grouped by what it's for: money, reviews and
//  reports, the journal on this phone. Settings isn't here: it has its own gear
//  on every tab. Opens from the ⋯ button on every tab. Mirrors MoreScreen.kt.
//

import SwiftUI

struct SheetMoreView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss

    /// The app's own screens from before the sheet. Each has its own
    /// navigation stack, so they open as sheets rather than being pushed.
    /// (Learning moved to the Study tab, backed by the sheet.)
    private enum Journal: String, Identifiable {
        case today, history
        var id: Self { self }
    }
    @State private var journal: Journal?
    @State private var converting = false
    @State private var reporting = false
    @State private var bills = false
    @State private var people = false
    @State private var weekReview = false
    @State private var yearReview = false

    var body: some View {
        NavigationStack {
            List {
                Section("Money") {
                    Button { bills = true } label: {
                        MoreRow(symbol: "repeat", title: "Monthly bills", subtitle: "Rent, phone, gym, added automatically", opens: true)
                    }
                    Button { people = true } label: {
                        MoreRow(symbol: "person.2.fill", title: "Friends and family", subtitle: "Money lent, borrowed and given", opens: true)
                    }
                    NavigationLink { RenewalsScreen(store: store) } label: {
                        MoreRow(symbol: "calendar.badge.exclamationmark", title: "Renewals", subtitle: "Reminded before documents expire")
                    }
                    NavigationLink { CardsScreen() } label: {
                        MoreRow(symbol: "creditcard.fill", title: "Cards", subtitle: "Kept on this iPhone only")
                    }
                    if !Money.single { Button { converting = true } label: {
                        MoreRow(symbol: "dollarsign.arrow.circlepath", title: "Currency converter", subtitle: "Your own rates, works offline", opens: true)
                    } }
                }
                Section("Reviews and reports") {
                    Button { weekReview = true } label: {
                        MoreRow(symbol: "chart.bar.doc.horizontal", title: "Week in review", subtitle: "How this week went", opens: true)
                    }
                    Button { reporting = true } label: {
                        MoreRow(symbol: "doc.richtext.fill", title: "Monthly report", subtitle: "Share a month as a PDF", opens: true)
                    }
                    Button { yearReview = true } label: {
                        MoreRow(symbol: "sparkles", title: "Year in review", subtitle: "Your year, added up", opens: true)
                    }
                    NavigationLink { SummaryTableScreen(store: store) } label: {
                        MoreRow(symbol: "tablecells", title: "Tables", subtitle: "Week by week and month by month")
                    }
                }
                Section {
                    Button { journal = .today } label: { MoreRow(symbol: "sun.max.fill", title: "Journal", subtitle: "To-dos, focus time and mood", opens: true) }
                    Button { journal = .history } label: { MoreRow(symbol: "clock.arrow.circlepath", title: "Journal history", opens: true) }
                } header: {
                    Text("Journal")
                } footer: {
                    Text("Kept on this iPhone, not in your sheet.")
                }
            }
            .sheet(isPresented: $weekReview) { WeeklyReviewView(store: store) }
            .sheet(isPresented: $yearReview) { YearReviewView(store: store) }
            .navigationTitle("More")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .sheet(isPresented: $converting) { CurrencyConverterView() }
            .sheet(isPresented: $reporting) { MonthlyReportView(store: store) }
            .sheet(isPresented: $bills) { RecurringListView(store: store) }
            .sheet(isPresented: $people) { PeopleView(store: store) }
            .sheet(item: $journal) { screen in
                switch screen {
                case .today: TodayView()
                case .history: HistoryView()
                }
            }
        }
    }
}

private struct MoreRow: View {
    let symbol: String
    let title: String
    var subtitle: String?
    /// Opens over More (a button), so it draws the chevron a pushed row gets from its NavigationLink.
    var opens = false

    var body: some View {
        HStack(spacing: 12) {
            IconBadge(symbol: symbol, tint: SheetTheme.navy, size: 32)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.body).foregroundStyle(Color(.label))
                if let subtitle { Text(subtitle).font(.caption).foregroundStyle(Color(.secondaryLabel)) }
            }
            if opens {
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Color(.tertiaryLabel))
                    .accessibilityHidden(true)
            }
        }
    }
}

private struct SummaryTableScreen: View {
    let store: SheetStore
    @State private var monthly = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Picker("Table", selection: $monthly) {
                    Text("Week by week").tag(false)
                    Text("Month by month").tag(true)
                }
                .pickerStyle(.segmented)
                if let snapshot = store.snapshot {
                    SheetTableView(table: monthly ? snapshot.monthly : snapshot.weekly, minColumnWidth: 72)
                } else {
                    NotConnectedHint()
                }
                Label(monthly ? "Habit percentages are days marked Y divided by days you logged that habit, so skipping a day doesn't punish the rate."
                              : "Weekly Budget = Daily Spend Limit × 7. Variance is budget minus actual: green is money left over, red is an overspend.",
                      systemImage: "info.circle").font(.caption).foregroundStyle(.secondary)
            }
            .padding(16)
        }
        .background(SheetTheme.page)
        .navigationTitle("Tables")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await store.refresh() }
    }
}

/// Picks the workbook's main and home currencies and applies them to the sheet,
/// which relabels every header. Optionally sets the exchange rate from live rates.
struct CurrencySection: View {
    let store: SheetStore
    let settings: SheetSettings
    let onSaved: () -> Void

    @State private var oneCurrency: Bool
    @State private var main: String
    @State private var home: String
    @State private var rate: Double?
    @State private var confirming = false
    @State private var failed: String?
    @State private var book = CurrencyBook.shared

    init(store: SheetStore, settings: SheetSettings, onSaved: @escaping () -> Void) {
        self.store = store
        self.settings = settings
        self.onSaved = onSaved
        _oneCurrency = State(initialValue: settings.mainCurrency == settings.homeCurrency)
        _main = State(initialValue: settings.mainCurrency)
        _home = State(initialValue: settings.homeCurrency)
        _rate = State(initialValue: settings.rateIsCurrent ? settings.rate : nil)
    }

    private var chosenHome: String { oneCurrency ? main : home }
    private var changed: Bool { main != settings.mainCurrency || chosenHome != settings.homeCurrency }
    /// Today's rate for the chosen pair (independent of the sheet's own rate).
    private var liveRate: Double? { book.liveRate(from: main, to: chosenHome) }
    private var canApply: Bool { changed && (oneCurrency || (main != home && (rate ?? 0) > 0)) }

    var body: some View {
        Section {
            Toggle(isOn: $oneCurrency) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("I only use one currency")
                    Text("E.g. everything in INR — no converter, no exchange rate").font(.caption).foregroundStyle(.secondary)
                }
            }
            .tint(SheetTheme.navy)
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(oneCurrency ? "Currency" : "Main currency")
                    Text(oneCurrency ? "Everything you earn, spend and send" : "What you earn and spend in").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                CurrencyMenu(selection: $main)
            }
            if !oneCurrency {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Home currency")
                        Text("What money sent home is shown in").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    CurrencyMenu(selection: $home)
                }
                if changed {
                    NumberRow(title: "Exchange rate", unit: "\(chosenHome) per \(main)", value: $rate,
                              hint: liveRate.map { "Today: 1 \(main) = \(CurrencyBook.format($0)) \(chosenHome)" } ?? "Offline — type the rate you use")
                }
            }
            // The sheet's rate belongs to an older pair: say so and offer today's.
            if !changed, !oneCurrency, !settings.rateIsCurrent {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Your exchange rate (\(SheetFormat.number(settings.rate, digits: 2))) was set for \((settings.rateFor ?? "").replacingOccurrences(of: "/", with: " → ")), not \(main) → \(home). Until you update it, the app uses today's rate.",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.caption).foregroundStyle(SheetTheme.warn)
                    if let liveRate {
                        Button("Use today's rate: 1 \(main) = \(CurrencyBook.format(liveRate)) \(home)") {
                            Task { if await store.saveSettings(["RATE": (liveRate * 10_000).rounded() / 10_000]) { onSaved() } }
                        }
                        .font(.subheadline.weight(.semibold))
                    }
                }
            }
            if changed {
                Button { confirming = true } label: {
                    HStack {
                        Spacer()
                        if store.isSaving { ProgressView().tint(.white) } else {
                            Text(oneCurrency ? "Use only \(main)" : "Apply \(main) · \(chosenHome) to the sheet").font(.headline)
                        }
                        Spacer()
                    }
                }
                .listRowBackground(canApply ? SheetTheme.navy : Color(.systemGray3))
                .foregroundStyle(.white)
                .disabled(store.isSaving || !canApply)
                .popup(isPresented: $confirming, Popup(title: oneCurrency ? "Use only \(main)?" : "Switch to \(main) and \(chosenHome)?",
                                                       message: "Existing amounts keep their numbers — they won't be converted.",
                                                       symbol: "arrow.left.arrow.right.circle.fill", confirmTitle: "Switch currency", onConfirm: {
                    Task {
                        failed = nil
                        if await store.saveCurrency(main: main, home: chosenHome, rate: oneCurrency ? 1 : rate) { onSaved() }
                        else { failed = store.errorMessage ?? "Couldn't change the currency. Check your connection and try again." }
                    }
                }))
                if let failed {
                    Label(failed, systemImage: "exclamationmark.triangle.fill").font(.footnote).foregroundStyle(SheetTheme.bad)
                }
            }
        } header: {
            Text("Currency")
        } footer: {
            Text(!oneCurrency && main == home ? "Pick two different currencies, or switch on “I only use one currency”."
                 : "Every sheet and screen switches to this. Amounts you already entered are not converted, so change it before you start logging.")
        }
        .task { await book.refreshIfStale() }
        .onChange(of: main) { prefillRate() }
        .onChange(of: home) { prefillRate() }
        .onChange(of: book.isRefreshing) { prefillRate() }
        .onChange(of: settings.mainCurrency) { _, new in main = new }
        .onChange(of: settings.homeCurrency) { _, new in home = new; oneCurrency = new == settings.mainCurrency }
    }

    /// A new pair starts with today's rate (you can change it).
    private func prefillRate() {
        guard changed, !oneCurrency else { return }
        if let liveRate { rate = (liveRate * 10_000).rounded() / 10_000 }
    }
}
