//
//  FoodModels.swift
//  MyPersonalTracker
//
//  The food side of the sheet (Food Log, Meal Plan, My Foods), the built-in
//  food list, and meal photos kept in Google Drive with a cache on the phone.
//

import Foundation
import UIKit

// MARK: - From the sheet

struct FoodData: Codable, Hashable {
    var log: [FoodEntry]
    var plan: [PlanItem]
    var myFoods: [MyFood]
    /// Set when meal photos can't be saved to Drive yet (needs one-time approval).
    var photoError: String?
}

/// One food eaten: a Food Log row. Rows with the same `mealId` are one meal.
struct FoodEntry: Codable, Hashable, Identifiable {
    var row: Int
    var mealId: String
    var date: String
    var time: String?
    var meal: String
    var food: String
    var amount: Double?
    var unit: String?
    var calories: Double?
    var protein: Double?
    var carbs: Double?
    var fat: Double?
    /// Meal-level fields, on the meal's first row only.
    var cost: Double?
    var photo: String?
    var ledgerRow: Int?
    var notes: String?

    var id: String { "\(mealId)-\(row)" }

    enum CodingKeys: String, CodingKey {
        case row, mealId, date, time, meal, food, amount, unit, calories, protein, carbs, fat, cost, photo, ledgerRow, notes
    }

    init(row: Int, mealId: String, date: String, time: String?, meal: String, food: String, amount: Double?, unit: String?,
         calories: Double?, protein: Double?, carbs: Double?, fat: Double?, cost: Double?, photo: String?, ledgerRow: Int?, notes: String?) {
        self.row = row; self.mealId = mealId; self.date = date; self.time = time; self.meal = meal; self.food = food
        self.amount = amount; self.unit = unit; self.calories = calories; self.protein = protein; self.carbs = carbs
        self.fat = fat; self.cost = cost; self.photo = photo; self.ledgerRow = ledgerRow; self.notes = notes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        row = Int(c.flexDouble(.row) ?? 0)
        mealId = c.flexString(.mealId) ?? ""
        date = c.flexString(.date) ?? ""
        time = c.flexString(.time)
        meal = c.flexString(.meal) ?? "Snack"
        food = c.flexString(.food) ?? ""
        amount = c.flexDouble(.amount); unit = c.flexString(.unit)
        calories = c.flexDouble(.calories); protein = c.flexDouble(.protein)
        carbs = c.flexDouble(.carbs); fat = c.flexDouble(.fat)
        cost = c.flexDouble(.cost); photo = c.flexString(.photo)
        ledgerRow = c.flexDouble(.ledgerRow).map { Int($0) }; notes = c.flexString(.notes)
    }
}

/// One planned food on the Meal Plan.
struct PlanItem: Codable, Hashable, Identifiable {
    var row: Int
    var date: String
    var meal: String
    var food: String
    var amount: Double?
    var unit: String?
    var calories: Double?
    var protein: Double?
    var carbs: Double?
    var fat: Double?
    var cost: Double?
    var eaten: Bool

    var id: String { "\(date)-\(meal)-\(row)-\(food)" }

    enum CodingKeys: String, CodingKey { case row, date, meal, food, amount, unit, calories, protein, carbs, fat, cost, eaten }

    init(row: Int, date: String, meal: String, item: FoodItem, eaten: Bool) {
        self.row = row; self.date = date; self.meal = meal; self.food = item.food; self.amount = item.amount
        self.unit = item.unit; self.calories = item.calories; self.protein = item.protein; self.carbs = item.carbs
        self.fat = item.fat; self.cost = item.cost; self.eaten = eaten
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        row = Int(c.flexDouble(.row) ?? 0)
        date = c.flexString(.date) ?? ""
        meal = c.flexString(.meal) ?? "Snack"
        food = c.flexString(.food) ?? ""
        amount = c.flexDouble(.amount); unit = c.flexString(.unit)
        calories = c.flexDouble(.calories); protein = c.flexDouble(.protein)
        carbs = c.flexDouble(.carbs); fat = c.flexDouble(.fat); cost = c.flexDouble(.cost)
        eaten = (try? c.decodeIfPresent(Bool.self, forKey: .eaten)) ?? false
    }

    var asItem: FoodItem {
        FoodItem(food: food, amount: amount ?? 1, unit: unit ?? "serving", calories: calories ?? 0, protein: protein ?? 0,
                 carbs: carbs ?? 0, fat: fat ?? 0, cost: cost)
    }
}

