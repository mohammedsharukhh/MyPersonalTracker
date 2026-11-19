//
//  SheetStore.swift
//  MyPersonalTracker
//
//  Talks to the Google Sheet through its Apps Script web app. Reads come back
//  as one snapshot; every write returns the recalculated snapshot, so the app
//  shows the sheet's formulas' answers the moment a save lands.
//
//  Everyday writes (days, expenses, salary) never wait for the network: they
//  go into a queue saved on disk, show on screen at once (an estimate until the
//  sheet answers), and are sent in order — straight away when online, later
//  when not. Each carries a client id so a retry never writes twice. Deletes
//  wait a few seconds first, which is what makes Undo possible.
//
//  Rare, structural writes (settings, currency, new period, recurring rules)
//  still go straight to the sheet and need a connection.
//

import Foundation
import Network
import Security
import UserNotifications

@MainActor
@Observable
final class SheetStore {
    static let shared = SheetStore()

    /// What the screens show: the sheet's last answer with queued writes applied on top.
    private(set) var snapshot: SheetSnapshot?
    /// The sheet's last answer, without anything still queued.
    private var confirmed: SheetSnapshot?
    private(set) var isLoading = false
    /// What the current load is doing, for screens with nothing to show yet.
    private(set) var loadProgress: SheetLoadProgress?
    private(set) var isSaving = false
    var errorMessage: String?
    private(set) var lastSynced: Date?
    /// The sheet refused the key. Cleared by a successful sync or a new key.
    private(set) var keyRejected = false

    /// Writes not yet confirmed by the sheet, oldest first.
    private(set) var pending: [PendingWrite] = []
    /// Saved on the sheet (it answered "got it") but not yet in a downloaded snapshot: still shown on top of it.
    @ObservationIgnored private var landed: [PendingWrite] = []
    @ObservationIgnored private var transientRetries = 0
    /// The write being sent right now. It lands even if it's taken out of the queue meanwhile.
    @ObservationIgnored private var sending: PendingWrite?
    private(set) var isSyncing = false
    /// The last send failed for lack of a connection; queued writes wait.
    private(set) var isOffline = false
    /// The sheet is being set up or reset: "5 of 13", or "starting". Cleared when data arrives.
    private(set) var setupProgress: String?
    private var setupPoll: Task<Void, Never>?

    /// The web app's `/exec` URL.
    var webAppURL: String {
        didSet {
            UserDefaults.standard.set(webAppURL, forKey: Self.urlKey)
            if webAppURL != oldValue { resetData() }
        }
    }
    /// Kept in the Keychain: it unlocks read and write on the whole sheet.
    var apiKey: String {
        didSet {
            Keychain.set(apiKey, for: Self.keyAccount)
            // A different (or removed) key may mean a different sheet, so the
            // old data must not stay on screen as if it belonged to it.
            if apiKey != oldValue { resetData() }
        }
    }

    /// A tracker sheet in the user's own Drive, reached with their Google sign-in
    /// instead of the Apps Script web app: the same script runs in the app (ScriptBackend). Nil: the web app.
    var googleSheetID: String? {
        didSet {
            UserDefaults.standard.set(googleSheetID, forKey: Self.googleSheetKey)
            if googleSheetID != oldValue { resetData() }
        }
    }
    var usesGoogleSheet: Bool { googleSheetID != nil }
    /// The Apps Script web app was chosen in Settings ▸ Your sheet ▸ Advanced. Its key alone isn't
    /// enough: the Keychain keeps it after signing out of Google, and even after reinstalling.
    var webAppChosen: Bool {
        didSet { UserDefaults.standard.set(webAppChosen, forKey: Self.webAppChosenKey) }
    }

    /// Settings ▸ Your sheet ▸ Advanced: the user's own Apps Script web app, from now on.
    func useWebApp(url: String, key: String) {
        googleSheetID = nil
        webAppURL = url
        apiKey = key
        webAppChosen = true
    }
    private var backend: ScriptBackend? { googleSheetID.map { ScriptBackend(spreadsheetID: $0) } }

    var isConfigured: Bool {
        SheetConnection.of(googleSheet: usesGoogleSheet, signedIn: GoogleAuth.shared.isSignedIn, webAppChosen: webAppChosen,
                           webAppURL: webAppURL, apiKey: apiKey) != .none
    }
    /// No usable connection: no key yet, or the sheet refused it.
    var needsConnection: Bool { !isDemo && (!isConfigured || keyRejected) }

    private static let urlKey = "sheet.webAppURL"
    private static let keyAccount = "sheet.apiKey"
    private static let googleSheetKey = "sheet.googleSheetID"
    private static let webAppChosenKey = "sheet.webAppChosen"
    /// Your own Apps Script web app deployment (Resources/Backend), set in Settings. Empty in the public repo.
    static let defaultURL = ""
    /** Old deployments whose saved links should be dropped. */
    private static let retiredURLs: [String] = []

    /// Debug `-sheetDemo`: sample data, no network, connect card hidden.
    private(set) var isDemo = false
    private let monitor = NWPathMonitor()
    private var retryTask: Task<Void, Never>?

    private init() {
        let saved = UserDefaults.standard.string(forKey: Self.urlKey)
        webAppURL = saved.flatMap { Self.retiredURLs.contains($0) ? nil : $0 } ?? Self.defaultURL
        let savedKey = Keychain.get(Self.keyAccount) ?? ""
        let savedSheet = UserDefaults.standard.string(forKey: Self.googleSheetKey)
        apiKey = savedKey
        googleSheetID = savedSheet
        if let chosen = UserDefaults.standard.object(forKey: Self.webAppChosenKey) as? Bool {
            webAppChosen = chosen
        } else {
            // First run of this version: a web app that loaded on this install stays in use.
            let cache = URL.applicationSupportDirectory.appending(path: "sheet-snapshot.json")
            webAppChosen = SheetConnection.chosenOnUpgrade(googleSheet: savedSheet != nil, hasKey: !savedKey.isEmpty,
                                                           hadData: FileManager.default.fileExists(atPath: cache.path))
            UserDefaults.standard.set(webAppChosen, forKey: Self.webAppChosenKey)
        }
        // Cached data is only shown for a saved key; without one it's stale.
        if isConfigured { loadCache() }
        pending = Self.loadQueue()
        landed = Self.loadLanded()
        #if DEBUG
        if SheetDemoData.isEnabled {
            confirmed = SheetDemoData.snapshot
            isDemo = true
        }
        #endif
        recompute()
        // Back online: send whatever waited.
        monitor.pathUpdateHandler = { path in
            guard path.status == .satisfied else { return }
            Task { @MainActor in
                let store = SheetStore.shared
                if store.isOffline || !store.pending.isEmpty { await store.flush() }
            }
        }
        monitor.start(queue: DispatchQueue(label: "sheet.network"))
    }

    // MARK: - Reads

    func refresh() async {
        if isDemo { return }
        guard isConfigured else {
            resetData()
            return
        }
        // Send queued writes first, so the snapshot fetched after includes them.
        await flush()
        isLoading = true
        loadProgress = .reading
        defer { isLoading = false; loadProgress = nil }
        refreshesStarted += 1
        let generation = refreshesStarted
        let writesBefore = writesLanded
        let started = Date.now
        Self.log("refresh \(generation) started")
        do {
            let fetched: SheetSnapshot
            if let backend {
                let report: @Sendable (SheetLoadProgress) -> Void = { step in Task { @MainActor in self.showProgress(step, of: generation) } }
                let waiting: @Sendable (TimeInterval) -> Void = { seconds in
                    Task { @MainActor in self.showProgress(.waiting(seconds: Int(seconds), during: self.loadProgress), of: generation) }
                }
                fetched = try await GoogleRetry.$onWait.withValue(waiting) {
                    try await viaGoogle { try await backend.snapshot(progress: report) }
                }
            } else {
                guard let url = URL(string: webAppURL) else { throw SheetError.message("The web app URL isn't valid.") }
                fetched = try await send(postRequest(url: url, action: "snapshot", payload: [:], timeout: 60))
            }
            // Read before a save landed (or overtaken by a newer refresh): showing it would
            // undo what was just saved. The save's own reply already brought the sheet as it is now.
            // With nothing on screen yet (just connected), it's shown anyway: saves still on their
            // way stay on top of it, and the newer refresh replaces it when it comes.
            let overtaken = generation != refreshesStarted || writesBefore != writesLanded || isSyncing
            if overtaken, confirmed != nil {
                Self.log("refresh \(generation) dropped after \(Self.seconds(since: started)): a newer refresh or a save overtook it")
                return
            }
            Self.log("refresh \(generation) shown after \(Self.seconds(since: started))")
            if !overtaken {
                landed = []
                persistLanded()
            }
            apply(fetched)
            isOffline = false
        } catch {
            Self.log("refresh \(generation) failed after \(Self.seconds(since: started)): \(error.localizedDescription)")
            handle(error)
        }
    }

    /// Only the newest load speaks, and only while it's still going.
    private func showProgress(_ step: SheetLoadProgress, of generation: Int) {
        guard isLoading, generation == refreshesStarted else { return }
        loadProgress = step
    }

    static let busyMessage = "Google is busy, so your sheet couldn't load just now. Try again in a minute."

    /// Unit tests run inside the app: it mustn't sync the simulator's real sheet meanwhile.
    nonisolated static var isUnitTesting: Bool { ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil }

    /// Debug builds only: how long the sheet takes, for the Xcode console.
    nonisolated static func log(_ message: String) {
        #if DEBUG
        print("[sheet] \(message)")
        #endif
    }

    nonisolated static func seconds(since start: Date) -> String {
        String(format: "%.1fs", Date.now.timeIntervalSince(start))
    }

    // MARK: - Queued writes

    @discardableResult
    func saveDay(_ day: SheetDay) async -> Bool {
        enqueue("saveDay", [
            "date": day.date,
            "office": day.office ?? "", "breakfast": day.breakfast ?? "", "lunch": day.lunch ?? "",
            "protein": day.protein ?? "", "workout": day.workout ?? "",
            "water": cell(day.water), "sleep": cell(day.sleep), "steps": cell(day.steps),
            "weight": cell(day.weight), "screen": cell(day.screen), "notes": day.notes ?? ""
        ], summary: "Day \(SheetDate.weekdayDay(day.date))")
        return true
    }

    @discardableResult
    func saveExpense(row: Int?, date: String, description: String, category: String,
                     method: String, qar: Double, notes: String) async -> Bool {
        let payload: [String: Any] = [
            "date": date, "description": description, "category": category,
            "method": method, "qar": qar, "notes": notes
        ]
        let summary = "\(description.isEmpty ? category : description) · \(SheetFormat.qar(qar))"
        let spentBefore = spent(on: date, excludingRow: row)
        // An add already on its way can't be changed any more: the edit follows it, and finds its row when it lands.
        if let row, row < 0, sending?.tempRow != row, let index = pending.firstIndex(where: { $0.tempRow == row }) {
            // Still queued: change what will be sent instead of sending twice.
            pending[index].payload = payload
            pending[index].summary = summary
            persistQueue()
            recompute()
        } else if let row {
            enqueue("updateExpense", payload.merging(["row": row]) { $1 }, summary: summary)
        } else {
            nextTempRow -= 1
            enqueue("addExpense", payload, summary: summary, tempRow: nextTempRow)
        }
        notifyIfOverCap(date: date, before: spentBefore, after: spentBefore + qar)
        return true
    }

