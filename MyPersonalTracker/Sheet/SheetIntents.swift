//
//  SheetIntents.swift
//  MyPersonalTracker
//
//  Siri, Spotlight, the Shortcuts app and the Action button: add an expense
//  by voice without opening the app, ask what you've spent today, or jump
//  straight to logging your day.
//

import AppIntents
import SwiftUI

/// Which tab or editor the app should show; set by intents that open the app.
@MainActor @Observable
final class AppNavigation {
    static let shared = AppNavigation()
    var tab = UserDefaults.standard.integer(forKey: "sheetTab")
    var showAddExpense = false
    /// More (summaries, tools) — opened from the button on every tab.
    var showMore = false
    /// Settings — its own gear on every tab, next to More.
    var showSettings = false
    /// Daily opens on this section next ("Planner"), e.g. from the Dashboard's plan card.
    var dailySection: String?
    /// The Planner jumps to the week of this Monday next ("Plan next week").
    var plannerWeek: Date?
    var showRenewals = false
    var showCards = false
    var showWeeklyReview = false
    var showYearReview = false
    var showHabits = false
    var showDayLog = false
    var showPay = false
    /** Pay opens straight on the QR scanner (the Pay widget). */
    var payWithScan = false
    /// Pay opens with this payee filled in ("Send money" to Mom).
    var payTo: PayPrefill?

    /// Where a tapped notification leads ("open" in its userInfo).
    func open(_ target: String?) {
        switch target {
        case "dashboard": tab = AppTab.dashboard
        case "finance": tab = AppTab.finance
        case "renewals": showRenewals = true
        case "cards": showCards = true
        case "parents": tab = AppTab.parents
        case "weeklyReview": showWeeklyReview = true
        case "habits": showHabits = true
        case "logToday": showDayLog = true
        case "settings": showSettings = true
        case "more": showMore = true
        case "pay": showPay = true
        case "scan": payWithScan = true; showPay = true
        default: break
        }
    }
}

/// Tab indices, left to right.
enum AppTab {
    static let dashboard = 0, daily = 1, parents = 2, study = 3, food = 4, finance = 5
}

/// The "More" button in each tab's navigation bar: summaries and tools.
struct MoreButton: View {
    var body: some View {
        Button { AppNavigation.shared.showMore = true } label: {
            Image(systemName: "ellipsis.circle")
        }
        .accessibilityLabel("More")
    }
}

/// Settings, straight from each tab's navigation bar (not inside More).
struct SettingsButton: View {
    var body: some View {
        Button { AppNavigation.shared.showSettings = true } label: {
            Image(systemName: "gearshape")
        }
        .accessibilityLabel("Settings")
    }
}

struct AddExpenseIntent: AppIntent {
    static let title: LocalizedStringResource = "Add Expense"
    static let description = IntentDescription("Adds an expense to your Life & Finance sheet. Works offline — it syncs later.")

    @Parameter(title: "Amount", description: "In your sheet's main currency.")
    var amount: Double

    @Parameter(title: "What was it?")
    var details: String?

    @Parameter(title: "Category", optionsProvider: CategoryOptions())
    var category: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$amount) for \(\.$details)") { \.$category }
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let store = SheetStore.shared
        guard store.isConfigured || store.isDemo else {
            return .result(dialog: "Connect your Google Sheet in the app first.")
        }
        guard amount > 0 else { return .result(dialog: "The amount needs to be more than zero.") }
        let lists = store.snapshot?.lists
        let name = details?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let chosen = category ?? CategoryGuess.guess(name, among: lists?.categories ?? []) ?? lists?.categories.last ?? "Other"
        await store.saveExpense(row: nil, date: SheetDate.today, description: name, category: chosen,
                                method: lists?.methods.first ?? "Cash", qar: amount, notes: "Added with Siri")
        // The app may be suspended as soon as this returns: give the save a moment to reach the sheet.
        await store.waitForSync(seconds: 15)
        let what = name.isEmpty ? chosen : name
        let tail = store.pending.isEmpty ? "" : " It'll sync when you're online."
        return .result(dialog: "Added \(SheetFormat.qar(amount)) \(Money.main) for \(what).\(tail)")
    }
}

struct SpentTodayIntent: AppIntent {
    static let title: LocalizedStringResource = "Spent Today"
    static let description = IntentDescription("Tells you how much you've spent today against your daily cap.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let store = SheetStore.shared
        if store.snapshot == nil { await store.refresh() }
        guard let snapshot = store.snapshot else { return .result(dialog: "Connect your Google Sheet in the app first.") }
        let spent = snapshot.expenses.filter { $0.date == SheetDate.today }.reduce(0) { $0 + ($1.qar ?? 0) }
        let cap = snapshot.settings.dailyLimit ?? 0
        let main = snapshot.settings.mainCurrency
        let verdict = cap <= 0 ? "" : (spent > cap ? " That's \(SheetFormat.qar(spent - cap)) over your cap." : " \(SheetFormat.qar(cap - spent)) left under your cap.")
        return .result(dialog: "You've spent \(SheetFormat.qar(spent)) \(main) today.\(verdict)")
    }
}

struct OpenDailyLogIntent: AppIntent {
    static let title: LocalizedStringResource = "Log Today"
    static let description = IntentDescription("Opens today's habit log.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppNavigation.shared.tab = AppTab.daily
        return .result()
    }
}

