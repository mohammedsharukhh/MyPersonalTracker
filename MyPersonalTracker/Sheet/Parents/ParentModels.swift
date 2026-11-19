//
//  ParentModels.swift
//  MyPersonalTracker
//
//  Parents (31_Parents.gs): what Mom and Dad asked you to do, the day to do
//  it, and when to be reminded — every day until it's done, if you like.
//  Something that comes back (call Grandma every Sunday, refill Dad's tablets
//  every month) moves on to its next date when it's done.
//

import Foundation

struct ParentAsk: Codable, Hashable, Identifiable {
    /// Nonisolated so Siri can use it as an App Enum.
    nonisolated enum Who: String, Codable, CaseIterable, Identifiable {
        case mom = "Mom", dad = "Dad", both = "Both"
        var id: String { rawValue }
        /// Who's asking, in a sentence: "Mom", "Dad", "Mom & Dad".
        var label: String { self == .both ? "Mom & Dad" : rawValue }
    }

    enum Repeat: String, Codable, CaseIterable, Identifiable {
        case once = "Once", daily = "Every day", weekly = "Every week", monthly = "Every month"
        var id: String { rawValue }
    }

    var id: String
    var task: String
    var who: Who
    /// The day to do it; for a repeating one, the next time it's due.
    var date: String
    /// "09:00" — when to be reminded; nil = on the list, no notification.
    var time: String?
    var repeats: Repeat
    /// Remind every day at `time` from `date` on, until it's done.
    var nag: Bool
    /// The day they asked.
    var asked: String?
    /// The day it was done (a repeating one: the last time).
    var done: String?
    var notes: String?
    /// Up to three: Drive ids, or "local:<key>" for one not uploaded yet.
    var photos: [String] = []
    /// What it cost (main currency), e.g. the medicines.
    var cost: Double?
    /// Logs `cost` in Finance (Gifts & Family) each time it's done.
    var finance = false
    /// Shown with a clock until the sheet confirms it.
    var pending = false

    static let defaultTime = "09:00"
    static let maxPhotos = 3

    init(id: String = UUID().uuidString, task: String = "", who: Who = .mom, date: String = SheetDate.today,
         time: String? = defaultTime, repeats: Repeat = .once, nag: Bool = true, asked: String? = SheetDate.today,
         done: String? = nil, notes: String? = nil, photos: [String] = [], cost: Double? = nil, finance: Bool = false) {
        self.id = id; self.task = task; self.who = who; self.date = date; self.time = time; self.repeats = repeats
        self.nag = nag; self.asked = asked; self.done = done; self.notes = notes
        self.photos = photos; self.cost = cost; self.finance = finance
    }

    enum CodingKeys: String, CodingKey { case id, task, who, date, time, repeats, nag, asked, done, notes, photos, cost, finance }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        task = c.flexString(.task) ?? ""
        who = c.flexString(.who).flatMap(Who.init(rawValue:)) ?? .both
        date = c.flexString(.date) ?? SheetDate.today
        time = c.flexString(.time)
        repeats = c.flexString(.repeats).flatMap(Repeat.init(rawValue:)) ?? .once
        nag = (try? c.decodeIfPresent(Bool.self, forKey: .nag)) ?? (c.flexString(.nag) == "Y")
        asked = c.flexString(.asked)
        done = c.flexString(.done)
        notes = c.flexString(.notes)
        photos = (try? c.decodeIfPresent([String].self, forKey: .photos))
            ?? (c.flexString(.photos)?.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }) ?? []
        cost = c.flexDouble(.cost)
        finance = (try? c.decodeIfPresent(Bool.self, forKey: .finance)) ?? (c.flexString(.finance) == "Y")
    }

    /// Still to do: a one-off not done yet, or anything that repeats.
    var isOpen: Bool { repeats != .once || done == nil }

    /// Days until its date (negative once it's late).
    func daysLeft(today: Date = .now) -> Int {
        guard let d = SheetDate.date(date) else { return 0 }
        return Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: today), to: d).day ?? 0
    }

    func isLate(today: Date = .now) -> Bool { isOpen && daysLeft(today: today) < 0 }

    /// Whether `day` is one of its dates: the first, then each repeat after it.
    func falls(on day: Date) -> Bool {
        guard let start = SheetDate.date(date) else { return false }
        let calendar = Calendar.current
        let day = calendar.startOfDay(for: day)
        let offset = calendar.dateComponents([.day], from: start, to: day).day ?? -1
        guard offset >= 0 else { return false }
        switch repeats {
        case .once: return offset == 0
        case .daily: return true
        case .weekly: return offset % 7 == 0
        case .monthly:
            // A 31st falls on the last day of shorter months.
            let last = calendar.range(of: .day, in: .month, for: day)?.count ?? 31
            return calendar.component(.day, from: day) == min(calendar.component(.day, from: start), last)
        }
    }

    /// Whether it sends a reminder on `day`: on each of its dates, and with `nag`
    /// every day after until it's done.
    func reminds(on day: Date) -> Bool {
        guard isOpen, time != nil, let start = SheetDate.date(date), Calendar.current.startOfDay(for: day) >= start else { return false }
        return nag || falls(on: day)
    }

    /// A repeating one's next date after `today` (done early skips the coming one too).
    func nextDate(after today: Date = .now) -> String? {
        guard repeats != .once, let start = SheetDate.date(date) else { return nil }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: today)
        let (unit, step): (Calendar.Component, Int) = switch repeats {
        case .daily: (.day, 1)
        case .weekly: (.day, 7)
        default: (.month, 1)
        }
        // Stepped from its date until past today (done after missing a few skips those too).
        for n in 1...2000 {
            guard let next = calendar.date(byAdding: unit, value: step * n, to: start) else { return nil }
            if next > today { return SheetDate.string(next) }
        }
        return nil
    }

    /// Done on `today`: a one-off is finished, a repeating one moves on to its next date.
    func markedDone(on today: Date = .now) -> ParentAsk {
        var copy = self
        copy.done = SheetDate.string(today)
        if let next = nextDate(after: today) { copy.date = next }
        return copy
    }
}

