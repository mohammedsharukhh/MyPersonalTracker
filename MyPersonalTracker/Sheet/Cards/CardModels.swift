//
//  CardModels.swift
//  MyPersonalTracker
//
//  More ▸ Cards: your debit and credit cards, kept on this iPhone only — in a
//  Keychain item that only Face ID or the passcode opens, never sent to the Google
//  Sheet, left out of Export everything and of backups to other devices.
//

import Foundation
import LocalAuthentication

struct SavedCard: Codable, Identifiable, Hashable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case credit = "Credit", debit = "Debit", prepaid = "Prepaid", forex = "Forex"
        var id: Self { self }
    }

    /// The card's colour in the app (white text sits on all of them).
    enum Style: String, Codable, CaseIterable, Identifiable {
        case navy, teal, purple, graphite, gold, rose
        var id: Self { self }
        var hex: (UInt32, UInt32) {
            switch self {
            case .navy: (0x1F3864, 0x2F5597)
            case .teal: (0x0F5132, 0x0F766E)
            case .purple: (0x4C1D95, 0x7C3AED)
            case .graphite: (0x1F2937, 0x4B5563)
            case .gold: (0x78350F, 0xB45309)
            case .rose: (0x881337, 0xBE123C)
            }
        }
    }

    var id = UUID()
    var nickname = ""
    var bank = ""
    var kind = Kind.credit
    /// Digits only.
    var number = ""
    var holder = ""
    var expiryMonth: Int?
    /// Four digits, e.g. 2029.
    var expiryYear: Int?
    var cvv = ""
    var notes = ""
    var style = Style.navy

    var brand: CardBrand { CardBrand(number: number) }
    var last4: String { String(number.suffix(4)) }

    /// "HDFC Millennia", else "HDFC Visa", else "Visa credit card".
    var title: String {
        let name = nickname.trimmingCharacters(in: .whitespaces)
        if !name.isEmpty { return name }
        let bankName = bank.trimmingCharacters(in: .whitespaces)
        let brandName = brand == .unknown ? "" : brand.name
        if !bankName.isEmpty { return [bankName, brandName].filter { !$0.isEmpty }.joined(separator: " ") }
        return [brandName, "\(kind.rawValue.lowercased()) card"].filter { !$0.isEmpty }.joined(separator: " ")
    }

    var maskedNumber: String { number.count >= 4 ? "•••• \(last4)" : "••••" }
    var groupedNumber: String { CardFormat.grouped(number) }

    /// "08/29", or "" when not set.
    var expiryText: String {
        guard let expiryMonth, let expiryYear else { return "" }
        return String(format: "%02d/%02d", expiryMonth, expiryYear % 100)
    }

    enum ExpiryState: Equatable { case fine, soon, expired }

    /// Soon is within 60 days of the last day of its month.
    func expiryState(on date: Date = .now, calendar: Calendar = .current) -> ExpiryState {
        guard let expiryMonth, let expiryYear,
              let firstOfMonth = calendar.date(from: DateComponents(year: expiryYear, month: expiryMonth, day: 1)),
              let firstAfter = calendar.date(byAdding: .month, value: 1, to: firstOfMonth) else { return .fine }
        let today = calendar.startOfDay(for: date)
        if today >= firstAfter { return .expired }
        let days = calendar.dateComponents([.day], from: today, to: firstAfter).day ?? .max
        return days <= 60 ? .soon : .fine
    }

    /// What VoiceOver reads for a card in the list.
    var spokenSummary: String {
        var parts = [title, "\(kind.rawValue) card"]
        if number.count >= 4 { parts.append("ending \(last4.map(String.init).joined(separator: " "))") }
        if !expiryText.isEmpty { parts.append("expires \(expiryText)") }
        switch expiryState() {
        case .expired: parts.append("expired")
        case .soon: parts.append("expires soon")
        case .fine: break
        }
        return parts.joined(separator: ", ")
    }
}

enum CardBrand: String, CaseIterable {
    case visa, mastercard, amex, rupay, discover, diners, jcb, maestro, unknown

    /// From the card's first digits.
    init(number: String) {
        let d = number.filter(\.isNumber)
        func prefix(_ length: Int) -> Int? { d.count >= length ? Int(d.prefix(length)) : nil }
        func starts(_ values: String...) -> Bool { values.contains { d.hasPrefix($0) } }
        func within(_ length: Int, _ range: ClosedRange<Int>) -> Bool { prefix(length).map(range.contains) ?? false }

        if starts("34", "37") { self = .amex }
        else if starts("4") { self = .visa }
        else if within(2, 51...55) || within(4, 2221...2720) { self = .mastercard }
        else if starts("6011") || within(3, 644...649) { self = .discover }
        else if starts("60", "81", "82", "508", "6521", "6522") { self = .rupay }
        else if starts("65") { self = .discover }
        else if starts("36", "38", "39") || within(3, 300...305) { self = .diners }
        else if within(4, 3528...3589) { self = .jcb }
        else if starts("50", "56", "57", "58", "63", "67") { self = .maestro }
        else { self = .unknown }
    }

