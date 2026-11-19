//
//  SettingsModels.swift
//  MyPersonalTracker
//
//  Settings is one screen (SettingsViews.swift): your sheet, then pages that save
//  to it, then what's only on this phone. Mirrors SettingsPages.kt.
//

import SwiftUI

/// A target on a Settings page: its Settings key, and percentages shown as 95 but kept as 0.95.
struct SettingsField: Identifiable {
    let key: String
    let title: String
    var unit: String = ""
    var percent = false
    var id: String { key }
}

/// The Settings pages that save to the sheet, each with its own Save.
enum SettingsPage: String, CaseIterable, Identifiable {
    case money, healthFood, study
    var id: Self { self }

    var title: String {
        switch self {
        case .money: "Money"
        case .healthFood: "Health & food"
        case .study: "Study"
        }
    }

    var subtitle: String {
        switch self {
        case .money: "Currency, salary, spend limits, salary split"
        case .healthFood: "Water, sleep, steps, calories, protein"
        case .study: "How long to study each day and week"
        }
    }

    func fields(single: Bool) -> [SettingsField] {
        switch self {
        case .money: Self.spending(single: single) + Self.monthlyLimit + Self.split
        case .healthFood: Self.health + Self.nutrition
        case .study: Self.studyGoals
        }
    }

    func keys(single: Bool) -> Set<String> { Set(fields(single: single).map(\.key)) }

    static func spending(single: Bool) -> [SettingsField] {
        (single ? [] : [SettingsField(key: "RATE", title: "Exchange rate", unit: "\(Money.home) per \(Money.main)")]) + [
            SettingsField(key: "DAILY_LIMIT", title: "Daily spend limit", unit: Money.main),
            SettingsField(key: "SALARY", title: "Usual monthly salary", unit: Money.main),
        ]
    }
    /// The switch (1 / 0) and your own amount when it's off.
    static var monthlyLimit: [SettingsField] {
        [SettingsField(key: "LIMIT_FROM_SPLIT", title: "Monthly limit from salary split"),
         SettingsField(key: "CUSTOM_LIMIT", title: "Monthly spend limit", unit: Money.main)]
    }
    static var split: [SettingsField] {
        SplitBucket.allCases.map { SettingsField(key: $0.key, title: $0.rawValue, unit: "%", percent: true) }
            + [SettingsField(key: "EMERGENCY_GOAL", title: "Emergency fund goal", unit: Money.main)]
    }
    static let health = [
        SettingsField(key: "WATER_TARGET", title: "Water", unit: "L/day"),
        SettingsField(key: "SLEEP_TARGET", title: "Sleep", unit: "h/night"),
        SettingsField(key: "STEPS_TARGET", title: "Steps", unit: "/day"),
        SettingsField(key: "SCREEN_LIMIT", title: "Screen time limit", unit: "h/day"),
        SettingsField(key: "GREAT_DAY", title: "Great day threshold", unit: "%", percent: true),
    ]
    static var nutrition: [SettingsField] {
        [SettingsField(key: "CALORIE_TARGET", title: "Calories", unit: "kcal/day"),
         SettingsField(key: "PROTEIN_TARGET", title: "Protein", unit: "g/day"),
         SettingsField(key: "CARB_TARGET", title: "Carbs", unit: "g/day"),
         SettingsField(key: "FAT_TARGET", title: "Fat", unit: "g/day"),
         SettingsField(key: "FOOD_BUDGET", title: "Monthly food budget", unit: Money.main)]
    }
    static let studyGoals = [
        SettingsField(key: "STUDY_DAILY_TARGET", title: "Study every day", unit: "min"),
        SettingsField(key: "STUDY_WEEKLY_TARGET", title: "Study every week", unit: "hours"),
    ]

    /// What the sheet has now, by Settings key.
    static func current(_ settings: SheetSettings) -> [String: Double?] {
        ["RATE": settings.rate, "DAILY_LIMIT": settings.dailyLimit, "WATER_TARGET": settings.waterTarget,
         "SLEEP_TARGET": settings.sleepTarget, "STEPS_TARGET": settings.stepsTarget, "SCREEN_LIMIT": settings.screenLimit,
         "SALARY": settings.salary, "GREAT_DAY": settings.greatDay,
         "CALORIE_TARGET": settings.calorieTarget, "PROTEIN_TARGET": settings.proteinTarget, "CARB_TARGET": settings.carbTarget,
         "FAT_TARGET": settings.fatTarget, "FOOD_BUDGET": settings.foodBudget,
         "STUDY_DAILY_TARGET": settings.studyDailyTarget, "STUDY_WEEKLY_TARGET": settings.studyWeeklyTarget,
         "EMERGENCY_GOAL": settings.emergencyGoal,
         "LIMIT_FROM_SPLIT": settings.limitFollowsSplit ? 1 : 0, "CUSTOM_LIMIT": settings.customLimit]
            .merging(Dictionary(uniqueKeysWithValues: SplitBucket.allCases.map { ($0.key, Optional(settings.share($0))) })) { $1 }
    }
}

