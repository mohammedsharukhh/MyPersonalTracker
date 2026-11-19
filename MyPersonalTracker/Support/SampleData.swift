//
//  SampleData.swift
//  MyPersonalTracker
//

#if DEBUG
import Foundation
import SwiftData

/// Launch with `-seedSampleData` (Scheme → Run → Arguments) to fill an empty store with a few weeks of demo data.
enum SampleData {
    static func seedIfRequested(_ context: ModelContext) {
        guard ProcessInfo.processInfo.arguments.contains("-seedSampleData"),
              (try? context.fetchCount(FetchDescriptor<StudySession>())) == 0 else { return }

        let calendar = Calendar.current
        let today = calendar.startOfDay(for: .now)
        let activities = ["DSA", "SwiftUI", "Swift Concurrency", "System Design", "Reading"]

        for offset in 1...40 {
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today), offset % 6 != 0 else { continue }
            let log = DailyLog(date: day, mood: 2 + offset % 4, showedUp: true)
            context.insert(log)
            for (i, title) in activities.enumerated() where (offset + i) % 3 == 0 || i == 0 {
                let session = StudySession(title: title, minutes: 15 * (1 + (offset + i) % 6))
                context.insert(session)
                session.log = log
            }
            if offset == 1 {
                log.whatIDid = "Solved 3 binary tree problems and refactored the Money tab."
                log.whatILearned = "Actors serialize access to their state; await marks suspension points."
                let todo = TodoItem(title: "Revise BFS vs DFS")
                context.insert(todo)
                todo.isDone = true
                todo.completedAt = day.addingTimeInterval(18 * 3600)
                todo.createdAt = day.addingTimeInterval(9 * 3600)
                let open = TodoItem(title: "Watch WWDC session on SwiftData")
                context.insert(open)
                open.createdAt = day.addingTimeInterval(10 * 3600)
            }
        }

        let ios = LearningSection(name: "iOS")
        let dsa = LearningSection(name: "DSA")
        context.insert(ios)
        context.insert(dsa)
        for (title, status, section) in [
            ("Swift concurrency", TopicStatus.learning, ios), ("SwiftData", .done, ios), ("Instruments", .notStarted, ios),
            ("Trees", .done, dsa), ("Graphs", .learning, dsa), ("Dynamic programming", .notStarted, dsa),
        ] {
            let topic = LearningTopic(title: title, status: status)
            context.insert(topic)
            topic.section = section
        }

        for offset in 0..<30 {
            guard let date = calendar.date(byAdding: .day, value: -offset, to: .now) else { continue }
            let category = ExpenseCategory.allCases[offset % ExpenseCategory.allCases.count]
            context.insert(Expense(amount: Decimal(80 + (offset * 37) % 400), category: category, date: date))
        }
    }
}
#endif