    /// Deletes after a short grace period; `undoDelete()` cancels it.
    @discardableResult
    func deleteExpense(row: Int) async -> Bool {
        let item = snapshot?.expenses.first { $0.row == row }
        let summary = item.map { "\($0.description ?? $0.category ?? "Expense") · \(SheetFormat.qar($0.qar))" } ?? "Expense"
        if row < 0, sending?.tempRow != row, let index = pending.firstIndex(where: { $0.tempRow == row }) {
            // Never reached the sheet: forget it, but keep it restorable.
            lastDeleted = pending.remove(at: index)
            persistQueue()
            recompute()
        } else {
            enqueue("deleteExpense", ["row": row], summary: summary, delay: Self.undoWindow)
            lastDeleted = pending.last
        }
        undoMessage = "Deleted \(summary)"
        return true
    }

    /// The last delete, while it can still be undone (shown as a toast).
    private(set) var undoMessage: String?
    private var lastDeleted: PendingWrite?
    static let undoWindow: TimeInterval = 5

    func undoDelete() {
        guard let item = lastDeleted else { return }
        if item.action == "deleteExpense" {
            pending.removeAll { $0.id == item.id }
        } else {
            pending.append(item)   // a queued add that was removed
        }
        lastDeleted = nil
        undoMessage = nil
        persistQueue()
        recompute()
        Task { await flush() }
    }

    func dismissUndo() {
        undoMessage = nil
        lastDeleted = nil
    }

    @discardableResult
    func saveSalary(month: String, salary: Double?, otherIncome: Double?, remittance: Double?) async -> Bool {
        enqueue("saveSalary", [
            "month": month,
            "salary": cell(salary), "otherIncome": cell(otherIncome), "remittanceQar": cell(remittance)
        ], summary: "Income \(month)")
        return true
    }

    // MARK: - Food (queued)

    /// Adds or replaces a meal. A new photo (JPEG) travels with the queued write and
    /// is shown from the phone until the sheet has saved it to Drive.
    @discardableResult
    func saveMeal(id: String, date: String, time: String, type: MealType, items: [FoodItem], cost: Double?, notes: String,
                  photo: Data?, removePhoto: Bool, addToFinance: Bool, financeCategory: String, method: String) -> Bool {
        if let photo { MealPhotos.saveLocal(photo, mealId: id) }
        if removePhoto { try? FileManager.default.removeItem(at: MealPhotos.localURL(mealId: id)) }
        var payload: [String: Any] = [
            "mealId": id, "date": date, "time": time, "meal": type.rawValue, "items": items.map(\.entry),
            "notes": notes, "removePhoto": removePhoto,
            "finance": ["add": addToFinance, "category": financeCategory, "method": method]
        ]
        if let cost { payload["cost"] = (cost * 100).rounded() / 100 }
        if let photo { payload["photo"] = photo.base64EncodedString() }
        // Replace an edit of the same meal that hasn't gone yet, keeping its photo if this edit has none.
        if let index = pending.firstIndex(where: { $0.action == "saveMeal" && ($0.payload["mealId"] as? String) == id }) {
            if photo == nil, !removePhoto, let earlier = pending[index].payload["photo"] { payload["photo"] = earlier }
            pending.remove(at: index)
        }
        let names = items.map(\.food).joined(separator: ", ")
        enqueue("saveMeal", payload, summary: "\(type.rawValue): \(names)")
        return true
    }

    @discardableResult
    func deleteMeal(id: String) -> Bool {
        if let index = pending.firstIndex(where: { $0.action == "saveMeal" && ($0.payload["mealId"] as? String) == id }),
           !(onSheet?.foodData.log.contains { $0.mealId == id } ?? false), !isSending("saveMeal", "mealId", id) {
            // Never reached the sheet.
            pending.remove(at: index)
            persistQueue()
            recompute()
        } else {
            enqueue("deleteMeal", ["mealId": id], summary: "Delete meal")
        }
        try? FileManager.default.removeItem(at: MealPhotos.localURL(mealId: id))
        return true
    }

    /// Replaces the planned foods for one date and meal (an empty list clears it).
    @discardableResult
    func savePlan(date: String, type: MealType, items: [FoodItem], eaten: Bool) -> Bool {
        pending.removeAll { $0.action == "savePlan" && ($0.payload["date"] as? String) == date && ($0.payload["meal"] as? String) == type.rawValue }
        enqueue("savePlan", ["date": date, "meal": type.rawValue, "items": items.map(\.entry), "eaten": eaten],
                summary: "Plan \(SheetDate.weekdayDay(date)) \(type.rawValue)")
        return true
    }

    // MARK: - People (queued)

    /// Adds or replaces money lent, borrowed, paid back or given to someone.
    @discardableResult
    func savePeopleEntry(_ entry: PeopleEntry, method: String) -> Bool {
        pending.removeAll { $0.action == "savePeopleEntry" && ($0.payload["id"] as? String) == entry.id }
        enqueue("savePeopleEntry", ["id": entry.id, "date": entry.date, "person": entry.person, "type": entry.type.rawValue,
                                    "amount": entry.amount, "note": entry.note ?? "", "method": method],
                summary: "\(entry.type.rawValue) · \(entry.person) · \(SheetFormat.qar(entry.amount))")
        return true
    }

    @discardableResult
    func deletePeopleEntry(id: String) -> Bool {
        let onSheet = self.onSheet?.people?.contains { $0.id == id } ?? false || isSending("savePeopleEntry", "id", id)
        pending.removeAll { $0.action == "savePeopleEntry" && ($0.payload["id"] as? String) == id }
        if onSheet { enqueue("deletePeopleEntry", ["id": id], summary: "Delete entry") } else { persistQueue(); recompute() }
        return true
    }

    // MARK: - Study (queued)

    /// Rows travel as the model's own JSON, which is also what the sheet expects.
    private static func dictionary<T: Encodable>(_ value: T) -> [String: Any] {
        guard let data = try? JSONEncoder().encode(value),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        return object
    }

