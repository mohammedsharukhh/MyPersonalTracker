//
//  RenewalsLoansModels.swift
//  MyPersonalTracker
//
//  Renewals (26_Renewals.gs): documents and contracts that expire, with
//  reminders before. Loans & EMIs (27_Loans.gs): instalments, what's left and
//  when they end.
//

import Foundation

// MARK: - Renewals

struct Renewal: Codable, Hashable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case qid = "QID / residence permit", passport = "Passport", visa = "Visa", license = "Driving licence",
             car = "Car registration", insurance = "Insurance", rent = "Rent contract", subscription = "Subscription", other = "Other"
        var id: String { rawValue }
        var symbol: String {
            switch self {
            case .qid: "person.text.rectangle"
            case .passport: "book.closed"
            case .visa: "airplane"
            case .license: "car.side"
            case .car: "car"
            case .insurance: "cross.case"
            case .rent: "house"
            case .subscription: "repeat"
            case .other: "calendar.badge.exclamationmark"
            }
        }
        /// The expense category a renewal's cost goes under in Finance.
        var category: String {
            switch self {
            case .car, .license: "Transport"
            case .rent: "Rent & Utilities"
            case .subscription: "Entertainment"
            default: "Other"
            }
        }
        /// How long one renewal usually lasts, to suggest the next date.
        var usualYears: Int { self == .passport ? 10 : (self == .license ? 5 : 1) }
    }

    var id: String
    var item: String
    var kind: Kind
    var expires: String
    /// Days before to remind, e.g. [30, 7, 1].
    var remind: [Int]
    var cost: Double?
    var lastRenewed: String?
    var notes: String?
    var pending = false

    init(id: String = UUID().uuidString, item: String = "", kind: Kind = .qid, expires: String = SheetDate.today,
         remind: [Int] = [30, 7, 1], cost: Double? = nil, lastRenewed: String? = nil, notes: String? = nil) {
        self.id = id; self.item = item; self.kind = kind; self.expires = expires; self.remind = remind
        self.cost = cost; self.lastRenewed = lastRenewed; self.notes = notes
    }

    enum CodingKeys: String, CodingKey { case id, item, kind, expires, remind, cost, lastRenewed, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        item = c.flexString(.item) ?? ""
        kind = c.flexString(.kind).flatMap(Kind.init(rawValue:)) ?? .other
        expires = c.flexString(.expires) ?? SheetDate.today
        remind = Self.days(c.flexString(.remind))
        cost = c.flexDouble(.cost)
        lastRenewed = c.flexString(.lastRenewed)
        notes = c.flexString(.notes)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(item, forKey: .item); try c.encode(kind.rawValue, forKey: .kind)
        try c.encode(expires, forKey: .expires); try c.encode(remind.map(String.init).joined(separator: ","), forKey: .remind)
        try c.encodeIfPresent(cost, forKey: .cost); try c.encodeIfPresent(lastRenewed, forKey: .lastRenewed)
        try c.encodeIfPresent(notes, forKey: .notes)
    }

    static func days(_ text: String?) -> [Int] {
        let list = (text ?? "30,7,1").split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }.filter { $0 >= 0 }
        return list.isEmpty ? [30, 7, 1] : Array(Set(list)).sorted(by: >)
    }

    /// Days until it expires (negative once it has).
    func daysLeft(today: Date = .now) -> Int {
        guard let d = SheetDate.date(expires) else { return 0 }
        return Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: today), to: d).day ?? 0
    }

    /// The usual next expiry after renewing.
    var suggestedNext: String {
        guard let d = SheetDate.date(expires), let next = Calendar.current.date(byAdding: .year, value: kind.usualYears, to: d) else { return expires }
        return SheetDate.string(next)
    }
}

extension SheetSnapshot {
    var renewalList: [Renewal] { (renewals ?? []).sorted { $0.expires < $1.expires } }
    /// Expired, or expiring within `days`.
    func renewalsDue(within days: Int = 30) -> [Renewal] { renewalList.filter { $0.daysLeft() <= days } }
}

