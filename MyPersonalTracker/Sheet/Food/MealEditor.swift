//
//  MealEditor.swift
//  MyPersonalTracker
//
//  Logging a meal: photo, foods with amounts (nutrition scales as you change
//  them), totals against today's targets, and what it cost — which can go
//  straight into the Finance ledger. The photo is optional. Foods come from
//  the built-in list, what you've eaten before, and your own foods.
//

import PhotosUI
import SwiftUI

struct MealEditor: View {
    let store: SheetStore
    let existing: Meal?

    @Environment(\.dismiss) private var dismiss
    @State private var mealId: String
    @State private var type: MealType
    @State private var date: Date
    @State private var items: [FoodItem]
    @State private var image: UIImage?
    @State private var newPhoto: Data?
    @State private var removePhoto = false
    @State private var costText: String
    @State private var currency = Money.main
    @State private var addToFinance: Bool
    @State private var financeCategory = "Food & Dining"
    @State private var method: String
    @State private var notes: String
    @State private var searching = false
    @State private var takingPhoto = false
    @State private var photoItem: PhotosPickerItem?
    @State private var choosingPhoto = false
    @State private var confirmDelete = false
    /// Plan foods this meal came from, marked eaten on save.
    private let fromPlan: (date: String, type: MealType)?
    @State private var book = CurrencyBook.shared

    init(store: SheetStore, existing: Meal? = nil, type: MealType? = nil, date: Date = .now, items: [FoodItem] = [],
         fromPlan: (date: String, type: MealType)? = nil, startWithCamera: Bool = false, startWithSearch: Bool = false) {
        self.store = store
        self.existing = existing
        self.fromPlan = fromPlan
        _mealId = State(initialValue: existing?.id ?? UUID().uuidString)
        _type = State(initialValue: existing?.type ?? type ?? .forNow())
        let start: Date = {
            guard let existing, let day = SheetDate.date(existing.date) else { return date }
            let parts = (existing.time ?? "12:00").split(separator: ":").compactMap { Int($0) }
            return Calendar.current.date(bySettingHour: parts.first ?? 12, minute: parts.dropFirst().first ?? 0, second: 0, of: day) ?? day
        }()
        _date = State(initialValue: start)
        _items = State(initialValue: existing?.entries.map {
            FoodItem(food: $0.food, amount: $0.amount ?? 1, unit: $0.unit ?? "serving", calories: $0.calories ?? 0,
                     protein: $0.protein ?? 0, carbs: $0.carbs ?? 0, fat: $0.fat ?? 0)
        } ?? items)
        let cost = existing?.cost ?? items.compactMap(\.cost).reduce(0, +)
        _costText = State(initialValue: cost > 0 ? cost.formatted(.number.precision(.fractionLength(0...2)).grouping(.never)) : "")
        _addToFinance = State(initialValue: existing?.hasLedgerExpense ?? true)
        _method = State(initialValue: store.snapshot?.lists.methods.first ?? "Cash")
        _notes = State(initialValue: existing?.notes ?? "")
        _takingPhoto = State(initialValue: startWithCamera)
        _searching = State(initialValue: startWithSearch)
    }

