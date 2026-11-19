//
//  UpiPay.swift
//  MyPersonalTracker
//
//  Pay with UPI and log it in one go: scan the shop's QR (or type a UPI ID),
//  enter the amount, pick PhonePe / Google Pay / Paytm / CRED…, pay there and
//  come back. The UPI apps don't tell an iPhone app how it went, so the app
//  asks — on return, and in a notification with Paid / Failed buttons — and a
//  paid one goes into Finance by itself. Payments stay on this phone.
//

import SwiftUI
import UIKit
import UserNotifications

/// Who you're paying: a UPI ID (VPA) and the name to show.
struct UpiPayee: Codable, Hashable {
    var vpa: String
    var name: String
    /// Merchant category code from a shop's QR (helps guess the category).
    var mcc: String?

    var label: String { name.isEmpty ? vpa : name }

    /// "name@bank": letters, digits, . - _ before the @, a handle after it.
    static func isValidVPA(_ text: String) -> Bool {
        text.range(of: #"^[A-Za-z0-9.\-_]{2,256}@[A-Za-z][A-Za-z0-9.\-]{1,63}$"#, options: .regularExpression) != nil
    }
}

/// What a UPI QR (or a pasted "upi://pay?…" link) asks for.
struct UpiScan: Equatable {
    var payee: UpiPayee
    var amount: Double?
    var note: String?

    /// Reads "upi://pay?pa=shop@okicici&pn=Shop&am=120&tn=Lunch&mc=5812", or a bare UPI ID.
    static func parse(_ text: String) -> UpiScan? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if UpiPayee.isValidVPA(trimmed) { return UpiScan(payee: UpiPayee(vpa: trimmed, name: "")) }
        guard let components = URLComponents(string: trimmed), components.scheme?.lowercased() == "upi" else { return nil }
        func value(_ key: String) -> String? {
            components.queryItems?.first { $0.name.lowercased() == key }?.value?.trimmingCharacters(in: .whitespaces).nilIfEmpty
        }
        guard let vpa = value("pa"), UpiPayee.isValidVPA(vpa) else { return nil }
        let amount = value("am").flatMap(Double.init).flatMap { $0 > 0 && $0.isFinite ? $0 : nil }
        return UpiScan(payee: UpiPayee(vpa: vpa, name: value("pn") ?? "", mcc: value("mc")), amount: amount, note: value("tn"))
    }
}

/// The UPI apps an iPhone can hand a payment to. An iPhone app can only see another app through
/// its link scheme, and only schemes listed in Info.plist (LSApplicationQueriesSchemes) — these
/// are the ones payment gateways publish for UPI on iOS. Each has a tile, "upi-<case>", in Assets ▸ UPI.
enum UpiApp: String, Codable, CaseIterable, Identifiable {
    case phonePe, googlePay, paytm, cred, bhim, amazonPay, whatsApp, navi, mobikwik, airtel, superMoney,
         pop, kiwi, simplyPay, tataNeu, bharatPe, iMobile, payZapp, famPay, snapmint, anyApp
    var id: Self { self }

    var title: String {
        switch self {
        case .phonePe: "PhonePe"
        case .googlePay: "Google Pay"
        case .paytm: "Paytm"
        case .cred: "CRED"
        case .bhim: "BHIM"
        case .amazonPay: "Amazon Pay"
        case .whatsApp: "WhatsApp"
        case .navi: "Navi"
        case .mobikwik: "MobiKwik"
        case .airtel: "Airtel"
        case .superMoney: "super.money"
        case .pop: "POP"
        case .kiwi: "Kiwi"
        case .simplyPay: "SimplyPay"
        case .tataNeu: "Tata Neu"
        case .bharatPe: "BharatPe"
        case .iMobile: "iMobile Pay"
        case .payZapp: "PayZapp"
        case .famPay: "FamPay"
        case .snapmint: "Snapmint"
        case .anyApp: "Other UPI app"
        }
    }

