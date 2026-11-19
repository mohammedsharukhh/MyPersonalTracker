//
//  TodoWidget.swift
//  TrackerWidgets
//

import SwiftUI
import WidgetKit

struct TodoWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TodoWidget", provider: SnapshotProvider()) { entry in
            TodoWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Sticky Note")
        .description("Today's open to-dos, your streak and (large) your recent days.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct TodoWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    private static let paper = Color(red: 1.0, green: 0.92, blue: 0.52)
    private static let ink = Color(red: 0.22, green: 0.18, blue: 0.06)

    private var maxTodos: Int { family == .systemLarge ? 7 : 3 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("To-do")
                    .font(.custom("Noteworthy-Bold", size: 22))
                if snapshot.doneTodayCount > 0 {
                    Text("\(snapshot.doneTodayCount) done")
                        .font(.custom("Noteworthy-Light", size: 14))
                        .opacity(0.7)
                }
                Spacer()
                Label("\(snapshot.streak)", systemImage: "flame.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.orange)
            }

            if snapshot.openTodos.isEmpty {
                Text(snapshot.doneTodayCount > 0 ? "All done for today 🎉" : "Nothing on the note yet.")
                    .font(.custom("Noteworthy-Light", size: 16))
                    .opacity(0.7)
            } else {
                ForEach(Array(snapshot.openTodos.prefix(maxTodos).enumerated()), id: \.offset) { _, title in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Image(systemName: "square")
                            .font(.footnote)
                        Text(title)
                            .font(.custom("Noteworthy-Light", size: 16))
                            .lineLimit(1)
                    }
                }
                if snapshot.openTodos.count > maxTodos {
                    Text("+ \(snapshot.openTodos.count - maxTodos) more")
                        .font(.caption)
                        .opacity(0.6)
                }
            }

            Spacer(minLength: 0)

            if family == .systemLarge {
                MiniHeatmap(levels: snapshot.recentLevels)
                HStack {
                    Text(snapshot.todayMinutes > 0 ? "Today \(snapshot.todayMinutes.durationText)" : "No time logged today")
                    Spacer()
                    if let spent = snapshot.monthSpentShown { Text("Spent \(spent) this month") }
                }
                .font(.caption.weight(.medium))
                .opacity(0.75)
            }
        }
        .foregroundStyle(Self.ink)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(Self.paper.gradient, for: .widget)
    }
}

/// Last 12 weeks, one column per week, GitHub-style.
private struct MiniHeatmap: View {
    let levels: [Int]

    var body: some View {
        let days = Array(levels.suffix(84))
        let weeks = stride(from: 0, to: days.count, by: 7).map { Array(days[$0..<min($0 + 7, days.count)]) }
        HStack(spacing: 3) {
            ForEach(weeks.indices, id: \.self) { week in
                VStack(spacing: 3) {
                    ForEach(weeks[week].indices, id: \.self) { day in
                        let level = weeks[week][day]
                        RoundedRectangle(cornerRadius: 2)
                            .fill(level == 0 ? Color.black.opacity(0.08) : Color.green.opacity(0.25 * Double(level)))
                            .aspectRatio(1, contentMode: .fit)
                    }
                }
            }
        }
    }
}

#Preview(as: .systemMedium) {
    TodoWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .placeholder)
}

#Preview(as: .systemLarge) {
    TodoWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .placeholder)
}
