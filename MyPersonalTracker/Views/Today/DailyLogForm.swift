//
//  DailyLogForm.swift
//  MyPersonalTracker
//

import SwiftData
import SwiftUI

/// Edits a single day's log in place. SwiftData autosaves, so there is no Save button.
struct DailyLogForm<Header: View, Footer: View>: View {
    @Bindable var log: DailyLog
    let header: Header
    let footer: Footer

    @Environment(\.modelContext) private var context
    @State private var isAddingSession = false
    @State private var editingSession: StudySession?

    init(log: DailyLog, @ViewBuilder header: () -> Header, @ViewBuilder footer: () -> Footer) {
        self.log = log
        self.header = header()
        self.footer = footer()
    }

    var body: some View {
        Form {
            header

            Section {
                Toggle(isOn: $log.showedUp) {
                    Label("I showed up", systemImage: log.showedUp ? "flame.fill" : "flame")
                        .foregroundStyle(log.showedUp ? .orange : .primary)
                        .font(.headline)
                }
                .sensoryFeedback(.success, trigger: log.showedUp) { _, new in new }
            } footer: {
                Text("At least one focused session of study or practice counts.")
            }

            Section {
                let sessions = log.sortedSessions
                ForEach(sessions) { session in
                    Button {
                        editingSession = session
                    } label: {
                        LabeledContent(session.title, value: session.minutes.durationText)
                            .contentShape(.rect)
                    }
                    .tint(.primary)
                }
                .onDelete { offsets in
                    offsets.map { sessions[$0] }.forEach(context.delete)
                }
                Button("Add time", systemImage: "plus.circle.fill") { isAddingSession = true }
            } header: {
                HStack {
                    Text("Time spent")
                    Spacer()
                    if log.totalMinutes > 0 {
                        Text("Total \(log.totalMinutes.durationText)")
                            .monospacedDigit()
                    }
                }
            }

            Section("Mood") {
                Picker("Mood", selection: $log.mood) {
                    ForEach(Mood.range, id: \.self) { value in
                        Text(Mood.emoji(value)).tag(value)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            Section("What I did") {
                TextField("Solved two tree problems, built a SwiftUI screen…", text: $log.whatIDid, axis: .vertical)
                    .lineLimit(3...8)
            }

            Section("What I learned") {
                TextField("Actors isolate mutable state…", text: $log.whatILearned, axis: .vertical)
                    .lineLimit(3...8)
            }

            footer
        }
        .scrollDismissesKeyboard(.interactively)
        .sheet(isPresented: $isAddingSession) {
            SessionEditor(log: log)
        }
        .sheet(item: $editingSession) { session in
            SessionEditor(log: log, session: session)
        }
    }
}

extension DailyLogForm where Header == EmptyView, Footer == EmptyView {
    init(log: DailyLog) {
        self.init(log: log, header: { EmptyView() }, footer: { EmptyView() })
    }
}
