//
//  SheetConnectionTests.swift
//  MyPersonalTrackerTests
//
//  Which sheet the app talks to: the Google sign-in sheet, or the Apps Script
//  web app only when it was chosen in Settings. Mirrors SheetConnectionTest.kt.
//

import Testing
@testable import MyPersonalTracker

struct SheetConnectionTests {
    private let url = "https://script.google.com/macros/s/x/exec"

    @Test func appsScriptOnlyWhenChosen() {
        #expect(SheetConnection.of(googleSheet: true, signedIn: true, webAppChosen: false, webAppURL: url, apiKey: "k") == .google)
        // Signed out of Google: not connected — not the web app, even with its key saved.
        #expect(SheetConnection.of(googleSheet: true, signedIn: false, webAppChosen: true, webAppURL: url, apiKey: "k") == .none)
        #expect(SheetConnection.of(googleSheet: false, signedIn: false, webAppChosen: false, webAppURL: url, apiKey: "k") == .none)
        // Just signed in, sheet not found yet: still not connected.
        #expect(SheetConnection.of(googleSheet: false, signedIn: true, webAppChosen: false, webAppURL: url, apiKey: "k") == .none)
        #expect(SheetConnection.of(googleSheet: false, signedIn: false, webAppChosen: true, webAppURL: url, apiKey: "k") == .webApp)
        #expect(SheetConnection.of(googleSheet: false, signedIn: false, webAppChosen: true, webAppURL: url, apiKey: "") == .none)
    }

    @Test func upgradingKeepsAWebAppThatWasInUse() {
        // It had loaded through the web app on this install: keep using it.
        #expect(SheetConnection.chosenOnUpgrade(googleSheet: false, hasKey: true, hadData: true))
        // A key left in the Keychain by an earlier install, with nothing loaded since: not chosen.
        #expect(!SheetConnection.chosenOnUpgrade(googleSheet: false, hasKey: true, hadData: false))
        #expect(!SheetConnection.chosenOnUpgrade(googleSheet: true, hasKey: true, hadData: true))
        #expect(!SheetConnection.chosenOnUpgrade(googleSheet: false, hasKey: false, hadData: true))
    }
}
