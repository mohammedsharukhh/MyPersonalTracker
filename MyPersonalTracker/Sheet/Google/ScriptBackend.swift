//
//  ScriptBackend.swift
//  MyPersonalTracker
//
//  The sheet's own Apps Script (bundled in Resources/Backend), run inside the
//  app for a workbook in the user's Drive: the workbook is loaded through the
//  Sheets API, the script answers the request exactly as the web app would,
//  and the changes it made are sent back to the sheet in the same order.
//  One copy of the logic serves the web app and the Google sign-in sheet.
//
//  Keep Resources/Backend/*.js in step with life-finance-command-center/*.gs
//  (the auto-deploy hook copies them).
//

import Foundation
import JavaScriptCore

/// One script run at a time on this phone. A Google sign-in sheet has no server lock (gas-shim's
/// LockService does nothing): two runs reading the workbook at once would both write the same free row.
/// shortcut: guards one phone only; two phones writing the same sheet at once can still collide, use the web app for that.
enum ScriptLane {
    private static var busy = false
    private static var waiting: [CheckedContinuation<Void, Never>] = []

    static func run<T>(_ work: () async throws -> T) async throws -> T {
        if busy { await withCheckedContinuation { waiting.append($0) } }
        busy = true
        // Handed straight to the next in line, so nothing can slip in between.
        defer { if waiting.isEmpty { busy = false } else { waiting.removeFirst().resume() } }
        return try await work()
    }
}

struct ScriptBackend {
    let spreadsheetID: String
    var api = GoogleSheetsAPI()

    enum ScriptError: LocalizedError {
        case notLoaded(String), failed(String), resetNeeded
        var errorDescription: String? {
            switch self {
            case .notLoaded(let file): "Part of the app's sheet logic (\(file)) is missing."
            case .failed(let message): message
            case .resetNeeded: "Reset is handled by the app."
            }
        }
    }

    /// The web app's reply to one request: `{ ok, data | ack | error }`.
    @discardableResult
    func request(_ action: String, payload: [String: Any] = [:], reply: String? = nil) async throws -> [String: Any] {
        try await ScriptLane.run {
            var workbook = try await loadWorkbook()
            workbook["uploads"] = try await uploadPhotos(in: payload)
            let result = try ScriptRuntime.shared.run(workbook: workbook, action: action, payload: payload, reply: reply)
            try await apply(result.ops)
            if result.reply["error"] as? String == "RESET_NATIVE" { throw ScriptError.resetNeeded }
            return result.reply
        }
    }

    /// Runs a script function by name on the workbook (setupNewWorkbook, continueSetup…) and applies its changes.
    func runFunction(_ name: String) async throws {
        try await ScriptLane.run {
            let workbook = try await loadWorkbook()
            let result = try ScriptRuntime.shared.call(workbook: workbook, function: name)
            try await apply(result.ops)
        }
    }

    /// The snapshot, with every formula worked out by the sheet. Looking may post
    /// monthly bills that fell due (or set up bookkeeping); then it looks again.
    func snapshot(progress: (@Sendable (SheetLoadProgress) -> Void)? = nil) async throws -> SheetSnapshot {
        var reply = try await ScriptLane.run { try await snapshotReply(progress: progress) }
        if reply.changed {
            progress?(.finishing)
            reply = try await ScriptLane.run { try await snapshotReply(progress: nil) }
        }
        guard reply.envelope["ok"] as? Bool == true, let data = reply.envelope["data"] else {
            throw ScriptError.failed(reply.envelope["error"] as? String ?? "The sheet didn't answer.")
        }
        return try JSONDecoder().decode(SheetSnapshot.self, from: JSONSerialization.data(withJSONObject: data))
    }

    private func snapshotReply(progress: (@Sendable (SheetLoadProgress) -> Void)?) async throws -> (envelope: [String: Any], changed: Bool) {
        progress?(.reading)
        var start = Date.now
        let workbook = try await loadWorkbook()
        SheetStore.log("  read workbook \(SheetStore.seconds(since: start))")
        start = .now
        progress?(.checking)
        let result = try ScriptRuntime.shared.run(workbook: workbook, action: "snapshot", payload: [:], reply: nil)
        SheetStore.log("  ran script \(SheetStore.seconds(since: start)), \(result.ops.count) changes")
        if !result.ops.isEmpty { SheetStore.log("  changes: \(Self.summary(result.ops))") }
        start = .now
        try await apply(result.ops, progress: progress)
        if !result.ops.isEmpty { SheetStore.log("  wrote changes \(SheetStore.seconds(since: start))") }
        return (result.reply, !result.ops.isEmpty)
    }

