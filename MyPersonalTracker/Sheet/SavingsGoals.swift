//
//  SavingsGoals.swift
//  MyPersonalTracker
//
//  Named things you're saving for (22_Goals.gs): a target, an optional date,
//  and the money you put aside for each. Finance ▸ Savings.
//

import SwiftUI

/// Finance ▸ Savings: your goals, with progress and what each month needs.
struct SavingsGoalsSection: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var editing: SavingsGoal?
    @State private var opened: SavingsGoal?

    var body: some View {
        let active = snapshot.goals.filter { !$0.done }
        let reached = snapshot.goals.filter(\.done)
        HStack(alignment: .bottom) {
            SectionTitle(title: "Savings goals", subtitle: active.isEmpty ? "Save toward something you want" : nil)
            Button { editing = SavingsGoal() } label: { Label("New goal", systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold)) }
        }
        if snapshot.goals.isEmpty {
            SheetCard {
                Text("Give your savings a purpose — a trip, a phone, a deposit. Set a target and a date, then choose the goal when you put money aside; it fills up as you go.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        ForEach(active) { goal in
            Button { opened = goal } label: { SavingsGoalRow(goal: goal, progress: snapshot.progress(of: goal)) }.buttonStyle(.plain)
        }
        if !reached.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Reached").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                    ForEach(reached) { goal in
                        Button { opened = goal } label: {
                            HStack {
                                Image(systemName: "checkmark.seal.fill").foregroundStyle(SheetTheme.good)
                                Text(goal.name).foregroundStyle(Color(.label))
                                Spacer()
                                Text(SheetFormat.qar(goal.target)).monospacedDigit().foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        Color.clear.frame(height: 0)
            .sheet(item: $editing) { goal in SavingsGoalEditor(store: store, goal: goal, isNew: !snapshot.goals.contains { $0.id == goal.id }) }
            .sheet(item: $opened) { SavingsGoalDetail(store: store, goalId: $0.id) }
    }
}

private struct SavingsGoalRow: View {
    let goal: SavingsGoal
    let progress: SavingsGoalProgress

    var body: some View {
        let done = progress.fraction >= 1
        SheetCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    IconBadge(symbol: done ? "checkmark.seal.fill" : "target", tint: done ? SheetTheme.good : SheetTheme.indigo, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(goal.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                        Text(detail).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(SheetFormat.percent(min(progress.fraction, 9.99), 0)).font(.headline.monospacedDigit())
                        .foregroundStyle(done ? SheetTheme.good : SheetTheme.indigo)
                }
                Capsule().fill(Color(.tertiarySystemFill)).frame(height: 8)
                    .overlay(alignment: .leading) {
                        GeometryReader { geo in
                            Capsule().fill(done ? SheetTheme.good : SheetTheme.indigo).frame(width: geo.size.width * min(progress.fraction, 1))
                        }
                    }
                HStack {
                    Text("\(SheetFormat.qar(progress.saved)) of \(SheetFormat.qar(goal.target)) \(Money.main)").font(.caption.weight(.semibold))
                    Spacer()
                    if let perMonth = progress.perMonth {
                        Text("about \(SheetFormat.qar(perMonth)) a month").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var detail: String {
        if progress.fraction >= 1 { return "Target reached — mark it done" }
        guard let due = goal.due, let days = StudyData.daysUntil(due) else { return "\(SheetFormat.qar(progress.left)) to go" }
        return days < 0 ? "Date passed · \(SheetFormat.qar(progress.left)) to go" : "By \(SheetDate.short(due)) · \(SheetFormat.qar(progress.left)) to go"
    }
}

/// One goal: progress, the money put toward it, and a way to add more.
private struct SavingsGoalDetail: View {
    let store: SheetStore
    let goalId: String
    @Environment(\.dismiss) private var dismiss
    @State private var putting = false
    @State private var editing = false
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            if let snapshot = store.snapshot, let goal = snapshot.goals.first(where: { $0.id == goalId }) {
                let progress = snapshot.progress(of: goal)
                let entries = (snapshot.setAside ?? []).filter { $0.goalId == goal.id }.sorted { $0.date > $1.date }
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(SheetFormat.qar(progress.saved)) \(Money.main)").font(.system(.largeTitle, design: .rounded, weight: .bold))
                            Text("of \(SheetFormat.qar(goal.target)) · \(SheetFormat.percent(min(progress.fraction, 9.99), 0))").foregroundStyle(.secondary)
                            ProgressView(value: min(progress.fraction, 1)).tint(progress.fraction >= 1 ? SheetTheme.good : SheetTheme.indigo)
                        }
                        .padding(.vertical, 4)
                        if let due = goal.due { LabeledContent("By", value: SheetDate.short(due)) }
                        if let perMonth = progress.perMonth, let months = progress.monthsLeft {
                            LabeledContent("To make it", value: "\(SheetFormat.qar(perMonth)) a month · \(months) month\(months == 1 ? "" : "s")")
                        }
                        if let notes = goal.notes { Text(notes).font(.subheadline).foregroundStyle(.secondary) }
                    }
                    Section {
                        Button { putting = true } label: { Label("Put money towards it", systemImage: "plus.circle.fill") }
                        if !goal.done {
                            Button {
                                var g = goal; g.done = true; g.doneOn = SheetDate.today
                                store.saveSavingsGoal(g)
                            } label: { Label("Mark as reached", systemImage: "checkmark.seal") }
                        } else {
                            Button {
                                var g = goal; g.done = false; g.doneOn = nil
                                store.saveSavingsGoal(g)
                            } label: { Label("Not reached yet", systemImage: "arrow.uturn.backward") }
                        }
                    }
                    if !entries.isEmpty {
                        Section("Put aside for it") {
                            ForEach(entries) { e in
                                HStack {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(SheetDate.short(e.date)).font(.subheadline)
                                        Text([e.bucket, e.note].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(SheetFormat.qar(e.amount)).monospacedDigit()
                                    if e.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                                }
                                .swipeActions { Button("Delete", systemImage: "trash", role: .destructive) { store.deleteSetAside(id: e.id) } }
                            }
                        }
                    }
                    Section {
                        Button("Delete goal", role: .destructive) { confirmDelete = true }
                    } footer: { Text("Money you put aside stays in your savings — it just stops counting toward this goal.") }
                }
                .navigationTitle(goal.name)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Edit") { editing = true } }
                    ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                }
                .sheet(isPresented: $putting) { PutAsideSheet(store: store, bucket: .savings, goalId: goal.id) }
                .sheet(isPresented: $editing) { SavingsGoalEditor(store: store, goal: goal, isNew: false) }
                .popup(isPresented: $confirmDelete, .delete("Delete \(goal.name)?", message: "The money you put aside for it stays in your savings.",
                                                            confirmTitle: "Delete goal") { store.deleteSavingsGoal(id: goal.id); dismiss() })
            } else {
                ContentUnavailableView("Goal deleted", systemImage: "target")
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            }
        }
        .tint(SheetTheme.navy)
    }
}

private struct SavingsGoalEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var goal: SavingsGoal
    @State private var hasDue: Bool
    @State private var due: Date

    init(store: SheetStore, goal: SavingsGoal, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _goal = State(initialValue: goal)
        _hasDue = State(initialValue: goal.due != nil)
        _due = State(initialValue: SheetDate.date(goal.due) ?? Calendar.current.date(byAdding: .month, value: 6, to: .now) ?? .now)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("What for? e.g. Trip to Kerala, new phone", text: $goal.name).textInputAutocapitalization(.sentences)
                    HStack {
                        Text("Target")
                        Spacer()
                        TextField("0", value: $goal.target, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 120)
                        Text(Money.main).foregroundStyle(.secondary)
                    }
                    Toggle("By a date", isOn: $hasDue.animation())
                    if hasDue { DatePicker("Date", selection: $due, in: Date.now..., displayedComponents: .date) }
                    TextField("Notes (optional)", text: Binding(get: { goal.notes ?? "" }, set: { goal.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                } footer: {
                    if hasDue, goal.target > 0 {
                        let months = max(1, (Calendar.current.dateComponents([.month], from: .now, to: due).month ?? 0) + 1)
                        Text("About \(SheetFormat.qar(goal.target / Double(months))) \(Money.main) a month over \(months) month\(months == 1 ? "" : "s").")
                    }
                }
            }
            .navigationTitle(isNew ? "New goal" : "Edit goal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = goal
                        saved.name = goal.name.trimmingCharacters(in: .whitespaces)
                        saved.due = hasDue ? SheetDate.string(due) : nil
                        store.saveSavingsGoal(saved)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(goal.name.trimmingCharacters(in: .whitespaces).isEmpty || goal.target <= 0)
                }
            }
        }
        .tint(SheetTheme.navy)
    }
}
