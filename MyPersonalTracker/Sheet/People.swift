//
//  People.swift
//  MyPersonalTracker
//
//  Money with friends & family: what you lent, borrowed, got back, paid back
//  and gave as gifts — with a running balance per person. Gifts you give also
//  count as spending in Finance (Gifts & Family).
//

import SwiftUI

struct PeopleEntry: Codable, Hashable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case lent = "Lent", gotBack = "Got back", borrowed = "Borrowed", paidBack = "Paid back"
        case giftGiven = "Gift given", giftGot = "Gift got"
        var id: String { rawValue }

        var symbol: String {
            switch self {
            case .lent: "arrow.up.right.circle.fill"
            case .gotBack: "arrow.down.left.circle.fill"
            case .borrowed: "arrow.down.right.circle.fill"
            case .paidBack: "arrow.up.left.circle.fill"
            case .giftGiven: "gift.fill"
            case .giftGot: "gift"
            }
        }

        var explanation: String {
            switch self {
            case .lent: "You gave money they'll pay back"
            case .gotBack: "They paid you back"
            case .borrowed: "They gave you money you'll pay back"
            case .paidBack: "You paid them back"
            case .giftGiven: "A gift from you — counts as spending"
            case .giftGot: "A gift to you"
            }
        }

        /// How it moves the balance (positive = they owe you).
        var sign: Double {
            switch self {
            case .lent, .paidBack: 1
            case .gotBack, .borrowed: -1
            case .giftGiven, .giftGot: 0
            }
        }

        var tint: Color {
            switch self {
            case .lent, .paidBack: SheetTheme.warn
            case .gotBack, .borrowed: SheetTheme.sky
            case .giftGiven: SheetTheme.categoryColor("Gifts & Family")
            case .giftGot: SheetTheme.good
            }
        }
    }

    var id: String
    var date: String
    var person: String
    var type: Kind
    var amount: Double
    var note: String?
    /// Shown with a clock until the sheet confirms it.
    var pending = false

    enum CodingKeys: String, CodingKey { case id, date, person, type, amount, note }

    init(id: String = UUID().uuidString, date: String, person: String, type: Kind, amount: Double, note: String?, pending: Bool = false) {
        self.id = id; self.date = date; self.person = person; self.type = type; self.amount = amount; self.note = note; self.pending = pending
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        date = c.flexString(.date) ?? ""
        person = c.flexString(.person) ?? ""
        type = c.flexString(.type).flatMap(Kind.init(rawValue:)) ?? .giftGot
        amount = c.flexDouble(.amount) ?? 0
        note = c.flexString(.note)
    }
}

/// One person's balance and history.
struct PersonSummary: Identifiable {
    let name: String
    let entries: [PeopleEntry]
    var id: String { name.lowercased() }
    /// Positive: they owe you. Negative: you owe them.
    var balance: Double { entries.reduce(0) { $0 + $1.type.sign * $1.amount } }
    var gifted: Double { entries.filter { $0.type == .giftGiven }.reduce(0) { $0 + $1.amount } }

    static func all(_ entries: [PeopleEntry]) -> [PersonSummary] {
        Dictionary(grouping: entries, by: { $0.person.trimmingCharacters(in: .whitespaces).lowercased() })
            .map { PersonSummary(name: $0.value.sorted { $0.date > $1.date }.first?.person ?? $0.key, entries: $0.value.sorted { $0.date > $1.date }) }
            .sorted { abs($0.balance) == abs($1.balance) ? $0.name < $1.name : abs($0.balance) > abs($1.balance) }
    }
}

