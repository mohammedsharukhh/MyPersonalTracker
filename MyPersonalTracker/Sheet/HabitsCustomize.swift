//
//  HabitsCustomize.swift
//  MyPersonalTracker
//
//  Your habits (29_Habits.gs): the five habit slots of the Daily Tracker, each
//  with your own name, icon and on/off, and the trackers (water, sleep, steps,
//  weight, screen time) you can turn off. The Daily Score counts only what's on.
//

import SwiftUI

/// Icons a habit can have — the same keys on Android.
enum HabitIconCatalog {
    static let all: [(key: String, symbol: String)] = [
        ("office", "building.2"), ("breakfast", "sunrise"), ("lunch", "fork.knife"), ("protein", "bolt.heart"),
        ("workout", "figure.run"), ("walk", "figure.walk"), ("bike", "bicycle"), ("swim", "figure.pool.swim"),
        ("water", "drop.fill"), ("sleep", "bed.double.fill"), ("meditate", "figure.mind.and.body"), ("pray", "hands.sparkles.fill"),
        ("book", "book.fill"), ("study", "graduationcap.fill"), ("journal", "square.and.pencil"), ("language", "character.bubble"),
        ("code", "chevron.left.forwardslash.chevron.right"), ("music", "music.note"), ("vitamins", "pills.fill"), ("fruit", "carrot.fill"),
        ("no-phone", "iphone.slash"), ("no-sugar", "nosign"), ("smoke-free", "lungs.fill"), ("clean", "sparkles"),
        ("family", "figure.2.and.child.holdinghands"), ("money", "banknote.fill"), ("sun", "sun.max.fill"), ("heart", "heart.fill"),
        ("star", "star.fill"), ("checklist", "checklist")
    ]

    static func symbol(_ key: String?) -> String? { all.first { $0.key == key }?.symbol }
}

extension Habit {
    /// 1…5: the Daily Tracker column it lives in.
    var slot: Int { Habit.allCases.firstIndex(of: self)! + 1 }

    var defaultName: String {
        switch self {
        case .office: "In Office by 9:00 AM"
        case .breakfast: "Breakfast Done"
        case .lunch: "Lunch Taken"
        case .protein: "Protein in Lunch"
        case .workout: "Workout Done"
        }
    }

    var defaultIcon: String { ["office", "breakfast", "lunch", "protein", "workout"][slot - 1] }

    func name(_ s: SheetSettings) -> String {
        let n = [s.habit1Name, s.habit2Name, s.habit3Name, s.habit4Name, s.habit5Name][slot - 1]
        return (n?.isEmpty == false ? n : nil) ?? defaultName
    }

    func isOn(_ s: SheetSettings) -> Bool { [s.habit1On, s.habit2On, s.habit3On, s.habit4On, s.habit5On][slot - 1] != "N" }

    func iconKey(_ s: SheetSettings) -> String { [s.habit1Icon, s.habit2Icon, s.habit3Icon, s.habit4Icon, s.habit5Icon][slot - 1] ?? defaultIcon }

    func symbol(_ s: SheetSettings) -> String { HabitIconCatalog.symbol(iconKey(s)) ?? symbol }

    /// The habits that are on, in column order.
    static func active(_ s: SheetSettings) -> [Habit] { allCases.filter { $0.isOn(s) } }

    /// The 5th habit can be ticked by the phone (Apple Health) only while it's a workout habit and on.
    static func healthFillsWorkout(_ s: SheetSettings) -> Bool {
        Habit.workout.isOn(s) && Habit.workout.name(s).range(of: "workout|exercise|gym|run|walk", options: [.regularExpression, .caseInsensitive]) != nil
    }
}

