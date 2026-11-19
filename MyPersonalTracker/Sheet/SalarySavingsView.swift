//
//  SalarySavingsView.swift
//  MyPersonalTracker
//
//  The Salary & Savings sheet: income, remittance to India and savings rate by
//  month. Salary, other income and remittance are inputs; expenses come from
//  the ledger and everything else is the sheet's formulas.
//

import SwiftUI

struct SalarySavingsView: View {
    let store: SheetStore
    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    if let error = store.errorMessage, store.isConfigured {
                        SheetErrorBanner(message: error) { Task { await store.refresh() } }
                    }
                    SyncStatusBar(store: store)
                    if let snapshot = store.snapshot {
                        SavingsContent(store: store, snapshot: snapshot)
                    } else if store.needsConnection {
                        NotConnectedHint()
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(SheetTheme.page)
            .navigationTitle("Savings")
            .refreshable { await store.refresh() }
        }
    }
}

/// Savings totals and the month cards — its own screen, and the Savings section of Finance.
struct SavingsContent: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var editing: SalaryMonth?
    @State private var putting: SplitBucket?

    var body: some View {
        TotalsCard(snapshot: snapshot)
        HStack(spacing: 10) {
            ForEach([SplitBucket.savings, .emergency], id: \.self) { bucket in
                Button { putting = bucket } label: {
                    VStack(spacing: 4) {
                        Image(systemName: "plus.circle.fill").font(.title3)
                        Text("Add to \(bucket == .savings ? "Savings / SIP" : "Emergency fund")")
                            .font(.subheadline.weight(.semibold)).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(bucket == .savings ? SheetTheme.navy : SplitBucket.emergency.tint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
            }
        }
        .sheet(item: $putting) { PutAsideSheet(store: store, bucket: $0) }
        RecentSavings(store: store, entries: snapshot.setAside ?? [])
        SavingsGoalsSection(store: store, snapshot: snapshot)
        SectionTitle(title: "Months", subtitle: "Tap a month to enter salary, other income and money sent home.")
        if snapshot.salary.isEmpty {
            SheetCard { Text("No months in the period yet.").font(.subheadline).foregroundStyle(.secondary) }
        }
        ForEach(snapshot.salary.reversed()) { month in
            Button { editing = month } label: {
                MonthCard(month: month, snapshot: snapshot)
            }
            .buttonStyle(.plain)
        }
        .sheet(item: $editing) { SalaryEditor(store: store, month: $0) }
    }
}

/// The last few times you put money aside, newest first; press and hold one to delete it.
private struct RecentSavings: View {
    let store: SheetStore
    let entries: [SetAsideEntry]
    @State private var deleting: SetAsideEntry?

    var body: some View {
        let recent = entries.sorted { $0.date > $1.date }.prefix(5)
        if !recent.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Recently put aside").font(.subheadline.weight(.semibold))
                    ForEach(Array(recent)) { e in
                        HStack(spacing: 10) {
                            IconBadge(symbol: e.bucket == SplitBucket.emergency.rawValue ? SplitBucket.emergency.symbol : SplitBucket.savings.symbol,
                                      tint: e.bucket == SplitBucket.emergency.rawValue ? SplitBucket.emergency.tint : SplitBucket.savings.tint, size: 28)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(e.note ?? e.bucket).font(.subheadline).lineLimit(1)
                                Text("\(SheetDate.short(e.date)) · \(e.bucket)").font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(SheetFormat.qar(e.amount)).font(.subheadline.weight(.semibold).monospacedDigit())
                            if e.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                        }
                        .contentShape(Rectangle())
                        .contextMenu { Button("Delete", systemImage: "trash", role: .destructive) { deleting = e } }
                    }
                }
            }
            .popup(isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                   .delete("Delete \(SheetFormat.qar(deleting?.amount ?? 0)) put aside?", message: "It comes off your savings.") {
                if let id = deleting?.id { store.deleteSetAside(id: id) }
                deleting = nil
            })
        }
    }
}

