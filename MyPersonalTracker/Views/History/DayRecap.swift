//
//  DayRecap.swift
//  MyPersonalTracker
//

import SwiftUI

/// Read-only summary of one day: time spent, sticky-note to-dos, notes and mood.
struct DayRecap: View {
    let day: Date
    let log: DailyLog?
    /// The day's sticky-note items: added that day and/or ticked that day.
    let todos: [TodoItem]
    var emptyMessage = "Nothing logged."

    static func hasContent(log: DailyLog?, todos: [TodoItem]) -> Bool {
        guard todos.isEmpty else { return true }
        guard let log else { return false }
        return log.showedUp || log.selfieData != nil || !log.sessions.isEmpty || !log.whatIDid.isEmpty || !log.whatILearned.isEmpty
    }

    var body: some View {
        if !Self.hasContent(log: log, todos: todos) {
            Label(emptyMessage, systemImage: "moon.zzz")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        } else {
            VStack(alignment: .leading, spacing: 14) {
                if let log {
                    HStack(spacing: 8) {
                        if let selfie = log.selfieData {
                            SelfieAvatar(data: selfie, size: 36)
                                .accessibilityLabel("Smile check-in photo")
                        }
                        Image(systemName: log.showedUp ? "flame.fill" : "flame")
                            .foregroundStyle(log.showedUp ? .orange : .secondary)
                        Text(log.showedUp ? "Showed up" : Calendar.current.isDateInToday(day) ? "Not yet" : "Missed")
                            .font(.subheadline.weight(.semibold))
                        if log.totalMinutes > 0 {
                            Text("· \(log.totalMinutes.durationText)")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Mood.emoji(log.mood))
                    }

                    if !log.sessions.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            ForEach(log.sortedSessions) { session in
                                HStack {
                                    Circle().fill(.tint).frame(width: 6, height: 6)
                                    Text(session.title)
                                    Spacer()
                                    Text(session.minutes.durationText)
                                        .monospacedDigit()
                                        .foregroundStyle(.secondary)
                                }
                                .font(.subheadline)
                            }
                        }
                    }
                }

                if !todos.isEmpty {
                    let doneCount = todos.count { $0.wasCompleted(on: day) }
                    RecapBlock(title: "To-do · \(doneCount)/\(todos.count) done", symbol: "note.text") {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(todos) { TodoRecapLine(item: $0, day: day) }
                        }
                    }
                }

                if let log, !log.whatIDid.isEmpty {
                    RecapBlock(title: "What I did", symbol: "hammer.fill") { Text(log.whatIDid) }
                }

                if let log, !log.whatILearned.isEmpty {
                    RecapBlock(title: "What I learned", symbol: "lightbulb.fill") { Text(log.whatILearned) }
                }
            }
            .padding(.vertical, 4)
        }
    }
}

private struct TodoRecapLine: View {
    let item: TodoItem
    let day: Date

    var body: some View {
        let done = item.wasCompleted(on: day)
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Image(systemName: done ? "checkmark.square.fill" : "square")
                .foregroundStyle(done ? .green : .secondary)
            Text(item.title)
                .strikethrough(done, color: .secondary)
                .foregroundStyle(done ? .secondary : .primary)
            if !done, item.isDone {
                Text("done later")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(.tertiarySystemFill), in: .capsule)
            }
            if let data = item.photoData {
                Spacer(minLength: 0)
                PhotoThumbnail(data: data, size: 32)
                    .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 5 }
                    .accessibilityLabel("Has photo")
            }
        }
    }
}

extension TodoItem {
    func wasCompleted(on day: Date, calendar: Calendar = .current) -> Bool {
        guard isDone, let completedAt else { return false }
        return calendar.isDate(completedAt, inSameDayAs: day)
    }
}

extension [TodoItem] {
    /// Sticky-note items grouped by day: each item appears on the day it was added,
    /// and also on the day it was ticked if that was a different day. Open items first.
    func groupedByDay(calendar: Calendar = .current) -> [Date: [TodoItem]] {
        var result: [Date: [TodoItem]] = [:]
        for item in sorted(by: { $0.createdAt < $1.createdAt }) {
            let added = calendar.startOfDay(for: item.createdAt)
            result[added, default: []].append(item)
            if let completedAt = item.completedAt, item.isDone {
                let ticked = calendar.startOfDay(for: completedAt)
                if ticked != added { result[ticked, default: []].append(item) }
            }
        }
        return result.reduce(into: [:]) { grouped, entry in
            let (day, items) = entry
            grouped[day] = items.filter { !$0.wasCompleted(on: day) } + items.filter { $0.wasCompleted(on: day) }
        }
    }
}

private struct RecapBlock<Content: View>: View {
    let title: String
    let symbol: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(title, systemImage: symbol)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            content
                .font(.subheadline)
        }
    }
}
