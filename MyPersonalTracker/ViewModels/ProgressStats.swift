//
//  ProgressStats.swift
//  MyPersonalTracker
//

import Foundation

/// Derived numbers for the Progress tab. Pure value type: build it from fetched models, read the results.
struct ProgressStats {
    struct WeekTotals: Identifiable {
        let weekStart: Date
        var minutes: Int = 0
        var minutesByActivity: [String: Int] = [:]
        var spent: Decimal = 0
        var daysShownUp: Int = 0

        var id: Date { weekStart }
        var studyHours: Double { Double(minutes) / 60 }

        /// The activity you spent the most time on this week.
        var topActivity: (title: String, minutes: Int)? {
            minutesByActivity.max { $0.value < $1.value }.map { ($0.key, $0.value) }
        }
    }

    /// Hours for one activity in one week, for the stacked bar chart.
    struct ActivityPoint: Identifiable {
        let weekStart: Date
        let activity: String
        let hours: Double

        var id: String { "\(weekStart.timeIntervalSince1970)-\(activity)" }
    }

    static let otherActivity = "Other"

    let currentStreak: Int
    let longestStreak: Int
    /// Start-of-day → heatmap intensity 0…4.
    let heatLevels: [Date: Int]
    /// Oldest first; the last element is the current week.
    let weeks: [WeekTotals]
    /// The most-used activities get their own colour; the rest are grouped as "Other".
    let activityPoints: [ActivityPoint]

    var thisWeek: WeekTotals { weeks.last ?? WeekTotals(weekStart: .now) }
    var lastWeek: WeekTotals? { weeks.count > 1 ? weeks[weeks.count - 2] : nil }

    init(logs: [DailyLog], expenses: [Expense], weekCount: Int = 8, today: Date = .now, calendar: Calendar = .current) {
        let today = calendar.startOfDay(for: today)
        let shownUpDays = Set(logs.filter(\.showedUp).map { calendar.startOfDay(for: $0.date) })

        currentStreak = Self.currentStreak(days: shownUpDays, today: today, calendar: calendar)
        longestStreak = Self.longestStreak(days: shownUpDays, calendar: calendar)

        var levels: [Date: Int] = [:]
        for log in logs where log.showedUp {
            levels[calendar.startOfDay(for: log.date)] = Self.heatLevel(hours: log.studyHours)
        }
        heatLevels = levels

        let thisWeekStart = calendar.startOfWeek(for: today)
        var weeks = (0..<weekCount).reversed().compactMap { offset in
            calendar.date(byAdding: .weekOfYear, value: -offset, to: thisWeekStart).map { WeekTotals(weekStart: $0) }
        }
        let index = Dictionary(uniqueKeysWithValues: weeks.enumerated().map { ($1.weekStart, $0) })
        for log in logs {
            guard let i = index[calendar.startOfWeek(for: log.date)] else { continue }
            if log.showedUp { weeks[i].daysShownUp += 1 }
            for session in log.sessions {
                weeks[i].minutes += session.minutes
                weeks[i].minutesByActivity[session.title, default: 0] += session.minutes
            }
        }
        for expense in expenses {
            guard let i = index[calendar.startOfWeek(for: expense.date)] else { continue }
            weeks[i].spent += expense.amount
        }
        self.weeks = weeks
        activityPoints = Self.activityPoints(weeks: weeks, maxActivities: 4)
    }

    static func activityPoints(weeks: [WeekTotals], maxActivities: Int) -> [ActivityPoint] {
        let totals = weeks.reduce(into: [String: Int]()) { result, week in
            result.merge(week.minutesByActivity, uniquingKeysWith: +)
        }
        let ranked = totals.sorted { ($0.value, $1.key) > ($1.value, $0.key) }.map(\.key)
        let top = Set(ranked.prefix(maxActivities))

        return weeks.flatMap { week in
            week.minutesByActivity
                .reduce(into: [String: Int]()) { result, entry in
                    result[top.contains(entry.key) ? entry.key : otherActivity, default: 0] += entry.value
                }
                .map { ActivityPoint(weekStart: week.weekStart, activity: $0.key, hours: Double($0.value) / 60) }
        }
    }

    /// Consecutive shown-up days ending today. If today isn't logged yet the streak
    /// still counts from yesterday — it only breaks once a whole day is missed.
    static func currentStreak(days: Set<Date>, today: Date, calendar: Calendar) -> Int {
        var day = today
        if !days.contains(day) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: day) else { return 0 }
            day = yesterday
        }
        var count = 0
        while days.contains(day) {
            count += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = calendar.startOfDay(for: previous)
        }
        return count
    }

    static func longestStreak(days: Set<Date>, calendar: Calendar) -> Int {
        var longest = 0
        var run = 0
        var previous: Date?
        for day in days.sorted() {
            if let previous, calendar.dateComponents([.day], from: previous, to: day).day == 1 {
                run += 1
            } else {
                run = 1
            }
            longest = max(longest, run)
            previous = day
        }
        return longest
    }

    /// GitHub-style intensity for a day you showed up: more study hours → darker.
    static func heatLevel(hours: Double) -> Int {
        switch hours {
        case ..<1: 1
        case ..<2: 2
        case ..<4: 3
        default: 4
        }
    }
}
