//
//  SheetComponents.swift
//  MyPersonalTracker
//
//  Design system for the Command Center screens: each tab has its own colour
//  world (TabWorld: a mesh-gradient hero and a soft wash behind the page) with
//  frosted cards over it; semantic good/bad/warn colours that work in light and
//  dark mode; and a handful of building blocks (cards, stat tiles, progress
//  rows, pills) so every screen reads the same way.
//

import SwiftUI

enum SheetTheme {
    /// Brand: the workbook's navy, lifted slightly in dark mode.
    static let navy = Color(light: 0x1F3864, dark: 0x6F8FD8)
    static let navyDeep = Color(hex: 0x16294A)
    static let blue = Color(light: 0x2F5597, dark: 0x7FA2E6)

    static let good = Color(light: 0x16A34A, dark: 0x4ADE80)
    static let bad = Color(light: 0xDC2626, dark: 0xF87171)
    static let warn = Color(light: 0xD97706, dark: 0xFBBF24)
    static let teal = Color(light: 0x0F766E, dark: 0x2DD4BF)
    static let purple = Color(light: 0x7C3AED, dark: 0xA78BFA)
    static let sky = Color(light: 0x0284C7, dark: 0x38BDF8)
    static let indigo = Color(light: 0x4F46E5, dark: 0x818CF8)

    /// Frosted, so the tab's colour wash glows through the cards.
    static let card = Material.regularMaterial
    static let page = Color(.systemGroupedBackground)
    static let hairline = Color(.separator).opacity(0.5)
    static let input = Color(.tertiarySystemFill)

    /// The hero banner: a mesh gradient in the colours of the tab it's on (navy elsewhere).
    static var heroGradient: WorldHero { WorldHero() }
    /// Behind a tab's page: the grouped background with a soft glow of the tab's colour at the top.
    static var pageWash: WorldWash { WorldWash() }

    /// Colour for the sheet's status words.
    static func tint(forStatus status: String?) -> Color {
        switch status?.lowercased() {
        case "excellent", "goal met", "ok": good
        case "on track": warn
        case "needs work", "below goal", "over": bad
        default: .secondary
        }
    }

    /// Colour for a 0…1 rate: green from 80%, amber from 50%, red below.
    static func tint(forRate rate: Double) -> Color {
        rate >= 0.8 ? good : (rate >= 0.5 ? warn : bad)
    }

    static func categoryColor(_ category: String?) -> Color {
        switch category {
        case "Groceries": Color(light: 0x16A34A, dark: 0x4ADE80)
        case "Food & Dining": Color(light: 0xEA580C, dark: 0xFB923C)
        case "Transport": Color(light: 0x2563EB, dark: 0x60A5FA)
        case "Rent & Utilities": Color(light: 0x7C3AED, dark: 0xA78BFA)
        case "Mobile & Internet": Color(light: 0x0891B2, dark: 0x22D3EE)
        case "Health & Pharmacy": Color(light: 0xDC2626, dark: 0xF87171)
        case "Personal Care": Color(light: 0xDB2777, dark: 0xF472B6)
        case "Household": Color(light: 0x92400E, dark: 0xD6A26B)
        case "Clothing": Color(light: 0xC026D3, dark: 0xE879F9)
        case "Remittance": Color(light: 0x0F766E, dark: 0x2DD4BF)
        case "Entertainment": Color(light: 0x9333EA, dark: 0xC084FC)
        case "Gifts & Family": Color(light: 0xE11D48, dark: 0xFB7185)
        case "Education": Color(light: 0x4F46E5, dark: 0x818CF8)
        case "Travel": Color(light: 0x0284C7, dark: 0x38BDF8)
        default: Color(.systemGray)
        }
    }

    static func categoryIcon(_ category: String?) -> String {
        switch category {
        case "Groceries": "cart.fill"
        case "Food & Dining": "fork.knife"
        case "Transport": "car.fill"
        case "Rent & Utilities": "house.fill"
        case "Mobile & Internet": "wifi"
        case "Health & Pharmacy": "cross.case.fill"
        case "Personal Care": "sparkles"
        case "Household": "sofa.fill"
        case "Clothing": "tshirt.fill"
        case "Remittance": "arrow.up.right.circle.fill"
        case "Entertainment": "film.fill"
        case "Gifts & Family": "gift.fill"
        case "Education": "graduationcap.fill"
        case "Travel": "airplane"
        default: "tag.fill"
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    /// A colour that switches with light/dark mode.
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                           blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
        })
    }
}