/// One of your own foods on the My Foods tab (nutrition per `amount`).
struct MyFood: Codable, Hashable, Identifiable {
    var row: Int?
    var name: String
    var amount: Double?
    var unit: String?
    var calories: Double?
    var protein: Double?
    var carbs: Double?
    var fat: Double?
    var cost: Double?
    var barcode: String?

    var id: String { row.map(String.init) ?? name }

    enum CodingKeys: String, CodingKey { case row, name, amount, unit, calories, protein, carbs, fat, cost, barcode }

    init(row: Int? = nil, name: String, amount: Double?, unit: String?, calories: Double?, protein: Double?,
         carbs: Double?, fat: Double?, cost: Double?, barcode: String?) {
        self.row = row; self.name = name; self.amount = amount; self.unit = unit; self.calories = calories
        self.protein = protein; self.carbs = carbs; self.fat = fat; self.cost = cost; self.barcode = barcode
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        row = c.flexDouble(.row).map { Int($0) }
        name = c.flexString(.name) ?? ""
        amount = c.flexDouble(.amount); unit = c.flexString(.unit)
        calories = c.flexDouble(.calories); protein = c.flexDouble(.protein)
        carbs = c.flexDouble(.carbs); fat = c.flexDouble(.fat); cost = c.flexDouble(.cost)
        barcode = c.flexString(.barcode)
    }
}

// MARK: - In the app

enum MealType: String, CaseIterable, Identifiable {
    case breakfast = "Breakfast", lunch = "Lunch", dinner = "Dinner", snack = "Snack"
    var id: String { rawValue }

    var symbol: String {
        switch self {
        case .breakfast: "sunrise.fill"
        case .lunch: "sun.max.fill"
        case .dinner: "moon.stars.fill"
        case .snack: "carrot.fill"
        }
    }

    /// The meal people usually mean at this time of day.
    static func forNow(_ date: Date = .now) -> MealType {
        let hour = Calendar.current.component(.hour, from: date)
        switch hour {
        case 4..<11: return .breakfast
        case 11..<16: return .lunch
        case 18..<23: return .dinner
        default: return .snack
        }
    }
}

/// A food with an amount, as edited in a meal or plan. Nutrition is for the
/// whole amount; `per` keeps the per-unit values so changing the amount rescales.
struct FoodItem: Identifiable, Hashable {
    var id = UUID()
    var food: String
    var amount: Double
    var unit: String
    var calories: Double
    var protein: Double
    var carbs: Double
    var fat: Double
    var cost: Double?
    private var per: Nutrition
    /// The food's own serving unit ("cup") and its weight in g / ml, when known —
    /// what lets you switch between "1 cup" and "250 ml".
    private(set) var servingUnit: String
    private(set) var servingGrams: Double?

    /// `grams` is the weight of the whole `amount` (e.g. 240 for "1 cup" of milk).
    init(food: String, amount: Double, unit: String, calories: Double, protein: Double, carbs: Double, fat: Double,
         cost: Double? = nil, grams: Double? = nil) {
        self.food = food; self.amount = amount; self.unit = unit
        self.calories = calories; self.protein = protein; self.carbs = carbs; self.fat = fat; self.cost = cost
        let a = max(amount, 0.0001)
        per = Nutrition(calories: calories / a, protein: protein / a, carbs: carbs / a, fat: fat / a, cost: cost.map { $0 / a })
        servingUnit = unit
        servingGrams = Self.isWeight(unit) ? 1 : grams.map { $0 / a }
    }

    /// g and ml count as the same for food (1 ml of milk ≈ 1 g).
    static func isWeight(_ unit: String) -> Bool { ["g", "ml"].contains(unit.lowercased()) }

    /// Units this food can be shown in: its serving, and g / ml when its weight is known.
    var unitChoices: [String] {
        var out: [String] = []
        if !Self.isWeight(servingUnit) { out.append(servingUnit) }
        if servingGrams != nil || Self.isWeight(unit) { out += ["g", "ml"] }
        return out.isEmpty ? [unit] : out
    }

    /// Grams in one of `unit`, if known.
    private func grams(per unit: String) -> Double? {
        if Self.isWeight(unit) { return 1 }
        return unit == servingUnit ? servingGrams : nil
    }

