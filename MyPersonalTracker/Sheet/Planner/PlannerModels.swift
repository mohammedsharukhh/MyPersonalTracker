//
//  PlannerModels.swift
//  MyPersonalTracker
//
//  The week planner (23_Planner.gs): plans for each day, what to wear each
//  day, and the wardrobe the outfits are picked from.
//

import Foundation

struct PlannerData: Codable {
    var plans: [DayPlan] = []
    var outfits: [Outfit] = []
    var wardrobe: [Clothing] = []

    init() {}

    enum CodingKeys: String, CodingKey { case plans, outfits, wardrobe }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        plans = (try? c.decodeIfPresent([DayPlan].self, forKey: .plans)) ?? []
        outfits = (try? c.decodeIfPresent([Outfit].self, forKey: .outfits)) ?? []
        wardrobe = (try? c.decodeIfPresent([Clothing].self, forKey: .wardrobe)) ?? []
    }
}

extension SheetSnapshot {
    var plannerData: PlannerData { planner ?? PlannerData() }
}

/// Something on a day: "Cinema with Arun", 18:30, at City Centre.
struct DayPlan: Codable, Hashable, Identifiable {
    var id: String
    var date: String
    /// As typed, "18:30"; nil = any time that day.
    var time: String?
    var title: String
    var place: String?
    var notes: String?
    var done: Bool
    var pending = false

    init(id: String = UUID().uuidString, date: String, time: String? = nil, title: String = "", place: String? = nil,
         notes: String? = nil, done: Bool = false) {
        self.id = id; self.date = date; self.time = time; self.title = title; self.place = place; self.notes = notes; self.done = done
    }

    enum CodingKeys: String, CodingKey { case id, date, time, title, place, notes, done }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        date = c.flexString(.date) ?? SheetDate.today
        time = c.flexString(.time)
        title = c.flexString(.title) ?? ""
        place = c.flexString(.place)
        notes = c.flexString(.notes)
        done = (try? c.decodeIfPresent(Bool.self, forKey: .done)) ?? (c.flexString(.done) == "Y")
    }
}

/// What to wear on a day: pieces from the wardrobe and a note ("borrowed blazer").
struct Outfit: Codable, Hashable, Identifiable {
    var id: String
    var date: String
    /// Wardrobe ids.
    var items: [String]
    /// The names as the sheet last wrote them (kept for pieces since removed).
    var names: String?
    var note: String?
    var pending = false

    /// One outfit per day.
    static func id(for date: String) -> String { "outfit-\(date)" }

    init(date: String, items: [String] = [], note: String? = nil) {
        self.id = Self.id(for: date); self.date = date; self.items = items; self.note = note
    }

    enum CodingKeys: String, CodingKey { case id, date, items, names, note }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        date = c.flexString(.date) ?? SheetDate.today
        id = c.flexString(.id) ?? Self.id(for: date)
        items = (try? c.decodeIfPresent([String].self, forKey: .items))
            ?? (c.flexString(.items) ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        names = c.flexString(.names)
        note = c.flexString(.note)
    }
}

/// A piece of clothing you own.
struct Clothing: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var type: ClothingType
    var colour: String?
    var notes: String?
    var added: String?
    var pending = false

    init(id: String = UUID().uuidString, name: String = "", type: ClothingType = .top, colour: String? = nil,
         notes: String? = nil, added: String? = SheetDate.today) {
        self.id = id; self.name = name; self.type = type; self.colour = colour; self.notes = notes; self.added = added
    }

    enum CodingKeys: String, CodingKey { case id, name, type, colour, notes, added }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        name = c.flexString(.name) ?? ""
        type = c.flexString(.type).flatMap(ClothingType.init(rawValue:)) ?? .other
        colour = c.flexString(.colour)
        notes = c.flexString(.notes)
        added = c.flexString(.added)
    }

    /// "Blue · Oxford shirt" style label.
    var label: String { [colour, name].compactMap { $0 }.joined(separator: " ") }
}

enum ClothingType: String, Codable, CaseIterable, Identifiable {
    case top = "Top", bottom = "Bottom", dress = "Dress", ethnic = "Ethnic", outerwear = "Outerwear",
         shoes = "Shoes", accessory = "Accessory", sports = "Sportswear", other = "Other"
    var id: String { rawValue }

    var emoji: String {
        switch self {
        case .top: "👕"
        case .bottom: "👖"
        case .dress: "👗"
        case .ethnic: "🥻"
        case .outerwear: "🧥"
        case .shoes: "👟"
        case .accessory: "⌚️"
        case .sports: "🏃"
        case .other: "🧺"
        }
    }
}

extension PlannerData {
    /// A day's plans: timed ones in order, then "any time".
    func plans(on date: String) -> [DayPlan] {
        plans.filter { $0.date == date }.sorted { a, b in
            switch (a.time, b.time) {
            case let (x?, y?): x < y
            case (_?, nil): true
            case (nil, _?): false
            case (nil, nil): a.title < b.title
            }
        }
    }

    func outfit(on date: String) -> Outfit? { outfits.first { $0.date == date } }

    func clothing(_ id: String) -> Clothing? { wardrobe.first { $0.id == id } }

    /// The pieces of an outfit, by name; pieces since removed fall back to the sheet's text.
    func pieces(of outfit: Outfit) -> [String] {
        let found = outfit.items.compactMap { clothing($0)?.label }
        if found.count == outfit.items.count || outfit.names == nil { return found }
        return outfit.names!.components(separatedBy: " + ")
    }

    /// The last day before `date` this piece was worn.
    func lastWorn(_ id: String, before date: String = SheetDate.today) -> String? {
        outfits.filter { $0.date < date && $0.items.contains(id) }.map(\.date).max()
    }

    /// Times worn up to today (outfits planned for later don't count yet).
    func timesWorn(_ id: String, today: String = SheetDate.today) -> Int {
        outfits.filter { $0.date <= today && $0.items.contains(id) }.count
    }

    /// The next day it's planned to be worn, from today on.
    func nextPlanned(_ id: String, today: String = SheetDate.today) -> String? {
        outfits.filter { $0.date >= today && $0.items.contains(id) }.map(\.date).min()
    }

    /// Pieces in this outfit also worn within `days` before it ("worn 2 days ago").
    func recentRepeats(in outfit: Outfit, days: Int = 3) -> [(Clothing, Int)] {
        guard let day = SheetDate.date(outfit.date) else { return [] }
        return outfit.items.compactMap { id in
            guard let piece = clothing(id), piece.type != .shoes, piece.type != .accessory,
                  let last = lastWorn(id, before: outfit.date), let lastDay = SheetDate.date(last),
                  let gap = Calendar.current.dateComponents([.day], from: lastDay, to: day).day, gap <= days else { return nil }
            return (piece, gap)
        }
    }
}

/// "18:30" ↔ Date, for the time picker.
enum PlanTime {
    static func date(_ text: String?, on day: Date = .now) -> Date? {
        guard let text, let colon = text.firstIndex(of: ":"),
              let h = Int(text[..<colon]), let m = Int(text[text.index(after: colon)...].prefix(2)) else { return nil }
        return Calendar.current.date(bySettingHour: h, minute: m, second: 0, of: day)
    }

    static func text(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }

    /// "6:30 PM" in the phone's own style.
    static func display(_ text: String?) -> String? {
        guard let d = date(text) else { return text }
        return d.formatted(date: .omitted, time: .shortened)
    }
}
