//
//  MyInputs.swift
//  MyPersonalTracker
//
//  Habits and health inputs you add yourself (30_MyInputs.gs): a habit is done /
//  missed like the five and counts toward the Daily Score while it's on; a health
//  input is a number with a unit and an optional daily target ("Pages · 20").
//  Added with + on the day log; tap a name there to rename it.
//

import SwiftUI

struct MyInput: Codable, Identifiable, Hashable {
    enum Section: String, Codable { case habit = "Habit", health = "Health" }

    var id: String
    var section: Section
    var name: String
    var icon: String?
    var unit: String?
    var target: Double?
    var step: Double?
    var on: Bool
    var order: Double?

    var symbol: String { HabitIconCatalog.symbol(icon) ?? (section == .habit ? "checklist" : "star.fill") }
    var stepSize: Double { (step ?? 0) > 0 ? step! : 1 }

    static func new(_ section: Section, order: Int) -> MyInput {
        MyInput(id: "in-" + UUID().uuidString.prefix(8).lowercased(), section: section, name: "",
                icon: section == .habit ? "checklist" : "star", unit: section == .health ? "" : nil,
                target: nil, step: section == .health ? 1 : nil, on: true, order: Double(order))
    }
}

struct MyInputLog: Codable, Hashable {
    var date: String
    var id: String
    var value: String
}

struct MyInputData: Codable {
    var inputs: [MyInput] = []
    var log: [MyInputLog] = []

    /// In the order they were added.
    func list(_ section: MyInput.Section, onlyOn: Bool = true) -> [MyInput] {
        inputs.filter { $0.section == section && (!onlyOn || $0.on) }.sorted { ($0.order ?? 0) < ($1.order ?? 0) }
    }

    func values(on date: String) -> [String: String] {
        Dictionary(log.filter { $0.date == date }.map { ($0.id, $0.value) }, uniquingKeysWith: { _, b in b })
    }

    /// Your habits that are on and done that day.
    func habitsMet(on date: String) -> Int {
        let done = Set(log.filter { $0.date == date && $0.value == "Y" }.map(\.id))
        return list(.habit).filter { done.contains($0.id) }.count
    }

    var habitsOn: Int { list(.habit).count }
}

extension SheetSnapshot {
    var inputData: MyInputData { myInputs ?? MyInputData() }
}

extension SheetSettings {
    /// "Daily Score out of" as the sheet works it out: the five that are on, water and sleep if on, and your habits that are on.
    func scoreOutOf(with inputs: MyInputData?) -> Double {
        Double(Habit.active(self).count + (Tracker.water.isOn(self) ? 1 : 0) + (Tracker.sleep.isOn(self) ? 1 : 0) + (inputs?.habitsOn ?? 0))
    }
}

// MARK: - Shown on Daily

/// One habit on Daily — one of the five or one of yours.
struct HabitDot: Identifiable {
    let id: String
    let symbol: String
    let name: String
    let answer: String?

    /// The five that are on, then yours.
    static func all(_ day: SheetDay, _ settings: SheetSettings, _ inputs: MyInputData?) -> [HabitDot] {
        let values = inputs?.values(on: day.date) ?? [:]
        return Habit.active(settings).map { HabitDot(id: $0.rawValue, symbol: $0.symbol(settings), name: $0.name(settings), answer: day.answer($0)) }
            + (inputs?.list(.habit) ?? []).map { HabitDot(id: $0.id, symbol: $0.symbol, name: $0.name, answer: values[$0.id]) }
    }
}

/// Filled green for Y, red for N, outlined grey when blank.
struct AnswerDot: View {
    let symbol: String
    let name: String
    let answer: String?
    let onDark: Bool
    var size: CGFloat = 28

    var body: some View {
        let fill: Color = answer == "Y" ? SheetTheme.good : (answer == "N" ? SheetTheme.bad : .clear)
        let stroke: Color = onDark ? .white.opacity(0.35) : Color(.tertiaryLabel)
        Image(systemName: answer == "N" ? "xmark" : symbol)
            .font(.system(size: size * 0.42, weight: .bold))
            .foregroundStyle(answer == nil ? (onDark ? .white.opacity(0.6) : .secondary) : .white)
            .frame(width: size, height: size)
            .background(Circle().fill(fill))
            .overlay(Circle().stroke(answer == nil ? stroke : .clear, lineWidth: 1.5))
            .accessibilityLabel("\(name): \(answer == "Y" ? "done" : answer == "N" ? "missed" : "not logged")")
    }
}

// MARK: - Add / edit one

