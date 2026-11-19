//
//  FoodView.swift
//  MyPersonalTracker
//
//  The Food tab: today's meals against your targets, the week's meal plan
//  (with a shopping list), and trends — protein, calories and food money.
//

import Charts
import SwiftUI

struct FoodView: View {
    let store: SheetStore

    private enum Section: String, CaseIterable { case today = "Today", plan = "Plan", shopping = "Shopping", trends = "Trends" }
    @State private var section: Section = .today
    @State private var day = Calendar.current.startOfDay(for: .now)
    @State private var weekStart = FoodView.monday(of: .now)
    @State private var editor: EditorRequest?
    @State private var bodyTargets = false

    /// What the meal editor should open with.
    struct EditorRequest: Identifiable {
        let id = UUID()
        var meal: Meal?
        var type: MealType?
        var items: [FoodItem] = []
        var fromPlan: (date: String, type: MealType)?
        var camera = false
        var search = false
    }

    static func monday(of date: Date) -> Date {
        let cal = Calendar.current
        let weekday = (cal.component(.weekday, from: date) + 5) % 7
        return cal.date(byAdding: .day, value: -weekday, to: cal.startOfDay(for: date)) ?? date
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    if let error = store.errorMessage, store.isConfigured {
                        SheetErrorBanner(message: error) { Task { await store.refresh() } }
                    }
                    SyncStatusBar(store: store)
                    if let photoError = store.snapshot?.foodData.photoError {
                        Label(photoError, systemImage: "photo.badge.exclamationmark")
                            .font(.footnote).foregroundStyle(SheetTheme.warn)
                            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                            .background(SheetTheme.warn.opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    Picker("View", selection: $section) {
                        ForEach(Section.allCases, id: \.self) { Text($0.rawValue) }
                    }
                    .pickerStyle(.segmented)

                    if let snapshot = store.snapshot {
                        switch section {
                        case .today: today(snapshot)
                        case .plan: plan(snapshot)
                        case .shopping: ShoppingSection(store: store, snapshot: snapshot, weekPlan: weekPlan(snapshot))
                        case .trends: FoodTrends(snapshot: snapshot)
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
            .navigationTitle("Food")
            .toolbar { ToolbarItemGroup(placement: .topBarTrailing) { MoreButton(); SettingsButton() } }
            .refreshable { await store.refresh() }
            .sheet(isPresented: $bodyTargets) { BodyTargetsView(store: store) }
            #if DEBUG
            .task {
                switch UserDefaults.standard.string(forKey: "sheetOpen") {
                case "body": try? await Task.sleep(for: .seconds(1)); bodyTargets = true
                case "meal": try? await Task.sleep(for: .seconds(1)); editor = EditorRequest(type: .forNow())
                case "shopping": section = .shopping
                case "plan": section = .plan
                default: break
                }
            }
            #endif
            .sheet(item: $editor) { request in
                MealEditor(store: store, existing: request.meal, type: request.type, date: day, items: request.items,
                           fromPlan: request.fromPlan, startWithCamera: request.camera, startWithSearch: request.search)
            }
        }
    }

    // MARK: Today

    @ViewBuilder
    private func today(_ s: SheetSnapshot) -> some View {
        let key = SheetDate.string(day)
        let meals = s.meals(on: key)
        let totals = NutritionTotals(entries: meals.flatMap(\.entries))
        let cost = meals.compactMap(\.cost).reduce(0, +)

        DayStepper(date: $day)
        NutritionHero(totals: totals, settings: s.settings, cost: cost, isToday: Calendar.current.isDateInToday(day))
        BodyTargetsLink(snapshot: s) { bodyTargets = true }

        HStack(spacing: 10) {
            QuickFoodButton(title: "Snap meal", symbol: "camera.fill", primary: false) {
                editor = EditorRequest(type: .forNow(), camera: true)
            }
            QuickFoodButton(title: "Add food", symbol: "plus.circle.fill", primary: true) {
                editor = EditorRequest(type: .forNow(), search: true)
            }
        }

        let planned = s.foodData.plan.filter { $0.date == key && !$0.eaten }
        if !planned.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 10) {
                    Label("Planned for \(Calendar.current.isDateInToday(day) ? "today" : "this day")", systemImage: "calendar.badge.checkmark").font(.headline)
                    ForEach(MealType.allCases) { type in
                        let items = planned.filter { $0.meal == type.rawValue }
                        if !items.isEmpty {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(type.rawValue).font(.subheadline.weight(.semibold))
                                    Text(items.map(\.food).joined(separator: ", ")).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                                }
                                Spacer()
                                Button("Log it") {
                                    editor = EditorRequest(type: type, items: items.map(\.asItem), fromPlan: (key, type))
                                }
                                .buttonStyle(.bordered).tint(SheetTheme.good)
                            }
                        }
                    }
                }
            }
        }

        ForEach(MealType.allCases) { type in
            let ofType = meals.filter { $0.type == type }
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label(type.rawValue, systemImage: type.symbol).font(.headline)
                    Spacer()
                    if !ofType.isEmpty {
                        let t = NutritionTotals(entries: ofType.flatMap(\.entries))
                        Text("\(SheetFormat.number(t.calories, digits: 0)) kcal · \(SheetFormat.number(t.protein, digits: 0)) g protein")
                            .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    }
                }
                ForEach(ofType) { meal in
                    MealCard(meal: meal) { editor = EditorRequest(meal: meal) }
                }
                if ofType.isEmpty {
                    Button { editor = EditorRequest(type: type, search: true) } label: {
                        Label("Add \(type.rawValue.lowercased())", systemImage: "plus")
                            .font(.subheadline.weight(.medium))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14)
                            .background(SheetTheme.card.opacity(0.6), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [5])).foregroundStyle(.tertiary))
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
                }
            }
            .padding(.top, 4)
        }
    }

    // MARK: Plan

    @ViewBuilder
    private func plan(_ s: SheetSnapshot) -> some View {
        WeekPlan(store: store, snapshot: s, weekStart: $weekStart, onShopping: { section = .shopping }) { request in editor = request }
    }

    /// The planned foods for the week shown on the Plan section.
    private func weekPlan(_ s: SheetSnapshot) -> [PlanItem] {
        let keys = Set((0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: weekStart) }.map(SheetDate.string))
        return s.foodData.plan.filter { keys.contains($0.date) && !$0.eaten }
    }
}

