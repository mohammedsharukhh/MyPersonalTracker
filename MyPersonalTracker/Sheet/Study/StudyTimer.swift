//
//  StudyTimer.swift
//  MyPersonalTracker
//
//  "Start studying" from the Study tab: the app's focus timer (which also
//  shows on the Lock Screen and Dynamic Island) remembering which subject and
//  concept it's for. Stopping hands back a session to review and save.
//

import Foundation

@MainActor
@Observable
final class StudyTimer {
    static let shared = StudyTimer()

    private(set) var subjectId: String?
    private(set) var topicId: String?
    private let defaults = UserDefaults.standard
    private static let subjectKey = "studyTimer.subject"
    private static let topicKey = "studyTimer.topic"

    private init() {
        subjectId = defaults.string(forKey: Self.subjectKey)
        topicId = defaults.string(forKey: Self.topicKey)
    }

    private var focus: FocusTimer { FocusTimer.shared }

    /// Running for a study subject (not a journal activity).
    var isRunning: Bool { focus.isRunning && subjectId != nil }
    var startedAt: Date? { isRunning ? focus.startedAt : nil }

    func start(subject: StudySubject, topic: StudyTopic?) {
        subjectId = subject.id
        topicId = topic?.id
        persist()
        focus.start(activity: [subject.name, topic?.topic].compactMap { $0 }.joined(separator: " · "))
    }

    /// Stops the clock and returns the session it timed (not saved yet).
    func stop(data: StudyData, at date: Date = .now) -> StudyEntry? {
        defer { clear() }
        guard let subjectId, let startedAt = focus.startedAt else { return nil }
        let minutes = Double(FocusTimer.loggedMinutes(from: startedAt, to: date))
        let subject = data.subject(subjectId)
        let topic = data.topic(topicId)
        return StudyEntry(date: SheetDate.string(startedAt), subjectId: subjectId, subject: subject?.name,
                          topicId: topic?.id, topic: topic?.topic, minutes: minutes,
                          kind: topic?.stage == .revising || topic?.stage == .mastered ? .revise : .learn)
    }

    func cancel() { clear() }

    private func clear() {
        subjectId = nil
        topicId = nil
        persist()
        focus.cancel()
    }

    private func persist() {
        defaults.set(subjectId, forKey: Self.subjectKey)
        defaults.set(topicId, forKey: Self.topicKey)
    }
}
