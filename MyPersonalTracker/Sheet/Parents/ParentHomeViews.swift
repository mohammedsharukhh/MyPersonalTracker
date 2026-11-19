//
//  ParentHomeViews.swift
//  MyPersonalTracker
//
//  Parents ▸ Trip (what to bring home, counting down to the trip), Parents ▸
//  Health (their readings, a chart, and a summary for the doctor), and the
//  "this month for Mom & Dad" card.
//

import Charts
import SwiftUI

// MARK: - This month for them

struct ParentMoneyCard: View {
    let money: ParentMoney

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text("This month for Mom & Dad").font(.subheadline.weight(.semibold))
                Spacer()
                Text("\(SheetFormat.qar(money.total)) \(Money.main)").font(.headline.monospacedDigit())
            }
            HStack(spacing: 16) {
                part("Sent home", money.sent, home: money.sentHome)
                part("Spent for them", money.spent, home: nil)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    private func part(_ title: String, _ value: Double, home: Double?) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text("\(SheetFormat.qar(value)) \(Money.main)").font(.subheadline.monospacedDigit())
            if let home, home > 0, !Money.single { Text("≈ \(Money.homeAmount(home))").font(.caption2).foregroundStyle(.secondary) }
        }
    }
}

// MARK: - Trip home

struct TakeHomeSections: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    let who: ParentAsk.Who?
    @State private var newItem = ""
    @State private var choosingTrip = false
    @State private var tripDate = Calendar.current.date(byAdding: .day, value: 30, to: .now) ?? .now
    @State private var editing: TakeHomeItem?
    @FocusState private var typing: Bool

    var body: some View {
        let data = snapshot.takeHomeData
        let items = data.sorted.filter { who == nil || $0.who == who || $0.who == .both }
        Section {
            if let countdown = data.countdown() {
                HStack(spacing: 12) {
                    IconBadge(symbol: "airplane", tint: (data.daysToTrip() ?? 99) <= 7 ? SheetTheme.warn : SheetTheme.sky, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(countdown).font(.headline)
                        Text("\(SheetDate.short(data.trip)) · \(data.toGet == 0 ? (data.items.isEmpty ? "nothing on the list yet" : "everything's got ✓") : "\(data.toGet) still to get")")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Menu {
                        Button("Change the date", systemImage: "calendar") { tripDate = SheetDate.date(data.trip) ?? tripDate; choosingTrip = true }
                        Button("No trip planned", systemImage: "xmark.circle", role: .destructive) {
                            store.saveTakeHomeTrip(nil)
                            Task { await ReminderScheduler.scheduleSheetAlerts() }
                        }
                    } label: { Image(systemName: "ellipsis.circle").font(.title3).frame(width: 44, height: 44) }
                    .accessibilityLabel("Trip options")
                }
            } else {
                Button { choosingTrip = true } label: {
                    Label("When's your next trip home? Set the date for a countdown", systemImage: "airplane.departure").font(.subheadline)
                }
            }
        } footer: {
            Text("Add what Mom and Dad want you to bring as they ask. The daily Mom & Dad notification reminds you two weeks, a week, 3 days and a day before if there's still something to get.")
        }
        Section {
            HStack {
                TextField("Add something to bring, e.g. dates and saffron", text: $newItem)
                    .focused($typing)
                    .submitLabel(.done)
                    .onSubmit(add)
                if !newItem.trimmingCharacters(in: .whitespaces).isEmpty {
                    Button("Add", action: add).fontWeight(.semibold)
                }
            }
            ForEach(items) { item in
                HStack(spacing: 8) {
                    Button {
                        var copy = item
                        copy.got.toggle()
                        store.saveTakeHomeItem(copy)
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                    } label: {
                        Image(systemName: item.got ? "checkmark.circle.fill" : "circle").font(.title2)
                            .foregroundStyle(item.got ? SheetTheme.good : SheetTheme.navy).frame(width: 44, height: 44)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(item.got ? "Got it" : "Not got yet")
                    .accessibilityValue(item.item)
                    Button { editing = item } label: {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(item.item).font(.subheadline).foregroundStyle(Color(.label)).strikethrough(item.got)
                            Text([item.who.label, item.notes].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if item.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                }
                .swipeActions {
                    Button("Delete", systemImage: "trash", role: .destructive) { store.deleteTakeHomeItem(id: item.id) }
                }
            }
        } header: {
            Text("To take home")
        }
        .sheet(isPresented: $choosingTrip) {
            NavigationStack {
                Form { DatePicker("Trip home", selection: $tripDate, in: Date.now..., displayedComponents: .date).datePickerStyle(.graphical) }
                    .navigationTitle("Next trip home")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("Cancel") { choosingTrip = false } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Save") {
                                store.saveTakeHomeTrip(SheetDate.string(tripDate))
                                Task { await ReminderScheduler.scheduleSheetAlerts() }
                                choosingTrip = false
                            }
                            .fontWeight(.semibold)
                        }
                    }
            }
            .presentationDetents([.large])
            .tint(SheetTheme.navy)
        }
        .sheet(item: $editing) { TakeHomeItemEditor(store: store, item: $0) }
    }

    private func add() {
        let text = newItem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        store.saveTakeHomeItem(TakeHomeItem(item: text, who: who ?? .both))
        newItem = ""
        Task { await ReminderScheduler.scheduleSheetAlerts() }
    }
}