    private static func decode<T: Decodable>(_ type: T.Type, _ payload: [String: Any]) -> T? {
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    /// Queues a save, replacing an unsent save of the same row.
    private func enqueueRowSave(_ action: String, _ payload: [String: Any], summary: String) {
        let id = payload["id"] as? String
        pending.removeAll { $0.action == action && ($0.payload["id"] as? String) == id }
        enqueue(action, payload, summary: summary)
    }

    /// Deletes a row: forgets unsent saves, and asks the sheet only if it ever got there.
    private func deleteRow(_ action: String, saveAction: String, id: String, onSheet: Bool, summary: String) {
        pending.removeAll { $0.action == saveAction && ($0.payload["id"] as? String) == id }
        if onSheet || isSending(saveAction, "id", id) { enqueue(action, ["id": id], summary: summary) } else { persistQueue(); recompute() }
    }

    /// The save on its way right now is this one: it will reach the sheet, so a delete must follow it.
    private func isSending(_ action: String, _ key: String, _ value: String) -> Bool {
        sending?.action == action && (sending?.payload[key] as? String) == value
    }

    func saveStudySubject(_ subject: StudySubject) {
        enqueueRowSave("saveStudySubject", Self.dictionary(subject), summary: "Subject \(subject.name)")
    }

    func deleteStudySubject(id: String) {
        let study = onSheet?.study
        // Its unsent topics and goals go with it.
        let topicIds = Set((snapshot?.studyData.topics ?? []).filter { $0.subjectId == id }.map(\.id))
        let goalIds = Set((snapshot?.studyData.goals ?? []).filter { $0.subjectId == id }.map(\.id))
        pending.removeAll {
            let rowId = $0.payload["id"] as? String ?? ""
            return ($0.action == "saveStudyTopic" && topicIds.contains(rowId)) || ($0.action == "saveStudyGoal" && goalIds.contains(rowId))
        }
        deleteRow("deleteStudySubject", saveAction: "saveStudySubject", id: id,
                    onSheet: study?.subjects.contains { $0.id == id } ?? false, summary: "Delete subject")
    }

    func saveStudyTopic(_ topic: StudyTopic) {
        enqueueRowSave("saveStudyTopic", Self.dictionary(topic), summary: "Topic \(topic.topic)")
    }

    func deleteStudyTopic(id: String) {
        deleteRow("deleteStudyTopic", saveAction: "saveStudyTopic", id: id,
                    onSheet: onSheet?.study?.topics.contains { $0.id == id } ?? false, summary: "Delete topic")
    }

    /// Logs a session, and updates the concept it was on (stage, confidence, next review).
    func saveStudySession(_ entry: StudyEntry, topic: StudyTopic? = nil) {
        if let topic { saveStudyTopic(topic) }
        enqueueRowSave("saveStudySession", Self.dictionary(entry),
                     summary: "Study \(StudyFormat.minutes(entry.minutes))\(entry.subject.map { " · \($0)" } ?? "")")
    }

    func deleteStudySession(id: String) {
        deleteRow("deleteStudySession", saveAction: "saveStudySession", id: id,
                    onSheet: onSheet?.study?.sessions.contains { $0.id == id } ?? false, summary: "Delete session")
    }

    func saveStudyGoal(_ goal: StudyGoal) {
        enqueueRowSave("saveStudyGoal", Self.dictionary(goal), summary: "Goal \(goal.title)")
    }

    func deleteStudyGoal(id: String) {
        deleteRow("deleteStudyGoal", saveAction: "saveStudyGoal", id: id,
                    onSheet: onSheet?.study?.goals.contains { $0.id == id } ?? false, summary: "Delete goal")
    }

    // MARK: - Planner (queued)

    func savePlan(_ plan: DayPlan) {
        enqueueRowSave("saveDayPlan", Self.dictionary(plan), summary: "Plan: \(plan.title)")
    }

    func deletePlan(id: String) {
        deleteRow("deleteDayPlan", saveAction: "saveDayPlan", id: id,
                  onSheet: onSheet?.planner?.plans.contains { $0.id == id } ?? false, summary: "Delete plan")
    }

    func saveOutfit(_ outfit: Outfit) {
        enqueueRowSave("saveOutfit", Self.dictionary(outfit), summary: "Outfit for \(SheetDate.short(outfit.date))")
    }

    func deleteOutfit(id: String) {
        deleteRow("deleteOutfit", saveAction: "saveOutfit", id: id,
                  onSheet: onSheet?.planner?.outfits.contains { $0.id == id } ?? false, summary: "Clear outfit")
    }

    func saveClothing(_ item: Clothing) {
        enqueueRowSave("saveClothing", Self.dictionary(item), summary: "Wardrobe: \(item.name)")
    }

    func deleteClothing(id: String) {
        deleteRow("deleteClothing", saveAction: "saveClothing", id: id,
                  onSheet: onSheet?.planner?.wardrobe.contains { $0.id == id } ?? false, summary: "Remove from wardrobe")
    }

    // MARK: - Health from the phone (queued)

    /// Steps, sleep, weight and workouts from Apple Health. Steps are never lowered;
    /// the rest only fill empty days. A newer sync replaces one still waiting to go.
    func saveHealth(_ days: [String: HealthDay]) {
        var merged = days
        let inFlight = sending?.id
        let waiting = pending.filter { ($0.action == "saveHealth" || $0.action == "saveSteps") && $0.id != inFlight }
        for write in waiting {
            for h in (write.payload["days"] as? [[String: Any]]) ?? [] {
                guard let date = h["date"] as? String else { continue }
                var day = merged[date] ?? HealthDay()
                if let n = (h["steps"] as? NSNumber)?.intValue { day.steps = max(day.steps ?? 0, n) }
                if day.sleep == nil { day.sleep = (h["sleep"] as? NSNumber)?.doubleValue }
                if day.weight == nil { day.weight = (h["weight"] as? NSNumber)?.doubleValue }
                if (h["workout"] as? Bool) == true { day.workout = true }
                merged[date] = day
            }
        }
        pending.removeAll { w in waiting.contains { $0.id == w.id } }
        let list: [[String: Any]] = merged.sorted { $0.key < $1.key }.map { date, h in
            var row: [String: Any] = ["date": date]
            if let v = h.steps { row["steps"] = v }
            if let v = h.sleep { row["sleep"] = v }
            if let v = h.weight { row["weight"] = v }
            if h.workout { row["workout"] = true }
            return row
        }
        enqueue("saveHealth", ["days": list], summary: "From Apple Health · \(list.count) day\(list.count == 1 ? "" : "s")")
    }

    // MARK: - Your habits (queued)

    func saveHabits(_ habits: [(name: String, on: Bool, icon: String)], trackers: [Tracker: Bool]) {
        enqueue("saveHabits", [
            "habits": habits.map { ["name": $0.name, "on": $0.on, "icon": $0.icon] as [String: Any] },
            "trackers": Dictionary(uniqueKeysWithValues: trackers.map { ($0.key.key, $0.value) })
        ], summary: "Your habits")
    }

    /// Renames one of the five, changes its icon or turns it off; the others stay as they are.
    func saveHabit(_ habit: Habit, name: String, on: Bool, icon: String) {
        guard let s = snapshot?.settings else { return }
        saveHabits(Habit.allCases.map { h in
            h == habit ? (name: name, on: on, icon: icon) : (name: h.name(s), on: h.isOn(s), icon: h.iconKey(s))
        }, trackers: Dictionary(uniqueKeysWithValues: Tracker.allCases.map { ($0, $0.isOn(s)) }))
    }

    // MARK: - Your own inputs (queued)

    func inputExists(_ id: String) -> Bool { snapshot?.myInputs?.inputs.contains { $0.id == id } ?? false }

    func saveInput(_ input: MyInput) {
        enqueueRowSave("saveInput", Self.dictionary(input), summary: "\(input.section == .habit ? "Habit" : "Input"): \(input.name)")
    }

    /// Deletes it and everything logged for it.
    func deleteInput(id: String) {
        for i in pending.indices where pending[i].action == "saveInputValues" {
            if var values = pending[i].payload["values"] as? [String: Any] {
                values[id] = nil
                pending[i].payload["values"] = values
            }
        }
        pending.removeAll { $0.action == "saveInputValues" && (($0.payload["values"] as? [String: Any])?.isEmpty ?? true) }
        deleteRow("deleteInput", saveAction: "saveInput", id: id,
                  onSheet: onSheet?.myInputs?.inputs.contains { $0.id == id } ?? false, summary: "Delete input")
    }

    /// What you logged for your own inputs on one day ("" clears one); merged into an unsent save for that day.
    func saveInputValues(date: String, values: [String: String]) {
        guard !values.isEmpty else { return }
        let inFlight = sending?.id
        if let i = pending.firstIndex(where: { $0.action == "saveInputValues" && ($0.payload["date"] as? String) == date && $0.id != inFlight }) {
            var merged = pending[i].payload["values"] as? [String: Any] ?? [:]
            for (k, v) in values { merged[k] = v }
            pending[i].payload["values"] = merged
            persistQueue()
            recompute()
            return
        }
        enqueue("saveInputValues", ["date": date, "values": values], summary: "Day \(SheetDate.weekdayDay(date)) · your inputs")
    }

    // MARK: - Renewals and loans (queued)

    func saveRenewal(_ renewal: Renewal) {
        enqueueRowSave("saveRenewal", Self.dictionary(renewal), summary: "Renewal: \(renewal.item)")
    }

    func deleteRenewal(id: String) {
        deleteRow("deleteRenewal", saveAction: "saveRenewal", id: id,
                  onSheet: onSheet?.renewals?.contains { $0.id == id } ?? false, summary: "Delete renewal")
    }

    func saveLoan(_ loan: Loan) {
        enqueueRowSave("saveLoan", Self.dictionary(loan), summary: "Loan: \(loan.name)")
    }

    // MARK: - Parents (queued)

    /// Photos not on Drive yet travel with every save of the request (from their copy on
    /// this phone), so an edit sent before the sheet has them doesn't lose them.
    func saveParentAsk(_ ask: ParentAsk) {
        var payload = Self.dictionary(ask)
        var added: [String: String] = [:]
        for key in ask.photos.compactMap(ParentPhotos.localKey) {
            if let data = try? Data(contentsOf: ParentPhotos.localURL(key: key)) { added[key] = data.base64EncodedString() }
        }
        if !added.isEmpty { payload["newPhotos"] = added }
        enqueueRowSave("saveParentAsk", payload, summary: "\(ask.who.label): \(ask.task)")
    }

    func saveParentMedicine(_ medicine: ParentMedicine) {
        enqueueRowSave("saveParentMedicine", Self.dictionary(medicine), summary: "Medicine: \(medicine.name)")
    }

    func deleteParentMedicine(id: String) {
        deleteRow("deleteParentMedicine", saveAction: "saveParentMedicine", id: id,
                  onSheet: onSheet?.parentMedicines?.contains { $0.id == id } ?? false, summary: "Delete medicine")
    }

    func saveParentDate(_ date: ParentDate) {
        enqueueRowSave("saveParentDate", Self.dictionary(date), summary: date.title)
    }

    func deleteParentDate(id: String) {
        deleteRow("deleteParentDate", saveAction: "saveParentDate", id: id,
                  onSheet: onSheet?.parentDates?.contains { $0.id == id } ?? false, summary: "Delete date")
    }

    func saveTakeHomeItem(_ item: TakeHomeItem) {
        enqueueRowSave("saveTakeHomeItem", Self.dictionary(item), summary: "Take home: \(item.item)")
    }

    func deleteTakeHomeItem(id: String) {
        deleteRow("deleteTakeHomeItem", saveAction: "saveTakeHomeItem", id: id,
                  onSheet: onSheet?.takeHome?.items.contains { $0.id == id } ?? false, summary: "Delete item")
    }

    /// The next trip home ("yyyy-MM-dd"), or nil for none planned.
    func saveTakeHomeTrip(_ date: String?) {
        let inFlight = sending?.id
        pending.removeAll { $0.action == "saveTakeHomeTrip" && $0.id != inFlight }
        enqueue("saveTakeHomeTrip", ["date": date ?? ""], summary: date.map { "Trip home \(SheetDate.short($0))" } ?? "No trip planned")
    }

    func saveParentReading(_ reading: ParentReading) {
        enqueueRowSave("saveParentReading", Self.dictionary(reading), summary: "\(reading.who.rawValue)'s \(reading.kind.short)")
    }

    func deleteParentReading(id: String) {
        deleteRow("deleteParentReading", saveAction: "saveParentReading", id: id,
                  onSheet: onSheet?.parentHealth?.contains { $0.id == id } ?? false, summary: "Delete reading")
    }

    func deleteParentAsk(id: String) {
        deleteRow("deleteParentAsk", saveAction: "saveParentAsk", id: id,
                  onSheet: onSheet?.parents?.contains { $0.id == id } ?? false, summary: "Delete request")
    }

    func deleteLoan(id: String) {
        deleteRow("deleteLoan", saveAction: "saveLoan", id: id,
                  onSheet: onSheet?.loans?.contains { $0.id == id } ?? false, summary: "Delete loan")
    }

    // MARK: - Life goals (queued)

    func saveLifeGoal(_ goal: LifeGoal) {
        enqueueRowSave("saveLifeGoal", Self.dictionary(goal), summary: "Goal: \(goal.title)")
    }

    /// Its steps go too; short-term goals under it stay, unlinked.
    func deleteLifeGoal(id: String) {
        deleteRow("deleteLifeGoal", saveAction: "saveLifeGoal", id: id,
                  onSheet: onSheet?.lifeGoals?.goals.contains { $0.id == id } ?? false, summary: "Delete goal")
    }

    func saveGoalStep(_ step: GoalStep) {
        enqueueRowSave("saveGoalStep", Self.dictionary(step), summary: "Step: \(step.step)")
    }

    func deleteGoalStep(id: String) {
        deleteRow("deleteGoalStep", saveAction: "saveGoalStep", id: id,
                  onSheet: onSheet?.lifeGoals?.steps.contains { $0.id == id } ?? false, summary: "Delete step")
    }

    // MARK: - Shopping (queued)

    func saveShoppingItem(_ item: ShoppingItem) {
        enqueueRowSave("saveShoppingItem", Self.dictionary(item), summary: "Shopping: \(item.item)")
    }

    func deleteShoppingItem(id: String) {
        deleteRow("deleteShoppingItem", saveAction: "saveShoppingItem", id: id,
                    onSheet: onSheet?.shopping?.items.contains { $0.id == id } ?? false, summary: "Remove from list")
    }

    /// Finishes a trip: the cart's items become bought, and (optionally) one Groceries expense is added.
    func checkoutShopping(tripId: String, date: String, store: String, total: Double?, method: String, addToFinance: Bool,
                          notes: String, items: [(id: String, price: Double?)]) {
        var payload: [String: Any] = [
            "tripId": tripId, "date": date, "store": store, "method": method, "addToFinance": addToFinance, "notes": notes,
            "items": items.map { line -> [String: Any] in
                var row: [String: Any] = ["id": line.id]
                if let price = line.price { row["price"] = price }
                return row
            }
        ]
        if let total { payload["total"] = (total * 100).rounded() / 100 }
        enqueue("checkoutShopping", payload, summary: "Shopping\(store.isEmpty ? "" : " at \(store)") · \(items.count) items")
    }

    func deleteShoppingTrip(id: String) {
        if let index = pending.firstIndex(where: { $0.action == "checkoutShopping" && ($0.payload["tripId"] as? String) == id }),
           !(onSheet?.shopping?.trips.contains { $0.id == id } ?? false), !isSending("checkoutShopping", "tripId", id) {
            pending.remove(at: index)
            persistQueue()
            recompute()
        } else {
            enqueue("deleteShoppingTrip", ["tripId": id], summary: "Delete shopping trip")
        }
    }

    // MARK: - Salary split (queued)

    func saveSetAside(_ entry: SetAsideEntry) {
        enqueueRowSave("saveSetAside", Self.dictionary(entry), summary: "\(entry.bucket) · \(SheetFormat.qar(entry.amount))")
    }

    func saveSavingsGoal(_ goal: SavingsGoal) {
        enqueueRowSave("saveSavingsGoal", Self.dictionary(goal), summary: "Goal \(goal.name)")
    }

    /// Removes the goal; money put aside for it stays, no longer linked.
    func deleteSavingsGoal(id: String) {
        deleteRow("deleteSavingsGoal", saveAction: "saveSavingsGoal", id: id,
                  onSheet: onSheet?.savingsGoals?.contains { $0.id == id } ?? false, summary: "Delete goal")
    }

    func deleteSetAside(id: String) {
        deleteRow("deleteSetAside", saveAction: "saveSetAside", id: id,
                  onSheet: onSheet?.setAside?.contains { $0.id == id } ?? false, summary: "Delete money put aside")
    }

    func copyPlanWeek(from: String, to: String) async -> Bool {
        await sendNow("copyPlanWeek", ["from": from, "to": to, "replace": true])
    }

    func saveMyFood(_ food: MyFood) async -> Bool {
        var payload: [String: Any] = ["name": food.name, "amount": food.amount ?? 1, "unit": food.unit ?? "serving",
                                      "calories": food.calories ?? 0, "protein": food.protein ?? 0, "carbs": food.carbs ?? 0,
                                      "fat": food.fat ?? 0, "barcode": food.barcode ?? ""]
        if let cost = food.cost { payload["cost"] = cost }
        if let row = food.row { payload["row"] = row }
        return await sendNow("saveMyFood", payload)
    }

    func deleteMyFood(row: Int) async -> Bool {
        await sendNow("deleteMyFood", ["row": row])
    }

    /// A meal photo from Drive, as JPEG/PNG data (the caller caches it).
    func photo(id: String, thumb: Bool) async -> Data? {
        if usesGoogleSheet {
            guard isConfigured else { return nil }
            return try? await GoogleSheetsAPI().download(id, thumbnail: thumb).data
        }
        guard isConfigured, let url = URL(string: webAppURL),
              let request = try? Self.webAppRequest(url: url, key: apiKey, action: "photo",
                                                    fields: ["id": id, "size": thumb ? "thumb" : "full"], timeout: 60) else { return nil }
        struct Photo: Decodable { let base64: String }
        struct Envelope: Decodable { let ok: Bool; let data: Photo? }
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let envelope = try? JSONDecoder().decode(Envelope.self, from: data),
              let base64 = envelope.data?.base64 else { return nil }
        return Data(base64Encoded: base64)
    }

    // MARK: - Your data

    /// One action whose answer isn't a snapshot (export, reset).
    /// One year added up across every period (28_YearReview.gs).
    func yearReview(year: Int) async throws -> [String: Any] {
        guard !isDemo else { throw SheetError.message("Not available with sample data.") }
        return try await callRaw("yearReview", payload: ["year": year])
    }

    private func callRaw(_ action: String, payload: [String: Any] = [:], timeout: TimeInterval = 120) async throws -> [String: Any] {
        if usesGoogleSheet {
            guard isConfigured else { throw SheetError.message("Sign in with Google first.") }
            if action == "resetAll" { return try await resetGoogleSheet() }
            return try await viaScript(action, payload)["data"] as? [String: Any] ?? [:]
        }
        guard isConfigured, let url = URL(string: webAppURL) else { throw SheetError.message("Connect the Google Sheet in Settings first.") }
        let request = try postRequest(url: url, action: action, payload: payload, timeout: timeout)
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SheetError.message("The sheet didn't answer. Check your connection and try again.")
        }
        guard json["ok"] as? Bool == true, let result = json["data"] as? [String: Any] else {
            let error = json["error"] as? String ?? "The sheet refused."
            if error.hasPrefix("SETUP_IN_PROGRESS") { throw SheetError.busy(error.replacingOccurrences(of: "SETUP_IN_PROGRESS: ", with: "")) }
            throw SheetError.message(error)
        }
        return result
    }

