//
//  BodyTargets.swift
//  MyPersonalTracker
//
//  Calorie and protein targets worked out from your weight — the same
//  Mifflin–St Jeor formula the sheet uses (19_Body.gs), so the preview here
//  matches what the sheet will show. With "Follow my weight" on, the sheet
//  keeps the targets in step with the newest weight you log.
//

import SwiftUI

enum BodyCalc {
    enum Sex: String, CaseIterable, Identifiable { case male = "Male", female = "Female"; var id: String { rawValue } }

    enum Activity: String, CaseIterable, Identifiable {
        case sedentary = "Sedentary", light = "Light", moderate = "Moderate", active = "Active", veryActive = "Very active"
        var id: String { rawValue }
        var factor: Double {
            switch self {
            case .sedentary: 1.2
            case .light: 1.375
            case .moderate: 1.55
            case .active: 1.725
            case .veryActive: 1.9
            }
        }
        var explanation: String {
            switch self {
            case .sedentary: "Desk job, little or no exercise"
            case .light: "Exercise 1–3 days a week"
            case .moderate: "Exercise 3–5 days a week"
            case .active: "Hard exercise 6–7 days a week"
            case .veryActive: "Physical job or training twice a day"
            }
        }
    }

    enum Goal: String, CaseIterable, Identifiable {
        case lose = "Lose fat", loseSlowly = "Lose slowly", maintain = "Maintain", gain = "Build muscle"
        var id: String { rawValue }
        var adjustment: Double {
            switch self {
            case .lose: -500
            case .loseSlowly: -250
            case .maintain: 0
            case .gain: 300
            }
        }
        var explanation: String {
            switch self {
            case .lose: "About 0.5 kg a week (−500 kcal a day)"
            case .loseSlowly: "About 0.25 kg a week (−250 kcal a day)"
            case .maintain: "Stay where you are"
            case .gain: "Lean gain (+300 kcal a day)"
            }
        }
        /** A sensible protein default for the goal (g per kg). */
        var proteinPerKg: Double {
            switch self {
            case .lose, .loseSlowly: 2.0
            case .maintain: 1.6
            case .gain: 1.8
            }
        }
    }

    struct Result: Equatable {
        let bmr: Double
        let maintenance: Double
        let calories: Double
        let protein: Double
        let fat: Double
        let carbs: Double
    }

    /// Mifflin–St Jeor, then the goal, protein by weight, fat 25% of calories, carbs the rest.
    static func targets(weight: Double, height: Double, age: Int, sex: Sex, activity: Activity, goal: Goal, proteinPerKg: Double) -> Result {
        let bmr = (10 * weight + 6.25 * height - 5 * Double(age) + (sex == .female ? -161 : 5)).rounded()
        let maintenance = (bmr * activity.factor).rounded()
        let calories = max(sex == .female ? 1200 : 1500, ((maintenance + goal.adjustment) / 10).rounded() * 10)
        let protein = (weight * proteinPerKg).rounded()
        let fat = (calories * 0.25 / 9).rounded()
        let carbs = max(0, ((calories - 4 * protein - 9 * fat) / 4).rounded())
        return Result(bmr: bmr, maintenance: maintenance, calories: calories, protein: protein, fat: fat, carbs: carbs)
    }
}

extension SheetSnapshot {
    /// Newest weight logged on the Daily Tracker, else the one on the Body block.
    var latestWeight: (kg: Double, date: String?)? {
        if let day = days.filter({ $0.weight != nil }).max(by: { $0.date < $1.date }), let kg = day.weight { return (kg, day.date) }
        return settings.bodyWeight.map { ($0, nil) }
    }
}

