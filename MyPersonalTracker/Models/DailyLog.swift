//
//  DailyLog.swift
//  MyPersonalTracker
//

import Foundation
import SwiftData

@Model
final class DailyLog {
    /// Always stored as the start of the day, so there is one log per calendar day.
    @Attribute(.unique) var date: Date
    var whatIDid: String
    var whatILearned: String
    /// 1 (bad) … 5 (great)
    var mood: Int
    var showedUp: Bool
    /// The day's smile check-in photo.
    @Attribute(.externalStorage) var selfieData: Data?
    @Relationship(deleteRule: .cascade, inverse: \StudySession.log)
    var sessions: [StudySession] = []

    var totalMinutes: Int { sessions.reduce(0) { $0 + $1.minutes } }
    var studyHours: Double { Double(totalMinutes) / 60 }
    var sortedSessions: [StudySession] { sessions.sorted { $0.createdAt < $1.createdAt } }

    init(
        date: Date,
        whatIDid: String = "",
        whatILearned: String = "",
        mood: Int = 3,
        showedUp: Bool = false
    ) {
        self.date = Calendar.current.startOfDay(for: date)
        self.whatIDid = whatIDid
        self.whatILearned = whatILearned
        self.mood = mood
        self.showedUp = showedUp
    }
}

extension DailyLog {
    /// The log for the given day, created if it doesn't exist yet.
    static func forDay(_ date: Date, in context: ModelContext) -> DailyLog {
        let day = Calendar.current.startOfDay(for: date)
        let descriptor = FetchDescriptor<DailyLog>(predicate: #Predicate { $0.date == day })
        if let existing = try? context.fetch(descriptor).first { return existing }
        let log = DailyLog(date: day)
        context.insert(log)
        return log
    }
}

enum Mood {
    static let range = 1...5

    static func emoji(_ value: Int) -> String {
        switch value {
        case 1: "😞"
        case 2: "😕"
        case 3: "😐"
        case 4: "🙂"
        default: "😄"
        }
    }
}