    /// Every table in the sheet, as rows of text (header first). Falls back to
    /// what's on the phone when the sheet can't be reached.
    func exportTables() async -> (tables: [(name: String, rows: [[String]])], complete: Bool) {
        if !isDemo, let result = try? await callRaw("export"), let files = result["files"] as? [[String: Any]] {
            let tables = files.compactMap { file -> (String, [[String]])? in
                guard let name = file["name"] as? String, let rows = file["rows"] as? [[Any]] else { return nil }
                return (name, rows.map { $0.map { "\($0)" } })
            }
            return (tables.map { (name: $0.0, rows: $0.1) }, true)
        }
        return (DataExport.tables(from: snapshot), false)
    }

    /// Wipes everything in the sheet and rebuilds it fresh from today (in the
    /// background), and forgets everything kept on this phone.
    func resetAllData() async -> Bool {
        if isDemo { errorMessage = "Not available with sample data."; return false }
        isSaving = true
        defer { isSaving = false }
        do {
            _ = try await callRaw("resetAll", timeout: 60)
            pending = []
            persistQueue()
            confirmed = nil
            snapshot = nil
            try? FileManager.default.removeItem(at: cacheURL)
            try? FileManager.default.removeItem(at: URL.cachesDirectory.appending(path: "meal-photos"))
            WidgetSync.updateSheet(nil)
            // A Google sign-in sheet is made fresh and ready at once; the web app rebuilds in the background.
            if usesGoogleSheet { await refresh() } else { noteSetup("starting") }
            return true
        } catch {
            errorMessage = error is URLError ? "You're offline. Resetting needs a connection." : error.localizedDescription
            return false
        }
    }

    /// Drops a queued write the sheet will never accept (from the sync banner).
    func discard(_ write: PendingWrite) {
        pending.removeAll { $0.id == write.id }
        persistQueue()
        recompute()
    }

    // MARK: - Direct writes (need a connection)

    /// Keys as in the sheet's `CFG` (e.g. "DAILY_LIMIT"); percentages as fractions.
    func saveSettings(_ values: [String: Double]) async -> Bool {
        await sendNow("saveSettings", values)
    }

    /// Body & goal; the sheet works out the calorie and protein targets from it.
    func saveBody(_ values: [String: Any]) async -> Bool {
        await sendNow("saveBody", values)
    }

    /// Sets the workbook's currencies (and optionally 1 main = `rate` home).
    func saveCurrency(main: String, home: String, rate: Double?) async -> Bool {
        var payload: [String: Any] = ["main": main, "home": home]
        if let rate { payload["rate"] = rate }
        return await sendNow("saveCurrency", payload)
    }

    /// Archives the current period in the sheet and starts a new one. Slow: the sheet rebuilds.
    func startNextPeriod(start: String, end: String) async -> Bool {
        await sendNow("startNextPeriod", ["start": start, "end": end, "archive": true], timeout: 300)
    }

    func saveRecurring(_ rule: RecurringExpense, addThisMonth: Bool) async -> Bool {
        var payload: [String: Any] = [
            "description": rule.description ?? "", "category": rule.category ?? "", "method": rule.method ?? "",
            "amount": rule.amount ?? 0, "day": rule.day ?? 1, "active": rule.active, "notes": rule.notes ?? "",
            "addThisMonth": addThisMonth
        ]
        if let row = rule.row { payload["row"] = row }
        if rule.row == nil, let lastAdded = rule.lastAdded { payload["lastAdded"] = lastAdded }
        return await sendNow("saveRecurring", payload)
    }

    func deleteRecurring(row: Int) async -> Bool {
        await sendNow("deleteRecurring", ["row": row])
    }

    /// A number, or an empty cell when there is none.
    private func cell(_ value: Double?) -> Any { value ?? "" }

    private func sendNow(_ action: String, _ payload: [String: Any], timeout: TimeInterval = 60) async -> Bool {
        if isDemo { errorMessage = "Not available with sample data."; return false }
        guard isConfigured, usesGoogleSheet || URL(string: webAppURL) != nil else {
            errorMessage = "Connect the Google Sheet in Settings first."
            return false
        }
        // Queued writes go first so the sheet sees everything in order.
        await flush()
        isSaving = true
        defer { isSaving = false }
        do {
            let result: SheetSnapshot
            if let backend {
                try await viaScript(action, payload, reply: "ack")
                result = try await viaGoogle { try await backend.snapshot() }
            } else {
                result = try await send(postRequest(url: URL(string: webAppURL)!, action: action, payload: payload, timeout: timeout))
            }
            writesLanded += 1
            forgetLanded()
            apply(result)
            return true
        } catch {
            handle(error)
            if error is URLError { errorMessage = "You're offline. This change needs a connection — try again when you're back online." }
            if case SheetError.transient = error { errorMessage = "Your sheet didn't answer just now. Try again in a minute." }
            return false
        }
    }

    // MARK: - Queue

    private var nextTempRow = -1
    /// Saves the sheet has confirmed, and refreshes begun — so a snapshot read before a save landed is never shown over it.
    @ObservationIgnored private var writesLanded = 0
    @ObservationIgnored private var refreshesStarted = 0

    private func enqueue(_ action: String, _ payload: [String: Any], summary: String, tempRow: Int? = nil, delay: TimeInterval = 0) {
        let write = PendingWrite(id: UUID().uuidString, action: action, payload: payload, summary: summary,
                                 createdAt: .now, notBefore: delay > 0 ? .now.addingTimeInterval(delay) : nil, tempRow: tempRow)
        pending.append(write)
        persistQueue()
        recompute()
        Task { await flush() }
    }

