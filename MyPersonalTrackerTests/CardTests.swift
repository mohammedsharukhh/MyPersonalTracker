//
//  CardTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import LocalAuthentication
import Testing
@testable import MyPersonalTracker

@Suite("Saved cards")
struct CardTests {
    @Test func detectsTheNetwork() {
        #expect(CardBrand(number: "4111111111111111") == .visa)
        #expect(CardBrand(number: "5555555555554444") == .mastercard)
        #expect(CardBrand(number: "2223003122003222") == .mastercard)
        #expect(CardBrand(number: "378282246310005") == .amex)
        #expect(CardBrand(number: "6070000000000000") == .rupay)
        #expect(CardBrand(number: "6521000000000000") == .rupay)
        #expect(CardBrand(number: "6011111111111117") == .discover)
        #expect(CardBrand(number: "6500000000000000") == .discover)
        #expect(CardBrand(number: "30569309025904") == .diners)
        #expect(CardBrand(number: "3530111333300000") == .jcb)
        #expect(CardBrand(number: "") == .unknown)
    }

    @Test func groupsTheNumberLikeTheCard() {
        #expect(CardFormat.grouped("4111111111111111") == "4111 1111 1111 1111")
        #expect(CardFormat.grouped("378282246310005") == "3782 822463 10005")
        #expect(CardFormat.grouped("30569309025904") == "3056 930902 5904")
        #expect(CardFormat.grouped("41111") == "4111 1")
        #expect(CardFormat.grouped("4111 1111-1111") == "4111 1111 1111")
    }

    @Test func luhnCatchesTypos() {
        #expect(CardFormat.passesLuhn("4111111111111111"))
        #expect(CardFormat.passesLuhn("378282246310005"))
        #expect(!CardFormat.passesLuhn("4111111111111112"))
        #expect(!CardFormat.passesLuhn("4111"))
    }

    @Test func readsTheExpiry() throws {
        let parsed = try #require(CardFormat.expiry("08/29"))
        #expect(parsed.month == 8 && parsed.year == 2029)
        #expect(CardFormat.expiry("1329") == nil)
        #expect(CardFormat.expiry("082") == nil)
        #expect(CardFormat.expiryField("0") == "0")
        #expect(CardFormat.expiryField("08") == "08")
        #expect(CardFormat.expiryField("0829") == "08/29")
        #expect(CardFormat.expiryField("08/2") == "08/2")
    }

    @Test func warnsBeforeItExpires() throws {
        let calendar = Calendar(identifier: .gregorian)
        let today = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 19)))
        var card = SavedCard(number: "4111111111111111", expiryMonth: 11, expiryYear: 2026)
        #expect(card.expiryState(on: today, calendar: calendar) == .soon)
        card.expiryMonth = 10
        #expect(card.expiryState(on: today, calendar: calendar) == .soon)   // valid to the end of October
        card.expiryMonth = 9
        #expect(card.expiryState(on: today, calendar: calendar) == .expired)
        card.expiryYear = 2029
        #expect(card.expiryState(on: today, calendar: calendar) == .fine)
        card.expiryMonth = nil
        #expect(card.expiryState(on: today, calendar: calendar) == .fine)
    }

    @Test func showsOnlyTheLastFour() {
        let card = SavedCard(bank: "HDFC", number: "4111111111111111", expiryMonth: 8, expiryYear: 2029)
        #expect(card.maskedNumber == "•••• 1111")
        #expect(card.title == "HDFC Visa")
        #expect(card.expiryText == "08/29")
        #expect(!card.spokenSummary.contains("4111"))
        #expect(SavedCard(nickname: " Millennia ", bank: "HDFC").title == "Millennia")
        #expect(SavedCard(kind: .debit).title == "debit card")
    }

    @Test func roundTripsThroughJSON() throws {
        let card = SavedCard(nickname: "Travel", bank: "ICICI", kind: .forex, number: "5555555555554444", holder: "A KUMAR",
                             expiryMonth: 1, expiryYear: 2030, cvv: "123", notes: "Limit 2L", style: .gold)
        let data = try JSONEncoder().encode([card])
        #expect(try JSONDecoder().decode([SavedCard].self, from: data) == [card])
    }
}

