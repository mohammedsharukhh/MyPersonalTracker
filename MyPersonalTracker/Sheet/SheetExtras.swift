//
//  SheetExtras.swift
//  MyPersonalTracker
//
//  The pieces around the main screens: sync status, the Undo toast, starting
//  the next period, and monthly bills (the sheet's Recurring tab).
//

import SwiftUI

// MARK: - Sync status

/// "2 changes waiting — will sync when you're online", shown above a screen's content.
struct SyncStatusBar: View {
    let store: SheetStore
    @State private var showingQueue = false

    var body: some View {
        if let progress = store.setupProgress {
            SetupProgressCard(progress: progress)
        }
        if !store.pending.isEmpty {
            // A save normally lands in a second or two: only say so if one is taking longer (or there's no connection).
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let ready = store.pending.filter { $0.notBefore == nil || $0.notBefore! <= context.date }
                let slow = ready.contains { context.date.timeIntervalSince(max($0.createdAt, $0.notBefore ?? $0.createdAt)) > 5 }
                bar(waiting: ready.count, show: store.isOffline || slow)
            }
        }
    }

    @ViewBuilder
    private func bar(waiting: Int, show: Bool) -> some View {
        if show {
            if waiting > 0 || store.isOffline {
                Button { showingQueue = true } label: {
                    HStack(spacing: 10) {
                        Image(systemName: store.isOffline ? "icloud.slash.fill" : "arrow.triangle.2.circlepath.icloud.fill")
                            .foregroundStyle(store.isOffline ? SheetTheme.warn : SheetTheme.sky)
                            .symbolEffect(.pulse, isActive: store.isSyncing)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(store.isOffline ? "Offline — \(waiting) change\(waiting == 1 ? "" : "s") saved on this phone"
                                                 : "Syncing \(waiting) change\(waiting == 1 ? "" : "s")…")
                                .font(.subheadline.weight(.semibold))
                            Text(store.isOffline ? "They'll be sent to your sheet automatically when you're back online."
                                                 : "Figures marked with a clock update once the sheet confirms.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                    .padding(12)
                    .background((store.isOffline ? SheetTheme.warn : SheetTheme.sky).opacity(0.1),
                                in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .sheet(isPresented: $showingQueue) { QueueView(store: store) }
            }
        }
    }
}

/// Shown while the sheet rebuilds after a reset (or first setup); the app loads it by itself when done.
private struct SetupProgressCard: View {
    let progress: String

    var body: some View {
        // "5 of 13 steps done" → 5/13.
        let numbers = progress.split(whereSeparator: { !$0.isNumber }).compactMap { Double($0) }
        let fraction = numbers.count >= 2 && numbers[1] > 0 ? numbers[0] / numbers[1] : nil
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                ProgressView()
                Text("Rebuilding your sheet").font(.subheadline.weight(.semibold))
                Spacer()
                if numbers.count >= 2 { Text("Step \(Int(numbers[0])) of \(Int(numbers[1]))").font(.caption.weight(.semibold)).foregroundStyle(.secondary) }
            }
            if let fraction { ProgressView(value: fraction).tint(SheetTheme.navy) }
            Text("This takes about 3–5 minutes. Keep the app open — your fresh sheet loads by itself as soon as it's ready.")
                .font(.caption).foregroundStyle(.secondary)
        }
        .padding(14)
        .background(SheetTheme.navy.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

enum SheetQueueLabels {
    /// "saveShoppingItem" → "Shopping item", "deleteGoalStep" → "Deleted goal step".
    static func readable(_ action: String) -> String {
        let verbs = ["save": "", "delete": "Deleted ", "add": "New ", "update": "Edited "]
        guard let verb = verbs.keys.first(where: { action.hasPrefix($0) }) else { return action }
        let words = action.dropFirst(verb.count).reduce(into: "") { out, c in out += c.isUppercase && !out.isEmpty ? " \(c)" : String(c) }.lowercased()
        let text = verbs[verb]! + words
        return text.prefix(1).uppercased() + text.dropFirst()
    }
}

/// What's waiting to reach the sheet, with a way to drop a change for good.
private struct QueueView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(store.pending) { write in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(write.summary).font(.subheadline.weight(.semibold))
                            Text("\(label(write.action)) · \(write.createdAt.formatted(.relative(presentation: .named)))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        .swipeActions {
                            Button("Discard", systemImage: "trash", role: .destructive) { store.discard(write) }
                        }
                    }
                } footer: {
                    Text("Changes are sent in order. Swipe to discard one you no longer want — it will never reach the sheet.")
                }
            }
            .navigationTitle("Waiting to sync")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .topBarLeading) {
                    Button("Retry now") { Task { await store.flush() } }.disabled(store.isSyncing)
                }
            }
            .overlay { if store.pending.isEmpty { ContentUnavailableView("All synced", systemImage: "checkmark.icloud") } }
        }
        .tint(SheetTheme.navy)
    }

    private func label(_ action: String) -> String {
        switch action {
        case "addExpense": "New expense"
        case "updateExpense": "Edited expense"
        case "deleteExpense": "Deleted expense"
        case "saveDay": "Daily log"
        case "saveSalary": "Income"
        case "saveMeal": "Meal"
        case "deleteMeal": "Deleted meal"
        case "savePlan": "Meal plan"
        case "savePeopleEntry": "Friends & family"
        case "deletePeopleEntry": "Deleted entry"
        default: SheetQueueLabels.readable(action)
        }
    }

}

