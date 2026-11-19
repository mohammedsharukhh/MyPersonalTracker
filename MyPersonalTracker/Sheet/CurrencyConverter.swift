//
//  CurrencyConverter.swift
//  MyPersonalTracker
//
//  Currency converter covering every ISO currency, based on the workbook's
//  main currency (Settings ▸ Currency): every rate reads "1 main = x". Where a
//  rate comes from, in priority order: the user's own rate, the sheet's
//  exchange rate (for the home currency), live rates (downloaded and cached for
//  offline use), then the fixed Gulf pegs.
//

import SwiftUI

@MainActor @Observable
final class CurrencyBook {
    static let shared = CurrencyBook()

    enum Source { case base, custom, sheet, live, peg, none }

    /// A rate the user typed: 1 `base` = `value` of the currency. Kept with its
    /// base so it stays right if the workbook's main currency changes later.
    struct Override: Codable, Hashable {
        var base: String
        var value: Double
    }

    private(set) var overrides: [String: Override]
    private(set) var customNames: [String: String]
    private(set) var favorites: [String]
    /// Live rates as published: 1 `liveBase` = x.
    private(set) var live: [String: Double]
    private var liveBase: String
    /// When the provider last published the downloaded rates.
    private(set) var liveDate: Date?
    private var liveFetched: Date?
    private(set) var isRefreshing = false
    private(set) var refreshError: String?

    var useSheetRateForHome: Bool { didSet { save() } }
    /// The sheet's currencies when last seen, to notice a change.
    private var seenMain: String
    private var seenHome: String
    var from: String { didSet { save() } }
    var to: String { didSet { save() } }

    private static let storageKey = "currencyBook.v3"
    static let defaultFavorites = ["QAR", "INR", "USD", "AED", "SAR", "EUR", "GBP"]

    /// Units per US dollar for currencies pegged to it, so exact offline.
    private static let pegsPerUSD: [String: Double] = [
        "USD": 1, "QAR": 3.64, "AED": 3.6725, "SAR": 3.75, "OMR": 0.3845, "BHD": 0.376, "JOD": 0.709
    ]

    private struct Stored: Codable {
        var overrides: [String: Override]
        var customNames: [String: String]
        var favorites: [String]
        var live: [String: Double]
        var liveBase: String
        var liveDate: Date?
        var liveFetched: Date?
        var useSheetRateForHome: Bool
        var from: String
        var to: String
        var seenMain: String?
        var seenHome: String?
    }

    private init() {
        let stored = UserDefaults.standard.data(forKey: Self.storageKey).flatMap { try? JSONDecoder().decode(Stored.self, from: $0) }
        overrides = stored?.overrides ?? [:]
        customNames = stored?.customNames ?? [:]
        favorites = stored?.favorites ?? Self.defaultFavorites
        live = stored?.live ?? [:]
        liveBase = stored?.liveBase ?? "QAR"
        liveDate = stored?.liveDate
        liveFetched = stored?.liveFetched
        useSheetRateForHome = stored?.useSheetRateForHome ?? true
        from = stored?.from ?? Money.main
        to = stored?.to ?? Money.home
        seenMain = stored?.seenMain ?? Money.main
        seenHome = stored?.seenHome ?? Money.home
    }

    /// The sheet is the source of truth for currencies: when its main or home
    /// currency changes, the converter's pair follows and rates re-base.
    func followSheet() {
        let main = Money.main, home = Money.home
        guard main != seenMain || home != seenHome else { return }
        if from == seenMain || from == seenHome { from = main }
        if to == seenHome || to == seenMain { to = home }
        for code in [main, home] where !favorites.contains(code) { favorites.insert(code, at: 0) }
        seenMain = main
        seenHome = home
        liveFetched = nil   // fetch rates based on the new main currency
        save()
    }

    private func save() {
        let stored = Stored(overrides: overrides, customNames: customNames, favorites: favorites, live: live, liveBase: liveBase,
                            liveDate: liveDate, liveFetched: liveFetched, useSheetRateForHome: useSheetRateForHome,
                            from: from, to: to, seenMain: seenMain, seenHome: seenHome)
        if let data = try? JSONEncoder().encode(stored) {
            UserDefaults.standard.set(data, forKey: Self.storageKey)
        }
    }

