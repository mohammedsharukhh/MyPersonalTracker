//
//  TestSupport.swift
//  MyPersonalTrackerTests
//

import Foundation
import SwiftData
@testable import MyPersonalTracker

let calendar = Calendar.current

func day(_ year: Int, _ month: Int, _ day: Int, hour: Int = 0) -> Date {
    calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
}

/// A throwaway in-memory store. Keep the returned container alive for the duration of the test.
func makeContainer() throws -> ModelContainer {
    try ModelContainer(
        for: DailyLog.self, StudySession.self, TodoItem.self, LearningSection.self, LearningTopic.self, Expense.self,
        configurations: ModelConfiguration(isStoredInMemoryOnly: true)
    )
}

@discardableResult
func addLog(on date: Date, showedUp: Bool = true, sessions: [(String, Int)] = [], in context: ModelContext) -> DailyLog {
    let log = DailyLog(date: date, showedUp: showedUp)
    context.insert(log)
    for (title, minutes) in sessions {
        let session = StudySession(title: title, minutes: minutes)
        context.insert(session)
        session.log = log
    }
    return log
}