    /// Shows the same food in another unit: "1 cup" → "240 ml", nutrition unchanged.
    mutating func setUnit(_ new: String) {
        guard new != unit, let from = grams(per: unit), let to = grams(per: new), to > 0 else { return }
        let factor = to / from
        let converted = amount / factor
        amount = Self.isWeight(new) ? converted.rounded() : (converted * 100).rounded() / 100
        per = Nutrition(calories: per.calories * factor, protein: per.protein * factor, carbs: per.carbs * factor,
                        fat: per.fat * factor, cost: per.cost.map { $0 * factor })
        unit = new
        setAmount(amount)
    }

    /// "250 ml", "2", "1.5 cup": switches unit when it can, then sets the amount.
    mutating func apply(_ quantity: FoodQuantity) {
        if let wanted = quantity.unit {
            let match = unitChoices.first { $0.lowercased() == wanted.lowercased() }
            if let match { setUnit(match) }
            else if Self.isWeight(wanted), let to = unitChoices.first(where: Self.isWeight) { setUnit(to) }
        }
        setAmount(quantity.amount)
    }

    /// Changes the amount and rescales nutrition and cost.
    mutating func setAmount(_ value: Double) {
        amount = max(0, value)
        calories = per.calories * amount
        protein = per.protein * amount
        carbs = per.carbs * amount
        fat = per.fat * amount
        cost = per.cost.map { $0 * amount }
    }

    /// Your own numbers for this amount (the list's values are averages). They
    /// become this food's per-unit values, so a new amount scales from them.
    mutating func setNutrition(calories: Double? = nil, protein: Double? = nil, carbs: Double? = nil, fat: Double? = nil) {
        if let calories { self.calories = max(0, calories) }
        if let protein { self.protein = max(0, protein) }
        if let carbs { self.carbs = max(0, carbs) }
        if let fat { self.fat = max(0, fat) }
        let a = max(amount, 0.0001)
        per = Nutrition(calories: self.calories / a, protein: self.protein / a, carbs: self.carbs / a, fat: self.fat / a, cost: per.cost)
    }

    /// Amount steps that make sense for the unit: 50 g, 0.5 cup, 1 piece.
    var step: Double {
        switch unit.lowercased() {
        case "g", "ml": amount >= 100 ? 50 : 10
        case "cup", "bowl", "plate", "glass", "tbsp", "scoop", "fruit": 0.5
        default: 1
        }
    }

    var entry: [String: Any] {
        var out: [String: Any] = ["food": food, "amount": amount, "unit": unit, "calories": round1(calories),
                                  "protein": round1(protein), "carbs": round1(carbs), "fat": round1(fat)]
        if let cost { out["cost"] = round1(cost) }
        return out
    }

    private func round1(_ v: Double) -> Double { (v * 10).rounded() / 10 }

    struct Nutrition: Hashable {
        var calories, protein, carbs, fat: Double
        var cost: Double?
    }
}

/// Totals for a set of foods.
struct NutritionTotals {
    var calories = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0

    init() {}
    init<S: Sequence>(entries: S) where S.Element == FoodEntry {
        for e in entries {
            calories += e.calories ?? 0; protein += e.protein ?? 0; carbs += e.carbs ?? 0; fat += e.fat ?? 0
        }
    }
    init<S: Sequence>(items: S) where S.Element == FoodItem {
        for i in items { calories += i.calories; protein += i.protein; carbs += i.carbs; fat += i.fat }
    }
    init<S: Sequence>(plan: S) where S.Element == PlanItem {
        for p in plan {
            calories += p.calories ?? 0; protein += p.protein ?? 0; carbs += p.carbs ?? 0; fat += p.fat ?? 0
        }
    }
}

/// A logged meal: its Food Log rows grouped.
struct Meal: Identifiable, Hashable {
    let id: String
    let date: String
    let time: String?
    let type: MealType
    let entries: [FoodEntry]
    var cost: Double? { entries.first?.cost }
    var photo: String? { entries.first?.photo }
    var notes: String? { entries.first?.notes }
    var hasLedgerExpense: Bool { entries.first?.ledgerRow != nil }
    var totals: NutritionTotals { NutritionTotals(entries: entries) }
    var isPending: Bool { entries.contains { $0.row < 0 } }

    static func group(_ log: [FoodEntry]) -> [Meal] {
        Dictionary(grouping: log, by: \.mealId).compactMap { id, entries in
            guard let first = entries.first else { return nil }
            return Meal(id: id, date: first.date, time: first.time, type: MealType(rawValue: first.meal) ?? .snack,
                        entries: entries.sorted { $0.row < $1.row })
        }
        .sorted { ($0.date, $0.time ?? "", $0.id) < ($1.date, $1.time ?? "", $1.id) }
    }
}