    /// Only for a tile missing from the asset catalog.
    var tint: Color { self == .anyApp ? SheetTheme.navy : Color(.darkGray) }
    var monogram: String { self == .anyApp ? "UPI" : String(title.prefix(1)) }

    /// Each app's own links for a UPI payment, tried in order (the scheme of each is in Info.plist).
    /// The first five are long-standing; the rest follow the usual "<scheme>://upi/pay" form.
    var prefixes: [String] {
        switch self {
        case .phonePe: ["phonepe://pay"]
        case .googlePay: ["tez://upi/pay", "gpay://upi/pay"]
        case .paytm: ["paytmmp://pay"]
        case .cred: ["credpay://upi/pay"]
        case .bhim: ["bhim://pay"]
        case .amazonPay: ["amazonpay://upi/pay"]
        case .whatsApp: ["whatsapp-consumer://upi/pay"]
        case .navi: ["navipay://upi/pay"]
        case .mobikwik: ["mobikwik://upi/pay"]
        case .airtel: ["myairtel://upi/pay"]
        case .superMoney: ["super://upi/pay"]
        case .pop: ["popclubapp://upi/pay"]
        case .kiwi: ["kiwi://upi/pay"]
        case .simplyPay: ["simplypayupi://upi/pay"]
        case .tataNeu: ["tnupi://upi/pay"]
        case .bharatPe: ["postpe://upi/pay"]
        case .iMobile: ["imobile://upi/pay"]
        case .payZapp: ["payzapp://upi/pay"]
        case .famPay: ["fpupi://upi/pay"]
        case .snapmint: ["snapmint://upi/pay"]
        case .anyApp: ["upi://pay"]
        }
    }

    /// The first of its links this iPhone can open (the first one when none can).
    @MainActor private var prefix: String {
        prefixes.first { URL(string: $0 + "?pa=test@upi").map(UIApplication.shared.canOpenURL) ?? false } ?? prefixes[0]
    }

    @MainActor func url(_ request: UpiRequest) -> URL? {
        var components = URLComponents(string: prefix)
        components?.queryItems = request.queryItems
        return components?.url
    }

    /// Every app, always: the ones found on this iPhone first, then the rest, then "Other UPI app".
    static func listing(found: Set<UpiApp>) -> [UpiApp] {
        let apps = allCases.filter { $0 != .anyApp }
        return apps.filter(found.contains) + apps.filter { !found.contains($0) } + [.anyApp]
    }

    @MainActor var isInstalled: Bool {
        prefixes.contains { URL(string: $0 + "?pa=test@upi").map(UIApplication.shared.canOpenURL) ?? false }
    }
}

/// One payment to hand to a UPI app.
struct UpiRequest: Equatable {
    var payee: UpiPayee
    /// Rupees; UPI pays in INR only.
    var amount: Double
    var note: String
    /// Our reference, so the payment can be matched up.
    var reference: String

    var queryItems: [URLQueryItem] {
        var items = [URLQueryItem(name: "pa", value: payee.vpa),
                     URLQueryItem(name: "pn", value: payee.name.isEmpty ? payee.vpa : payee.name),
                     URLQueryItem(name: "am", value: String(format: "%.2f", amount)),
                     URLQueryItem(name: "cu", value: "INR"),
                     URLQueryItem(name: "tr", value: reference)]
        if !note.isEmpty { items.append(URLQueryItem(name: "tn", value: String(note.prefix(50)))) }
        if let mcc = payee.mcc { items.append(URLQueryItem(name: "mc", value: mcc)) }
        return items
    }
}

/// What Pay with UPI says to keep a payment safe. Mirrors UpiSafety in UpiPay.kt.
enum UpiSafety {
    static let pin = "Enter your UPI PIN only in your UPI or bank app — never on a website, in a chat or on a call."
    static let qrName = "This name came from the QR code, which anyone can print. Before entering your PIN, check the name your UPI app shows — that one comes from the bank."
    /// iOS lets any app claim a UPI app's link, and doesn't say which one answered.
    static let iPhoneLinks = "Check that the app that opens is the one you picked before you enter your PIN."
}

