//
//  LifeGoalsView.swift
//  MyPersonalTracker
//
//  Daily ▸ Goals: short-term goals (the next few months) and long-term goals
//  (a year or more), each with steps; short-term goals can build toward a
//  long-term one.
//

import SwiftUI

struct LifeGoalsContent: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var editing: LifeGoal?
    @State private var opened: String?
    @State private var showReached = false

    var body: some View {
        let data = snapshot.lifeGoalData
        let short = data.active(.short)
        let long = data.active(.long)
        let reached = data.goals.filter { $0.status == .reached }.sorted { ($0.doneOn ?? "") > ($1.doneOn ?? "") }

        GoalsHero(data: data, short: short.count, long: long.count) { editing = LifeGoal() }

        if data.goals.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Where do you want to be?").font(.headline)
                    Text("Long-term goals are the big picture — a role, a home, a fitness level, a year or more away. Short-term goals are the next few months, and the steps that get you there. Link a short-term goal to a long-term one and it moves the bigger goal along.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    // Side by side when both fit on one line, otherwise one under the other — never wrapped.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { startButtons(fill: true) }
                        VStack(spacing: 8) { startButtons(fill: true) }
                    }
                    .buttonStyle(.bordered).font(.subheadline)
                }
            }
        }

        if !short.isEmpty || !data.goals.isEmpty {
            HorizonHeader(horizon: .short) { editing = LifeGoal(horizon: .short) }
            if short.isEmpty {
                Text("Nothing for the next few months yet.").font(.subheadline).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            ForEach(short) { goal in
                Button { opened = goal.id } label: { GoalCard(goal: goal, data: data) }.buttonStyle(.plain)
            }
            HorizonHeader(horizon: .long) { editing = LifeGoal(horizon: .long) }
            if long.isEmpty {
                Text("Add a bigger goal to give the short ones a direction.").font(.subheadline).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            ForEach(long) { goal in
                Button { opened = goal.id } label: { GoalCard(goal: goal, data: data) }.buttonStyle(.plain)
            }
        }

        if !reached.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 8) {
                    Button { withAnimation(.snappy) { showReached.toggle() } } label: {
                        HStack {
                            Label("Reached · \(reached.count)", systemImage: "checkmark.seal.fill")
                                .font(.subheadline.weight(.semibold)).foregroundStyle(SheetTheme.good)
                            Spacer()
                            Image(systemName: showReached ? "chevron.up" : "chevron.down").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                        }
                    }
                    .buttonStyle(.plain)
                    if showReached {
                        ForEach(reached) { goal in
                            Button { opened = goal.id } label: {
                                HStack {
                                    Image(systemName: goal.area.symbol).foregroundStyle(.secondary).accessibilityLabel(goal.area.rawValue)
                                    Text(goal.title).foregroundStyle(Color(.label)).lineLimit(1)
                                    Spacer()
                                    if let d = goal.doneOn { Text(SheetDate.short(d)).font(.caption).foregroundStyle(.secondary) }
                                }
                                .font(.subheadline)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }

        Color.clear.frame(height: 0)
            .sheet(item: $editing) { goal in
                LifeGoalEditor(store: store, goal: goal, isNew: !data.goals.contains { $0.id == goal.id })
            }
            .sheet(item: Binding(get: { opened.map(GoalRef.init) }, set: { opened = $0?.id })) { ref in
                LifeGoalDetail(store: store, goalId: ref.id)
            }
    }
}

private struct GoalRef: Identifiable { let id: String }

extension LifeGoalsContent {
    @ViewBuilder
    fileprivate func startButtons(fill: Bool) -> some View {
        Button { editing = LifeGoal(horizon: .short) } label: {
            Label("Short-term goal", systemImage: "flag.fill").lineLimit(1).fixedSize().frame(maxWidth: fill ? .infinity : nil)
        }
        Button { editing = LifeGoal(horizon: .long) } label: {
            Label("Long-term goal", systemImage: "mountain.2.fill").lineLimit(1).fixedSize().frame(maxWidth: fill ? .infinity : nil)
        }
    }
}

private struct GoalsHero: View {
    let data: LifeGoalData
    let short: Int
    let long: Int
    let onAdd: () -> Void

    var body: some View {
        let active = data.active
        let average = active.isEmpty ? 0 : active.reduce(0) { $0 + data.progress(of: $1) } / Double(active.count)
        let thisMonth = data.reached().count
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Goals").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    Text("\(short) short term · \(long) long term")
                        .font(.title3.weight(.bold)).foregroundStyle(.white)
                    Text(thisMonth > 0 ? "\(thisMonth) reached this month 🎉" : "Keep the next step small.")
                        .font(.caption).foregroundStyle(.white.opacity(0.8))
                }
                Spacer()
                ZStack {
                    ScoreRing(fraction: average, lineWidth: 7, tint: Color(hex: 0x4ADE80))
                    VStack(spacing: 0) {
                        Text(SheetFormat.percent(average, 0)).font(.system(.subheadline, design: .rounded, weight: .bold))
                        Text("overall").font(.system(size: 9, weight: .semibold)).opacity(0.8)
                    }
                    .foregroundStyle(.white)
                }
                .frame(width: 62, height: 62)
            }
            Button(action: onAdd) {
                Label("New goal", systemImage: "plus").font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(.white.opacity(0.16), in: Capsule())
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .background(LinearGradient(colors: [Color(hex: 0x4C1D95), Color(hex: 0x6D28D9)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.top, 4)
    }
}

private struct HorizonHeader: View {
    let horizon: LifeGoal.Horizon
    let onAdd: () -> Void

    var body: some View {
        HStack(alignment: .bottom) {
            SectionTitle(title: horizon.rawValue, subtitle: horizon.hint)
            Button(action: onAdd) { Image(systemName: "plus.circle.fill").font(.title3) }
                .foregroundStyle(SheetTheme.navy)
                .accessibilityLabel("Add a \(horizon.rawValue.lowercased()) goal")
        }
    }
}

private struct GoalCard: View {
    let goal: LifeGoal
    let data: LifeGoalData

    var body: some View {
        let progress = data.progress(of: goal)
        let tint = goal.horizon == .short ? SheetTheme.indigo : SheetTheme.purple
        let kids = data.children(of: goal.id)
        let steps = data.steps(of: goal.id)
        SheetCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: goal.area.symbol).font(.title3).foregroundStyle(tint).frame(width: 34, height: 34)
                        .accessibilityLabel(goal.area.rawValue)
                        .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(goal.title).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label)).multilineTextAlignment(.leading)
                        let meta = [goal.area.rawValue, GoalTime.due(goal.due)].compactMap { $0 }.joined(separator: " · ")
                        Text(meta).font(.caption).foregroundStyle((GoalTime.due(goal.due) ?? "").contains("overdue") ? SheetTheme.bad : .secondary)
                    }
                    Spacer(minLength: 4)
                    Text(SheetFormat.percent(progress, 0)).font(.headline.monospacedDigit()).foregroundStyle(tint)
                    if goal.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                }
                ProgressView(value: progress).tint(tint)
                if let next = data.nextStep(of: goal) {
                    Label("Next: \(next.step)", systemImage: "arrow.right.circle").font(.caption).foregroundStyle(Color(.label)).lineLimit(1)
                } else if steps.isEmpty && kids.isEmpty {
                    Label("Add the first step", systemImage: "plus.circle").font(.caption).foregroundStyle(SheetTheme.navy)
                }
                HStack(spacing: 10) {
                    if !steps.isEmpty { Text("\(steps.filter(\.done).count)/\(steps.count) steps").font(.caption2.weight(.semibold)).foregroundStyle(.secondary) }
                    if !kids.isEmpty { Text("\(kids.count) short-term goal\(kids.count == 1 ? "" : "s")").font(.caption2.weight(.semibold)).foregroundStyle(.secondary) }
                    if let parent = data.goal(goal.parentId) {
                        Label(parent.title, systemImage: "mountain.2").font(.caption2.weight(.semibold)).foregroundStyle(SheetTheme.purple).lineLimit(1)
                    }
                    if data.isStale(goal) {
                        Label("No progress in 2 weeks", systemImage: "hourglass").font(.caption2.weight(.semibold)).foregroundStyle(SheetTheme.warn)
                    }
                }
            }
        }
    }
}

