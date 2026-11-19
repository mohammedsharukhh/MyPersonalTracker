//
//  PayWidget.swift
//  TrackerWidgets
//
//  One tap to Pay with UPI: opens the app on the Pay screen (scan, amount, app).
//

import SwiftUI
import WidgetKit

struct PayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PayWidget", provider: PayProvider()) { _ in
            PayWidgetView()
                .widgetURL(URL(string: "mypersonaltracker://scan"))
        }
        .configurationDisplayName("Scan & Pay")
        .description("Opens the QR scanner: scan, pay with PhonePe, Google Pay or Paytm, and it's added to Finance.")
        .supportedFamilies([.systemSmall, .accessoryCircular])
    }
}

struct PayEntry: TimelineEntry { let date: Date }

struct PayProvider: TimelineProvider {
    func placeholder(in context: Context) -> PayEntry { PayEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (PayEntry) -> Void) { completion(PayEntry(date: .now)) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<PayEntry>) -> Void) {
        completion(Timeline(entries: [PayEntry(date: .now)], policy: .never))
    }
}

struct PayWidgetView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if family == .accessoryCircular {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "qrcode.viewfinder").font(.title2.weight(.semibold))
            }
            .containerBackground(.clear, for: .widget)
            .accessibilityLabel("Scan and pay")
        } else {
            PayWidgetFace()
                .containerBackground(for: .widget) { PayWidgetFace.background }
        }
    }
}

/// Dark card, "Scan. Pay. Track it." top left, the app's mark top right, a round
/// green scan button bottom left — tap anywhere to open the scanner.
struct PayWidgetFace: View {
    static let green = Color(red: 0.16, green: 0.62, blue: 0.40)

    static var background: some View {
        ZStack {
            Color(red: 0.03, green: 0.04, blue: 0.06)
            // A soft glow: navy at the top, green rising from the bottom.
            RadialGradient(colors: [Color(red: 0.24, green: 0.30, blue: 0.62).opacity(0.55), .clear],
                           center: .init(x: 0.55, y: -0.05), startRadius: 0, endRadius: 150)
            RadialGradient(colors: [green.opacity(0.55), .clear], center: .init(x: 0.35, y: 1.15), startRadius: 0, endRadius: 140)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Scan. Pay.").font(.system(size: 15, weight: .medium))
                        .foregroundStyle(.white.opacity(0.75))
                    Text("Track it.").font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                }
                Spacer(minLength: 4)
                Image(systemName: "indianrupeesign.square")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
            }
            Spacer(minLength: 0)
            ZStack {
                Circle()
                    .fill(LinearGradient(colors: [Self.green.opacity(0.95), Color(red: 0.07, green: 0.38, blue: 0.24)],
                                         startPoint: .top, endPoint: .bottom))
                    .overlay(Circle().stroke(.white.opacity(0.18), lineWidth: 1))
                    .shadow(color: Self.green.opacity(0.6), radius: 10)
                Image(systemName: "qrcode.viewfinder")
                    .font(.system(size: 24, weight: .medium))
                    .foregroundStyle(.white)
            }
            .frame(width: 54, height: 54)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Scan and pay with UPI")
    }
}