// MARK: - Number formatting (as the sheet formats them)

enum SheetFormat {
    static func qar(_ value: Double?) -> String {
        (value ?? 0).formatted(.number.precision(.fractionLength(2)).grouping(.automatic))
    }

    static func inr(_ value: Double?) -> String {
        (value ?? 0).formatted(.number.precision(.fractionLength(0)))
    }

    /// A fraction as "75.0%".
    static func percent(_ fraction: Double?) -> String {
        (fraction ?? 0).formatted(.percent.precision(.fractionLength(1)))
    }

    static func percent(_ fraction: Double?, _ digits: Int) -> String {
        (fraction ?? 0).formatted(.percent.precision(.fractionLength(digits)))
    }

    static func number(_ value: Double?, digits: Int = 1) -> String {
        guard let value else { return "—" }
        return value.formatted(.number.precision(.fractionLength(0...digits)))
    }

    /// Parses the sheet's display text ("2,015.00", "75.0%", "(1,915.00)") back to a number.
    static func parse(_ text: String) -> Double? {
        var t = text.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces)
        var sign = 1.0
        if t.hasPrefix("("), t.hasSuffix(")") { sign = -1; t = String(t.dropFirst().dropLast()) }
        if t.hasSuffix("%") { return Double(t.dropLast()).map { sign * $0 / 100 } }
        return Double(t).map { sign * $0 }
    }
}

// MARK: - Currencies

/// The workbook's two currencies, from its Settings sheet. Read inside a view's
/// body, they update the view when the sheet's setting changes.
enum Money {
    static var main: String { SheetStore.shared.snapshot?.settings.mainCurrency ?? "QAR" }
    static var home: String { SheetStore.shared.snapshot?.settings.homeCurrency ?? "INR" }

    /// "₹", "$", "€" — or the code itself when there's no distinct symbol.
    static func symbol(_ code: String) -> String {
        Locale(identifier: "en@currency=\(code)").currencySymbol ?? code
    }

    /// An already formatted amount in the home currency: "₹2,610" or "PKR 2,610".
    static func homeText(_ formatted: String) -> String {
        let s = symbol(home)
        return s.count == 1 ? s + formatted : "\(home) \(formatted)"
    }

    static func homeAmount(_ value: Double?) -> String { homeText(SheetFormat.inr(value)) }

    /// Everything in one currency (main = home): no converter, no "≈ other currency".
    static var single: Bool { main == home }
}

/// Where a currency picker would be, when there's only one currency.
struct CurrencyPill: View {
    let code: String
    var body: some View {
        HStack(spacing: 6) {
            Text(CurrencyBook.flag(code))
            Text(code).font(.headline)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(SheetTheme.input, in: Capsule())
    }
}

// MARK: - Dates

enum SheetDate {
    private static let parser: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func date(_ text: String?) -> Date? { text.flatMap(parser.date(from:)) }
    static func string(_ date: Date) -> String { parser.string(from: date) }
    static var today: String { string(.now) }

    /// "15 Aug 2026"
    static func short(_ text: String?) -> String {
        guard let date = date(text) else { return text ?? "" }
        return date.formatted(.dateTime.day().month(.abbreviated).year())
    }

    /// "Mon, 15 Aug"
    static func weekdayDay(_ text: String?) -> String {
        guard let date = date(text) else { return text ?? "" }
        return date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
    }
}

// MARK: - Tab colour worlds

/// Each tab's colour world: its hero, the wash behind its page and its tab-bar highlight.
/// White text stays above 4.5:1 on every hero colour, the brightest included.
enum TabWorld {
    case neutral, dashboard, daily, parents, study, food, finance