/// Saved = what you put aside. Spending inside your plan never counts against it;
/// it only lowers "left over" — income not yet spent or put aside.
private struct TotalsCard: View {
    let snapshot: SheetSnapshot

    var body: some View {
        let months = snapshot.salary
        let income = months.reduce(0) { $0 + ($1.totalIncome ?? 0) }
        let expenses = months.reduce(0) { $0 + ($1.expenses ?? 0) }
        let remit = months.reduce(0) { $0 + ($1.remittanceQar ?? 0) }
        let saved = SalarySplit.putAside(snapshot)
        let emergency = SalarySplit.emergencySaved(snapshot)
        let leftOver = months.reduce(0) { $0 + ($1.netSavings ?? 0) } - saved
        let plan = SalarySplit.savingsShare(snapshot.settings)
        let savedRate = income > 0 ? saved / income : 0
        let onPlan = savedRate + 0.0005 >= plan

        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Saved · put aside").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(SheetFormat.qar(saved))
                            .font(.system(.largeTitle, design: .rounded, weight: .bold))
                            .monospacedDigit().lineLimit(1).minimumScaleFactor(0.6)
                        Text(Money.main).font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    }
                    .foregroundStyle(.white)
                    Text("Emergency fund \(SheetFormat.qar(emergency)) · Savings / SIP \(SheetFormat.qar(saved - emergency))")
                        .font(.caption).foregroundStyle(.white.opacity(0.8))
                }
                Spacer()
                ZStack {
                    ScoreRing(fraction: plan > 0 ? savedRate / plan : 0, lineWidth: 7, tint: onPlan ? Color(hex: 0x4ADE80) : Color(hex: 0xFBBF24))
                    VStack(spacing: 0) {
                        Text(savedRate.formatted(.percent.precision(.fractionLength(0))))
                            .font(.system(.subheadline, design: .rounded, weight: .bold))
                        Text("saved").font(.system(size: 9, weight: .semibold)).opacity(0.8)
                    }
                    .foregroundStyle(.white)
                }
                .frame(width: 66, height: 66)
            }
            HeroChip(symbol: onPlan ? "checkmark.seal.fill" : "target",
                     text: onPlan ? "On plan: \(SheetFormat.percent(plan, 0)) of income" : "Plan: put aside \(SheetFormat.percent(plan, 0)) of income")
            Grid(horizontalSpacing: 8, verticalSpacing: 8) {
                GridRow {
                    stat("Income", income, symbol: "arrow.down.circle.fill")
                    stat("Spent", expenses, symbol: "cart.fill")
                }
                GridRow {
                    stat("Sent home", remit, symbol: "paperplane.fill")
                    stat("Left over", leftOver, symbol: "wallet.pass.fill")
                }
            }
            Text("Spending inside your budget doesn't touch what you've saved — it only lowers Left over (income you haven't spent or put aside yet).")
                .font(.caption2).foregroundStyle(.white.opacity(0.75))
        }
        .padding(20)
        .background(LinearGradient(colors: [Color(hex: 0x0F5132), Color(hex: 0x0F766E)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.top, 4)
    }

    private func stat(_ title: String, _ qar: Double, symbol: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(title, systemImage: symbol).font(.caption2.weight(.semibold)).foregroundStyle(.white.opacity(0.85)).lineLimit(1)
            Text(SheetFormat.qar(qar)).font(.subheadline.weight(.bold)).foregroundStyle(.white).monospacedDigit()
                .lineLimit(1).minimumScaleFactor(0.6)
        }
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct MonthCard: View {
    let month: SalaryMonth
    let snapshot: SheetSnapshot

    var body: some View {
        let income = month.totalIncome ?? 0
        let putAside = SalarySplit.putAside(snapshot, month: month.month)
        let planned = income * SalarySplit.savingsShare(snapshot.settings)
        let leftOver = (month.netSavings ?? 0) - putAside
        SheetCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    IconBadge(symbol: "calendar", tint: SheetTheme.teal, size: 34)
                    Text(month.label).font(.headline)
                    Spacer()
                    switch SalarySplit.state(snapshot, month: month.month) {
                    case .none: EmptyView()
                    case .saved: StatusPill(text: "Saved ✓", tint: SheetTheme.good)
                    case .toPutAside(let amount): StatusPill(text: "\(SheetFormat.qar(amount)) to put aside", tint: SheetTheme.warn)
                    case .overBudget(let amount, let buckets):
                        StatusPill(text: "\(buckets.count == 1 ? buckets[0].rawValue : "\(buckets.count) buckets") over by \(SheetFormat.qar(amount))", tint: SheetTheme.bad)
                    }
                    Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                }
                HStack(spacing: 0) {
                    value("Income", month.totalIncome, .primary)
                    value("Spent", month.expenses, SheetTheme.bad)
                    value("Sent", month.remittanceQar, SheetTheme.sky)
                    value("Put aside", putAside, SheetTheme.good)
                    value("Left over", leftOver, leftOver < 0 ? SheetTheme.bad : .primary)
                }
                if planned > 0 {
                    ProgressRow(title: "Put aside vs plan", detail: "\(SheetFormat.qar(putAside)) / \(SheetFormat.qar(planned))",
                                fraction: putAside / planned, tint: putAside >= planned ? SheetTheme.good : SheetTheme.teal)
                }
            }
        }
        .contentShape(Rectangle())
    }

    private func value(_ title: String, _ amount: Double?, _ tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.caption2.weight(.semibold)).foregroundStyle(.secondary).lineLimit(1)
            Text(SheetFormat.qar(amount)).font(.footnote.weight(.bold)).monospacedDigit().foregroundStyle(tint)
                .lineLimit(1).minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct SalaryEditor: View {
    let store: SheetStore
    let month: SalaryMonth

    @Environment(\.dismiss) private var dismiss
    @State private var salary: Double?
    @State private var otherIncome: Double?
    @State private var remittance: Double?
    /// Bumped to make the salary field show a value filled in by the button.
    @State private var filled = 0

    init(store: SheetStore, month: SalaryMonth) {
        self.store = store
        self.month = month
        _salary = State(initialValue: month.salary)
        _otherIncome = State(initialValue: month.otherIncome)
        _remittance = State(initialValue: month.remittanceQar)
    }

    var body: some View {
        let rate = store.snapshot?.settings.rate ?? 0
        NavigationStack {
            Form {
                Section("Income") {
                    NumberRow(title: "Salary", unit: Money.main, value: $salary, hint: "This month only · blank = no salary")
                        .id(filled)
                    if salary == nil, let usual = store.snapshot?.settings.salary, usual > 0 {
                        Button("Use my usual salary · \(SheetFormat.qar(usual))", systemImage: "arrow.down.circle") {
                            salary = usual
                            filled += 1
                        }
                    }
                    NumberRow(title: "Other income", unit: Money.main, value: $otherIncome, hint: nil)
                }
                Section("Money sent home") {
                    NumberRow(title: "Sent home", unit: Money.main, value: $remittance, hint: Money.single ? nil : remittance.map { "≈ \(Money.homeAmount($0 * rate))" })
                }
                Section {
                    LabeledContent("Spent (from your expenses)", value: SheetFormat.qar(month.expenses))
                    if let snapshot = store.snapshot {
                        let putAside = SalarySplit.putAside(snapshot, month: month.month)
                        LabeledContent("Put aside", value: SheetFormat.qar(putAside))
                        LabeledContent("Left over", value: SheetFormat.qar((month.netSavings ?? 0) - putAside))
                    }
                } header: { Text("This month") } footer: {
                    Text("Left over = income − spent − sent home − put aside: money you haven't used or saved yet.")
                }
            }
            .navigationTitle(month.label)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if store.isSaving {
                        ProgressView()
                    } else {
                        Button("Save") {
                            Task {
                                if await store.saveSalary(month: month.month, salary: salary, otherIncome: otherIncome, remittance: remittance) {
                                    dismiss()
                                }
                            }
                        }
                        .fontWeight(.semibold)
                    }
                }
            }
        }
        .tint(SheetTheme.navy)
    }
}
