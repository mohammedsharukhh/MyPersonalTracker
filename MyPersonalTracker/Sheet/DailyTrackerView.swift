//
//  DailyTrackerView.swift
//  MyPersonalTracker
//
//  The Daily Tracker sheet: one row per day of the period. The inputs (five
//  Y/N habits, water, sleep, steps, weight, screen time, notes) are edited
//  here; Habits Met, Daily Score, Status and Streak are the sheet's formulas.
//

import SwiftUI

struct DailyTrackerView: View {
    let store: SheetStore
    @State private var editing: SheetDay?
    @State private var onlyMissing = false
    @State private var section: Section = {
        #if DEBUG
        // `-dailySection Planner` opens that section (screenshots).
        if let raw = UserDefaults.standard.string(forKey: "dailySection"), let s = Section(rawValue: raw) { return s }
        #endif
        return .habits
    }()
    @State private var nav = AppNavigation.shared

    private enum Section: String, CaseIterable { case habits = "Habits", planner = "Planner", goals = "Goals", wardrobe = "Wardrobe" }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    if let error = store.errorMessage, store.isConfigured {
                        SheetErrorBanner(message: error) { Task { await store.refresh() } }
                    }
                    SyncStatusBar(store: store)
                    Picker("View", selection: $section) {
                        ForEach(Section.allCases, id: \.self) { Text($0.rawValue) }
                    }
                    .pickerStyle(.segmented)
                    if let snapshot = store.snapshot, section != .habits {
                        if section == .planner {
                            PlannerContent(store: store, snapshot: snapshot)
                        } else if section == .goals {
                            LifeGoalsContent(store: store, snapshot: snapshot)
                        } else {
                            WardrobeContent(store: store, snapshot: snapshot)
                        }
                    } else if let snapshot = store.snapshot {
                        let today = SheetDate.today
                        if NewPeriodCard.isDue(snapshot.settings) {
                            NewPeriodCard(store: store, settings: snapshot.settings)
                        }
                        StepsConnectCard(store: store)
                        if let todayRow = snapshot.days.first(where: { $0.date == today }) {
                            TodayHero(day: todayRow, settings: snapshot.settings, inputs: snapshot.myInputs, stepStreak: snapshot.stepStreak()) { editing = todayRow }
                        } else if !NewPeriodCard.isDue(snapshot.settings) {
                            SheetCard {
                                Label("Today is outside the period in Settings (\(SheetDate.short(snapshot.settings.startDate)) – \(SheetDate.short(snapshot.settings.endDate))).",
                                      systemImage: "calendar.badge.exclamationmark")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        let allPast = snapshot.days.filter { $0.date < today }.reversed()
                        let missing = allPast.filter { !$0.isLogged }.count
                        HStack(alignment: .bottom) {
                            SectionTitle(title: "Previous days")
                            if missing > 0 || onlyMissing {
                                FilterChip(title: "Not logged · \(missing)", symbol: "exclamationmark.circle.fill",
                                           tint: SheetTheme.warn, selected: onlyMissing) {
                                    withAnimation(.snappy) { onlyMissing.toggle() }
                                }
                            }
                        }
                        let past = allPast.filter { !onlyMissing || !$0.isLogged }
                        if past.isEmpty {
                            Text(onlyMissing ? "Every day is logged. Nice work!" : "No earlier days in this period yet.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                        ForEach(Array(past)) { day in
                            Button { editing = day } label: { DayRow(day: day, settings: snapshot.settings, inputs: snapshot.myInputs) }
                                .buttonStyle(.plain)
                        }
                    } else if store.needsConnection {
                        NotConnectedHint()
                    } else {
                        SheetLoadingView(progress: store.loadProgress).padding(.top, 60)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .containerBackground(SheetTheme.pageWash, for: .navigation)
            .navigationTitle("Daily")
            .onChange(of: nav.dailySection, initial: true) {
                if let raw = nav.dailySection, let s = Section(rawValue: raw) { section = s; nav.dailySection = nil }
            }
            .toolbar { ToolbarItemGroup(placement: .topBarTrailing) { MoreButton(); SettingsButton() } }
            .refreshable { await store.refresh(); await HealthSteps.shared.sync(store: store) }
            .sheet(item: $editing) { day in
                DayLogSheet(store: store, day: day)
            }
        }
    }
}

struct NotConnectedHint: View {
    var body: some View {
        SheetCard {
            Label("Sign in with Google in More ▸ Settings & connection to connect your sheet.", systemImage: "link")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Today

private struct TodayHero: View {
    let day: SheetDay
    let settings: SheetSettings
    var inputs: MyInputData?
    var stepStreak = 0
    let onLog: () -> Void
    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .headline) private var ringSize = 72
    /// Habit tiles get wider with the text, so their names still fit on two lines.
    @ScaledMetric(relativeTo: .caption2) private var dotWidth = 64

    var body: some View {
        // Big text: the date and the ring stack instead of squeezing side by side.
        let header = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12)) : AnyLayout(HStackLayout(alignment: .center))
        VStack(alignment: .leading, spacing: 18) {
            header {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Today").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    Text(SheetDate.date(day.date)?.formatted(.dateTime.weekday(.wide).day().month(.wide)) ?? day.date)
                        .font(.title3.weight(.bold)).foregroundStyle(.white)
                    if let status = day.status {
                        Text(status).font(.caption.weight(.bold)).foregroundStyle(.white)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(.white.opacity(0.18), in: Capsule())
                            .padding(.top, 4)
                    }
                }
                if !typeSize.isAccessibilitySize { Spacer() }
                ZStack {
                    ScoreRing(fraction: day.score ?? 0, lineWidth: 8)
                    Text(day.isLogged ? (day.score ?? 0).formatted(.percent.precision(.fractionLength(0))) : "—")
                        .font(.system(.headline, design: .rounded, weight: .bold))
                        .foregroundStyle(.white)
                }
                .frame(width: min(ringSize, 120), height: min(ringSize, 120))
            }

            // Wraps onto a second row rather than cutting names off (or the last habit) at the edge.
            LazyVGrid(columns: [GridItem(.adaptive(minimum: dotWidth), spacing: 4, alignment: .top)], spacing: 12) {
                ForEach(HabitDot.all(day, settings, inputs)) { dot in heroDot(dot) }
            }

            VStack(spacing: 10) {
                if Tracker.water.isOn(settings) { TargetBar(symbol: "drop.fill", title: "Water", value: day.water, target: settings.waterTarget, unit: "L") }
                if Tracker.sleep.isOn(settings) { TargetBar(symbol: "bed.double.fill", title: "Sleep", value: day.sleep, target: settings.sleepTarget, unit: "h") }
                if Tracker.steps.isOn(settings) { TargetBar(symbol: "figure.walk", title: "Steps", value: day.steps, target: settings.stepsTarget, unit: "", digits: 0) }
                if stepStreak > 1, Tracker.steps.isOn(settings) {
                    let left = max(0, (settings.stepsTarget ?? 8000) - (day.steps ?? 0))
                    Label(left > 0 ? "\(stepStreak)-day step streak · \(Int(left).formatted()) more today keeps it going" : "\(stepStreak)-day step streak",
                          systemImage: "flame.fill")
                        .font(.caption.weight(.semibold)).foregroundStyle(.white.opacity(0.9))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            Button(action: onLog) {
                Label(day.isLogged ? "Edit today's log" : "Log today", systemImage: day.isLogged ? "pencil" : "plus.circle.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .foregroundStyle(SheetTheme.navyDeep)
            }
            .buttonStyle(.plain)
        }
        .padding(20)
        .background(SheetTheme.heroGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.top, 4)
    }

    private func heroDot(_ dot: HabitDot) -> some View {
        VStack(spacing: 6) {
            AnswerDot(symbol: dot.symbol, name: dot.name, answer: dot.answer, onDark: true, size: 38)
            Text(dot.name).font(.caption2).foregroundStyle(.white.opacity(0.85))
                .multilineTextAlignment(.center).lineLimit(2).padding(.horizontal, 2)
        }
    }
}

/// Progress toward a Settings target, on the hero's dark background.
private struct TargetBar: View {
    let symbol: String
    let title: String
    let value: Double?
    let target: Double?
    let unit: String
    var digits = 1
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        if typeSize.isAccessibilitySize {
            // Big text: name and figure on one line, the bar under them, nothing squeezed into fixed widths.
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Label(title, systemImage: symbol).font(.caption.weight(.semibold)).foregroundStyle(.white)
                    Spacer()
                    figure
                }
                bar
            }
        } else {
            HStack(spacing: 10) {
                Image(systemName: symbol).font(.caption).foregroundStyle(.white).frame(width: 18)
                Text(title).font(.caption.weight(.semibold)).foregroundStyle(.white).frame(width: 44, alignment: .leading)
                bar
                figure.frame(width: 92, alignment: .trailing)
            }
        }
    }

    private var bar: some View {
        let fraction = (value ?? 0) / max(target ?? 1, 0.0001)
        return Capsule().fill(.white.opacity(0.18)).frame(height: 7)
            .overlay(alignment: .leading) {
                GeometryReader { geo in
                    Capsule().fill(fraction >= 1 ? Color(hex: 0x4ADE80) : .white)
                        .frame(width: geo.size.width * min(max(fraction, 0), 1))
                }
            }
            .accessibilityHidden(true)
    }

    private var figure: some View {
        Text("\(SheetFormat.number(value, digits: digits))\(value == nil ? "" : unit) / \(SheetFormat.number(target, digits: digits))")
            .font(.caption2.monospacedDigit())
            .foregroundStyle(.white.opacity(0.85))
    }
}

// MARK: - Rows

private struct DayRow: View {
    let day: SheetDay
    let settings: SheetSettings
    var inputs: MyInputData?