    // MARK: Currencies

    /// The workbook's main currency: every rate is "1 main = x".
    var main: String { Money.main }

    /// Every currency we can name: ISO list, whatever the rate feed has, and the user's own.
    var allCodes: [String] {
        let iso = Locale.commonISOCurrencyCodes
        return Array(Set(iso).union(live.keys).union(overrides.keys).union(customNames.keys).union([main, Money.home]))
            .sorted { name($0).localizedCaseInsensitiveCompare(name($1)) == .orderedAscending }
    }

    func name(_ code: String) -> String {
        customNames[code] ?? Locale.current.localizedString(forCurrencyCode: code) ?? code
    }

    func isFavorite(_ code: String) -> Bool { favorites.contains(code) }

    func toggleFavorite(_ code: String) {
        if let index = favorites.firstIndex(of: code) { favorites.remove(at: index) } else { favorites.append(code) }
        save()
    }

    // MARK: Rates

    var sheetRate: Double? { SheetStore.shared.snapshot?.settings.rate }

    func source(_ code: String) -> Source {
        if code == main { return .base }
        if overrides[code] != nil { return .custom }
        // The sheet's rate only counts while it was set for the current pair.
        if code == Money.home, useSheetRateForHome, sheetRate != nil,
           SheetStore.shared.snapshot?.settings.rateIsCurrent ?? false { return .sheet }
        if live[code] != nil, live[main] != nil { return .live }
        if Self.pegsPerUSD[code] != nil, Self.pegsPerUSD[main] != nil { return .peg }
        return .none
    }

    func followsSheet(_ code: String) -> Bool { source(code) == .sheet }

    /// 1 main = rate(code), or nil when no rate is known yet.
    func rate(_ code: String) -> Double? {
        switch source(code) {
        case .base: return 1
        case .custom:
            guard let o = overrides[code] else { return nil }
            // 1 main = (1 main in o.base) × o.value
            if o.base == main { return o.value }
            return marketRate(from: main, to: o.base).map { $0 * o.value }
        case .sheet: return sheetRate
        case .live, .peg: return marketRate(from: main, to: code)
        case .none: return nil
        }
    }

    /// Live (or pegged) rate for any pair, ignoring the user's and the sheet's rates.
    func liveRate(from: String, to: String) -> Double? { marketRate(from: from, to: to) }

    private func marketRate(from: String, to: String) -> Double? {
        if from == to { return 1 }
        if let f = live[from], let t = live[to], f > 0 { return t / f }
        if let f = Self.pegsPerUSD[from], let t = Self.pegsPerUSD[to] { return t / f }
        return nil
    }

    func convert(_ amount: Double, from: String, to: String) -> Double? {
        guard let fromRate = rate(from), let toRate = rate(to), fromRate > 0 else { return nil }
        return amount / fromRate * toRate
    }

    /// Sets (or with nil, clears) the user's rate: 1 main = `perMain` of `code`.
    func setOverride(_ code: String, perMain: Double?, name: String? = nil) {
        overrides[code] = perMain.map { Override(base: main, value: $0) }
        if let name, !name.isEmpty, Locale.current.localizedString(forCurrencyCode: code) == nil { customNames[code] = name }
        if !favorites.contains(code) { favorites.append(code) }
        save()
    }

    func removeCustomRates() {
        overrides = [:]
        useSheetRateForHome = true
        save()
    }

    // MARK: Live rates

