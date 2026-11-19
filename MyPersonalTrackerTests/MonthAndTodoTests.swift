//
//  MonthAndTodoTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import Testing
@testable import MyPersonalTracker

@Suite("Sticky-note to-dos by day")
struct TodoGroupingTests {
    let monday = day(2026, 9, 21, hour: 9)
    let wednesday = day(2026, 9, 23, hour: 18)

    private func todo(_ title: String, created: Date, completed: Date? = nil) -> TodoItem {
        let item = TodoItem(title: title)
        item.createdAt = created
        if let completed {
            item.isDone = true
            item.completedAt = completed
        }
        return item
    }

    @Test func itemFinishedLaterShowsOnBothDays() {
        let finishedLater = todo("Finished later", created: monday, completed: wednesday)
        let stillOpen = todo("Still open", created: monday.addingTimeInterval(60))
        let doneSameDay = todo("Done same day", created: monday.addingTimeInterval(120), completed: monday.addingTimeInterval(3600))

        let grouped = [doneSameDay, stillOpen, finishedLater].groupedByDay(calendar: calendar)

        #expect(grouped[calendar.startOfDay(for: monday)]?.map(\.title) == ["Finished later", "Still open", "Done same day"],
                "open-on-that-day items first, in the order they were added")
        #expect(grouped[calendar.startOfDay(for: wednesday)]?.map(\.title) == ["Finished later"])
        #expect(finishedLater.wasCompleted(on: wednesday, calendar: calendar))
        #expect(!finishedLater.wasCompleted(on: monday, calendar: calendar))
    }

    @Test func openItemsCarryOverAndDoneItemsDisappearNextDay() {
        let open = todo("Open", created: monday)
        let done = todo("Done", created: monday, completed: monday.addingTimeInterval(60))
        let tuesday = day(2026, 9, 22, hour: 8)

        #expect(open.isVisible(on: tuesday, calendar: calendar))
        #expect(done.isVisible(on: monday, calendar: calendar))
        #expect(!done.isVisible(on: tuesday, calendar: calendar))
    }

    @Test func toggleSetsAndClearsCompletionDate() {
        let item = TodoItem(title: "Toggle me")
        item.toggle()
        #expect(item.isDone && item.completedAt != nil)
        item.toggle()
        #expect(!item.isDone && item.completedAt == nil)
    }
}
