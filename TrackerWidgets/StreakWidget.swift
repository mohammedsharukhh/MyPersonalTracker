//
//  StreakWidget.swift
//  TrackerWidgets
//

import SwiftUI
import WidgetKit

struct StreakWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StreakWidget", provider: SnapshotProvider()) { entry in
            StreakWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Streak")
        .description("Your great-day streak and whether today is logged.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct StreakWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    /// The Google Sheet's today, when connected and still current.
    private var sheetToday: WidgetSnapshot.SheetToday? {
        guard let sheet = snapshot.sheet, sheet.isFor(snapshot.generatedAt) else { return nil }
        return sheet
    }

    /// With a sheet connected, its great-day streak and "logged" win over the local journal.
    private var streak: Int { snapshot.streak }
    private var loggedToday: Bool { snapshot.sheet != nil ? (sheetToday?.logged ?? false) : snapshot.showedUpToday }

    private var todayText: String {
        if snapshot.sheet != nil {
            guard let sheetToday, sheetToday.logged else { return "Not logged yet" }
            return "Logged · \(Int(((sheetToday.score ?? 0) * 100).rounded()))%"
        }
        return snapshot.todayMinutes > 0 ? "Today \(snapshot.todayMinutes.durationText)" : (snapshot.showedUpToday ? "Showed up today" : "Not logged yet")
    }

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 0) {
                    Image(systemName: "flame.fill")
                        .font(.caption)
                    Text("\(streak)")
                        .font(.title3.bold())
                        .minimumScaleFactor(0.6)
                }
            }
            .widgetAccentable()
            .containerBackground(for: .widget) {}

        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Label("\(streak)-day streak", systemImage: "flame.fill")
                    .font(.headline)
                    .widgetAccentable()
                Text(todayText)
                Text(snapshot.openTodos.isEmpty ? "No open to-dos" : "\(snapshot.openTodos.count) to-dos open")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .containerBackground(for: .widget) {}

        case .accessoryInline:
            Label("\(streak) days · \(snapshot.openTodos.count) to-dos", systemImage: "flame.fill")
                .containerBackground(for: .widget) {}

        default:
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: loggedToday ? "flame.fill" : "flame")
                        .font(.title2)
                        .foregroundStyle(loggedToday ? .orange : .secondary)
                    Spacer()
                    if loggedToday {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                    }
                }
                Spacer(minLength: 0)
                Text("\(streak)")
                    .font(.system(size: 48, weight: .bold, design: .rounded))
                    .minimumScaleFactor(0.5)
                    .contentTransition(.numericText())
                Text("day streak")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                Text(todayText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(loggedToday ? .green : .orange)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .containerBackground(for: .widget) {
                LinearGradient(colors: [Color.orange.opacity(0.18), Color.clear], startPoint: .top, endPoint: .bottom)
            }
        }
    }
}

#Preview(as: .systemSmall) {
    StreakWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .placeholder)
}

#Preview(as: .accessoryRectangular) {
    StreakWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .placeholder)
}
