//
//  LearningTopic.swift
//  MyPersonalTracker
//

import Foundation
import SwiftData

/// A user-defined group of topics, e.g. "iOS", "DSA", "System Design".
@Model
final class LearningSection {
    var name: String
    var createdAt: Date
    @Relationship(deleteRule: .cascade, inverse: \LearningTopic.section)
    var topics: [LearningTopic] = []

    init(name: String) {
        self.name = name
        self.createdAt = .now
    }

    /// Learning first, then not started, then done; oldest first within each.
    var sortedTopics: [LearningTopic] {
        topics.sorted {
            ($0.status.sortOrder, $0.createdAt) < ($1.status.sortOrder, $1.createdAt)
        }
    }

    var doneCount: Int { topics.count { $0.status == .done } }
}

@Model
final class LearningTopic {
    var title: String
    // Stored as a raw string so it stays easy to filter and migrate.
    var statusRaw: String
    var notes: String
    var createdAt: Date
    var section: LearningSection?

    var status: TopicStatus {
        get { TopicStatus(rawValue: statusRaw) ?? .notStarted }
        set { statusRaw = newValue.rawValue }
    }

    init(title: String, status: TopicStatus = .notStarted, notes: String = "") {
        self.title = title
        self.statusRaw = status.rawValue
        self.notes = notes
        self.createdAt = .now
    }
}

enum TopicStatus: String, CaseIterable, Identifiable {
    case notStarted, learning, done

    var id: Self { self }

    var title: String {
        switch self {
        case .notStarted: "Not started"
        case .learning: "Learning"
        case .done: "Done"
        }
    }

    var symbol: String {
        switch self {
        case .notStarted: "circle"
        case .learning: "circle.lefthalf.filled"
        case .done: "checkmark.circle.fill"
        }
    }

    var sortOrder: Int {
        switch self {
        case .learning: 0
        case .notStarted: 1
        case .done: 2
        }
    }

    /// The status a tap on the status icon moves a topic to.
    var next: TopicStatus {
        switch self {
        case .notStarted: .learning
        case .learning: .done
        case .done: .notStarted
        }
    }
}
