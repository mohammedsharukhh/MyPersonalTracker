//
//  SyncLoopTests.swift
//  MyPersonalTrackerTests
//
//  A quick save is followed by one reload of the sheet — not a reload that
//  starts another reload, forever (which ran the app out of memory).
//

import Foundation
import Testing
@testable import MyPersonalTracker

/// Stands in for the Apps Script web app: snapshots for reloads (action "snapshot"), "got it" for quick saves.
final class FakeSheet: URLProtocol {
    nonisolated(unsafe) static var snapshotJSON = Data()
    nonisolated(unsafe) static var reloads = 0
    nonisolated(unsafe) static var saves = 0
    /// How long each reload takes to answer, and how long the next one takes after it.
    nonisolated(unsafe) static var reloadDelays: [TimeInterval] = []
    /// What every request answers with: 200, or e.g. 429 for "too many requests".
    nonisolated(unsafe) static var status = 200
    /// How long a quick save takes to answer "got it".
    nonisolated(unsafe) static var ackDelay: TimeInterval = 0
    /// Every write received (action, payload), in order.
    nonisolated(unsafe) static var writes: [(action: String, payload: [String: Any])] = []

    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "fake-sheet.test" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        var body = Data()
        if let stream = request.httpBodyStream {
            stream.open()
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable { let n = stream.read(&buffer, maxLength: buffer.count); if n <= 0 { break }; body.append(buffer, count: n) }
            stream.close()
        } else if let b = request.httpBody { body = b }
        let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any]
        let action = json?["action"] as? String
        if action == nil || action == "snapshot" { Self.reloads += 1 } else {
            Self.saves += 1
            Self.writes.append((action ?? "", json?["payload"] as? [String: Any] ?? [:]))
        }
        let quick = String(data: body, encoding: .utf8)?.contains("\"reply\":\"ack\"") ?? false
        let reply = quick ? Data(#"{"ok":true,"ack":true}"#.utf8) : Data(#"{"ok":true,"data":"#.utf8) + Self.snapshotJSON + Data("}".utf8)
        let delay = quick ? Self.ackDelay : (!Self.reloadDelays.isEmpty ? Self.reloadDelays.removeFirst() : 0)
        DispatchQueue.global().asyncAfter(deadline: .now() + delay) { [self] in
            client?.urlProtocol(self, didReceive: HTTPURLResponse(url: request.url!, statusCode: Self.status, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!,
                                cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: reply)
            client?.urlProtocolDidFinishLoading(self)
        }
    }

    override func stopLoading() {}
}

@MainActor
@Suite(.serialized)
struct SyncLoopTests {
    @Test func aQuickSaveIsFollowedByOneReloadNotEndlessOnes() async throws {
        let store = SheetStore.shared
        let oldURL = store.webAppURL, oldKey = store.apiKey, oldGoogleSheet = store.googleSheetID, oldChosen = store.webAppChosen
        defer { store.webAppURL = oldURL; store.apiKey = oldKey; store.googleSheetID = oldGoogleSheet; store.webAppChosen = oldChosen; URLProtocol.unregisterClass(FakeSheet.self) }
        // This test is about the web app; a Google sign-in sheet left switched on in the simulator mustn't take over.
        store.googleSheetID = nil
        let demo = try #require(SheetDemoData.snapshot)
        FakeSheet.snapshotJSON = try JSONEncoder().encode(demo)
        FakeSheet.reloads = 0; FakeSheet.saves = 0
        URLProtocol.registerClass(FakeSheet.self)

        store.useWebApp(url: "https://fake-sheet.test/exec", key: "test-key")
        await store.refresh()
        #expect(store.snapshot != nil)
        let reloadsBefore = FakeSheet.reloads

        let day = try #require(store.snapshot?.days.first { $0.date <= SheetDate.today })
        var edited = day
        edited.notes = "sync loop test"
        await store.saveDay(edited)
        // Sent within a moment (longer while the whole suite runs), then time for a loop to show itself.
        for _ in 0..<100 where !store.pending.isEmpty { try await Task.sleep(for: .milliseconds(100)) }
        try await Task.sleep(for: .seconds(2))

        #expect(FakeSheet.saves == 1)
        #expect(store.pending.isEmpty)
        // One reload after the save — a loop would have made hundreds by now.
        #expect(FakeSheet.reloads - reloadsBefore <= 2, "reloads after one save: \(FakeSheet.reloads - reloadsBefore)")
    }