    var name: String {
        switch self {
        case .visa: "Visa"
        case .mastercard: "Mastercard"
        case .amex: "American Express"
        case .rupay: "RuPay"
        case .discover: "Discover"
        case .diners: "Diners Club"
        case .jcb: "JCB"
        case .maestro: "Maestro"
        case .unknown: ""
        }
    }

    /// The wordmark-style label on the card face.
    var mark: String {
        switch self {
        case .amex: "AMEX"
        case .diners: "DINERS"
        case .unknown: ""
        default: name.uppercased()
        }
    }

    var maxLength: Int {
        switch self {
        case .amex: 15
        case .diners: 14
        default: 19
        }
    }

    var cvvLength: Int { self == .amex ? 4 : 3 }
}

enum CardFormat {
    /// "4111 1111 1111 1111"; American Express "3782 822463 10005"; Diners "3056 930902 5904".
    static func grouped(_ number: String) -> String {
        let digits = Array(number.filter(\.isNumber))
        let sizes: [Int]
        switch CardBrand(number: number) {
        case .amex: sizes = [4, 6, 5]
        case .diners where digits.count <= 14: sizes = [4, 6, 4]
        default: sizes = Array(repeating: 4, count: 5)
        }
        var groups: [String] = []
        var index = 0
        for size in sizes where index < digits.count {
            let end = min(index + size, digits.count)
            groups.append(String(digits[index..<end]))
            index = end
        }
        if index < digits.count { groups.append(String(digits[index...])) }
        return groups.joined(separator: " ")
    }

    /// The Luhn check every real card number passes (catches most typos).
    static func passesLuhn(_ number: String) -> Bool {
        let digits = number.compactMap(\.wholeNumberValue)
        guard digits.count >= 12 else { return false }
        let sum = digits.reversed().enumerated().reduce(0) { total, pair in
            let (i, digit) = pair
            guard i % 2 == 1 else { return total + digit }
            let doubled = digit * 2
            return total + (doubled > 9 ? doubled - 9 : doubled)
        }
        return sum % 10 == 0
    }

    /// "0829" or "08/29" → (8, 2029). Nil unless the month is 1–12.
    static func expiry(_ text: String) -> (month: Int, year: Int)? {
        let digits = text.filter(\.isNumber)
        guard digits.count == 4, let month = Int(digits.prefix(2)), let year = Int(digits.suffix(2)), (1...12).contains(month) else { return nil }
        return (month, 2000 + year)
    }

    /// What the expiry field shows while typing: "0", "08", "08/2", "08/29".
    static func expiryField(_ text: String) -> String {
        let digits = String(text.filter(\.isNumber).prefix(4))
        return digits.count > 2 ? "\(digits.prefix(2))/\(digits.dropFirst(2))" : digits
    }
}

enum CardKeychainError: Error { case notConfirmed, failed(OSStatus) }

/// Where the cards are kept: the Keychain on the iPhone, a stand-in in tests.
protocol CardKeychain {
    /// The saved cards, read with the context Face ID or the passcode just unlocked. Nil: none saved.
    func read(context: LAContext?) throws -> Data?
    /// Nil deletes them.
    func write(_ data: Data?, context: LAContext?) -> Bool
    /// What earlier versions saved, without the Face ID / passcode binding.
    func readLegacy() -> Data?
    func deleteLegacy()
}

/// One Keychain item that only Face ID, Touch ID or the passcode opens — not
/// merely an unlocked iPhone — so nothing in the app can read the cards without
/// the person. iOS deletes it if the passcode is turned off.
struct KeychainCardStore: CardKeychain {
    static let accessible = kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
    static let flags: SecAccessControlCreateFlags = .userPresence
    private static let service = "MyPersonalTracker"
    private static let legacyAccount = "savedCards"

