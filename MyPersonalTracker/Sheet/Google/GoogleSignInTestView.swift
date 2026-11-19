//
//  GoogleSignInTestView.swift
//  MyPersonalTracker
//
//  Debug only: Settings ▸ Sign in with Google (test). Tries the pieces the
//  "no Apps Script" setup needs — sign in, build a sheet with formulas and
//  read the results back, copy a template — and logs what Google answers.
//

#if DEBUG
import SwiftUI

struct GoogleSignInTestView: View {
    @State private var auth = GoogleAuth.shared
    @State private var log: [LogLine] = []
    @State private var working = false
    @State private var templateLink = ""
    @State private var lastSheet: URL?
    /// The tracker sheet made here last, kept so it can be read and used after a relaunch.
    @AppStorage("debug.trackerSheetID") private var trackerID = ""
    @State private var store = SheetStore.shared
    @Environment(\.openURL) private var openURL
    private let api = GoogleSheetsAPI()

    struct LogLine: Identifiable {
        let id = UUID()
        let ok: Bool?
        let text: String
    }

    var body: some View {
        Form {
            Section {
                if !auth.isConfigured {
                    Label("No OAuth client ID yet", systemImage: "exclamationmark.triangle.fill").foregroundStyle(SheetTheme.warn)
                    Text("Google Cloud ▸ Google Auth Platform ▸ Clients ▸ Create client ▸ iOS, bundle ID \(Bundle.main.bundleIdentifier ?? "?"). Put the client ID in Config/MyPersonalTracker-Info.plist as GoogleOAuthClientID.")
                        .font(.caption).foregroundStyle(.secondary)
                } else if auth.isSignedIn {
                    LabeledContent("Signed in", value: auth.email ?? "Google account")
                    Button("Sign out", role: .destructive) { Task { await auth.signOut(); add(nil, "Signed out and revoked.") } }
                } else {
                    Button("Sign in with Google") { run("Sign in") { try await auth.signIn(); return "Signed in as \(auth.email ?? "?")." } }
                }
            } header: {
                Text("Google account")
            } footer: {
                Text("Asks only for drive.file: the files this app creates or copies — nothing else in your Drive.")
            }

            Section {
                Button("Create my tracker sheet") { run("Tracker") { try await createTracker() } }
                    .fontWeight(.semibold)
                    .disabled(!auth.isSignedIn || working)
            } footer: {
                Text("The real thing: makes “Life & Finance Command Center” in your Drive from the design built into the app, with a 30-day period from today. No Apps Script.")
            }

            if !trackerID.isEmpty {
                Section {
                    Button("Read it back as the app would") { run("Snapshot") { try await readSnapshot() } }
                        .disabled(!auth.isSignedIn || working)
                    Toggle("Use it in the app", isOn: Binding(
                        get: { store.googleSheetID == trackerID },
                        set: { on in
                            store.googleSheetID = on ? trackerID : nil
                            add(nil, on ? "The app now reads and saves this sheet directly." : "The app is back on the web app.")
                            Task { await store.refresh() }
                        }))
                    .disabled(!auth.isSignedIn)
                } header: {
                    Text("Your tracker sheet")
                } footer: {
                    Text("The sheet's own script runs inside the app, so every tab works. Not yet: starting a new period.")
                }
            }

            Section {
                Button("Tidy up test sheets", role: .destructive) { run("Tidy up") { "moved \(try await store.trashOtherGoogleSheets()) other sheets to the Drive trash." } }
                    .disabled(!auth.isSignedIn || working || store.googleSheetID == nil)
            } footer: {
                Text("Moves every other sheet this app made (tests, old copies) to the Google Drive trash. Keeps the sheet in use and backups.")
            }

            Section {
                Button("Run the setup script into a new sheet") { run("Setup") { try await setupScriptSheet() } }
                    .disabled(!auth.isSignedIn || working)
            } footer: {
                Text("Builds the whole workbook from an empty sheet with the sheet's own setup script, every format, drop-down, colour rule and chart sent as Sheets requests — the same path a new period takes.")
            }

            Section {
                Button("Build a test sheet") { run("Build", buildTestSheet) }
                    .disabled(!auth.isSignedIn || working)
            } footer: {
                Text("Creates “Tracker sign-in test” with Settings and Ledger tabs, writes rows and formulas, formats the header, then reads the worked-out totals back.")
            }

            Section {
                TextField("Template link or file ID", text: $templateLink)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.footnote.monospaced())
                Button("Copy the template") { run("Copy", copyTemplate) }
                    .disabled(!auth.isSignedIn || working || GoogleSheetsAPI.fileID(from: templateLink) == nil)
            } footer: {
                Text("Share the template as Anyone with the link ▸ Viewer. Use an empty copy — never your real sheet, which anyone with the link could then read.")
            }

            if let lastSheet {
                Section { Button("Open the last sheet in Google Sheets") { openURL(lastSheet) } }
            }

            Section {
                if working { ProgressView() }
                ForEach(log.reversed()) { line in
                    Label {
                        Text(line.text).font(.caption.monospaced()).textSelection(.enabled)
                    } icon: {
                        Image(systemName: line.ok == nil ? "info.circle" : line.ok! ? "checkmark.circle.fill" : "xmark.octagon.fill")
                            .foregroundStyle(line.ok == nil ? Color.secondary : line.ok! ? SheetTheme.good : SheetTheme.bad)
                    }
                }
            } header: {
                Text("Log")
            }
        }
        .navigationTitle("Sign in with Google (test)")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func add(_ ok: Bool?, _ text: String) { log.append(LogLine(ok: ok, text: text)) }

