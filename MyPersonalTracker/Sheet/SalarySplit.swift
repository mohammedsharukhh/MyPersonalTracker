//
//  SalarySplit.swift
//  MyPersonalTracker
//
//  Where each month's salary is meant to go (21_Split.gs): Essentials,
//  Emergency fund, Savings / SIP, Family support and Lifestyle, each a
//  percentage set in Settings. Each bucket shows its share of the month's
//  income and what has gone to it — worked out here the same way as the
//  sheet's Salary Split tab, so it updates the moment you add an expense.
//

import SwiftUI

/// Money moved into the emergency fund or savings / SIP — saving, not spending.
struct SetAsideEntry: Codable, Hashable, Identifiable {
    var id: String
    var date: String
    var bucket: String
    var amount: Double
    var note: String?
    /// The savings goal it's for, if any.
    var goalId: String?
    var pending = false

    enum CodingKeys: String, CodingKey { case id, date, bucket, amount, note, goalId }

    init(id: String = UUID().uuidString, date: String = SheetDate.today, bucket: String, amount: Double, note: String? = nil,
         goalId: String? = nil, pending: Bool = false) {
        self.id = id; self.date = date; self.bucket = bucket; self.amount = amount; self.note = note; self.goalId = goalId; self.pending = pending
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        date = c.flexString(.date) ?? ""
        bucket = c.flexString(.bucket) ?? ""
        amount = c.flexDouble(.amount) ?? 0
        note = c.flexString(.note)
        goalId = c.flexString(.goalId)
    }
}

/// Something you're saving for: "Trip to Kerala · 15,000 by March".
struct SavingsGoal: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var target: Double
    var due: String?
    var created: String?
    var done: Bool
    var doneOn: String?
    var notes: String?

    init(id: String = UUID().uuidString, name: String = "", target: Double = 0, due: String? = nil, created: String? = SheetDate.today,
         done: Bool = false, doneOn: String? = nil, notes: String? = nil) {
        self.id = id; self.name = name; self.target = target; self.due = due; self.created = created
        self.done = done; self.doneOn = doneOn; self.notes = notes
    }

    enum CodingKeys: String, CodingKey { case id, name, target, due, created, done, doneOn, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        name = c.flexString(.name) ?? ""
        target = c.flexDouble(.target) ?? 0
        due = c.flexString(.due)
        created = c.flexString(.created)
        done = (try? c.decodeIfPresent(Bool.self, forKey: .done)) ?? (c.flexString(.done) == "Y")
        doneOn = c.flexString(.doneOn)
        notes = c.flexString(.notes)
    }
}

/// A goal's progress, from the money put aside for it.
struct SavingsGoalProgress {
    let saved: Double
    let target: Double
    var left: Double { max(0, target - saved) }
    var fraction: Double { target > 0 ? saved / target : 0 }
    /// What it takes each month to make the date (nil without one, or once reached).
    let perMonth: Double?
    let monthsLeft: Int?
}

extension SheetSnapshot {
    var goals: [SavingsGoal] { savingsGoals ?? [] }

    func progress(of goal: SavingsGoal, today: Date = .now) -> SavingsGoalProgress {
        let saved = (setAside ?? []).filter { $0.goalId == goal.id }.reduce(0) { $0 + $1.amount }
        var months: Int?
        var perMonth: Double?
        if let due = SheetDate.date(goal.due), !goal.done, saved < goal.target {
            let m = max(1, (Calendar.current.dateComponents([.month], from: Calendar.current.startOfDay(for: today), to: due).month ?? 0) + 1)
            months = m
            perMonth = (goal.target - saved) / Double(m)
        }
        return SavingsGoalProgress(saved: saved, target: goal.target, perMonth: perMonth, monthsLeft: months)
    }
}

enum SplitBucket: String, CaseIterable, Identifiable {
    case essentials = "Essentials", emergency = "Emergency fund", savings = "Savings / SIP", family = "Family support", lifestyle = "Lifestyle"
    var id: String { rawValue }

    /// The Settings key holding its share (a fraction).
    var key: String {
        switch self {
        case .essentials: "SPLIT_ESSENTIALS"
        case .emergency: "SPLIT_EMERGENCY"
        case .savings: "SPLIT_SAVINGS"
        case .family: "SPLIT_FAMILY"
        case .lifestyle: "SPLIT_LIFESTYLE"
        }
    }

    var defaultShare: Double {
        switch self {
        case .essentials: 0.45
        case .emergency: 0.10
        case .savings: 0.20
        case .family: 0.15
        case .lifestyle: 0.10
        }
    }

