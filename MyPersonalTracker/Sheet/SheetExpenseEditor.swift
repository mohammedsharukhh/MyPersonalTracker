//
//  SheetExpenseEditor.swift
//  MyPersonalTracker
//
//  Add or edit one ledger row — the Finance Tracker's yellow columns. Built
//  for quick entry: amount first (in any currency), then taps for the date,
//  category and payment method, with recent expenses one tap away.
//

import SwiftUI

struct SheetExpenseEditor: View {
    let store: SheetStore
    let expense: SheetExpense?

    @Environment(\.dismiss) private var dismiss
    @State private var book = CurrencyBook.shared
    @State private var date: Date
    @State private var description: String
    @State private var category: String
    @State private var method: String
    @State private var amountText: String
    @State private var currency = Money.main
    @State private var notes: String
    @State private var confirmDelete = false
    @State private var repeatMonthly = false
    @FocusState private var focus: Field?

    private enum Field { case amount, description, notes }

    init(store: SheetStore, expense: SheetExpense?) {
        self.store = store
        self.expense = expense
        let lists = store.snapshot?.lists
        _date = State(initialValue: SheetDate.date(expense?.date) ?? .now)
        _description = State(initialValue: expense?.description ?? "")
        _category = State(initialValue: expense?.category ?? lists?.categories.first ?? "")
        _method = State(initialValue: expense?.method ?? lists?.methods.first ?? "")
        _amountText = State(initialValue: expense?.qar.map { $0.formatted(.number.precision(.fractionLength(0...2)).grouping(.never)) } ?? "")
        _notes = State(initialValue: expense?.notes ?? "")
    }

    private var amount: Double? { Double(amountText.replacingOccurrences(of: ",", with: ".")) }
    private var qar: Double? { amount.flatMap { book.convert($0, from: currency, to: Money.main) } }
    private var rate: Double { store.snapshot?.settings.rate ?? 0 }
    private var canSave: Bool { (qar ?? 0) > 0 && !store.isSaving }

