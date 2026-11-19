//
//  SheetWidgets.swift
//  TrackerWidgets
//
//  Salary split, study today and the shopping list, from the Google Sheet.
//

import SwiftUI
import WidgetKit

private func amount(_ v: Double) -> String { v.formatted(.number.precision(.fractionLength(0))) }

/// Shown by every sheet widget until the app has synced a sheet.
private struct ConnectSheetHint: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: "tablecells").font(.title2).foregroundStyle(.secondary)
            Text("Connect your sheet in the app").font(.caption.weight(.semibold))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct Bar: View {
    let fraction: Double
    let tint: Color
    var body: some View {
        GeometryReader { geo in
            Capsule().fill(.quaternary)
                .overlay(alignment: .leading) { Capsule().fill(tint).frame(width: geo.size.width * min(max(fraction, 0), 1)) }
        }
        .frame(height: 5)
    }
}

// MARK: - Salary split

struct SplitWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SplitWidget", provider: SnapshotProvider()) { entry in
            SplitWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Salary split")
        .description("This month's buckets: what's used and what's left of each share.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct SplitWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if let sheet = snapshot.sheet {
                if let rows = sheet.split, !rows.isEmpty {
                    if family == .systemSmall { small(rows, sheet.currency) } else { list(rows, sheet.currency) }
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        Image(systemName: "chart.pie.fill").font(.title2).foregroundStyle(.indigo)
                        Text("Enter this month's salary to see your split").font(.caption.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            } else {
                ConnectSheetHint()
            }
        }
        .containerBackground(for: .widget) {
            LinearGradient(colors: [Color.indigo.opacity(0.14), .clear], startPoint: .top, endPoint: .bottom)
        }
    }

    private func tint(_ row: WidgetSnapshot.SheetToday.SplitRow) -> Color {
        if row.saving { return row.used + 0.005 >= row.planned ? .green : .teal }
        let f = row.planned > 0 ? row.used / row.planned : 0
        return f > 1.0001 ? .red : f >= 0.8 ? .orange : .indigo
    }

    private func status(_ row: WidgetSnapshot.SheetToday.SplitRow, _ currency: String) -> String {
        let left = row.planned - row.used
        if row.saving { return left > 0.005 ? "\(amount(left)) to put aside" : "Done ✓" }
        return left >= -0.005 ? "\(amount(left)) left" : "\(amount(-left)) over"
    }

    /// Small: the spending buckets only, as compact bars.
    private func small(_ rows: [WidgetSnapshot.SheetToday.SplitRow], _ currency: String) -> some View {
        let spending = rows.filter { !$0.saving }
        let left = spending.reduce(0) { $0 + $1.planned - $1.used }
        return VStack(alignment: .leading, spacing: 6) {
            Label("Split", systemImage: "chart.pie.fill").font(.caption.weight(.semibold)).foregroundStyle(.indigo)
            Text(amount(left)).font(.system(size: 26, weight: .bold, design: .rounded)).minimumScaleFactor(0.5)
                .foregroundStyle(left < 0 ? .red : .primary)
            Text(left < 0 ? "\(currency) over to spend" : "\(currency) left to spend").font(.caption2).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            ForEach(spending, id: \.name) { row in
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.name).font(.system(size: 10, weight: .semibold)).lineLimit(1)
                    Bar(fraction: row.planned > 0 ? row.used / row.planned : 0, tint: tint(row))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func list(_ rows: [WidgetSnapshot.SheetToday.SplitRow], _ currency: String) -> some View {
        VStack(alignment: .leading, spacing: family == .systemLarge ? 12 : 5) {
            HStack {
                Label("Salary split", systemImage: "chart.pie.fill").font(.caption.weight(.semibold)).foregroundStyle(.indigo)
                Spacer()
                Text(currency).font(.caption2).foregroundStyle(.secondary)
            }
            ForEach(rows, id: \.name) { row in
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Image(systemName: row.symbol).font(.caption2).foregroundStyle(tint(row)).frame(width: 14)
                        Text(row.name).font(.caption.weight(.semibold)).lineLimit(1)
                        Spacer(minLength: 4)
                        Text(status(row, currency)).font(.caption2.weight(.medium)).monospacedDigit()
                            .foregroundStyle(row.used > row.planned + 0.005 && !row.saving ? .red : .secondary)
                            .lineLimit(1)
                    }
                    if family == .systemLarge {
                        Bar(fraction: row.planned > 0 ? row.used / row.planned : 0, tint: tint(row))
                        Text("\(amount(row.used)) of \(amount(row.planned))").font(.caption2).foregroundStyle(.secondary).monospacedDigit()
                    } else {
                        Bar(fraction: row.planned > 0 ? row.used / row.planned : 0, tint: tint(row))
                    }
                }
            }
            if family == .systemLarge { Spacer(minLength: 0) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - Study today

struct StudyWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StudyWidget", provider: SnapshotProvider()) { entry in
            StudyWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Study today")
        .description("Minutes against today's target, your study streak, what's due and what's next.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular])
    }
}

struct StudyWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    private var study: WidgetSnapshot.SheetToday.Study? { snapshot.sheet?.study }
    /// After midnight with the app unopened, today's minutes start again at zero.
    private var isToday: Bool { snapshot.sheet?.isFor(snapshot.generatedAt) ?? false }
    private var minutes: Int { isToday ? study?.minutesToday ?? 0 : 0 }
    private var target: Int { max(study?.dailyTarget ?? 60, 1) }
    private var fraction: Double { Double(minutes) / Double(target) }
    private var done: Bool { minutes >= target }

    var body: some View {
        Group {
            if snapshot.sheet == nil {
                ConnectSheetHint()
            } else if let study {
                switch family {
                case .accessoryCircular:
                    Gauge(value: min(fraction, 1)) { Image(systemName: "book.fill") } currentValueLabel: { Text(minutes.durationText) }
                        .gaugeStyle(.accessoryCircularCapacity)
                case .accessoryRectangular:
                    VStack(alignment: .leading, spacing: 2) {
                        Label("Study \(minutes.durationText) / \(target.durationText)", systemImage: "book.fill").font(.headline).widgetAccentable()
                        if let next = study.next { Text(next).lineLimit(1) }
                        if study.due > 0 { Text("\(study.due) to revise").foregroundStyle(.secondary) }
                    }
                case .systemMedium:
                    HStack(spacing: 16) {
                        ring.frame(width: 96, height: 96)
                        details(study)
                    }
                default:
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: "book.fill").foregroundStyle(.purple)
                            Spacer()
                            if study.streak > 0 { Text("🔥 \(study.streak)").font(.caption.weight(.bold)) }
                        }
                        Spacer(minLength: 0)
                        Text(minutes.durationText).font(.system(size: 30, weight: .bold, design: .rounded)).minimumScaleFactor(0.5)
                        Text(done ? "Target reached ✓" : "of \(target.durationText) today").font(.caption.weight(.medium))
                            .foregroundStyle(done ? .green : .secondary)
                        Bar(fraction: fraction, tint: done ? .green : .purple)
                        if study.due > 0 { Text("\(study.due) to revise").font(.caption2.weight(.semibold)).foregroundStyle(.orange) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: "book.fill").font(.title2).foregroundStyle(.purple)
                    Text("Add a subject in the Study tab").font(.caption.weight(.semibold))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .containerBackground(for: .widget) {
            if family == .systemSmall || family == .systemMedium {
                LinearGradient(colors: [Color.purple.opacity(0.14), .clear], startPoint: .top, endPoint: .bottom)
            }
        }
    }

    private var ring: some View {
        ZStack {
            Circle().stroke(.quaternary, lineWidth: 9)
            Circle().trim(from: 0, to: min(fraction, 1)).stroke(done ? Color.green : .purple, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text(minutes.durationText).font(.system(.title3, design: .rounded, weight: .bold)).minimumScaleFactor(0.6)
                Text("of \(target.durationText)").font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    private func details(_ study: WidgetSnapshot.SheetToday.Study) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(done ? "Target reached ✓" : "Study today").font(.subheadline.weight(.semibold)).foregroundStyle(done ? .green : .primary)
            if study.streak > 0 { Label("\(study.streak)-day streak", systemImage: "flame.fill").font(.caption).foregroundStyle(.orange) }
            if study.due > 0 {
                Label("\(study.due) concept\(study.due == 1 ? "" : "s") to revise", systemImage: "arrow.clockwise").font(.caption)
            }
            if let next = study.next {
                VStack(alignment: .leading, spacing: 1) {
                    Text("NEXT\(study.nextSubject.map { " · \($0.uppercased())" } ?? "")").font(.system(size: 9, weight: .bold)).foregroundStyle(.secondary)
                    Text(next).font(.caption.weight(.semibold)).lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Shopping list

struct ShoppingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ShoppingWidget", provider: SnapshotProvider()) { entry in
            ShoppingWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Shopping list")
        .description("What's left to buy, with an estimate from the prices you've paid.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}

struct ShoppingWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if let sheet = snapshot.sheet {
                let list = sheet.shopping ?? .init(count: 0, inCart: 0, items: [], estimate: nil)
                switch family {
                case .accessoryRectangular:
                    VStack(alignment: .leading, spacing: 2) {
                        Label(list.count == 0 ? "Shopping list empty" : "\(list.count) to buy", systemImage: "cart.fill").font(.headline).widgetAccentable()
                        Text(list.items.prefix(3).joined(separator: ", ")).lineLimit(2)
                    }
                default:
                    content(list, currency: sheet.currency)
                }
            } else {
                ConnectSheetHint()
            }
        }
        .containerBackground(for: .widget) {
            if family != .accessoryRectangular {
                LinearGradient(colors: [Color.green.opacity(0.14), .clear], startPoint: .top, endPoint: .bottom)
            }
        }
    }

    private func content(_ list: WidgetSnapshot.SheetToday.Shopping, currency: String) -> some View {
        let shown = family == .systemSmall ? 3 : 6
        return VStack(alignment: .leading, spacing: 5) {
            HStack(alignment: .firstTextBaseline) {
                Image(systemName: "cart.fill").foregroundStyle(.green)
                Text(list.count == 0 ? "All bought" : "\(list.count) to buy").font(.subheadline.weight(.bold))
                Spacer(minLength: 0)
                if let estimate = list.estimate, family != .systemSmall {
                    Text("≈ \(amount(estimate)) \(currency)").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                }
            }
            if list.count == 0 {
                Spacer(minLength: 0)
                Text("Nothing on your list.").font(.caption).foregroundStyle(.secondary)
                Spacer(minLength: 0)
            } else {
                let columns = family == .systemMedium && list.items.count > 3
                    ? [GridItem(.flexible(), alignment: .leading), GridItem(.flexible(), alignment: .leading)]
                    : [GridItem(.flexible(), alignment: .leading)]
                LazyVGrid(columns: columns, alignment: .leading, spacing: 4) {
                    ForEach(list.items.prefix(shown), id: \.self) { item in
                        HStack(spacing: 5) {
                            Image(systemName: "circle").font(.system(size: 9)).foregroundStyle(.secondary)
                            Text(item).font(.caption).lineLimit(1)
                        }
                    }
                }
                Spacer(minLength: 0)
                let more = list.count - min(shown, list.items.count)
                HStack {
                    if more > 0 { Text("+\(more) more").font(.caption2.weight(.semibold)).foregroundStyle(.secondary) }
                    Spacer(minLength: 0)
                    if list.inCart > 0 { Text("\(list.inCart) in cart").font(.caption2.weight(.semibold)).foregroundStyle(.green) }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

#Preview("Split", as: .systemMedium) { SplitWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .placeholder) }
#Preview("Study", as: .systemMedium) { StudyWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .placeholder) }
#Preview("Shopping", as: .systemMedium) { ShoppingWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .placeholder) }

// MARK: - Steps

struct StepsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StepsWidget", provider: SnapshotProvider()) { entry in
            StepsWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Steps")
        .description("Today's steps against your goal, your step streak and the week.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct StepsWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    private var steps: WidgetSnapshot.SheetToday.Steps? { snapshot.sheet?.steps }
    /// After midnight with the app unopened, today starts again at zero.
    private var isToday: Bool { snapshot.sheet?.isFor(snapshot.generatedAt) ?? false }
    private var today: Int { isToday ? steps?.today ?? 0 : 0 }
    private var target: Int { max(steps?.target ?? 8000, 1) }
    private var fraction: Double { Double(today) / Double(target) }
    private var done: Bool { today >= target }
    private let green = Color(red: 0.13, green: 0.77, blue: 0.37)

    var body: some View {
        Group {
            if snapshot.sheet == nil {
                ConnectSheetHint()
            } else {
                switch family {
                case .accessoryInline:
                    Label("\(today.formatted()) steps", systemImage: "figure.walk")
                case .accessoryCircular:
                    Gauge(value: min(fraction, 1)) { Image(systemName: "figure.walk") } currentValueLabel: {
                        Text(today >= 10_000 ? "\(today / 1000)k" : today.formatted(.number.notation(.compactName)))
                    }
                    .gaugeStyle(.accessoryCircularCapacity)
                case .accessoryRectangular:
                    VStack(alignment: .leading, spacing: 2) {
                        Label("\(today.formatted()) steps", systemImage: "figure.walk").font(.headline).widgetAccentable()
                        Gauge(value: min(fraction, 1)) { EmptyView() }.gaugeStyle(.accessoryLinearCapacity)
                        Text(done ? "Goal reached ✓" : "\((target - today).formatted()) to go").font(.caption)
                    }
                case .systemMedium:
                    HStack(spacing: 16) {
                        ring.frame(width: 100, height: 100)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(done ? "Goal reached ✓" : "\((target - today).formatted()) to go").font(.subheadline.weight(.semibold))
                                .foregroundStyle(done ? green : .primary)
                            if let streak = steps?.streak, streak > 0 {
                                Label("\(streak)-day streak", systemImage: "flame.fill").font(.caption.weight(.semibold)).foregroundStyle(.orange)
                            }
                            weekBars.frame(height: 40)
                        }
                    }
                default:
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: "figure.walk").foregroundStyle(green)
                            Spacer()
                            if let streak = steps?.streak, streak > 0 { Text("🔥 \(streak)").font(.caption.weight(.bold)) }
                        }
                        Spacer(minLength: 0)
                        Text(today.formatted()).font(.system(size: 30, weight: .bold, design: .rounded)).minimumScaleFactor(0.5)
                        Text(done ? "goal reached ✓" : "of \(target.formatted()) steps").font(.caption.weight(.medium))
                            .foregroundStyle(done ? green : .secondary)
                        Bar(fraction: fraction, tint: green)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .containerBackground(for: .widget) {
            if family == .systemSmall || family == .systemMedium {
                LinearGradient(colors: [green.opacity(0.16), .clear], startPoint: .top, endPoint: .bottom)
            }
        }
    }

    private var ring: some View {
        ZStack {
            Circle().stroke(.quaternary, lineWidth: 10)
            Circle().trim(from: 0, to: min(fraction, 1)).stroke(green, style: StrokeStyle(lineWidth: 10, lineCap: .round)).rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text(today.formatted()).font(.system(.title3, design: .rounded, weight: .bold)).minimumScaleFactor(0.5)
                Text("steps").font(.caption2).foregroundStyle(.secondary)
            }
        }
    }

    /// The week as bars, today last; days at the goal in green.
    private var weekBars: some View {
        let week = (steps?.week ?? []).enumerated().map { i, v in i == 6 && !isToday ? 0 : v }
        let top = max(week.max() ?? 0, target)
        return HStack(alignment: .bottom, spacing: 4) {
            ForEach(Array(week.enumerated()), id: \.offset) { _, v in
                Capsule().fill(v >= target ? green : Color.secondary.opacity(0.35))
                    .frame(height: max(3, 40 * CGFloat(v) / CGFloat(top)))
            }
        }
    }
}

#Preview("Steps", as: .systemMedium) { StepsWidget() } timeline: { SnapshotEntry(date: .now, snapshot: .placeholder) }
