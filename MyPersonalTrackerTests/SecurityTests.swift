//
//  SecurityTests.swift
//  MyPersonalTrackerTests
//
//  What could leak: the API key in links, payment history in backups, and what
//  Pay with UPI warns about. Mirrors SecretsTest.kt.
//

import Foundation
import Testing
@testable import MyPersonalTracker

@Suite("Security", .serialized)
@MainActor
struct SecurityTests {
    @Test func webAppRequestsKeepTheKeyOutOfTheLink() throws {
        let url = try #require(URL(string: "https://script.google.com/macros/s/abc/exec"))
        let request = try SheetStore.webAppRequest(url: url, key: "k3y", action: "photo", fields: ["id": "abc", "size": "thumb"], timeout: 30)
        #expect(request.url == url)
        #expect(request.httpMethod == "POST")
        let data = try #require(request.httpBody)
        let body = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        #expect(body["key"] as? String == "k3y")
        #expect(body["action"] as? String == "photo")
        #expect(body["id"] as? String == "abc")
        #expect(body["size"] as? String == "thumb")
        #expect(body["payload"] is [String: Any])
        // Links end up in server and proxy logs: the key never goes in one.
        let source = try String(contentsOf: URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .appending(path: "MyPersonalTracker/Sheet/SheetStore.swift"), encoding: .utf8)
        #expect(!source.contains(#"URLQueryItem(name: "key""#))
    }

    @Test func paymentHistoryStaysOutOfBackups() throws {
        let folder = FileManager.default.temporaryDirectory.appending(path: "upi-" + UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let file = folder.appending(path: "payments.json")
        let defaults = try #require(UserDefaults(suiteName: "test.upi." + UUID().uuidString))
        // Saved by an earlier version, in UserDefaults (which backups include).
        let old = UpiPayment(id: "MPT1", date: .now, payee: UpiPayee(vpa: "shop@okicici", name: "Shop"), amount: 10, note: "",
                             category: "Other", app: .phonePe, status: .paid, loggedAmount: 10)
        defaults.set(try JSONEncoder().encode([old]), forKey: "upi.payments")

        let payments = UpiPayments(storeURL: file, defaults: defaults)
        #expect(payments.payments.map(\.id) == ["MPT1"])
        #expect(defaults.data(forKey: "upi.payments") == nil)
        let values = try file.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(values.isExcludedFromBackup == true)
        #expect(UpiPayments(storeURL: file, defaults: defaults).payments.map(\.id) == ["MPT1"])
    }

    @Test func payWarnsWhereThePINGoesAndWhoNamedThePayee() {
        #expect(UpiSafety.pin.contains("PIN"))
        #expect(UpiSafety.qrName.contains("QR code"))
        #expect(UpiSafety.qrName.contains("bank"))
    }
}
