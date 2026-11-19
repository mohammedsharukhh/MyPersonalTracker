//
//  DataTools.swift
//  MyPersonalTracker
//
//  Your data: export everything as spreadsheet files (CSV, in one zip) to keep
//  before a reset or before deleting the app, and reset everything to start fresh.
//

import SwiftUI

enum DataExport {
    /// What's on the phone, for when the sheet can't be reached (food: last ~2 months).
    static func tables(from s: SheetSnapshot?) -> [(name: String, rows: [[String]])] {
        guard let s else { return [] }
        func n(_ v: Double?) -> String { v.map { $0.formatted(.number.precision(.fractionLength(0...2)).grouping(.never)) } ?? "" }
        var out: [(String, [[String]])] = []
        out.append(("Daily Tracker", [["Date", "Office", "Breakfast", "Lunch", "Protein", "Workout", "Water", "Sleep", "Steps", "Weight", "Screen", "Notes", "Score", "Status"]] +
            s.days.map { [$0.date, $0.office ?? "", $0.breakfast ?? "", $0.lunch ?? "", $0.protein ?? "", $0.workout ?? "", n($0.water), n($0.sleep),
                          n($0.steps), n($0.weight), n($0.screen), $0.notes ?? "", n($0.score), $0.status ?? ""] }))
        out.append(("Expenses", [["Date", "Description", "Category", "Payment Method", "Amount (\(s.settings.mainCurrency))", "Amount (\(s.settings.homeCurrency))", "Notes"]] +
            s.expenses.filter { $0.row > 0 }.map { [$0.date ?? "", $0.description ?? "", $0.category ?? "", $0.method ?? "", n($0.qar), n($0.inr), $0.notes ?? ""] }))
        out.append(("Salary & Savings", [["Month", "Salary", "Other Income", "Total Income", "Expenses", "Sent Home", "Net Savings", "Savings Rate"]] +
            s.salary.map { [$0.label, n($0.salary), n($0.otherIncome), n($0.totalIncome), n($0.expenses), n($0.remittanceQar), n($0.netSavings), n($0.savingsRate)] }))
        let food = s.foodData
        out.append(("Food Log", [["Date", "Time", "Meal", "Food", "Amount", "Unit", "Calories", "Protein", "Carbs", "Fat", "Cost", "Notes"]] +
            food.log.map { [$0.date, $0.time ?? "", $0.meal, $0.food, n($0.amount), $0.unit ?? "", n($0.calories), n($0.protein), n($0.carbs), n($0.fat), n($0.cost), $0.notes ?? ""] }))
        out.append(("Meal Plan", [["Date", "Meal", "Food", "Amount", "Unit", "Calories", "Protein", "Carbs", "Fat", "Est. Cost", "Eaten"]] +
            food.plan.map { [$0.date, $0.meal, $0.food, n($0.amount), $0.unit ?? "", n($0.calories), n($0.protein), n($0.carbs), n($0.fat), n($0.cost), $0.eaten ? "Y" : ""] }))
        out.append(("My Foods", [["Food", "Amount", "Unit", "Calories", "Protein", "Carbs", "Fat", "Cost"]] +
            food.myFoods.map { [$0.name, n($0.amount), $0.unit ?? "", n($0.calories), n($0.protein), n($0.carbs), n($0.fat), n($0.cost)] }))
        out.append(("Monthly Bills", [["Description", "Category", "Payment Method", "Amount", "Day of Month", "Active", "Last Added", "Notes"]] +
            (s.recurring ?? []).map { [$0.description ?? "", $0.category ?? "", $0.method ?? "", n($0.amount), $0.day.map(String.init) ?? "", $0.active ? "Y" : "N", $0.lastAdded ?? "", $0.notes ?? ""] }))
        out.append(("People", [["Date", "Person", "Type", "Amount", "Note"]] +
            (s.people ?? []).map { [$0.date, $0.person, $0.type.rawValue, n($0.amount), $0.note ?? ""] }))
        out.append(("Set Aside", [["Date", "Bucket", "Amount", "Note", "Goal"]] +
            (s.setAside ?? []).map { e in [e.date, e.bucket, n(e.amount), e.note ?? "", s.goals.first { $0.id == e.goalId }?.name ?? ""] }))
        out.append(("Savings Goals", [["Goal", "Target", "Saved", "Due", "Created", "Done", "Done On", "Notes"]] +
            s.goals.map { [$0.name, n($0.target), n(s.progress(of: $0).saved), $0.due ?? "", $0.created ?? "", $0.done ? "Y" : "N", $0.doneOn ?? "", $0.notes ?? ""] }))
        out.append(("Renewals", [["Item", "Kind", "Expires", "Remind (days before)", "Renewal cost", "Last Renewed", "Notes"]] +
            s.renewalList.map { [$0.item, $0.kind.rawValue, $0.expires, $0.remind.map(String.init).joined(separator: ","), n($0.cost), $0.lastRenewed ?? "", $0.notes ?? ""] }))
        out.append(("Parents", [["What they asked", "Who", "Do on", "Remind at", "Repeat", "Keep reminding", "Asked on", "Done on", "Notes", "Photos", "Cost", "Add to Finance"]] +
            s.parentAsks.sorted { $0.date < $1.date }.map { [$0.task, $0.who.rawValue, $0.date, $0.time ?? "", $0.repeats.rawValue, $0.nag ? "Y" : "",
                                                          $0.asked ?? "", $0.done ?? "", $0.notes ?? "", String($0.photos.count), n($0.cost), $0.finance ? "Y" : ""] }))
        out.append(("Parent Medicines", [["Who", "Medicine", "Dose", "A day", "Left", "Counted on", "Warn (days before)", "Pack of", "Pack cost", "Add to Finance", "Stopped", "Notes"]] +
            s.medicineList.map { [$0.who.rawValue, $0.name, $0.dose ?? "", n($0.perDay), n($0.left), $0.counted, String($0.warn), n($0.pack), n($0.cost),
                                  $0.finance ? "Y" : "", $0.stopped ? "Y" : "", $0.notes ?? ""] }))
        out.append(("Parent Dates", [["What", "Who", "Kind", "Day (MM-DD)", "Year", "Remind (days before)", "Notes"]] +
            s.parentDateList.map { [$0.title, $0.who.rawValue, $0.kind.rawValue, $0.day, $0.year.map(String.init) ?? "", $0.remind.map(String.init).joined(separator: ","), $0.notes ?? ""] }))
        out.append(("Take Home", [["What to bring", "For", "Got it", "Notes", "Added"]] +
            s.takeHomeData.sorted.map { [$0.item, $0.who.rawValue, $0.got ? "Y" : "", $0.notes ?? "", $0.added ?? ""] }))
        out.append(("Parent Health", [["Date", "Time", "Who", "Reading", "Value", "Value 2", "Note"]] +
            s.readingList.map { [$0.date, $0.time ?? "", $0.who.rawValue, $0.kind.rawValue, n($0.value), n($0.value2), $0.note ?? ""] }))
        out.append(("Loans", [["Loan", "Lender", "Monthly", "First payment", "Months", "Paid", "Left", "Paid off", "Borrowed", "Interest %", "Closed"]] +
            (s.loans ?? []).map { [$0.name, $0.lender ?? "", n($0.emi), $0.firstPayment, String($0.months), String($0.paidCount()), n($0.remaining()),
                                   $0.lastPayment, n($0.principal), n($0.rate), $0.closed ? "Y" : ""] }))
        let goals = s.lifeGoalData
        out.append(("Life Goals", [["Goal", "Horizon", "Area", "Why", "By", "Part of", "Status", "Progress", "Created", "Reached On", "Notes"]] +
            goals.goals.map { g in [g.title, g.horizon.rawValue, g.area.rawValue, g.why ?? "", g.due ?? "", goals.goal(g.parentId)?.title ?? "",
                                    g.status.rawValue, SheetFormat.percent(goals.progress(of: g), 0), g.created ?? "", g.doneOn ?? "", g.notes ?? ""] }))
        out.append(("Goal Steps", [["Goal", "Step", "Done", "Done On"]] +
            goals.steps.map { st in [goals.goal(st.goalId)?.title ?? "", st.step, st.done ? "Y" : "", st.doneOn ?? ""] }))
        let planner = s.plannerData
        out.append(("Planner", [["Date", "Time", "Plan", "Where", "Notes", "Done"]] +
            planner.plans.sorted { ($0.date, $0.time ?? "") < ($1.date, $1.time ?? "") }
                .map { [$0.date, $0.time ?? "", $0.title, $0.place ?? "", $0.notes ?? "", $0.done ? "Y" : ""] }))
        out.append(("Outfits", [["Date", "Clothes", "Note"]] +
            planner.outfits.sorted { $0.date < $1.date }.map { [$0.date, planner.pieces(of: $0).joined(separator: " + "), $0.note ?? ""] }))
        out.append(("Wardrobe", [["Item", "Type", "Colour", "Notes", "Added", "Times Worn"]] +
            planner.wardrobe.map { [$0.name, $0.type.rawValue, $0.colour ?? "", $0.notes ?? "", $0.added ?? "", String(planner.timesWorn($0.id))] }))
        let shop = s.shoppingData
        out.append(("Shopping", [["Item", "Quantity", "Aisle", "Status", "Price", "Added", "Bought On", "Notes"]] +
            shop.items.map { [$0.item, $0.quantity ?? "", $0.category, $0.status.rawValue, n($0.price), $0.added ?? "", $0.boughtOn ?? "", $0.notes ?? ""] }))
        out.append(("Shopping Trips", [["Date", "Shop", "Items", "Total", "Paid With", "Notes"]] +
            shop.trips.map { [$0.date, $0.store ?? "", String($0.items), n($0.total), $0.method ?? "", $0.notes ?? ""] }))
        let study = s.studyData
        out.append(("Study Subjects", [["Subject", "Why", "Book, course or link", "Weekly Target (h)", "Target Date", "Status", "Started"]] +
            study.subjects.map { [$0.name, $0.why ?? "", $0.resource ?? "", n($0.weeklyHours), $0.targetDate ?? "", $0.status.rawValue, $0.started ?? ""] }))
        out.append(("Study Topics", [["Subject", "Topic", "Stage", "Confidence", "Last Studied", "Next Review", "Reviews", "Notes", "Link"]] +
            study.topics.map { [study.subject($0.subjectId)?.name ?? "", $0.topic, $0.stage.rawValue, $0.confidence.map(String.init) ?? "",
                                $0.lastStudied ?? "", $0.nextReview ?? "", String($0.reviews), $0.notes ?? "", $0.link ?? ""] }))
        out.append(("Study Log", [["Date", "Subject", "Topic", "Minutes", "Kind", "Focus", "Notes", "Score"]] +
            study.sessions.map { [$0.date, $0.subject ?? "", $0.topic ?? "", n($0.minutes), $0.kind.rawValue, $0.focus.map(String.init) ?? "", $0.notes ?? "", n($0.score)] }))
        out.append(("Study Goals", [["Goal", "Kind", "Subject", "Target", "Start", "Due", "Done", "Done On", "Notes"]] +
            study.goals.map { [$0.title, $0.kind.rawValue, study.subject($0.subjectId)?.name ?? "", n($0.target), $0.start ?? "", $0.due ?? "",
                               $0.done ? "Y" : "", $0.doneOn ?? "", $0.notes ?? ""] }))
        return out.map { (name: $0.0, rows: $0.1) }
    }