    var body: some View {
        let date = SheetDate.date(day.date)
        HStack(spacing: 14) {
            VStack(spacing: 0) {
                Text(date?.formatted(.dateTime.weekday(.abbreviated)).uppercased() ?? "")
                    .font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                Text(date?.formatted(.dateTime.day()) ?? "")
                    .font(.system(.title2, design: .rounded, weight: .bold))
                Text(date?.formatted(.dateTime.month(.abbreviated)) ?? "")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            .frame(width: 44)

            if day.isLogged {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 6) {
                        let dots = HabitDot.all(day, settings, inputs)
                        ForEach(dots.prefix(dots.count > 7 ? 6 : 7)) { dot in
                            AnswerDot(symbol: dot.symbol, name: dot.name, answer: dot.answer, onDark: false, size: 26)
                        }
                        if dots.count > 7 {
                            Text("+\(dots.count - 6)").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        }
                    }
                    HStack(spacing: 12) {
                        if Tracker.water.isOn(settings) { mini("drop.fill", day.water, "L") }
                        if Tracker.sleep.isOn(settings) { mini("bed.double.fill", day.sleep, "h") }
                        if Tracker.steps.isOn(settings) { mini("figure.walk", day.steps, "", digits: 0) }
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 6) {
                    Text((day.score ?? 0).formatted(.percent.precision(.fractionLength(0))))
                        .font(.system(.title3, design: .rounded, weight: .bold))
                        .foregroundStyle(SheetTheme.tint(forStatus: day.status))
                    if let status = day.status { StatusPill(text: status) }
                }
            } else {
                Text("Not logged").font(.subheadline).foregroundStyle(.secondary)
                Spacer()
                Image(systemName: "plus.circle.fill").font(.title3).foregroundStyle(SheetTheme.navy)
            }
        }
        .padding(14)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .contentShape(Rectangle())
    }

