//
//  FocusActivityAttributes.swift
//  Shared by the app and the widget extension.
//

import ActivityKit
import Foundation

/// Live Activity for a running focus timer (Lock Screen + Dynamic Island).
nonisolated struct FocusActivityAttributes: ActivityAttributes {
    nonisolated struct ContentState: Codable, Hashable {
        var startedAt: Date
    }

    /// What the user is focusing on, e.g. "DSA".
    var activity: String
}