/// "Deleted Coffee · 14.00  Undo" for a few seconds after a delete.
struct UndoToast: View {
    let store: SheetStore

    var body: some View {
        if let message = store.undoMessage {
            HStack(spacing: 12) {
                Image(systemName: "trash").foregroundStyle(.white.opacity(0.8))
                Text(message).font(.subheadline.weight(.medium)).foregroundStyle(.white).lineLimit(1)
                Spacer(minLength: 8)
                Button("Undo") { withAnimation(.snappy) { store.undoDelete() } }
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Color(hex: 0x93C5FD))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(hex: 0x1F2937), in: Capsule())
            .shadow(color: .black.opacity(0.2), radius: 12, y: 4)
            .padding(.horizontal, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .task(id: message) {
                try? await Task.sleep(for: .seconds(SheetStore.undoWindow))
                if store.undoMessage == message { withAnimation(.snappy) { store.dismissUndo() } }
            }
        }
    }
}

// MARK: - Next period

/// Shown when the period has ended or ends within three days.
struct NewPeriodCard: View {
    let store: SheetStore
    let settings: SheetSettings
    @State private var starting = false

    static func isDue(_ settings: SheetSettings) -> Bool {
        guard let end = SheetDate.date(settings.endDate) else { return false }
        let days = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: .now), to: end).day ?? 0
        return days <= 3
    }

    var body: some View {
        let ended = (SheetDate.date(settings.endDate) ?? .distantFuture) < Calendar.current.startOfDay(for: .now)
        SheetCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    IconBadge(symbol: ended ? "calendar.badge.exclamationmark" : "calendar.badge.clock", tint: ended ? SheetTheme.warn : SheetTheme.navy, size: 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ended ? "This period has ended" : "Period ends \(SheetDate.short(settings.endDate))").font(.headline)
                        Text("Start the next one — this period is kept in an archive in your sheet.").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Button { starting = true } label: {
                    Text("Start next period").font(.headline).frame(maxWidth: .infinity).padding(.vertical, 11)
                }
                .buttonStyle(.borderedProminent)
                .tint(SheetTheme.navy)
                .buttonBorderShape(.roundedRectangle(radius: 12))
            }
        }
        .sheet(isPresented: $starting) { NewPeriodSheet(store: store, settings: settings) }
    }
}

struct NewPeriodSheet: View {
    let store: SheetStore
    let settings: SheetSettings
    @Environment(\.dismiss) private var dismiss
    @State private var start: Date
    @State private var end: Date
    @State private var working = false

