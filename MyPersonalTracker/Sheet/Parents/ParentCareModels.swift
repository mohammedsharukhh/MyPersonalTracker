//
//  ParentCareModels.swift
//  MyPersonalTracker
//
//  The rest of the Parents tab: their medicines (31_Parents.gs ▸ Parent
//  Medicines) counted down to the next refill, their important days (Parent
//  Dates) every year, how long since you last talked, and the one daily
//  "Mom & Dad" notification that brings those together.
//

import Foundation

// MARK: - Medicines

struct ParentMedicine: Codable, Hashable, Identifiable {
    var id: String
    var who: ParentAsk.Who
    var name: String
    /// As they take it, e.g. "1 morning, 1 night".
    var dose: String?
    /// Tablets (or doses) a day.
    var perDay: Double
    /// How many there were on `counted`.
    var left: Double
    var counted: String
    /// Warn this many days before they run out.
    var warn: Int
    /// How many come in a pack (the usual refill).
    var pack: Double?
    /// What a pack costs (main currency).
    var cost: Double?
    /// Logs the cost in Finance at each refill.
    var finance: Bool
    var stopped: Bool
    var notes: String?
    var pending = false

    init(id: String = UUID().uuidString, who: ParentAsk.Who = .dad, name: String = "", dose: String? = nil, perDay: Double = 1,
         left: Double = 30, counted: String = SheetDate.today, warn: Int = 7, pack: Double? = nil, cost: Double? = nil,
         finance: Bool = false, stopped: Bool = false, notes: String? = nil) {
        self.id = id; self.who = who; self.name = name; self.dose = dose; self.perDay = perDay; self.left = left; self.counted = counted
        self.warn = warn; self.pack = pack; self.cost = cost; self.finance = finance; self.stopped = stopped; self.notes = notes
    }

    enum CodingKeys: String, CodingKey { case id, who, name, dose, perDay, left, counted, warn, pack, cost, finance, stopped, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        who = c.flexString(.who).flatMap(ParentAsk.Who.init(rawValue:)) ?? .both
        name = c.flexString(.name) ?? ""
        dose = c.flexString(.dose)
        perDay = max(c.flexDouble(.perDay) ?? 1, 0)
        left = max(c.flexDouble(.left) ?? 0, 0)
        counted = c.flexString(.counted) ?? SheetDate.today
        warn = Int(c.flexDouble(.warn) ?? 7)
        pack = c.flexDouble(.pack)
        cost = c.flexDouble(.cost)
        finance = (try? c.decodeIfPresent(Bool.self, forKey: .finance)) ?? (c.flexString(.finance) == "Y")
        stopped = (try? c.decodeIfPresent(Bool.self, forKey: .stopped)) ?? (c.flexString(.stopped) == "Y")
        notes = c.flexString(.notes)
    }

    /// How many are left on `day`, counting down from the last count.
    func leftOn(_ day: Date) -> Double {
        guard let start = SheetDate.date(counted) else { return left }
        let days = Calendar.current.dateComponents([.day], from: start, to: Calendar.current.startOfDay(for: day)).day ?? 0
        return max(0, left - perDay * Double(max(days, 0)))
    }

    /// Whole days they last from `day` (nil when not counting down: stopped, or none a day).
    func daysLeft(on day: Date = .now) -> Int? {
        guard !stopped, perDay > 0 else { return nil }
        return Int((leftOn(day) / perDay).rounded(.down))
    }

    func runsLow(on day: Date = .now) -> Bool { (daysLeft(on: day) ?? .max) <= warn }

    /// After buying `bought` more today.
    func refilled(with bought: Double, on day: Date = .now) -> ParentMedicine {
        var copy = self
        copy.left = leftOn(day) + bought
        copy.counted = SheetDate.string(day)
        return copy
    }

    /// "18 left · 9 days", "Runs out tomorrow", "Ran out".
    func status(on day: Date = .now) -> String {
        guard let days = daysLeft(on: day) else { return stopped ? "Stopped" : "\(SheetFormat.number(leftOn(day))) left" }
        let count = "\(SheetFormat.number(leftOn(day))) left"
        switch days {
        case ..<1: return leftOn(day) <= 0 ? "Ran out" : "Runs out today"
        case 1: return "\(count) · runs out tomorrow"
        default: return "\(count) · \(days) days"
        }
    }
}

// MARK: - Important dates