enum Tracker: String, CaseIterable, Identifiable {
    case water, sleep, steps, weight, screen
    var id: Self { self }
    var title: String { ["Water", "Sleep", "Steps", "Weight", "Screen time"][Tracker.allCases.firstIndex(of: self)!] }
    var symbol: String { ["drop.fill", "bed.double.fill", "figure.walk", "scalemass.fill", "iphone"][Tracker.allCases.firstIndex(of: self)!] }
    var key: String { ["WATER_ON", "SLEEP_ON", "STEPS_ON", "WEIGHT_ON", "SCREEN_ON"][Tracker.allCases.firstIndex(of: self)!] }
    /// Counts toward the Daily Score.
    var scored: Bool { self == .water || self == .sleep }

    func isOn(_ s: SheetSettings) -> Bool { [s.waterOn, s.sleepOn, s.stepsOn, s.weightOn, s.screenOn][Tracker.allCases.firstIndex(of: self)!] != "N" }
}

extension SheetSettings {
    /// What the Daily Score is out of, worked out the way the sheet does.
    var scoreDenominator: Double {
        if let scoreOutOf, scoreOutOf > 0 { return scoreOutOf }
        return Double(Habit.active(self).count + (Tracker.water.isOn(self) ? 1 : 0) + (Tracker.sleep.isOn(self) ? 1 : 0))
    }
}

// MARK: - Editor