/// What you typed on one Settings page, and what of it differs from the sheet.
@Observable
@MainActor
final class SettingsDraft {
    let page: SettingsPage
    private let fields: [SettingsField]
    private var before: [String: Double?] = [:]
    /// As typed, by Settings key (percentages as fractions, like the sheet).
    var values: [String: Double?] = [:]

    init(page: SettingsPage, single: Bool = Money.single) {
        self.page = page
        fields = page.fields(single: single)
    }

    func seed(_ settings: SheetSettings?) {
        guard let settings else { return }
        before = SettingsPage.current(settings)
        values = Dictionary(uniqueKeysWithValues: fields.map { ($0.key, before[$0.key] ?? nil) })
    }

    /// Percent fields are shown as 95 and kept as 0.95.
    func binding(_ key: String, percent: Bool) -> Binding<Double?> {
        Binding(
            get: { (self.values[key] ?? nil).map { percent ? $0 * 100 : $0 } },
            set: { self.values[key] = $0.map { percent ? $0 / 100 : $0 } }
        )
    }

    /// Only this page's keys, and only numbers (a cleared field keeps the sheet's value).
    var changes: [String: Double] {
        var out: [String: Double] = [:]
        for field in fields {
            guard let typed = values[field.key] ?? nil else { continue }
            if let old = before[field.key] ?? nil, Self.shown(old, field.percent) == Self.shown(typed, field.percent) { continue }
            out[field.key] = typed
        }
        return out
    }

    var hasChanges: Bool { !changes.isEmpty }

    /// As a field shows it (NumberRow rounds to 2 decimals), so just opening a page isn't a change.
    private static func shown(_ value: Double, _ percent: Bool) -> Double {
        ((percent ? value * 100 : value) * 100).rounded()
    }
}

/// Which morning notification comes. With the sheet it's the briefing; without, the daily smile.
enum MorningSlot {
    case briefing, smile, off

    static func of(sheetConnected: Bool, briefingOn: Bool, morningOn: Bool) -> MorningSlot {
        if sheetConnected { return briefingOn ? .briefing : .off }
        return morningOn ? .smile : .off
    }
}

extension ReminderSettings {
    private static let morningSeparateKey = "morningReminderSeparate"

    /// Once: the morning reminder gets its own switch instead of hanging off Daily reminders
    /// (now the evening one). It stays on only if it used to come.
    static func separateMorning(_ defaults: UserDefaults = .standard) {
        guard defaults.object(forKey: morningSeparateKey) == nil else { return }
        let morning = defaults.object(forKey: morningEnabledKey) as? Bool ?? true
        defaults.set(defaults.bool(forKey: enabledKey) && morning, forKey: morningEnabledKey)
        defaults.set(true, forKey: morningSeparateKey)
    }
}

/// The Notifications row on Settings: how many are on.
enum NotificationSummary {
    /// The switches that apply: without a sheet, only the morning and evening reminders.
    static func count(_ defaults: UserDefaults = .standard, sheetConnected: Bool) -> (on: Int, total: Int) {
        func isOn(_ key: String) -> Bool { defaults.object(forKey: key) as? Bool ?? (key != ReminderSettings.enabledKey) }
        let keys = sheetConnected
            ? [ReminderSettings.briefingEnabledKey, ReminderSettings.enabledKey, ReminderSettings.billRemindersKey,
               ReminderSettings.renewalRemindersKey, ReminderSettings.parentRemindersKey, BudgetAlerts.enabledKey,
               ReminderSettings.weeklyReviewKey]
            : [ReminderSettings.morningEnabledKey, ReminderSettings.enabledKey]
        return (keys.filter(isOn).count, keys.count)
    }

    static func text(on: Int, total: Int) -> String {
        switch on {
        case total: "All on"
        case 0: "All off"
        default: "\(on) of \(total) on"
        }
    }
}

/// The sheet row at the top of Settings.
enum SheetStatus {
    static func subtitle(connected: Bool, account: String?, synced: Date?, now: Date = .now) -> String {
        guard connected else { return "Not connected — tap to connect" }
        if let account { return "Connected · \(account)" }
        if let synced {
            let relative = RelativeDateTimeFormatter()
            relative.dateTimeStyle = .named
            return "Connected · synced \(relative.localizedString(for: synced, relativeTo: now))"
        }
        return "Connected"
    }
}

/// What "Your plan" on Settings shows. Connecting happens only on the Your sheet row above it.
enum PlanState: Equatable {
    case ready, hidden, loading
    case failed(String)

    static func of(connected: Bool, loaded: Bool, loading: Bool, error: String?) -> PlanState {
        if !connected { return .hidden }
        if loaded { return .ready }
        guard let error, !loading else { return .loading }
        return .failed(error)
    }
}