    @Test func aFirstLoadIsShownEvenWhenAnotherHasStarted() async throws {
        // Just connected: the load from signing in and the one from coming back to the app overlap.
        // The screen is empty, so the first answer is shown rather than thrown away for the newer one.
        let store = SheetStore.shared
        let oldURL = store.webAppURL, oldKey = store.apiKey, oldGoogleSheet = store.googleSheetID, oldChosen = store.webAppChosen
        defer { store.webAppURL = oldURL; store.apiKey = oldKey; store.googleSheetID = oldGoogleSheet; store.webAppChosen = oldChosen; FakeSheet.reloadDelays = []; URLProtocol.unregisterClass(FakeSheet.self) }
        store.googleSheetID = nil
        FakeSheet.snapshotJSON = try JSONEncoder().encode(try #require(SheetDemoData.snapshot))
        URLProtocol.registerClass(FakeSheet.self)
        store.apiKey = ""
        await store.refresh()   // not connected: forgets any data
        #expect(store.snapshot == nil)

        store.useWebApp(url: "https://fake-sheet.test/exec", key: "test-key")
        FakeSheet.reloadDelays = [0.3, 3]
        let first = Task { await store.refresh() }
        try await Task.sleep(for: .milliseconds(100))
        let second = Task { await store.refresh() }
        await first.value
        #expect(store.snapshot != nil, "the first load was thrown away while the screen was empty")
        await second.value
        #expect(store.snapshot != nil)
    }

    @Test func aBusySheetWithNothingOnScreenSaysSo() async throws {
        // Google busy before anything ever loaded: say so, instead of a spinner that never ends.
        let store = SheetStore.shared
        let oldURL = store.webAppURL, oldKey = store.apiKey, oldGoogleSheet = store.googleSheetID, oldChosen = store.webAppChosen
        defer { store.webAppURL = oldURL; store.apiKey = oldKey; store.googleSheetID = oldGoogleSheet; store.webAppChosen = oldChosen; FakeSheet.status = 200; URLProtocol.unregisterClass(FakeSheet.self) }
        store.googleSheetID = nil
        FakeSheet.snapshotJSON = try JSONEncoder().encode(try #require(SheetDemoData.snapshot))
        URLProtocol.registerClass(FakeSheet.self)
        store.apiKey = ""
        await store.refresh()
        store.useWebApp(url: "https://fake-sheet.test/exec", key: "test-key")

        FakeSheet.status = 429
        await store.refresh()
        #expect(store.snapshot == nil)
        #expect(store.errorMessage == SheetStore.busyMessage)

        // With the sheet on screen, a busy moment stays quiet: it's tried again shortly.
        FakeSheet.status = 200
        await store.refresh()
        FakeSheet.status = 429
        await store.refresh()
        #expect(store.snapshot != nil)
        #expect(store.errorMessage == nil)
    }

    @Test func aSavedAppsScriptKeyAloneDoesNotConnect() {
        // The key stays in the Keychain after signing out of Google (or reinstalling the app):
        // that mustn't quietly switch the app to the Apps Script web app.
        let store = SheetStore.shared
        let oldURL = store.webAppURL, oldKey = store.apiKey, oldGoogleSheet = store.googleSheetID, oldChosen = store.webAppChosen
        defer { store.webAppURL = oldURL; store.apiKey = oldKey; store.googleSheetID = oldGoogleSheet; store.webAppChosen = oldChosen }
        store.googleSheetID = nil
        store.webAppChosen = false
        store.apiKey = "left-in-keychain"
        #expect(!store.isConfigured)
        // Chosen in Settings ▸ Your sheet ▸ Advanced: now it's used.
        store.useWebApp(url: "https://fake-sheet.test/exec", key: "test-key")
        #expect(store.isConfigured)
        #expect(store.webAppChosen)
    }

    @Test func deletingAnExpenseWhileItsAddIsOnItsWayDeletesTheRowTheSheetGaveIt() async throws {
        // The add was already being sent: taking it out of the queue didn't stop it landing.
        let store = SheetStore.shared
        let oldURL = store.webAppURL, oldKey = store.apiKey, oldGoogleSheet = store.googleSheetID, oldChosen = store.webAppChosen
        defer { store.webAppURL = oldURL; store.apiKey = oldKey; store.googleSheetID = oldGoogleSheet; store.webAppChosen = oldChosen
                FakeSheet.reloadDelays = []; FakeSheet.writes = []; URLProtocol.unregisterClass(FakeSheet.self) }
        store.googleSheetID = nil
        var sheet = try #require(SheetDemoData.snapshot)
        FakeSheet.snapshotJSON = try JSONEncoder().encode(sheet)
        URLProtocol.registerClass(FakeSheet.self)
        store.useWebApp(url: "https://fake-sheet.test/exec", key: "test-key")
        await store.refresh()
        for _ in 0..<50 where !store.pending.isEmpty { try await Task.sleep(for: .milliseconds(100)) }
        FakeSheet.writes = []

        // The sheet will put it in row 999, and takes a moment to say so.
        sheet.expenses.append(SheetExpense(row: 999, date: SheetDate.today, description: "In flight", category: "Food",
                                           method: "Cash", qar: 7.5, inr: nil, notes: nil))
        FakeSheet.snapshotJSON = try JSONEncoder().encode(sheet)
        FakeSheet.reloadDelays = [1.5]
        await store.saveExpense(row: nil, date: SheetDate.today, description: "In flight", category: "Food", method: "Cash", qar: 7.5, notes: "")
        let tempRow = try #require(store.pending.last?.tempRow)
        for _ in 0..<50 where FakeSheet.writes.isEmpty { try await Task.sleep(for: .milliseconds(50)) }
        await store.deleteExpense(row: tempRow)
        #expect(!(store.snapshot?.expenses.contains { $0.row == tempRow } ?? true), "hidden at once")

        // After the Undo window, the delete goes for the row the sheet gave it.
        for _ in 0..<150 where FakeSheet.writes.count < 2 { try await Task.sleep(for: .milliseconds(100)) }
        #expect(FakeSheet.writes.map(\.action) == ["addExpense", "deleteExpense"])
        #expect((FakeSheet.writes.last?.payload["row"] as? NSNumber)?.intValue == 999)
    }

    @Test func deletingARowWhileItsSaveIsOnItsWayStillDeletesIt() async throws {
        let store = SheetStore.shared
        let oldURL = store.webAppURL, oldKey = store.apiKey, oldGoogleSheet = store.googleSheetID, oldChosen = store.webAppChosen
        defer { store.webAppURL = oldURL; store.apiKey = oldKey; store.googleSheetID = oldGoogleSheet; store.webAppChosen = oldChosen
                FakeSheet.ackDelay = 0; FakeSheet.writes = []; URLProtocol.unregisterClass(FakeSheet.self) }
        store.googleSheetID = nil
        FakeSheet.snapshotJSON = try JSONEncoder().encode(try #require(SheetDemoData.snapshot))
        URLProtocol.registerClass(FakeSheet.self)
        store.useWebApp(url: "https://fake-sheet.test/exec", key: "test-key")
        await store.refresh()
        for _ in 0..<50 where !store.pending.isEmpty { try await Task.sleep(for: .milliseconds(100)) }
        FakeSheet.writes = []

        FakeSheet.ackDelay = 1.5
        let renewal = Renewal(item: "In-flight test")
        store.saveRenewal(renewal)
        for _ in 0..<50 where FakeSheet.writes.isEmpty { try await Task.sleep(for: .milliseconds(50)) }
        store.deleteRenewal(id: renewal.id)

        for _ in 0..<60 where FakeSheet.writes.count < 2 { try await Task.sleep(for: .milliseconds(100)) }
        #expect(FakeSheet.writes.map(\.action) == ["saveRenewal", "deleteRenewal"])
    }
}