    func refreshIfStale() async {
        followSheet()
        if live.isEmpty || (liveFetched.map { Date.now.timeIntervalSince($0) > 6 * 3600 } ?? true) {
            await refresh()
        }
    }

    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }
        struct Response: Decodable {
            let result: String
            let base_code: String?
            let rates: [String: Double]?
            let time_last_update_unix: Double?
        }
        do {
            let base = main
            let url = URL(string: "https://open.er-api.com/v6/latest/\(base)")!
            let (data, _) = try await URLSession.shared.data(from: url)
            let response = try JSONDecoder().decode(Response.self, from: data)
            guard response.result == "success", let rates = response.rates, !rates.isEmpty else {
                throw URLError(.badServerResponse)
            }
            live = rates
            liveBase = response.base_code ?? base
            liveDate = response.time_last_update_unix.map { Date(timeIntervalSince1970: $0) } ?? .now
            liveFetched = .now
            refreshError = nil
            save()
        } catch {
            refreshError = live.isEmpty ? "Couldn't download rates. Check your connection, or set a rate yourself."
                                        : "Couldn't update. Showing the last saved rates."
        }
    }

    // MARK: Formatting

    static func flag(_ code: String) -> String {
        let special = ["EUR": "🇪🇺", "XAF": "🌍", "XOF": "🌍", "XCD": "🌴", "XPF": "🌊", "ANG": "🇨🇼", "XDR": "🌐"]
        if let flag = special[code] { return flag }
        let letters = Array(code.uppercased().unicodeScalars.prefix(2))
        guard letters.count == 2, letters.allSatisfy({ ("A"..."Z").contains($0) }), letters[0] != "X" else { return "💱" }
        return String(String.UnicodeScalarView(letters.compactMap { UnicodeScalar(127397 + $0.value) }))
    }

    static func format(_ amount: Double) -> String {
        let digits = abs(amount) >= 100 ? 2 : (abs(amount) < 1 && amount != 0 ? 4 : 2)
        return amount.formatted(.number.precision(.fractionLength(0...digits)).grouping(.automatic))
    }

    static func format(_ amount: Double, _ code: String) -> String { format(amount) }
}

// MARK: - Converter screen

struct CurrencyConverterView: View {
    @State private var book = CurrencyBook.shared
    @State private var amountText = "100"
    @State private var editing: String?
    @State private var browsing = false
    @State private var adding = false
    @FocusState private var amountFocused: Bool
    @Environment(\.dismiss) private var dismiss