/// The vault's Keychain use: the cards are only readable with the context Face ID
/// or the passcode just unlocked. Mirrors CardVaultTest.kt.
@Suite("Saved cards · vault")
struct CardVaultTests {
    private let card = SavedCard(nickname: "Millennia", bank: "HDFC", number: "4111111111111111", holder: "Test", expiryMonth: 8, expiryYear: 2029, cvv: "123")

    /// The Keychain stand-in: its item opens only while `confirmed` (just after Face ID or the passcode).
    final class FakeKeychain: CardKeychain {
        var item: Data?
        var legacy: Data?
        var confirmed = true
        var failWrites = false
        var contexts: [LAContext?] = []
        func read(context: LAContext?) throws -> Data? {
            contexts.append(context)
            guard confirmed else { throw CardKeychainError.notConfirmed }
            return item
        }
        func write(_ data: Data?, context: LAContext?) -> Bool {
            contexts.append(context)
            guard confirmed, !failWrites else { return false }
            item = data
            return true
        }
        func readLegacy() -> Data? { legacy }
        func deleteLegacy() { legacy = nil }
    }

    @Test func cardsOpenOnlyWithTheContextFaceIDUnlocked() throws {
        let keychain = FakeKeychain()
        let context = LAContext()
        let vault = CardVault(keychain: keychain)
        vault.unlocked(with: context)
        #expect(vault.save(card))
        #expect(keychain.contexts.allSatisfy { $0 === context })

        // Anything in the app that skips the prompt gets nothing.
        keychain.confirmed = false
        let skipped = CardVault(keychain: keychain)
        skipped.unlocked(with: nil)
        #expect(!skipped.isUnlocked)
        #expect(skipped.cards.isEmpty)
        #expect(skipped.errorMessage != nil)
    }

    @Test func movesCardsSavedBeforeTheFaceIDBinding() throws {
        let keychain = FakeKeychain()
        keychain.legacy = try JSONEncoder().encode([card])
        let vault = CardVault(keychain: keychain)
        vault.unlocked(with: LAContext())
        #expect(vault.isUnlocked)
        #expect(vault.cards == [card])
        #expect(keychain.legacy == nil, "the old item that needed no Face ID is deleted")
        #expect(try JSONDecoder().decode([SavedCard].self, from: #require(keychain.item)) == [card])
    }

    @Test func keepsTheOldItemUntilTheNewOneIsSaved() throws {
        let keychain = FakeKeychain()
        keychain.legacy = try JSONEncoder().encode([card])
        keychain.failWrites = true
        let vault = CardVault(keychain: keychain)
        vault.unlocked(with: LAContext())
        #expect(vault.cards == [card])
        #expect(keychain.legacy != nil)
    }

    @Test func lockingForgetsTheCards() {
        let vault = CardVault(keychain: FakeKeychain())
        vault.unlocked(with: LAContext())
        vault.save(card)
        vault.lock()
        #expect(!vault.isUnlocked)
        #expect(vault.cards.isEmpty)
        #expect(!vault.save(card))
    }

    @Test func locksThirtySecondsAfterLeavingTheApp() {
        var now = Date(timeIntervalSince1970: 0)
        let vault = CardVault(keychain: FakeKeychain(), now: { now })
        vault.unlocked(with: LAContext())
        vault.didEnterBackground(); now += 29; vault.didBecomeActive()
        #expect(vault.isUnlocked)
        vault.didEnterBackground(); now += 31; vault.didBecomeActive()
        #expect(!vault.isUnlocked)
    }

    @Test func theItemNeedsFaceIDOrThePasscodeAndStaysOnThisIPhone() {
        // Deleted by iOS if the passcode is turned off; never in a backup or on another device.
        #expect(KeychainCardStore.accessible == kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly)
        #expect(KeychainCardStore.flags == .userPresence)
    }

    @Test func roundTripsThroughTheRealKeychain() throws {
        // The simulator has no passcode, so this checks the item without the Face ID binding.
        let store = KeychainCardStore(account: "test.cards." + UUID().uuidString)
        defer { _ = store.write(nil, context: nil) }
        #expect(try store.read(context: nil) == nil)
        #expect(store.write(Data("one".utf8), context: nil))
        #expect(store.write(Data("two".utf8), context: nil))
        #expect(try store.read(context: nil) == Data("two".utf8))
        #expect(store.write(nil, context: nil))
        #expect(try store.read(context: nil) == nil)
    }
}