    /// What a run of changes touches, without any cell contents (debug log): kinds and tabs,
    /// the script's own setting names (App Data, column A) and the kinds of Sheets requests.
    static func summary(_ ops: [[String: Any]]) -> String {
        var kinds: [String: Int] = [:], tabs: [String: Int] = [:], requests: [String: Int] = [:]
        var settings: [String] = []
        var calls = 0, last = ""
        for op in ops {
            let kind = op["t"] as? String ?? "?"
            kinds[kind, default: 0] += 1
            if kind != last { calls += 1; last = kind }
            if let sheet = op["sheet"] as? String { tabs[sheet, default: 0] += 1 }
            if kind == "req", let request = (op["req"] as? [String: Any])?.keys.first { requests[request, default: 0] += 1 }
            if op["sheet"] as? String == TrackerWorkbook.appDataTab, op["col"] as? Int == 1 {
                settings += (op["values"] as? [[Any]] ?? []).compactMap { $0.first as? String }
            }
        }
        func list(_ counts: [String: Int]) -> String {
            counts.sorted { $0.value > $1.value }.map { "\($0.key) \($0.value)" }.joined(separator: ", ")
        }
        return "\(list(kinds)) · about \(calls) calls · tabs: \(list(tabs)) · requests: \(list(requests)) · settings: \(settings.joined(separator: " "))"
    }

    // MARK: Loading

    /// Every tab: values (dates marked), formulas, and the text the sheet shows.
    func loadWorkbook() async throws -> [String: Any] {
        let info = try await api.workbook(spreadsheetID)
        let ranges = info.tabs.map { "'\($0.title.replacingOccurrences(of: "'", with: "''"))'" }
        async let raw = api.read(spreadsheetID, ranges: ranges, render: .raw)
        async let rawText = api.read(spreadsheetID, ranges: ranges, render: .raw, datesAsText: true)
        async let shown = api.read(spreadsheetID, ranges: ranges, render: .shown)
        async let formulas = api.read(spreadsheetID, ranges: ranges, render: .formulas)
        let (values, texts, display, typed) = try await (raw, rawText, shown, formulas)
        let sheets = info.tabs.enumerated().map { i, tab -> [String: Any] in
            [
                "id": tab.id, "title": tab.title, "index": tab.index, "hidden": tab.hidden,
                "maxRows": tab.rows, "maxCols": tab.columns, "frozenRows": tab.frozenRows, "frozenCols": tab.frozenColumns,
                "charts": tab.charts, "cfCount": tab.conditionalRules,
                "values": Self.markDates(values[i], texts: texts[i]),
                "formulas": typed[i].map { row in row.map { cell -> String in
                    guard let text = cell as? String, text.hasPrefix("=") else { return "" }
                    return text
                } },
                "display": display[i].map { row in row.map { "\($0)" } },
            ]
        }
        return ["id": spreadsheetID, "name": info.name, "tz": info.timeZone, "sheets": sheets]
    }

    /// A number that the date-as-text read shows as text is a date or time: sent as {"$s": serial}.
    static func markDates(_ values: [[Any]], texts: [[Any]]) -> [[Any]] {
        values.enumerated().map { r, row in
            row.enumerated().map { c, value -> Any in
                guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return value }
                let text = r < texts.count && c < texts[r].count ? texts[r][c] : nil
                return text is String ? ["$s": number.doubleValue] : value
            }
        }
    }

    // MARK: Photos

    /// Uploads any new photo in the request (a meal's `photo`, Mom & Dad's `newPhotos`)
    /// and returns base64 → Drive id for the script.
    private func uploadPhotos(in payload: [String: Any]) async throws -> [String: String] {
        var uploads: [String: String] = [:]
        if let photo = payload["photo"] as? String, !photo.isEmpty, let data = Data(base64Encoded: photo) {
            let folder = try await api.folder(named: "Life & Finance · Meal photos")
            let name = "\(payload["date"] ?? "") \(payload["meal"] ?? "meal").jpg"
            uploads[photo] = try await api.uploadJPEG(data, name: name, folder: folder)
        }
        if let added = payload["newPhotos"] as? [String: Any], !added.isEmpty {
            let folder = try await api.folder(named: "Life & Finance · Mom & Dad photos")
            for case let base64 as String in added.values {
                guard let data = Data(base64Encoded: base64) else { continue }
                uploads[base64] = try await api.uploadJPEG(data, name: "\(payload["date"] ?? "") \(payload["task"] ?? "photo").jpg", folder: folder)
            }
        }
        return uploads
    }

    // MARK: Applying changes