/// Your details in, suggested daily targets out — and saved to the sheet.
struct BodyTargetsView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var sex = BodyCalc.Sex.male
    @State private var age: Int?
    @State private var height: Double?
    @State private var weight: Double?
    @State private var activity = BodyCalc.Activity.light
    @State private var goal = BodyCalc.Goal.maintain
    @State private var proteinPerKg = 1.6
    @State private var follow = true
    @State private var saving = false
    @State private var error: String?

    private var result: BodyCalc.Result? {
        guard let age, age > 0, let height, height > 50, let weight, weight > 20 else { return nil }
        return BodyCalc.targets(weight: weight, height: height, age: age, sex: sex, activity: activity, goal: goal, proteinPerKg: proteinPerKg)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Sex", selection: $sex) { ForEach(BodyCalc.Sex.allCases) { Text($0.rawValue).tag($0) } }
                        .pickerStyle(.segmented)
                    field("Age", value: $age.double, unit: "years")
                    field("Height", value: $height, unit: "cm")
                    field("Weight", value: $weight, unit: "kg")
                } header: { Text("About you") } footer: {
                    if let latest = store.snapshot?.latestWeight, let date = latest.date {
                        Text("Weight from your Daily Tracker on \(SheetDate.short(date)). Log your weight there and the targets follow it.")
                    }
                }

                Section("How active are you?") {
                    ForEach(BodyCalc.Activity.allCases) { level in
                        choice(level.rawValue, level.explanation, selected: activity == level) { activity = level }
                    }
                }

                Section("Your goal") {
                    ForEach(BodyCalc.Goal.allCases) { g in
                        choice(g.rawValue, g.explanation, selected: goal == g) {
                            goal = g
                            proteinPerKg = g.proteinPerKg
                        }
                    }
                }

                Section {
                    Stepper(value: $proteinPerKg, in: 0.8...3.0, step: 0.1) {
                        HStack {
                            Text("Protein")
                            Spacer()
                            Text("\(proteinPerKg.formatted(.number.precision(.fractionLength(1)))) g per kg").monospacedDigit().foregroundStyle(.secondary)
                        }
                    }
                } footer: {
                    Text("1.6 g/kg suits most people; 1.8–2.2 when losing fat or lifting, to keep muscle.")
                }

                if let r = result {
                    Section {
                        VStack(alignment: .leading, spacing: 14) {
                            HStack(alignment: .firstTextBaseline) {
                                Text(SheetFormat.number(r.calories, digits: 0)).font(.system(size: 40, weight: .bold, design: .rounded))
                                Text("kcal a day").foregroundStyle(.secondary)
                            }
                            HStack(spacing: 10) {
                                macro("Protein", r.protein, SheetTheme.good)
                                macro("Carbs", r.carbs, SheetTheme.sky)
                                macro("Fat", r.fat, SheetTheme.purple)
                            }
                            VStack(alignment: .leading, spacing: 4) {
                                KeyValueRow(key: "Burned at rest (BMR)", value: "\(SheetFormat.number(r.bmr, digits: 0)) kcal")
                                KeyValueRow(key: "Maintenance (× \(activity.factor.formatted()))", value: "\(SheetFormat.number(r.maintenance, digits: 0)) kcal")
                                KeyValueRow(key: goal.rawValue, value: goal.adjustment == 0 ? "±0" : "\(goal.adjustment > 0 ? "+" : "")\(Int(goal.adjustment)) kcal")
                            }
                        }
                        .padding(.vertical, 6)
                        Toggle("Follow my weight", isOn: $follow)
                    } header: {
                        Text("Your daily targets")
                    } footer: {
                        Text(follow ? "When you log a new weight, the sheet recalculates these targets. Typing a target by hand in Settings stops that."
                                    : "Saved once as fixed numbers. Come back here to recalculate.")
                    }
                } else {
                    Section { Text("Enter your age, height and weight to see your targets.").foregroundStyle(.secondary) }
                }

                if let error { Text(error).font(.caption).foregroundStyle(SheetTheme.bad) }
            }
            .navigationTitle("Targets from my weight")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear(perform: load)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button { save() } label: { if saving { ProgressView() } else { Text("Use these").fontWeight(.semibold) } }
                        .disabled(result == nil || saving)
                }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func load() {
        guard let s = store.snapshot else { return }
        let set = s.settings
        sex = set.sex.flatMap(BodyCalc.Sex.init(rawValue:)) ?? .male
        age = set.birthYear.map { Calendar.current.component(.year, from: .now) - Int($0) }
        height = set.heightCm
        weight = s.latestWeight?.kg
        activity = set.activity.flatMap(BodyCalc.Activity.init(rawValue:)) ?? .light
        goal = set.bodyGoal.flatMap(BodyCalc.Goal.init(rawValue:)) ?? .maintain
        proteinPerKg = set.proteinPerKg ?? goal.proteinPerKg
        follow = set.followWeight ?? true
    }

    private func save() {
        guard let age, let height, let weight else { return }
        saving = true
        error = nil
        Task {
            let ok = await store.saveBody([
                "sex": sex.rawValue, "birthYear": Calendar.current.component(.year, from: .now) - age, "height": height,
                "weight": weight, "activity": activity.rawValue, "goal": goal.rawValue,
                "proteinPerKg": (proteinPerKg * 10).rounded() / 10, "follow": follow
            ])
            saving = false
            if ok { dismiss() } else { error = store.errorMessage ?? "Couldn't save." }
        }
    }

    private func field(_ title: String, value: Binding<Double?>, unit: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            TextField("0", value: value, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
            Text(unit).foregroundStyle(.secondary).frame(width: 44, alignment: .leading)
        }
    }

    private func choice(_ title: String, _ detail: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                    Text(detail).font(.caption).foregroundStyle(Color(.secondaryLabel))
                }
                Spacer()
                if selected { Image(systemName: "checkmark").foregroundStyle(SheetTheme.navy).fontWeight(.bold) }
            }
        }
    }

    private func macro(_ title: String, _ grams: Double, _ tint: Color) -> some View {
        VStack(spacing: 2) {
            Text("\(SheetFormat.number(grams, digits: 0)) g").font(.headline.monospacedDigit())
            Text(title).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 8)
        .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private extension Binding where Value == Int? {
    /// An Int? edited through a number field.
    var double: Binding<Double?> {
        Binding<Double?>(get: { wrappedValue.map(Double.init) }, set: { wrappedValue = $0.map { Int($0) } })
    }
}

/// Under the Food hero: where the targets come from, and the way to set them from your weight.
struct BodyTargetsLink: View {
    let snapshot: SheetSnapshot

    let onTap: () -> Void

    var body: some View {
        let s = snapshot.settings
        let following = s.followWeight == true && s.heightCm != nil
        Button(action: onTap) {
            HStack(spacing: 12) {
                IconBadge(symbol: "scalemass.fill", tint: SheetTheme.teal, size: 34)
                VStack(alignment: .leading, spacing: 2) {
                    Text(following ? "Targets follow your weight" : "Set targets from your weight").font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                    Text(following
                         ? [snapshot.latestWeight.map { "\(SheetFormat.number($0.kg)) kg" }, s.bodyGoal, "\(SheetFormat.number(s.proteinTarget, digits: 0)) g protein"].compactMap { $0 }.joined(separator: " · ")
                         : "Calories and protein worked out for your body and goal")
                        .font(.caption).foregroundStyle(Color(.secondaryLabel)).lineLimit(1)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(12)
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
