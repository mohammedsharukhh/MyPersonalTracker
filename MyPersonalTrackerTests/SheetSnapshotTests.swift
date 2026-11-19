//
//  SheetSnapshotTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import Testing
@testable import MyPersonalTracker

/// The Apps Script API returns sheet cells loosely typed: blanks as null,
/// numbers sometimes typed as text. One odd cell must not lose the snapshot.
@Suite("Sheet snapshot decoding")
struct SheetSnapshotTests {
    private let json = """
    {"ok":true,"data":{
      "generatedAt":"2026-09-30T10:00:00Z",
      "settings":{"START_DATE":"2026-08-15","END_DATE":"2026-09-30","RATE":26.1,"DAILY_LIMIT":20,
                  "MONTHLY_LIMIT":"620","WATER_TARGET":4,"SLEEP_TARGET":7.5,"STEPS_TARGET":8000,
                  "SCREEN_LIMIT":3,"SALARY":2500,"SAVINGS_GOAL":0.95,"GREAT_DAY":0.85,"REMIT_AS_SAVING":"Yes"},
      "lists":{"categories":["Groceries","Food & Dining"],"methods":["Cash"]},
      "dashboard":[{"label":"OVERALL HABIT SCORE","value":"75.0%","caption":"average of your daily scores"}],
      "dashboardTables":[{"title":"SPEND BY CATEGORY","headers":["Category","QAR","INR","% of Total"],
                          "rows":[["Food & Dining","300.00","7,830","14.9%"],["TOTAL","2,015.00","52,592","100.0%"]]}],
      "budget":{"title":"","headers":["Date","Spent (QAR)"],"rows":[["15-Aug-26","500.00"]]},
      "days":[
        {"date":"2026-08-15","office":"Y","breakfast":"Y","lunch":"Y","protein":"Y","workout":"N",
         "water":3,"sleep":8,"steps":5000,"weight":61,"screen":1.5,"notes":"missing workout",
         "habitsMet":5,"score":0.714,"status":"On Track","streak":0},
        {"date":"2026-08-19","office":null,"breakfast":null,"lunch":null,"protein":null,"workout":null,
         "water":"4.5","sleep":null,"steps":null,"weight":null,"screen":null,"notes":null,
         "habitsMet":null,"score":null,"status":null,"streak":null}
      ],
      "expenses":[{"row":5,"date":"2026-08-15","description":"Hotel","category":"Rent & Utilities",
                   "method":"Cash","qar":500,"inr":13050,"notes":"2 Days Spent"}],
      "salary":[{"row":5,"month":"2026-08","label":"Aug 2026","salary":2500,"otherIncome":1000,"totalIncome":3500,
                 "expenses":2015,"remittanceQar":1000,"remittanceInr":26100,"netSavings":485,"savingsRate":0.139,
                 "goalSavings":3325,"cumulative":485,"status":"Below goal"}],
      "weekly":{"headers":["Week","Avg Habit Score"],"rows":[["1","75.0%"]]},
      "monthly":{"headers":["Month"],"rows":[["Aug 2026"]]}
    }}
    """

    private struct Envelope: Decodable { let data: SheetSnapshot }

    private func decode() throws -> SheetSnapshot {
        try JSONDecoder().decode(Envelope.self, from: Data(json.utf8)).data
    }

    @Test func decodesSettingsIncludingNumbersTypedAsText() throws {
        let settings = try decode().settings
        #expect(settings.monthlyLimit == 620)
        #expect(settings.savingsGoal == 0.95)
        #expect(settings.startDate == "2026-08-15")
    }

    @Test func blankDaysDecodeAsUnlogged() throws {
        let days = try decode().days
        #expect(days[0].isLogged)
        #expect(days[0].answer(.workout) == "N")
        #expect(days[1].isLogged == false)
        #expect(days[1].water == 4.5, "a number typed as text still reads")
        #expect(days[1].answer(.office) == nil)
    }

    @Test func ledgerAndSalaryRowsKeepTheirSheetRow() throws {
        let snapshot = try decode()
        #expect(snapshot.expenses.first?.row == 5)
        #expect(snapshot.salary.first?.status == "Below goal")
    }

    @Test func sheetDisplayTextParsesBackToNumbers() {
        #expect(SheetFormat.parse("2,015.00") == 2015)
        #expect(SheetFormat.parse("75.0%") == 0.75)
        #expect(SheetFormat.parse("(1,915.00)") == -1915)
        #expect(SheetFormat.parse("-") == nil)
    }
}