    /// Deep, middle and bright hero colours, and the accent shown on dark backgrounds.
    private var hex: (deep: UInt32, mid: UInt32, bright: UInt32, darkAccent: UInt32) {
        switch self {
        case .neutral: (0x16294A, 0x1F3864, 0x2F5597, 0x6F8FD8)
        case .dashboard: (0x1E1B4B, 0x3730A3, 0x6D28D9, 0xA78BFA)
        case .daily: (0x042F2E, 0x115E59, 0x0F766E, 0x2DD4BF)
        case .parents: (0x4C0519, 0x881337, 0xBE123C, 0xFB7185)
        case .study: (0x431407, 0x7C2D12, 0xC2410C, 0xFB923C)
        case .food: (0x052E16, 0x14532D, 0x15803D, 0x4ADE80)
        case .finance: (0x172554, 0x1E3A8A, 0x1D4ED8, 0x60A5FA)
        }
    }

    /// The tab's colour on the page: bright in light mode, lifted in dark.
    var accent: Color { Color(light: hex.bright, dark: hex.darkAccent) }

    /// Deep corners, a bright glow upper right.
    var heroMesh: MeshGradient {
        let (d, m, b) = (Color(hex: hex.deep), Color(hex: hex.mid), Color(hex: hex.bright))
        return MeshGradient(width: 3, height: 3,
                            points: [[0, 0], [0.5, 0], [1, 0], [0, 0.5], [0.6, 0.4], [1, 0.5], [0, 1], [0.5, 1], [1, 1]],
                            colors: [d, m, b, m, b, m, d, d, m])
    }

    /// The page background with the tab's colour glowing in from the top.
    var washMesh: MeshGradient {
        let base = Color(.systemGroupedBackground)
        func glow(_ amount: Double) -> Color { base.mix(with: accent, by: amount) }
        return MeshGradient(width: 3, height: 3,
                            points: [[0, 0], [0.5, 0], [1, 0], [0, 0.35], [0.5, 0.35], [1, 0.35], [0, 1], [0.5, 1], [1, 1]],
                            colors: [glow(0.16), glow(0.22), glow(0.12), glow(0.05), glow(0.07), glow(0.04), base, base, base])
    }
}

extension EnvironmentValues {
    /// Set per tab in ContentView; sheets opened elsewhere stay neutral (navy).
    @Entry var tabWorld: TabWorld = .neutral
}

/// SheetTheme.heroGradient: the hero mesh of the tab it's drawn on.
struct WorldHero: ShapeStyle {
    func resolve(in environment: EnvironmentValues) -> MeshGradient { environment.tabWorld.heroMesh }
}

/// SheetTheme.pageWash: the page background of the tab it's drawn on.
struct WorldWash: ShapeStyle {
    func resolve(in environment: EnvironmentValues) -> MeshGradient { environment.tabWorld.washMesh }
}

// MARK: - Building blocks

/// The standard surface: frosted glass over the page's colour wash, with a light edge.
struct SheetCard<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(SheetTheme.card, in: shape)
            .overlay(shape.strokeBorder(.white.opacity(scheme == .dark ? 0.08 : 0.6), lineWidth: 0.75))
    }
}

/// Section title above a group of cards.
struct SectionTitle: View {
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.title3.weight(.bold))
            if let subtitle { Text(subtitle).font(.footnote).foregroundStyle(.secondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }
}

/// Tinted icon in a soft rounded square.
struct IconBadge: View {
    let symbol: String
    let tint: Color
    var size: CGFloat = 34

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: size * 0.3, style: .continuous))
    }
}

/// One KPI: icon, label, big rounded value, caption.
struct SheetStatTile: View {
    let symbol: String
    let tint: Color
    let title: String
    let value: String
    var caption: String?
    var valueTint: Color = .primary

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                IconBadge(symbol: symbol, tint: tint, size: 30)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
            Text(value)
                .font(.system(.title2, design: .rounded, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(valueTint)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            if let caption {
                Text(caption).font(.caption2).foregroundStyle(.tertiary).lineLimit(1)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

/// Title, trailing detail, and a rounded bar filled to `fraction`.
struct ProgressRow: View {
    let title: String
    let detail: String
    let fraction: Double
    let tint: Color
    var symbol: String?

    var body: some View {
        HStack(spacing: 12) {
            if let symbol { IconBadge(symbol: symbol, tint: tint, size: 30) }
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(title).font(.subheadline)
                    Spacer()
                    Text(detail).font(.subheadline.weight(.semibold)).monospacedDigit()
                }
                Capsule()
                    .fill(Color(.tertiarySystemFill))
                    .frame(height: 7)
                    .overlay(alignment: .leading) {
                        GeometryReader { geo in
                            Capsule().fill(tint).frame(width: geo.size.width * min(max(fraction, 0), 1))
                        }
                    }
            }
        }
    }
}

/// Label ··· value, as in the sheet's two-column tables.
struct KeyValueRow: View {
    let key: String
    let value: String
    var valueTint: Color = .primary

    var body: some View {
        HStack {
            Text(key).font(.subheadline).foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value).font(.subheadline.weight(.semibold)).monospacedDigit().foregroundStyle(valueTint)
        }
        .padding(.vertical, 2)
    }
}

/// Coloured capsule for a status word ("Excellent", "OVER", "Goal met").
struct StatusPill: View {
    let text: String
    var tint: Color?

