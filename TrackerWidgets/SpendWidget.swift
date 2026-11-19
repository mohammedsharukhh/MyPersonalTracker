//
//  SpendWidget.swift
//  TrackerWidgets
//
//  Today's spending against the daily cap, and the month against its limit,
//  from the Google Sheet.
//

import SwiftUI
import WidgetKit

struct SpendWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SpendWidget", provider: SnapshotProvider()) { entry in
            SpendWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Spending today")
        .description("Today's spend against your daily cap, and the month so far.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

struct SpendWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    private var sheet: WidgetSnapshot.SheetToday? { snapshot.sheet }
    /// After midnight with the app unopened, today's spend starts again at zero.
    private var spentToday: Double { sheet.map { $0.isFor(snapshot.generatedAt) ? $0.spentToday : 0 } ?? 0 }
    private var cap: Double { sheet?.dailyCap ?? 0 }
    private var over: Bool { cap > 0 && spentToday > cap }
    private var currency: String { sheet?.currency ?? "" }

    private func amount(_ v: Double) -> String { v.formatted(.number.precision(.fractionLength(0...2))) }

    var body: some View {
        if sheet == nil {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "tablecells").font(.title2).foregroundStyle(.secondary)
                Text("Connect your sheet in the app").font(.caption.weight(.semibold))
            }
            .containerBackground(for: .widget) {}
        } else {
            switch family {
            case .accessoryInline:
                Label("\(amount(spentToday)) / \(amount(cap)) \(currency) today", systemImage: "banknote")
                    .containerBackground(for: .widget) {}
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 2) {
                    Label("Spent today", systemImage: "banknote").font(.headline).widgetAccentable()
                    Text("\(amount(spentToday)) of \(amount(cap)) \(currency)")
                    Gauge(value: min(spentToday, max(cap, 0.01)), in: 0...max(cap, 0.01)) { EmptyView() }
                        .gaugeStyle(.accessoryLinearCapacity)
                }
                .containerBackground(for: .widget) {}
            case .systemMedium:
                HStack(spacing: 16) {
                    todayBlock
                    Divider()
                    monthBlock
                }
                .containerBackground(for: .widget) { background }
            default:
                todayBlock.containerBackground(for: .widget) { background }
            }
        }
    }

    private var background: some View {
        LinearGradient(colors: [(over ? Color.red : Color.teal).opacity(0.16), .clear], startPoint: .top, endPoint: .bottom)
    }

    private var todayBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "banknote.fill").foregroundStyle(over ? .red : .teal)
                Spacer()
                if over { Text("OVER").font(.caption2.weight(.heavy)).foregroundStyle(.red) }
            }
            Spacer(minLength: 0)
            Text(amount(spentToday))
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .minimumScaleFactor(0.5)
            Text("of \(amount(cap)) \(currency) today").font(.caption.weight(.medium)).foregroundStyle(.secondary)
            ProgressView(value: min(spentToday, max(cap, 0.01)), total: max(cap, 0.01))
                .tint(over ? .red : .teal)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var monthBlock: some View {
        let spent = sheet?.monthSpent ?? 0
        let limit = sheet?.monthlyLimit ?? 0
        return VStack(alignment: .leading, spacing: 6) {
            Text("This month").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            Text(amount(spent)).font(.system(size: 26, weight: .bold, design: .rounded)).minimumScaleFactor(0.5)
            Text(limit > 0 ? (spent <= limit ? "\(amount(limit - spent)) \(currency) left" : "\(amount(spent - limit)) \(currency) over")
                           : currency)
                .font(.caption.weight(.medium))
                .foregroundStyle(limit > 0 && spent > limit ? .red : .secondary)
            ProgressView(value: min(spent, max(limit, 0.01)), total: max(limit, 0.01)).tint(spent > limit ? .red : .indigo)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview(as: .systemMedium) {
    SpendWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .placeholder)
}
