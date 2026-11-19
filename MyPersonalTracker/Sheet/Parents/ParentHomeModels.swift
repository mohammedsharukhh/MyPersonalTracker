//
//  ParentHomeModels.swift
//  MyPersonalTracker
//
//  Parents ▸ Trip (31_Parents.gs ▸ Take Home: what to bring on the next trip
//  home, with a countdown), Parents ▸ Health (Parent Health: the readings they
//  tell you on calls), and what went to them this month from Finance.
//

import Foundation

// MARK: - Things to take home

struct TakeHomeData: Codable {
    /// The next trip home, "yyyy-MM-dd" (nil = none planned).
    var trip: String?
    var items: [TakeHomeItem] = []

    init(trip: String? = nil, items: [TakeHomeItem] = []) { self.trip = trip; self.items = items }

    enum CodingKeys: String, CodingKey { case trip, items }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        trip = c.flexString(.trip)
        items = (try? c.decodeIfPresent([TakeHomeItem].self, forKey: .items)) ?? []
    }

    /// Days until the trip (nil = none planned, or it's passed).
    func daysToTrip(from: Date = .now) -> Int? {
        guard let trip, let date = SheetDate.date(trip) else { return nil }
        let days = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: from), to: date).day ?? 0
        return days >= 0 ? days : nil
    }

    /// Still to get first, then what's got.
    var sorted: [TakeHomeItem] { items.sorted { ($0.got ? 1 : 0, $0.added ?? "") < ($1.got ? 1 : 0, $1.added ?? "") } }
    var toGet: Int { items.filter { !$0.got }.count }

    /// "Trip home in 12 days", "Trip home tomorrow", "Trip home today".
    func countdown(from: Date = .now) -> String? {
        guard let days = daysToTrip(from: from) else { return nil }
        switch days {
        case 0: return "Trip home today"
        case 1: return "Trip home tomorrow"
        default: return "Trip home in \(days) days"
        }
    }
}

struct TakeHomeItem: Codable, Hashable, Identifiable {
    var id: String
    var item: String
    var who: ParentAsk.Who
    var got: Bool
    var notes: String?
    var added: String?
    var pending = false

    init(id: String = UUID().uuidString, item: String = "", who: ParentAsk.Who = .mom, got: Bool = false,
         notes: String? = nil, added: String? = SheetDate.today) {
        self.id = id; self.item = item; self.who = who; self.got = got; self.notes = notes; self.added = added
    }

    enum CodingKeys: String, CodingKey { case id, item, who, got, notes, added }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        item = c.flexString(.item) ?? ""
        who = c.flexString(.who).flatMap(ParentAsk.Who.init(rawValue:)) ?? .both
        got = (try? c.decodeIfPresent(Bool.self, forKey: .got)) ?? (c.flexString(.got) == "Y")
        notes = c.flexString(.notes)
        added = c.flexString(.added)
    }
}

// MARK: - Health notes

struct ParentReading: Codable, Hashable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case bloodPressure = "Blood pressure", sugarFasting = "Sugar (fasting)", sugarAfterFood = "Sugar (after food)",
             weight = "Weight", pulse = "Pulse"
        var id: String { rawValue }
        var unit: String {
            switch self {
            case .bloodPressure: "mmHg"
            case .sugarFasting, .sugarAfterFood: "mg/dL"
            case .weight: "kg"
            case .pulse: "bpm"
            }
        }
        var symbol: String {
            switch self {
            case .bloodPressure: "heart.text.square.fill"
            case .sugarFasting, .sugarAfterFood: "drop.fill"
            case .weight: "scalemass.fill"
            case .pulse: "waveform.path.ecg"
            }
        }
        /// Blood pressure is two numbers (systolic / diastolic).
        var hasSecond: Bool { self == .bloodPressure }
        var short: String {
            switch self {
            case .bloodPressure: "BP"
            case .sugarFasting: "Sugar fasting"
            case .sugarAfterFood: "Sugar after food"
            case .weight: "Weight"
            case .pulse: "Pulse"
            }
        }
    }

    var id: String
    var date: String
    var time: String?
    var who: ParentAsk.Who
    var kind: Kind
    var value: Double
    /// Diastolic, for blood pressure.
    var value2: Double?
    var note: String?
    var pending = false

    init(id: String = UUID().uuidString, date: String = SheetDate.today, time: String? = nil, who: ParentAsk.Who = .dad,
         kind: Kind = .bloodPressure, value: Double = 0, value2: Double? = nil, note: String? = nil) {
        self.id = id; self.date = date; self.time = time; self.who = who; self.kind = kind; self.value = value; self.value2 = value2; self.note = note
    }

    enum CodingKeys: String, CodingKey { case id, date, time, who, kind, value, value2, note }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        date = c.flexString(.date) ?? SheetDate.today
        time = c.flexString(.time)
        who = c.flexString(.who).flatMap(ParentAsk.Who.init(rawValue:)) ?? .dad
        kind = c.flexString(.kind).flatMap(Kind.init(rawValue:)) ?? .bloodPressure
        value = c.flexDouble(.value) ?? 0
        value2 = c.flexDouble(.value2)
        note = c.flexString(.note)
    }

    /// "138/88 mmHg", "112 mg/dL".
    var display: String {
        let first = SheetFormat.number(value)
        let pair = kind.hasSecond ? "\(first)/\(SheetFormat.number(value2))" : first
        return "\(pair) \(kind.unit)"
    }
}

