//
//  ScriptBackendTests.swift
//  MyPersonalTrackerTests
//
//  The sheet's own script running in the app (gas-shim.js + Resources/Backend),
//  on a workbook made from the blueprint (Fixtures/blueprint-workbook.json):
//  what it answers, and exactly what it would change in the real sheet.
//

import Foundation
import Testing
@testable import MyPersonalTracker

private final class FixtureMarker {}

@Suite("Sheet script in the app", .serialized)
struct ScriptBackendTests {
    private func workbook() throws -> [String: Any] {
        let url = try #require(Bundle(for: FixtureMarker.self).url(forResource: "blueprint-workbook", withExtension: "json"))
        return try #require(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }

    private func run(_ action: String, _ payload: [String: Any] = [:], reply: String? = nil,
                     uploads: [String: String] = [:]) throws -> ScriptRuntime.Result {
        var wb = try workbook()
        wb["uploads"] = uploads
        return try ScriptRuntime.shared.run(workbook: wb, action: action, payload: payload, reply: reply)
    }

    private func sets(_ result: ScriptRuntime.Result, on sheet: String) -> [[String: Any]] {
        result.ops.filter { $0["t"] as? String == "set" && $0["sheet"] as? String == sheet }
    }

    @Test func snapshotReadsEveryAreaWithoutChangingAnything() throws {
        let result = try run("snapshot")
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        #expect(result.ops.isEmpty, "a fresh workbook needs no changes to be read: \(result.ops.prefix(3))")
        let data = try #require(result.reply["data"] as? [String: Any])
        let snapshot = try JSONDecoder().decode(SheetSnapshot.self, from: JSONSerialization.data(withJSONObject: data))
        #expect(snapshot.days.count == 30)
        #expect(snapshot.days.first?.date == "2026-10-19")
        #expect(snapshot.settings.startDate == "2026-10-19")
        #expect(snapshot.dashboard.count == 12)
        #expect(snapshot.salary.count == 12)
        #expect(snapshot.parents != nil && snapshot.food != nil && snapshot.study != nil && snapshot.renewals != nil)
    }

    @Test func savingADayWritesItsRow() throws {
        let result = try run("saveDay", ["date": "2026-10-20", "office": "Y", "breakfast": "N", "water": 2.5, "notes": "good"], reply: "ack")
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        #expect(result.reply["ack"] as? Bool == true)
        let set = try #require(sets(result, on: "Daily Tracker").first)
        #expect(set["row"] as? Int == 6)    // the second day of the period
        #expect(set["col"] as? Int == 3)
        let row = try #require((set["values"] as? [[Any]])?.first)
        #expect(row[0] as? String == "Y" && row[1] as? String == "N")
        #expect(row[5] as? Double == 2.5)
        #expect(row[10] as? String == "good")
    }

    @Test func addingAnExpenseUsesTheFirstFreeRowAndRemembersTheWrite() throws {
        let result = try run("addExpense", ["date": "2026-10-19", "description": "Coffee", "category": "Food & Dining",
                                            "method": "Cash", "qar": 12, "notes": "", "clientId": "abc-1"], reply: "ack")
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        let ledger = sets(result, on: "Finance Tracker")
        let first = try #require(ledger.first)
        #expect(first["row"] as? Int == 5 && first["col"] as? Int == 1)
        let left = try #require((first["values"] as? [[Any]])?.first)
        #expect((left[0] as? [String: Any])?["$d"] as? String == "2026-10-19")
        #expect(left[1] as? String == "Coffee")
        #expect(ledger.contains { $0["col"] as? Int == 5 && (($0["values"] as? [[Any]])?.first?.first as? Double) == 12 })
        // The clientId is kept, so a resent write is acknowledged without adding it twice.
        let remembered = sets(result, on: "App Data").flatMap { ($0["values"] as? [[Any]]) ?? [] }
        #expect(remembered.contains { ($0.first as? String) == "script:CLIENT_WRITES" && "\($0.last ?? "")".contains("abc-1") })
    }

    @Test func refusedChangesSayWhy() throws {
        let noAmount = try run("addExpense", ["date": "2026-10-19", "qar": 0])
        #expect(noAmount.reply["ok"] as? Bool == false)
        #expect((noAmount.reply["error"] as? String)?.contains("amount") == true)
        #expect(noAmount.ops.isEmpty)
        let unknown = try run("noSuchThing")
        #expect((unknown.reply["error"] as? String)?.contains("Unknown action") == true)
    }

    @Test func momAndDadRequestKeepsUploadedPhotos() throws {
        let result = try run("saveParentAsk", [
            "id": "ask-1", "task": "Book eye check-up", "who": "Dad", "date": "2026-10-21", "time": "09:00",
            "repeats": "Once", "nag": true, "photos": ["local:p1"], "newPhotos": ["p1": "QUJD"],
        ], reply: "ack", uploads: ["QUJD": "drive-photo-1"])
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        let row = try #require((sets(result, on: "Parents").first?["values"] as? [[Any]])?.first)
        #expect(row[0] as? String == "ask-1")
        #expect(row[1] as? String == "Book eye check-up")
        #expect((row[3] as? [String: Any])?["$d"] as? String == "2026-10-21")
        #expect(row[10] as? String == "drive-photo-1")
    }

    @Test func mealGoesToFoodLogAndLedger() throws {
        let result = try run("saveMeal", [
            "mealId": "meal-1", "date": "2026-10-19", "time": "08:30", "meal": "Breakfast",
            "items": [["food": "Oats", "amount": 50, "unit": "g", "calories": 190]], "cost": 15,
            "finance": ["add": true, "category": "Food & Dining", "method": "Cash"],
        ], reply: "ack")
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        let food = try #require((sets(result, on: "Food Log").first?["values"] as? [[Any]])?.first)
        #expect(food[0] as? String == "meal-1" && food[4] as? String == "Oats")
        #expect(food[13] as? Int == 5)    // the ledger row it added
        #expect(sets(result, on: "Finance Tracker").contains { ($0["values"] as? [[Any]])?.first?.contains { $0 as? String == "Breakfast: Oats" } == true })
    }

    @Test func replacingAPlanDeletesTheOldRows() throws {
        // Two writes in one go: the second sees the first (the workbook stays loaded between them here).
        var wb = try workbook()
        wb["uploads"] = [String: String]()
        let first = try ScriptRuntime.shared.run(workbook: wb, action: "savePlan",
                                                 payload: ["date": "2026-10-22", "meal": "Lunch", "items": [["food": "Rice"]]], reply: "ack")
        #expect(first.reply["ok"] as? Bool == true, "\(first.reply["error"] ?? "")")
        #expect(sets(first, on: "Meal Plan").count == 1)
        #expect(!first.ops.contains { $0["t"] as? String == "deleteRows" })
    }

    @Test func newPeriodArchivesAndRebuildsWithFormatting() throws {
        let result = try run("startNextPeriod", ["start": "2026-11-18", "end": "2026-12-27"])
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        let requests = result.ops.compactMap { $0["req"] as? [String: Any] }.compactMap(\.keys.first)
        #expect(requests.filter { $0 == "duplicateSheet" }.count == 4)          // Dashboard, Daily, Finance, Salary archived
        #expect(requests.contains("copyPaste") && requests.contains("addChart") && requests.contains("addConditionalFormatRule"))
        #expect(result.ops.contains { $0["t"] as? String == "dropChartsOf" })   // the archived Dashboard's charts go
        let settings = sets(result, on: "Settings").compactMap { ($0["values"] as? [[Any]])?.first?.first as? [String: Any] }
        #expect(settings.contains { $0["$d"] as? String == "2026-11-18" })
    }

    @Test func setupBuildsAWholeWorkbookFromAnEmptySheet() throws {
        let empty: [String: Any] = ["id": "empty", "name": "Test", "tz": "Asia/Kolkata", "sheets": [
            ["id": 0, "title": "Sheet1", "index": 0, "maxRows": 1000, "maxCols": 26, "values": [], "formulas": [], "display": []],
        ]]
        let result = try ScriptRuntime.shared.call(workbook: empty, function: "setupNewWorkbook")
        let requests = result.ops.compactMap { ($0["req"] as? [String: Any])?.keys.first }
        #expect(requests.filter { $0 == "addSheet" }.count >= 35)
        #expect(requests.filter { $0 == "addChart" }.count == 4)
        #expect(requests.contains("setDataValidation") && requests.contains("mergeCells") && requests.contains("updateBorders"))
    }

    @Test func exportAndYearReviewRun() throws {
        let export = try run("export")
        #expect(export.reply["ok"] as? Bool == true, "\(export.reply["error"] ?? "")")
        #expect(((export.reply["data"] as? [String: Any])?["files"] as? [Any])?.isEmpty == false)
        let review = try run("yearReview", ["year": 2026])
        #expect(review.reply["ok"] as? Bool == true, "\(review.reply["error"] ?? "")")
    }

    /// Every API action the app can send, one after another on one workbook (deletes follow their saves).
    @Test func everyActionRunsInTheAppsEngine() throws {
        let url = try #require(Bundle(for: FixtureMarker.self).url(forResource: "all-actions", withExtension: "json"))
        let steps = try #require(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [[String: Any]])
        var wb = try workbook()
        wb["uploads"] = ["QUJD": "drive-1"]
        let requests = steps.map { ($0["action"] as? String ?? "", $0["payload"] as? [String: Any] ?? [:]) }
        let results = try ScriptRuntime.shared.runSequence(workbook: wb, requests: requests)
        #expect(results.count == 71)
        var changed = 0
        for (step, result) in zip(requests, results) {
            #expect(result.reply["ok"] as? Bool == true, "\(step.0): \(result.reply["error"] ?? "")")
            if !result.ops.isEmpty { changed += 1 }
        }
        #expect(changed >= 60, "most actions change the sheet (\(changed))")
        // After all that, the workbook still reads.
        let last = try #require(results.last)
        #expect((last.reply["data"] as? [String: Any])?["days"] != nil)
    }

    /// Sets one cell of a workbook: a formula when the text starts with "=", else a value.
    private func put(_ wb: inout [String: Any], _ title: String, _ row: Int, _ col: Int, _ value: Any) {
        var sheets = wb["sheets"] as! [[String: Any]]
        let i = sheets.firstIndex { $0["title"] as? String == title }!
        var values = sheets[i]["values"] as! [[Any]], formulas = sheets[i]["formulas"] as! [[Any]]
        let formula = (value as? String)?.hasPrefix("=") == true
        values[row - 1][col - 1] = formula ? "" : value
        formulas[row - 1][col - 1] = formula ? value : ""
        sheets[i]["values"] = values
        sheets[i]["formulas"] = formulas
        wb["sheets"] = sheets
    }

    private func salaryCells(_ result: ScriptRuntime.Result) -> [Int: Any] {
        var cells = [Int: Any]()
        for set in sets(result, on: "Salary & Savings") where set["col"] as? Int == 2 {
            cells[set["row"] as! Int] = (set["values"] as? [[Any]])?.first?.first
        }
        return cells
    }

    @Test func salaryOnlyGoesToTheMonthItWasTypedFor() throws {
        let result = try run("saveSalary", ["month": "2026-11", "salary": 5000, "otherIncome": "", "remittanceQar": ""])
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        let cells = salaryCells(result)
        #expect(cells.count == 1 && cells[6] as? Double == 5000)    // Nov 2026, nothing else
    }

    @Test func blankSalaryLeavesTheMonthEmptyNotTheSettingsSalary() throws {
        let result = try run("saveSalary", ["month": "2026-10", "salary": "", "otherIncome": "", "remittanceQar": ""])
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        let cell = try #require(salaryCells(result)[5])
        #expect(cell as? String == "", "a cleared month must not fall back to the Settings salary: \(cell)")
    }

    @Test func negativeOrEndlessIncomeIsRefused() throws {
        let negative = try run("saveSalary", ["month": "2026-11", "salary": -5000])
        #expect(negative.reply["ok"] as? Bool == false)
        #expect((negative.reply["error"] as? String)?.contains("negative") == true)
        #expect(salaryCells(negative).isEmpty)
        let endless = try run("saveSalary", ["month": "2026-11", "salary": "Infinity"])
        #expect(salaryCells(endless)[6] as? String == "", "Infinity is not a number the sheet should keep")
    }

    @Test func autoFilledSalariesAreClearedOnceAndTypedOnesKept() throws {
        // An older sheet: every month fills from the Settings salary by formula, except November, typed over.
        var wb = try workbook()
        wb["uploads"] = [String: String]()
        put(&wb, "Settings", 15, 2, 3000)
        for row in 5...16 { put(&wb, "Salary & Savings", row, 2, "=IF(OR(Settings!$B$15=\"\",$A\(row)>TODAY()),\"\",Settings!$B$15)") }
        put(&wb, "Salary & Savings", 6, 2, 4200)
        put(&wb, "App Data", 10, 2, "")
        let result = try ScriptRuntime.shared.run(workbook: wb, action: "snapshot", payload: [:], reply: nil)
        #expect(result.reply["ok"] as? Bool == true, "\(result.reply["error"] ?? "")")
        let cells = salaryCells(result)
        #expect(Set(cells.keys) == Set(5...16).subtracting([6]), "typed November is left alone: \(cells.keys.sorted())")
        #expect(cells.values.allSatisfy { $0 as? String == "" })
        let data = try #require(result.reply["data"] as? [String: Any])
        let snapshot = try JSONDecoder().decode(SheetSnapshot.self, from: JSONSerialization.data(withJSONObject: data))
        #expect(snapshot.salary.first { $0.month == "2026-11" }?.salary == 4200)
        #expect(snapshot.salary.first { $0.month == "2026-10" }?.salary == nil)
        let remembered = sets(result, on: "App Data").flatMap { ($0["values"] as? [[Any]]) ?? [] }
        #expect(remembered.contains { ($0.first as? String) == "doc:SALARY_AUTOFILL_CLEARED" })
    }

    @Test func helpers() {
        #expect(ScriptBackend.a1("Salary & Savings", 5, 2) == "'Salary & Savings'!B5")
        #expect(ScriptBackend.a1("Dad's", 4, 1, rows: 2, cols: 3) == "'Dad''s'!A4:C5")
        #expect(ScriptBackend.column(27) == "AA")
        #expect(ScriptBackend.typed(["$d": "2026-10-19"]) as? String == "2026-10-19")
        let marked = ScriptBackend.markDates([[46314, 12, "x"]], texts: [["19-Oct-26", 12, "x"]])
        #expect((marked[0][0] as? [String: Any])?["$s"] as? Double == 46314)
        #expect(marked[0][1] as? Int == 12)
    }
}