    /// Sends queued writes in order until done, offline, or one is held for Undo.
    func flush() async {
        guard !isSyncing, !isDemo, isConfigured else { return }
        let backend = backend
        guard backend != nil || URL(string: webAppURL) != nil else { return }
        isSyncing = true
        defer { isSyncing = false }
        // Sent writes are removed by id, not position: an edit made while one is
        // on its way can replace it in the queue, and must not cost another write.
        var sentQuick = false
        while let write = pending.first {
            if let notBefore = write.notBefore, notBefore > .now {
                scheduleRetry(after: notBefore.timeIntervalSinceNow + 0.2)
                return
            }
            if write.id == lastDeleted?.id { lastDeleted = nil; undoMessage = nil }
            sending = write
            defer { sending = nil }
            do {
                var payload = write.payload
                payload["clientId"] = write.id
                // Most saves only need "got it" (a second or two); the snapshot follows once the queue is empty.
                // An added expense waits for the full reply, which carries the row the sheet gave it.
                let quick = write.tempRow == nil
                if quick { payload["reply"] = "ack" }
                let result: SheetSnapshot?
                if let backend {
                    try await viaScript(write.action, payload, reply: "ack")
                    result = quick ? nil : try await viaGoogle { try await backend.snapshot() }
                } else {
                    result = try await send(postRequest(url: URL(string: webAppURL)!, action: write.action, payload: payload, timeout: 60), allowAck: quick)
                }
                pending.removeAll { $0.id == write.id }
                var lostEdits = false
                if let tempRow = write.tempRow, let result {
                    // Edits and deletes made while it was on its way go to the row the sheet gave it.
                    let row = Self.rowAssigned(to: write, before: onSheet?.expenses ?? [], after: result.expenses)
                    let before = pending.count
                    pending = Self.pointing(pending, from: tempRow, to: row)
                    lostEdits = pending.count < before
                }
                writesLanded += 1
                transientRetries = 0
                persistQueue()
                if let result {
                    forgetLanded()
                    apply(result)
                    if lostEdits { errorMessage = "Couldn't change “\(write.summary)” on the sheet. Edit it again there." }
                } else {
                    landed.append(write)
                    persistLanded()
                    recompute()
                    sentQuick = true
                }
                isOffline = false
            } catch is URLError {
                isOffline = true
                scheduleRetry(after: 30)
                return
            } catch SheetError.unauthorized {
                handle(SheetError.unauthorized)
                return
            } catch SheetError.signedOut {
                // Kept: everything goes once you sign in again.
                errorMessage = SheetError.signedOut.localizedDescription
                return
            } catch let error as SheetError {
                if case .busy(let progress) = error {
                    // Being rebuilt: keep the change and try again shortly.
                    noteSetup(progress)
                    scheduleRetry(after: 60)
                    return
                }
                if case .transient = error {
                    // Google busy: the change stays queued (a resend is never written twice) and goes again soon.
                    transientRetries += 1
                    scheduleRetry(after: min(60, 3 * pow(2, Double(transientRetries - 1))))
                    return
                }
                pending.removeAll { $0.id == write.id }
                persistQueue()
                recompute()
                errorMessage = "Couldn't save “\(write.summary)”: \(error.localizedDescription)"
            } catch {
                // The sheet refused this one (bad date, full ledger…): drop it and say so.
                pending.removeAll { $0.id == write.id }
                persistQueue()
                recompute()
                errorMessage = "Couldn't save “\(write.summary)”: \(error.localizedDescription)"
            }
        }
        // All sent: download once so the sheet's formulas (score, budget, summaries) catch up.
        // Only after this run sent something — refresh() flushes first, and must not start itself again.
        if sentQuick, pending.isEmpty { Task { await self.refresh() } }
    }

    /// Waits until the queue is empty (or the time runs out) — for Siri, which
    /// may suspend the app as soon as the intent returns.
    func waitForSync(seconds: Double) async {
        let deadline = Date.now.addingTimeInterval(seconds)
        while !pending.isEmpty, !isOffline, Date.now < deadline {
            await flush()
            if pending.isEmpty || isOffline { break }
            try? await Task.sleep(for: .milliseconds(300))
        }
    }

    private func scheduleRetry(after seconds: TimeInterval) {
        retryTask?.cancel()
        retryTask = Task {
            try? await Task.sleep(for: .seconds(max(0.5, seconds)))
            guard !Task.isCancelled else { return }
            await self.flush()
        }
    }

    private func persistQueue() {
        try? FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(pending) {
            try? data.write(to: Self.queueURL, options: .atomic)
        }
    }

    private static var queueURL: URL { URL.applicationSupportDirectory.appending(path: "sheet-queue.json") }
    private static var landedURL: URL { URL.applicationSupportDirectory.appending(path: "sheet-landed.json") }

    private func persistLanded() {
        try? FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(landed) { try? data.write(to: Self.landedURL, options: .atomic) }
    }

    private static func loadLanded() -> [PendingWrite] {
        guard let data = try? Data(contentsOf: landedURL) else { return [] }
        return (try? JSONDecoder().decode([PendingWrite].self, from: data)) ?? []
    }

    /// What the sheet has: the last download plus saves it has confirmed since (for "is this row on the sheet yet?").
    private var onSheet: SheetSnapshot? {
        guard var view = confirmed else { return nil }
        for write in landed { Self.applyLocally(write, to: &view) }
        return view
    }

    /// A snapshot downloaded after these saves landed has them: stop drawing them on top.
    private func forgetLanded() {
        guard !landed.isEmpty else { return }
        landed = []
        persistLanded()
    }

    /// The ledger row a queued add got: new in the sheet's answer, with the add's date, amount and description
    /// (a recurring bill may have been posted in the same answer).
    static func rowAssigned(to add: PendingWrite, before: [SheetExpense], after: [SheetExpense]) -> Int? {
        let taken = Set(before.map(\.row)), p = add.payload
        let qar = (p["qar"] as? NSNumber)?.doubleValue ?? 0
        return after.first { e in
            !taken.contains(e.row) && e.date == p["date"] as? String && abs((e.qar ?? 0) - qar) < 0.005
                && (e.description ?? "") == (p["description"] as? String ?? "")
        }?.row
    }

    /// Queued edits and deletes of an add that was on its way, moved to the row it got; dropped when there's none.
    static func pointing(_ writes: [PendingWrite], from tempRow: Int, to row: Int?) -> [PendingWrite] {
        writes.compactMap { write in
            guard ["updateExpense", "deleteExpense"].contains(write.action),
                  (write.payload["row"] as? NSNumber)?.intValue == tempRow else { return write }
            guard let row else { return nil }
            var moved = write
            moved.payload["row"] = row
            return moved
        }
    }

    private static func loadQueue() -> [PendingWrite] {
        guard let data = try? Data(contentsOf: queueURL) else { return [] }
        return (try? JSONDecoder().decode([PendingWrite].self, from: data)) ?? []
    }

    // MARK: - Optimistic view

    /// The confirmed snapshot with every queued write applied, so the screens
    /// show a change the moment it's made. Formula columns are estimated.
    private func recompute() {
        guard var view = confirmed else {
            snapshot = nil
            WidgetSync.updateSheet(nil)
            return
        }
        for write in landed + pending { Self.applyLocally(write, to: &view) }
        snapshot = view
        WidgetSync.updateSheet(view)
        if !isDemo { BudgetAlerts.check(view) }
        // Deferred: this also runs while the store itself is being created.
        Task { @MainActor in CurrencyBook.shared.followSheet() }
    }

    /// The sheet's formula: five Y habits + water + sleep targets, out of 7. A day is
    /// "logged" once office, breakfast, lunch, protein, water or screen time is in —
    /// what the phone fills in (workout, sleep, steps, weight) doesn't count on its own.
    static func rescore(_ day: inout SheetDay, settings: SheetSettings, inputs: MyInputData? = nil) {
        let logged = [Habit.office, .breakfast, .lunch, .protein].contains { day.answer($0) != nil } || day.water != nil || day.screen != nil
            || (!Habit.healthFillsWorkout(settings) && day.answer(.workout) != nil)
            || (inputs?.log.contains { $0.date == day.date } ?? false)
        guard logged, settings.scoreDenominator > 0 else { day.habitsMet = nil; day.score = nil; day.status = nil; return }
        let yes = Habit.active(settings).filter { day.answer($0) == "Y" }.count + (inputs?.habitsMet(on: day.date) ?? 0)
        let met = yes + (Tracker.water.isOn(settings) && (day.water ?? 0) >= (settings.waterTarget ?? .infinity) ? 1 : 0)
            + (Tracker.sleep.isOn(settings) && (day.sleep ?? 0) >= (settings.sleepTarget ?? .infinity) ? 1 : 0)
        let score = Double(met) / settings.scoreDenominator
        day.habitsMet = Double(met)
        day.score = score
        day.status = score >= (settings.greatDay ?? 0.85) ? "Excellent" : (score >= 0.7 ? "On Track" : "Needs Work")
    }

    /// After a habit is renamed, added or turned on or off: the score's out-of and every day so far.
    private static func rescoreAll(_ s: inout SheetSnapshot) {
        s.settings.scoreOutOf = s.settings.scoreOutOf(with: s.myInputs)
        for i in s.days.indices where s.days[i].date <= SheetDate.today { rescore(&s.days[i], settings: s.settings, inputs: s.myInputs) }
    }