    /// Ledger categories that count toward it.
    var categories: Set<String> {
        switch self {
        case .essentials: ["Groceries", "Rent & Utilities", "Transport", "Mobile & Internet", "Health & Pharmacy", "Household", "Education"]
        case .family: ["Remittance", "Gifts & Family"]
        case .lifestyle: ["Food & Dining", "Personal Care", "Clothing", "Entertainment", "Travel", "Other"]
        case .emergency, .savings: []
        }
    }

    /// The bucket an expense counts toward: monthly bills are always Essentials,
    /// everything else goes by its category.
    static func of(_ expense: SheetExpense) -> SplitBucket? {
        if isMonthlyBill(expense) { return .essentials }
        return [.essentials, .family, .lifestyle].first { $0.categories.contains(expense.category ?? "") }
    }

    /// Added from the Recurring tab (its note ends "Recurring").
    static func isMonthlyBill(_ expense: SheetExpense) -> Bool {
        (expense.notes ?? "").localizedCaseInsensitiveContains("Recurring")
    }

    /// Filled by putting money aside rather than by spending.
    var isSaving: Bool { self == .emergency || self == .savings }

    var symbol: String {
        switch self {
        case .essentials: "house.fill"
        case .emergency: "cross.case.fill"
        case .savings: "chart.line.uptrend.xyaxis"
        case .family: "figure.2.and.child.holdinghands"
        case .lifestyle: "sparkles"
        }
    }

    var tint: Color {
        switch self {
        case .essentials: SheetTheme.navy
        case .emergency: SheetTheme.bad
        case .savings: SheetTheme.good
        case .family: SheetTheme.purple
        case .lifestyle: SheetTheme.warn
        }
    }

    var explanation: String {
        switch self {
        case .essentials: "Monthly bills, rent, groceries, transport, health"
        case .emergency: "Put aside until you reach your goal"
        case .savings: "Investments, SIP, deposits"
        case .family: "Money sent home, gifts, remittance"
        case .lifestyle: "Eating out, shopping, fun, travel"
        }
    }
}

extension SheetSettings {
    func share(_ bucket: SplitBucket) -> Double { splitShares[bucket.key] ?? bucket.defaultShare }
}

/// One bucket for one month.
struct SplitLine: Identifiable {
    let bucket: SplitBucket
    let share: Double
    let planned: Double
    let used: Double
    var id: String { bucket.rawValue }
    var left: Double { planned - used }
    var fraction: Double { planned > 0 ? used / planned : 0 }
}

enum SalarySplit {
    /// Each bucket for `month` ("yyyy-MM"): its share of that month's income and what has gone to it.
    static func lines(_ s: SheetSnapshot, month: String) -> [SplitLine] {
        let row = s.salary.first { $0.month == month }
        let income = row?.totalIncome ?? ((row?.salary ?? 0) + (row?.otherIncome ?? 0))
        let expenses = s.expenses.filter { ($0.date ?? "").hasPrefix(month) }
        let setAside = (s.setAside ?? []).filter { $0.date.hasPrefix(month) }
        return SplitBucket.allCases.map { bucket in
            let share = s.settings.share(bucket)
            var used = bucket.isSaving
                ? setAside.filter { $0.bucket == bucket.rawValue }.reduce(0) { $0 + $1.amount }
                : expenses.filter { SplitBucket.of($0) == bucket }.reduce(0) { $0 + ($1.qar ?? 0) }
            if bucket == .family { used += row?.remittanceQar ?? 0 }
            return SplitLine(bucket: bucket, share: share, planned: (income * share * 100).rounded() / 100, used: used)
        }
    }

    static func income(_ s: SheetSnapshot, month: String) -> Double {
        let row = s.salary.first { $0.month == month }
        return row?.totalIncome ?? ((row?.salary ?? 0) + (row?.otherIncome ?? 0))
    }

    /// Put aside (emergency fund + savings / SIP), in one month ("yyyy-MM") or overall (nil).
    static func putAside(_ s: SheetSnapshot, month: String? = nil) -> Double {
        (s.setAside ?? []).filter { month == nil || $0.date.hasPrefix(month!) }.reduce(0) { $0 + $1.amount }
    }

    /// The share of income planned to be saved: emergency fund + savings / SIP.
    static func savingsShare(_ s: SheetSettings) -> Double { s.share(.emergency) + s.share(.savings) }

