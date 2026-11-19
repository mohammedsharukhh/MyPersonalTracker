//
//  SettingsTests.swift
//  MyPersonalTrackerTests
//
//  Settings is one screen: your sheet, then pages that save to it (Money,
//  Health & food, Study), then what's only on this phone (Notifications…).
//  Mirrors SettingsTest.kt.
//

import Foundation
import SwiftUI
import Testing
@testable import MyPersonalTracker

@Suite("Settings")
@MainActor
struct SettingsTests {
    private func settings(_ json: String) throws -> SheetSettings {
        try JSONDecoder().decode(SheetSettings.self, from: Data(json.utf8))
    }

    private func defaults() throws -> UserDefaults {
        try #require(UserDefaults(suiteName: "test.settings." + UUID().uuidString))
    }

    @Test func everyTargetIsOnExactlyOnePage() {
        // Everything the old single Settings screen let you change, so nothing went missing.
        let editable: Set<String> = [
            "RATE", "DAILY_LIMIT", "SALARY", "LIMIT_FROM_SPLIT", "CUSTOM_LIMIT", "EMERGENCY_GOAL",
            "SPLIT_ESSENTIALS", "SPLIT_EMERGENCY", "SPLIT_SAVINGS", "SPLIT_FAMILY", "SPLIT_LIFESTYLE",
            "WATER_TARGET", "SLEEP_TARGET", "STEPS_TARGET", "SCREEN_LIMIT", "GREAT_DAY",
            "CALORIE_TARGET", "PROTEIN_TARGET", "CARB_TARGET", "FAT_TARGET", "FOOD_BUDGET",
            "STUDY_DAILY_TARGET", "STUDY_WEEKLY_TARGET",
        ]
        let pages = SettingsPage.allCases.map { $0.keys(single: false) }
        #expect(pages.reduce(Set()) { $0.union($1) } == editable)
        #expect(pages.map(\.count).reduce(0, +) == editable.count, "a key is on two pages")
        // One currency: no exchange rate to set.
        #expect(!SettingsPage.money.keys(single: true).contains("RATE"))
        #expect(SettingsPage.allCases.allSatisfy { !$0.title.isEmpty && !$0.subtitle.isEmpty })
    }