// MARK: - Today pieces

private struct DayStepper: View {
    @Binding var date: Date

    var body: some View {
        HStack {
            Button { date = Calendar.current.date(byAdding: .day, value: -1, to: date) ?? date } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 36)
            }
            Spacer()
            VStack(spacing: 1) {
                Text(Calendar.current.isDateInToday(date) ? "Today" : (Calendar.current.isDateInYesterday(date) ? "Yesterday" : date.formatted(.dateTime.weekday(.wide))))
                    .font(.headline)
                Text(date.formatted(.dateTime.day().month(.wide))).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button { date = Calendar.current.date(byAdding: .day, value: 1, to: date) ?? date } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 36)
            }
            .disabled(Calendar.current.isDateInToday(date))
        }
        .foregroundStyle(SheetTheme.navy)
    }
}

/// Calories as a ring, protein/carbs/fat as bars, and what food cost today.
struct NutritionHero: View {
    let totals: NutritionTotals
    let settings: SheetSettings
    let cost: Double
    let isToday: Bool

    var body: some View {
        let kcalTarget = settings.calorieTarget ?? 2200
        let left = kcalTarget - totals.calories
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 18) {
                ZStack {
                    ScoreRing(fraction: totals.calories / max(kcalTarget, 1), lineWidth: 10,
                              tint: totals.calories > kcalTarget * 1.05 ? Color(hex: 0xF87171) : Color(hex: 0xFBBF24))
                    VStack(spacing: 0) {
                        Text(SheetFormat.number(totals.calories, digits: 0)).font(.system(.title2, design: .rounded, weight: .bold))
                        Text("of \(SheetFormat.number(kcalTarget, digits: 0)) kcal").font(.caption2).opacity(0.8)
                    }
                    .foregroundStyle(.white)
                }
                .frame(width: 110, height: 110)
                VStack(alignment: .leading, spacing: 10) {
                    macroBar("Protein", totals.protein, settings.proteinTarget ?? 120, Color(hex: 0x4ADE80))
                    macroBar("Carbs", totals.carbs, settings.carbTarget ?? 250, Color(hex: 0x7DD3FC))
                    macroBar("Fat", totals.fat, settings.fatTarget ?? 70, Color(hex: 0xC4B5FD))
                }
            }
            HStack(spacing: 8) {
                HeroChip(symbol: left >= 0 ? "flame" : "exclamationmark.triangle.fill",
                         text: left >= 0 ? "\(SheetFormat.number(left, digits: 0)) kcal left" : "\(SheetFormat.number(-left, digits: 0)) kcal over")
                if totals.protein >= (settings.proteinTarget ?? 120) {
                    HeroChip(symbol: "checkmark.seal.fill", text: "Protein goal hit")
                }
                if cost > 0 { HeroChip(symbol: "banknote", text: "\(SheetFormat.qar(cost)) \(Money.main)") }
            }
        }
        .padding(20)
        .background(SheetTheme.heroGradient,
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func macroBar(_ title: String, _ value: Double, _ target: Double, _ tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title).font(.caption.weight(.semibold))
                Spacer()
                Text("\(SheetFormat.number(value, digits: 0)) / \(SheetFormat.number(target, digits: 0)) g").font(.caption2.monospacedDigit())
            }
            .foregroundStyle(.white)
            Capsule().fill(.white.opacity(0.2)).frame(height: 7)
                .overlay(alignment: .leading) {
                    GeometryReader { geo in Capsule().fill(tint).frame(width: geo.size.width * min(max(value / max(target, 1), 0), 1)) }
                }
        }
    }
}