struct TakeHomeItemEditor: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var item: TakeHomeItem

    init(store: SheetStore, item: TakeHomeItem) {
        self.store = store
        _item = State(initialValue: item)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("What to bring", text: $item.item)
                    Picker("For", selection: $item.who) { ForEach(ParentAsk.Who.allCases) { Text($0.label).tag($0) } }.pickerStyle(.segmented)
                    Toggle("Got it", isOn: $item.got)
                }
                Section {
                    TextField("Notes (optional) — brand, size, where to buy…", text: Binding(get: { item.notes ?? "" }, set: { item.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                Section { Button("Delete", role: .destructive) { store.deleteTakeHomeItem(id: item.id); dismiss() } }
            }
            .navigationTitle(item.item)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = item
                        saved.item = item.item.trimmingCharacters(in: .whitespacesAndNewlines)
                        store.saveTakeHomeItem(saved)
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(item.item.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
        .tint(SheetTheme.navy)
    }
}

// MARK: - Health

struct ParentHealthSections: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    /// Readings are one person's; "Everyone" shows Dad's.
    let who: ParentAsk.Who
    @State private var kind = ParentReading.Kind.bloodPressure
    @State private var editing: ParentReading?

    var body: some View {
        let list = snapshot.readings(kind, who: who)
        let recent = Array(list.suffix(30))
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ParentReading.Kind.allCases) { k in
                        FilterChip(title: k.short, symbol: k.symbol, tint: SheetTheme.navy, selected: kind == k) { kind = k }
                    }
                }
            }
            if recent.count >= 2 {
                chart(recent)
                    .frame(height: 180)
                    .padding(.vertical, 4)
            } else {
                Text(list.isEmpty ? "No \(kind.short.lowercased()) readings for \(who.rawValue) yet. Add one when they tell you — after two you'll see the trend here."
                                  : "Add one more reading to see the trend.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            Button { editing = ParentReading(who: who, kind: kind) } label: {
                Label("Add \(who.rawValue)'s \(kind.short.lowercased())", systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold))
            }
        } header: {
            Text("\(who.rawValue)'s health")
        } footer: {
            Text("As they tell you, to see how it's going and share with their doctor. It doesn't judge the numbers — ask the doctor what's right for them.")
        }
        if !list.isEmpty {
            Section {
                ForEach(list.reversed().prefix(40)) { r in
                    Button { editing = r } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(r.display).font(.subheadline.weight(.semibold).monospacedDigit()).foregroundStyle(Color(.label))
                                Text([SheetDate.weekdayDay(r.date), PlanTime.display(r.time), r.note].compactMap { $0 }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if r.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            } header: {
                HStack {
                    Text("Readings")
                    Spacer()
                    ShareLink(item: ParentHealthShare.text(snapshot, who: who)) {
                        Label("Share for the doctor", systemImage: "square.and.arrow.up").font(.caption.weight(.semibold))
                    }
                    .textCase(nil)
                }
            }
        }
        Color.clear.frame(height: 0).listRowBackground(Color.clear)
            .sheet(item: $editing) { r in
                ParentReadingEditor(store: store, reading: r, isNew: !(snapshot.parentHealth ?? []).contains { $0.id == r.id })
            }
    }

    private func chart(_ list: [ParentReading]) -> some View {
        Chart {
            ForEach(list) { r in
                if let day = SheetDate.date(r.date) {
                    LineMark(x: .value("Day", day), y: .value(kind.hasSecond ? "Systolic" : kind.short, r.value), series: .value("Line", "1"))
                        .foregroundStyle(SheetTheme.bad)
                    PointMark(x: .value("Day", day), y: .value(kind.short, r.value)).foregroundStyle(SheetTheme.bad)
                    if let v2 = r.value2, kind.hasSecond {
                        LineMark(x: .value("Day", day), y: .value("Diastolic", v2), series: .value("Line", "2"))
                            .foregroundStyle(SheetTheme.sky)
                        PointMark(x: .value("Day", day), y: .value("Diastolic", v2)).foregroundStyle(SheetTheme.sky)
                    }
                }
            }
        }
        .chartYScale(domain: .automatic(includesZero: false))
        .accessibilityLabel("\(kind.rawValue) over time")
        .accessibilityValue(list.map { "\(SheetDate.weekdayDay($0.date)) \($0.display)" }.joined(separator: ", "))
    }
}