    init(store: SheetStore, settings: SheetSettings) {
        self.store = store
        self.settings = settings
        let cal = Calendar.current
        let oldStart = SheetDate.date(settings.startDate) ?? .now
        let oldEnd = SheetDate.date(settings.endDate) ?? .now
        let length = max(1, (cal.dateComponents([.day], from: oldStart, to: oldEnd).day ?? 30))
        // Next period: the day after this one ends (or today, if that's long gone), same length.
        let nextStart = max(cal.date(byAdding: .day, value: 1, to: oldEnd) ?? .now,
                            cal.date(byAdding: .day, value: -7, to: cal.startOfDay(for: .now)) ?? .now)
        _start = State(initialValue: nextStart)
        _end = State(initialValue: cal.date(byAdding: .day, value: length, to: nextStart) ?? nextStart)
    }

    private var days: Int { (Calendar.current.dateComponents([.day], from: start, to: end).day ?? 0) + 1 }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Starts", selection: $start, displayedComponents: .date)
                    DatePicker("Ends", selection: $end, in: start..., displayedComponents: .date)
                    LabeledContent("Length", value: "\(days) days")
                } header: {
                    Text("New period")
                } footer: {
                    Text("Quick picks: ").font(.caption) +
                    Text("use the buttons below to match a calendar month.").font(.caption)
                }
                Section {
                    Button("This calendar month") { setMonth(offset: 0) }
                    Button("Next calendar month") { setMonth(offset: 1) }
                }
                Section {
                    Label("Your current period (\(SheetDate.short(settings.startDate)) – \(SheetDate.short(settings.endDate))) is copied to “Archive” tabs in the sheet.", systemImage: "archivebox")
                    Label("Days, expenses and income inside the new dates are kept.", systemImage: "checkmark.circle")
                    Label("Targets, limits, recurring bills and currency stay as they are.", systemImage: "slider.horizontal.3")
                }
                .font(.subheadline)
                if let error = store.errorMessage, !working {
                    Section { Text(error).font(.footnote).foregroundStyle(SheetTheme.bad) }
                }
            }
            .navigationTitle("Start next period")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(working) }
                ToolbarItem(placement: .confirmationAction) {
                    if working { ProgressView() } else {
                        Button("Start") {
                            working = true
                            Task {
                                let ok = await store.startNextPeriod(start: SheetDate.string(start), end: SheetDate.string(end))
                                working = false
                                if ok { dismiss() }
                            }
                        }
                        .fontWeight(.semibold)
                        .disabled(days < 1)
                    }
                }
            }
            .overlay {
                if working {
                    VStack(spacing: 12) {
                        ProgressView().controlSize(.large)
                        Text("Archiving and rebuilding your sheet…").font(.headline)
                        Text("This takes up to a minute.").font(.caption).foregroundStyle(.secondary)
                    }
                    .padding(28)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                }
            }
            .interactiveDismissDisabled(working)
        }
        .tint(SheetTheme.navy)
    }

    private func setMonth(offset: Int) {
        let cal = Calendar.current
        guard let month = cal.date(byAdding: .month, value: offset, to: .now),
              let interval = cal.dateInterval(of: .month, for: month) else { return }
        start = interval.start
        end = cal.date(byAdding: .day, value: -1, to: interval.end) ?? interval.end
    }
}

// MARK: - Monthly bills

struct RecurringListView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var editing: RecurringExpense?
    @State private var adding = false

    var body: some View {
        NavigationStack {
            List {
                let rules = store.snapshot?.recurring ?? []
                if rules.isEmpty {
                    ContentUnavailableView("No monthly bills yet", systemImage: "repeat",
                                           description: Text("Add rent, your phone plan or a gym membership once — it's added to your ledger on its day every month."))
                }
                ForEach(rules) { rule in
                    Button { editing = rule } label: {
                        HStack(spacing: 12) {
                            IconBadge(symbol: SheetTheme.categoryIcon(rule.category), tint: SheetTheme.categoryColor(rule.category), size: 36)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(rule.description ?? rule.category ?? "Bill").font(.subheadline.weight(.semibold))
                                Text("Every month on the \(ordinal(rule.day ?? 1))\(rule.active ? "" : " · paused")")
                                    .font(.caption).foregroundStyle(rule.active ? .secondary : SheetTheme.warn)
                            }
                            Spacer()
                            Text(SheetFormat.qar(rule.amount)).font(.subheadline.weight(.bold)).monospacedDigit()
                        }
                        .opacity(rule.active ? 1 : 0.55)
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("Monthly bills")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add", systemImage: "plus") { adding = true }
                }
            }
            .sheet(item: $editing) { RecurringEditor(store: store, rule: $0) }
            .sheet(isPresented: $adding) { RecurringEditor(store: store, rule: nil) }
        }
        .tint(SheetTheme.navy)
    }
}