private struct QuickFoodButton: View {
    let title: String
    let symbol: String
    /// One filled main action per screen; the other is the quieter tinted style.
    let primary: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(primary ? SheetTheme.navy : SheetTheme.navy.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .foregroundStyle(primary ? .white : SheetTheme.navy)
        }
        .buttonStyle(.plain)
    }
}

struct MealCard: View {
    let meal: Meal
    let onTap: () -> Void
    @State private var photo: UIImage?

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 12) {
                Group {
                    if let photo {
                        Image(uiImage: photo).resizable().scaledToFill()
                    } else {
                        Image(systemName: meal.type.symbol).font(.title3).foregroundStyle(SheetTheme.good)
                            .frame(maxWidth: .infinity, maxHeight: .infinity).background(SheetTheme.good.opacity(0.12))
                    }
                }
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(meal.entries.map(\.food).joined(separator: ", ")).font(.subheadline.weight(.semibold)).lineLimit(2)
                        if meal.isPending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                    }
                    let t = meal.totals
                    Text("\(SheetFormat.number(t.calories, digits: 0)) kcal · P \(SheetFormat.number(t.protein, digits: 0)) g · C \(SheetFormat.number(t.carbs, digits: 0)) g · F \(SheetFormat.number(t.fat, digits: 0)) g")
                        .font(.caption).foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        if let time = meal.time { Label(time, systemImage: "clock").font(.caption2).foregroundStyle(.tertiary) }
                        if let cost = meal.cost, cost > 0 {
                            Label("\(SheetFormat.qar(cost)) \(Money.main)", systemImage: meal.hasLedgerExpense ? "banknote.fill" : "banknote")
                                .font(.caption2.weight(.semibold)).foregroundStyle(SheetTheme.teal)
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .task(id: "\(meal.id)-\(meal.photo ?? "")") { photo = await MealPhotos.image(for: meal, thumb: true) }
    }
}