    @Test func aPageSavesOnlyWhatChangedOnIt() throws {
        let sheet = try settings(#"{"DAILY_LIMIT": 50, "SALARY": 9000, "SPLIT_ESSENTIALS": 0.45, "WATER_TARGET": 3}"#)
        let draft = SettingsDraft(page: .money, single: true)
        draft.seed(sheet)
        #expect(!draft.hasChanges)

        draft.binding("DAILY_LIMIT", percent: false).wrappedValue = 60
        // Shown as 50 (%), saved as 0.5, as the sheet keeps it.
        let essentials = draft.binding("SPLIT_ESSENTIALS", percent: true)
        #expect(essentials.wrappedValue == 45)
        essentials.wrappedValue = 50
        // Typing the same number back isn't a change.
        draft.binding("SALARY", percent: false).wrappedValue = 9000
        // A key from another page can't sneak into this page's save.
        draft.binding("WATER_TARGET", percent: false).wrappedValue = 4

        #expect(draft.hasChanges)
        #expect(draft.changes == ["DAILY_LIMIT": 60, "SPLIT_ESSENTIALS": 0.5])
    }

    @Test func openingAPageIsNotAChange() throws {
        // Fields show 2 decimals and write that back when they appear.
        let draft = SettingsDraft(page: .money, single: false)
        draft.seed(try settings(#"{"RATE": 3.7654, "SPLIT_SAVINGS": 0.2049}"#))
        draft.binding("RATE", percent: false).wrappedValue = 3.77
        draft.binding("SPLIT_SAVINGS", percent: true).wrappedValue = 20.49
        #expect(!draft.hasChanges)
        draft.binding("RATE", percent: false).wrappedValue = 3.8
        #expect(draft.changes == ["RATE": 3.8])
    }

    @Test func clearingAFieldIsNotSaved() throws {
        let draft = SettingsDraft(page: .study, single: true)
        draft.seed(try settings(#"{"STUDY_DAILY_TARGET": 60}"#))
        draft.binding("STUDY_DAILY_TARGET", percent: false).wrappedValue = nil
        #expect(!draft.hasChanges)
    }

    @Test func morningAndEveningRemindersAreSeparate() {
        // With the sheet, the morning slot is the briefing; without it, the daily smile.
        #expect(MorningSlot.of(sheetConnected: true, briefingOn: true, morningOn: false) == .briefing)
        #expect(MorningSlot.of(sheetConnected: true, briefingOn: false, morningOn: true) == .off)
        #expect(MorningSlot.of(sheetConnected: false, briefingOn: true, morningOn: true) == .smile)
        #expect(MorningSlot.of(sheetConnected: false, briefingOn: true, morningOn: false) == .off)
    }

    @Test func upgradingKeepsTheMorningReminderAsItWas() throws {
        // Before, the morning reminder only came with Daily reminders on.
        let off = try defaults()
        off.set(false, forKey: ReminderSettings.enabledKey)
        ReminderSettings.separateMorning(off)
        #expect(off.bool(forKey: ReminderSettings.morningEnabledKey) == false)

        let on = try defaults()
        on.set(true, forKey: ReminderSettings.enabledKey)
        ReminderSettings.separateMorning(on)
        #expect(on.bool(forKey: ReminderSettings.morningEnabledKey) == true)

        // Only once: turning it on afterwards sticks.
        off.set(true, forKey: ReminderSettings.morningEnabledKey)
        ReminderSettings.separateMorning(off)
        #expect(off.bool(forKey: ReminderSettings.morningEnabledKey) == true)
    }

    @Test func notificationsRowSaysHowManyAreOn() throws {
        let store = try defaults()
        // Fresh install with a sheet: the sheet's alerts are on, the evening one isn't.
        #expect(NotificationSummary.count(store, sheetConnected: true) == (on: 6, total: 7))
        store.set(false, forKey: ReminderSettings.billRemindersKey)
        store.set(true, forKey: ReminderSettings.enabledKey)
        #expect(NotificationSummary.count(store, sheetConnected: true) == (on: 6, total: 7))
        // Without a sheet only the morning and evening reminders apply.
        store.set(false, forKey: ReminderSettings.morningEnabledKey)
        #expect(NotificationSummary.count(store, sheetConnected: false) == (on: 1, total: 2))

        #expect(NotificationSummary.text(on: 7, total: 7) == "All on")
        #expect(NotificationSummary.text(on: 0, total: 7) == "All off")
        #expect(NotificationSummary.text(on: 3, total: 7) == "3 of 7 on")
    }

    @Test func sheetRowSaysWhetherItIsConnected() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        #expect(SheetStatus.subtitle(connected: false, account: nil, synced: nil, now: now) == "Not connected — tap to connect")
        #expect(SheetStatus.subtitle(connected: true, account: "me@gmail.com", synced: nil, now: now) == "Connected · me@gmail.com")
        let synced = SheetStatus.subtitle(connected: true, account: nil, synced: now.addingTimeInterval(-120), now: now)
        #expect(synced.hasPrefix("Connected · synced "))
    }

    @Test func yourPlanNeverAsksToConnectAnAlreadyConnectedSheet() {
        // Signed in but the sheet hasn't arrived yet: say so, don't offer to connect again.
        #expect(PlanState.of(connected: true, loaded: false, loading: true, error: nil) == .loading)
        #expect(PlanState.of(connected: true, loaded: false, loading: false, error: nil) == .loading)
        #expect(PlanState.of(connected: true, loaded: false, loading: false, error: "Offline") == .failed("Offline"))
        #expect(PlanState.of(connected: true, loaded: true, loading: true, error: "Offline") == .ready)
        // Not connected: the Your sheet row above is the one way to connect.
        #expect(PlanState.of(connected: false, loaded: false, loading: false, error: nil) == .hidden)
        #expect(PlanState.of(connected: false, loaded: true, loading: false, error: nil) == .hidden)
    }

    @Test func settingsOpensStraightFromItsButtonOrANotification() {
        let nav = AppNavigation.shared
        defer { nav.showSettings = false; nav.showMore = false }
        nav.showSettings = false
        nav.open("settings")
        #expect(nav.showSettings)
        #expect(!nav.showMore, "Settings shouldn't open behind More")
    }

    @Test func thereIsOneSettingsScreen() throws {
        // The old reminders-only Settings (from Today) is gone; Today opens the same Settings.
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        #expect(!FileManager.default.fileExists(atPath: root.appending(path: "MyPersonalTracker/Views/Settings/SettingsView.swift").path))
        let today = try String(contentsOf: root.appending(path: "MyPersonalTracker/Views/Today/TodayView.swift"), encoding: .utf8)
        #expect(today.contains("SheetSettingsView("))
        // Every tab has its own Settings button, next to More (not inside it).
        for tab in ["Sheet/CommandDashboardView", "Sheet/DailyTrackerView", "Sheet/FinanceTrackerView",
                    "Sheet/Parents/ParentsView", "Sheet/Study/StudyView", "Sheet/Food/FoodView"] {
            let source = try String(contentsOf: root.appending(path: "MyPersonalTracker/\(tab).swift"), encoding: .utf8)
            #expect(source.contains("SettingsButton()"), "\(tab) has no Settings button")
            #expect(source.contains("MoreButton()"), "\(tab) has no More button")
        }
        // One way to sign in: no developer test screen next to the real button.
        let settings = try String(contentsOf: root.appending(path: "MyPersonalTracker/Sheet/Settings/SettingsViews.swift"), encoding: .utf8)
        #expect(!settings.contains("GoogleSignInTestView"))
        // One way to connect: the Your sheet row, not a second "Connect your sheet" row under it.
        #expect(!settings.contains("\"Connect your sheet\""))
    }
}
