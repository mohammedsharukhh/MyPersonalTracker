//
//  StudySession.swift
//  MyPersonalTracker
//

import Foundation
import SwiftData

/// Time spent on one thing during a day, e.g. "DSA — 45m" or "SwiftUI animations — 1h 30m".
@Model
final class StudySession {
    var title: String
    var minutes: Int
    var createdAt: Date
    var log: DailyLog?

    init(title: String, minutes: Int) {
        self.title = title
        self.minutes = minutes
        self.createdAt = .now
    }
}