    static func applyLocally(_ write: PendingWrite, to s: inout SheetSnapshot) {
        let p = write.payload
        func d(_ key: String) -> Double? {
            if let n = p[key] as? NSNumber { return n.doubleValue }
            return (p[key] as? String).flatMap(Double.init)
        }
        func t(_ key: String) -> String? { (p[key] as? String).flatMap { $0.isEmpty ? nil : $0 } }
        switch write.action {
        case "saveHealth", "saveSteps":
            for h in (p["days"] as? [[String: Any]]) ?? [] {
                guard let date = h["date"] as? String, let i = s.days.firstIndex(where: { $0.date == date }) else { continue }
                var day = s.days[i]
                if let steps = (h["steps"] as? NSNumber)?.doubleValue, steps > (day.steps ?? 0) { day.steps = steps }
                if let sleep = (h["sleep"] as? NSNumber)?.doubleValue, sleep > 0, day.sleep == nil { day.sleep = sleep }
                if let weight = (h["weight"] as? NSNumber)?.doubleValue, weight > 0, day.weight == nil { day.weight = weight }
                if (h["workout"] as? Bool) == true, day.workout == nil { day.workout = "Y" }
                if day.score != nil { rescore(&day, settings: s.settings, inputs: s.myInputs) }
                s.days[i] = day
            }
        case "saveDay":
            guard let date = t("date"), let i = s.days.firstIndex(where: { $0.date == date }) else { return }
            var day = s.days[i]
            day.office = t("office"); day.breakfast = t("breakfast"); day.lunch = t("lunch")
            day.protein = t("protein"); day.workout = t("workout")
            day.water = d("water"); day.sleep = d("sleep"); day.steps = d("steps")
            day.weight = d("weight"); day.screen = d("screen"); day.notes = t("notes")
            Self.rescore(&day, settings: s.settings, inputs: s.myInputs)
            s.days[i] = day
            // Targets that follow your weight move with a newly logged one (the sheet does the same).
            if s.settings.followWeight == true, let weight = s.latestWeight?.kg, let height = s.settings.heightCm, let born = s.settings.birthYear {
                let set = s.settings
                let goal = set.bodyGoal.flatMap(BodyCalc.Goal.init(rawValue:)) ?? .maintain
                let r = BodyCalc.targets(weight: weight, height: height, age: Calendar.current.component(.year, from: .now) - Int(born),
                                         sex: set.sex.flatMap(BodyCalc.Sex.init(rawValue:)) ?? .male,
                                         activity: set.activity.flatMap(BodyCalc.Activity.init(rawValue:)) ?? .light,
                                         goal: goal, proteinPerKg: set.proteinPerKg ?? goal.proteinPerKg)
                s.settings.calorieTarget = r.calories
                s.settings.proteinTarget = r.protein
                s.settings.carbTarget = r.carbs
                s.settings.fatTarget = r.fat
            }
        case "addExpense", "updateExpense":
            let row = write.tempRow ?? (p["row"] as? NSNumber)?.intValue ?? 0
            let qar = d("qar")
            let expense = SheetExpense(row: row, date: t("date"), description: t("description"), category: t("category"),
                                       method: t("method"), qar: qar, inr: qar.map { $0 * (s.settings.rate ?? 0) }, notes: t("notes"))
            if let i = s.expenses.firstIndex(where: { $0.row == row }) { s.expenses[i] = expense } else { s.expenses.append(expense) }
        case "deleteExpense":
            let row = (p["row"] as? NSNumber)?.intValue ?? 0
            s.expenses.removeAll { $0.row == row }
        case "saveMeal":
            guard let id = t("mealId"), let date = t("date"), let meal = t("meal") else { return }
            var food = s.foodData
            let old = food.log.first { $0.mealId == id }
            food.log.removeAll { $0.mealId == id }
            let items = (p["items"] as? [[String: Any]]) ?? []
            let photo = (p["photo"] != nil) ? "local" : ((p["removePhoto"] as? Bool) == true ? nil : old?.photo)
            for (n, item) in items.enumerated() {
                func v(_ k: String) -> Double? { (item[k] as? NSNumber)?.doubleValue }
                food.log.append(FoodEntry(row: -1 - n, mealId: id, date: date, time: t("time"), meal: meal,
                                          food: item["food"] as? String ?? "", amount: v("amount"), unit: item["unit"] as? String,
                                          calories: v("calories"), protein: v("protein"), carbs: v("carbs"), fat: v("fat"),
                                          cost: n == 0 ? d("cost") : nil, photo: n == 0 ? photo : nil,
                                          ledgerRow: n == 0 ? old?.ledgerRow : nil, notes: n == 0 ? t("notes") : nil))
            }
            s.food = food
        case "deleteMeal":
            guard let id = t("mealId") else { return }
            var food = s.foodData
            food.log.removeAll { $0.mealId == id }
            s.food = food
        case "savePlan":
            guard let date = t("date"), let meal = t("meal") else { return }
            var food = s.foodData
            food.plan.removeAll { $0.date == date && $0.meal == meal }
            let eaten = (p["eaten"] as? Bool) ?? false
            for (n, item) in ((p["items"] as? [[String: Any]]) ?? []).enumerated() {
                func v(_ k: String) -> Double { (item[k] as? NSNumber)?.doubleValue ?? 0 }
                let food1 = FoodItem(food: item["food"] as? String ?? "", amount: v("amount"), unit: item["unit"] as? String ?? "",
                                     calories: v("calories"), protein: v("protein"), carbs: v("carbs"), fat: v("fat"),
                                     cost: (item["cost"] as? NSNumber)?.doubleValue)
                food.plan.append(PlanItem(row: -1 - n, date: date, meal: meal, item: food1, eaten: eaten))
            }
            s.food = food
        case "savePeopleEntry":
            guard let id = t("id"), let type = t("type").flatMap(PeopleEntry.Kind.init(rawValue:)) else { return }
            var people = s.people ?? []
            people.removeAll { $0.id == id }
            people.append(PeopleEntry(id: id, date: t("date") ?? SheetDate.today, person: t("person") ?? "", type: type,
                                      amount: d("amount") ?? 0, note: t("note"), pending: true))
            s.people = people
        case "deletePeopleEntry":
            guard let id = t("id") else { return }
            s.people?.removeAll { $0.id == id }
        case "saveStudySubject":
            guard let row = decode(StudySubject.self, p) else { return }
            var study = s.studyData
            if let i = study.subjects.firstIndex(where: { $0.id == row.id }) { study.subjects[i] = row } else { study.subjects.append(row) }
            for i in study.sessions.indices where study.sessions[i].subjectId == row.id { study.sessions[i].subject = row.name }
            s.study = study
        case "deleteStudySubject":
            guard let id = t("id") else { return }
            var study = s.studyData
            study.subjects.removeAll { $0.id == id }
            study.topics.removeAll { $0.subjectId == id }
            study.goals.removeAll { $0.subjectId == id }
            s.study = study
        case "saveStudyTopic":
            guard let row = decode(StudyTopic.self, p) else { return }
            var study = s.studyData
            if let i = study.topics.firstIndex(where: { $0.id == row.id }) { study.topics[i] = row } else { study.topics.append(row) }
            for i in study.sessions.indices where study.sessions[i].topicId == row.id { study.sessions[i].topic = row.topic }
            s.study = study
        case "deleteStudyTopic":
            guard let id = t("id") else { return }
            s.study?.topics.removeAll { $0.id == id }
        case "saveStudySession":
            guard var row = decode(StudyEntry.self, p) else { return }
            var study = s.studyData
            row.pending = true
            row.subject = study.subject(row.subjectId)?.name ?? row.subject
            row.topic = study.topic(row.topicId)?.topic ?? row.topic
            if let i = study.sessions.firstIndex(where: { $0.id == row.id }) { study.sessions[i] = row } else { study.sessions.append(row) }
            s.study = study
        case "deleteStudySession":
            guard let id = t("id") else { return }
            s.study?.sessions.removeAll { $0.id == id }
        case "saveStudyGoal":
            guard let row = decode(StudyGoal.self, p) else { return }
            var study = s.studyData
            if let i = study.goals.firstIndex(where: { $0.id == row.id }) { study.goals[i] = row } else { study.goals.append(row) }
            s.study = study
        case "deleteStudyGoal":
            guard let id = t("id") else { return }
            s.study?.goals.removeAll { $0.id == id }
        case "saveHabits":
            let habits = (p["habits"] as? [[String: Any]]) ?? []
            guard habits.count == 5 else { return }
            func name(_ i: Int) -> String? { (habits[i]["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } }
            func on(_ i: Int) -> String { (habits[i]["on"] as? Bool) == false ? "N" : "Y" }
            func icon(_ i: Int) -> String? { habits[i]["icon"] as? String }
            s.settings.habit1Name = name(0); s.settings.habit2Name = name(1); s.settings.habit3Name = name(2)
            s.settings.habit4Name = name(3); s.settings.habit5Name = name(4)
            s.settings.habit1On = on(0); s.settings.habit2On = on(1); s.settings.habit3On = on(2); s.settings.habit4On = on(3); s.settings.habit5On = on(4)
            s.settings.habit1Icon = icon(0); s.settings.habit2Icon = icon(1); s.settings.habit3Icon = icon(2)
            s.settings.habit4Icon = icon(3); s.settings.habit5Icon = icon(4)
            let trackers = (p["trackers"] as? [String: Any]) ?? [:]
            func t(_ key: String, _ old: String?) -> String? { (trackers[key] as? Bool).map { $0 ? "Y" : "N" } ?? old }
            s.settings.waterOn = t("WATER_ON", s.settings.waterOn); s.settings.sleepOn = t("SLEEP_ON", s.settings.sleepOn)
            s.settings.stepsOn = t("STEPS_ON", s.settings.stepsOn); s.settings.weightOn = t("WEIGHT_ON", s.settings.weightOn)
            s.settings.screenOn = t("SCREEN_ON", s.settings.screenOn)
            rescoreAll(&s)
        case "saveInput":
            guard let row = decode(MyInput.self, p) else { return }
            var data = s.inputData
            if let i = data.inputs.firstIndex(where: { $0.id == row.id }) { data.inputs[i] = row } else { data.inputs.append(row) }
            s.myInputs = data
            rescoreAll(&s)
        case "deleteInput":
            guard let id = t("id") else { return }
            var data = s.inputData
            data.inputs.removeAll { $0.id == id }
            data.log.removeAll { $0.id == id }
            s.myInputs = data
            rescoreAll(&s)
        case "saveInputValues":
            guard let date = t("date") else { return }
            var data = s.inputData
            for (id, value) in (p["values"] as? [String: Any]) ?? [:] {
                data.log.removeAll { $0.date == date && $0.id == id }
                let text = (value as? String) ?? (value as? NSNumber)?.stringValue ?? ""
                if !text.isEmpty { data.log.append(MyInputLog(date: date, id: id, value: text)) }
            }
            s.myInputs = data
            if let i = s.days.firstIndex(where: { $0.date == date }) { rescore(&s.days[i], settings: s.settings, inputs: data) }
        case "saveRenewal":
            guard var row = decode(Renewal.self, p) else { return }
            row.pending = true
            var list = s.renewals ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.renewals = list
        case "deleteRenewal":
            guard let id = t("id") else { return }
            s.renewals?.removeAll { $0.id == id }
        case "saveLoan":
            guard var row = decode(Loan.self, p) else { return }
            row.pending = true
            var list = s.loans ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.loans = list
        case "deleteLoan":
            guard let id = t("id") else { return }
            s.loans?.removeAll { $0.id == id }
        case "saveParentAsk":
            guard var row = decode(ParentAsk.self, p) else { return }
            row.pending = true
            var list = s.parents ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.parents = list
        case "deleteParentAsk":
            guard let id = t("id") else { return }
            s.parents?.removeAll { $0.id == id }
        case "saveParentMedicine":
            guard var row = decode(ParentMedicine.self, p) else { return }
            row.pending = true
            var list = s.parentMedicines ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.parentMedicines = list
        case "deleteParentMedicine":
            guard let id = t("id") else { return }
            s.parentMedicines?.removeAll { $0.id == id }
        case "saveParentDate":
            guard var row = decode(ParentDate.self, p) else { return }
            row.pending = true
            var list = s.parentDates ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.parentDates = list
        case "deleteParentDate":
            guard let id = t("id") else { return }
            s.parentDates?.removeAll { $0.id == id }
        case "saveTakeHomeItem":
            guard var row = decode(TakeHomeItem.self, p) else { return }
            row.pending = true
            var data = s.takeHomeData
            if let i = data.items.firstIndex(where: { $0.id == row.id }) { data.items[i] = row } else { data.items.append(row) }
            s.takeHome = data
        case "deleteTakeHomeItem":
            guard let id = t("id") else { return }
            s.takeHome?.items.removeAll { $0.id == id }
        case "saveTakeHomeTrip":
            var data = s.takeHomeData
            data.trip = t("date")
            s.takeHome = data
        case "saveParentReading":
            guard var row = decode(ParentReading.self, p) else { return }
            row.pending = true
            var list = s.parentHealth ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.parentHealth = list
        case "deleteParentReading":
            guard let id = t("id") else { return }
            s.parentHealth?.removeAll { $0.id == id }
        case "saveLifeGoal":
            guard var row = decode(LifeGoal.self, p) else { return }
            row.pending = true
            row.updated = SheetDate.today
            if row.status == .reached, row.doneOn == nil { row.doneOn = SheetDate.today }
            if row.status != .reached { row.doneOn = nil }
            if row.horizon == .long { row.parentId = nil }
            var data = s.lifeGoalData
            if let i = data.goals.firstIndex(where: { $0.id == row.id }) { data.goals[i] = row } else { data.goals.append(row) }
            s.lifeGoals = data
        case "deleteLifeGoal":
            guard let id = t("id") else { return }
            var data = s.lifeGoalData
            data.goals.removeAll { $0.id == id }
            data.steps.removeAll { $0.goalId == id }
            for i in data.goals.indices where data.goals[i].parentId == id { data.goals[i].parentId = nil }
            s.lifeGoals = data
        case "saveGoalStep":
            guard var row = decode(GoalStep.self, p) else { return }
            row.pending = true
            if row.done, row.doneOn == nil { row.doneOn = SheetDate.today }
            if !row.done { row.doneOn = nil }
            var data = s.lifeGoalData
            if let i = data.steps.firstIndex(where: { $0.id == row.id }) { data.steps[i] = row } else { data.steps.append(row) }
            if let g = data.goals.firstIndex(where: { $0.id == row.goalId }) { data.goals[g].updated = SheetDate.today }
            s.lifeGoals = data
        case "deleteGoalStep":
            guard let id = t("id") else { return }
            s.lifeGoals?.steps.removeAll { $0.id == id }
        case "saveDayPlan":
            guard var row = decode(DayPlan.self, p) else { return }
            row.pending = true
            var planner = s.plannerData
            if let i = planner.plans.firstIndex(where: { $0.id == row.id }) { planner.plans[i] = row } else { planner.plans.append(row) }
            s.planner = planner
        case "deleteDayPlan":
            guard let id = t("id") else { return }
            s.planner?.plans.removeAll { $0.id == id }
        case "saveOutfit":
            guard var row = decode(Outfit.self, p) else { return }
            row.pending = true
            var planner = s.plannerData
            if let i = planner.outfits.firstIndex(where: { $0.id == row.id }) { planner.outfits[i] = row } else { planner.outfits.append(row) }
            s.planner = planner
        case "deleteOutfit":
            guard let id = t("id") else { return }
            s.planner?.outfits.removeAll { $0.id == id }
        case "saveClothing":
            guard var row = decode(Clothing.self, p) else { return }
            row.pending = true
            var planner = s.plannerData
            if let i = planner.wardrobe.firstIndex(where: { $0.id == row.id }) { planner.wardrobe[i] = row } else { planner.wardrobe.append(row) }
            s.planner = planner
        case "deleteClothing":
            guard let id = t("id") else { return }
            s.planner?.wardrobe.removeAll { $0.id == id }
        case "saveShoppingItem":
            guard var row = decode(ShoppingItem.self, p) else { return }
            var shop = s.shoppingData
            row.pending = true
            if let i = shop.items.firstIndex(where: { $0.id == row.id }) {
                if row.status == .bought { row.boughtOn = shop.items[i].boughtOn; row.tripId = shop.items[i].tripId }
                shop.items[i] = row
            } else {
                shop.items.append(row)
            }
            s.shopping = shop
        case "deleteShoppingItem":
            guard let id = t("id") else { return }
            s.shopping?.items.removeAll { $0.id == id }
        case "checkoutShopping":
            guard let tripId = t("tripId"), let date = t("date") else { return }
            var shop = s.shoppingData
            let lines = (p["items"] as? [[String: Any]]) ?? []
            for line in lines {
                guard let id = line["id"] as? String, let i = shop.items.firstIndex(where: { $0.id == id }) else { continue }
                shop.items[i].status = .bought
                shop.items[i].boughtOn = date
                shop.items[i].tripId = tripId
                if let price = (line["price"] as? NSNumber)?.doubleValue { shop.items[i].price = price }
            }
            shop.trips.removeAll { $0.id == tripId }
            shop.trips.append(ShoppingTrip(id: tripId, date: date, store: t("store"), items: lines.count, total: d("total"),
                                           method: t("method"), notes: t("notes"), pending: true))
            s.shopping = shop
        case "deleteShoppingTrip":
            guard let id = t("tripId") else { return }
            s.shopping?.trips.removeAll { $0.id == id }
            s.shopping?.items.removeAll { $0.tripId == id }
        case "saveSetAside":
            guard var row = decode(SetAsideEntry.self, p) else { return }
            row.pending = true
            var list = s.setAside ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.setAside = list
        case "deleteSetAside":
            guard let id = t("id") else { return }
            s.setAside?.removeAll { $0.id == id }
        case "saveSavingsGoal":
            guard let row = decode(SavingsGoal.self, p) else { return }
            var list = s.savingsGoals ?? []
            if let i = list.firstIndex(where: { $0.id == row.id }) { list[i] = row } else { list.append(row) }
            s.savingsGoals = list
        case "deleteSavingsGoal":
            guard let id = t("id") else { return }
            s.savingsGoals?.removeAll { $0.id == id }
            if var list = s.setAside {
                for i in list.indices where list[i].goalId == id { list[i].goalId = nil }
                s.setAside = list
            }
        case "saveSalary":
            guard let month = t("month"), let i = s.salary.firstIndex(where: { $0.month == month }) else { return }
            var m = s.salary[i]
            m.salary = d("salary"); m.otherIncome = d("otherIncome"); m.remittanceQar = d("remittanceQar")
            let income = (m.salary ?? 0) + (m.otherIncome ?? 0)
            m.totalIncome = income
            m.netSavings = income - (m.expenses ?? 0) - (m.remittanceQar ?? 0)
            m.savingsRate = income > 0 ? (m.netSavings ?? 0) / income : nil
            s.salary[i] = m
        default:
            break
        }
    }

    private func spent(on date: String, excludingRow row: Int?) -> Double {
        (snapshot?.expenses ?? []).filter { $0.date == date && $0.row != row }.reduce(0) { $0 + ($1.qar ?? 0) }
    }

    /// A notification the moment an expense pushes today over the daily cap.
    private func notifyIfOverCap(date: String, before: Double, after: Double) {
        guard date == SheetDate.today, let cap = snapshot?.settings.dailyLimit, before <= cap, after > cap else { return }
        let content = UNMutableNotificationContent()
        content.title = "Over today's spending cap"
        content.body = "\(SheetFormat.qar(after)) of \(SheetFormat.number(cap)) \(Money.main) spent today."
        content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "overCap.\(date)", content: content, trigger: nil))
    }

    var isTodayLogged: Bool { snapshot?.days.first { $0.date == SheetDate.today }?.isLogged ?? false }

    // MARK: - Transport

    private struct Envelope: Decodable {
        let ok: Bool
        let error: String?
        let data: SheetSnapshot?
    }

    private func postRequest(url: URL, action: String, payload: [String: Any], timeout: TimeInterval) throws -> URLRequest {
        try Self.webAppRequest(url: url, key: apiKey, action: action, payload: payload, timeout: timeout)
    }

    /// Every request to the web app, reads too: the key goes in the POST body,
    /// never in the link (links end up in server and proxy logs). `fields` sit
    /// beside `action`, where the script reads them (`id`, `size` for a photo).
    static func webAppRequest(url: URL, key: String, action: String, payload: [String: Any] = [:],
                              fields: [String: String] = [:], timeout: TimeInterval) throws -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = timeout
        // text/plain: Apps Script reads the raw body either way.
        request.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = ["key": key, "action": action, "payload": payload]
        request.httpBody = try JSONSerialization.data(withJSONObject: PendingWrite.jsonSafe(body.merging(fields) { current, _ in current }))
        return request
    }