/// The People section: who owes whom, then each person.
struct PeopleContent: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var adding: PeopleEntry?
    @State private var person: PersonSummary?

    var body: some View {
        let people = PersonSummary.all(snapshot.people ?? [])
        let owedToYou = people.map(\.balance).filter { $0 > 0 }.reduce(0, +)
        let youOwe = people.filter { $0.balance < 0 }.reduce(0) { $0 - $1.balance }

        HStack(spacing: 12) {
            SheetStatTile(symbol: "arrow.down.left.circle.fill", tint: SheetTheme.good, title: "Owed to you", value: SheetFormat.qar(owedToYou),
                          caption: "\(people.filter { $0.balance > 0 }.count) people · \(Money.main)")
            SheetStatTile(symbol: "arrow.up.right.circle.fill", tint: SheetTheme.bad, title: "You owe", value: SheetFormat.qar(youOwe),
                          caption: "\(people.filter { $0.balance < 0 }.count) people · \(Money.main)")
        }
        Button { adding = PeopleEntry(date: SheetDate.today, person: "", type: .lent, amount: 0, note: nil) } label: {
            Label("Add money with someone", systemImage: "plus.circle.fill")
                .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 13)
                .background(SheetTheme.navy, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .foregroundStyle(.white)
        }
        .buttonStyle(.plain)

        if people.isEmpty {
            SheetCard {
                Text("Track money you lend, borrow and give to friends and family. Each person gets a running balance, and gifts you give count as spending in Finance.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        ForEach(people) { p in
            Button { person = p } label: {
                HStack(spacing: 12) {
                    Text(String(p.name.prefix(1)).uppercased())
                        .font(.headline).foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(SheetTheme.indigo, in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(p.name).font(.subheadline.weight(.semibold))
                        Text(p.balance > 0 ? "Owes you" : (p.balance < 0 ? "You owe" : (p.gifted > 0 ? "Gifts \(SheetFormat.qar(p.gifted))" : "Settled up")))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if p.balance != 0 {
                        Text(SheetFormat.qar(abs(p.balance))).font(.headline.monospacedDigit())
                            .foregroundStyle(p.balance > 0 ? SheetTheme.good : SheetTheme.bad)
                    } else {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(SheetTheme.good)
                    }
                    Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                }
                .padding(14)
                .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        Color.clear.frame(height: 0)
            .sheet(item: $adding) { PeopleEntryEditor(store: store, entry: $0, isNew: true) }
            .sheet(item: $person) { p in PersonDetail(store: store, name: p.name) }
    }
}

/// Full screen (from the Dashboard or More).
struct PeopleView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    SyncStatusBar(store: store)
                    if let snapshot = store.snapshot { PeopleContent(store: store, snapshot: snapshot) } else { NotConnectedHint() }
                }
                .padding(16)
            }
            .background(SheetTheme.page)
            .navigationTitle("Friends & family")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .tint(SheetTheme.navy)
    }
}

/// One person's balance and every entry with them.
private struct PersonDetail: View {
    let store: SheetStore
    let name: String
    @Environment(\.dismiss) private var dismiss
    @State private var editing: PeopleEntry?
    @State private var adding: PeopleEntry?

    var body: some View {
        let entries = (store.snapshot?.people ?? []).filter { $0.person.lowercased() == name.lowercased() }.sorted { $0.date > $1.date }
        let summary = PersonSummary(name: name, entries: entries)
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(summary.balance > 0 ? "\(name) owes you" : (summary.balance < 0 ? "You owe \(name)" : "All settled"))
                            .font(.subheadline).foregroundStyle(.secondary)
                        Text("\(SheetFormat.qar(abs(summary.balance))) \(Money.main)")
                            .font(.system(.largeTitle, design: .rounded, weight: .bold))
                            .foregroundStyle(summary.balance > 0 ? SheetTheme.good : (summary.balance < 0 ? SheetTheme.bad : .primary))
                        if summary.gifted > 0 { Text("Gifts given: \(SheetFormat.qar(summary.gifted))").font(.caption).foregroundStyle(.secondary) }
                    }
                    .padding(.vertical, 6)
                    if summary.balance != 0 {
                        Button {
                            adding = PeopleEntry(date: SheetDate.today, person: name, type: summary.balance > 0 ? .gotBack : .paidBack,
                                                 amount: abs(summary.balance), note: nil)
                        } label: {
                            Label(summary.balance > 0 ? "They paid me back" : "I paid them back", systemImage: "checkmark.circle")
                        }
                    }
                    Button { adding = PeopleEntry(date: SheetDate.today, person: name, type: .lent, amount: 0, note: nil) } label: {
                        Label("Add entry with \(name)", systemImage: "plus.circle")
                    }
                }
                Section("History") {
                    ForEach(entries) { entry in
                        Button { editing = entry } label: {
                            HStack(spacing: 12) {
                                Image(systemName: entry.type.symbol).foregroundStyle(entry.type.tint).font(.title3)
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 4) {
                                        Text(entry.type.rawValue).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                                        if entry.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                                    }
                                    Text([SheetDate.short(entry.date), entry.note].compactMap { $0 }.joined(separator: " · "))
                                        .font(.caption).foregroundStyle(Color(.secondaryLabel)).lineLimit(1)
                                }
                                Spacer()
                                Text(SheetFormat.qar(entry.amount)).font(.subheadline.weight(.bold).monospacedDigit()).foregroundStyle(Color(.label))
                            }
                        }
                        .swipeActions {
                            Button("Delete", systemImage: "trash", role: .destructive) { store.deletePeopleEntry(id: entry.id) }
                        }
                    }
                }
            }
            .navigationTitle(name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .sheet(item: $editing) { PeopleEntryEditor(store: store, entry: $0, isNew: false) }
            .sheet(item: $adding) { PeopleEntryEditor(store: store, entry: $0, isNew: true) }
        }
        .tint(SheetTheme.navy)
    }
}