// MARK: - Week plan

private struct WeekPlan: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @Binding var weekStart: Date
    let onShopping: () -> Void
    let onLog: (FoodView.EditorRequest) -> Void

    @State private var editing: PlanSlot?
    @State private var copying = false
    @State private var addedNote: String?

    struct PlanSlot: Identifiable {
        let date: String
        let type: MealType
        var id: String { "\(date)-\(type.rawValue)" }
    }

    private var days: [Date] { (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: weekStart) } }

    /// "Add to shopping list" and "Copy last week", each on one line.
    @ViewBuilder
    private func weekActions(_ weekItems: [PlanItem], fill: Bool) -> some View {
        Button {
            let added = ShoppingActions.addPlan(weekItems.filter { !$0.eaten }, to: store)
            addedNote = added == 0 ? "Already on your shopping list." : "Added \(added) to your shopping list."
        } label: {
            Label("Add to shopping list", systemImage: "cart.badge.plus").lineLimit(1).frame(maxWidth: fill ? .infinity : nil)
        }
        .buttonStyle(.bordered).disabled(weekItems.isEmpty)
        Button { copying = true } label: {
            Label("Copy last week", systemImage: "doc.on.doc").lineLimit(1).frame(maxWidth: fill ? .infinity : nil)
        }
        .buttonStyle(.bordered)
    }

    var body: some View {
        let plan = snapshot.foodData.plan
        let weekKeys = Set(days.map(SheetDate.string))
        let weekItems = plan.filter { weekKeys.contains($0.date) }
        let totals = NutritionTotals(plan: weekItems)
        let cost = weekItems.compactMap(\.cost).reduce(0, +)

        HStack {
            Button { weekStart = Calendar.current.date(byAdding: .day, value: -7, to: weekStart) ?? weekStart } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 36)
            }
            Spacer()
            VStack(spacing: 1) {
                Text(weekStart == FoodView.monday(of: .now) ? "This week" : "Week of \(weekStart.formatted(.dateTime.day().month()))").font(.headline)
                Text("\(weekStart.formatted(.dateTime.day().month())) – \(days.last!.formatted(.dateTime.day().month()))").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button { weekStart = Calendar.current.date(byAdding: .day, value: 7, to: weekStart) ?? weekStart } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 36)
            }
        }
        .foregroundStyle(SheetTheme.navy)

        SheetCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Week planned").font(.headline)
                        Text("\(SheetFormat.number(totals.calories / 7, digits: 0)) kcal · \(SheetFormat.number(totals.protein / 7, digits: 0)) g protein a day on average")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if cost > 0 {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("\(SheetFormat.qar(cost))").font(.headline.monospacedDigit())
                            Text("\(Money.main) est.").font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                }
                // Side by side when both fit on one line, otherwise one under the other — never wrapped.
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { weekActions(weekItems, fill: false) }
                    VStack(spacing: 8) { weekActions(weekItems, fill: true) }
                }
                .font(.subheadline)
                if let addedNote {
                    Button { onShopping() } label: {
                        Label("\(addedNote) Open it", systemImage: "cart").font(.caption.weight(.semibold))
                    }
                }
            }
        }

        ForEach(days, id: \.self) { date in
            let key = SheetDate.string(date)
            let items = plan.filter { $0.date == key }
            let dayTotals = NutritionTotals(plan: items)
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(date.formatted(.dateTime.weekday(.wide).day().month())).font(.subheadline.weight(.bold))
                    if Calendar.current.isDateInToday(date) { StatusPill(text: "Today", tint: SheetTheme.navy) }
                    Spacer()
                    if !items.isEmpty {
                        Text("\(SheetFormat.number(dayTotals.calories, digits: 0)) kcal · \(SheetFormat.number(dayTotals.protein, digits: 0)) g P")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(dayTotals.protein >= (snapshot.settings.proteinTarget ?? 120) ? SheetTheme.good : .secondary)
                    }
                }
                .padding(.horizontal, 14).padding(.top, 12).padding(.bottom, 6)
                ForEach(MealType.allCases) { type in
                    let mealItems = items.filter { $0.meal == type.rawValue }
                    Divider().padding(.leading, 14)
                    Button { editing = PlanSlot(date: key, type: type) } label: {
                        HStack(spacing: 10) {
                            Image(systemName: type.symbol).font(.caption).foregroundStyle(SheetTheme.good).frame(width: 18)
                            Text(type.rawValue).font(.caption.weight(.semibold)).foregroundStyle(.secondary).frame(width: 66, alignment: .leading)
                            Text(mealItems.isEmpty ? "Plan…" : mealItems.map(\.food).joined(separator: ", "))
                                .font(.subheadline)
                                .foregroundStyle(mealItems.isEmpty ? .tertiary : .primary)
                                .lineLimit(1)
                            Spacer(minLength: 0)
                            if mealItems.contains(where: \.eaten) {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(SheetTheme.good)
                            }
                        }
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .sheet(item: $editing) { slot in
            PlanEditor(store: store, date: slot.date, type: slot.type) { items in
                onLog(FoodView.EditorRequest(type: slot.type, items: items, fromPlan: (slot.date, slot.type)))
            }
        }
        .popup(isPresented: $copying, Popup(title: "Copy last week's plan?", message: "This week's plan is replaced with last week's meals.",
                                            symbol: "doc.on.doc.fill", confirmTitle: "Copy last week") {
            let from = Calendar.current.date(byAdding: .day, value: -7, to: weekStart) ?? weekStart
            Task { _ = await store.copyPlanWeek(from: SheetDate.string(from), to: SheetDate.string(weekStart)) }
        })
    }
}

