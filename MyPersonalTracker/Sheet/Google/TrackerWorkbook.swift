//
//  TrackerWorkbook.swift
//  MyPersonalTracker
//
//  Makes the tracker's workbook in the signed-in person's own Google Drive,
//  with no Apps Script: Blueprint.xlsx (bundled; exported from a sheet that
//  `setupNewWorkbook` had just built) is imported as a Google Sheet, then given
//  this person's dates and the one chart an .xlsx import leaves out.
//
//  To refresh the blueprint after the workbook's design changes: run
//  setupNewWorkbook in a clean sheet, File ▸ Download ▸ Microsoft Excel, and
//  replace Resources/Blueprint.xlsx.
//

import Foundation

enum TrackerWorkbook {
    static let title = "Life & Finance Command Center"
    /// The blueprint's Daily Tracker and budget hold this many dated rows (5…34).
    static let periodDays = 30
    static let firstRow = 5

    /// Cells whose formulas only exist in Google Sheets. Excel files carry them
    /// wrapped in `__xludf.DUMMYFUNCTION`, which the import must turn back into
    /// the real formula.
    static let googleOnlyFormulaCells = ["Daily Tracker!N5", "Daily Tracker!N34", "Settings!B60"]

    enum SetupError: LocalizedError {
        case noBlueprint, formulasNotRestored(String)
        var errorDescription: String? {
            switch self {
            case .noBlueprint: "The workbook design (Blueprint.xlsx) is missing from the app."
            case .formulasNotRestored(let cell): "Google didn't restore the formula in \(cell) after the import."
            }
        }
    }

    /// The tracker this app made in the user's Drive before (on this or another device), or a new one.
    static func findOrCreate(api: GoogleSheetsAPI? = nil, progress: (String) -> Void = { _ in }) async throws -> String {
        let api = api ?? GoogleSheetsAPI()
        if let existing = try await api.spreadsheets(named: title).first {
            progress("Found your sheet in Google Drive")
            return existing.id
        }
        return try await create(api: api, progress: progress).id
    }

    /// Creates the workbook in the user's Drive with a period of `periodDays`
    /// starting on `startDay`. `progress` gets a line per step.
    static func create(api: GoogleSheetsAPI? = nil, startingOn startDay: Date = .now,
                       progress: (String) -> Void = { _ in }) async throws -> GoogleSheetsAPI.Created {
        let api = api ?? GoogleSheetsAPI()
        guard let url = Bundle.main.url(forResource: "Blueprint", withExtension: "xlsx"),
              let blueprint = try? Data(contentsOf: url) else { throw SetupError.noBlueprint }

        let created = try await api.importSpreadsheet(name: title, xlsx: blueprint)
        progress("Created “\(title)” in your Drive")

        let (start, end) = period(startingOn: startDay)
        try await api.write(created.id, range: GoogleSheetsAPI.a1("Settings", "B6:B7"), rows: [[start], [end]])
        progress("Period set: \(start) to \(end)")

        let tabs = Dictionary(try await api.tabIDs(created.id).map { ($0.title, $0.id) }, uniquingKeysWith: { first, _ in first })
        var requests: [[String: Any]] = [["addSheet": ["properties": ["title": appDataTab, "hidden": true, "index": tabs.count]]]]
        if let dashboard = tabs["Dashboard"], let daily = tabs["Daily Tracker"] {
            requests.append(habitScoreChart(dashboard: dashboard, daily: daily))
        }
        try await api.batchUpdate(created.id, requests: requests)
        try await api.write(created.id, [(GoogleSheetsAPI.a1(appDataTab, "A1"), appData(start: start))], typed: true)
        progress("Added the Daily Habit Score chart and the app's bookkeeping")

        for cell in googleOnlyFormulaCells {
            let parts = cell.split(separator: "!", maxSplits: 1).map(String.init)
            let formula = try await api.readFormulas(created.id, range: GoogleSheetsAPI.a1(parts[0], parts[1])).first?.first as? String ?? ""
            if formula.contains("DUMMYFUNCTION") || !formula.hasPrefix("=") { throw SetupError.formulasNotRestored(cell) }
        }
        progress("Checked the Google-only formulas came through")
        return created
    }

    /// The hidden tab where the sheet's script keeps its own settings (PropertiesService, see gas-shim.js).
    static let appDataTab = "App Data"

    /// What setupNewWorkbook leaves in the document properties, so the script
    /// doesn't redo that work on the first sync. Stored as text ('…).
    static func appData(start: String) -> [[Any]] {
        [["Key", "Value — kept by the app, please leave as it is"]] + [
            ("SALARY_DEFAULTS_FIXED", "1"), ("SPLIT_FORMULAS", "2"), ("DAILY_FORMULAS", "3"), ("HABIT_FORMULAS", "2"),
            ("CURRENCY_FORMULAS_DYNAMIC", "1"), ("CURRENCY_LABELS", #"{"main":"QAR","home":"INR"}"#), ("RATE_FOR", "QAR/INR"),
            ("ROWS_START", start),
        ].map { ["doc:\($0.0)", "'\($0.1)"] }
    }

    /// "2026-10-19" and the day `periodDays - 1` later, as typed into Settings.
    static func period(startingOn day: Date, calendar: Calendar = .current) -> (start: String, end: String) {
        let start = calendar.startOfDay(for: day)
        let end = calendar.date(byAdding: .day, value: periodDays - 1, to: start) ?? start
        let format = Date.ISO8601FormatStyle(timeZone: calendar.timeZone).year().month().day()
        return (start.formatted(format), end.formatted(format))
    }

    /// The Dashboard's line chart of the daily score (Daily Tracker column O by
    /// date in column A), as 07_Dashboard.gs draws it at row 34.
    static func habitScoreChart(dashboard: Int, daily: Int) -> [String: Any] {
        func column(_ index: Int) -> [String: Any] {
            ["sourceRange": ["sources": [["sheetId": daily, "startRowIndex": firstRow - 1, "endRowIndex": firstRow - 1 + periodDays,
                                          "startColumnIndex": index, "endColumnIndex": index + 1]]]]
        }
        return ["addChart": ["chart": [
            "spec": [
                "title": "Daily Habit Score",
                "basicChart": [
                    "chartType": "LINE",
                    "legendPosition": "NO_LEGEND",
                    "headerCount": 0,
                    "axis": [["position": "BOTTOM_AXIS"],
                             ["position": "LEFT_AXIS", "viewWindowOptions": ["viewWindowMin": 0, "viewWindowMax": 1]]],
                    "domains": [["domain": column(0)]],
                    "series": [["series": column(14), "targetAxis": "LEFT_AXIS",
                                "colorStyle": ["rgbColor": ["red": 0.184, "green": 0.333, "blue": 0.592]]]],
                ],
            ],
            "position": ["overlayPosition": ["anchorCell": ["sheetId": dashboard, "rowIndex": 33, "columnIndex": 0],
                                             "widthPixels": 620, "heightPixels": 260]],
        ]]]
    }
}