    private func mini(_ symbol: String, _ value: Double?, _ unit: String, digits: Int = 1) -> some View {
        Label(value.map { SheetFormat.number($0, digits: digits) + unit } ?? "—", systemImage: symbol)
            .font(.caption.monospacedDigit())
            .foregroundStyle(.secondary)
            .labelStyle(.titleAndIcon)
    }
}

/// A habit's icon: filled green for Y, red for N, outlined grey when blank.
struct HabitIcon: View {
    let habit: Habit
    let answer: String?
    let onDark: Bool
    var size: CGFloat = 28
    var settings: SheetSettings?

    var body: some View {
        AnswerDot(symbol: settings.map { habit.symbol($0) } ?? habit.symbol, name: settings.map { habit.name($0) } ?? habit.title,
                  answer: answer, onDark: onDark, size: size)
    }
}

// MARK: - Editor

/// Log or edit one day. Writes exactly the Daily Tracker's input cells.
struct DayLogSheet: View {
    let store: SheetStore
    @State private var day: SheetDay
    /// What you logged for your own inputs, by input id.
    @State private var values: [String: String]
    private let savedValues: [String: String]
    @State private var customizing = false
    @State private var editing: InputEditTarget?
    @Environment(\.dismiss) private var dismiss

    init(store: SheetStore, day: SheetDay) {
        self.store = store
        _day = State(initialValue: day)
        let saved = store.snapshot?.inputData.values(on: day.date) ?? [:]
        savedValues = saved
        _values = State(initialValue: saved)
    }