    /// Sends the script's changes in order: runs of value writes, clears and
    /// Sheets requests each go as one call (big rebuilds in chunks).
    /// `progress` hears how far along it is, by the part of the sheet being written.
    func apply(_ ops: [[String: Any]], progress: (@Sendable (SheetLoadProgress) -> Void)? = nil) async throws {
        guard !ops.isEmpty else { return }
        let info = try await api.workbook(spreadsheetID)
        var used = Set(info.tabs.map(\.id))
        var realID: [Int: Int] = [:]   // the script's temporary ids for new tabs → ids chosen here
        func resolve(_ id: Int) -> Int {
            guard id < 0 else { return id }
            if let real = realID[id] { return real }
            var real = Int.random(in: 100_000...2_000_000_000)
            while used.contains(real) { real = Int.random(in: 100_000...2_000_000_000) }
            used.insert(real)
            realID[id] = real
            return real
        }
        /// Swaps temporary tab ids anywhere in a request.
        func resolved(_ value: Any) -> Any {
            if let dict = value as? [String: Any] {
                var out: [String: Any] = [:]
                for (key, inner) in dict {
                    if ["sheetId", "newSheetId", "sourceSheetId"].contains(key), let id = inner as? Int { out[key] = resolve(id) }
                    else { out[key] = resolved(inner) }
                }
                return out
            }
            if let array = value as? [Any] { return array.map(resolved) }
            return value
        }

        var batch: [[String: Any]] = []
        var sets: [(range: String, rows: [[Any]])] = []
        var clears: [String] = []
        var trash: [String] = []
        func flush() async throws {
            if !sets.isEmpty { try await api.write(spreadsheetID, sets, typed: true); sets = [] }
            if !clears.isEmpty { try await api.clear(spreadsheetID, ranges: clears); clears = [] }
            for start in stride(from: 0, to: batch.count, by: 400) {
                try await api.batchUpdate(spreadsheetID, requests: Array(batch[start..<min(start + 400, batch.count)]))
            }
            batch = []
        }

        var tab: String?
        for (index, op) in ops.enumerated() {
            let type = op["t"] as? String ?? ""
            // Keep the order: a different kind of change sends what's waiting first.
            let switching = (type == "set" && !(clears.isEmpty && batch.isEmpty))
                || (type == "clear" && !(sets.isEmpty && batch.isEmpty))
                || (type == "req" && !(sets.isEmpty && clears.isEmpty))
            if switching {
                progress?(.writing(tab: tab, done: index, total: ops.count))
                try await flush()
            }
            if let sheet = op["sheet"] as? String { tab = sheet }

            switch type {
            case "set":
                let rows = (op["values"] as? [[Any]] ?? []).map { $0.map(Self.typed) }
                sets.append((Self.a1(op["sheet"] as? String ?? "", op["row"] as? Int ?? 1, op["col"] as? Int ?? 1), rows))
            case "clear":
                clears.append(Self.a1(op["sheet"] as? String ?? "", op["row"] as? Int ?? 1, op["col"] as? Int ?? 1,
                                      rows: op["rows"] as? Int ?? 1, cols: op["cols"] as? Int ?? 1))
            case "req":
                if let request = resolved(op["req"] ?? [:]) as? [String: Any] { batch.append(request) }
            case "dropChartsOf":
                // A tab copied in this run: its charts' ids are only known once the copy exists.
                try await flush()
                let ids = try await api.chartIDs(spreadsheetID, sheetID: resolve(op["s"] as? Int ?? 0))
                if !ids.isEmpty { try await api.batchUpdate(spreadsheetID, requests: ids.map { ["deleteEmbeddedObject": ["objectId": $0]] }) }
            case "trash":
                if let id = op["id"] as? String { trash.append(id) }
            default:
                break
            }
        }
        progress?(.writing(tab: tab, done: ops.count - 1, total: ops.count))
        try await flush()
        for id in trash { await api.trash(id) }
    }

    /// A cell as typed: dates as their wall-clock text, which the sheet reads like typing.
    static func typed(_ value: Any) -> Any {
        if let date = (value as? [String: Any])?["$d"] as? String { return date }
        return value
    }

    static func a1(_ sheet: String, _ row: Int, _ col: Int, rows: Int = 1, cols: Int = 1) -> String {
        let from = "\(column(col))\(row)"
        let cells = rows == 1 && cols == 1 ? from : "\(from):\(column(col + cols - 1))\(row + rows - 1)"
        return "'\(sheet.replacingOccurrences(of: "'", with: "''"))'!\(cells)"
    }

    static func column(_ n: Int) -> String {
        var n = n, s = ""
        while n > 0 { let r = (n - 1) % 26; s = String(UnicodeScalar(65 + r)!) + s; n = (n - 1) / 26 }
        return s
    }
}

