//
//  TodoItem.swift
//  MyPersonalTracker
//

import Foundation
import SwiftData

@Model
final class TodoItem {
    var title: String
    var isDone: Bool
    var createdAt: Date
    var completedAt: Date?
    /// Optional attached photo, stored as downscaled JPEG outside the main database file.
    @Attribute(.externalStorage) var photoData: Data?

    init(title: String) {
        self.title = title
        self.isDone = false
        self.createdAt = .now
    }

    func toggle() {
        isDone.toggle()
        completedAt = isDone ? .now : nil
    }

    /// Unfinished to-dos carry over to the next day; finished ones stay visible only on the day they were ticked.
    func isVisible(on day: Date, calendar: Calendar = .current) -> Bool {
        guard isDone, let completedAt else { return true }
        return calendar.isDate(completedAt, inSameDayAs: day)
    }
}