    private var settings: SheetSettings? { store.snapshot?.settings }
    private var inputs: MyInputData { store.snapshot?.inputData ?? MyInputData() }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(habits) { habit in
                        HStack(spacing: 12) {
                            nameButton(symbol: settings.map { habit.symbol($0) } ?? habit.symbol,
                                       name: settings.map { habit.name($0) } ?? habit.title) { editing = .builtIn(habit) }
                            AnswerToggle(answer: binding(for: habit))
                        }
                    }
                    ForEach(inputs.list(.habit)) { input in
                        HStack(spacing: 12) {
                            nameButton(symbol: input.symbol, name: input.name) { editing = .mine(input) }
                            AnswerToggle(answer: Binding(get: { values[input.id] }, set: { values[input.id] = $0 }))
                        }
                    }
                    if habits.count + inputs.list(.habit).count > 1 {
                        Button {
                            for habit in habits { day.setAnswer("Y", for: habit) }
                            for input in inputs.list(.habit) { values[input.id] = "Y" }
                        } label: {
                            Label("Mark all done", systemImage: "checkmark.circle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(SheetTheme.good)
                        }
                    }
                } header: {
                    sectionHeader("Habits", add: "Add a habit") { editing = .mine(.new(.habit, order: inputs.inputs.count + 1)) }
                } footer: {
                    Text("Tap once for done, again for missed, a third time to clear. Tap a name to rename it or change its icon.")
                }
                Section {
                    if on(.water) { StepperRow(symbol: "drop.fill", tint: SheetTheme.sky, title: "Water", unit: "L", value: $day.water, step: 0.5, target: settings?.waterTarget) }
                    if on(.sleep) { StepperRow(symbol: "bed.double.fill", tint: SheetTheme.indigo, title: "Sleep", unit: "h", value: $day.sleep, step: 0.5, target: settings?.sleepTarget) }
                    if on(.steps) { StepperRow(symbol: "figure.walk", tint: SheetTheme.good, title: "Steps", unit: "", value: $day.steps, step: 500, target: settings?.stepsTarget) }
                    if on(.screen) { StepperRow(symbol: "iphone", tint: SheetTheme.warn, title: "Screen time", unit: "h", value: $day.screen, step: 0.5, target: settings?.screenLimit, isLimit: true) }
                    if on(.weight) { NumberRow(title: "Weight", unit: "kg", value: $day.weight, hint: nil) }
                    ForEach(inputs.list(.health)) { input in
                        StepperRow(symbol: input.symbol, tint: SheetTheme.navy, title: input.name, unit: spacedUnit(input.unit),
                                   value: number(input.id), step: input.stepSize, target: input.target,
                                   onTitleTap: { editing = .mine(input) })
                    }
                    if !Tracker.allCases.contains(where: on) && inputs.list(.health).isEmpty {
                        Text("Nothing to track here. Tap + to add something — pages read, cups of tea, minutes meditated.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                } header: {
                    sectionHeader("Health", add: "Add to Health") { editing = .mine(.new(.health, order: inputs.inputs.count + 1)) }
                }
                Section("Notes") {
                    TextField("Anything worth remembering", text: Binding(get: { day.notes ?? "" }, set: { day.notes = $0 }), axis: .vertical)
                        .lineLimit(2...5)
                }
                Section {
                    Button { customizing = true } label: { Label("Customize habits & trackers", systemImage: "slider.horizontal.3") }
                }
            }
            .navigationTitle(SheetDate.weekdayDay(day.date))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if store.isSaving {
                        ProgressView()
                    } else {
                        Button("Save") { Task { if await save() { dismiss() } } }
                            .fontWeight(.semibold)
                    }
                }
            }
            .popup(isPresented: Binding(get: { store.errorMessage != nil && !store.isSaving }, set: { if !$0 { store.errorMessage = nil } }),
                   .error("Couldn't save", message: store.errorMessage))
            .sheet(isPresented: $customizing) { HabitsEditor(store: store) }
            .sheet(item: $editing) { target in InputEditor(store: store, target: target) }
        }
        .tint(SheetTheme.navy)
    }

    private var habits: [Habit] { settings.map(Habit.active) ?? Habit.allCases }
    private func on(_ t: Tracker) -> Bool { settings.map { t.isOn($0) } ?? true }

    private func binding(for habit: Habit) -> Binding<String?> {
        Binding(get: { day.answer(habit) }, set: { day.setAnswer($0, for: habit) })
    }

    private func number(_ id: String) -> Binding<Double?> {
        Binding(get: { values[id].flatMap(Double.init) },
                set: { values[id] = $0.map { $0.rounded() == $0 ? String(Int($0)) : String($0) } })
    }

    /// The icon and name, tappable to rename.
    private func nameButton(symbol: String, name: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                IconBadge(symbol: symbol, tint: SheetTheme.navy, size: 32)
                Text(name).font(.subheadline).foregroundStyle(Color(.label)).multilineTextAlignment(.leading)
                Spacer(minLength: 4)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Rename or change icon")
    }

    private func sectionHeader(_ title: String, add: String, onAdd: @escaping () -> Void) -> some View {
        HStack {
            Text(title)
            Spacer()
            Button(action: onAdd) {
                Label("Add", systemImage: "plus")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(SheetTheme.navy, in: Capsule())
            }
            .buttonStyle(.plain)
            .textCase(nil)
            .accessibilityLabel(add)
        }
    }

    /// "cups" reads "2 cups"; short ones like "L" or "kg" stay "2L".
    private func spacedUnit(_ unit: String?) -> String {
        guard let u = unit?.trimmingCharacters(in: .whitespaces), !u.isEmpty else { return "" }
        return u.count > 2 ? " " + u : u
    }

    private func save() async -> Bool {
        let ok = await store.saveDay(day)
        var changed: [String: String] = [:]
        for id in Set(values.keys).union(savedValues.keys) where values[id] != savedValues[id] {
            changed[id] = values[id] ?? ""
        }
        store.saveInputValues(date: day.date, values: changed)
        return ok
    }
}