    /// One CSV per table (UTF-8 with BOM, so Excel shows ₹ and Arabic correctly), zipped.
    static func zip(_ tables: [(name: String, rows: [[String]])]) throws -> URL {
        let stamp = Date.now.formatted(.iso8601.year().month().day())
        let base = FileManager.default.temporaryDirectory.appending(path: "export-\(UUID().uuidString)")
        let folder = base.appending(path: "Life & Finance export \(stamp)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        for table in tables {
            let csv = table.rows.map { row in row.map(escape).joined(separator: ",") }.joined(separator: "\r\n")
            try Data(("\u{FEFF}" + csv).utf8).write(to: folder.appending(path: "\(table.name).csv"))
        }
        // The system zips a folder when it's read "for uploading".
        var result: URL?
        var coordinatorError: NSError?
        var copyError: Error?
        NSFileCoordinator().coordinate(readingItemAt: folder, options: .forUploading, error: &coordinatorError) { zipped in
            let target = base.appending(path: "Life & Finance export \(stamp).zip")
            do {
                try FileManager.default.copyItem(at: zipped, to: target)
                result = target
            } catch { copyError = error }
        }
        if let error = coordinatorError ?? copyError { throw error }
        guard let result else { throw CocoaError(.fileWriteUnknown) }
        return result
    }