func ordinal(_ n: Int) -> String {
    let suffix = (11...13).contains(n % 100) ? "th" : (["th", "st", "nd", "rd"] + Array(repeating: "th", count: 6))[n % 10]
    return "\(n)\(suffix)"
}

struct RecurringEditor: View {
    let store: SheetStore
    let original: RecurringExpense?
    @Environment(\.dismiss) private var dismiss
    @State private var rule: RecurringExpense
    @State private var addThisMonth = false
    @State private var confirmDelete = false
    @State private var working = false

    init(store: SheetStore, rule: RecurringExpense?) {
        self.store = store
        self.original = rule
        let lists = store.snapshot?.lists
        _rule = State(initialValue: rule ?? RecurringExpense(
            category: lists?.categories.first { $0 == "Rent & Utilities" } ?? lists?.categories.first,
            method: lists?.methods.first, day: 1))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    NumberRow(title: "Amount", unit: Money.main, value: $rule.amount, hint: nil)
                    TextField("What is it? e.g. Rent", text: Binding(get: { rule.description ?? "" }, set: { rule.description = $0 }))
                    Picker("Day of month", selection: Binding(get: { rule.day ?? 1 }, set: { rule.day = $0 })) {
                        ForEach(1...31, id: \.self) { Text(ordinal($0)).tag($0) }
                    }
                } footer: {
                    Text("Days that don't exist in a month (like the 31st) use the last day instead.")
                }
                Section("Category") {
                    ChipGrid(items: store.snapshot?.lists.categories ?? [],
                             selection: Binding(get: { rule.category ?? "" }, set: { rule.category = $0 }), minWidth: 104) { item in
                        (SheetTheme.categoryIcon(item), SheetTheme.categoryColor(item))
                    }
                    .listRowInsets(EdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12))
                }
                Section("Paid with") {
                    Picker("Payment method", selection: Binding(get: { rule.method ?? "" }, set: { rule.method = $0 })) {
                        ForEach(store.snapshot?.lists.methods ?? [], id: \.self) { Text($0) }
                    }
                }
                Section {
                    Toggle("Active", isOn: $rule.active)
                    if original == nil {
                        Toggle("Also add this month's now", isOn: $addThisMonth)
                    }
                } footer: {
                    Text(original == nil ? "Leave off if you've already logged this month's payment." : "Pause a bill to stop it being added without deleting it.")
                }
                if original != nil {
                    Section {
                        Button("Delete bill", role: .destructive) { confirmDelete = true }
                    }
                }
                if let error = store.errorMessage, !working {
                    Section { Text(error).font(.footnote).foregroundStyle(SheetTheme.bad) }
                }
            }
            .navigationTitle(original == nil ? "New monthly bill" : "Monthly bill")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if working { ProgressView() } else {
                        Button("Save") {
                            working = true
                            Task {
                                let ok = await store.saveRecurring(rule, addThisMonth: addThisMonth)
                                working = false
                                if ok { dismiss() }
                            }
                        }
                        .fontWeight(.semibold)
                        .disabled((rule.amount ?? 0) <= 0)
                    }
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete this bill?", message: "Expenses it already added stay in your ledger.") {
                guard let row = original?.row else { return }
                Task { if await store.deleteRecurring(row: row) { dismiss() } }
            })
        }
        .tint(SheetTheme.navy)
    }
}