    var account = "savedCards.locked"

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: Self.service, kSecAttrAccount as String: account]
    }

    private func query(_ context: LAContext?) -> [String: Any] {
        var query = query
        if let context { query[kSecUseAuthenticationContext as String] = context }
        return query
    }

    func read(context: LAContext?) throws -> Data? {
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query(context).merging([kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]) { $1 } as CFDictionary, &result)
        switch status {
        case errSecSuccess: return result as? Data
        case errSecItemNotFound: return nil
        case errSecUserCanceled, errSecAuthFailed, errSecInteractionNotAllowed: throw CardKeychainError.notConfirmed
        default: throw CardKeychainError.failed(status)
        }
    }

    func write(_ data: Data?, context: LAContext?) -> Bool {
        guard let data else {
            let status = SecItemDelete(query as CFDictionary)
            return status == errSecSuccess || status == errSecItemNotFound
        }
        // In place, so a failed write never loses what was there.
        let status = SecItemUpdate(query(context) as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecSuccess { return true }
        guard status == errSecItemNotFound else { return false }
        var item = query(context)
        item[kSecValueData as String] = data
        #if targetEnvironment(simulator)
        // The simulator has no passcode, so an item bound to one can't be made there.
        item[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        #else
        guard let access = SecAccessControlCreateWithFlags(nil, Self.accessible, Self.flags, nil) else { return false }
        item[kSecAttrAccessControl as String] = access
        #endif
        return SecItemAdd(item as CFDictionary, nil) == errSecSuccess
    }

    func readLegacy() -> Data? { Keychain.get(Self.legacyAccount).map { Data($0.utf8) } }
    func deleteLegacy() { Keychain.set("", for: Self.legacyAccount) }
}

/// The saved cards, read from the Keychain only after Face ID or the passcode,
/// and dropped from memory (with the unlocked context) again when locked.
@Observable
final class CardVault {
    static let shared = CardVault()

    /// How long the app can be in the background before the cards lock again
    /// (long enough to copy a number into another app and come back for the next one).
    private static let backgroundGrace: TimeInterval = 30

    private(set) var cards: [SavedCard] = []
    private(set) var isUnlocked = false
    var errorMessage: String?
    private var backgroundedAt: Date?
    /// The Face ID / passcode check that opened the Keychain item; reused to save, invalidated on lock.
    private var context: LAContext?
    @ObservationIgnored private let keychain: CardKeychain
    @ObservationIgnored private let now: () -> Date

    init(keychain: CardKeychain = KeychainCardStore(), now: @escaping () -> Date = { .now }) {
        self.keychain = keychain
        self.now = now
    }

    /// Face ID, Touch ID or the passcode, as the device has.
    var unlockSymbol: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        case .opticID: return "opticid"
        default: return "lock.open.fill"
        }
    }

    func unlock() async {
        guard !isUnlocked else { return }
        errorMessage = nil
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-cardsDemo") { loadDemo(); return }
        #endif
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            #if DEBUG && targetEnvironment(simulator)
            unlocked(with: nil)   // The simulator usually has no passcode to ask for.
            #else
            errorMessage = "Set a passcode for this iPhone in Settings ▸ Face ID & Passcode first. Cards only open behind it."
            #endif
            return
        }
        do {
            if try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Show your saved cards") { unlocked(with: context) }
        } catch let error as LAError where [.userCancel, .appCancel, .systemCancel].contains(error.code) {
            // Closed the prompt: stay locked, say nothing.
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func lock() {
        cards = []
        isUnlocked = false
        backgroundedAt = nil
        context?.invalidate()
        context = nil
    }

    func didEnterBackground() { if isUnlocked { backgroundedAt = now() } }

    func didBecomeActive() {
        if let backgroundedAt, now().timeIntervalSince(backgroundedAt) > Self.backgroundGrace { lock() }
        backgroundedAt = nil
    }

    /// Adds or replaces it by id. False (with a message) if the Keychain refused.
    @discardableResult
    func save(_ card: SavedCard) -> Bool {
        var list = cards
        if let index = list.firstIndex(where: { $0.id == card.id }) { list[index] = card } else { list.append(card) }
        return write(list)
    }

    func delete(id: SavedCard.ID) {
        write(cards.filter { $0.id != id })
    }

    /// Reads the cards with the context Face ID or the passcode just unlocked
    /// (nil only for the simulator, which has no passcode).
    func unlocked(with context: LAContext?) {
        do {
            var data = try keychain.read(context: context)
            var fromLegacy = false
            if data == nil, let legacy = keychain.readLegacy() { data = legacy; fromLegacy = true }
            let list = try data.map { try JSONDecoder().decode([SavedCard].self, from: $0) } ?? []
            // Saved by an earlier version: into the item that needs Face ID; the old one goes once that's saved.
            if fromLegacy, let data, keychain.write(data, context: context) { keychain.deleteLegacy() }
            self.context = context
            cards = list
            isUnlocked = true
            errorMessage = nil
        } catch CardKeychainError.notConfirmed {
            errorMessage = "Unlock with Face ID or your passcode to see your cards."
        } catch {
            // Never open (and later overwrite) a list that didn't read back whole.
            errorMessage = "Your saved cards couldn't be read."
        }
    }

    #if DEBUG
    /// `-cardsDemo`: test numbers, in memory only (screenshots).
    private func loadDemo() {
        if cards.isEmpty {
            cards = [
                SavedCard(nickname: "Millennia", bank: "HDFC", number: "4111111111111111", holder: "Mohammed Sharuk",
                          expiryMonth: 8, expiryYear: 2029, cvv: "123", notes: "Bill on the 5th · limit 2,00,000"),
                SavedCard(bank: "SBI", kind: .debit, number: "6521000000000006", holder: "Mohammed Sharuk",
                          expiryMonth: 11, expiryYear: 2026, cvv: "456", style: .teal),
                SavedCard(nickname: "Travel", bank: "Amex", number: "378282246310005", holder: "Mohammed Sharuk",
                          expiryMonth: 3, expiryYear: 2031, cvv: "7890", style: .graphite),
            ]
        }
        isUnlocked = true
    }
    #endif

    @discardableResult
    private func write(_ list: [SavedCard]) -> Bool {
        guard isUnlocked else { return false }
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-cardsDemo") { cards = list; return true }
        #endif
        guard let data = try? JSONEncoder().encode(list), keychain.write(list.isEmpty ? nil : data, context: context) else {
            errorMessage = "The card couldn't be saved. Try again."
            return false
        }
        cards = list
        return true
    }
}