    private static func escape(_ value: String) -> String {
        guard value.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" || $0 == "\r" }) else { return value }
        return "\"" + value.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }
}

/// More ▸ Your data: export, and reset everything.
struct DataToolsView: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var exporting = false
    @State private var exportURL: URL?
    @State private var exportNote: String?
    @State private var resetting = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button { Task { await export() } } label: {
                        HStack {
                            Label("Export all my data", systemImage: "square.and.arrow.down.on.square")
                            Spacer()
                            if exporting { ProgressView() }
                        }
                    }
                    .disabled(exporting)
                    if let exportURL {
                        ShareLink(item: exportURL) {
                            Label("Save or share the export (\(exportURL.lastPathComponent))", systemImage: "square.and.arrow.up")
                        }
                        .fontWeight(.semibold)
                    }
                    if let exportNote { Text(exportNote).font(.caption).foregroundStyle(SheetTheme.warn) }
                } header: {
                    Text("Export")
                } footer: {
                    Text("Every table — days, expenses, budget, salary & savings, summaries, food log, meal plan, your foods, monthly bills, friends & family money, and study (subjects, concepts, sessions, goals) — as spreadsheet files (CSV) in one zip. Save it to Files, Google Drive or email before resetting or deleting the app. Meal photos stay in your Google Drive folder.")
                }

                Section {
                    Button(role: .destructive) { resetting = true } label: {
                        Label("Reset all data…", systemImage: "arrow.counterclockwise.circle")
                    }
                } header: {
                    Text("Start fresh")
                } footer: {
                    Text("Erases everything in your Google Sheet and on this phone, and starts a new 30-day period from today. Settings go back to defaults; the connection stays.")
                }
            }
            .navigationTitle("Your data")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .sheet(isPresented: $resetting) { ResetDataView(store: store, onExport: { Task { await export() } }) { dismiss() } }
        }
        .tint(SheetTheme.navy)
    }

    private func export() async {
        exporting = true
        defer { exporting = false }
        let result = await store.exportTables()
        guard !result.tables.isEmpty else {
            exportNote = "Nothing to export yet — connect your sheet first."
            return
        }
        do {
            exportURL = try DataExport.zip(result.tables)
            exportNote = result.complete ? nil : "Couldn't reach the sheet, so this export is what's on this phone (food: the last two months)."
        } catch {
            exportNote = "Couldn't create the export: \(error.localizedDescription)"
        }
    }
}

