//
//  Formatting.swift
//  MyPersonalTracker
//

import Foundation

enum AppCurrency {
    static let code = Locale.current.currency?.identifier ?? "INR"
    static let symbol = Locale.current.currencySymbol ?? "₹"
}

extension Decimal {
    var currencyText: String { formatted(.currency(code: AppCurrency.code)) }
    var doubleValue: Double { NSDecimalNumber(decimal: self).doubleValue }
}

extension Int {
    /// Minutes as "45m", "2h", "1h 30m".
    var durationText: String {
        let hours = self / 60, minutes = self % 60
        switch (hours, minutes) {
        case (0, _): return "\(minutes)m"
        case (_, 0): return "\(hours)h"
        default: return "\(hours)h \(minutes)m"
        }
    }
}

extension Calendar {
    func startOfMonth(for date: Date) -> Date {
        dateInterval(of: .month, for: date)?.start ?? startOfDay(for: date)
    }

    func startOfWeek(for date: Date) -> Date {
        dateInterval(of: .weekOfYear, for: date)?.start ?? startOfDay(for: date)
    }
}