extension SheetSnapshot {
    var parentAsks: [ParentAsk] { parents ?? [] }
    /// Still to do, soonest first (late ones at the top).
    var openParentAsks: [ParentAsk] {
        parentAsks.filter(\.isOpen).sorted { ($0.date, $0.time ?? "99") < ($1.date, $1.time ?? "99") }
    }
    /// One-offs that are done, latest first.
    var doneParentAsks: [ParentAsk] { parentAsks.filter { !$0.isOpen }.sorted { ($0.done ?? "") > ($1.done ?? "") } }
    /// Open ones due on or before `day`.
    func parentAsksDue(by day: String = SheetDate.today) -> [ParentAsk] { openParentAsks.filter { $0.date <= day } }
}

enum ParentText {
    /// "Today", "Tomorrow", "Thursday", "3 days late".
    static func when(_ a: ParentAsk, today: Date = .now) -> String {
        let days = a.daysLeft(today: today)
        switch days {
        case ..<(-1): return "\(-days) days late"
        case -1: return "Since yesterday"
        case 0: return "Today"
        case 1: return "Tomorrow"
        case 2...6: return SheetDate.date(a.date)?.formatted(.dateTime.weekday(.wide)) ?? a.date
        default: return SheetDate.short(a.date)
        }
    }

    /// "Mom · Today · 9:00 AM · Every week" (or "Done 12 Oct 2026").
    static func detail(_ a: ParentAsk, today: Date = .now) -> String {
        guard a.isOpen else { return [a.who.label, "Done \(SheetDate.short(a.done))"].joined(separator: " · ") }
        return [a.who.label, when(a, today: today), PlanTime.display(a.time), a.repeats == .once ? nil : a.repeats.rawValue]
            .compactMap { $0 }.joined(separator: " · ")
    }
}

/// When the notifications go out and what they say. Reminders.swift schedules them.
enum ParentReminders {
    /// What a request's cost is logged with in Finance.
    static let financeNote = "Mom & Dad"
    /// The notification button that marks a request done.
    static let categoryId = "parentAsk"
    static let doneAction = "parentAsk.done"

    static let tomorrowAction = "parentAsk.tomorrow"

    /// Each reminder time in the next `days` days with what's due then, soonest first.
    /// Requests sharing a time go out as one notification. `snoozed` maps a request
    /// to the first day it reminds again ("Tomorrow" on a notification).
    static func slots(_ asks: [ParentAsk], from now: Date, days: Int = 7,
                      snoozed: [String: String] = [:]) -> [(fire: Date, asks: [ParentAsk])] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        var byTime: [Date: [ParentAsk]] = [:]
        for offset in 0..<days {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            let key = SheetDate.string(day)
            for a in asks where a.reminds(on: day) && key >= (snoozed[a.id] ?? "") {
                guard let fire = PlanTime.date(a.time, on: day), fire > now else { continue }
                byTime[fire, default: []].append(a)
            }
        }
        return byTime.keys.sorted().map { ($0, byTime[$0] ?? []) }
    }

    /// What a notification for `asks` on `day` says.
    static func message(_ asks: [ParentAsk], on day: Date) -> (title: String, body: String) {
        if asks.count == 1, let a = asks.first {
            let when = a.daysLeft(today: day) < 0 ? "Still to do — it was for \(SheetDate.weekdayDay(a.date))." : "It's for today."
            let body = [a.notes, when + " Tap Done once it's sorted."].compactMap { $0 }.joined(separator: "\n")
            return ("\(a.who.label) asked: \(a.task)", body)
        }
        let people = Set(asks.map(\.who))
        let who = people == [.mom] ? "Mom" : people == [.dad] ? "Dad" : "Mom & Dad"
        let lines = asks.map { people.count > 1 ? "• \($0.who.label): \($0.task)" : "• \($0.task)" }
        return ("\(asks.count) things \(who) asked for", lines.joined(separator: "\n"))
    }
}