    var body: some View {
        let color = tint ?? SheetTheme.tint(forStatus: text)
        Text(text)
            .font(.caption2.weight(.bold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.14), in: Capsule())
    }
}

/// Small translucent chip for the hero cards ("Rate 26.10", "Cap 20 QAR").
struct HeroChip: View {
    let symbol: String
    let text: String

    var body: some View {
        Label(text, systemImage: symbol)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(.white.opacity(0.16), in: Capsule())
    }
}

/// Ring showing a 0…1 value, with text in the middle.
struct ScoreRing: View {
    let fraction: Double
    var lineWidth: CGFloat = 8
    var tint: Color = .white
    var track: Color = .white.opacity(0.2)

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(max(fraction, 0), 1))
                .stroke(tint, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
    }
}

/// Any sheet table as the sheet displays it — used for the wide Weekly and
/// Monthly summaries, where a real grid is the clearest form.
struct SheetTableView: View {
    let table: SheetTable
    var minColumnWidth: CGFloat = 84

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            Grid(horizontalSpacing: 0, verticalSpacing: 0) {
                GridRow {
                    ForEach(Array(table.headers.enumerated()), id: \.offset) { index, header in
                        cell(header, column: index, isHeader: true)
                    }
                }
                .background(SheetTheme.navy.opacity(0.08))
                ForEach(Array(table.rows.enumerated()), id: \.offset) { rowIndex, row in
                    Divider().gridCellUnsizedAxes(.horizontal)
                    GridRow {
                        ForEach(Array(row.enumerated()), id: \.offset) { index, value in
                            cell(value, column: index, isHeader: false)
                        }
                    }
                }
            }
        }
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            if table.rows.isEmpty {
                Text("Nothing here yet.").font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private func cell(_ text: String, column: Int, isHeader: Bool) -> some View {
        let status = SheetTheme.tint(forStatus: text)
        let isStatus = !isHeader && status != .secondary
        let isNegative = !isHeader && text.hasPrefix("(")
        return Text(text.isEmpty ? " " : text)
            .font(isHeader ? .caption.weight(.semibold) : .subheadline.monospacedDigit())
            .fontWeight(isStatus ? .bold : nil)
            .foregroundStyle(isHeader ? SheetTheme.navy : (isStatus ? status : (isNegative ? SheetTheme.bad : .primary)))
            .multilineTextAlignment(column == 0 ? .leading : .trailing)
            .lineLimit(2)
            .frame(minWidth: minColumnWidth, alignment: column == 0 ? .leading : .trailing)
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
    }
}

/// Shown until the app can reach the sheet.
struct ConnectSheetCard: View {
    let onOpenSettings: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            IconBadge(symbol: "tablecells.badge.ellipsis", tint: SheetTheme.navy, size: 44)
            Text("Connect your Google Sheet").font(.title3.weight(.bold))
            Text("Everything here reads from and saves to your Life & Finance Command Center sheet. Sign in with Google and the app makes it in your own Google Drive — or finds the one you already have.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            ConnectGoogleButton(store: SheetStore.shared)
            Button("Other ways to connect", action: onOpenSettings)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
        }
        .padding(20)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// Error line with a retry.
struct SheetErrorBanner: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(SheetTheme.bad)
            Text(message).font(.footnote).frame(maxWidth: .infinity, alignment: .leading)
            Button("Retry", action: retry).font(.footnote.weight(.semibold)).tint(SheetTheme.bad)
        }
        .padding(12)
        .background(SheetTheme.bad.opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