    /// How a month went against the plan.
    enum MonthState { case none, saved, toPutAside(Double), overBudget(Double, buckets: [SplitBucket]) }

    static func state(_ s: SheetSnapshot, month: String) -> MonthState {
        let lines = lines(s, month: month)
        guard income(s, month: month) > 0 else { return .none }
        let overLines = lines.filter { !$0.bucket.isSaving && $0.left < -0.005 }
        let over = overLines.reduce(0) { $0 - $1.left }
        if over > 0.005 { return .overBudget(over, buckets: overLines.map(\.bucket)) }
        // The month's savings target as a whole (the split card shows each bucket).
        let saving = lines.filter(\.bucket.isSaving)
        let toGo = max(0, saving.reduce(0) { $0 + $1.planned } - saving.reduce(0) { $0 + $1.used })
        return toGo > 0.005 ? .toPutAside(toGo) : .saved
    }

    /// Everything ever put in the emergency fund.
    static func emergencySaved(_ s: SheetSnapshot) -> Double {
        (s.setAside ?? []).filter { $0.bucket == SplitBucket.emergency.rawValue }.reduce(0) { $0 + $1.amount }
    }
}

/// Finance ▸ Savings: this month's salary, split into the five buckets.
struct SalarySplitCard: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var month = String(SheetDate.today.prefix(7))
    @State private var putAside: SplitBucket?
    @State private var editingSalary: SalaryMonth?

    var body: some View {
        let months = snapshot.salary.map(\.month)
        let lines = SalarySplit.lines(snapshot, month: month)
        let income = SalarySplit.income(snapshot, month: month)
        let label = snapshot.salary.first { $0.month == month }?.label ?? month
        let shareTotal = lines.reduce(0) { $0 + $1.share }

        SheetCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Button { step(-1, months) } label: { Image(systemName: "chevron.left") }.disabled(months.first == month)
                    Spacer()
                    VStack(spacing: 1) {
                        Text("Salary split · \(label)").font(.headline)
                        Text(income > 0 ? "\(SheetFormat.qar(income)) \(Money.main) income" : "No salary entered for this month")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button { step(1, months) } label: { Image(systemName: "chevron.right") }.disabled(months.last == month)
                }
                .buttonStyle(.borderless)

                if income <= 0 {
                    Button {
                        editingSalary = snapshot.salary.first { $0.month == month }
                    } label: {
                        Label("Enter this month's salary to split it", systemImage: "banknote")
                            .font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity).padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent).tint(SheetTheme.navy)
                    .disabled(snapshot.salary.first { $0.month == month } == nil)
                }

                ForEach(lines) { line in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 10) {
                            IconBadge(symbol: line.bucket.symbol, tint: line.bucket.tint, size: 30)
                            VStack(alignment: .leading, spacing: 1) {
                                Text("\(line.bucket.rawValue) · \(SheetFormat.percent(line.share, 0))").font(.subheadline.weight(.semibold))
                                Text(detail(line)).font(.caption).foregroundStyle(line.left < 0 && !line.bucket.isSaving ? SheetTheme.bad : .secondary)
                            }
                            Spacer()
                            Text(SheetFormat.qar(line.planned)).font(.subheadline.weight(.bold).monospacedDigit())
                        }
                        Capsule().fill(Color(.tertiarySystemFill)).frame(height: 7)
                            .overlay(alignment: .leading) {
                                GeometryReader { geo in
                                    Capsule()
                                        .fill(line.bucket.isSaving ? (line.fraction >= 1 ? SheetTheme.good : line.bucket.tint)
                                                                   : (line.fraction > 1 ? SheetTheme.bad : line.bucket.tint))
                                        .frame(width: geo.size.width * min(max(line.fraction, 0), 1))
                                }
                            }
                        if line.bucket.isSaving {
                            Button { putAside = line.bucket } label: {
                                Label("Put money aside", systemImage: "plus.circle").font(.caption.weight(.semibold))
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                }

                if let goal = snapshot.settings.emergencyGoal, goal > 0 {
                    let saved = SalarySplit.emergencySaved(snapshot)
                    Divider()
                    ProgressRow(title: "Emergency fund", detail: "\(SheetFormat.qar(saved)) of \(SheetFormat.qar(goal))",
                                fraction: saved / goal, tint: SplitBucket.emergency.tint, symbol: "shield.lefthalf.filled")
                }
                if abs(shareTotal - 1) > 0.001 {
                    Label("Your shares add up to \(SheetFormat.percent(shareTotal, 0)), not 100% — change them in Settings ▸ Salary split.",
                          systemImage: "exclamationmark.triangle.fill").font(.caption).foregroundStyle(SheetTheme.warn)
                }
            }
        }
        .sheet(item: $putAside) { PutAsideSheet(store: store, bucket: $0) }
        .sheet(item: $editingSalary) { SalaryEditor(store: store, month: $0) }
        .onAppear { if !months.contains(month), let last = months.last(where: { $0 <= month }) ?? months.first { month = last } }
    }

    private func detail(_ line: SplitLine) -> String {
        if line.bucket.isSaving {
            return line.planned > 0 ? "\(SheetFormat.qar(line.used)) put aside · \(line.left > 0 ? "\(SheetFormat.qar(line.left)) to go" : "done ✓")"
                                    : "\(SheetFormat.qar(line.used)) put aside"
        }
        if line.planned <= 0 { return "\(SheetFormat.qar(line.used)) used" }
        return line.left >= 0 ? "\(SheetFormat.qar(line.used)) used · \(SheetFormat.qar(line.left)) left"
                              : "\(SheetFormat.qar(line.used)) used · \(SheetFormat.qar(-line.left)) over"
    }

    private func step(_ by: Int, _ months: [String]) {
        guard let i = months.firstIndex(of: month), months.indices.contains(i + by) else { return }
        month = months[i + by]
    }
}