extension SheetSnapshot {
    var takeHomeData: TakeHomeData { takeHome ?? TakeHomeData() }

    /// Newest first.
    var readingList: [ParentReading] { (parentHealth ?? []).sorted { ($0.date, $0.time ?? "") > ($1.date, $1.time ?? "") } }

    /// One kind for one person, oldest first (for the chart).
    func readings(_ kind: ParentReading.Kind, who: ParentAsk.Who) -> [ParentReading] {
        readingList.filter { $0.kind == kind && $0.who == who }.reversed()
    }

    /// What went to Mom & Dad in `month` ("yyyy-MM"): money sent home (Remittance) and
    /// what you spent for them from the Parents tab (requests, medicines).
    func parentMoney(month: String = String(SheetDate.today.prefix(7))) -> ParentMoney {
        let inMonth = expenses.filter { ($0.date ?? "").hasPrefix(month) }
        let sent = inMonth.filter { $0.category == "Remittance" }
        return ParentMoney(sent: sent.reduce(0) { $0 + ($1.qar ?? 0) }, sentHome: sent.reduce(0) { $0 + ($1.inr ?? 0) },
                           spent: parentSpend(month: month))
    }
}

struct ParentMoney: Equatable {
    /// Remittance, in the main currency.
    var sent: Double
    /// The same, in the home currency.
    var sentHome: Double
    /// Spent for them from the Parents tab.
    var spent: Double
    var total: Double { sent + spent }
}

/// Their readings as text, for the doctor: the latest first, with the average.
enum ParentHealthShare {
    static func text(_ s: SheetSnapshot, who: ParentAsk.Who, days: Int = 30, today: Date = .now) -> String {
        let since = SheetDate.string(Calendar.current.date(byAdding: .day, value: -days, to: today) ?? today)
        var lines = ["\(who.rawValue)'s readings — last \(days) days"]
        for kind in ParentReading.Kind.allCases {
            let list = s.readings(kind, who: who).filter { $0.date >= since }
            guard !list.isEmpty else { continue }
            lines.append("")
            lines.append("\(kind.rawValue) (\(kind.unit))")
            for r in list.reversed() {
                lines.append("• \(SheetDate.weekdayDay(r.date))\(r.time.map { " " + $0 } ?? ""): \(r.display)\(r.note.map { " — " + $0 } ?? "")")
            }
            let avg = list.map(\.value).reduce(0, +) / Double(list.count)
            let avg2 = kind.hasSecond ? list.compactMap(\.value2).reduce(0, +) / Double(max(list.compactMap(\.value2).count, 1)) : nil
            lines.append("Average: \(SheetFormat.number(avg, digits: 0))\(avg2.map { "/" + SheetFormat.number($0, digits: 0) } ?? "") over \(list.count) reading\(list.count == 1 ? "" : "s")")
        }
        if lines.count == 1 { lines.append("No readings yet.") }
        return lines.joined(separator: "\n")
    }
}
