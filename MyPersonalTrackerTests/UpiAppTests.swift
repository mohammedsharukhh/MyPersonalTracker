//
//  UpiAppTests.swift
//  MyPersonalTrackerTests
//
//  Pay with UPI's app list. The unit tests check the list itself; the
//  integration tests run inside the app (its asset catalog, Info.plist and
//  UIApplication) and pay through UpiPayments for real. Mirrors UpiAppsTest.kt.
//

import Foundation
import Testing
import UIKit
@testable import MyPersonalTracker

@Suite("UPI apps")
struct UpiAppTests {
    private let request = UpiRequest(payee: UpiPayee(vpa: "shop@okicici", name: "Corner Shop"), amount: 120.5, note: "Lunch", reference: "MPT1")

    @Test func aQrCodeCantAskForAnEndlessAmount() {
        for am in ["Infinity", "inf", "nan", "-5"] {
            #expect(UpiScan.parse("upi://pay?pa=shop@okicici&am=\(am)")?.amount == nil, "am=\(am)")
        }
        #expect(UpiScan.parse("upi://pay?pa=shop@okicici&am=120.5")?.amount == 120.5)
    }

    @Test func listsEveryAppEvenWithNoneFound() {
        let list = UpiApp.listing(found: [])
        #expect(list == UpiApp.allCases.filter { $0 != .anyApp } + [.anyApp])
        #expect(list.count == 21)
    }

    @Test func foundOnesComeFirstAndOtherIsAlwaysLast() {
        let list = UpiApp.listing(found: [.paytm, .phonePe])
        #expect(Array(list.prefix(2)) == [.phonePe, .paytm])
        #expect(list.last == .anyApp)
        #expect(Set(list).count == list.count)
        #expect(list.count == UpiApp.allCases.count)
    }

    @Test func otherUpiAppIsNeverListedTwice() {
        let list = UpiApp.listing(found: [.anyApp, .cred])
        #expect(list.first == .cred)
        #expect(list.filter { $0 == .anyApp }.count == 1)
    }

    @Test func namesAndLinksAreUnique() {
        let titles = UpiApp.allCases.map(\.title)
        let links = UpiApp.allCases.flatMap(\.prefixes)
        #expect(Set(titles).count == titles.count)
        #expect(Set(links).count == links.count)
        #expect(UpiApp.allCases.allSatisfy { !$0.prefixes.isEmpty })
    }

    @Test func everyLinkIsAValidPaymentURL() throws {
        for app in UpiApp.allCases {
            for prefix in app.prefixes {
                var components = try #require(URLComponents(string: prefix), "\(prefix)")
                components.queryItems = request.queryItems
                let url = try #require(components.url, "\(prefix)")
                #expect(url.query?.contains("pa=shop@okicici") == true, "\(prefix)")
                #expect(url.query?.contains("am=120.50") == true, "\(prefix)")
                #expect(url.query?.contains("cu=INR") == true, "\(prefix)")
            }
        }
        #expect(UpiApp.googlePay.prefixes == ["tez://upi/pay", "gpay://upi/pay"])
        #expect(UpiApp.anyApp.prefixes == ["upi://pay"])
    }

    @Test func savedPaymentsStillReadBack() throws {
        // Raw values are stored with each payment: renaming a case would lose the history.
        let json = Data(#"["phonePe","googlePay","paytm","cred","bhim","anyApp"]"#.utf8)
        let apps = try JSONDecoder().decode([UpiApp].self, from: json)
        #expect(apps == [.phonePe, .googlePay, .paytm, .cred, .bhim, .anyApp])
    }
}

@Suite("UPI apps · in the app", .serialized)
@MainActor
struct UpiAppIntegrationTests {
    private var request: UpiRequest {
        UpiRequest(payee: UpiPayee(vpa: "shop@okicici", name: "Corner Shop"), amount: 1, note: "", reference: "MPT-TEST-\(UUID().uuidString)")
    }

    @Test func everyAppHasItsTileInTheAssetCatalog() {
        for app in UpiApp.allCases {
            let image = UIImage(named: "upi-" + app.rawValue)
            #expect(image != nil, "upi-\(app.rawValue) missing")
            #expect(image.map { $0.size.width == 44 && $0.size.height == 44 } == true, "upi-\(app.rawValue) isn't 44 pt")
        }
    }

    @Test func everySchemeIsDeclaredSoTheIPhoneMayCheckIt() throws {
        let declared = try #require(Bundle.main.object(forInfoDictionaryKey: "LSApplicationQueriesSchemes") as? [String])
        let used = Set(UpiApp.allCases.flatMap(\.prefixes).compactMap { URL(string: $0)?.scheme })
        #expect(used.isSubset(of: Set(declared)), "Not in Info.plist: \(used.subtracting(declared).sorted())")
        // iOS reads no more than 50 of them.
        #expect(declared.count <= 50)
    }

    @Test func cardsHaveAFaceIDReason() {
        let reason = Bundle.main.object(forInfoDictionaryKey: "NSFaceIDUsageDescription") as? String
        #expect(reason?.isEmpty == false)
    }

    @Test func buildsTheLinkForTheAppThatCanOpenIt() throws {
        // No UPI apps on the simulator: each app falls back to its first link.
        let url = try #require(UpiApp.googlePay.url(request))
        #expect(url.absoluteString.hasPrefix("tez://upi/pay?pa=shop@okicici"))
        #expect(UpiApp.allCases.filter(\.isInstalled).isEmpty)
        #expect(UpiApp.listing(found: Set(UpiApp.allCases.filter(\.isInstalled))).count == UpiApp.allCases.count)
    }

    @Test func aPaymentNoAppOpensLeavesNothingBehind() async {
        let payments = UpiPayments.shared
        let before = payments.payments
        for app in [UpiApp.phonePe, .googlePay, .anyApp] {
            let opened = await payments.pay(request, category: "Other", with: app)
            #expect(!opened, "\(app.title) shouldn't open on the simulator")
        }
        #expect(payments.payments == before)
        // What's saved on the iPhone matches too (the file may only now exist, holding the same list).
        let saved = (try? Data(contentsOf: UpiPayments.defaultStoreURL)).flatMap { try? JSONDecoder().decode([UpiPayment].self, from: $0) } ?? []
        #expect(saved == before)
    }
}