/// Move money into the emergency fund or savings / SIP.
struct PutAsideSheet: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var bucket: SplitBucket
    @State private var goalId: String?
    @State private var amountText = ""
    @State private var date = Date.now
    @State private var note = ""

    init(store: SheetStore, bucket: SplitBucket, goalId: String? = nil) {
        self.store = store
        _bucket = State(initialValue: bucket)
        _goalId = State(initialValue: goalId)
    }

    var body: some View {
        let entries = (store.snapshot?.setAside ?? []).filter { $0.bucket == bucket.rawValue }.sorted { $0.date > $1.date }
        NavigationStack {
            Form {
                Section {
                    HStack {
                        CurrencyPill(code: Money.main)
                        TextField("0.00", text: $amountText).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                            .font(.system(.title2, design: .rounded, weight: .bold))
                    }
                    DatePicker("Date", selection: $date, in: ...Date.now, displayedComponents: .date)
                    TextField(bucket == .savings ? "Note, e.g. Nifty SIP, FD" : "Note (optional)", text: $note)
                    Picker("Into", selection: $bucket) {
                        Text(SplitBucket.savings.rawValue).tag(SplitBucket.savings)
                        Text(SplitBucket.emergency.rawValue).tag(SplitBucket.emergency)
                    }
                    let goals = (store.snapshot?.goals ?? []).filter { !$0.done || $0.id == goalId }
                    if !goals.isEmpty {
                        Picker("For a goal", selection: $goalId) {
                            Text("None").tag(String?.none)
                            ForEach(goals) { Text($0.name).tag(Optional($0.id)) }
                        }
                    }
                } header: { Text("Put money aside") } footer: {
                    Text("Saving, not spending — it doesn't go into your expenses.")
                }
                if !entries.isEmpty {
                    Section("Put aside so far · \(SheetFormat.qar(entries.reduce(0) { $0 + $1.amount })) \(Money.main)") {
                        ForEach(entries.prefix(12)) { e in
                            HStack {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(SheetDate.short(e.date)).font(.subheadline)
                                    if let n = e.note { Text(n).font(.caption).foregroundStyle(.secondary) }
                                }
                                Spacer()
                                Text(SheetFormat.qar(e.amount)).monospacedDigit()
                                if e.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                            }
                            .swipeActions { Button("Delete", systemImage: "trash", role: .destructive) { store.deleteSetAside(id: e.id) } }
                        }
                    }
                }
            }
            .navigationTitle("Put money aside")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let amount = Double(amountText.replacingOccurrences(of: ",", with: ".")), amount > 0 else { return }
                        store.saveSetAside(SetAsideEntry(date: SheetDate.string(date), bucket: bucket.rawValue, amount: (amount * 100).rounded() / 100,
                                                         note: note.trimmingCharacters(in: .whitespaces).isEmpty ? nil : note.trimmingCharacters(in: .whitespaces),
                                                         goalId: goalId))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled((Double(amountText.replacingOccurrences(of: ",", with: ".")) ?? 0) <= 0)
                }
            }
        }
        .tint(SheetTheme.navy)
    }
}