/// A payment made from here, kept on this phone.
struct UpiPayment: Codable, Identifiable, Hashable {
    enum Status: String, Codable { case waiting, paid, failed }

    var id: String
    var date: Date
    var payee: UpiPayee
    var amount: Double
    var note: String
    var category: String
    var app: UpiApp
    var status: Status
    /// The expense it became in Finance (paid only).
    var loggedAmount: Double?
}

@MainActor @Observable
final class UpiPayments {
    static let shared = UpiPayments()

    private(set) var payments: [UpiPayment] = []
    /// Waiting for "Paid?" — asked when you come back to the app.
    var asking: UpiPayment?

    static let categoryId = "UPI_CONFIRM"
    static let paidAction = "UPI_PAID"
    static let failedAction = "UPI_FAILED"
    /// Where earlier versions kept them (UserDefaults, which backups include).
    private static let legacyKey = "upi.payments"
    /// Application Support/upi-payments.json, left out of backups.
    nonisolated static var defaultStoreURL: URL { URL.applicationSupportDirectory.appending(path: "upi-payments.json") }

    private let storeURL: URL

    /// Who you paid and how much stays on this iPhone: not in iCloud or computer backups.
    init(storeURL: URL = UpiPayments.defaultStoreURL, defaults: UserDefaults = .standard) {
        self.storeURL = storeURL
        if let data = try? Data(contentsOf: storeURL), let list = try? JSONDecoder().decode([UpiPayment].self, from: data) {
            payments = list
        } else if let data = defaults.data(forKey: Self.legacyKey), let list = try? JSONDecoder().decode([UpiPayment].self, from: data) {
            payments = list
            if save() { defaults.removeObject(forKey: Self.legacyKey) }
        }
    }

    var waiting: [UpiPayment] { payments.filter { $0.status == .waiting } }

    /// The people and shops paid before, most recent first.
    var recentPayees: [UpiPayee] {
        var seen = Set<String>()
        return payments.compactMap { seen.insert($0.payee.vpa.lowercased()).inserted ? $0.payee : nil }.prefix(8).map { $0 }
    }

    /// The category last used for this payee, if any.
    func lastCategory(for vpa: String) -> String? {
        payments.first { $0.payee.vpa.caseInsensitiveCompare(vpa) == .orderedSame && $0.status == .paid }?.category
    }

    /// Opens the UPI app; the payment waits for "Paid" or "Failed".
    func pay(_ request: UpiRequest, category: String, with app: UpiApp) async -> Bool {
        guard let url = app.url(request) else { return false }
        let payment = UpiPayment(id: request.reference, date: .now, payee: request.payee, amount: request.amount, note: request.note,
                                 category: category, app: app, status: .waiting)
        payments.insert(payment, at: 0)
        save()
        guard await UIApplication.shared.open(url) else {
            payments.removeAll { $0.id == payment.id }
            save()
            return false
        }
        scheduleQuestion(payment)
        return true
    }

    /// Back in the app: ask about the latest payment still waiting.
    func askIfWaiting() {
        guard asking == nil, let latest = waiting.first, latest.date.timeIntervalSinceNow > -86_400 else { return }
        asking = latest
    }