/// Add or edit one entry: who, what kind, how much, when.
struct PeopleEntryEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var entry: PeopleEntry
    @State private var amountText: String
    @State private var currency = Money.main
    @State private var date: Date
    @State private var method: String
    @State private var book = CurrencyBook.shared

    init(store: SheetStore, entry: PeopleEntry, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _entry = State(initialValue: entry)
        _amountText = State(initialValue: entry.amount > 0 ? entry.amount.formatted(.number.precision(.fractionLength(0...2)).grouping(.never)) : "")
        _date = State(initialValue: SheetDate.date(entry.date) ?? .now)
        _method = State(initialValue: store.snapshot?.lists.methods.first ?? "Cash")
    }

    private var known: [String] { PersonSummary.all(store.snapshot?.people ?? []).map(\.name) }
    private var amount: Double? {
        guard let typed = Double(amountText.replacingOccurrences(of: ",", with: ".")), typed > 0 else { return nil }
        return book.convert(typed, from: currency, to: Money.main)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Who") {
                    TextField("Name, e.g. Ammi, Rahul", text: $entry.person).textInputAutocapitalization(.words)
                    if !known.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(known, id: \.self) { name in
                                    Button(name) { entry.person = name }
                                        .font(.caption.weight(.semibold))
                                        .padding(.horizontal, 12).padding(.vertical, 7)
                                        .background(entry.person == name ? SheetTheme.navy : SheetTheme.input, in: Capsule())
                                        .foregroundStyle(entry.person == name ? .white : .primary)
                                        .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
                Section {
                    ForEach(PeopleEntry.Kind.allCases) { kind in
                        Button { entry.type = kind } label: {
                            HStack(spacing: 12) {
                                Image(systemName: kind.symbol).foregroundStyle(kind.tint).font(.title3).frame(width: 28)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(kind.rawValue).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                                    Text(kind.explanation).font(.caption).foregroundStyle(Color(.secondaryLabel))
                                }
                                Spacer()
                                if entry.type == kind { Image(systemName: "checkmark").foregroundStyle(SheetTheme.navy).fontWeight(.bold) }
                            }
                        }
                    }
                } header: { Text("What happened") }
                Section {
                    HStack {
                        if Money.single { CurrencyPill(code: Money.main) } else { CurrencyMenu(selection: $currency) }
                        TextField("0.00", text: $amountText).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                            .font(.system(.title2, design: .rounded, weight: .bold))
                    }
                    if currency != Money.main, let amount { Text("Saved as \(SheetFormat.qar(amount)) \(Money.main)").font(.caption).foregroundStyle(SheetTheme.navy) }
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                    if entry.type == .giftGiven {
                        Picker("Paid with", selection: $method) {
                            ForEach(store.snapshot?.lists.methods ?? ["Cash"], id: \.self) { Text($0) }
                        }
                    }
                    TextField("Note (optional), e.g. for rent, Eid gift", text: Binding(get: { entry.note ?? "" }, set: { entry.note = $0 }))
                } header: { Text("Amount") } footer: {
                    Text(entry.type == .giftGiven ? "Also added to Finance as Gifts & Family spending." : "")
                }
                if !isNew {
                    Section { Button("Delete entry", role: .destructive) { store.deletePeopleEntry(id: entry.id); dismiss() } }
                }
            }
            .navigationTitle(isNew ? "Money with someone" : "Edit entry")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let amount else { return }
                        var saved = entry
                        saved.person = entry.person.trimmingCharacters(in: .whitespaces)
                        saved.amount = (amount * 100).rounded() / 100
                        saved.date = SheetDate.string(date)
                        store.savePeopleEntry(saved, method: method)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(entry.person.trimmingCharacters(in: .whitespaces).isEmpty || amount == nil)
                }
            }
        }
        .tint(SheetTheme.navy)
    }
}