extension SheetSnapshot {
    var foodData: FoodData { food ?? FoodData(log: [], plan: [], myFoods: [], photoError: nil) }
    func meals(on date: String) -> [Meal] { Meal.group(foodData.log.filter { $0.date == date }) }
}

// MARK: - Food search

/// Something that can be added to a meal: a built-in food, one of yours, or an Open Food Facts product.
struct FoodRef: Identifiable, Hashable {
    enum Source: Hashable { case common, mine, recent }

    let id: String
    let name: String
    let detail: String?
    let amount: Double
    let unit: String
    let calories: Double
    let protein: Double
    let carbs: Double
    let fat: Double
    let cost: Double?
    let source: Source
    let barcode: String?
    /// Per-100 g values, for products where a serving isn't known.
    let per100g: Bool
    /// Weight of `amount` in g / ml, when known.
    var grams: Double? = nil
    /// An amount typed with the search ("milk 250ml"), applied when it's added.
    var wanted: FoodQuantity? = nil

    var item: FoodItem {
        var item = FoodItem(food: name, amount: amount, unit: unit, calories: calories, protein: protein, carbs: carbs, fat: fat,
                            cost: cost, grams: grams)
        if let wanted { item.apply(wanted) }
        return item
    }
}

/// An amount typed with a food: "250 ml", "2", "1.5 cups", "half a litre" is not understood — numbers only.
struct FoodQuantity: Hashable {
    var amount: Double
    /// Normalised: "ml", "g", "cup", "glass", "piece"… or nil for a plain number.
    var unit: String?

    private static let units: [(words: [String], unit: String, scale: Double)] = [
        (["ml", "millilitre", "millilitres", "milliliter", "milliliters"], "ml", 1),
        (["l", "ltr", "litre", "litres", "liter", "liters"], "ml", 1000),
        (["g", "gm", "gms", "gram", "grams", "gr"], "g", 1),
        (["kg", "kgs", "kilo", "kilos"], "g", 1000),
        (["cup", "cups"], "cup", 1), (["glass", "glasses"], "glass", 1), (["bowl", "bowls"], "bowl", 1),
        (["piece", "pieces", "pc", "pcs"], "piece", 1), (["slice", "slices"], "slice", 1), (["plate", "plates"], "plate", 1),
        (["tbsp", "tablespoon", "tablespoons"], "tbsp", 1), (["tsp", "teaspoon", "teaspoons"], "tsp", 1),
        (["scoop", "scoops"], "scoop", 1), (["serving", "servings"], "serving", 1)
    ]

    /// Splits "milk 250ml" / "250 ml milk" / "2 eggs" into the food words and the quantity.
    static func split(_ text: String) -> (name: String, quantity: FoodQuantity?) {
        let pattern = #"(?i)(?:^|\s)(\d+(?:[.,]\d+)?)\s*([a-z]+)?(?=\s|$)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let numberRange = Range(match.range(at: 1), in: text),
              let number = Double(text[numberRange].replacingOccurrences(of: ",", with: ".")), number > 0 else {
            return (text, nil)
        }
        var quantity = FoodQuantity(amount: number, unit: nil)
        var consumed = match.range(at: 1)
        if let wordRange = Range(match.range(at: 2), in: text) {
            let word = text[wordRange].lowercased()
            if let known = units.first(where: { $0.words.contains(word) }) {
                quantity = FoodQuantity(amount: number * known.scale, unit: known.unit)
                consumed = match.range
            }
        }
        let name = (text as NSString).replacingCharacters(in: consumed, with: " ")
            .split(separator: " ").joined(separator: " ")
        return (name, quantity)
    }
}

/// The ~140 common foods shipped with the app (Foods.json), plus searching.
enum FoodDatabase {
    private struct Row: Decodable {
        let name: String, amount: Double, unit: String, grams: Double?
        let calories: Double, protein: Double, carbs: Double, fat: Double, group: String
    }