/// "Tomorrow" on a reminder: that request stays quiet until tomorrow. Kept on
/// this phone only, so a weekly request keeps its day.
enum ParentSnooze {
    private static let key = "parentSnoozes"

    /// Request id → the first day ("yyyy-MM-dd") it reminds again; past ones are dropped.
    static var all: [String: String] {
        let today = SheetDate.today
        return (UserDefaults.standard.dictionary(forKey: key) as? [String: String] ?? [:]).filter { $0.value > today }
    }

    static func untilTomorrow(_ id: String, now: Date = .now) {
        var list = all
        list[id] = SheetDate.string(Calendar.current.date(byAdding: .day, value: 1, to: now) ?? now)
        UserDefaults.standard.set(list, forKey: key)
    }
}

/// Mom's and Dad's numbers, to call them or tell them it's done. Kept on this phone only.
enum ParentContacts {
    static let momKey = "parentMomPhone", dadKey = "parentDadPhone"
    static let momUpiKey = "parentMomUpi", dadUpiKey = "parentDadUpi"

    /// Their UPI ID (name@bank), to send them money with Pay.
    static func upi(_ who: ParentAsk.Who) -> String? {
        let id = (UserDefaults.standard.string(forKey: who == .dad ? dadUpiKey : momUpiKey) ?? "").trimmingCharacters(in: .whitespaces)
        return UpiPayee.isValidVPA(id) ? id : nil
    }

    static func phone(_ who: ParentAsk.Who) -> String? {
        let raw = UserDefaults.standard.string(forKey: who == .dad ? dadKey : momKey) ?? ""
        return digits(raw).isEmpty ? nil : raw
    }

    /// Who to contact about a request: both for "Mom & Dad".
    static func people(for who: ParentAsk.Who) -> [ParentAsk.Who] { who == .both ? [.mom, .dad] : [who] }

    /// "+91 98765-43210" → "919876543210" (what WhatsApp's link wants).
    static func digits(_ phone: String) -> String { phone.filter(\.isNumber) }

    /// What to send: "Hi Mom, done ✅ Book the eye check-up", or a question about one still to do.
    static func message(_ a: ParentAsk, to who: ParentAsk.Who, today: String = SheetDate.today) -> String {
        !a.isOpen || a.done == today
            ? "Hi \(who.rawValue), done ✅ \(a.task)"
            : "Hi \(who.rawValue), about \"\(a.task)\" — "
    }

    static func callURL(_ phone: String) -> URL? { URL(string: "tel:" + (phone.hasPrefix("+") ? "+" : "") + digits(phone)) }
    static func whatsAppURL(_ phone: String, text: String) -> URL? {
        var c = URLComponents(string: "https://wa.me/" + digits(phone))
        c?.queryItems = [URLQueryItem(name: "text", value: text)]
        return c?.url
    }
    static func smsURL(_ phone: String, text: String) -> URL? {
        let body = text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "sms:" + (phone.hasPrefix("+") ? "+" : "") + digits(phone) + "&body=" + body)
    }
}

extension SheetSnapshot {
    /// Spent for Mom & Dad this month: costs logged from requests (notes "Mom & Dad").
    func parentSpend(month: String = String(SheetDate.today.prefix(7))) -> Double {
        expenses.filter { ($0.date ?? "").hasPrefix(month) && $0.notes == ParentReminders.financeNote }.reduce(0) { $0 + ($1.qar ?? 0) }
    }
}

/// A request's photos on this phone: ones not uploaded yet (kept until the sheet
/// has them, so a later save can still send them) and downloaded copies.
enum ParentPhotos {
    private static func folder(_ name: String, in base: URL) -> URL {
        let url = base.appending(path: name)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func localURL(key: String) -> URL { folder("parent-photos", in: .applicationSupportDirectory).appending(path: "\(key).jpg") }
    static func cacheURL(id: String, thumb: Bool) -> URL { folder("parent-photos", in: .cachesDirectory).appending(path: "\(id)-\(thumb ? "t" : "f").jpg") }

    /// Keeps a new photo; its reference is "local:<key>" until it's on Drive.
    static func add(_ jpeg: Data) -> String {
        let key = UUID().uuidString
        try? jpeg.write(to: localURL(key: key), options: .atomic)
        return "local:" + key
    }

    static func localKey(_ ref: String) -> String? { ref.hasPrefix("local:") ? String(ref.dropFirst(6)) : nil }
}