    /// Apps Script answers a POST with a redirect to the result; URLSession
    /// follows it as a GET, which is exactly what the redirect expects.
    private func send(_ request: URLRequest) async throws -> SheetSnapshot {
        guard let snapshot = try await send(request, allowAck: false) else { throw SheetError.transient("no data") }
        return snapshot
    }

    /// The sheet's answer: its snapshot, or nil when a quick save was acknowledged without one.
    private func send(_ request: URLRequest, allowAck: Bool) async throws -> SheetSnapshot? {
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            // Google busy or briefly down: try again later rather than give up on the change.
            if http.statusCode == 408 || http.statusCode == 429 || http.statusCode >= 500 { throw SheetError.transient("HTTP \(http.statusCode)") }
            throw SheetError.message("The sheet answered with HTTP \(http.statusCode).")
        }
        let envelope: Envelope
        do {
            envelope = try JSONDecoder().decode(Envelope.self, from: data)
        } catch {
            // An HTML page instead of JSON almost always means the deployment
            // isn't public or the URL points somewhere else.
            let text = String(data: data, encoding: .utf8) ?? ""
            if text.contains("Authorization is required") || text.contains("authorization") || text.contains("Access denied") {
                throw SheetError.message("Your sheet needs its one-time setup: open the sheet ▸ Extensions ▸ Apps Script, choose setupNewWorkbook and press Run (allow access when asked).")
            }
            // Google's own "try again later" pages are HTML too: keep queued changes and retry.
            throw SheetError.transient("The web app didn't return data. Check the URL and that it's deployed for \"Anyone\".")
        }
        if envelope.ok, envelope.data == nil, allowAck { return nil }
        guard envelope.ok, let snapshot = envelope.data else {
            if envelope.error?.hasPrefix("Unauthorized") == true { throw SheetError.unauthorized }
            if let error = envelope.error, error.hasPrefix("SETUP_IN_PROGRESS") {
                throw SheetError.busy(error.replacingOccurrences(of: "SETUP_IN_PROGRESS: ", with: ""))
            }
            let error = envelope.error ?? "The sheet rejected the request."
            if SheetError.isTemporary(error) { throw SheetError.transient(error) }
            throw SheetError.message(error)
        }
        return snapshot
    }

    /// While the sheet rebuilds, check back every 15 seconds and load it the moment it's ready.
    private func noteSetup(_ progress: String) {
        setupProgress = progress
        errorMessage = nil
        guard setupPoll == nil else { return }
        setupPoll = Task {
            while !Task.isCancelled, setupProgress != nil {
                try? await Task.sleep(for: .seconds(15))
                await refresh()
            }
            setupPoll = nil
        }
    }

    private func apply(_ snapshot: SheetSnapshot) {
        setupProgress = nil
        confirmed = snapshot
        lastSynced = .now
        errorMessage = nil
        keyRejected = false
        saveCache(snapshot)
        recompute()
    }

    /// A wrong key resets the UI to the connect state; being offline keeps the
    /// last good data on screen; other failures show above it.
    /// Google's answers in the terms the queue understands: busy or down is
    /// "try again shortly"; anything else is a refusal to show.
    private func viaGoogle<T>(_ work: () async throws -> T) async throws -> T {
        do {
            return try await work()
        } catch let error as GoogleError {
            throw SheetError.fromGoogle(error)
        } catch let error as ScriptBackend.ScriptError {
            throw SheetError.message(error.localizedDescription)
        } catch is DecodingError {
            throw SheetError.message("The sheet's layout isn't what the app expects.")
        }
    }

    /// Signs in with Google and connects the tracker sheet in the user's own Drive,
    /// making it the first time. `progress` gets a line per step.
    func connectGoogle(progress: @escaping (String) -> Void = { _ in }) async throws {
        if !GoogleAuth.shared.isSignedIn { try await GoogleAuth.shared.signIn() }
        progress("Looking for your sheet…")
        let id = try await TrackerWorkbook.findOrCreate(progress: progress)
        googleSheetID = id
        webAppChosen = false
        progress("Loading it…")
        await refresh()
    }

    /// A new, empty tracker sheet from the app's blueprint, used from now on. The one in use
    /// stays in Drive, renamed "… (old 19 Oct 2026)", so signing in later finds the new one.
    func startNewGoogleSheet(progress: @escaping (String) -> Void = { _ in }) async throws {
        let old = googleSheetID
        let created = try await viaGoogle { try await TrackerWorkbook.create(progress: progress) }
        if let old {
            let stamp = Date.now.formatted(.dateTime.day().month(.abbreviated).year())
            try? await GoogleSheetsAPI().rename(old, to: "\(TrackerWorkbook.title) (old \(stamp))")
        }
        googleSheetID = created.id
        webAppChosen = false
        progress("Loading it…")
        await refresh()
    }

    /// A backup in Google Drive: a copy of the sheet ("… (backup 19 Oct 2026)") and the same as an
    /// Excel file in the "Life & Finance · Backups" folder. Returns the backup's name.
    func backUpGoogleSheet() async throws -> String {
        guard let id = googleSheetID else { throw SheetError.message("Sign in with Google first.") }
        let api = GoogleSheetsAPI()
        let name = "\(TrackerWorkbook.title) (backup \(Date.now.formatted(.dateTime.day().month(.abbreviated).year())))"
        return try await viaGoogle {
            _ = try await api.copyFile(id: id, name: name)
            let xlsx = try await api.exportXLSX(id)
            let folder = try await api.folder(named: "Life & Finance · Backups")
            _ = try await api.upload(xlsx, name: "\(name).xlsx", mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", folder: folder)
            return name
        }
    }

    /// Moves every other sheet this app made (test sheets, old copies) to the Drive trash,
    /// keeping the one in use and backups. Returns how many went.
    func trashOtherGoogleSheets() async throws -> Int {
        guard let current = googleSheetID else { throw SheetError.message("Sign in with Google first.") }
        let api = GoogleSheetsAPI()
        let others = try await viaGoogle { try await api.appSpreadsheets() }.filter { $0.id != current && !$0.name.contains("(backup") }
        for sheet in others { await api.trash(sheet.id) }
        return others.count
    }

    /// Back to no connection: forgets the sheet here (it stays in Drive) and signs out of Google.
    func disconnectGoogle() async {
        googleSheetID = nil
        await GoogleAuth.shared.signOut()
    }

    /// The tracker sheet's address in Google Sheets.
    var googleSheetURL: URL? { googleSheetID.flatMap { URL(string: "https://docs.google.com/spreadsheets/d/\($0)/edit") } }

    /// One request to the sheet's script running in the app, refused the way the web app would refuse it.
    @discardableResult
    private func viaScript(_ action: String, _ payload: [String: Any], reply: String? = nil) async throws -> [String: Any] {
        guard let backend else { throw SheetError.message("Sign in with Google first.") }
        let envelope = try await viaGoogle { try await backend.request(action, payload: payload, reply: reply) }
        guard envelope["ok"] as? Bool == true else {
            let error = envelope["error"] as? String ?? "The sheet refused the change."
            if SheetError.isTemporary(error) { throw SheetError.transient(error) }
            throw SheetError.message(error)
        }
        return envelope
    }

    /// "Reset all data" for a Google sign-in sheet: a fresh workbook from the
    /// design built into the app replaces the old one, which goes to the Drive trash.
    private func resetGoogleSheet() async throws -> [String: Any] {
        guard let old = googleSheetID else { return [:] }
        let created = try await viaGoogle { try await TrackerWorkbook.create() }
        googleSheetID = created.id
        await GoogleSheetsAPI().trash(old)
        return ["resetting": false]
    }

    private func handle(_ error: Error) {
        if case SheetError.busy(let progress) = error {
            noteSetup(progress)
            return
        }
        if case SheetError.unauthorized = error {
            resetData()
            keyRejected = true
        }
        if error is URLError {
            isOffline = true
            errorMessage = nil
        } else if case SheetError.transient = error {
            // Keep showing the last good data; it's tried again shortly. With nothing to show, say so.
            errorMessage = confirmed == nil ? Self.busyMessage : nil
        } else {
            errorMessage = error.localizedDescription
        }
    }

    /// Forgets the sheet's data here and on disk. Settings stay; so do queued
    /// writes, which belong to whatever sheet is connected next.
    private func resetData() {
        confirmed = nil
        snapshot = nil
        lastSynced = nil
        keyRejected = false
        try? FileManager.default.removeItem(at: cacheURL)
        WidgetSync.updateSheet(nil)
    }

    // MARK: - Cache

    private var cacheURL: URL {
        URL.applicationSupportDirectory.appending(path: "sheet-snapshot.json")
    }

    private func loadCache() {
        guard let data = try? Data(contentsOf: cacheURL),
              let cached = try? JSONDecoder().decode(SheetSnapshot.self, from: data) else { return }
        confirmed = cached
        lastSynced = (try? cacheURL.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
    }

    private func saveCache(_ snapshot: SheetSnapshot) {
        try? FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(snapshot) {
            try? data.write(to: cacheURL, options: .atomic)
        }
    }
}

/// One write waiting for the sheet. The payload is kept as JSON so the queue
/// survives the app being closed.
struct PendingWrite: Codable, Identifiable, Equatable {
    let id: String
    let action: String
    private var payloadJSON: Data
    var summary: String
    let createdAt: Date
    /// Held until then (deletes wait for the Undo window).
    var notBefore: Date?
    /// The placeholder row a queued add shows under until the sheet assigns one.
    var tempRow: Int?

    init(id: String, action: String, payload: [String: Any], summary: String, createdAt: Date, notBefore: Date?, tempRow: Int?) {
        self.id = id
        self.action = action
        self.payloadJSON = (try? JSONSerialization.data(withJSONObject: Self.jsonSafe(payload))) ?? Data("{}".utf8)
        self.summary = summary
        self.createdAt = createdAt
        self.notBefore = notBefore
        self.tempRow = tempRow
    }

    var payload: [String: Any] {
        get { ((try? JSONSerialization.jsonObject(with: payloadJSON)) as? [String: Any]) ?? [:] }
        set { payloadJSON = (try? JSONSerialization.data(withJSONObject: Self.jsonSafe(newValue))) ?? payloadJSON }
    }

    /// Blanks infinity and NaN (a pasted "inf" parses as a Double): JSON can't hold them, and
    /// JSONSerialization raises an exception for them, which `try?` doesn't catch.
    static func jsonSafe(_ value: Any) -> Any {
        switch value {
        case let number as Double where !number.isFinite: ""
        case let object as [String: Any]: object.mapValues(jsonSafe)
        case let list as [Any]: list.map(jsonSafe)
        default: value
        }
    }
}

enum SheetError: LocalizedError {
    case message(String)
    case unauthorized
    /// The sheet is being set up or reset; try again in a minute.
    case busy(String)
    /// Google or the sheet couldn't take it just now (busy, quota, timeout): retried, never dropped.
    case transient(String)
    /// The Google sign-in ended (revoked, or expired): queued changes wait for the next sign-in.
    case signedOut

    /// Google's answers in the terms the queue understands. A rejected token (401) is retried:
    /// the next try fetches a fresh one, or finds the sign-in gone.
    static func fromGoogle(_ error: GoogleError) -> SheetError {
        switch error {
        case .http(let status, let message):
            if status == 401 || status == 408 || status == 429 || status >= 500 { return .transient("HTTP \(status)") }
            return .message(message)
        case .signedOut: return .signedOut
        default: return .message(error.localizedDescription)
        }
    }

    /// Apps Script's wording for "try again later", as opposed to "this change is wrong".
    static func isTemporary(_ error: String) -> Bool {
        let e = error.lowercased()
        return ["lock timeout", "too many times", "timed out", "exceeded maximum execution time", "internal error",
                "service unavailable", "try again", "server error", "rate limit", "temporarily"].contains { e.contains($0) }
    }

    var errorDescription: String? {
        switch self {
        case .message(let text): text
        case .unauthorized: "The API key is wrong or was replaced. Run createApiKey in the sheet's Apps Script and paste the new key in Settings."
        case .busy(let progress): "Your sheet is being set up — \(progress). This takes a few minutes; pull down to refresh."
        case .transient(let detail): "Your sheet didn't answer just now (\(detail)). Your changes are kept and sent again shortly."
        case .signedOut: "Sign in with Google again. Your changes are kept and sent once you do."
        }
    }
}

/// Minimal generic-password Keychain access for one string per account.
enum Keychain {
    private static let service = "MyPersonalTracker"

    /// Replaces the value in place (an empty value deletes it), so a failed write
    /// never loses what was there. `accessible` defaults to after first unlock.
    @discardableResult
    static func set(_ value: String, for account: String, accessible: CFString = kSecAttrAccessibleAfterFirstUnlock) -> Bool {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account]
        guard !value.isEmpty else {
            let status = SecItemDelete(query as CFDictionary)
            return status == errSecSuccess || status == errSecItemNotFound
        }
        let attributes: [String: Any] = [kSecValueData as String: Data(value.utf8),
                                         kSecAttrAccessible as String: accessible]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecSuccess { return true }
        guard status == errSecItemNotFound else { return false }
        return SecItemAdd(query.merging(attributes) { $1 } as CFDictionary, nil) == errSecSuccess
    }

    static func get(_ account: String) -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account,
                                    kSecReturnData as String: true,
                                    kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

/// Which sheet the app talks to. The web app only when chosen: a key left in the Keychain
/// (after signing out of Google, or from an earlier install) doesn't switch to it.
enum SheetConnection: Equatable {
    case google, webApp, none

    static func of(googleSheet: Bool, signedIn: Bool, webAppChosen: Bool, webAppURL: String, apiKey: String) -> SheetConnection {
        if googleSheet { return signedIn ? .google : .none }
        guard webAppChosen, URL(string: webAppURL)?.scheme == "https", !apiKey.isEmpty else { return .none }
        return .webApp
    }

    /// Before this choice was saved, a key meant "use the web app". Kept only where it had
    /// loaded on this install (its data is cached); a reinstall's leftover key isn't a choice.
    static func chosenOnUpgrade(googleSheet: Bool, hasKey: Bool, hadData: Bool) -> Bool {
        !googleSheet && hasKey && hadData
    }
}