    private var settings: SheetSettings? { store.snapshot?.settings }
    private var totals: NutritionTotals { NutritionTotals(items: items) }
    private var cost: Double? {
        guard let amount = SheetFormat.parse(costText.replacingOccurrences(of: ",", with: ".")), amount > 0 else { return nil }
        return book.convert(amount, from: currency, to: Money.main)
    }
    private var canSave: Bool { !items.isEmpty && items.allSatisfy { !$0.food.isEmpty } }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    photoCard
                    mealAndTime
                    foodsCard
                    totalsCard
                    costCard
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Notes (optional)").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                        TextField("How was it? Home-made, restaurant…", text: $notes, axis: .vertical)
                            .lineLimit(1...4)
                            .padding(12)
                            .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    if existing != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Delete meal", systemImage: "trash").font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(SheetTheme.bad.opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .tint(SheetTheme.bad)
                    }
                }
                .padding(16)
                // Exactly the screen's width: nothing inside can push it wider.
                .containerRelativeFrame(.horizontal)
            }
            .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
            .scrollDismissesKeyboard(.interactively)
            // Once you've started a meal, scrolling up mustn't drag the sheet (and your meal) away.
            .interactiveDismissDisabled(!items.isEmpty || newPhoto != nil)
            .background(SheetTheme.page)
            .safeAreaInset(edge: .bottom) { saveBar }
            .navigationTitle(existing == nil ? "Log a meal" : "Edit meal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() }.fontWeight(.semibold).disabled(!canSave) }
            }
            .sheet(isPresented: $searching) {
                FoodSearchView(store: store) { ref in items.append(ref.item) }
            }
            .fullScreenCover(isPresented: $takingPhoto) {
                MealCameraPicker { picked in
                    takingPhoto = false
                    if let picked { setPhoto(picked) }
                }
                .ignoresSafeArea()
            }
            .photosPicker(isPresented: $choosingPhoto, selection: $photoItem, matching: .images)
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let picked = UIImage(data: data) { setPhoto(picked) }
                    photoItem = nil
                }
            }
            .task {
                if let existing, image == nil { image = await MealPhotos.image(for: existing, thumb: false) }
                #if DEBUG
                // `-demoMealPhoto YES`: a wide landscape photo, to check the page stays inside the screen.
                if UserDefaults.standard.bool(forKey: "demoMealPhoto"), image == nil {
                    let size = CGSize(width: 2400, height: 900)
                    image = UIGraphicsImageRenderer(size: size).image { ctx in
                        UIColor.systemOrange.setFill(); ctx.fill(CGRect(origin: .zero, size: size))
                        UIColor.systemGreen.setFill(); ctx.fill(CGRect(x: 0, y: 0, width: 300, height: 900))
                        UIColor.systemBlue.setFill(); ctx.fill(CGRect(x: 2100, y: 0, width: 300, height: 900))
                    }
                }
                #endif
            }
            .popup(isPresented: $confirmDelete, .delete("Delete this meal?", message: existing?.hasLedgerExpense == true ? "Its expense in Finance is deleted too." : nil) {
                store.deleteMeal(id: mealId)
                dismiss()
            })
        }
        .tint(SheetTheme.navy)
    }

    // MARK: Sections

    private var photoCard: some View {
        Group {
            if let image, !removePhoto {
                // The frame sets the size and the photo fills it, cropped. (A fill-scaled
                // image sized by height alone comes out wider than the screen, which
                // made the whole page slide sideways while scrolling.)
                Color.clear
                    .frame(maxWidth: .infinity)
                    .frame(height: 220)
                    .overlay { Image(uiImage: image).resizable().scaledToFill() }
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(alignment: .topTrailing) {
                        Menu {
                            Button("Retake", systemImage: "camera") { takingPhoto = true }
                            Button("Choose another", systemImage: "photo") { choosingPhoto = true }
                            Button("Remove photo", systemImage: "trash", role: .destructive) {
                                self.image = nil; newPhoto = nil; removePhoto = true
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle.fill").font(.title).symbolRenderingMode(.palette)
                                .foregroundStyle(.white, .black.opacity(0.45)).padding(10)
                        }
                    }
            } else {
                HStack(spacing: 10) {
                    photoButton("Snap your meal", "camera.fill") { takingPhoto = true }
                    photoButton("Choose photo", "photo.on.rectangle") { choosingPhoto = true }
                }
            }
        }
    }

    private func photoButton(_ title: String, _ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: symbol).font(.title2)
                Text(title).font(.subheadline.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 22)
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .foregroundStyle(SheetTheme.navy)
        }
        .buttonStyle(.plain)
    }

    private var mealAndTime: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                ForEach(MealType.allCases) { meal in
                    Button { type = meal } label: {
                        VStack(spacing: 4) {
                            Image(systemName: meal.symbol).font(.subheadline)
                            Text(meal.rawValue).font(.caption.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(type == meal ? AnyShapeStyle(SheetTheme.navy) : AnyShapeStyle(SheetTheme.card), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .foregroundStyle(type == meal ? .white : .primary)
                    }
                    .buttonStyle(.plain)
                }
            }
            DatePicker("When", selection: $date, in: ...Date.now.addingTimeInterval(86_400 * 30))
                .font(.subheadline)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    private var foodsCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("What you ate").font(.headline)
                Spacer()
                Text("\(items.count) item\(items.count == 1 ? "" : "s")").font(.caption).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 8)

            ForEach($items) { $item in
                Divider().padding(.leading, 16)
                FoodItemRow(item: $item, onRemove: { items.removeAll { $0.id == item.id } }, onRemember: remember)
            }

            Divider()
            Button { searching = true } label: {
                Label(items.isEmpty ? "Add what you ate" : "Add another food", systemImage: "plus.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .font(.subheadline.weight(.semibold))
            .padding(.vertical, 14)
        }
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var totalsCard: some View {
        let t = totals
        return HStack(spacing: 0) {
            macro("Calories", t.calories, settings?.calorieTarget, "kcal", SheetTheme.warn)
            macro("Protein", t.protein, settings?.proteinTarget, "g", SheetTheme.good)
            macro("Carbs", t.carbs, settings?.carbTarget, "g", SheetTheme.sky)
            macro("Fat", t.fat, settings?.fatTarget, "g", SheetTheme.purple)
        }
        .padding(.vertical, 14)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func macro(_ title: String, _ value: Double, _ target: Double?, _ unit: String, _ tint: Color) -> some View {
        VStack(spacing: 3) {
            Text(title).font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
            Text("\(SheetFormat.number(value, digits: 0))\(unit == "kcal" ? "" : unit)")
                .font(.system(.title3, design: .rounded, weight: .bold)).foregroundStyle(tint)
            if let target, target > 0 {
                Text("\(Int((value / target * 100).rounded()))% of day").font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var costCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("What it cost").font(.headline)
                Spacer()
                Text("Leave empty for home food you've already paid for").font(.caption2).foregroundStyle(.secondary)
                    .multilineTextAlignment(.trailing)
            }
            HStack(spacing: 12) {
                if Money.single { CurrencyPill(code: Money.main) } else { CurrencyMenu(selection: $currency) }
                TextField("0.00", text: $costText)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.system(size: 26, weight: .bold, design: .rounded))
            }
            if currency != Money.main, let cost {
                Text("Saved as \(SheetFormat.qar(cost)) \(Money.main)").font(.caption).foregroundStyle(SheetTheme.navy)
            }
            if cost != nil {
                Toggle(isOn: $addToFinance) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Add to Finance").font(.subheadline.weight(.semibold))
                        Text("Logged as an expense too, and kept in step if you edit this meal.").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .tint(SheetTheme.navy)
                if addToFinance {
                    HStack(spacing: 8) {
                        ForEach(["Food & Dining", "Groceries"], id: \.self) { category in
                            Button { financeCategory = category } label: {
                                Label(category == "Food & Dining" ? "Eating out" : "Groceries", systemImage: SheetTheme.categoryIcon(category))
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(financeCategory == category ? SheetTheme.categoryColor(category) : SheetTheme.categoryColor(category).opacity(0.1), in: Capsule())
                                    .foregroundStyle(financeCategory == category ? .white : SheetTheme.categoryColor(category))
                            }
                            .buttonStyle(.plain)
                        }
                        Spacer()
                        Picker("Paid with", selection: $method) {
                            ForEach(store.snapshot?.lists.methods ?? ["Cash"], id: \.self) { Text($0) }
                        }
                        .labelsHidden()
                    }
                }
            }
        }
        .padding(16)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var saveBar: some View {
        Button(action: save) {
            Text(canSave ? "Save \(type.rawValue.lowercased()) · \(SheetFormat.number(totals.calories, digits: 0)) kcal · \(SheetFormat.number(totals.protein, digits: 0)) g protein"
                         : "Add a food to save")
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(canSave ? SheetTheme.navy : Color(.systemGray3), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .foregroundStyle(.white)
        }
        .buttonStyle(.plain)
        .disabled(!canSave)
        .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 4)
        .background(.bar)
    }

    // MARK: Actions

    private func remember(_ item: FoodItem) {
        let food = MyFood(name: item.food, amount: item.amount, unit: item.unit, calories: item.calories, protein: item.protein,
                          carbs: item.carbs, fat: item.fat, cost: nil, barcode: nil)
        Task { _ = await store.saveMyFood(food) }
    }

    private func setPhoto(_ picked: UIImage) {
        image = picked
        newPhoto = MealPhotos.prepare(picked)
        removePhoto = false
    }

    private func save() {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        store.saveMeal(id: mealId, date: SheetDate.string(date), time: f.string(from: date), type: type, items: items,
                       cost: cost, notes: notes.trimmingCharacters(in: .whitespacesAndNewlines), photo: newPhoto,
                       removePhoto: removePhoto, addToFinance: addToFinance && cost != nil,
                       financeCategory: financeCategory, method: method)
        if let fromPlan {
            let planned = store.snapshot?.foodData.plan.filter { $0.date == fromPlan.date && $0.meal == fromPlan.type.rawValue }.map(\.asItem) ?? []
            if !planned.isEmpty { store.savePlan(date: fromPlan.date, type: fromPlan.type, items: planned, eaten: true) }
        }
        dismiss()
    }
}

/// One food in a meal or plan: name, − amount +, and its nutrition.
struct FoodItemRow: View {
    @Binding var item: FoodItem
    let onRemove: () -> Void
    /// Saves this food, with your numbers, to My Foods.
    var onRemember: ((FoodItem) -> Void)? = nil
    @State private var editingNutrition = false
    @State private var remembered = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(item.food).font(.subheadline.weight(.semibold)).lineLimit(2)
                Spacer()
                Button(action: onRemove) { Image(systemName: "xmark.circle.fill").foregroundStyle(.tertiary) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Remove \(item.food)")
            }
            HStack(spacing: 10) {
                Button { item.setAmount(max(item.step, item.amount - item.step)) } label: { Image(systemName: "minus") }
                    .buttonStyle(AmountButtonStyle())
                AmountField(amount: item.amount) { item.setAmount($0) }
                if item.unitChoices.count > 1 {
                    Menu {
                        ForEach(item.unitChoices, id: \.self) { unit in
                            Button { item.setUnit(unit) } label: {
                                if unit == item.unit { Label(unit, systemImage: "checkmark") } else { Text(unit) }
                            }
                        }
                    } label: {
                        HStack(spacing: 2) {
                            Text(item.unit)
                            Image(systemName: "chevron.up.chevron.down").font(.caption2)
                        }
                        .font(.subheadline.weight(.semibold)).foregroundStyle(SheetTheme.navy)
                    }
                    .accessibilityLabel("Unit: \(item.unit)")
                } else {
                    Text(item.unit).font(.subheadline.weight(.semibold))
                }
                Button { item.setAmount(item.amount + item.step) } label: { Image(systemName: "plus") }
                    .buttonStyle(AmountButtonStyle())
                Spacer()
                Button { withAnimation(.snappy) { editingNutrition.toggle() } } label: {
                    HStack(spacing: 6) {
                        VStack(alignment: .trailing, spacing: 1) {
                            Text("\(SheetFormat.number(item.calories, digits: 0)) kcal").font(.caption.weight(.semibold))
                            Text("P \(SheetFormat.number(item.protein, digits: 0)) · C \(SheetFormat.number(item.carbs, digits: 0)) · F \(SheetFormat.number(item.fat, digits: 0))")
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                        Image(systemName: editingNutrition ? "checkmark.circle.fill" : "pencil.circle")
                            .font(.title3).foregroundStyle(SheetTheme.navy)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(editingNutrition ? "Done editing nutrition" : "Edit calories and protein")
            }
            if editingNutrition {
                VStack(alignment: .leading, spacing: 6) {
                    Text("For \(SheetFormat.number(item.amount, digits: 2)) \(item.unit) — change them to match your food")
                        .font(.caption2).foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        NutritionField(title: "kcal", value: item.calories, tint: SheetTheme.warn) { item.setNutrition(calories: $0) }
                        NutritionField(title: "Protein g", value: item.protein, tint: SheetTheme.good) { item.setNutrition(protein: $0) }
                        NutritionField(title: "Carbs g", value: item.carbs, tint: SheetTheme.sky) { item.setNutrition(carbs: $0) }
                        NutritionField(title: "Fat g", value: item.fat, tint: SheetTheme.purple) { item.setNutrition(fat: $0) }
                    }
                    if let onRemember {
                        Button {
                            onRemember(item)
                            remembered = true
                        } label: {
                            Label(remembered ? "Saved to My Foods" : "Remember for next time", systemImage: remembered ? "checkmark" : "star")
                                .font(.caption.weight(.semibold))
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(remembered ? SheetTheme.good : SheetTheme.navy)
                        .disabled(remembered)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}

/// The amount, typed: "250". Follows − / + and unit changes while you're not typing.
private struct AmountField: View {
    let amount: Double
    let onChange: (Double) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        TextField("0", text: $text)
            .keyboardType(.decimalPad)
            .multilineTextAlignment(.center)
            .font(.subheadline.monospacedDigit().weight(.semibold))
            .focused($focused)
            .frame(width: 64)
            .padding(.vertical, 6)
            .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .onAppear { text = Self.format(amount) }
            .onChange(of: text) { _, new in
                guard focused, let parsed = Double(new.replacingOccurrences(of: ",", with: ".")), parsed >= 0 else { return }
                onChange(parsed)
            }
            .onChange(of: amount) { _, new in if !focused { text = Self.format(new) } }
            .accessibilityLabel("Amount")
    }

    static func format(_ value: Double) -> String {
        value.formatted(.number.precision(.fractionLength(0...2)).grouping(.never))
    }
}

/// A small labelled number box for one nutrition value.
private struct NutritionField: View {
    let title: String
    let value: Double
    let tint: Color
    let onChange: (Double) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 3) {
            TextField("0", text: $text)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.center)
                .font(.subheadline.monospacedDigit().weight(.bold))
                .foregroundStyle(tint)
                .focused($focused)
                .padding(.vertical, 8)
                .background(tint.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            Text(title).font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
        }
        .onAppear { text = SheetFormat.number(value, digits: 1).replacingOccurrences(of: ",", with: "") }
        .onChange(of: text) { _, new in
            guard focused, let parsed = Double(new.replacingOccurrences(of: ",", with: ".")) else { return }
            onChange(parsed)
        }
        // Amount changed with − / +: show the rescaled value.
        .onChange(of: value) { _, new in
            if !focused { text = SheetFormat.number(new, digits: 1).replacingOccurrences(of: ",", with: "") }
        }
    }
}

private struct AmountButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.caption.weight(.bold))
            .foregroundStyle(SheetTheme.navy)
            .frame(width: 30, height: 30)
            .background(SheetTheme.input, in: Circle())
            .scaleEffect(configuration.isPressed ? 0.9 : 1)
    }
}

// MARK: - Search

/// Your foods, recent foods, the built-in list and Open Food Facts — in one search.
struct FoodSearchView: View {
    let store: SheetStore
    let onPick: (FoodRef) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var creating = false

    private var food: FoodData { store.snapshot?.foodData ?? FoodData(log: [], plan: [], myFoods: [], photoError: nil) }
    /// "milk 250ml" → search "milk", add 250 ml.
    private var typed: (name: String, quantity: FoodQuantity?) { FoodQuantity.split(query.trimmingCharacters(in: .whitespaces)) }

    var body: some View {
        NavigationStack {
            List {
                let mine = FoodDatabase.filter(FoodDatabase.mine(food.myFoods), typed.name)
                let recent = typed.name.isEmpty ? FoodDatabase.recent(food.log) : []
                let common = FoodDatabase.filter(FoodDatabase.common, typed.name)
                if let quantity = typed.quantity {
                    Section {
                        Label("Adds \(SheetFormat.number(quantity.amount, digits: 2))\(quantity.unit.map { " \($0)" } ?? "") of the food you pick",
                              systemImage: "scalemass").font(.subheadline).foregroundStyle(SheetTheme.navy)
                    }
                }
                if !recent.isEmpty { section("Recent", recent) }
                if !mine.isEmpty { section("My foods", mine) }
                if !common.isEmpty { section(query.isEmpty ? "Common foods" : "Common foods", Array(common.prefix(query.isEmpty ? 200 : 40))) }
                Section {
                    Button { creating = true } label: { Label(typed.name.isEmpty ? "Create a food" : "Create “\(typed.name)”", systemImage: "plus.square.on.square") }
                } footer: {
                    Text("Type an amount with the food — “milk 250ml”, “rice 150g”, “2 chapati” — or change it after adding. Can't find it? Create it once and it's saved to My Foods.")
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "e.g. milk 250ml, 2 eggs, dal")
            .navigationTitle("Add food")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            }
            .sheet(isPresented: $creating) {
                CustomFoodEditor(store: store, name: typed.name, barcode: nil) { ref in pick(ref) }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func section(_ title: String, _ foods: [FoodRef]) -> some View {
        Section(title) { ForEach(foods) { row($0) } }
    }

    private func row(_ food: FoodRef) -> some View {
        Button { pick(food) } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(food.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label)).lineLimit(2)
                    Text("\(SheetFormat.number(food.amount, digits: 2)) \(food.unit)\(food.detail.map { " · \($0)" } ?? "")")
                        .font(.caption).foregroundStyle(Color(.secondaryLabel)).lineLimit(1)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text("\(SheetFormat.number(food.calories, digits: 0)) kcal").font(.caption.weight(.semibold)).foregroundStyle(Color(.label))
                    Text("\(SheetFormat.number(food.protein, digits: 0)) g protein").font(.caption2).foregroundStyle(SheetTheme.good)
                }
                Image(systemName: "plus.circle.fill").foregroundStyle(SheetTheme.navy)
            }
        }
        .contextMenu {
            if food.source == .mine, let my = store.snapshot?.foodData.myFoods.first(where: { "mine-\($0.id)" == food.id }), let row = my.row {
                Button("Delete from My Foods", systemImage: "trash", role: .destructive) { Task { _ = await store.deleteMyFood(row: row) } }
            }
        }
    }

    private func pick(_ food: FoodRef) {
        var food = food
        food.wanted = typed.quantity
        onPick(food)
        dismiss()
    }
}

/// Your own food (a home recipe, a café item): saved to My Foods in the sheet.
struct CustomFoodEditor: View {
    let store: SheetStore
    let onSaved: (FoodRef) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var amount: Double? = 1
    @State private var unit = "serving"
    @State private var calories: Double?
    @State private var protein: Double?
    @State private var carbs: Double?
    @State private var fat: Double?
    @State private var cost: Double?
    @State private var barcode: String
    @State private var working = false

    init(store: SheetStore, name: String, barcode: String?, onSaved: @escaping (FoodRef) -> Void) {
        self.store = store
        self.onSaved = onSaved
        _name = State(initialValue: name)
        _barcode = State(initialValue: barcode ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name, e.g. Mum's chicken curry", text: $name)
                    NumberRow(title: "Amount", unit: "", value: $amount, hint: nil)
                    Picker("Unit", selection: $unit) {
                        ForEach(["serving", "g", "ml", "piece", "cup", "bowl", "plate", "slice", "tbsp", "scoop"], id: \.self) { Text($0) }
                    }
                } footer: {
                    Text("Nutrition below is for this amount.")
                }
                Section("Nutrition") {
                    NumberRow(title: "Calories", unit: "kcal", value: $calories, hint: nil)
                    NumberRow(title: "Protein", unit: "g", value: $protein, hint: nil)
                    NumberRow(title: "Carbs", unit: "g", value: $carbs, hint: nil)
                    NumberRow(title: "Fat", unit: "g", value: $fat, hint: nil)
                }
                Section {
                    NumberRow(title: "Usual cost", unit: Money.main, value: $cost, hint: "Optional — fills in a meal's cost")
                }
                if let error = store.errorMessage, !working {
                    Section { Text(error).font(.footnote).foregroundStyle(SheetTheme.bad) }
                }
            }
            .navigationTitle("New food")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if working { ProgressView() } else {
                        Button("Add") { save() }.fontWeight(.semibold)
                            .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || (calories ?? 0) <= 0)
                    }
                }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func save() {
        let food = MyFood(name: name.trimmingCharacters(in: .whitespaces), amount: amount ?? 1, unit: unit, calories: calories,
                          protein: protein ?? 0, carbs: carbs ?? 0, fat: fat ?? 0, cost: cost, barcode: barcode.isEmpty ? nil : barcode)
        let ref = FoodDatabase.mine([food]).first!
        working = true
        Task {
            // Added to the meal even if offline; saved to My Foods when the sheet is reachable.
            _ = await store.saveMyFood(food)
            working = false
            onSaved(ref)
            dismiss()
        }
    }
}

/// The system camera, for snapping a meal.
struct MealCameraPicker: UIViewControllerRepresentable {
    let onFinish: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (UIImage?) -> Void
        init(onFinish: @escaping (UIImage?) -> Void) { self.onFinish = onFinish }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            onFinish(info[.originalImage] as? UIImage)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { onFinish(nil) }
    }
}