enum RenewalText {
    static func left(_ days: Int) -> String {
        switch days {
        case ..<(-1): "expired \(-days) days ago"
        case -1: "expired yesterday"
        case 0: "expires today"
        case 1: "expires tomorrow"
        case 2...60: "in \(days) days"
        default: "in \(days / 30) months"
        }
    }
}

// MARK: - Loans

struct Loan: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var lender: String?
    var emi: Double
    var firstPayment: String
    var months: Int
    /// Typed count of instalments paid; nil = worked out from the dates.
    var paid: Int?
    var principal: Double?
    var rate: Double?
    var closed: Bool
    var notes: String?
    /// Has its own monthly bill, posting the instalment to Finance (Essentials) each month.
    var bill: Bool
    var pending = false

    init(id: String = UUID().uuidString, name: String = "", lender: String? = nil, emi: Double = 0, firstPayment: String = SheetDate.today,
         months: Int = 12, paid: Int? = nil, principal: Double? = nil, rate: Double? = nil, closed: Bool = false, notes: String? = nil, bill: Bool = true) {
        self.id = id; self.name = name; self.lender = lender; self.emi = emi; self.firstPayment = firstPayment; self.months = months
        self.paid = paid; self.principal = principal; self.rate = rate; self.closed = closed; self.notes = notes; self.bill = bill
    }

    enum CodingKeys: String, CodingKey { case id, name, lender, emi, firstPayment, months, paid, principal, rate, closed, notes, bill }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        name = c.flexString(.name) ?? ""
        lender = c.flexString(.lender)
        emi = c.flexDouble(.emi) ?? 0
        firstPayment = c.flexString(.firstPayment) ?? SheetDate.today
        months = Int(c.flexDouble(.months) ?? 12)
        paid = c.flexDouble(.paid).map { Int($0) }
        principal = c.flexDouble(.principal)
        rate = c.flexDouble(.rate)
        closed = (try? c.decodeIfPresent(Bool.self, forKey: .closed)) ?? (c.flexString(.closed) == "Y")
        notes = c.flexString(.notes)
        bill = (try? c.decodeIfPresent(Bool.self, forKey: .bill)) ?? false
    }

    /// Instalments paid: as typed, else every payment date on or before today.
    func paidCount(today: Date = .now) -> Int {
        if closed { return months }
        if let paid { return min(max(paid, 0), months) }
        guard let first = SheetDate.date(firstPayment), first <= today else { return 0 }
        let elapsed = Calendar.current.dateComponents([.month], from: first, to: today).month ?? 0
        return min(elapsed + 1, months)
    }

    func left(today: Date = .now) -> Int { months - paidCount(today: today) }
    func remaining(today: Date = .now) -> Double { Double(left(today: today)) * emi }
    var total: Double { Double(months) * emi }
    func fraction(today: Date = .now) -> Double { months > 0 ? Double(paidCount(today: today)) / Double(months) : 0 }

    /// The date of the last instalment.
    var lastPayment: String {
        guard let first = SheetDate.date(firstPayment), let last = Calendar.current.date(byAdding: .month, value: months - 1, to: first) else { return firstPayment }
        return SheetDate.string(last)
    }

    /// The next instalment date from today on (nil once paid off).
    func nextPayment(today: Date = .now) -> String? {
        guard !closed, left(today: today) > 0, let first = SheetDate.date(firstPayment) else { return nil }
        let next = Calendar.current.date(byAdding: .month, value: paidCount(today: today), to: first) ?? first
        return SheetDate.string(next)
    }

    /// Interest paid over the whole loan, when the amount borrowed is known.
    var interest: Double? { principal.map { max(0, total - $0) } }
}

extension SheetSnapshot {
    var activeLoans: [Loan] { (loans ?? []).filter { !$0.closed && $0.left() > 0 } }
    var monthlyEMI: Double { activeLoans.reduce(0) { $0 + $1.emi } }
}
