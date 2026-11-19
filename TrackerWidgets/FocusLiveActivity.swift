//
//  FocusLiveActivity.swift
//  TrackerWidgets
//

import ActivityKit
import SwiftUI
import WidgetKit

/// Lock Screen and Dynamic Island UI for a running focus timer.
struct FocusLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            HStack(spacing: 14) {
                Image(systemName: "timer")
                    .font(.title)
                    .foregroundStyle(.orange)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Focusing on")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(context.attributes.activity)
                        .font(.headline)
                        .lineLimit(1)
                }
                Spacer()
                ElapsedText(start: context.state.startedAt)
                    .font(.title.bold())
                    .multilineTextAlignment(.trailing)
            }
            .padding()
            .activityBackgroundTint(Color.black.opacity(0.75))
            .activitySystemActionForegroundColor(.orange)

        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.attributes.activity, systemImage: "timer")
                        .font(.headline)
                        .foregroundStyle(.orange)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ElapsedText(start: context.state.startedAt)
                        .font(.title2.bold())
                        .multilineTextAlignment(.trailing)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Open the app to stop and log this time.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "timer")
                    .foregroundStyle(.orange)
            } compactTrailing: {
                ElapsedText(start: context.state.startedAt)
                    .frame(maxWidth: 52)
                    .multilineTextAlignment(.trailing)
            } minimal: {
                Image(systemName: "timer")
                    .foregroundStyle(.orange)
            }
        }
    }
}

/// A count-up clock the system keeps ticking without app updates.
private struct ElapsedText: View {
    let start: Date

    var body: some View {
        Text(timerInterval: start...Date.distantFuture, countsDown: false)
            .monospacedDigit()
    }
}

#Preview("Lock Screen", as: .content, using: FocusActivityAttributes(activity: "DSA")) {
    FocusLiveActivity()
} contentStates: {
    FocusActivityAttributes.ContentState(startedAt: .now.addingTimeInterval(-25 * 60))
}
