//
//  TrackerWidgetsBundle.swift
//  TrackerWidgets
//

import SwiftUI
import WidgetKit

@main
struct TrackerWidgetsBundle: WidgetBundle {
    var body: some Widget {
        // First, so it's the one offered when you add this app's widget.
        PayWidget()
        StreakWidget()
        SpendWidget()
        SplitWidget()
        StudyWidget()
        ShoppingWidget()
        StepsWidget()
        TodoWidget()
        FocusLiveActivity()
    }
}

// MARK: - Timeline

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

/// Shows the latest snapshot now, and rolls it over at midnight so streaks and "today" stay honest
/// even if the app isn't opened.
struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: .now, snapshot: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        let snapshot = context.isPreview ? .placeholder : (WidgetSnapshot.load() ?? .placeholder)
        completion(SnapshotEntry(date: .now, snapshot: snapshot.asOf(.now)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let now = Date.now
        let calendar = Calendar.current
        guard let stored = WidgetSnapshot.load() else {
            completion(Timeline(entries: [SnapshotEntry(date: now, snapshot: .placeholder)], policy: .never))
            return
        }
        var entries = [SnapshotEntry(date: now, snapshot: stored.asOf(now))]
        if let midnight = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) {
            entries.append(SnapshotEntry(date: midnight, snapshot: stored.asOf(midnight)))
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

extension Int {
    /// Minutes as "45m", "2h", "1h 30m".
    var durationText: String {
        let hours = self / 60, minutes = self % 60
        switch (hours, minutes) {
        case (0, _): return "\(minutes)m"
        case (_, 0): return "\(hours)h"
        default: return "\(hours)h \(minutes)m"
        }
    }
}
