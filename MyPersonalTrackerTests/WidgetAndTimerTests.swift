//
//  WidgetAndTimerTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import SwiftData
import Testing
@testable import MyPersonalTracker

@Suite("Widget snapshot rollover")
struct WidgetSnapshotTests {
    let generated = day(2026, 9, 25, hour: 21)

    private func snapshot(showedUpToday: Bool) -> WidgetSnapshot {
        WidgetSnapshot(
            generatedAt: generated, currentStreak: 5, showedUpToday: showedUpToday, todayMinutes: 90,
            openTodos: ["A"], doneTodayCount: 2, recentLevels: [1, 2, 3, 4]
        )
    }

    @Test func theToDoWidgetShowsTheSheetsStreakAndSpending() {
        var s = snapshot(showedUpToday: true)
        #expect(s.streak == 5)
        #expect(s.monthSpentShown == nil, "no sheet: no spending to show")
        s.sheet = .init(date: "2026-09-25", logged: true, score: 1, streak: 9, spentToday: 0, dailyCap: nil,
                        monthSpent: 444.5, monthlyLimit: nil, currency: "QAR")
        #expect(s.streak == 9)
        #expect(s.monthSpentShown == "444.5 QAR")
    }

    @Test func sameDayIsUnchanged() {
        let later = snapshot(showedUpToday: true).asOf(day(2026, 9, 25, hour: 23), calendar: calendar)
        #expect(later.currentStreak == 5)
        #expect(later.todayMinutes == 90)
    }

    @Test func nextDayKeepsStreakIfYesterdayWasShownUp() {
        let next = snapshot(showedUpToday: true).asOf(day(2026, 9, 26, hour: 7), calendar: calendar)
        #expect(next.currentStreak == 5)
        #expect(!next.showedUpToday)
        #expect(next.todayMinutes == 0)
        #expect(next.doneTodayCount == 0)
        #expect(next.openTodos == ["A"], "open to-dos carry over")
        #expect(next.recentLevels == [2, 3, 4, 0], "heatmap shifts one day")
    }

    @Test func nextDayBreaksStreakIfYesterdayWasMissed() {
        let next = snapshot(showedUpToday: false).asOf(day(2026, 9, 26, hour: 7), calendar: calendar)
        #expect(next.currentStreak == 0)
    }

    @Test func twoDaysLaterTheStreakIsGone() {
        let later = snapshot(showedUpToday: true).asOf(day(2026, 9, 27, hour: 7), calendar: calendar)
        #expect(later.currentStreak == 0)
        #expect(later.recentLevels == [3, 4, 0, 0])
    }
}

@Suite("Focus timer")
struct FocusTimerTests {
    private func isolatedDefaults() -> UserDefaults {
        let name = "FocusTimerTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    @Test(arguments: zip([20.0, 89.0, 90.0, 25 * 60.0, 2 * 3600.0], [1, 1, 2, 25, 120]))
    func roundsToNearestMinuteWithAMinimumOfOne(seconds: Double, expected: Int) {
        let start = day(2026, 9, 25, hour: 10)
        #expect(FocusTimer.loggedMinutes(from: start, to: start.addingTimeInterval(seconds)) == expected)
    }

    @Test func stoppingLogsTheSessionOnToday() throws {
        let container = try makeContainer()
        let context = container.mainContext
        let timer = FocusTimer(defaults: isolatedDefaults())
        let end = Date.now

        timer.start(activity: "DSA", at: end.addingTimeInterval(-30 * 60))
        #expect(timer.isRunning)

        let session = try #require(timer.stop(context: context, at: end))
        #expect(session.title == "DSA")
        #expect(session.minutes == 30)
        #expect(session.log?.date == calendar.startOfDay(for: end))
        #expect(session.log?.showedUp == true)
        #expect(!timer.isRunning)
    }

    @Test func stateSurvivesRelaunch() {
        let defaults = isolatedDefaults()
        let start = day(2026, 9, 25, hour: 10)
        FocusTimer(defaults: defaults).start(activity: "Reading", at: start)

        let relaunched = FocusTimer(defaults: defaults)
        #expect(relaunched.activity == "Reading")
        #expect(relaunched.startedAt == start)
        relaunched.cancel()
        #expect(FocusTimer(defaults: defaults).isRunning == false)
    }
}

@Suite("Formatting")
struct FormattingTests {
    @Test(arguments: zip([0, 5, 45, 60, 90, 125], ["0m", "5m", "45m", "1h", "1h 30m", "2h 5m"]))
    func durationText(minutes: Int, expected: String) {
        #expect(minutes.durationText == expected)
    }
}
