//
//  ProgressStatsTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import SwiftData
import Testing
@testable import MyPersonalTracker

@Suite("Streaks")
struct StreakTests {
    let today = day(2026, 9, 25)

    @Test func countsConsecutiveDaysEndingToday() {
        let days: Set = [day(2026, 9, 23), day(2026, 9, 24), today]
        #expect(ProgressStats.currentStreak(days: days, today: today, calendar: calendar) == 3)
    }

    @Test func todayNotLoggedYetDoesNotBreakTheStreak() {
        let days: Set = [day(2026, 9, 23), day(2026, 9, 24)]
        #expect(ProgressStats.currentStreak(days: days, today: today, calendar: calendar) == 2)
    }

    @Test func aMissedDayBreaksTheStreak() {
        let days: Set = [day(2026, 9, 22), day(2026, 9, 24)]
        #expect(ProgressStats.currentStreak(days: days, today: today, calendar: calendar) == 1)
    }

    @Test func noRecentDaysMeansNoStreak() {
        let days: Set = [day(2026, 9, 20)]
        #expect(ProgressStats.currentStreak(days: days, today: today, calendar: calendar) == 0)
    }

    @Test func streakContinuesAcrossMonthAndYearBoundaries() {
        let newYear = day(2027, 1, 1)
        let days: Set = [day(2026, 12, 30), day(2026, 12, 31), newYear]
        #expect(ProgressStats.currentStreak(days: days, today: newYear, calendar: calendar) == 3)
    }

    @Test func longestStreakAcrossMonthBoundary() {
        let days: Set = [day(2026, 8, 30), day(2026, 8, 31), day(2026, 9, 1), day(2026, 9, 2), day(2026, 9, 5)]
        #expect(ProgressStats.longestStreak(days: days, calendar: calendar) == 4)
    }

    @Test func longestStreakOfNothingIsZero() {
        #expect(ProgressStats.longestStreak(days: [], calendar: calendar) == 0)
    }
}

@Suite("Heatmap levels")
struct HeatLevelTests {
    @Test(arguments: zip([0.0, 0.5, 1.0, 1.9, 2.0, 3.9, 4.0, 12.0], [1, 1, 2, 2, 3, 3, 4, 4]))
    func levelForHours(hours: Double, expected: Int) {
        #expect(ProgressStats.heatLevel(hours: hours) == expected)
    }
}

@Suite("Weekly totals")
struct WeeklyTotalsTests {
    @Test func splitsTimeAndSpendingIntoWeeks() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let today = day(2026, 9, 25, hour: 12)
        let lastWeek = calendar.date(byAdding: .day, value: -1, to: calendar.startOfWeek(for: today))!

        addLog(on: today, sessions: [("DSA", 60), ("SwiftUI", 30)], in: context)
        addLog(on: lastWeek, sessions: [("DSA", 45)], in: context)
        let expenses = [
            Expense(amount: 120, category: .food, date: today),
            Expense(amount: 80, category: .transport, date: lastWeek),
        ]
        expenses.forEach(context.insert)

        let logs = try context.fetch(FetchDescriptor<DailyLog>())
        let stats = ProgressStats(logs: logs, expenses: expenses, weekCount: 8, today: today, calendar: calendar)

        #expect(stats.weeks.count == 8)
        #expect(stats.thisWeek.minutes == 90)
        #expect(stats.thisWeek.spent == 120)
        #expect(stats.thisWeek.daysShownUp == 1)
        #expect(stats.thisWeek.topActivity?.title == "DSA")
        #expect(stats.lastWeek?.minutes == 45)
        #expect(stats.lastWeek?.spent == 80)
    }

    @Test func groupsLessUsedActivitiesAsOther() {
        var week = ProgressStats.WeekTotals(weekStart: day(2026, 9, 21))
        week.minutesByActivity = ["A": 100, "B": 90, "C": 80, "D": 70, "E": 20, "F": 10]

        let points = ProgressStats.activityPoints(weeks: [week], maxActivities: 4)
        let byActivity = Dictionary(uniqueKeysWithValues: points.map { ($0.activity, $0.hours) })

        #expect(Set(byActivity.keys) == ["A", "B", "C", "D", ProgressStats.otherActivity])
        #expect(byActivity[ProgressStats.otherActivity] == 0.5)
    }
}