/// Cycles blank → Y → N → blank, shown as a coloured check or cross.
private struct AnswerToggle: View {
    @Binding var answer: String?

    var body: some View {
        Button {
            answer = answer == nil ? "Y" : (answer == "Y" ? "N" : nil)
        } label: {
            let (symbol, tint, text): (String, Color, String) = switch answer {
            case "Y": ("checkmark.circle.fill", SheetTheme.good, "Done")
            case "N": ("xmark.circle.fill", SheetTheme.bad, "Missed")
            default: ("circle.dashed", .secondary, "—")
            }
            HStack(spacing: 6) {
                Text(text).font(.subheadline.weight(.semibold))
                Image(systemName: symbol).font(.title3)
            }
            .foregroundStyle(tint)
            .frame(minWidth: 92, alignment: .trailing)
            .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: answer)
    }
}

/// − value + for quick entry, with the target and a tap-to-type fallback.
struct StepperRow: View {
    let symbol: String
    let tint: Color
    let title: String
    let unit: String
    @Binding var value: Double?
    let step: Double
    let target: Double?
    var isLimit = false
    /// Tapping the icon or name (to rename one of yours).
    var onTitleTap: (() -> Void)?

    @State private var typing = false
    @State private var text = ""
    @FocusState private var focused: Bool

