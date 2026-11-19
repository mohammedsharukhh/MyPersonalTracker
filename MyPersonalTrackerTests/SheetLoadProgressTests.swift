//
//  SheetLoadProgressTests.swift
//  MyPersonalTrackerTests
//
//  While a sheet loads (the first time can take a minute), the screen says what's
//  happening — "Building your habits…" — instead of a bare spinner.
//  Mirrors SheetLoadProgressTest.kt.
//

import Foundation
import Testing
@testable import MyPersonalTracker

@Suite(.serialized)
struct SheetLoadProgressTests {
    @Test func eachStepSaysWhatItsDoing() {
        #expect(SheetLoadProgress.reading.title == "Reading your sheet…")
        #expect(SheetLoadProgress.checking.title == "Checking what needs setting up…")
        #expect(SheetLoadProgress.finishing.title == "Almost done…")
        #expect(SheetLoadProgress.reading.fraction == nil)
    }

    @Test func settingUpNamesThePartOfTheSheet() {
        let habits = SheetLoadProgress.writing(tab: "Daily Tracker", done: 390, total: 1560)
        #expect(habits.title == "Building your habits…")
        #expect(habits.fraction == 0.25)
        #expect(habits.note == "25% done · keep the app open")
        #expect(SheetLoadProgress.writing(tab: "Finance Tracker", done: 0, total: 10).title == "Setting up your money…")
        #expect(SheetLoadProgress.writing(tab: "Salary Split", done: 0, total: 10).title == "Setting up your money…")
        #expect(SheetLoadProgress.writing(tab: "Food Log", done: 0, total: 10).title == "Setting up your meals…")
        #expect(SheetLoadProgress.writing(tab: "Study Topics", done: 0, total: 10).title == "Setting up your study plan…")
        #expect(SheetLoadProgress.writing(tab: "Parent Medicines", done: 0, total: 10).title == "Setting up Mom & Dad…")
        #expect(SheetLoadProgress.writing(tab: "Wardrobe", done: 0, total: 10).title == "Setting up your planner…")
        #expect(SheetLoadProgress.writing(tab: "Goal Steps", done: 0, total: 10).title == "Setting up your goals…")
        #expect(SheetLoadProgress.writing(tab: "Renewals", done: 0, total: 10).title == "Setting up your renewals…")
        #expect(SheetLoadProgress.writing(tab: "App Data", done: 0, total: 10).title == "Getting your sheet ready…")
        #expect(SheetLoadProgress.writing(tab: nil, done: 0, total: 0).fraction == 0)
    }

    @Test func waitingForGoogleKeepsWhatItWasDoing() {
        let habits = SheetLoadProgress.writing(tab: "Daily Tracker", done: 780, total: 1560)
        let waiting = SheetLoadProgress.waiting(seconds: 8, during: habits)
        #expect(waiting.title == "Building your habits…")
        #expect(waiting.fraction == 0.5)
        #expect(waiting.note == "Google asked us to slow down · carrying on in 8s")
        #expect(SheetLoadProgress.waiting(seconds: 2, during: nil).title == "Reading your sheet…")
    }

    @Test func theLoadingScreensShowTheProgress() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        // Every tab, Settings and the Sign in with Google button: none is blank (or says "connect") while loading.
        for file in ["Sheet/CommandDashboardView", "Sheet/Settings/SettingsViews", "Sheet/DailyTrackerView", "Sheet/FinanceTrackerView",
                     "Sheet/Food/FoodView", "Sheet/Parents/ParentsView", "Sheet/Study/StudyView", "Sheet/Google/WelcomeView"] {
            let source = try String(contentsOf: root.appending(path: "MyPersonalTracker/\(file).swift"), encoding: .utf8)
            #expect(source.contains("SheetLoadingView("), "\(file) shows a bare spinner")
        }
    }
}