/// Settings ▸ Your habits, and "Customize" on the day log.
struct HabitsEditor: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss

    private struct Row: Identifiable {
        let habit: Habit
        var name: String
        var on: Bool
        var icon: String
        var id: Habit { habit }
    }
    @State private var rows: [Row] = []
    @State private var trackers: [Tracker: Bool] = [:]
    @State private var picking: Habit?
    @State private var editing: InputEditTarget?

    private var mine: [MyInput] { (store.snapshot?.inputData.inputs ?? []).sorted { ($0.section == .habit ? 0 : 1, $0.order ?? 0) < ($1.section == .habit ? 0 : 1, $1.order ?? 0) } }
    private var mineHabitsOn: Int { store.snapshot?.inputData.habitsOn ?? 0 }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach($rows) { $row in
                        HStack(spacing: 12) {
                            Button { picking = row.habit } label: {
                                Image(systemName: HabitIconCatalog.symbol(row.icon) ?? "circle")
                                    .font(.system(size: 17, weight: .semibold))
                                    .foregroundStyle(row.on ? SheetTheme.navy : .secondary)
                                    .frame(width: 40, height: 40)
                                    .background((row.on ? SheetTheme.navy : Color.secondary).opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Icon for \(row.name)")
                            TextField(row.habit.defaultName, text: $row.name)
                                .textInputAutocapitalization(.sentences)
                                .foregroundStyle(row.on ? Color(.label) : .secondary)
                                .padding(.horizontal, 10).padding(.vertical, 7)
                                .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            Toggle("", isOn: $row.on).labelsHidden()
                        }
                    }
                } header: { Text("Your habits") } footer: {
                    Text("Name them what you actually do — “Read 20 pages”, “No sugar”, “Pray Fajr”. Tap an icon to change it. A habit that's off is hidden and doesn't count toward your score; what you logged before stays.")
                }
                Section {
                    ForEach(mine) { input in
                        HStack(spacing: 12) {
                            Button { editing = .mine(input) } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: input.symbol)
                                        .font(.system(size: 17, weight: .semibold))
                                        .foregroundStyle(input.on ? SheetTheme.navy : .secondary)
                                        .frame(width: 40, height: 40)
                                        .background((input.on ? SheetTheme.navy : Color.secondary).opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(input.name).foregroundStyle(input.on ? Color(.label) : .secondary)
                                        Text(input.section == .habit ? "Habit" : ["Health", input.unit, input.target.map { "target \(SheetFormat.number($0, digits: $0.rounded() == $0 ? 0 : 1))" }]
                                            .compactMap { $0?.isEmpty == false ? $0 : nil }.joined(separator: " · "))
                                            .font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer(minLength: 4)
                                    Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            Toggle("", isOn: Binding(get: { input.on }, set: { var x = input; x.on = $0; store.saveInput(x) })).labelsHidden()
                        }
                    }
                    Button { editing = .mine(.new(.habit, order: mine.count + 1)) } label: { Label("Add a habit", systemImage: "plus.circle.fill") }
                    Button { editing = .mine(.new(.health, order: mine.count + 1)) } label: { Label("Add to Health", systemImage: "plus.circle.fill") }
                } header: { Text("Added by you") } footer: {
                    Text("Anything you want to track — “Read 20 pages”, “Pages read”, “Cups of tea”. Your habits count toward the score while they're on; health numbers are tracked, not scored. Changes here save straight away.")
                }
                Section {
                    ForEach(Tracker.allCases) { t in
                        Toggle(isOn: Binding(get: { trackers[t] ?? true }, set: { trackers[t] = $0 })) {
                            Label(t.title, systemImage: t.symbol)
                        }
                    }
                } header: { Text("Track") } footer: {
                    Text("Water and sleep count toward the score when they're on. Steps, weight and screen time are tracked but not scored.")
                }
                Section {
                    let outOf = rows.filter(\.on).count + ((trackers[.water] ?? true) ? 1 : 0) + ((trackers[.sleep] ?? true) ? 1 : 0) + mineHabitsOn
                    LabeledContent("Daily Score is out of", value: "\(outOf)")
                } footer: {
                    if Habit.healthFillsWorkout(previewSettings) {
                        Text("“\(rows.last?.name ?? "")” can be ticked by Apple Health when you work out for 20 minutes or more.")
                    }
                }
            }
            .navigationTitle("Customize")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        store.saveHabits(rows.map { (name: $0.name.trimmingCharacters(in: .whitespaces), on: $0.on, icon: $0.icon) },
                                         trackers: Dictionary(uniqueKeysWithValues: Tracker.allCases.map { ($0, trackers[$0] ?? true) }))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(rows.filter(\.on).isEmpty && !(trackers[.water] ?? true) && !(trackers[.sleep] ?? true) && mineHabitsOn == 0)
                }
            }
            .sheet(item: $picking) { habit in
                IconPicker(selected: rows.first { $0.habit == habit }?.icon ?? habit.defaultIcon) { key in
                    if let i = rows.firstIndex(where: { $0.habit == habit }) { rows[i].icon = key }
                }
            }
            .sheet(item: $editing) { target in InputEditor(store: store, target: target) }
            .onAppear(perform: load)
        }
        .tint(SheetTheme.navy)
    }

    /// The settings as they'd be saved (for the footer).
    private var previewSettings: SheetSettings {
        var s = store.snapshot?.settings ?? (try! JSONDecoder().decode(SheetSettings.self, from: Data("{}".utf8)))
        s.habit5Name = rows.last?.name; s.habit5On = (rows.last?.on ?? true) ? "Y" : "N"
        return s
    }

    private func load() {
        guard rows.isEmpty else { return }
        let s = store.snapshot?.settings
        rows = Habit.allCases.map { h in
            Row(habit: h, name: s.map { h.name($0) } ?? h.defaultName, on: s.map { h.isOn($0) } ?? true, icon: s.map { h.iconKey($0) } ?? h.defaultIcon)
        }
        for t in Tracker.allCases { trackers[t] = s.map { t.isOn($0) } ?? true }
    }
}

struct IconPicker: View {
    let selected: String
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 5), spacing: 12) {
                    ForEach(HabitIconCatalog.all, id: \.key) { icon in
                        Button { onPick(icon.key); dismiss() } label: {
                            Image(systemName: icon.symbol)
                                .font(.system(size: 20, weight: .semibold))
                                .frame(maxWidth: .infinity).frame(height: 56)
                                .foregroundStyle(icon.key == selected ? .white : SheetTheme.navy)
                                .background(icon.key == selected ? SheetTheme.navy : SheetTheme.navy.opacity(0.1),
                                            in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(icon.key)
                    }
                }
                .padding(16)
            }
            .navigationTitle("Icon")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
