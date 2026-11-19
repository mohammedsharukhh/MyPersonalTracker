//
//  ShoppingModels.swift
//  MyPersonalTracker
//
//  The grocery list as the sheet keeps it (20_Shopping.gs): items to buy, what
//  went in the cart, and every finished trip with what it cost.
//

import Foundation

struct ShoppingData: Codable {
    var items: [ShoppingItem] = []
    var trips: [ShoppingTrip] = []
}

extension SheetSnapshot {
    var shoppingData: ShoppingData { shopping ?? ShoppingData() }
}

struct ShoppingItem: Codable, Hashable, Identifiable {
    enum Status: String, Codable, CaseIterable { case need = "Need", inCart = "In cart", bought = "Bought" }

    var id: String
    var item: String
    /// As typed: "2 L", "5 kg", "30".
    var quantity: String?
    var category: String
    var status: Status
    var price: Double?
    var added: String?
    var boughtOn: String?
    var tripId: String?
    var fromPlan: Bool
    var notes: String?
    var pending = false

    init(id: String = UUID().uuidString, item: String, quantity: String? = nil, category: String? = nil, status: Status = .need,
         price: Double? = nil, added: String? = SheetDate.today, fromPlan: Bool = false, notes: String? = nil) {
        self.id = id; self.item = item; self.quantity = quantity; self.category = category ?? ShoppingAisle.guess(item)
        self.status = status; self.price = price; self.added = added; self.fromPlan = fromPlan; self.notes = notes
    }

    enum CodingKeys: String, CodingKey { case id, item, quantity, category, status, price, added, boughtOn, tripId, fromPlan, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        item = c.flexString(.item) ?? ""
        quantity = c.flexString(.quantity)
        category = c.flexString(.category) ?? "Other"
        status = c.flexString(.status).flatMap(Status.init(rawValue:)) ?? .need
        price = c.flexDouble(.price)
        added = c.flexString(.added)
        boughtOn = c.flexString(.boughtOn)
        tripId = c.flexString(.tripId)
        fromPlan = (try? c.decodeIfPresent(Bool.self, forKey: .fromPlan)) ?? false
        notes = c.flexString(.notes)
    }

    var label: String { [item, quantity].compactMap { $0 }.joined(separator: " · ") }
}

struct ShoppingTrip: Codable, Hashable, Identifiable {
    var id: String
    var date: String
    var store: String?
    var items: Int
    var total: Double?
    var method: String?
    var notes: String?
    var pending = false

    enum CodingKeys: String, CodingKey { case id, date, store, items, total, method, notes }

    init(id: String, date: String, store: String?, items: Int, total: Double?, method: String?, notes: String?, pending: Bool = false) {
        self.id = id; self.date = date; self.store = store; self.items = items; self.total = total
        self.method = method; self.notes = notes; self.pending = pending
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        date = c.flexString(.date) ?? ""
        store = c.flexString(.store)
        items = c.flexDouble(.items).map { Int($0) } ?? 0
        total = c.flexDouble(.total)
        method = c.flexString(.method)
        notes = c.flexString(.notes)
    }
}

/// Aisles, in the order you walk a shop, and a guess from the item's name.
enum ShoppingAisle {
    static let all = ["Vegetables", "Fruits", "Dairy & Eggs", "Meat & Fish", "Grains & Bread", "Spices & Oils",
                      "Snacks & Drinks", "Frozen", "Household", "Personal care", "Other"]

    static func symbol(_ aisle: String) -> String {
        switch aisle {
        case "Vegetables": "carrot.fill"
        case "Fruits": "apple.logo"
        case "Dairy & Eggs": "oval.portrait.fill"
        case "Meat & Fish": "fish.fill"
        case "Grains & Bread": "takeoutbag.and.cup.and.straw.fill"
        case "Spices & Oils": "drop.fill"
        case "Snacks & Drinks": "cup.and.saucer.fill"
        case "Frozen": "snowflake"
        case "Household": "house.fill"
        case "Personal care": "hands.and.sparkles.fill"
        default: "bag.fill"
        }
    }

