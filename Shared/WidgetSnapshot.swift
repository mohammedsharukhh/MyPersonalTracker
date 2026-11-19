//
//  WidgetSnapshot.swift
//  Shared by the app and the widget extension.
//

import Foundation

/// A small, precomputed summary the app writes to the shared App Group for widgets to display.
/// Widgets never open the database; they only read this.
nonisolated struct WidgetSnapshot: Codable, Sendable {
    var generatedAt: Date
    var currentStreak: Int
    var showedUpToday: Bool
    var todayMinutes: Int
    /// Titles of unfinished sticky-note to-dos, oldest first.
    var openTodos: [String]
    var doneTodayCount: Int
    /// One heat level (0…4) per day, oldest first, ending on `generatedAt`'s day.
    var recentLevels: [Int]
    /// Today from the Google Sheet, when one is connected. Preferred over the
    /// local journal for the streak and "logged today".
    var sheet: SheetToday?

    struct SheetToday: Codable, Sendable, Equatable {
        var date: String
        var logged: Bool
        var score: Double?
        var streak: Int
        var spentToday: Double
        var dailyCap: Double?
        var monthSpent: Double
        var monthlyLimit: Double?
        var currency: String
        /// This month's salary split, one row per bucket (nil before income is entered).
        var split: [SplitRow]? = nil
        var study: Study? = nil
        var shopping: Shopping? = nil
        var steps: Steps? = nil

        struct Steps: Codable, Sendable, Equatable {
            var today: Int
            var target: Int
            /// Days in a row at or over the target (today counts once it's reached).
            var streak: Int
            /// The last seven days, oldest first, ending today.
            var week: [Int]
        }

        struct SplitRow: Codable, Sendable, Equatable {
            var name: String
            var symbol: String
            var planned: Double
            var used: Double
            /// Emergency fund and Savings / SIP fill up by putting money aside; the rest are spending.
            var saving: Bool
        }

        struct Study: Codable, Sendable, Equatable {
            var minutesToday: Int
            var dailyTarget: Int
            var streak: Int
            var due: Int
            var next: String?
            var nextSubject: String?
        }

        struct Shopping: Codable, Sendable, Equatable {
            var count: Int
            var inCart: Int
            /// The first few things still to buy.
            var items: [String]
            var estimate: Double?
        }

        func isFor(_ date: Date) -> Bool {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM-dd"
            return f.string(from: date) == self.date
        }
    }

    /// The sheet's great-day streak when one is connected, else the local journal's.
    var streak: Int { sheet?.streak ?? currentStreak }

    /// "444.5 QAR" spent this month, from the sheet; nil without one.
    var monthSpentShown: String? {
        sheet.map { "\($0.monthSpent.formatted(.number.precision(.fractionLength(0...2)))) \($0.currency)" }
    }

    static let appGroup = "group.mohammedsharukms.MyPersonalTracker"
    private static let storageKey = "widgetSnapshot"

    static func load() -> WidgetSnapshot? {
        guard let data = UserDefaults(suiteName: appGroup)?.data(forKey: storageKey) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        UserDefaults(suiteName: Self.appGroup)?.set(data, forKey: Self.storageKey)
    }

    /// The snapshot as it should read on `date`, if the app hasn't refreshed it since.
    /// Days that passed without opening the app count as not shown up.
    func asOf(_ date: Date, calendar: Calendar = .current) -> WidgetSnapshot {
        let elapsed = calendar.dateComponents([.day], from: calendar.startOfDay(for: generatedAt), to: calendar.startOfDay(for: date)).day ?? 0
        guard elapsed > 0 else { return self }

        var copy = self
        // The streak survives the first new day only if the snapshot's day was a shown-up day.
        copy.currentStreak = elapsed == 1 && showedUpToday ? currentStreak : 0
        copy.showedUpToday = false
        copy.todayMinutes = 0
        copy.doneTodayCount = 0
        copy.recentLevels = Array((recentLevels + Array(repeating: 0, count: elapsed)).suffix(recentLevels.count))
        copy.generatedAt = date
        return copy
    }

    static let placeholder = WidgetSnapshot(
        generatedAt: .now,
        currentStreak: 12,
        showedUpToday: true,
        todayMinutes: 95,
        openTodos: ["Revise BFS vs DFS", "Build widget UI", "Pay electricity bill"],
        doneTodayCount: 2,
        recentLevels: (0..<84).map { [0, 1, 2, 3, 4, 2, 3][$0 % 7] },
        sheet: SheetToday(date: "", logged: true, score: 0.71, streak: 4, spentToday: 12, dailyCap: 20,
                          monthSpent: 444, monthlyLimit: 620, currency: "QAR",
                          split: [
                            .init(name: "Essentials", symbol: "house.fill", planned: 2700, used: 1980, saving: false),
                            .init(name: "Emergency fund", symbol: "shield.lefthalf.filled", planned: 600, used: 600, saving: true),
                            .init(name: "Savings / SIP", symbol: "chart.line.uptrend.xyaxis", planned: 1200, used: 500, saving: true),
                            .init(name: "Family support", symbol: "heart.fill", planned: 900, used: 900, saving: false),
                            .init(name: "Lifestyle", symbol: "sparkles", planned: 600, used: 520, saving: false)
                          ],
                          study: .init(minutesToday: 35, dailyTarget: 60, streak: 6, due: 3, next: "Binary search trees", nextSubject: "DSA"),
                          shopping: .init(count: 7, inCart: 1, items: ["Milk", "Eggs", "Rice", "Tomatoes"], estimate: 64),
                          steps: .init(today: 6240, target: 8000, streak: 4, week: [9100, 8400, 10200, 8050, 8800, 3900, 6240]))
    )
}