struct ParentDate: Codable, Hashable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case birthday = "Birthday", anniversary = "Anniversary", remembrance = "Remembrance", other = "Other"
        var id: String { rawValue }
        var symbol: String {
            switch self {
            case .birthday: "gift.fill"
            case .anniversary: "heart.fill"
            case .remembrance: "flame.fill"
            case .other: "calendar"
            }
        }
        var emoji: String {
            switch self {
            case .birthday: "🎂"
            case .anniversary: "💐"
            case .remembrance: "🪔"
            case .other: "📅"
            }
        }
    }

    var id: String
    var title: String
    var who: ParentAsk.Who
    var kind: Kind
    /// "MM-dd", every year.
    var day: String
    /// The first one (birth year, wedding year), when known.
    var year: Int?
    /// Days before to remind, e.g. [7, 1, 0].
    var remind: [Int]
    var notes: String?
    var pending = false

    init(id: String = UUID().uuidString, title: String = "", who: ParentAsk.Who = .mom, kind: Kind = .birthday,
         day: String = String(SheetDate.today.dropFirst(5)), year: Int? = nil, remind: [Int] = [7, 1, 0], notes: String? = nil) {
        self.id = id; self.title = title; self.who = who; self.kind = kind; self.day = day; self.year = year; self.remind = remind; self.notes = notes
    }

    enum CodingKeys: String, CodingKey { case id, title, who, kind, day, year, remind, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        title = c.flexString(.title) ?? ""
        who = c.flexString(.who).flatMap(ParentAsk.Who.init(rawValue:)) ?? .both
        kind = c.flexString(.kind).flatMap(Kind.init(rawValue:)) ?? .other
        day = c.flexString(.day) ?? "01-01"
        year = c.flexDouble(.year).map { Int($0) }.flatMap { $0 > 0 ? $0 : nil }
        remind = Renewal.days(c.flexString(.remind) ?? "7,1,0")
        notes = c.flexString(.notes)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(title, forKey: .title); try c.encode(who.rawValue, forKey: .who)
        try c.encode(kind.rawValue, forKey: .kind); try c.encode(day, forKey: .day); try c.encodeIfPresent(year, forKey: .year)
        try c.encode(remind.map(String.init).joined(separator: ","), forKey: .remind); try c.encodeIfPresent(notes, forKey: .notes)
    }

    /// Its next day on or after `from` (29 Feb falls on 28 Feb in other years).
    func next(from: Date = .now) -> Date? {
        let calendar = Calendar.current
        let parts = day.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        let today = calendar.startOfDay(for: from)
        let thisYear = calendar.component(.year, from: today)
        for year in [thisYear, thisYear + 1] {
            guard let first = calendar.date(from: DateComponents(year: year, month: parts[0], day: 1)),
                  let length = calendar.range(of: .day, in: .month, for: first)?.count,
                  let date = calendar.date(from: DateComponents(year: year, month: parts[0], day: min(parts[1], length))) else { continue }
            if date >= today { return date }
        }
        return nil
    }

    func daysUntil(from: Date = .now) -> Int? {
        guard let next = next(from: from) else { return nil }
        return Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: from), to: next).day
    }

    /// How many years it'll be on its next day, when the year is known.
    func years(from: Date = .now) -> Int? {
        guard let year, let next = next(from: from) else { return nil }
        let n = Calendar.current.component(.year, from: next) - year
        return n > 0 ? n : nil
    }

    /// "Mom turns 60", "35th anniversary", or the title.
    func headline(from: Date = .now) -> String {
        guard let n = years(from: from) else { return title }
        switch kind {
        case .birthday: return "\(title) — turns \(n)"
        case .anniversary: return "\(title) — \(ordinal(n))"
        case .remembrance: return "\(title) — \(n) year\(n == 1 ? "" : "s")"
        case .other: return title
        }
    }

    /// "Today", "Tomorrow", "In 7 days", "On 24 Oct".
    func when(from: Date = .now) -> String {
        guard let days = daysUntil(from: from) else { return day }
        switch days {
        case 0: return "Today"
        case 1: return "Tomorrow"
        case 2...30: return "In \(days) days"
        default: return next(from: from).map { $0.formatted(.dateTime.day().month(.abbreviated)) } ?? day
        }
    }

    private func ordinal(_ n: Int) -> String {
        let suffix = (11...13).contains(n % 100) ? "th" : (["th", "st", "nd", "rd"] + Array(repeating: "th", count: 6))[n % 10]
        return "\(n)\(suffix)"
    }
}

extension SheetSnapshot {
    var medicineList: [ParentMedicine] {
        (parentMedicines ?? []).sorted { ($0.stopped ? 1 : 0, $0.daysLeft() ?? .max, $0.name) < ($1.stopped ? 1 : 0, $1.daysLeft() ?? .max, $1.name) }
    }
    /// Soonest first.
    var parentDateList: [ParentDate] { (parentDates ?? []).sorted { ($0.daysUntil() ?? 999) < ($1.daysUntil() ?? 999) } }
}

// MARK: - Talking to them