struct OpenAddExpenseIntent: AppIntent {
    static let title: LocalizedStringResource = "Open Add Expense"
    static let description = IntentDescription("Opens the app on the Add expense screen.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppNavigation.shared.tab = AppTab.finance
        AppNavigation.shared.showAddExpense = true
        return .result()
    }
}

struct OpenPayIntent: AppIntent {
    static let title: LocalizedStringResource = "Pay with UPI"
    static let description = IntentDescription("Opens Pay with UPI: scan, pick PhonePe / Google Pay / Paytm, and it's added to Finance.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppNavigation.shared.showPay = true
        return .result()
    }
}

nonisolated extension ParentAsk.Who: AppEnum {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Parent"
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [.mom: "Mom", .dad: "Dad", .both: "Mom & Dad"]
}

/// "Mom asked…" — on the phone with them, add it without opening the app.
struct AddParentAskIntent: AppIntent {
    static let title: LocalizedStringResource = "Add What Mom or Dad Asked"
    static let description = IntentDescription("Adds something your parents asked you to do. You're reminded every day until it's done.")

    @Parameter(title: "Who asked", default: .mom)
    var who: ParentAsk.Who

    @Parameter(title: "What did they ask for?")
    var task: String

    static var parameterSummary: some ParameterSummary {
        Summary("\(\.$who) asked: \(\.$task)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let store = SheetStore.shared
        guard store.isConfigured || store.isDemo else {
            return .result(dialog: "Connect your Google Sheet in the app first.")
        }
        let text = task.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return .result(dialog: "Say what they asked for.") }
        let ask = ParentAsk(task: text, who: who)
        store.saveParentAsk(ask)
        await ReminderScheduler.scheduleSheetAlerts()
        await store.waitForSync(seconds: 15)
        let at = PlanTime.display(ask.time) ?? ParentAsk.defaultTime
        return .result(dialog: "Added for \(who.label). I'll remind you at \(at) every day until it's done.")
    }
}

struct CategoryOptions: DynamicOptionsProvider {
    @MainActor
    func results() async throws -> [String] {
        SheetStore.shared.snapshot?.lists.categories ?? [
            "Groceries", "Food & Dining", "Transport", "Rent & Utilities", "Mobile & Internet", "Health & Pharmacy",
            "Personal Care", "Household", "Clothing", "Remittance", "Entertainment", "Gifts & Family", "Education", "Travel", "Other"
        ]
    }
}

/// Picks a category from words in the description ("coffee" → Food & Dining).
enum CategoryGuess {
    private static let words: [(String, [String])] = [
        ("Groceries", ["grocery", "groceries", "supermarket", "lulu", "carrefour", "vegetable", "milk"]),
        ("Food & Dining", ["coffee", "lunch", "dinner", "breakfast", "restaurant", "cafe", "tea", "pizza", "burger", "food", "snack"]),
        ("Transport", ["uber", "careem", "taxi", "metro", "bus", "fuel", "petrol", "parking", "karwa"]),
        ("Rent & Utilities", ["rent", "electricity", "water bill", "utility", "kahramaa"]),
        ("Mobile & Internet", ["recharge", "mobile", "internet", "wifi", "ooredoo", "vodafone", "data"]),
        ("Health & Pharmacy", ["pharmacy", "medicine", "doctor", "clinic", "hospital"]),
        ("Personal Care", ["haircut", "salon", "barber", "shampoo"]),
        ("Clothing", ["shirt", "shoes", "clothes", "jeans", "dress"]),
        ("Remittance", ["remittance", "sent home", "transfer home"]),
        ("Entertainment", ["movie", "cinema", "netflix", "game", "concert"]),
        ("Gifts & Family", ["gift", "family", "birthday"]),
        ("Education", ["course", "book", "udemy", "class", "exam"]),
        ("Travel", ["flight", "hotel", "airbnb", "ticket"])
    ]

    static func guess(_ text: String, among categories: [String]) -> String? {
        let lower = text.lowercased()
        guard !lower.isEmpty else { return nil }
        return words.first { pair in categories.contains(pair.0) && pair.1.contains { lower.contains($0) } }?.0
    }
}

struct TrackerShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: AddExpenseIntent(),
                    phrases: ["Add an expense in \(.applicationName)", "Log spending in \(.applicationName)"],
                    shortTitle: "Add Expense", systemImageName: "plus.circle.fill")
        AppShortcut(intent: SpentTodayIntent(),
                    phrases: ["How much did I spend today in \(.applicationName)", "Spent today in \(.applicationName)"],
                    shortTitle: "Spent Today", systemImageName: "banknote")
        AppShortcut(intent: OpenDailyLogIntent(),
                    phrases: ["Log my day in \(.applicationName)", "Log today in \(.applicationName)"],
                    shortTitle: "Log Today", systemImageName: "checklist")
        AppShortcut(intent: AddParentAskIntent(),
                    phrases: ["\(\.$who) asked something in \(.applicationName)", "Add what \(\.$who) asked in \(.applicationName)",
                              "Add a request from my parents in \(.applicationName)"],
                    shortTitle: "Mom & Dad Asked", systemImageName: "figure.2.and.child.holdinghands")
        AppShortcut(intent: OpenPayIntent(),
                    phrases: ["Pay with UPI in \(.applicationName)", "Pay in \(.applicationName)"],
                    shortTitle: "Pay with UPI", systemImageName: "indianrupeesign.circle")
        AppShortcut(intent: OpenAddExpenseIntent(),
                    phrases: ["Open add expense in \(.applicationName)"],
                    shortTitle: "Open Add Expense", systemImageName: "square.and.pencil")
    }
}
