//
//  RenewalsLoansView.swift
//  MyPersonalTracker
//
//  More ▸ Renewals: what expires and when, with reminders before.
//  Finance ▸ Budget ▸ Loans & EMIs: what's left on each and when it ends.
//

import SwiftUI

// MARK: - Renewals

struct RenewalsScreen: View {
    let store: SheetStore
    @State private var editing: Renewal?
    @State private var renewing: Renewal?

    var body: some View {
        let list = store.snapshot?.renewalList ?? []
        List {
            if list.isEmpty {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Never miss an expiry").font(.headline)
                        Text("Add your QID, passport, visa, driving licence, car registration, insurance or rent contract with its expiry date. You'll get a reminder 30, 7 and 1 day before.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
            ForEach(list) { r in
                let days = r.daysLeft()
                Button { editing = r } label: {
                    HStack(spacing: 12) {
                        IconBadge(symbol: r.kind.symbol, tint: tint(days), size: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(r.item).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                            Text("\(SheetDate.short(r.expires)) · \(RenewalText.left(days))").font(.caption).foregroundStyle(days <= 7 ? tint(days) : .secondary)
                        }
                        Spacer()
                        if r.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                        if days <= 30 { StatusPill(text: days < 0 ? "Expired" : "Soon", tint: tint(days)) }
                    }
                }
                .swipeActions(edge: .leading) {
                    Button("Renewed", systemImage: "checkmark.seal") { renewing = r }.tint(SheetTheme.good)
                }
            }
        }
        .navigationTitle("Renewals")
        .toolbar {
            ToolbarItem(placement: .primaryAction) { Button { editing = Renewal() } label: { Image(systemName: "plus") } }
        }
        .sheet(item: $editing) { r in RenewalEditor(store: store, renewal: r, isNew: !list.contains { $0.id == r.id }) }
        .sheet(item: $renewing) { r in RenewedSheet(store: store, renewal: r) }
    }

    private func tint(_ days: Int) -> Color { days < 0 ? SheetTheme.bad : days <= 7 ? SheetTheme.warn : days <= 30 ? SheetTheme.sky : SheetTheme.navy }
}

struct RenewalEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var r: Renewal
    @State private var expires: Date
    @State private var renewing = false
    @State private var confirmDelete = false

    init(store: SheetStore, renewal: Renewal, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _r = State(initialValue: renewal)
        _expires = State(initialValue: SheetDate.date(renewal.expires) ?? Calendar.current.date(byAdding: .year, value: 1, to: .now) ?? .now)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("What", selection: $r.kind) {
                        ForEach(Renewal.Kind.allCases) { Label($0.rawValue, systemImage: $0.symbol).tag($0) }
                    }
                    TextField(r.kind == .other ? "e.g. Gym membership" : "e.g. \(r.kind.rawValue)", text: $r.item)
                    DatePicker("Expires", selection: $expires, displayedComponents: .date)
                } footer: {
                    Text("Only the date is kept — no document numbers.")
                }
                Section("Remind me") {
                    ForEach([60, 30, 14, 7, 3, 1, 0], id: \.self) { d in
                        Toggle(d == 0 ? "On the day" : "\(d) day\(d == 1 ? "" : "s") before", isOn: Binding(
                            get: { r.remind.contains(d) },
                            set: { on in r.remind = on ? Array(Set(r.remind + [d])).sorted(by: >) : r.remind.filter { $0 != d } }))
                    }
                }
                Section {
                    HStack {
                        Text("Renewal cost")
                        Spacer()
                        TextField("Optional", value: $r.cost, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 110)
                        Text(Money.main).foregroundStyle(.secondary)
                    }
                    TextField("Notes (optional)", text: Binding(get: { r.notes ?? "" }, set: { r.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                if !isNew {
                    Section {
                        Button("Renewed — set the new date") { renewing = true }
                        Button("Delete", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(isNew ? "New renewal" : r.item)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = r
                        saved.item = r.item.trimmingCharacters(in: .whitespaces).isEmpty ? r.kind.rawValue : r.item.trimmingCharacters(in: .whitespaces)
                        saved.expires = SheetDate.string(expires)
                        store.saveRenewal(saved)
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .sheet(isPresented: $renewing) { RenewedSheet(store: store, renewal: r) { dismiss() } }
            .popup(isPresented: $confirmDelete, .delete("Delete \(r.item)?", message: "Its reminders stop.") {
                store.deleteRenewal(id: r.id); dismiss()
            })
        }
        .tint(SheetTheme.navy)
    }
}

/// Moves a renewal on to its next expiry.
private struct RenewedSheet: View {
    let store: SheetStore
    let renewal: Renewal
    var onDone: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @State private var next: Date
    @State private var cost: Double?
    @State private var toFinance = true

    init(store: SheetStore, renewal: Renewal, onDone: @escaping () -> Void = {}) {
        self.store = store
        self.renewal = renewal
        self.onDone = onDone
        _next = State(initialValue: SheetDate.date(renewal.suggestedNext) ?? .now)
        _cost = State(initialValue: renewal.cost)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("New expiry", selection: $next, displayedComponents: .date)
                } footer: {
                    Text("Reminders move to the new date.")
                }
                Section {
                    HStack {
                        Text("It cost")
                        Spacer()
                        TextField("0", value: $cost, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 110)
                        Text(Money.main).foregroundStyle(.secondary)
                    }
                    if (cost ?? 0) > 0 { Toggle("Add to Finance", isOn: $toFinance) }
                } footer: {
                    if (cost ?? 0) > 0, toFinance { Text("Logged today as \(renewal.kind.category), “\(renewal.item) renewal”.") }
                }
            }
            .navigationTitle("Renewed \(renewal.item)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var r = renewal
                        r.lastRenewed = SheetDate.today
                        r.expires = SheetDate.string(next)
                        if let cost, cost > 0 { r.cost = cost }
                        store.saveRenewal(r)
                        if let cost, cost > 0, toFinance {
                            Task {
                                await store.saveExpense(row: nil, date: SheetDate.today, description: "\(renewal.item) renewal",
                                                        category: renewal.kind.category, method: "", qar: cost, notes: "Renewal")
                            }
                        }
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                        dismiss()
                        onDone()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .presentationDetents([.medium])
        .tint(SheetTheme.navy)
    }
}

/// Expired or expiring within 30 days, on the Dashboard.
struct RenewalsDueCard: View {
    let snapshot: SheetSnapshot
    let onOpen: () -> Void

    var body: some View {
        let due = snapshot.renewalsDue(within: 30)
        if !due.isEmpty {
            Button(action: onOpen) {
                SheetCard {
                    HStack(alignment: .top, spacing: 12) {
                        IconBadge(symbol: "calendar.badge.exclamationmark", tint: due.contains { $0.daysLeft() < 0 } ? SheetTheme.bad : SheetTheme.warn, size: 44)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Renewals coming up").font(.headline).foregroundStyle(Color(.label))
                            ForEach(due.prefix(3)) { r in
                                Text("\(r.item) · \(RenewalText.left(r.daysLeft()))").font(.subheadline)
                                    .foregroundStyle(r.daysLeft() < 0 ? SheetTheme.bad : Color(.label))
                            }
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

// MARK: - Loans

/// Finance ▸ Budget: each loan's progress, and the month's instalments.
struct LoansSection: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var editing: Loan?

    var body: some View {
        let loans = (snapshot.loans ?? []).sorted { ($0.closed ? 1 : 0, $0.lastPayment) < ($1.closed ? 1 : 0, $1.lastPayment) }
        HStack(alignment: .bottom) {
            SectionTitle(title: "Loans & EMIs", subtitle: snapshot.activeLoans.isEmpty ? nil
                         : "\(SheetFormat.qar(snapshot.monthlyEMI)) \(Money.main) a month · \(SheetFormat.qar(snapshot.activeLoans.reduce(0) { $0 + $1.remaining() })) left")
            Button { editing = Loan() } label: { Image(systemName: "plus.circle.fill").font(.title3) }
                .foregroundStyle(SheetTheme.navy).accessibilityLabel("Add a loan")
        }
        if loans.isEmpty {
            SheetCard {
                Text("Car loan, phone on instalments, a personal loan? Add it with the monthly amount and how many months — you'll see what's left and the date it's paid off.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        ForEach(loans) { loan in
            Button { editing = loan } label: { LoanCard(loan: loan) }.buttonStyle(.plain)
        }
        Color.clear.frame(height: 0)
            .sheet(item: $editing) { l in LoanEditor(store: store, loan: l, isNew: !(snapshot.loans ?? []).contains { $0.id == l.id }) }
    }
}

private struct LoanCard: View {
    let loan: Loan

    var body: some View {
        let paid = loan.paidCount()
        let done = loan.closed || loan.left() == 0
        SheetCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    IconBadge(symbol: done ? "checkmark.seal.fill" : "creditcard.fill", tint: done ? SheetTheme.good : SheetTheme.indigo, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(loan.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                        Text([loan.lender, "\(SheetFormat.qar(loan.emi)) a month"].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if loan.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                    Text(done ? "Paid off" : "\(paid)/\(loan.months)").font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundStyle(done ? SheetTheme.good : SheetTheme.indigo)
                }
                ProgressView(value: loan.fraction()).tint(done ? SheetTheme.good : SheetTheme.indigo)
                if !done {
                    HStack {
                        Text("\(SheetFormat.qar(loan.remaining())) \(Money.main) left · \(loan.left()) payment\(loan.left() == 1 ? "" : "s")").font(.caption.weight(.semibold))
                        Spacer()
                        Text("Paid off \(SheetDate.date(loan.lastPayment)?.formatted(.dateTime.month(.abbreviated).year()) ?? loan.lastPayment)")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if let next = loan.nextPayment() {
                        Text("Next: \(SheetDate.short(next))" + (loan.bill ? " · added to Finance on the day" : " · not in Finance"))
                            .font(.caption).foregroundStyle(loan.bill ? Color.secondary : SheetTheme.warn)
                    }
                }
            }
        }
    }
}

struct LoanEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var loan: Loan
    @State private var first: Date
    @State private var setPaid: Bool
    @State private var confirmDelete = false

    init(store: SheetStore, loan: Loan, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _loan = State(initialValue: loan)
        _first = State(initialValue: SheetDate.date(loan.firstPayment) ?? .now)
        _setPaid = State(initialValue: loan.paid != nil)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("e.g. Car loan, iPhone instalments", text: $loan.name)
                    TextField("Bank or shop (optional)", text: Binding(get: { loan.lender ?? "" }, set: { loan.lender = $0.isEmpty ? nil : $0 }))
                    amountRow("Monthly instalment", value: Binding(get: { loan.emi > 0 ? loan.emi : nil }, set: { loan.emi = $0 ?? 0 }))
                    Stepper("\(loan.months) months (\(loan.months / 12 > 0 ? "\(loan.months / 12) yr " : "")\(loan.months % 12 > 0 ? "\(loan.months % 12) mo" : "")".replacingOccurrences(of: " )", with: ")"),
                            value: $loan.months, in: 1...360)
                    DatePicker("First payment", selection: $first, displayedComponents: .date)
                } footer: {
                    let total = Double(loan.months) * loan.emi
                    if total > 0 { Text("\(SheetFormat.qar(total)) \(Money.main) in all · last payment \(SheetDate.short(lastDate))") }
                }
                Section {
                    Toggle("Set how many are paid", isOn: $setPaid.animation())
                    if setPaid {
                        Stepper("\(loan.paid ?? 0) paid", value: Binding(get: { loan.paid ?? 0 }, set: { loan.paid = $0 }), in: 0...loan.months)
                    }
                } footer: {
                    Text(setPaid ? "Use this if you paid ahead or missed one." : "Counted from the first payment date: one a month.")
                }
                Section("Optional") {
                    amountRow("Amount borrowed", value: $loan.principal)
                    HStack {
                        Text("Interest")
                        Spacer()
                        TextField("%", value: $loan.rate, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 70)
                        Text("% a year").foregroundStyle(.secondary)
                    }
                    TextField("Notes", text: Binding(get: { loan.notes ?? "" }, set: { loan.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                Section {
                    Toggle("Monthly bill", isOn: $loan.bill)
                } footer: {
                    Text(loan.bill ? "Each instalment is added to Finance on its day, counted in Essentials — and stops by itself after the last one."
                                   : "Instalments won't show in Finance. Turn this on unless you log them yourself.")
                }
                if !isNew {
                    Section {
                        Toggle("Closed / paid off", isOn: $loan.closed)
                        Button("Delete loan", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(isNew ? "New loan" : loan.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .fontWeight(.semibold)
                        .disabled(loan.name.trimmingCharacters(in: .whitespaces).isEmpty || loan.emi <= 0)
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete \(loan.name)?", message: loan.bill ? "Its monthly bill stops. Instalments already logged stay in Finance." : nil) {
                store.deleteLoan(id: loan.id); dismiss()
            })
        }
        .tint(SheetTheme.navy)
    }

    private var lastDate: String {
        SheetDate.string(Calendar.current.date(byAdding: .month, value: loan.months - 1, to: first) ?? first)
    }

    private func amountRow(_ title: String, value: Binding<Double?>) -> some View {
        HStack {
            Text(title)
            Spacer()
            TextField("0", value: value, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 110)
            Text(Money.main).foregroundStyle(.secondary)
        }
    }

    private func save() {
        var saved = loan
        saved.name = loan.name.trimmingCharacters(in: .whitespaces)
        saved.firstPayment = SheetDate.string(first)
        if !setPaid { saved.paid = nil }
        store.saveLoan(saved)
        dismiss()
    }
}