/// What the editor is editing: one of the five, one of yours, or a new one.
enum InputEditTarget: Identifiable {
    case builtIn(Habit)
    case mine(MyInput)

    var id: String {
        switch self {
        case .builtIn(let habit): "builtin-" + habit.rawValue
        case .mine(let input): input.id
        }
    }
}

/// Rename a habit or input, change its icon, turn it off; for one of yours, its unit and target, or delete it.
struct InputEditor: View {
    let store: SheetStore
    let target: InputEditTarget
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var icon = "checklist"
    @State private var on = true
    @State private var unit = ""
    @State private var goal = ""
    @State private var step = "1"
    @State private var picking = false
    @State private var confirmDelete = false
    @State private var loaded = false
    @FocusState private var nameFocused: Bool

    private var input: MyInput? { if case .mine(let input) = target { input } else { nil } }
    private var isNew: Bool { input.map { mine in !store.inputExists(mine.id) } ?? false }
    private var isHealth: Bool { input?.section == .health }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        Button { picking = true } label: {
                            Image(systemName: HabitIconCatalog.symbol(icon) ?? "circle")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 48, height: 48)
                                .background(SheetTheme.navy, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .overlay(alignment: .bottomTrailing) {
                                    Image(systemName: "pencil.circle.fill").font(.system(size: 16)).foregroundStyle(.white, SheetTheme.good)
                                        .offset(x: 5, y: 5)
                                }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Change icon")
                        TextField(isHealth ? "e.g. Pages read" : "e.g. Read 20 pages", text: $name)
                            .font(.headline)
                            .textInputAutocapitalization(.sentences)
                            .focused($nameFocused)
                    }
                    .padding(.vertical, 4)
                } footer: { Text("Tap the icon to change it.") }

                if isHealth {
                    Section {
                        LabeledContent("Unit") {
                            TextField("pages, min, cups…", text: $unit).multilineTextAlignment(.trailing)
                        }
                        LabeledContent("Daily target") {
                            TextField("Optional", text: $goal).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                        }
                        LabeledContent("Each + adds") {
                            TextField("1", text: $step).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                        }
                    } footer: { Text("Tracked every day; not part of the Daily Score.") }
                }

                Section {
                    Toggle("Show on Daily", isOn: $on)
                } footer: {
                    Text(on ? (isHealth ? "Turn off to hide it. What you logged stays." : "Counts toward your Daily Score while it's on. Turn off to hide it; what you logged stays.")
                             : "Hidden and not counted. Turn on again any time.")
                }

                if let input, !isNew {
                    Section {
                        Button("Delete \(input.name)", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isNew ? "Add" : "Save") { save(); dismiss() }
                        .fontWeight(.semibold)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .sheet(isPresented: $picking) { IconPicker(selected: icon) { icon = $0 } }
            .popup(isPresented: $confirmDelete, .delete("Delete \(input?.name ?? "")?", message: "Everything logged for it is deleted too. To keep it, turn it off instead.") {
                if let input { store.deleteInput(id: input.id) }
                dismiss()
            })
            .onAppear(perform: load)
        }
        .tint(SheetTheme.navy)
        .presentationDetents(isHealth ? [.large] : [.medium, .large])
    }

    private var title: String {
        if isNew { return isHealth ? "Add to Health" : "Add a habit" }
        return isHealth ? "Edit input" : "Edit habit"
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        switch target {
        case .builtIn(let habit):
            let s = store.snapshot?.settings
            name = s.map { habit.name($0) } ?? habit.defaultName
            icon = s.map { habit.iconKey($0) } ?? habit.defaultIcon
            on = s.map { habit.isOn($0) } ?? true
        case .mine(let input):
            name = input.name
            icon = input.icon ?? (input.section == .habit ? "checklist" : "star")
            on = input.on
            unit = input.unit ?? ""
            goal = input.target.map { SheetFormat.number($0, digits: $0.rounded() == $0 ? 0 : 1) } ?? ""
            step = SheetFormat.number(input.stepSize, digits: input.stepSize.rounded() == input.stepSize ? 0 : 1)
            if isNew { nameFocused = true }
        }
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        switch target {
        case .builtIn(let habit):
            store.saveHabit(habit, name: trimmed, on: on, icon: icon)
        case .mine(var input):
            input.name = trimmed
            input.icon = icon
            input.on = on
            if input.section == .health {
                input.unit = unit.trimmingCharacters(in: .whitespaces)
                input.target = Double(goal.replacingOccurrences(of: ",", with: "."))
                input.step = Double(step.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil } ?? 1
            }
            store.saveInput(input)
        }
    }
}