    private static let words: [(String, [String])] = [
        ("Vegetables", ["onion", "tomato", "potato", "carrot", "spinach", "palak", "cucumber", "capsicum", "pepper", "garlic", "ginger",
                        "chilli", "chili", "coriander", "mint", "cabbage", "cauliflower", "beans", "peas", "brinjal", "okra", "bhindi",
                        "lettuce", "broccoli", "mushroom", "beetroot", "radish", "lemon", "lime", "pumpkin", "gourd", "veg"]),
        ("Fruits", ["apple", "banana", "orange", "mango", "grape", "papaya", "pineapple", "watermelon", "melon", "pomegranate",
                    "kiwi", "pear", "berry", "berries", "dates", "guava", "fruit", "avocado"]),
        ("Dairy & Eggs", ["milk", "egg", "curd", "yogurt", "yoghurt", "laban", "cheese", "paneer", "butter", "ghee", "cream", "labneh"]),
        ("Meat & Fish", ["chicken", "mutton", "beef", "lamb", "fish", "prawn", "shrimp", "meat", "tuna", "salmon", "keema"]),
        ("Grains & Bread", ["rice", "atta", "flour", "maida", "bread", "roti", "khubz", "oats", "pasta", "noodle", "dal", "lentil",
                            "chana", "rajma", "poha", "rava", "sooji", "semolina", "cereal", "cornflakes", "quinoa", "besan"]),
        ("Spices & Oils", ["oil", "salt", "sugar", "masala", "turmeric", "haldi", "jeera", "cumin", "spice", "ketchup", "sauce",
                           "vinegar", "honey", "jam", "pickle", "mayonnaise", "mustard", "tea", "coffee"]),
        ("Snacks & Drinks", ["biscuit", "chips", "chocolate", "juice", "water", "soda", "cola", "snack", "nuts", "almond", "cashew",
                             "peanut", "namkeen", "cake", "cookie", "drink"]),
        ("Frozen", ["frozen", "ice cream", "nuggets", "fries"]),
        ("Household", ["detergent", "soap", "dishwash", "tissue", "toilet", "cleaner", "bin bag", "garbage", "foil", "cling",
                       "sponge", "bulb", "battery", "batteries", "napkin", "liquid"]),
        ("Personal care", ["shampoo", "toothpaste", "toothbrush", "deodorant", "razor", "lotion", "facewash", "sanitary", "pad",
                           "conditioner", "cream", "body wash"])
    ]

    static func guess(_ name: String) -> String {
        let n = name.lowercased()
        return words.first { $0.1.contains { n.contains($0) } }?.0 ?? "Other"
    }
}

/// "milk 2L", "rice 5 kg", "30 eggs" → the item and the quantity as written.
enum ShoppingParse {
    private static let units: Set<String> = ["kg", "kgs", "g", "gm", "gms", "gram", "grams", "l", "ltr", "litre", "litres", "liter",
                                             "liters", "ml", "pc", "pcs", "piece", "pieces", "pack", "packs", "packet", "packets",
                                             "dozen", "doz", "bottle", "bottles", "can", "cans", "box", "boxes", "bunch", "bunches",
                                             "loaf", "loaves", "tray", "trays", "bag", "bags", "jar", "jars", "tin", "tins"]

    static func split(_ text: String) -> (item: String, quantity: String?) {
        let (item, quantity) = rawSplit(text)
        return (item.prefix(1).uppercased() + item.dropFirst(), quantity)
    }

    private static func rawSplit(_ text: String) -> (item: String, quantity: String?) {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        let pattern = #"(?i)(?:^|\s)(\d+(?:[.,]\d+)?)\s*([a-z]+)?(?=\s|$)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let m = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
              let numberRange = Range(m.range(at: 1), in: trimmed) else { return (trimmed, nil) }
        let number = String(trimmed[numberRange])
        var consumed = m.range(at: 1)
        var quantity = number
        if let unitRange = Range(m.range(at: 2), in: trimmed) {
            let unit = trimmed[unitRange].lowercased()
            if units.contains(unit) {
                quantity = "\(number) \(unit == "l" || unit == "ltr" ? "L" : unit)"
                consumed = m.range
            }
        }
        let item = (trimmed as NSString).replacingCharacters(in: consumed, with: " ")
            .split(separator: " ").joined(separator: " ")
        return item.isEmpty ? (trimmed, nil) : (item, quantity)
    }
}

extension ShoppingData {
    var toBuy: [ShoppingItem] { items.filter { $0.status == .need } }
    var inCart: [ShoppingItem] { items.filter { $0.status == .inCart } }

    func bought(on tripId: String) -> [ShoppingItem] { items.filter { $0.tripId == tripId } }

    /// Things you buy often that aren't on the list now, most bought first.
    var buyAgain: [String] {
        let onList = Set(items.filter { $0.status != .bought }.map { $0.item.lowercased() })
        let counts = Dictionary(grouping: items.filter { $0.status == .bought }, by: { $0.item.lowercased() })
        return counts.filter { !onList.contains($0.key) }
            .sorted { $0.value.count == $1.value.count ? $0.key < $1.key : $0.value.count > $1.value.count }
            .prefix(12).compactMap { $0.value.first?.item }
    }

    /// The last thing typed for an item ("2 L"), to re-add it the same way.
    func lastQuantity(of name: String) -> String? {
        items.filter { $0.item.lowercased() == name.lowercased() }.max { ($0.boughtOn ?? $0.added ?? "") < ($1.boughtOn ?? $1.added ?? "") }?.quantity
    }

    /// The last price paid (or expected) for an item, to fill it in next time.
    func lastPrice(of name: String) -> Double? {
        items.filter { $0.item.lowercased() == name.lowercased() && $0.price != nil }
            .max { ($0.boughtOn ?? $0.added ?? "") < ($1.boughtOn ?? $1.added ?? "") }?.price
    }

    func spent(month: String) -> (trips: Int, total: Double) {
        let list = trips.filter { $0.date.hasPrefix(month) }
        return (list.count, list.reduce(0) { $0 + ($1.total ?? 0) })
    }
}