/// The JavaScript engine with the shim and the sheet's scripts loaded, kept for the app's lifetime.
final class ScriptRuntime {
    static let shared = ScriptRuntime()
    private var context: JSContext?

    struct Result { let reply: [String: Any]; let ops: [[String: Any]] }

    func run(workbook: [String: Any], action: String, payload: [String: Any], reply: String?) throws -> Result {
        let context = try loaded()
        guard let load = context.objectForKeyedSubscript("__load"), let request = context.objectForKeyedSubscript("__request") else {
            throw ScriptBackend.ScriptError.notLoaded("gas-shim.js")
        }
        load.call(withArguments: [workbook])
        if let error = context.exception { context.exception = nil; throw ScriptBackend.ScriptError.failed(error.toString()) }
        let output = request.call(withArguments: [action, payload, reply ?? NSNull()])
        if let error = context.exception { context.exception = nil; throw ScriptBackend.ScriptError.failed(error.toString()) }
        guard let json = output?.toString(), let data = json.data(using: .utf8),
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ScriptBackend.ScriptError.failed("The sheet logic didn't answer.")
        }
        return Result(reply: object["reply"] as? [String: Any] ?? [:], ops: object["ops"] as? [[String: Any]] ?? [])
    }

    /// A script function by name (no arguments), with the changes it made.
    func call(workbook: [String: Any], function: String) throws -> Result {
        let context = try loaded()
        context.objectForKeyedSubscript("__load").call(withArguments: [workbook])
        if let error = context.exception { context.exception = nil; throw ScriptBackend.ScriptError.failed(error.toString()) }
        let output = context.objectForKeyedSubscript("__call").call(withArguments: [function])
        if let error = context.exception { context.exception = nil; throw ScriptBackend.ScriptError.failed("\(function): \(error.toString() ?? "")") }
        guard let data = output?.toString()?.data(using: .utf8),
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ScriptBackend.ScriptError.failed("\(function): no answer")
        }
        return Result(reply: object["reply"] as? [String: Any] ?? [:], ops: object["ops"] as? [[String: Any]] ?? [])
    }

    /// Several requests in a row on one loaded workbook: each sees what the ones before it changed.
    func runSequence(workbook: [String: Any], requests: [(action: String, payload: [String: Any])]) throws -> [Result] {
        let context = try loaded()
        context.objectForKeyedSubscript("__load").call(withArguments: [workbook])
        if let error = context.exception { context.exception = nil; throw ScriptBackend.ScriptError.failed(error.toString()) }
        return try requests.map { request in
            let output = context.objectForKeyedSubscript("__request").call(withArguments: [request.action, request.payload, NSNull()])
            if let error = context.exception { context.exception = nil; throw ScriptBackend.ScriptError.failed("\(request.action): \(error.toString() ?? "")") }
            guard let data = output?.toString()?.data(using: .utf8),
                  let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw ScriptBackend.ScriptError.failed("\(request.action): no answer")
            }
            return Result(reply: object["reply"] as? [String: Any] ?? [:], ops: object["ops"] as? [[String: Any]] ?? [])
        }
    }

    private func loaded() throws -> JSContext {
        if let context { return context }
        guard let context = JSContext() else { throw ScriptBackend.ScriptError.failed("No JavaScript engine.") }
        let log: @convention(block) (String) -> Void = { message in
            #if DEBUG
            print("[sheet] \(message)")
            #endif
        }
        context.setObject(log, forKeyedSubscript: "__nativeLog" as NSString)
        context.evaluateScript("var console = { log: function () { __nativeLog(Array.prototype.slice.call(arguments).join(' ')); } };")
        let urls = Bundle.main.urls(forResourcesWithExtension: "js", subdirectory: nil) ?? []
        guard let shim = urls.first(where: { $0.lastPathComponent == "gas-shim.js" }) else { throw ScriptBackend.ScriptError.notLoaded("gas-shim.js") }
        // The scripts in Apps Script's order (00_Config … 31_Parents), after the shim.
        let scripts = urls.filter { $0.lastPathComponent.range(of: #"^\d\d_.*\.js$"#, options: .regularExpression) != nil }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        guard scripts.count >= 30 else { throw ScriptBackend.ScriptError.notLoaded("the .gs scripts") }
        for url in [shim] + scripts {
            let source = try String(contentsOf: url, encoding: .utf8)
            context.evaluateScript(source, withSourceURL: url)
            if let error = context.exception {
                context.exception = nil
                throw ScriptBackend.ScriptError.failed("\(url.lastPathComponent): \(error.toString() ?? "error")")
            }
        }
        context.evaluateScript("__overrides();")
        if let error = context.exception { context.exception = nil; throw ScriptBackend.ScriptError.failed(error.toString()) }
        self.context = context
        return context
    }
}
