//
//  HistoryView.swift
//  MyPersonalTracker
//

import SwiftData
import SwiftUI

/// Every logged day in one place, newest first, grouped by month and searchable.
struct HistoryView: View {
    private struct Day: Identifiable {
        let log: DailyLog
        let todos: [TodoItem]

        var id: PersistentIdentifier { log.persistentModelID }
    }

    private struct Month: Identifiable {
        let start: Date
        let days: [Day]

        var id: Date { start }
        var totalMinutes: Int { days.reduce(0) { $0 + $1.log.totalMinutes } }
        var daysShownUp: Int { days.count { $0.log.showedUp } }
    }

    @Query(sort: \DailyLog.date, order: .reverse) private var logs: [DailyLog]
    @Query private var todos: [TodoItem]
    @State private var searchText = ""

    private var months: [Month] {
        let calendar = Calendar.current
        let todosByDay = todos.groupedByDay(calendar: calendar)
        let query = searchText.trimmingCharacters(in: .whitespaces)

        let days = logs
            .map { Day(log: $0, todos: todosByDay[$0.date] ?? []) }
            .filter { DayRecap.hasContent(log: $0.log, todos: $0.todos) }
            .filter { query.isEmpty || Self.matches($0, query: query) }

        return Dictionary(grouping: days) { calendar.startOfMonth(for: $0.log.date) }
            .map { Month(start: $0.key, days: $0.value.sorted { $0.log.date > $1.log.date }) }
            .sorted { $0.start > $1.start }
    }

    var body: some View {
        let months = months
        NavigationStack {
            List {
                ForEach(months) { month in
                    Section {
                        ForEach(month.days) { day in
                            NavigationLink {
                                PastDayView(log: day.log)
                            } label: {
                                VStack(alignment: .leading, spacing: 10) {
                                    DayTitle(date: day.log.date)
                                    DayRecap(day: day.log.date, log: day.log, todos: day.todos)
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    } header: {
                        HStack {
                            Text(month.start.formatted(.dateTime.month(.wide).year()))
                            Spacer()
                            Text("\(month.daysShownUp) days · \(month.totalMinutes.durationText)")
                                .monospacedDigit()
                        }
                    }
                }
            }
            .overlay {
                if months.isEmpty {
                    if searchText.isEmpty {
                        ContentUnavailableView(
                            "No logs yet",
                            systemImage: "calendar",
                            description: Text("Days you log on the Today tab show up here.")
                        )
                    } else {
                        ContentUnavailableView.search(text: searchText)
                    }
                }
            }
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search activities, notes, to-dos")
            .navigationTitle("History")
        }
    }

    private static func matches(_ day: Day, query: String) -> Bool {
        let log = day.log
        let fields = [log.whatIDid, log.whatILearned] + log.sessions.map(\.title) + day.todos.map(\.title)
        return fields.contains { $0.localizedStandardContains(query) }
    }
}

private struct DayTitle: View {
    let date: Date

    var body: some View {
        let calendar = Calendar.current
        let label: String = if calendar.isDateInToday(date) {
            "Today"
        } else if calendar.isDateInYesterday(date) {
            "Yesterday"
        } else {
            date.formatted(.dateTime.weekday(.wide))
        }
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.headline)
            Text(date.formatted(.dateTime.day().month()))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

/// Full editable log for a past day.
struct PastDayView: View {
    let log: DailyLog

    var body: some View {
        DailyLogForm(log: log)
            .navigationTitle(log.date.formatted(.dateTime.weekday(.wide).day().month()))
            .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    HistoryView()
        .modelContainer(for: [DailyLog.self, StudySession.self, TodoItem.self], inMemory: true)
}
