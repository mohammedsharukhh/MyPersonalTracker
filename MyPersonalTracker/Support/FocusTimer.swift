//
//  FocusTimer.swift
//  MyPersonalTracker
//

import ActivityKit
import Foundation
import SwiftData

/// A single running stopwatch for "time spent on X". Survives the app being closed (state lives in
/// UserDefaults) and mirrors itself to a Live Activity on the Lock Screen and Dynamic Island.
@Observable
final class FocusTimer {
    static let shared = FocusTimer()

    private(set) var activity: String?
    private(set) var startedAt: Date?

    var isRunning: Bool { startedAt != nil }

    private let defaults: UserDefaults
    private static let activityKey = "focusTimer.activity"
    private static let startedAtKey = "focusTimer.startedAt"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        activity = defaults.string(forKey: Self.activityKey)
        startedAt = defaults.object(forKey: Self.startedAtKey) as? Date
    }

    func start(activity: String, at date: Date = .now) {
        self.activity = activity
        startedAt = date
        persist()
        Task { await Self.endLiveActivities(); startLiveActivity() }
    }

    /// Stops the timer and logs the elapsed time (rounded to the nearest minute, at least 1) on today's log.
    @discardableResult
    func stop(context: ModelContext, at date: Date = .now) -> StudySession? {
        defer { reset() }
        guard let activity, let startedAt else { return nil }
        let minutes = Self.loggedMinutes(from: startedAt, to: date)
        let session = StudySession(title: activity, minutes: minutes)
        context.insert(session)
        let log = DailyLog.forDay(date, in: context)
        session.log = log
        log.showedUp = true
        return session
    }

    func cancel() { reset() }

    /// Brings the Live Activity back in line with the stored state, e.g. after a relaunch.
    func syncLiveActivity() {
        Task {
            let running = Activity<FocusActivityAttributes>.activities
            if isRunning {
                if running.isEmpty { startLiveActivity() }
            } else {
                await Self.endLiveActivities()
            }
        }
    }

    static func loggedMinutes(from start: Date, to end: Date) -> Int {
        max(1, Int((end.timeIntervalSince(start) / 60).rounded()))
    }

    private func reset() {
        activity = nil
        startedAt = nil
        persist()
        Task { await Self.endLiveActivities() }
    }

    private func persist() {
        defaults.set(activity, forKey: Self.activityKey)
        defaults.set(startedAt, forKey: Self.startedAtKey)
    }

    private func startLiveActivity() {
        guard let activity, let startedAt, ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        _ = try? Activity.request(
            attributes: FocusActivityAttributes(activity: activity),
            content: ActivityContent(state: .init(startedAt: startedAt), staleDate: nil)
        )
    }

    private static func endLiveActivities() async {
        for liveActivity in Activity<FocusActivityAttributes>.activities {
            await liveActivity.end(nil, dismissalPolicy: .immediate)
        }
    }
}