    private var amount: Double { Double(amountText.replacingOccurrences(of: ",", with: ".")) ?? 0 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    converterCard
                    quickAmounts
                    if let error = book.refreshError {
                        Label(error, systemImage: "wifi.exclamationmark")
                            .font(.footnote)
                            .foregroundStyle(SheetTheme.warn)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    favoritesCard
                    Text("Live rates by Exchange Rate API (open.er-api.com), saved for offline use. Gulf currencies are pegged to the US dollar. Tap any currency to use your own rate.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(16)
            }
            .background(SheetTheme.page)
            .navigationTitle("Currency converter")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { await book.refresh() }
            .task {
                #if DEBUG
                if UserDefaults.standard.bool(forKey: "converterBrowse") { browsing = true }
                #endif
                await book.refreshIfStale()
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() }.fontWeight(.semibold) }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { amountFocused = false }
                }
            }
            .sheet(item: Binding(get: { editing.map(CodeItem.init) }, set: { editing = $0?.code })) { RateEditor(code: $0.code) }
            .sheet(isPresented: $adding) { RateEditor(code: nil) }
            .sheet(isPresented: $browsing) { CurrencyPickerSheet(selection: $book.to, title: "All currencies") }
        }
        .tint(SheetTheme.navy)
    }

    private var converterCard: some View {
        let result = book.convert(amount, from: book.from, to: book.to)
        let unit = book.convert(1, from: book.from, to: book.to)
        return VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text("You have").font(.caption.weight(.semibold)).foregroundStyle(.white.opacity(0.75))
                HStack(spacing: 12) {
                    CurrencyMenu(selection: $book.from, onDark: true)
                    TextField("0", text: $amountText)
                        .keyboardType(.decimalPad)
                        .focused($amountFocused)
                        .multilineTextAlignment(.trailing)
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .minimumScaleFactor(0.5)
                }
                Text(book.name(book.from)).font(.caption).foregroundStyle(.white.opacity(0.7))
            }
            .padding(18)

            ZStack {
                Rectangle().fill(.white.opacity(0.15)).frame(height: 1)
                Button {
                    withAnimation(.snappy) { (book.from, book.to) = (book.to, book.from) }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.headline)
                        .foregroundStyle(SheetTheme.navyDeep)
                        .frame(width: 44, height: 44)
                        .background(.white, in: Circle())
                        .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
                }
                .accessibilityLabel("Swap currencies")
                .sensoryFeedback(.selection, trigger: book.from)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("You get").font(.caption.weight(.semibold)).foregroundStyle(.white.opacity(0.75))
                HStack(spacing: 12) {
                    CurrencyMenu(selection: $book.to, onDark: true)
                    Spacer()
                    Text(result.map { CurrencyBook.format($0) } ?? "—")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                        .contentTransition(.numericText())
                        .textSelection(.enabled)
                }
                HStack {
                    Text(book.name(book.to)).font(.caption).foregroundStyle(.white.opacity(0.7))
                    Spacer()
                    if let unit {
                        Text("1 \(book.from) = \(CurrencyBook.format(unit)) \(book.to)")
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }
                if result == nil {
                    let missing = book.rate(book.from) == nil ? book.from : book.to
                    Button {
                        editing = missing
                    } label: {
                        Label(book.isRefreshing ? "Downloading rates…" : "No rate for \(missing) yet — set one", systemImage: "pencil")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(.white.opacity(0.18), in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
            .padding(18)
        }
        .background(SheetTheme.heroGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .animation(.snappy, value: result)
    }

    private var quickAmounts: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach([1, 10, 50, 100, 500, 1000, 5000], id: \.self) { value in
                    Button(value.formatted()) { amountText = "\(value)"; amountFocused = false }
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(amount == Double(value) ? AnyShapeStyle(SheetTheme.navy) : AnyShapeStyle(SheetTheme.card), in: Capsule())
                        .foregroundStyle(amount == Double(value) ? .white : .primary)
                }
            }
        }
    }

    /// The user's starred currencies, each converted from the "You have" amount.
    private var favoritesCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Favourites").font(.headline)
                    Text(updatedText).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    Task { await book.refresh() }
                } label: {
                    if book.isRefreshing { ProgressView() } else { Image(systemName: "arrow.clockwise").font(.headline) }
                }
                .accessibilityLabel("Update rates")
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 8)

            ForEach(book.favorites.filter { $0 != book.from }, id: \.self) { code in
                Divider().padding(.leading, 60)
                Button { book.to = code } label: {
                    HStack(spacing: 12) {
                        Text(CurrencyBook.flag(code)).font(.title2).frame(width: 32)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(code).font(.subheadline.weight(.semibold))
                            Text(book.name(code)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(book.convert(amount, from: book.from, to: code).map { CurrencyBook.format($0) } ?? "—")
                                .font(.subheadline.weight(.bold).monospacedDigit())
                                .foregroundStyle(code == book.to ? SheetTheme.navy : .primary)
                            SourceLabel(source: book.source(code))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .contextMenu {
                    Button("Convert to \(code)", systemImage: "arrow.right") { book.to = code }
                    Button("Set my own rate", systemImage: "pencil") { editing = code }
                    Button("Remove from favourites", systemImage: "star.slash") { book.toggleFavorite(code) }
                }
            }

            Divider()
            HStack(spacing: 0) {
                Button { browsing = true } label: {
                    Label("All \(book.allCodes.count) currencies", systemImage: "globe")
                        .frame(maxWidth: .infinity)
                }
                Divider().frame(height: 24)
                Button { adding = true } label: {
                    Label("Custom rate", systemImage: "slider.horizontal.3")
                        .frame(maxWidth: .infinity)
                }
            }
            .font(.subheadline.weight(.semibold))
            .padding(.vertical, 14)
        }
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var updatedText: String {
        if book.isRefreshing { return "Updating rates…" }
        guard let date = book.liveDate else { return "Pull down to download live rates" }
        return "Rates from \(date.formatted(.relative(presentation: .named))) · long-press to edit"
    }
}

private struct CodeItem: Identifiable { let code: String; var id: String { code } }

private struct SourceLabel: View {
    let source: CurrencyBook.Source

    var body: some View {
        let (text, tint): (String, Color) = switch source {
        case .base: ("Base", .secondary)
        case .custom: ("Your rate", SheetTheme.purple)
        case .sheet: ("From your sheet", SheetTheme.good)
        case .live: ("Live", SheetTheme.sky)
        case .peg: ("Fixed peg", .secondary)
        case .none: ("No rate", SheetTheme.warn)
        }
        Text(text).font(.caption2.weight(.semibold)).foregroundStyle(tint)
    }
}

// MARK: - Picking a currency

/// A flag + code pill that opens the searchable list of every currency.
struct CurrencyMenu: View {
    @Binding var selection: String
    var onDark = false
    @State private var picking = false

    var body: some View {
        Button { picking = true } label: {
            HStack(spacing: 6) {
                Text(CurrencyBook.flag(selection))
                Text(selection).font(.headline)
                Image(systemName: "chevron.down").font(.caption2.weight(.bold))
            }
            .foregroundStyle(onDark ? .white : .primary)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(onDark ? .white.opacity(0.18) : SheetTheme.input, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Currency \(selection). Change")
        .sheet(isPresented: $picking) { CurrencyPickerSheet(selection: $selection) }
    }
}

/// Every currency, searchable by code, name or country, with favourites first.
struct CurrencyPickerSheet: View {
    @Binding var selection: String
    var title = "Choose currency"

    @State private var book = CurrencyBook.shared
    @State private var search = ""
    @State private var editing: String?
    @Environment(\.dismiss) private var dismiss

    private func matches(_ code: String) -> Bool {
        let query = search.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return true }
        return code.localizedCaseInsensitiveContains(query) || book.name(code).localizedCaseInsensitiveContains(query)
    }

    var body: some View {
        NavigationStack {
            List {
                let favorites = book.favorites.filter(matches)
                if !favorites.isEmpty {
                    Section("Favourites") {
                        ForEach(favorites, id: \.self) { row($0) }
                    }
                }
                let others = book.allCodes.filter { !book.isFavorite($0) && matches($0) }
                if !others.isEmpty {
                    Section(search.isEmpty ? "All currencies" : "Results") {
                        ForEach(others, id: \.self) { row($0) }
                    }
                }
                if favorites.isEmpty && others.isEmpty {
                    ContentUnavailableView.search(text: search)
                }
            }
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search code or name, e.g. peso")
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
            .task { await book.refreshIfStale() }
            .sheet(item: Binding(get: { editing.map(CodeItem.init) }, set: { editing = $0?.code })) { RateEditor(code: $0.code) }
        }
        .tint(SheetTheme.navy)
    }

    private func row(_ code: String) -> some View {
        Button {
            selection = code
            dismiss()
        } label: {
            HStack(spacing: 12) {
                Text(CurrencyBook.flag(code)).font(.title2).frame(width: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(code).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                    Text(book.name(code)).font(.caption).foregroundStyle(Color(.secondaryLabel)).lineLimit(1)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    if let rate = book.rate(code), code != book.main {
                        Text("1 \(book.main) = \(CurrencyBook.format(rate))").font(.caption.monospacedDigit()).foregroundStyle(Color(.secondaryLabel))
                    }
                    if code != book.main { SourceLabel(source: book.source(code)) }
                }
                if code == selection {
                    Image(systemName: "checkmark").font(.subheadline.weight(.bold)).foregroundStyle(SheetTheme.navy)
                }
            }
        }
        .swipeActions(edge: .trailing) {
            Button(book.isFavorite(code) ? "Unstar" : "Star", systemImage: book.isFavorite(code) ? "star.slash" : "star.fill") {
                book.toggleFavorite(code)
            }
            .tint(.yellow)
            if code != book.main {
                Button("Rate", systemImage: "pencil") { editing = code }.tint(SheetTheme.purple)
            }
        }
        .contextMenu {
            Button(book.isFavorite(code) ? "Remove from favourites" : "Add to favourites",
                   systemImage: book.isFavorite(code) ? "star.slash" : "star") { book.toggleFavorite(code) }
            if code != book.main { Button("Set my own rate", systemImage: "pencil") { editing = code } }
        }
    }
}

// MARK: - Own rates

/// Set your own rate for a currency (entered either way round), or create a
/// custom currency when `code` is nil.
private struct RateEditor: View {
    let code: String?

    @State private var book = CurrencyBook.shared
    @State private var newCode = ""
    @State private var newName = ""
    @State private var perMain: Double?
    @State private var inMain: Double?
    @Environment(\.dismiss) private var dismiss

    init(code: String?) {
        self.code = code
        let rate = code.flatMap { CurrencyBook.shared.rate($0) }
        _perMain = State(initialValue: rate)
        _inMain = State(initialValue: rate.map { 1 / $0 })
    }

    private var effectiveCode: String { (code ?? newCode).trimmingCharacters(in: .whitespaces).uppercased() }
    private var label: String { effectiveCode.isEmpty ? "?" : effectiveCode }
    private var isHomeFromSheet: Bool { effectiveCode == Money.home && book.useSheetRateForHome && book.sheetRate != nil }
    private var hasOverride: Bool { code.map { book.source($0) == .custom } ?? false }
    private var canSave: Bool { effectiveCode.count >= 2 && (perMain ?? 0) > 0 }

    var body: some View {
        NavigationStack {
            Form {
                if code == nil {
                    Section {
                        TextField("Code, e.g. GOLD or PHP", text: $newCode)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                        TextField("Name (optional)", text: $newName)
                    } header: {
                        Text("Currency")
                    } footer: {
                        Text("Use this for a rate you got from a money exchange, or anything not in the list.")
                    }
                } else if let code {
                    Section {
                        HStack(spacing: 12) {
                            Text(CurrencyBook.flag(code)).font(.largeTitle)
                            VStack(alignment: .leading) {
                                Text(code).font(.headline)
                                Text(book.name(code)).font(.subheadline).foregroundStyle(.secondary)
                            }
                            Spacer()
                            SourceLabel(source: book.source(code))
                        }
                    }
                }
                if effectiveCode == Money.home, let sheetRate = book.sheetRate {
                    Section {
                        Toggle("Use the sheet's rate", isOn: Binding(get: { book.useSheetRateForHome }, set: { book.useSheetRateForHome = $0 }))
                    } footer: {
                        Text("Your sheet says 1 \(Money.main) = \(SheetFormat.number(sheetRate, digits: 2)) \(Money.home). Change it in Settings to update every \(Money.home) figure in the sheet.")
                    }
                }
                Section {
                    RateField(prefix: "1 \(book.main) =", suffix: label, value: $perMain) { value in
                        inMain = value.map { $0 > 0 ? 1 / $0 : 0 }
                    }
                    RateField(prefix: "1 \(label) =", suffix: book.main, value: $inMain) { value in
                        perMain = value.map { $0 > 0 ? 1 / $0 : 0 }
                    }
                } header: {
                    Text("My rate")
                } footer: {
                    Text("Fill either line — the other updates itself.")
                }
                .disabled(isHomeFromSheet && !hasOverride)

                if hasOverride, let code {
                    Section {
                        Button("Go back to the \(code == Money.home && book.sheetRate != nil ? "sheet" : "live") rate", role: .destructive) {
                            book.setOverride(code, perMain: nil)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(code == nil ? "Custom rate" : "Set rate")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if !(isHomeFromSheet && !hasOverride) {
                            book.setOverride(effectiveCode, perMain: perMain, name: newName.trimmingCharacters(in: .whitespaces))
                        }
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(!canSave && !isHomeFromSheet)
                }
            }
        }
        .tint(SheetTheme.navy)
    }
}

private struct RateField: View {
    let prefix: String
    let suffix: String
    @Binding var value: Double?
    let onEdit: (Double?) -> Void

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack {
            Text(prefix).foregroundStyle(.secondary)
            TextField("0", text: $text)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .font(.body.monospacedDigit().weight(.semibold))
                .focused($focused)
            Text(suffix).font(.subheadline.weight(.semibold))
        }
        .onAppear { text = display(value) }
        .onChange(of: text) { _, new in
            guard focused else { return }
            let parsed = Double(new.replacingOccurrences(of: ",", with: "."))
            value = parsed
            onEdit(parsed)
        }
        .onChange(of: value) { _, new in
            if !focused { text = display(new) }
        }
    }

    private func display(_ value: Double?) -> String {
        value.map { $0.formatted(.number.precision(.fractionLength(0...6)).grouping(.never)) } ?? ""
    }
}