    static let common: [FoodRef] = {
        guard let url = Bundle.main.url(forResource: "Foods", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let rows = try? JSONDecoder().decode([Row].self, from: data) else { return [] }
        return rows.map {
            FoodRef(id: "common-\($0.name)", name: $0.name, detail: $0.group, amount: $0.amount, unit: $0.unit,
                    calories: $0.calories, protein: $0.protein, carbs: $0.carbs, fat: $0.fat, cost: nil,
                    source: .common, barcode: nil, per100g: false, grams: $0.grams)
        }
    }()

    static func mine(_ foods: [MyFood]) -> [FoodRef] {
        foods.map {
            FoodRef(id: "mine-\($0.id)", name: $0.name, detail: "My food", amount: $0.amount ?? 1, unit: $0.unit ?? "serving",
                    calories: $0.calories ?? 0, protein: $0.protein ?? 0, carbs: $0.carbs ?? 0, fat: $0.fat ?? 0,
                    cost: $0.cost, source: .mine, barcode: $0.barcode, per100g: false)
        }
    }

    /// Foods eaten recently, most frequent first — one tap to log again.
    static func recent(_ log: [FoodEntry], limit: Int = 12) -> [FoodRef] {
        let counts = Dictionary(grouping: log, by: { $0.food.lowercased() })
        return counts.values
            .sorted { ($0.count, $0.map(\.date).max() ?? "") > ($1.count, $1.map(\.date).max() ?? "") }
            .prefix(limit)
            .compactMap { entries in
                guard let e = entries.max(by: { $0.date < $1.date }) else { return nil }
                // A common food logged in its usual unit still knows its weight.
                let known = common.first { $0.name.lowercased() == e.food.lowercased() && $0.unit == (e.unit ?? "") }
                let grams = known.flatMap { k in k.grams.map { $0 / max(k.amount, 0.0001) * (e.amount ?? 1) } }
                return FoodRef(id: "recent-\(e.food)", name: e.food, detail: "Eaten \(entries.count)×", amount: e.amount ?? 1,
                               unit: e.unit ?? "serving", calories: e.calories ?? 0, protein: e.protein ?? 0,
                               carbs: e.carbs ?? 0, fat: e.fat ?? 0, cost: nil, source: .recent, barcode: nil, per100g: false, grams: grams)
            }
    }

    /// Matches every word of the query, best (starts-with) first.
    static func filter(_ foods: [FoodRef], _ query: String) -> [FoodRef] {
        let words = query.lowercased().split(separator: " ").map(String.init)
        guard !words.isEmpty else { return foods }
        // "eggs" finds "Egg, boiled", "chapatis" finds "Chapati".
        func matches(_ name: String, _ word: String) -> Bool {
            name.contains(word) || (word.hasSuffix("es") && name.contains(String(word.dropLast(2))))
                || (word.hasSuffix("s") && word.count > 3 && name.contains(String(word.dropLast())))
        }
        return foods
            .filter { food in words.allSatisfy { matches(food.name.lowercased(), $0) } }
            .sorted { ($0.name.lowercased().hasPrefix(words[0]) ? 0 : 1, $0.name) < ($1.name.lowercased().hasPrefix(words[0]) ? 0 : 1, $1.name) }
    }
}

// MARK: - Meal photos

/// Meal photos: kept in Google Drive by the sheet, cached on the phone. A photo
/// taken offline is shown from its local copy until the meal syncs.
@MainActor
enum MealPhotos {
    private static var folder: URL {
        let url = URL.cachesDirectory.appending(path: "meal-photos")
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func localURL(mealId: String) -> URL { folder.appending(path: "local-\(mealId).jpg") }
    private static func cacheURL(id: String, thumb: Bool) -> URL { folder.appending(path: "\(id)-\(thumb ? "t" : "f").jpg") }

    /// Shrinks a camera photo for upload: about 1280 px on the long side, JPEG.
    static func prepare(_ image: UIImage) -> Data? {
        let longest = max(image.size.width, image.size.height)
        let scale = min(1, 1280 / max(longest, 1))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        return resized.jpegData(compressionQuality: 0.72)
    }

    static func saveLocal(_ data: Data, mealId: String) {
        try? data.write(to: localURL(mealId: mealId), options: .atomic)
    }

    /// The photo for a meal: its local copy, the cache, or downloaded from the sheet.
    static func image(for meal: Meal, thumb: Bool) async -> UIImage? {
        if let image = UIImage(contentsOfFile: localURL(mealId: meal.id).path) { return image }
        guard let id = meal.photo, !id.isEmpty else { return nil }
        let cached = cacheURL(id: id, thumb: thumb)
        if let image = UIImage(contentsOfFile: cached.path) { return image }
        guard let data = await SheetStore.shared.photo(id: id, thumb: thumb), let image = UIImage(data: data) else { return nil }
        try? data.write(to: cached, options: .atomic)
        return image
    }
}