/// Plan one meal: pick foods and amounts; "Eat now" logs it.
private struct PlanEditor: View {
    let store: SheetStore
    let date: String
    let type: MealType
    let onLogNow: ([FoodItem]) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var items: [FoodItem]
    @State private var searching = false

    init(store: SheetStore, date: String, type: MealType, onLogNow: @escaping ([FoodItem]) -> Void) {
        self.store = store
        self.date = date
        self.type = type
        self.onLogNow = onLogNow
        _items = State(initialValue: store.snapshot?.foodData.plan.filter { $0.date == date && $0.meal == type.rawValue }.map(\.asItem) ?? [])
    }

    var body: some View {
        let t = NutritionTotals(items: items)
        NavigationStack {
            List {
                Section {
                    ForEach($items) { $item in
                        FoodItemRow(item: $item) { items.removeAll { $0.id == item.id } }
                            .listRowInsets(EdgeInsets())
                    }
                    Button { searching = true } label: { Label("Add food", systemImage: "plus.circle.fill") }
                } header: {
                    Text("\(type.rawValue) · \(SheetDate.weekdayDay(date))")
                } footer: {
                    Text("\(SheetFormat.number(t.calories, digits: 0)) kcal · \(SheetFormat.number(t.protein, digits: 0)) g protein · \(SheetFormat.number(t.carbs, digits: 0)) g carbs · \(SheetFormat.number(t.fat, digits: 0)) g fat")
                }
                if !items.isEmpty {
                    Section {
                        Button { dismiss(); onLogNow(items) } label: { Label("I ate this — log it", systemImage: "checkmark.circle") }
                        Button("Clear this meal", role: .destructive) { items = [] }
                    }
                }
            }
            .navigationTitle("Plan \(type.rawValue.lowercased())")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        store.savePlan(date: date, type: type, items: items, eaten: false)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .sheet(isPresented: $searching) { FoodSearchView(store: store) { items.append($0.item) } }
        }
        .tint(SheetTheme.navy)
    }
}

// MARK: - Trends

private struct FoodTrends: View {
    let snapshot: SheetSnapshot
    @State private var range = 7