// MARK: - Detail

struct LifeGoalDetail: View {
    let store: SheetStore
    let goalId: String
    @Environment(\.dismiss) private var dismiss
    @State private var newStep = ""
    @State private var editing: LifeGoal?
    @State private var openedChild: String?
    @State private var confirmDelete = false
    @FocusState private var addingStep: Bool

    var body: some View {
        NavigationStack {
            if let data = store.snapshot?.lifeGoalData, let goal = data.goal(goalId) {
                let progress = data.progress(of: goal)
                let steps = data.steps(of: goal.id)
                let kids = data.children(of: goal.id)
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Label(goal.horizon.rawValue, systemImage: goal.horizon.symbol).font(.caption.weight(.semibold))
                                    .foregroundStyle(goal.horizon == .short ? SheetTheme.indigo : SheetTheme.purple)
                                Text("·").foregroundStyle(.tertiary)
                                Label(goal.area.rawValue, systemImage: goal.area.symbol).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                                if goal.status != .active { StatusPill(text: goal.status.rawValue, tint: goal.status == .reached ? SheetTheme.good : .secondary) }
                            }
                            Text(goal.title).font(.title3.weight(.bold))
                            if let due = GoalTime.due(goal.due) { Text(due).font(.subheadline).foregroundStyle(.secondary) }
                            HStack {
                                ProgressView(value: progress).tint(goal.horizon == .short ? SheetTheme.indigo : SheetTheme.purple)
                                Text(SheetFormat.percent(progress, 0)).font(.subheadline.weight(.bold).monospacedDigit())
                            }
                            if let why = goal.why {
                                Text("Why: \(why)").font(.subheadline).italic().foregroundStyle(.secondary)
                            }
                            if let parent = data.goal(goal.parentId) {
                                Label("Part of: \(parent.title)", systemImage: "mountain.2").font(.caption.weight(.semibold)).foregroundStyle(SheetTheme.purple)
                            }
                        }
                        .padding(.vertical, 4)
                    }