    /// The last few distinct expenses, newest first, to repeat with one tap.
    private var recents: [SheetExpense] {
        var seen = Set<String>()
        return (store.snapshot?.expenses ?? [])
            .sorted { ($0.date ?? "", $0.row) > ($1.date ?? "", $1.row) }
            .filter { item in
                guard let name = item.description, !name.isEmpty else { return false }
                return seen.insert(name.lowercased()).inserted
            }
            .prefix(8)
            .map { $0 }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    amountCard
                    if expense == nil, !recents.isEmpty { recentStrip }
                    field("What was it?") {
                        TextField("e.g. Groceries at Lulu", text: $description)
                            .focused($focus, equals: .description)
                            .textInputAutocapitalization(.sentences)
                            .padding(12)
                            .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    field("When?") { dateRow }
                    field("Category") {
                        ChipGrid(items: store.snapshot?.lists.categories ?? [], selection: $category, minWidth: 104) { item in
                            (SheetTheme.categoryIcon(item), SheetTheme.categoryColor(item))
                        }
                    }
                    field("Paid with") {
                        ChipGrid(items: store.snapshot?.lists.methods ?? [], selection: $method, minWidth: 150) { item in
                            (Self.methodIcon(item), SheetTheme.navy)
                        }
                    }
                    if expense == nil {
                        Toggle(isOn: $repeatMonthly) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Repeat every month").font(.subheadline.weight(.semibold))
                                Text("Added for you on the \(ordinal(Calendar.current.component(.day, from: date))) of each month — for rent, phone, gym.")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .tint(SheetTheme.purple)
                        .padding(14)
                        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    field("Notes (optional)") {
                        TextField("Anything to remember", text: $notes, axis: .vertical)
                            .lineLimit(1...4)
                            .focused($focus, equals: .notes)
                            .padding(12)
                            .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    if expense != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Delete expense", systemImage: "trash")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(SheetTheme.bad.opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .tint(SheetTheme.bad)
                    }
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(SheetTheme.page)
            .safeAreaInset(edge: .bottom) { if focus == nil { saveBar } }
            .navigationTitle(expense == nil ? "Add expense" : "Edit expense")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if store.isSaving {
                        ProgressView()
                    } else {
                        Button("Save") { Task { await save() } }.fontWeight(.semibold).disabled(!canSave)
                    }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { focus = nil }
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete this expense?", message: "It's removed from the sheet. You can undo for a few seconds.") {
                Task { if let expense, await store.deleteExpense(row: expense.row) { dismiss() } }
            })
            .popup(isPresented: Binding(get: { store.errorMessage != nil && !store.isSaving && store.snapshot != nil },
                                        set: { if !$0 { store.errorMessage = nil } }),
                   .error("Couldn't save", message: store.errorMessage))
            .onAppear { if expense == nil { focus = .amount } }
            .task(id: currency) { if currency != Money.main { await book.refreshIfStale() } }
        }
        .tint(SheetTheme.navy)
    }

    // MARK: Sections

    private var amountCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Amount").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
                if expense == nil {
                    ReceiptScanButton { result in
                        if let amount = result.amount {
                            currency = Money.main
                            amountText = amount.formatted(.number.precision(.fractionLength(0...2)).grouping(.never))
                        }
                        if let found = result.date { date = found }
                        if description.isEmpty, let merchant = result.merchant { description = merchant }
                        if let merchant = result.merchant,
                           let guess = CategoryGuess.guess(merchant, among: store.snapshot?.lists.categories ?? []) { category = guess }
                    }
                }
            }
            HStack(spacing: 12) {
                if Money.single { CurrencyPill(code: Money.main) } else { CurrencyMenu(selection: $currency) }
                TextField("0.00", text: $amountText)
                    .keyboardType(.decimalPad)
                    .focused($focus, equals: .amount)
                    .multilineTextAlignment(.trailing)
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .minimumScaleFactor(0.5)
            }
            HStack {
                if currency != Money.main, let qar {
                    Label("Saved as \(SheetFormat.qar(qar)) \(Money.main)", systemImage: "arrow.triangle.2.circlepath")
                        .foregroundStyle(SheetTheme.navy)
                } else if currency != Money.main, book.rate(currency) == nil {
                    Label(book.isRefreshing ? "Getting the \(currency) rate…" : "No \(currency) rate yet — set one in the converter",
                          systemImage: "exclamationmark.circle")
                        .foregroundStyle(SheetTheme.warn)
                }
                Spacer()
                if let qar, !Money.single { Text("≈ \(Money.homeAmount(qar * rate))").foregroundStyle(.secondary) }
            }
            .font(.footnote.weight(.medium))
            if let qar, let cap = store.snapshot?.settings.dailyLimit, qar + spent(on: date) > cap {
                Label("This puts \(SheetDate.weekdayDay(SheetDate.string(date))) over your \(SheetFormat.number(cap)) \(Money.main) daily cap.",
                      systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(SheetTheme.warn)
            }
        }
        .padding(16)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var recentStrip: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Repeat a recent one").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(recents) { item in
                        Button { reuse(item) } label: {
                            HStack(spacing: 8) {
                                IconBadge(symbol: SheetTheme.categoryIcon(item.category), tint: SheetTheme.categoryColor(item.category), size: 28)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(item.description ?? "").font(.caption.weight(.semibold)).lineLimit(1)
                                    Text("\(SheetFormat.qar(item.qar)) \(Money.main)").font(.caption2.monospacedDigit()).foregroundStyle(.secondary)
                                }
                            }
                            .padding(.leading, 6)
                            .padding(.trailing, 12)
                            .padding(.vertical, 6)
                            .background(SheetTheme.card, in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var dateRow: some View {
        let cal = Calendar.current
        let yesterday = cal.date(byAdding: .day, value: -1, to: .now)!
        return HStack(spacing: 8) {
            dateChip("Today", .now, selected: cal.isDateInToday(date))
            dateChip("Yesterday", yesterday, selected: cal.isDateInYesterday(date))
            Spacer(minLength: 0)
            DatePicker("Other day", selection: $date, in: ...Date.now.addingTimeInterval(86_400 * 31), displayedComponents: .date)
                .labelsHidden()
        }
    }

    private func dateChip(_ title: String, _ value: Date, selected: Bool) -> some View {
        Button(title) { date = value }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(selected ? SheetTheme.navy : SheetTheme.input, in: Capsule())
            .foregroundStyle(selected ? .white : .primary)
            .buttonStyle(.plain)
    }

    private var saveBar: some View {
        Button { Task { await save() } } label: {
            Group {
                if store.isSaving {
                    ProgressView().tint(.white)
                } else {
                    Text(expense == nil ? "Add \(qar.map { SheetFormat.qar($0) + " \(Money.main)" } ?? "expense")" : "Save changes")
                }
            }
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .background(canSave ? SheetTheme.navy : Color(.systemGray3), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .foregroundStyle(.white)
        }
        .buttonStyle(.plain)
        .disabled(!canSave)
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .background(.bar)
    }

    private func field<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
            content()
        }
    }

    // MARK: Actions

    private func reuse(_ item: SheetExpense) {
        description = item.description ?? ""
        if let c = item.category { category = c }
        if let m = item.method { method = m }
        currency = Money.main
        amountText = item.qar.map { $0.formatted(.number.precision(.fractionLength(0...2)).grouping(.never)) } ?? ""
    }

    /// What's already in the ledger for that day (excluding the row being edited).
    private func spent(on day: Date) -> Double {
        let key = SheetDate.string(day)
        return (store.snapshot?.expenses ?? [])
            .filter { $0.date == key && $0.row != expense?.row }
            .reduce(0) { $0 + ($1.qar ?? 0) }
    }

    private func save() async {
        guard let qar, let amount else { return }
        var finalNotes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        if currency != Money.main {
            let original = "Paid \(CurrencyBook.format(amount, currency)) \(currency)"
            finalNotes = finalNotes.isEmpty ? original : "\(finalNotes) · \(original)"
        }
        let saved = await store.saveExpense(
            row: expense?.row,
            date: SheetDate.string(date),
            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
            category: category,
            method: method,
            qar: (qar * 100).rounded() / 100,
            notes: finalNotes
        )
        if saved, repeatMonthly {
            let rule = RecurringExpense(description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                                        category: category, method: method, amount: (qar * 100).rounded() / 100,
                                        day: Calendar.current.component(.day, from: date), active: true,
                                        // This month's is the expense just saved.
                                        lastAdded: String(SheetDate.string(date).prefix(7)))
            Task { _ = await store.saveRecurring(rule, addThisMonth: false) }
        }
        if saved { dismiss() }
    }

    static func methodIcon(_ method: String) -> String {
        switch method {
        case "Cash": "banknote.fill"
        case "Debit Card": "creditcard"
        case "Credit Card": "creditcard.fill"
        case "Apple Pay / Google Pay": "iphone"
        case "Bank Transfer": "building.columns.fill"
        default: "ellipsis.circle.fill"
        }
    }
}

/// Tap-to-select chips in a wrapping grid, each with an icon.
struct ChipGrid: View {
    let items: [String]
    @Binding var selection: String
    var minWidth: CGFloat = 110
    let style: (String) -> (symbol: String, tint: Color)

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: minWidth), spacing: 8)], spacing: 8) {
            ForEach(items, id: \.self) { item in
                let (symbol, tint) = style(item)
                let selected = item == selection
                Button { selection = item } label: {
                    HStack(spacing: 6) {
                        Image(systemName: symbol).font(.caption.weight(.semibold))
                        Text(item).font(.caption.weight(.semibold)).lineLimit(2).multilineTextAlignment(.leading)
                        Spacer(minLength: 0)
                    }
                    .foregroundStyle(selected ? .white : tint)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(selected ? tint : tint.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .sensoryFeedback(.selection, trigger: selection)
    }
}