    private struct DayPoint: Identifiable {
        let date: Date
        let calories: Double
        let protein: Double
        var id: Date { date }
    }

    var body: some View {
        let s = snapshot
        let cal = Calendar.current
        let start = cal.date(byAdding: .day, value: -(range - 1), to: cal.startOfDay(for: .now)) ?? .now
        let log = s.foodData.log.filter { ($0.date) >= SheetDate.string(start) }
        let byDay = Dictionary(grouping: log, by: \.date)
        let points = (0..<range).compactMap { offset -> DayPoint? in
            guard let date = cal.date(byAdding: .day, value: offset, to: start) else { return nil }
            let t = NutritionTotals(entries: byDay[SheetDate.string(date)] ?? [])
            return DayPoint(date: date, calories: t.calories, protein: t.protein)
        }
        let logged = points.filter { $0.calories > 0 }
        let avgKcal = logged.isEmpty ? 0 : logged.reduce(0) { $0 + $1.calories } / Double(logged.count)
        let avgProtein = logged.isEmpty ? 0 : logged.reduce(0) { $0 + $1.protein } / Double(logged.count)
        let proteinTarget = s.settings.proteinTarget ?? 120
        let hit = logged.filter { $0.protein >= proteinTarget }.count

        Picker("Range", selection: $range) {
            Text("7 days").tag(7)
            Text("30 days").tag(30)
        }
        .pickerStyle(.segmented)

        HStack(spacing: 12) {
            SheetStatTile(symbol: "flame.fill", tint: SheetTheme.warn, title: "Avg calories", value: SheetFormat.number(avgKcal, digits: 0),
                          caption: "Target \(SheetFormat.number(s.settings.calorieTarget, digits: 0))")
            SheetStatTile(symbol: "bolt.heart.fill", tint: SheetTheme.good, title: "Avg protein", value: "\(SheetFormat.number(avgProtein, digits: 0)) g",
                          caption: "\(hit) of \(logged.count) days hit \(SheetFormat.number(proteinTarget, digits: 0)) g")
        }

        trendChart("Protein per day", points.map { ($0.date, $0.protein) }, target: proteinTarget, tint: SheetTheme.good, unit: "g")
        trendChart("Calories per day", points.map { ($0.date, $0.calories) }, target: s.settings.calorieTarget, tint: SheetTheme.warn, unit: "kcal")

        FoodMoneyCard(snapshot: s, log: log)
        TopFoodsCard(log: log)
    }

    private func trendChart(_ title: String, _ values: [(Date, Double)], target: Double?, tint: Color, unit: String) -> some View {
        SheetCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(title).font(.headline)
                if values.allSatisfy({ $0.1 == 0 }) {
                    Text("Log meals to see this.").font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, minHeight: 120)
                } else {
                    Chart {
                        ForEach(values, id: \.0) { point in
                            BarMark(x: .value("Day", point.0, unit: .day), y: .value(unit, point.1))
                                .foregroundStyle(target.map { point.1 >= $0 ? tint : tint.opacity(0.45) } ?? tint)
                                .cornerRadius(3)
                        }
                        if let target {
                            RuleMark(y: .value("Target", target))
                                .foregroundStyle(.secondary)
                                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                                .annotation(position: .top, alignment: .trailing) { Text("Target").font(.caption2).foregroundStyle(.secondary) }
                        }
                    }
                    .frame(height: 170)
                }
            }
        }
    }
}

/// Food money: the ledger's Groceries + Food & Dining against the food budget, and meal costs.
private struct FoodMoneyCard: View {
    let snapshot: SheetSnapshot
    let log: [FoodEntry]

