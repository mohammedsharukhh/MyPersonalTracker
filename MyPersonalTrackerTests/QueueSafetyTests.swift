//
//  QueueSafetyTests.swift
//  MyPersonalTrackerTests
//
//  What keeps a queued change from being lost or landing twice: a lapsed
//  Google sign-in, an edit or delete of an add still on its way, and two
//  script runs on one phone at once.
//

import Foundation
import Testing
@testable import MyPersonalTracker

@MainActor
struct QueueSafetyTests {
    private func write(_ action: String, _ payload: [String: Any], tempRow: Int? = nil) -> PendingWrite {
        PendingWrite(id: UUID().uuidString, action: action, payload: payload, summary: "", createdAt: .now, notBefore: nil, tempRow: tempRow)
    }

    private func expense(_ row: Int, _ description: String, qar: Double) -> SheetExpense {
        SheetExpense(row: row, date: "2026-10-27", description: description, category: nil, method: nil, qar: qar, inr: nil, notes: nil)
    }

    /// A pasted "inf" parses as a Double, and JSON can't hold it: NSJSONSerialization raises (a crash) instead of throwing.
    @Test func infinityOrNaNTypedIntoAFieldIsSavedBlankNotACrash() {
        var queued = write("saveSalary", ["month": "2026-11", "salary": Double.infinity, "items": [["calories": Double.nan]]])
        #expect(queued.payload["month"] as? String == "2026-11")
        #expect(queued.payload["salary"] as? String == "")
        #expect(((queued.payload["items"] as? [[String: Any]])?.first?["calories"]) as? String == "")
        queued.payload["salary"] = -Double.infinity
        #expect(queued.payload["salary"] as? String == "")
    }

    @Test func anAddGetsItsOwnNewRowNotABillPostedAlongside() {
        let add = write("addExpense", ["date": "2026-10-27", "description": "Lunch", "qar": 12.5], tempRow: -1)
        let before = [expense(5, "Rent", qar: 900)]
        // A recurring bill fell due in the same reply.
        let after = before + [expense(6, "Netflix", qar: 45), expense(7, "Lunch", qar: 12.5)]
        #expect(SheetStore.rowAssigned(to: add, before: before, after: after) == 7)
        #expect(SheetStore.rowAssigned(to: add, before: after, after: after) == nil)
    }

    @Test func editsAndDeletesOfAnAddOnItsWayMoveToItsRow() {
        let edit = write("updateExpense", ["row": -1, "qar": 13])
        let delete = write("deleteExpense", ["row": -1])
        let other = write("deleteExpense", ["row": 8])
        let moved = SheetStore.pointing([edit, delete, other], from: -1, to: 7)
        #expect(moved.map { ($0.payload["row"] as? NSNumber)?.intValue } == [7, 7, 8])
        // No row found: they can't be sent anywhere safe.
        #expect(SheetStore.pointing([edit, delete, other], from: -1, to: nil).map(\.id) == [other.id])
    }

    @Test func aLapsedGoogleSignInIsNotARefusal() {
        // A refusal drops the change; these must keep it queued.
        if case .signedOut = SheetError.fromGoogle(.signedOut) {} else { Issue.record("signed out read as a refusal") }
        if case .transient = SheetError.fromGoogle(.http(401, "Invalid Credentials")) {} else { Issue.record("a rejected token isn't retried") }
        if case .transient = SheetError.fromGoogle(.http(503, "")) {} else { Issue.record("busy isn't retried") }
        if case .message = SheetError.fromGoogle(.http(400, "Bad range")) {} else { Issue.record("a bad request isn't a refusal") }
    }

    @Test func scriptRunsGoOneAtATime() async throws {
        final class Log { var lines: [String] = [] }
        let log = Log()
        let first = Task { try await ScriptLane.run { log.lines.append("a"); try await Task.sleep(for: .milliseconds(200)); log.lines.append("a done") } }
        try await Task.sleep(for: .milliseconds(50))
        let second = Task { try await ScriptLane.run { log.lines.append("b"); log.lines.append("b done") } }
        try await first.value
        try await second.value
        #expect(log.lines == ["a", "a done", "b", "b done"])
    }
}