//
//  GoogleSheetsAPI.swift
//  MyPersonalTracker
//
//  The few Google Sheets and Drive REST calls the app needs, signed with
//  GoogleAuth's token: create a spreadsheet, copy one, change its structure,
//  write cells and read them back (formulas are worked out by Google).
//

import Foundation

struct GoogleSheetsAPI {
    var auth = GoogleAuth.shared

    struct Created {
        let id: String
        let url: URL?
        /// Tab name → its numeric sheetId (what batchUpdate requests point at).
        var tabIDs: [String: Int] = [:]
    }

    /// A new spreadsheet in the user's Drive with these tabs, in order.
    func createSpreadsheet(title: String, tabs: [String]) async throws -> Created {
        let body: [String: Any] = [
            "properties": ["title": title],
            "sheets": tabs.map { ["properties": ["title": $0]] },
        ]
        let result = try await send("POST", "https://sheets.googleapis.com/v4/spreadsheets", body: body)
        guard let id = result["spreadsheetId"] as? String else { throw GoogleError.badResponse("No spreadsheet id came back.") }
        var tabIDs: [String: Int] = [:]
        for sheet in result["sheets"] as? [[String: Any]] ?? [] {
            if let properties = sheet["properties"] as? [String: Any], let title = properties["title"] as? String,
               let sheetID = properties["sheetId"] as? Int { tabIDs[title] = sheetID }
        }
        return Created(id: id, url: (result["spreadsheetUrl"] as? String).flatMap(URL.init(string:)), tabIDs: tabIDs)
    }

    /// Drive's files.copy: the copy is made by this app, so `drive.file` covers it from then on.
    func copyFile(id: String, name: String) async throws -> Created {
        let result = try await send("POST", "https://www.googleapis.com/drive/v3/files/\(id)/copy?fields=id,webViewLink",
                                    body: ["name": name])
        guard let newID = result["id"] as? String else { throw GoogleError.badResponse("No file id came back.") }
        return Created(id: newID, url: (result["webViewLink"] as? String).flatMap(URL.init(string:)))
    }