struct ParentReadingEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var r: ParentReading
    @State private var day: Date
    @State private var value: Double?
    @State private var value2: Double?
    @State private var withTime: Bool
    @State private var time: Date

    init(store: SheetStore, reading: ParentReading, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _r = State(initialValue: reading)
        _day = State(initialValue: SheetDate.date(reading.date) ?? .now)
        _value = State(initialValue: isNew ? nil : reading.value)
        _value2 = State(initialValue: reading.value2)
        _withTime = State(initialValue: reading.time != nil || isNew)
        _time = State(initialValue: PlanTime.date(reading.time) ?? .now)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Whose", selection: $r.who) {
                        Text("Mom").tag(ParentAsk.Who.mom)
                        Text("Dad").tag(ParentAsk.Who.dad)
                    }
                    .pickerStyle(.segmented)
                    Picker("Reading", selection: $r.kind) {
                        ForEach(ParentReading.Kind.allCases) { Label($0.rawValue, systemImage: $0.symbol).tag($0) }
                    }
                }
                Section {
                    number(r.kind.hasSecond ? "Top (systolic)" : r.kind.rawValue, $value)
                    if r.kind.hasSecond { number("Bottom (diastolic)", $value2) }
                } footer: {
                    Text("In \(r.kind.unit)\(r.kind.hasSecond ? ", e.g. 130 over 85" : "").")
                }
                Section {
                    DatePicker("Day", selection: $day, in: ...Date.now, displayedComponents: .date)
                    Toggle("Time", isOn: $withTime)
                    if withTime { DatePicker("At", selection: $time, displayedComponents: .hourAndMinute) }
                    TextField("Note (optional) — after a walk, missed a tablet…", text: Binding(get: { r.note ?? "" }, set: { r.note = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                if !isNew {
                    Section { Button("Delete", role: .destructive) { store.deleteParentReading(id: r.id); dismiss() } }
                }
            }
            .navigationTitle(isNew ? "New reading" : r.kind.rawValue)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = r
                        saved.value = value ?? 0
                        saved.value2 = r.kind.hasSecond ? value2 : nil
                        saved.date = SheetDate.string(day)
                        saved.time = withTime ? PlanTime.text(time) : nil
                        store.saveParentReading(saved)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled((value ?? 0) <= 0 || (r.kind.hasSecond && (value2 ?? 0) <= 0))
                }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func number(_ title: String, _ binding: Binding<Double?>) -> some View {
        HStack {
            Text(title)
            Spacer()
            TextField("0", value: binding, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
            Text(r.kind.unit).foregroundStyle(.secondary)
        }
    }
}