    private var digits: Int { step >= 1 ? 0 : 1 }

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 12) {
                IconBadge(symbol: symbol, tint: tint, size: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline)
                    if let target {
                        let met = value.map { isLimit ? $0 <= target : $0 >= target } ?? false
                        Text("\(isLimit ? "Limit" : "Target") \(SheetFormat.number(target, digits: digits))\(unit)\(met ? " ✓" : "")")
                            .font(.caption)
                            .foregroundStyle(met ? SheetTheme.good : .secondary)
                    } else if !unit.isEmpty, onTitleTap != nil {
                        Text(unit.trimmingCharacters(in: .whitespaces)).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { onTitleTap?() }
            Spacer(minLength: 4)
            Button { adjust(-step) } label: { Image(systemName: "minus") }
                .buttonStyle(StepButtonStyle())
                .disabled((value ?? 0) <= 0)
            Group {
                if typing {
                    TextField("0", text: $text)
                        .keyboardType(.decimalPad)
                        .focused($focused)
                        .onSubmit { typing = false }
                        .onChange(of: text) { _, new in value = Double(new.replacingOccurrences(of: ",", with: ".")) }
                        .onChange(of: focused) { _, isFocused in if !isFocused { typing = false } }
                } else {
                    Text(value.map { SheetFormat.number($0, digits: digits) } ?? "—")
                        .onTapGesture {
                            text = value.map { SheetFormat.number($0, digits: digits).replacingOccurrences(of: ",", with: "") } ?? ""
                            typing = true
                            focused = true
                        }
                }
            }
            .font(.body.monospacedDigit().weight(.semibold))
            .multilineTextAlignment(.center)
            .frame(width: step >= 100 ? 70 : 44)
            Button { adjust(step) } label: { Image(systemName: "plus") }
                .buttonStyle(StepButtonStyle())
        }
        .sensoryFeedback(.increase, trigger: value)
    }

    private func adjust(_ delta: Double) {
        value = max(0, (value ?? (delta > 0 ? 0 : 0)) + delta)
    }
}

private struct StepButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.bold))
            .foregroundStyle(isEnabled ? SheetTheme.navy : Color(.tertiaryLabel))
            .frame(width: 34, height: 34)
            .background(SheetTheme.input, in: Circle())
            .scaleEffect(configuration.isPressed ? 0.9 : 1)
    }
}

/// A numeric input; empty text means an empty cell in the sheet.
struct NumberRow: View {
    let title: String
    var unit: String = ""
    @Binding var value: Double?
    let hint: String?
    var decimals = true

    @State private var text = ""

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline)
                if let hint { Text(hint).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer()
            HStack(spacing: 4) {
                TextField("0", text: $text)
                    .keyboardType(decimals ? .decimalPad : .numberPad)
                    .multilineTextAlignment(.trailing)
                    .font(.body.monospacedDigit().weight(.semibold))
                if !unit.isEmpty { Text(unit).font(.caption).foregroundStyle(.secondary) }
            }
            .frame(width: 130)
            .padding(.vertical, 8)
            .padding(.horizontal, 10)
            .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .onChange(of: text) { _, new in
                value = Double(new.replacingOccurrences(of: ",", with: "."))
            }
        }
        .onAppear {
            text = value.map { SheetFormat.number($0, digits: decimals ? 2 : 0).replacingOccurrences(of: ",", with: "") } ?? ""
        }
    }
}

/// Offers to fill steps in from Apple Health, until it's connected (or dismissed).
private struct StepsConnectCard: View {
    let store: SheetStore
    @State private var health = HealthSteps.shared
    @AppStorage("healthStepsCardDismissed") private var dismissed = false

    var body: some View {
        if health.isAvailable, !health.isEnabled, !dismissed, !store.isDemo {
            SheetCard {
                HStack(alignment: .top, spacing: 12) {
                    IconBadge(symbol: "figure.walk", tint: SheetTheme.good, size: 40)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Fill in steps, sleep and more automatically").font(.subheadline.weight(.semibold))
                        Text("Your iPhone already tracks them. Connect Apple Health and each day's steps, sleep, weight and workouts fill in here by themselves.")
                            .font(.caption).foregroundStyle(.secondary)
                        HStack(spacing: 14) {
                            Button("Connect Apple Health") { Task { await health.connect(store: store) } }
                                .font(.subheadline.weight(.semibold)).buttonStyle(.borderedProminent).tint(SheetTheme.good)
                            Button("Not now") { dismissed = true }.font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }
}