                    Section {
                        ForEach(steps) { step in
                            Button {
                                var s = step; s.done.toggle(); store.saveGoalStep(s)
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: step.done ? "checkmark.circle.fill" : "circle")
                                        .font(.title3).foregroundStyle(step.done ? SheetTheme.good : Color(.tertiaryLabel))
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(step.step).strikethrough(step.done).foregroundStyle(step.done ? .secondary : Color(.label))
                                        if step.done, let on = step.doneOn { Text("Done \(SheetDate.short(on))").font(.caption2).foregroundStyle(.secondary) }
                                    }
                                    Spacer()
                                    if step.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                                }
                            }
                            .swipeActions { Button("Delete", systemImage: "trash", role: .destructive) { store.deleteGoalStep(id: step.id) } }
                        }
                        HStack {
                            Image(systemName: "plus.circle.fill").foregroundStyle(SheetTheme.navy)
                            TextField("Add a step", text: $newStep)
                                .focused($addingStep)
                                .submitLabel(.done)
                                .onSubmit { addStep(goal, after: steps) }
                            if !newStep.trimmingCharacters(in: .whitespaces).isEmpty {
                                Button("Add") { addStep(goal, after: steps) }.fontWeight(.semibold)
                            }
                        }
                    } header: {
                        Text("Steps")
                    } footer: {
                        if steps.isEmpty && kids.isEmpty {
                            Text("Break it into small, concrete steps — progress moves as you tick them off.")
                        }
                    }

                    if goal.horizon == .long {
                        Section {
                            ForEach(kids) { kid in
                                Button { openedChild = kid.id } label: {
                                    HStack {
                                        Image(systemName: kid.area.symbol).foregroundStyle(.secondary).accessibilityLabel(kid.area.rawValue)
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(kid.title).foregroundStyle(Color(.label))
                                            if let due = GoalTime.due(kid.due) { Text(due).font(.caption).foregroundStyle(.secondary) }
                                        }
                                        Spacer()
                                        Text(SheetFormat.percent(data.progress(of: kid), 0)).font(.subheadline.weight(.semibold).monospacedDigit())
                                            .foregroundStyle(kid.status == .reached ? SheetTheme.good : SheetTheme.indigo)
                                    }
                                }
                            }
                            Button { editing = LifeGoal(horizon: .short, area: goal.area, parentId: goal.id) } label: {
                                Label("Add a short-term goal for this", systemImage: "flag.fill")
                            }
                        } header: {
                            Text("Short-term goals toward it")
                        } footer: {
                            Text("Each counts toward this goal's progress.")
                        }
                    }

                    if steps.isEmpty && kids.isEmpty && goal.status == .active {
                        Section {
                            HStack {
                                Text("Progress")
                                Slider(value: Binding(get: { goal.progress ?? 0 }, set: { v in
                                    var g = goal; g.progress = (v * 20).rounded() / 20; store.saveLifeGoal(g)
                                }), in: 0...1, step: 0.05)
                                Text(SheetFormat.percent(goal.progress ?? 0, 0)).monospacedDigit().frame(width: 44, alignment: .trailing)
                            }
                        } footer: {
                            Text("No steps yet, so set how far along you are by hand.")
                        }
                    }

                    if let notes = goal.notes { Section("Notes") { Text(notes) } }

                    Section {
                        if goal.status == .active {
                            Button {
                                var g = goal; g.status = .reached; g.doneOn = SheetDate.today; store.saveLifeGoal(g)
                            } label: { Label("Mark as reached", systemImage: "checkmark.seal.fill") }
                            .tint(SheetTheme.good)
                            Button {
                                var g = goal; g.status = .dropped; store.saveLifeGoal(g)
                            } label: { Label("Drop this goal", systemImage: "xmark.circle") }
                            .tint(.secondary)
                        } else {
                            Button {
                                var g = goal; g.status = .active; g.doneOn = nil; store.saveLifeGoal(g)
                            } label: { Label("Back to active", systemImage: "arrow.uturn.backward") }
                        }
                        Button("Delete goal", role: .destructive) { confirmDelete = true }
                    } footer: {
                        Text(goal.horizon == .long && !kids.isEmpty ? "Deleting it keeps its short-term goals." : "")
                    }
                }
                .navigationTitle(goal.horizon.rawValue)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Edit") { editing = goal } }
                    ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                }
                .sheet(item: $editing) { g in LifeGoalEditor(store: store, goal: g, isNew: !data.goals.contains { $0.id == g.id }) }
                .sheet(item: Binding(get: { openedChild.map(GoalRef.init) }, set: { openedChild = $0?.id })) { ref in
                    LifeGoalDetail(store: store, goalId: ref.id)
                }
                .popup(isPresented: $confirmDelete, .delete("Delete \(goal.title)?", message: goal.horizon == .long ? "Its steps are deleted too. Short-term goals under it stay." : "Its steps are deleted too.",
                                                            confirmTitle: "Delete goal") { store.deleteLifeGoal(id: goal.id); dismiss() })
            } else {
                ContentUnavailableView("Goal deleted", systemImage: "flag")
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func addStep(_ goal: LifeGoal, after steps: [GoalStep]) {
        let text = newStep.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        store.saveGoalStep(GoalStep(goalId: goal.id, step: text, order: Double((steps.compactMap(\.order).max() ?? Double(steps.count)) + 1)))
        newStep = ""
        addingStep = true
    }
}