    /// Uploads an .xlsx and has Drive turn it into a Google Sheet in the user's Drive
    /// (created by this app, so `drive.file` covers it from then on).
    func importSpreadsheet(name: String, xlsx: Data) async throws -> Created {
        let boundary = UUID().uuidString
        let metadata = try JSONSerialization.data(withJSONObject: ["name": name, "mimeType": "application/vnd.google-apps.spreadsheet"])
        var body = Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".utf8)
        body += metadata
        body += Data("\r\n--\(boundary)\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n".utf8)
        body += xlsx
        body += Data("\r\n--\(boundary)--".utf8)
        var request = URLRequest(url: URL(string: "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(try await auth.validAccessToken())", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/related; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        request.timeoutInterval = 120
        let result = try await GoogleHTTP.json(request)
        guard let id = result["id"] as? String else { throw GoogleError.badResponse("No file id came back.") }
        return Created(id: id, url: (result["webViewLink"] as? String).flatMap(URL.init(string:)))
    }

    /// The spreadsheet's tab names.
    func tabTitles(_ spreadsheetID: String) async throws -> [String] {
        try await tabIDs(spreadsheetID).map(\.title)
    }

    /// Each tab's name and numeric sheetId, in order.
    func tabIDs(_ spreadsheetID: String) async throws -> [(title: String, id: Int)] {
        let result = try await send("GET", "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)?fields=sheets.properties(title,sheetId)")
        let sheets = result["sheets"] as? [[String: Any]] ?? []
        return sheets.compactMap {
            guard let properties = $0["properties"] as? [String: Any], let title = properties["title"] as? String,
                  let id = properties["sheetId"] as? Int else { return nil }
            return (title, id)
        }
    }

    /// The ids of the charts on one tab.
    func chartIDs(_ spreadsheetID: String, sheetID: Int) async throws -> [Int] {
        let result = try await send("GET", "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)?fields=sheets(properties.sheetId,charts.chartId)")
        let sheet = (result["sheets"] as? [[String: Any]] ?? []).first { ($0["properties"] as? [String: Any])?["sheetId"] as? Int == sheetID }
        return (sheet?["charts"] as? [[String: Any]] ?? []).compactMap { $0["chartId"] as? Int }
    }

    /// spreadsheets.batchUpdate: formatting, validation, new tabs and so on.
    func batchUpdate(_ spreadsheetID: String, requests: [[String: Any]]) async throws {
        _ = try await send("POST", "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID):batchUpdate", body: ["requests": requests])
    }

    /// Writes rows as if typed, so "=SUM(…)" becomes a formula and "2026-10-19" a date.
    func write(_ spreadsheetID: String, range: String, rows: [[Any]]) async throws {
        let path = "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)/values/\(Self.escape(range))?valueInputOption=USER_ENTERED"
        _ = try await send("PUT", path, body: ["range": range, "majorDimension": "ROWS", "values": rows])
    }

    /// The cells' values as shown (formulas already worked out), unformatted.
    func read(_ spreadsheetID: String, range: String) async throws -> [[Any]] {
        let path = "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)/values/\(Self.escape(range))?valueRenderOption=UNFORMATTED_VALUE"
        return try await send("GET", path)["values"] as? [[Any]] ?? []
    }

    enum Render: String {
        /// Numbers as numbers, dates as day serials (days since 1899-12-30).
        case raw = "UNFORMATTED_VALUE"
        /// As the sheet shows them ("75.0%", "2,015.00", "19-Oct").
        case shown = "FORMATTED_VALUE"
        /// Formulas as typed; other cells as their values.
        case formulas = "FORMULA"
    }

    struct Tab {
        let id: Int; let title: String; let index: Int; let hidden: Bool; let rows: Int; let columns: Int
        var frozenRows = 0, frozenColumns = 0
        /// Charts as { chartId, spec }, and how many conditional-format rules the tab has.
        var charts: [[String: Any]] = []
        var conditionalRules = 0
    }
    struct Workbook { let name: String; let timeZone: String; let tabs: [Tab] }

    /// The spreadsheet's name, time zone and tabs.
    func workbook(_ spreadsheetID: String) async throws -> Workbook {
        let result = try await send("GET", "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)?fields="
            + "properties(title,timeZone),sheets(properties(sheetId,title,index,hidden,"
            + "gridProperties(rowCount,columnCount,frozenRowCount,frozenColumnCount)),charts(chartId,spec),conditionalFormats(ranges(sheetId)))")
        let properties = result["properties"] as? [String: Any] ?? [:]
        let tabs = (result["sheets"] as? [[String: Any]] ?? []).compactMap { sheet -> Tab? in
            guard let p = sheet["properties"] as? [String: Any], let id = p["sheetId"] as? Int, let title = p["title"] as? String else { return nil }
            let grid = p["gridProperties"] as? [String: Any] ?? [:]
            var tab = Tab(id: id, title: title, index: p["index"] as? Int ?? 0, hidden: p["hidden"] as? Bool ?? false,
                          rows: grid["rowCount"] as? Int ?? 1000, columns: grid["columnCount"] as? Int ?? 26)
            tab.frozenRows = grid["frozenRowCount"] as? Int ?? 0
            tab.frozenColumns = grid["frozenColumnCount"] as? Int ?? 0
            tab.charts = sheet["charts"] as? [[String: Any]] ?? []
            tab.conditionalRules = (sheet["conditionalFormats"] as? [Any])?.count ?? 0
            return tab
        }
        return Workbook(name: properties["title"] as? String ?? "", timeZone: properties["timeZone"] as? String ?? "UTC", tabs: tabs)
    }

    /// Several ranges in one request, in the order asked. Each comes back trimmed:
    /// trailing empty rows and cells are left out.
    /// `datesAsText`: dates and times come back as the sheet shows them instead of as serials,
    /// which tells a date cell from a number cell.
    func read(_ spreadsheetID: String, ranges: [String], render: Render, datesAsText: Bool = false) async throws -> [[[Any]]] {
        // Escaped by hand: URLComponents leaves "&" alone, and "Salary & Savings" would split the query.
        let query = ranges.map { "ranges=\(Self.escape($0))" }
            + ["valueRenderOption=\(render.rawValue)", "dateTimeRenderOption=\(datesAsText ? "FORMATTED_STRING" : "SERIAL_NUMBER")"]
        let result = try await send("GET", "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)/values:batchGet?" + query.joined(separator: "&"))
        let ranges = result["valueRanges"] as? [[String: Any]] ?? []
        return ranges.map { $0["values"] as? [[Any]] ?? [] }
    }

    /// Writes several ranges in one request. `typed`: as if typed (formulas, dates);
    /// otherwise stored exactly as given, so "1/2" stays text.
    func write(_ spreadsheetID: String, _ data: [(range: String, rows: [[Any]])], typed: Bool = false) async throws {
        guard !data.isEmpty else { return }
        _ = try await send("POST", "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)/values:batchUpdate", body: [
            "valueInputOption": typed ? "USER_ENTERED" : "RAW",
            "data": data.map { ["range": $0.range, "majorDimension": "ROWS", "values": $0.rows] },
        ])
    }

    /// Empties the cells' contents (formatting stays).
    func clear(_ spreadsheetID: String, ranges: [String]) async throws {
        _ = try await send("POST", "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)/values:batchClear", body: ["ranges": ranges])
    }

    /// The cells' formulas as typed ("=SUM(…)"), or their values where there's no formula.
    func readFormulas(_ spreadsheetID: String, range: String) async throws -> [[Any]] {
        let path = "https://sheets.googleapis.com/v4/spreadsheets/\(spreadsheetID)/values/\(Self.escape(range))?valueRenderOption=FORMULA"
        return try await send("GET", path)["values"] as? [[Any]] ?? []
    }

    // MARK: Drive (only files this app made are visible to it)

    /// A folder this app made, by name, or a new one.
    func folder(named name: String) async throws -> String {
        let q = "name = '\(name.replacingOccurrences(of: "'", with: "\\'"))' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        let found = try await send("GET", "https://www.googleapis.com/drive/v3/files?fields=files(id)&q=\(Self.escape(q))")
        if let id = (found["files"] as? [[String: Any]])?.first?["id"] as? String { return id }
        let created = try await send("POST", "https://www.googleapis.com/drive/v3/files?fields=id",
                                     body: ["name": name, "mimeType": "application/vnd.google-apps.folder"])
        guard let id = created["id"] as? String else { throw GoogleError.badResponse("Couldn't make the folder \(name).") }
        return id
    }

    /// Spreadsheets with this name that the app made, newest first.
    func spreadsheets(named name: String) async throws -> [(id: String, modified: String)] {
        let q = "name = '\(name.replacingOccurrences(of: "'", with: "\\'"))' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
        let found = try await send("GET", "https://www.googleapis.com/drive/v3/files?orderBy=modifiedTime%20desc&fields=files(id,modifiedTime)&q=\(Self.escape(q))")
        return (found["files"] as? [[String: Any]] ?? []).compactMap { file in
            (file["id"] as? String).map { ($0, file["modifiedTime"] as? String ?? "") }
        }
    }

    /// Every spreadsheet this app made (any name), newest first.
    func appSpreadsheets() async throws -> [(id: String, name: String)] {
        let q = "mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
        let found = try await send("GET", "https://www.googleapis.com/drive/v3/files?orderBy=modifiedTime%20desc&pageSize=200&fields=files(id,name)&q=\(Self.escape(q))")
        return (found["files"] as? [[String: Any]] ?? []).compactMap { file in
            guard let id = file["id"] as? String else { return nil }
            return (id, file["name"] as? String ?? "")
        }
    }

    /// The spreadsheet as an Excel file.
    func exportXLSX(_ fileID: String) async throws -> Data {
        let type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        var request = URLRequest(url: URL(string: "https://www.googleapis.com/drive/v3/files/\(fileID)/export?mimeType=\(Self.escape(type))")!)
        request.setValue("Bearer \(try await auth.validAccessToken())", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 120
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200 else { throw GoogleError.http(status, "Couldn't export the sheet.") }
        return data
    }

    /// Uploads a JPEG into a folder; returns its Drive id.
    func uploadJPEG(_ data: Data, name: String, folder: String) async throws -> String {
        try await upload(data, name: name, mimeType: "image/jpeg", folder: folder)
    }

    /// Uploads any file into a folder; returns its Drive id.
    func upload(_ data: Data, name: String, mimeType: String, folder: String) async throws -> String {
        let boundary = UUID().uuidString
        var body = Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".utf8)
        body += try JSONSerialization.data(withJSONObject: ["name": name, "parents": [folder], "mimeType": mimeType])
        body += Data("\r\n--\(boundary)\r\nContent-Type: \(mimeType)\r\n\r\n".utf8) + data + Data("\r\n--\(boundary)--".utf8)
        var request = URLRequest(url: URL(string: "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(try await auth.validAccessToken())", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/related; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        request.timeoutInterval = 120
        guard let id = try await GoogleHTTP.json(request)["id"] as? String else { throw GoogleError.badResponse("\(name) didn't upload.") }
        return id
    }

    /// A file's bytes (`thumbnail`: Drive's small preview when there is one).
    func download(_ fileID: String, thumbnail: Bool = false) async throws -> (data: Data, mimeType: String) {
        if thumbnail {
            let info = try await send("GET", "https://www.googleapis.com/drive/v3/files/\(fileID)?fields=thumbnailLink,mimeType")
            if let link = (info["thumbnailLink"] as? String).flatMap(URL.init(string:)) {
                var request = URLRequest(url: link)
                request.setValue("Bearer \(try await auth.validAccessToken())", forHTTPHeaderField: "Authorization")
                if let (data, response) = try? await URLSession.shared.data(for: request),
                   (response as? HTTPURLResponse)?.statusCode == 200 { return (data, "image/jpeg") }
            }
        }
        var request = URLRequest(url: URL(string: "https://www.googleapis.com/drive/v3/files/\(fileID)?alt=media")!)
        request.setValue("Bearer \(try await auth.validAccessToken())", forHTTPHeaderField: "Authorization")
        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        guard http?.statusCode == 200 else { throw GoogleError.http(http?.statusCode ?? 0, "Couldn't download the photo.") }
        return (data, http?.value(forHTTPHeaderField: "Content-Type") ?? "image/jpeg")
    }

    /// Renames a file in Drive.
    func rename(_ fileID: String, to name: String) async throws {
        _ = try await send("PATCH", "https://www.googleapis.com/drive/v3/files/\(fileID)", body: ["name": name])
    }

    /// Moves a file to the Drive trash (quietly does nothing for files this app can't see).
    func trash(_ fileID: String) async {
        _ = try? await send("PATCH", "https://www.googleapis.com/drive/v3/files/\(fileID)", body: ["trashed": true])
    }

    /// The id inside a Google Sheets or Drive link, or the text itself when it already is one.
    static func fileID(from text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if let range = trimmed.range(of: #"/d/([A-Za-z0-9_-]{20,})"#, options: .regularExpression) {
            return String(trimmed[range].dropFirst(3))
        }
        if let id = URLComponents(string: trimmed)?.queryItems?.first(where: { $0.name == "id" })?.value { return id }
        return trimmed.range(of: #"^[A-Za-z0-9_-]{20,}$"#, options: .regularExpression) != nil ? trimmed : nil
    }

    /// "Mom & Dad!A1" → "'Mom & Dad'!A1" (quoted when the tab name needs it).
    static func a1(_ tab: String, _ cells: String) -> String {
        let plain = tab.range(of: #"^[A-Za-z_][A-Za-z0-9_]*$"#, options: .regularExpression) != nil
        return plain ? "\(tab)!\(cells)" : "'\(tab.replacingOccurrences(of: "'", with: "''"))'!\(cells)"
    }

    private static func escape(_ range: String) -> String {
        range.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? range
    }

    private func send(_ method: String, _ url: String, body: [String: Any]? = nil) async throws -> [String: Any] {
        guard let url = URL(string: url) else { throw GoogleError.badResponse("Bad request address.") }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(try await auth.validAccessToken())", forHTTPHeaderField: "Authorization")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        return try await GoogleHTTP.json(request)
    }
}
