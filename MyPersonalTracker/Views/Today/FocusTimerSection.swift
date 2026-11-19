//
//  FocusTimerSection.swift
//  MyPersonalTracker
//

import SwiftData
import SwiftUI

/// Start a stopwatch on an activity; stopping it adds the time to today's log.
struct FocusTimerSection: View {
    @Environment(\.modelContext) private var context
    @State private var timer = FocusTimer.shared
    @State private var isChoosing = false
    @State private var lastLogged: String?

    var body: some View {
        Section {
            if let activity = timer.activity, let startedAt = timer.startedAt {
                HStack(spacing: 14) {
                    Image(systemName: "timer")
                        .font(.title2)
                        .foregroundStyle(.orange)
                        .symbolEffect(.pulse, options: .repeating)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(activity)
                            .font(.headline)
                        Text(startedAt, style: .timer)
                            .font(.title2.bold())
                            .monospacedDigit()
                    }
                    Spacer()
                    Button("Cancel timer", systemImage: "xmark.circle.fill") { withAnimation { timer.cancel() } }
                        .labelStyle(.iconOnly)
                        .foregroundStyle(.secondary)
                        .font(.title2)
                        .buttonStyle(.borderless)
                    Button("Stop") { stop() }
                        .buttonStyle(.borderedProminent)
                        .tint(.green)
                }
            } else {
                Button {
                    isChoosing = true
                } label: {
                    Label("Start focus timer", systemImage: "timer")
                }
            }
        } header: {
            Text("Focus")
        } footer: {
            if let lastLogged {
                Text("Logged \(lastLogged) ✓")
            } else if timer.isRunning {
                Text("Keeps running on your Lock Screen. Stop to add the time to today.")
            }
        }
        .sheet(isPresented: $isChoosing) {
            FocusStartSheet { activity in
                lastLogged = nil
                withAnimation { timer.start(activity: activity) }
            }
        }
    }

    private func stop() {
        guard let session = withAnimation(.default, { timer.stop(context: context) }) else { return }
        lastLogged = "\(session.minutes.durationText) of \(session.title)"
    }
}

private struct FocusStartSheet: View {
    let onStart: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @Query(sort: \LearningSection.createdAt) private var sections: [LearningSection]
    @Query(sort: \StudySession.createdAt, order: .reverse) private var pastSessions: [StudySession]
    @State private var title = ""
    @FocusState private var focused: Bool

    private var trimmed: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            Form {
                Section("What are you focusing on?") {
                    TextField("e.g. DSA, SwiftUI", text: $title)
                        .focused($focused)
                        .submitLabel(.go)
                        .onSubmit(start)
                    let suggestions = ActivitySuggestions.make(pastSessions: pastSessions, sections: sections)
                    if !suggestions.isEmpty {
                        ChipRow(items: suggestions, selected: { $0.caseInsensitiveCompare(trimmed) == .orderedSame }) {
                            title = $0
                            focused = false
                        }
                    }
                }
            }
            .navigationTitle("Start Focus")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", role: .cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Start", action: start)
                        .disabled(trimmed.isEmpty)
                }
            }
            .onAppear { focused = true }
        }
        .presentationDetents([.medium])
    }

    private func start() {
        guard !trimmed.isEmpty else { return }
        onStart(trimmed)
        dismiss()
    }
}
