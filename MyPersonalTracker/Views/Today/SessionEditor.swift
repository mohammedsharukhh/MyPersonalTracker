//
//  SessionEditor.swift
//  MyPersonalTracker
//

import SwiftData
import SwiftUI

/// Adds time spent on something to a day's log (`session == nil`), or edits an existing entry.
struct SessionEditor: View {
    let log: DailyLog
    let session: StudySession?

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \LearningSection.createdAt) private var sections: [LearningSection]
    @Query(sort: \StudySession.createdAt, order: .reverse) private var pastSessions: [StudySession]
    @State private var title: String
    @State private var minutes: Int
    @FocusState private var titleFocused: Bool

    private static let presets = [15, 30, 45, 60, 90, 120, 180]

    init(log: DailyLog, session: StudySession? = nil) {
        self.log = log
        self.session = session
        _title = State(initialValue: session?.title ?? "")
        _minutes = State(initialValue: session?.minutes ?? 30)
    }

    private var trimmedTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var suggestions: [String] {
        ActivitySuggestions.make(pastSessions: pastSessions, sections: sections)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("What did you work on?") {
                    TextField("e.g. DSA, SwiftUI, Reading", text: $title)
                        .focused($titleFocused)
                        .submitLabel(.done)
                    if !suggestions.isEmpty {
                        ChipRow(items: suggestions, selected: { $0.caseInsensitiveCompare(trimmedTitle) == .orderedSame }) {
                            title = $0
                            titleFocused = false
                        }
                    }
                }

                Section("How long?") {
                    HStack {
                        Text(minutes.durationText)
                            .font(.title.bold())
                            .monospacedDigit()
                            .contentTransition(.numericText())
                        Spacer()
                        Stepper("Duration", value: $minutes.animation(), in: 5...(16 * 60), step: 5)
                            .labelsHidden()
                    }
                    ChipRow(items: Self.presets.map(\.durationText), selected: { $0 == minutes.durationText }) { text in
                        if let preset = Self.presets.first(where: { $0.durationText == text }) {
                            withAnimation { minutes = preset }
                        }
                    }
                }

                if let session {
                    Section {
                        Button("Delete Entry", role: .destructive) {
                            context.delete(session)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(session == nil ? "Add Time" : "Edit Time")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", role: .cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", role: .confirm, action: save)
                        .disabled(trimmedTitle.isEmpty)
                }
            }
            .onAppear { if session == nil { titleFocused = true } }
        }
        .presentationDetents([.medium, .large])
    }

    private func save() {
        if let session {
            session.title = trimmedTitle
            session.minutes = minutes
        } else {
            let new = StudySession(title: trimmedTitle, minutes: minutes)
            context.insert(new)
            new.log = log
            log.showedUp = true
        }
        dismiss()
    }
}
