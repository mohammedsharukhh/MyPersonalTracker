//
//  ParentCareViews.swift
//  MyPersonalTracker
//
//  Parents ▸ Medicines (what they take, how many are left, Refilled) and
//  Parents ▸ Dates (birthdays, anniversaries, remembrance days).
//

import SwiftUI

// MARK: - Medicines

struct ParentMedicineRow: View {
    let medicine: ParentMedicine
    let onEdit: () -> Void
    let onRefill: () -> Void

    var body: some View {
        let low = medicine.runsLow()
        let out = (medicine.daysLeft() ?? 1) < 1
        HStack(spacing: 12) {
            Button(action: onEdit) {
                HStack(spacing: 12) {
                    IconBadge(symbol: "pills.fill", tint: medicine.stopped ? .secondary : (out ? SheetTheme.bad : low ? SheetTheme.warn : SheetTheme.teal), size: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(medicine.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                        Text([medicine.who.label, medicine.dose].compactMap { $0 }.joined(separator: " · "))
                            .font(.caption).foregroundStyle(.secondary)
                        Text(medicine.status()).font(.caption.weight(low ? .semibold : .regular))
                            .foregroundStyle(out ? SheetTheme.bad : low ? SheetTheme.warn : .secondary)
                    }
                    Spacer(minLength: 0)
                    if medicine.pending {
                        Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky).accessibilityLabel("Not synced yet")
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if !medicine.stopped {
                Button("Refilled", action: onRefill)
                    .font(.caption.weight(.semibold))
                    .buttonStyle(.bordered).tint(low ? SheetTheme.warn : SheetTheme.navy)
                    .accessibilityHint("Add the new pack")
            }
        }
    }
}

struct ParentMedicineEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var m: ParentMedicine
    @State private var leftNow: Double
    @State private var confirmDelete = false

    init(store: SheetStore, medicine: ParentMedicine, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _m = State(initialValue: medicine)
        _leftNow = State(initialValue: medicine.leftOn(.now))
    }

    private var trimmed: String { m.name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("For", selection: $m.who) {
                        ForEach(ParentAsk.Who.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    TextField("Medicine, e.g. Telmisartan 40 mg", text: $m.name)
                    TextField("How they take it, e.g. 1 morning, 1 night", text: Binding(get: { m.dose ?? "" }, set: { m.dose = $0.isEmpty ? nil : $0 }))
                }
                Section {
                    Stepper(value: $m.perDay, in: 0...12, step: 0.5) {
                        LabeledContent("A day", value: SheetFormat.number(m.perDay))
                    }
                    HStack {
                        Text("Left now")
                        Spacer()
                        TextField("0", value: $leftNow, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
                    }
                    Stepper(value: $m.warn, in: 1...30) {
                        LabeledContent("Warn me", value: "\(m.warn) day\(m.warn == 1 ? "" : "s") before")
                    }
                } header: {
                    Text("Refills")
                } footer: {
                    Text(m.perDay > 0 ? "Counts down by itself. With \(SheetFormat.number(leftNow)) left at \(SheetFormat.number(m.perDay)) a day, they last \(Int(leftNow / m.perDay)) days. You'll get the daily Mom & Dad notification from \(m.warn) days before."
                         : "Set how many a day so it can count down.")
                }
                Section {
                    HStack {
                        Text("Pack of")
                        Spacer()
                        TextField("Optional", value: $m.pack, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
                    }
                    HStack {
                        Text("A pack costs")
                        Spacer()
                        TextField("Optional", value: $m.cost, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
                        Text(Money.main).foregroundStyle(.secondary)
                    }
                    if (m.cost ?? 0) > 0 { Toggle("Add to Finance at each refill", isOn: $m.finance) }
                } footer: {
                    Text("“Refilled” adds a pack to what's left\((m.cost ?? 0) > 0 && m.finance ? " and logs its cost under Gifts & Family" : "").")
                }
                Section {
                    TextField("Notes (optional) — doctor, pharmacy…", text: Binding(get: { m.notes ?? "" }, set: { m.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                    if !isNew { Toggle("They've stopped taking it", isOn: $m.stopped) }
                }
                if !isNew {
                    Section { Button("Delete", role: .destructive) { confirmDelete = true } }
                }
            }
            .navigationTitle(isNew ? "New medicine" : m.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = m
                        saved.name = trimmed
                        // What's left now becomes the new count.
                        saved.left = max(leftNow, 0)
                        saved.counted = SheetDate.today
                        if (saved.cost ?? 0) <= 0 { saved.cost = nil; saved.finance = false }
                        store.saveParentMedicine(saved)
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(trimmed.isEmpty)
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete \(m.name)?", message: "Its refill reminders stop.") {
                store.deleteParentMedicine(id: m.id)
                Task { await ReminderScheduler.scheduleSheetAlerts() }
                dismiss()
            })
        }
        .tint(SheetTheme.navy)
    }
}

/// Adds the new pack to what's left (and its cost to Finance, if set).
struct ParentRefillSheet: View {
    let store: SheetStore
    let medicine: ParentMedicine
    @Environment(\.dismiss) private var dismiss
    @State private var bought: Double
    @State private var cost: Double?
    @State private var toFinance: Bool

    init(store: SheetStore, medicine: ParentMedicine) {
        self.store = store
        self.medicine = medicine
        _bought = State(initialValue: medicine.pack ?? 30)
        _cost = State(initialValue: medicine.cost)
        _toFinance = State(initialValue: medicine.finance)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Text("Bought")
                        Spacer()
                        TextField("0", value: $bought, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
                    }
                    HStack {
                        Text("It cost")
                        Spacer()
                        TextField("Optional", value: $cost, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
                        Text(Money.main).foregroundStyle(.secondary)
                    }
                    if (cost ?? 0) > 0 { Toggle("Add to Finance", isOn: $toFinance) }
                } footer: {
                    let after = medicine.leftOn(.now) + bought
                    Text("\(SheetFormat.number(medicine.leftOn(.now))) left + \(SheetFormat.number(bought)) = \(SheetFormat.number(after))"
                         + (medicine.perDay > 0 ? ", about \(Int(after / medicine.perDay)) days." : "."))
                }
            }
            .navigationTitle("Refilled \(medicine.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var refilled = medicine.refilled(with: max(bought, 0))
                        if let cost, cost > 0 { refilled.cost = cost }
                        store.saveParentMedicine(refilled)
                        if let cost, cost > 0, toFinance {
                            let lists = store.snapshot?.lists
                            let category = (lists?.categories ?? []).contains("Gifts & Family") ? "Gifts & Family" : (lists?.categories.last ?? "Other")
                            Task {
                                _ = await store.saveExpense(row: nil, date: SheetDate.today, description: "\(medicine.who.label): \(medicine.name)", category: category,
                                                            method: lists?.methods.first ?? "Cash", qar: cost, notes: ParentReminders.financeNote)
                            }
                        }
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .presentationDetents([.medium])
        .tint(SheetTheme.navy)
    }
}

// MARK: - Dates

struct ParentDateRow: View {
    let date: ParentDate
    let onEdit: () -> Void

    var body: some View {
        let soon = (date.daysUntil() ?? 99) <= 7
        Button(action: onEdit) {
            HStack(spacing: 12) {
                IconBadge(symbol: date.kind.symbol, tint: soon ? SheetTheme.warn : SheetTheme.purple, size: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(date.headline()).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                    Text("\(date.who.label) · \(date.next().map { $0.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)) } ?? date.day)")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if date.pending {
                    Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky).accessibilityLabel("Not synced yet")
                }
                StatusPill(text: date.when(), tint: soon ? SheetTheme.warn : SheetTheme.navy)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct ParentDateEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var d: ParentDate
    @State private var picked: Date
    @State private var knowYear: Bool
    @State private var confirmDelete = false

    init(store: SheetStore, date: ParentDate, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _d = State(initialValue: date)
        let parts = date.day.split(separator: "-").compactMap { Int($0) }
        let year = date.year ?? Calendar.current.component(.year, from: .now)
        _picked = State(initialValue: Calendar.current.date(from: DateComponents(year: year, month: parts.first ?? 1, day: parts.last ?? 1)) ?? .now)
        _knowYear = State(initialValue: date.year != nil)
    }

    private var trimmed: String { d.title.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Kind", selection: $d.kind) {
                        ForEach(ParentDate.Kind.allCases) { Label($0.rawValue, systemImage: $0.symbol).tag($0) }
                    }
                    Picker("Whose", selection: $d.who) {
                        ForEach(ParentAsk.Who.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    TextField(placeholder, text: $d.title)
                }
                Section {
                    DatePicker("Day", selection: $picked, displayedComponents: .date)
                    Toggle(yearLabel, isOn: $knowYear)
                } footer: {
                    Text(knowYear ? "With the year it says how many years it'll be." : "Every year on this day — the year is ignored.")
                }
                Section("Remind me") {
                    ForEach([30, 14, 7, 3, 1, 0], id: \.self) { n in
                        Toggle(n == 0 ? "On the day" : "\(n) day\(n == 1 ? "" : "s") before", isOn: Binding(
                            get: { d.remind.contains(n) },
                            set: { on in d.remind = on ? Array(Set(d.remind + [n])).sorted(by: >) : d.remind.filter { $0 != n } }))
                    }
                }
                Section {
                    TextField("Notes (optional) — gift ideas, where…", text: Binding(get: { d.notes ?? "" }, set: { d.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                if !isNew {
                    Section { Button("Delete", role: .destructive) { confirmDelete = true } }
                }
            }
            .navigationTitle(isNew ? "New date" : d.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = d
                        saved.title = trimmed
                        let c = Calendar.current.dateComponents([.year, .month, .day], from: picked)
                        saved.day = String(format: "%02d-%02d", c.month ?? 1, c.day ?? 1)
                        saved.year = knowYear ? c.year : nil
                        store.saveParentDate(saved)
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(trimmed.isEmpty || d.remind.isEmpty)
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete \(d.title)?", message: "Its reminders stop.") {
                store.deleteParentDate(id: d.id)
                Task { await ReminderScheduler.scheduleSheetAlerts() }
                dismiss()
            })
        }
        .tint(SheetTheme.navy)
    }

    private var placeholder: String {
        switch d.kind {
        case .birthday: d.who == .dad ? "e.g. Dad's birthday" : "e.g. Mom's birthday"
        case .anniversary: "e.g. Their wedding anniversary"
        case .remembrance: "e.g. Grandpa's remembrance day"
        case .other: "e.g. Housewarming day"
        }
    }

    private var yearLabel: String {
        switch d.kind {
        case .birthday: "I know the birth year"
        case .anniversary: "I know the year they married"
        default: "I know the year"
        }
    }
}