/// The reset itself: says exactly what goes, offers an export, and needs "RESET" typed.
private struct ResetDataView: View {
    let store: SheetStore
    let onExport: () -> Void
    let onDone: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirmation = ""
    @State private var working = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label("Every day you logged", systemImage: "checklist")
                    Label("Every expense, income entry and monthly bill", systemImage: "banknote")
                    Label("Every meal, meal plan, saved food and shopping list", systemImage: "fork.knife")
                    Label("Everything with friends & family", systemImage: "person.2")
                    Label("Every subject, concept, study session and goal", systemImage: "graduationcap")
                    Label("Old period archives, targets and currencies", systemImage: "archivebox")
                } header: {
                    Text("This permanently erases")
                } footer: {
                    Text(store.usesGoogleSheet
                         ? "You get a fresh, empty sheet with a 30-day period from today. The old one goes to your Google Drive trash, where you can still get it back for 30 days."
                         : "Your sheet is rebuilt with a fresh 30-day period from today — it takes a few minutes. This can't be undone.")
                }
                Section {
                    Button { onExport(); dismiss() } label: { Label("Export my data first", systemImage: "square.and.arrow.down") }
                }
                Section {
                    TextField("Type RESET to confirm", text: $confirmation)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    Button(role: .destructive) {
                        working = true
                        Task {
                            let ok = await store.resetAllData()
                            working = false
                            if ok { dismiss(); onDone() }
                        }
                    } label: {
                        HStack {
                            Text("Erase everything and start fresh").fontWeight(.semibold)
                            Spacer()
                            if working { ProgressView() }
                        }
                    }
                    .disabled(confirmation.trimmingCharacters(in: .whitespaces).uppercased() != "RESET" || working)
                    if let error = store.errorMessage, !working { Text(error).font(.caption).foregroundStyle(SheetTheme.bad) }
                }
            }
            .navigationTitle("Reset all data")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
        .tint(SheetTheme.navy)
    }
}