    /// Runs one test, timing it and logging the outcome or Google's error.
    private func run(_ name: String, _ work: @escaping () async throws -> String) {
        working = true
        Task {
            let start = Date.now
            do {
                let result = try await work()
                add(true, "\(name) — \(result) (\(String(format: "%.1f", Date.now.timeIntervalSince(start)))s)")
            } catch GoogleError.cancelled {
                add(nil, "\(name) — cancelled.")
            } catch {
                add(false, "\(name) — \(error.localizedDescription)")
            }
            working = false
        }
    }

    private func createTracker() async throws -> String {
        let created = try await TrackerWorkbook.create { step in add(nil, step) }
        lastSheet = created.url
        trackerID = created.id
        let tabs = try await api.tabTitles(created.id)
        return "ready with \(tabs.count) tabs (\(tabs.contains("Parents") ? "Parents included" : "no Parents tab!"))."
    }

    private func setupScriptSheet() async throws -> String {
        let created = try await api.createSpreadsheet(title: "Setup script test \(Date.now.formatted(date: .abbreviated, time: .shortened))", tabs: ["Sheet1"])
        lastSheet = created.url
        add(nil, "Created \(created.id); running setupNewWorkbook…")
        let start = Date.now
        try await ScriptBackend(spreadsheetID: created.id).runFunction("setupNewWorkbook")
        add(nil, "Setup sent in \(String(format: "%.0f", Date.now.timeIntervalSince(start)))s; reading it back…")
        let snapshot = try await ScriptBackend(spreadsheetID: created.id).snapshot()
        let tabs = try await api.tabTitles(created.id)
        return "\(tabs.count) tabs, \(snapshot.days.count) days, \(snapshot.dashboard.count) cards."
    }

    private func readSnapshot() async throws -> String {
        let snapshot = try await ScriptBackend(spreadsheetID: trackerID).snapshot()
        let cards = snapshot.dashboard.prefix(3).map { "\($0.label) \($0.value)" }.joined(separator: ", ")
        return "\(snapshot.days.count) days from \(snapshot.settings.startDate ?? "?"), \(snapshot.expenses.count) expenses, "
            + "\(snapshot.salary.count) months, \(snapshot.dashboard.count) cards (\(cards)), weekly \(snapshot.weekly.rows.count) rows."
    }

    private func buildTestSheet() async throws -> String {
        let created = try await api.createSpreadsheet(title: "Tracker sign-in test \(Date.now.formatted(date: .abbreviated, time: .shortened))",
                                                      tabs: ["Settings", "Ledger"])
        lastSheet = created.url
        add(nil, "Created \(created.id)")
        try await api.write(created.id, range: GoogleSheetsAPI.a1("Ledger", "A1:C4"), rows: [
            ["Date", "Item", "Amount"],
            ["2026-10-19", "Coffee", 12],
            ["2026-10-19", "Lunch", 35],
            ["2026-10-19", "Taxi", 20],
        ])
        try await api.write(created.id, range: GoogleSheetsAPI.a1("Settings", "A1:B3"), rows: [
            ["Daily limit", 100],
            ["Spent", "=SUM(Ledger!C2:C)"],
            ["Left", "=B1-B2"],
        ])
        if let ledger = created.tabIDs["Ledger"] {
            try await api.batchUpdate(created.id, requests: [
                ["repeatCell": [
                    "range": ["sheetId": ledger, "startRowIndex": 0, "endRowIndex": 1],
                    "cell": ["userEnteredFormat": ["textFormat": ["bold": true]]],
                    "fields": "userEnteredFormat.textFormat.bold",
                ]],
                ["updateSheetProperties": [
                    "properties": ["sheetId": ledger, "gridProperties": ["frozenRowCount": 1]],
                    "fields": "gridProperties.frozenRowCount",
                ]],
            ])
        }
        let values = try await api.read(created.id, range: GoogleSheetsAPI.a1("Settings", "B2:B3"))
        let spent = (values.first?.first as? NSNumber)?.doubleValue
        let left = (values.dropFirst().first?.first as? NSNumber)?.doubleValue
        guard spent == 67, left == 33 else { throw GoogleError.badResponse("Formulas read back as \(values), expected 67 and 33.") }
        return "formulas worked out by Google: spent 67, left 33."
    }

    private func copyTemplate() async throws -> String {
        guard let id = GoogleSheetsAPI.fileID(from: templateLink) else { throw GoogleError.badResponse("That isn't a Sheets link or file ID.") }
        let copy = try await api.copyFile(id: id, name: "Life & Finance Command Center")
        lastSheet = copy.url
        let tabs = try await api.tabTitles(copy.id)
        return "copied to \(copy.id) with \(tabs.count) tabs: \(tabs.prefix(6).joined(separator: ", "))\(tabs.count > 6 ? "…" : "")."
    }
}
#endif