// MARK: - Editor

struct LifeGoalEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var goal: LifeGoal
    @State private var hasDue: Bool
    @State private var due: Date
    @State private var firstSteps = ""

    init(store: SheetStore, goal: LifeGoal, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _goal = State(initialValue: goal)
        _hasDue = State(initialValue: goal.due != nil || isNew)
        let months = goal.horizon == .short ? 3 : 12
        _due = State(initialValue: SheetDate.date(goal.due) ?? Calendar.current.date(byAdding: .month, value: months, to: .now) ?? .now)
    }

    var body: some View {
        let longGoals = (store.snapshot?.lifeGoalData.active(.long) ?? []).filter { $0.id != goal.id }
        NavigationStack {
            Form {
                Section {
                    TextField(goal.horizon == .short ? "e.g. Run 5 km without stopping" : "e.g. Become a senior iOS developer", text: $goal.title, axis: .vertical)
                        .textInputAutocapitalization(.sentences)
                    Picker("Horizon", selection: $goal.horizon.animation()) {
                        ForEach(LifeGoal.Horizon.allCases, id: \.self) { Text($0.rawValue) }
                    }
                    .pickerStyle(.segmented)
                    Picker("Area", selection: $goal.area) {
                        ForEach(GoalArea.allCases) { Label($0.rawValue, systemImage: $0.symbol).tag($0) }
                    }
                } footer: {
                    Text("\(goal.horizon.rawValue): \(goal.horizon.hint.lowercased()).")
                }
                Section {
                    Toggle("By a date", isOn: $hasDue.animation())
                    if hasDue {
                        DatePicker("Date", selection: $due, displayedComponents: .date)
                    }
                } footer: {
                    if hasDue, GoalTime.suggestedHorizon(for: due) != goal.horizon {
                        Button("That's \(GoalTime.suggestedHorizon(for: due) == .short ? "only a few months" : "a long way") off — make it \(GoalTime.suggestedHorizon(for: due).rawValue.lowercased())?") {
                            withAnimation { goal.horizon = GoalTime.suggestedHorizon(for: due) }
                        }
                        .font(.footnote)
                    }
                }
                if goal.horizon == .short, !longGoals.isEmpty {
                    Section {
                        Picker("Builds toward", selection: $goal.parentId) {
                            Text("Nothing in particular").tag(String?.none)
                            ForEach(longGoals) { Label($0.title, systemImage: $0.area.symbol).tag(Optional($0.id)) }
                        }
                    } footer: {
                        Text("Progress here moves that long-term goal along too.")
                    }
                }
                Section {
                    TextField("Why does it matter to you?", text: Binding(get: { goal.why ?? "" }, set: { goal.why = $0.isEmpty ? nil : $0 }), axis: .vertical)
                } footer: {
                    Text("Shown on the goal, for the days it's hard.")
                }
                if isNew {
                    Section {
                        TextField("One step per line", text: $firstSteps, axis: .vertical).lineLimit(3...8)
                    } header: { Text("First steps (optional)") }
                }
                Section {
                    TextField("Notes (optional)", text: Binding(get: { goal.notes ?? "" }, set: { goal.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
            }
            .navigationTitle(isNew ? "New goal" : "Edit goal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .fontWeight(.semibold)
                        .disabled(goal.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func save() {
        var saved = goal
        saved.title = goal.title.trimmingCharacters(in: .whitespacesAndNewlines)
        saved.due = hasDue ? SheetDate.string(due) : nil
        if saved.horizon == .long { saved.parentId = nil }
        store.saveLifeGoal(saved)
        let lines = firstSteps.split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: CharacterSet.whitespaces.union(CharacterSet(charactersIn: "-•*·"))) }
            .filter { !$0.isEmpty }
        for (i, line) in lines.enumerated() { store.saveGoalStep(GoalStep(goalId: saved.id, step: line, order: Double(i + 1))) }
        dismiss()
    }
}

// MARK: - Dashboard

/// The short-term goals closest to their date, on the Dashboard.
struct GoalsFocusCard: View {
    let snapshot: SheetSnapshot
    let onOpen: () -> Void

    var body: some View {
        let data = snapshot.lifeGoalData
        let focus = Array(data.active(.short).prefix(3))
        if !focus.isEmpty {
            Button(action: onOpen) {
                SheetCard {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Label("Goals in focus", systemImage: "flag.fill").font(.headline).foregroundStyle(Color(.label))
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                        }
                        ForEach(focus) { goal in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Label(goal.title, systemImage: goal.area.symbol).font(.subheadline.weight(.medium)).foregroundStyle(Color(.label)).lineLimit(1)
                                    Spacer()
                                    Text(SheetFormat.percent(data.progress(of: goal), 0)).font(.caption.weight(.bold).monospacedDigit()).foregroundStyle(SheetTheme.indigo)
                                }
                                ProgressView(value: data.progress(of: goal)).tint(SheetTheme.indigo)
                                if let next = data.nextStep(of: goal) {
                                    Text("Next: \(next.step)").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }
                            }
                        }
                    }
                }
            }
            .buttonStyle(.plain)
        }
    }
}