    var body: some View {
        let month = String(SheetDate.today.prefix(7))
        let foodCategories: Set<String> = ["Groceries", "Food & Dining"]
        let spent = snapshot.expenses.filter { ($0.date ?? "").hasPrefix(month) && foodCategories.contains($0.category ?? "") }
        let groceries = spent.filter { $0.category == "Groceries" }.reduce(0) { $0 + ($1.qar ?? 0) }
        let eatingOut = spent.filter { $0.category == "Food & Dining" }.reduce(0) { $0 + ($1.qar ?? 0) }
        let total = groceries + eatingOut
        let budget = snapshot.settings.foodBudget ?? 0
        let meals = Meal.group(log)
        let paid = meals.compactMap(\.cost).filter { $0 > 0 }
        let mealProtein = meals.filter { ($0.cost ?? 0) > 0 }.reduce(0) { $0 + $1.totals.protein }
        SheetCard {
            VStack(alignment: .leading, spacing: 12) {
                Label("Food money this month", systemImage: "banknote").font(.headline)
                if budget > 0 {
                    ProgressRow(title: "Spent of \(SheetFormat.number(budget, digits: 0)) \(Money.main) budget", detail: SheetFormat.qar(total),
                                fraction: total / budget, tint: total > budget ? SheetTheme.bad : SheetTheme.teal)
                }
                KeyValueRow(key: "Groceries", value: "\(SheetFormat.qar(groceries)) \(Money.main)")
                KeyValueRow(key: "Eating out", value: "\(SheetFormat.qar(eatingOut)) \(Money.main)")
                if !paid.isEmpty {
                    Divider()
                    KeyValueRow(key: "Average paid meal", value: "\(SheetFormat.qar(paid.reduce(0, +) / Double(paid.count))) \(Money.main)")
                    let spentOnMeals = paid.reduce(0, +)
                    if spentOnMeals > 0 {
                        KeyValueRow(key: "Protein per 10 \(Money.main) (paid meals)", value: "\(SheetFormat.number(mealProtein / spentOnMeals * 10, digits: 1)) g")
                    }
                }
                Text("From Finance (Groceries + Food & Dining). Meals with a cost added to Finance count here automatically.")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}

private struct TopFoodsCard: View {
    let log: [FoodEntry]

    var body: some View {
        let byFood = Dictionary(grouping: log, by: { $0.food })
        let frequent = byFood.map { ($0.key, $0.value.count) }.sorted { $0.1 > $1.1 }.prefix(5)
        let protein = byFood.map { ($0.key, $0.value.reduce(0) { $0 + ($1.protein ?? 0) }) }.sorted { $0.1 > $1.1 }.prefix(5)
        if !log.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Your foods").font(.headline)
                    Text("Most eaten").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    ForEach(Array(frequent), id: \.0) { KeyValueRow(key: $0.0, value: "\($0.1)×") }
                    Text("Biggest protein sources").font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.top, 4)
                    ForEach(Array(protein), id: \.0) { KeyValueRow(key: $0.0, value: "\(SheetFormat.number($0.1, digits: 0)) g", valueTint: SheetTheme.good) }
                }
            }
        }
    }
}

/// Small Dashboard card: today's calories and protein, opens the Food tab.
struct FoodTodayCard: View {
    let snapshot: SheetSnapshot

    var body: some View {
        let totals = NutritionTotals(entries: snapshot.foodData.log.filter { $0.date == SheetDate.today })
        let s = snapshot.settings
        Button { AppNavigation.shared.tab = AppTab.food } label: {
            HStack(spacing: 14) {
                IconBadge(symbol: "fork.knife", tint: SheetTheme.good, size: 40)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Food today").font(.subheadline.weight(.semibold))
                    ProgressRow(title: "Calories", detail: "\(SheetFormat.number(totals.calories, digits: 0)) / \(SheetFormat.number(s.calorieTarget, digits: 0))",
                                fraction: totals.calories / max(s.calorieTarget ?? 1, 1), tint: SheetTheme.warn)
                    ProgressRow(title: "Protein", detail: "\(SheetFormat.number(totals.protein, digits: 0)) / \(SheetFormat.number(s.proteinTarget, digits: 0)) g",
                                fraction: totals.protein / max(s.proteinTarget ?? 1, 1), tint: SheetTheme.good)
                }
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(14)
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