/// When you last talked to each of them (a Call or WhatsApp from the app, or "Talked ✓"),
/// and how often you'd like to. Kept on this phone only.
enum ParentTalk {
    static func lastKey(_ who: ParentAsk.Who) -> String { who == .dad ? "parentDadLastTalk" : "parentMomLastTalk" }
    static func everyKey(_ who: ParentAsk.Who) -> String { who == .dad ? "parentDadCallEvery" : "parentMomCallEvery" }

    static func last(_ who: ParentAsk.Who) -> String? { UserDefaults.standard.string(forKey: lastKey(who)) }
    static func talked(_ who: ParentAsk.Who, on day: Date = .now) { UserDefaults.standard.set(SheetDate.string(day), forKey: lastKey(who)) }
    /// Days between calls you'd like; 0 = no reminder.
    static func every(_ who: ParentAsk.Who) -> Int { UserDefaults.standard.integer(forKey: everyKey(who)) }

    /// Days since you last talked as of `day` (nil = never noted).
    static func daysSince(_ last: String?, on day: Date) -> Int? {
        guard let last, let date = SheetDate.date(last) else { return nil }
        return Calendar.current.dateComponents([.day], from: date, to: Calendar.current.startOfDay(for: day)).day
    }

    /// "Talked today", "Talked yesterday", "Talked 4 days ago".
    static func text(_ last: String?, on day: Date = .now) -> String? {
        guard let days = daysSince(last, on: day) else { return nil }
        switch days {
        case ..<1: return "Talked today"
        case 1: return "Talked yesterday"
        default: return "Talked \(days) days ago"
        }
    }
}

// MARK: - The daily Mom & Dad notification

enum ParentDigest {
    /// Settings ▸ the time it goes out (minutes after midnight).
    static let minutesKey = "parentDigestMinutes"
    static let defaultMinutes = 18 * 60

    /// What's worth saying on `day`: medicines running low, their days coming up, and a call that's due.
    /// `last` / `every` are when you last talked and how often you'd like to, per parent.
    static func lines(_ s: SheetSnapshot, on day: Date, last: [ParentAsk.Who: String], every: [ParentAsk.Who: Int]) -> [String] {
        var lines: [String] = []
        for m in s.medicineList where m.runsLow(on: day) {
            let who = m.who == .both ? "" : "\(m.who.rawValue)'s "
            lines.append("💊 \(who)\(m.name): \(m.status(on: day).lowercased().replacingOccurrences(of: " · ", with: ", "))")
        }
        for d in s.parentDateList {
            guard let days = d.daysUntil(from: day), d.remind.contains(days) else { continue }
            lines.append("\(d.kind.emoji) \(d.headline(from: day)) · \(d.when(from: day).lowercased())")
        }
        let home = s.takeHomeData
        if let days = home.daysToTrip(from: day), [14, 7, 3, 1, 0].contains(days), home.toGet > 0 {
            lines.append("🧳 \(home.countdown(from: day) ?? "Trip home soon") — \(home.toGet) thing\(home.toGet == 1 ? "" : "s") still to get")
        }
        for who in [ParentAsk.Who.mom, .dad] {
            let want = every[who] ?? 0
            guard want > 0 else { continue }
            if let since = ParentTalk.daysSince(last[who], on: day), since < want { continue }
            let since = ParentTalk.daysSince(last[who], on: day)
            lines.append(since.map { "📞 You haven't talked to \(who.rawValue) in \($0) day\($0 == 1 ? "" : "s")" } ?? "📞 Give \(who.rawValue) a call")
        }
        return lines
    }

    static func message(_ lines: [String]) -> (title: String, body: String) {
        if lines.count == 1 { return ("Mom & Dad", lines[0]) }
        return ("Mom & Dad · \(lines.count) things", lines.joined(separator: "\n"))
    }
}

/// What's still to do for them, to send to a sibling (or to them).
enum ParentShare {
    static func text(_ s: SheetSnapshot, who: ParentAsk.Who? = nil, today: Date = .now) -> String {
        let open = s.openParentAsks.filter { who == nil || $0.who == who || $0.who == .both }
        var lines = ["Still to do for \(who?.label ?? "Mom & Dad"):"]
        if open.isEmpty { lines.append("Nothing — all sorted ✅") }
        for a in open {
            let late = a.isLate(today: today) ? " (late)" : ""
            lines.append("• \(a.task) — \(a.who.label), \(ParentText.when(a, today: today).lowercased())\(late)")
        }
        let low = s.medicineList.filter { $0.runsLow(on: today) && (who == nil || $0.who == who || $0.who == .both) }
        if !low.isEmpty {
            lines.append("")
            lines.append("Medicines to refill:")
            lines += low.map { "• \($0.name) (\($0.who.label)) — \($0.status(on: today).lowercased())" }
        }
        return lines.joined(separator: "\n")
    }
}