    /// Paid: into Finance (in your main currency) and a notification saying so.
    func markPaid(_ id: String) {
        guard let i = payments.firstIndex(where: { $0.id == id }), payments[i].status == .waiting else { return }
        let p = payments[i]
        let store = SheetStore.shared
        let main = Money.main
        let amount = main == "INR" ? p.amount : CurrencyBook.shared.convert(p.amount, from: "INR", to: main)
        if let amount {
            let methods = store.snapshot?.lists.methods ?? []
            let method = methods.first { $0.localizedCaseInsensitiveContains("UPI") } ?? methods.first { $0 == "Bank Transfer" } ?? methods.first ?? "Other"
            let rupees = "₹" + SheetFormat.number(p.amount, digits: p.amount.rounded() == p.amount ? 0 : 2)
            let notes = "UPI · \(p.app.title)" + (main == "INR" ? "" : " · \(rupees)") + (p.note.isEmpty ? "" : " · \(p.note)")
            Task {
                await store.saveExpense(row: nil, date: SheetDate.string(p.date), description: p.payee.label, category: p.category,
                                        method: method, qar: (amount * 100).rounded() / 100, notes: notes)
            }
            payments[i].loggedAmount = amount
        }
        payments[i].status = .paid
        save()
        resolve(p, paid: true, logged: amount != nil)
    }

    func markFailed(_ id: String) {
        guard let i = payments.firstIndex(where: { $0.id == id }), payments[i].status == .waiting else { return }
        payments[i].status = .failed
        save()
        resolve(payments[i], paid: false, logged: false)
    }

    func delete(_ id: String) {
        payments.removeAll { $0.id == id }
        save()
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["upi-ask-" + id])
    }

    // MARK: - Notifications

    static func registerCategory() {
        let paid = UNNotificationAction(identifier: paidAction, title: "Paid ✓", options: [])
        let failed = UNNotificationAction(identifier: failedAction, title: "It failed", options: [.destructive])
        let category = UNNotificationCategory(identifier: categoryId, actions: [paid, failed], intentIdentifiers: [])
        let center = UNUserNotificationCenter.current()
        center.getNotificationCategories { existing in
            center.setNotificationCategories(existing.filter { $0.identifier != categoryId }.union([category]))
        }
    }

    /// If you don't come back to the app, a notification asks (with Paid / Failed buttons).
    private func scheduleQuestion(_ p: UpiPayment) {
        Task {
            _ = await ReminderScheduler.requestAuthorization()
            let content = UNMutableNotificationContent()
            content.title = "Did your payment go through?"
            content.body = "\(rupees(p.amount)) to \(p.payee.label) with \(p.app.title). Tap Paid to add it to Finance."
            content.categoryIdentifier = Self.categoryId
            content.userInfo = ["upiPayment": p.id]
            content.sound = .default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 25, repeats: false)
            try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "upi-ask-" + p.id, content: content, trigger: trigger))
        }
    }

    /// The result, always as a notification too.
    private func resolve(_ p: UpiPayment, paid: Bool, logged: Bool) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ["upi-ask-" + p.id])
        center.removeDeliveredNotifications(withIdentifiers: ["upi-ask-" + p.id])
        if asking?.id == p.id { asking = nil }
        let content = UNMutableNotificationContent()
        if paid {
            content.title = "Paid \(rupees(p.amount)) ✓"
            content.body = logged ? "To \(p.payee.label) — added to Finance as \(p.category)."
                                  : "To \(p.payee.label). Add it in Finance: there's no INR rate yet to convert it."
        } else {
            content.title = "Payment failed"
            content.body = "\(rupees(p.amount)) to \(p.payee.label) didn't go through — nothing was added to Finance."
        }
        content.userInfo = ["open": "finance"]
        Task { try? await center.add(UNNotificationRequest(identifier: "upi-result-" + p.id, content: content, trigger: nil)) }
    }

    private func rupees(_ amount: Double) -> String { "₹" + SheetFormat.number(amount, digits: amount.rounded() == amount ? 0 : 2) }

    @discardableResult
    private func save() -> Bool {
        payments = Array(payments.prefix(60))
        do {
            try FileManager.default.createDirectory(at: storeURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(payments).write(to: storeURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            var url = storeURL
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try url.setResourceValues(values)
            return true
        } catch {
            return false
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
