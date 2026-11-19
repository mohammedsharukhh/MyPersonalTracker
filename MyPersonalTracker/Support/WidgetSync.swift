//
//  WidgetSync.swift
//  MyPersonalTracker
//

import Foundation
import SwiftData
import WidgetKit

/// Writes the widget snapshot to the shared App Group and asks WidgetKit to redraw.
enum WidgetSync {
    static let heatmapDays = 84

    static func update(context: ModelContext, now: Date = .now) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let logs = (try? context.fetch(FetchDescriptor<DailyLog>())) ?? []
        let todos = (try? context.fetch(FetchDescriptor<TodoItem>(sortBy: [SortDescriptor(\.createdAt)]))) ?? []

        let stats = ProgressStats(logs: logs, expenses: [], today: now, calendar: calendar)
        let todayLog = logs.first { $0.date == today }

        let recentLevels: [Int] = (0..<heatmapDays).reversed().map { offset in
            guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { return 0 }
            return stats.heatLevels[calendar.startOfDay(for: day)] ?? 0
        }

        let snapshot = WidgetSnapshot(
            generatedAt: now,
            currentStreak: stats.currentStreak,
            showedUpToday: todayLog?.showedUp ?? false,
            todayMinutes: todayLog?.totalMinutes ?? 0,
            openTodos: todos.filter { !$0.isDone }.map(\.title),
            doneTodayCount: todos.count { $0.wasCompleted(on: today, calendar: calendar) },
            recentLevels: recentLevels
        )
        var withSheet = snapshot
        withSheet.sheet = WidgetSnapshot.load()?.sheet
        withSheet.save()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Adds the Google Sheet's "today" to the widget snapshot (or clears it).
    /// Called whenever the sheet data on screen changes.
    @MainActor
    /// Today's steps straight from Apple Health (fresher than the sheet), from its last sync.
    private static var healthStepsToday: Int?

    static func updateSheet(_ sheet: SheetSnapshot?, stepsToday: Int? = nil) {
        if let stepsToday { healthStepsToday = stepsToday }
        var snapshot = WidgetSnapshot.load() ?? WidgetSnapshot(
            generatedAt: .now, currentStreak: 0, showedUpToday: false, todayMinutes: 0,
            openTodos: [], doneTodayCount: 0, recentLevels: Array(repeating: 0, count: heatmapDays))
        let previous = snapshot.sheet
        if let sheet {
            let today = SheetDate.today
            let day = sheet.days.first { $0.date == today }
            let spent = sheet.expenses.filter { $0.date == today }.reduce(0) { $0 + ($1.qar ?? 0) }
            let month = String(today.prefix(7))
            let monthSpent = sheet.expenses.filter { ($0.date ?? "").hasPrefix(month) }.reduce(0) { $0 + ($1.qar ?? 0) }
            snapshot.sheet = WidgetSnapshot.SheetToday(
                date: today,
                logged: day?.isLogged ?? false,
                score: day?.score,
                streak: Int(day?.streak ?? sheet.days.last { $0.date < today && $0.isLogged }?.streak ?? 0),
                spentToday: spent,
                dailyCap: sheet.settings.dailyLimit,
                monthSpent: monthSpent,
                monthlyLimit: sheet.settings.monthlyLimit,
                currency: sheet.settings.mainCurrency,
                split: splitRows(sheet, month: month),
                study: studyToday(sheet),
                shopping: shoppingList(sheet),
                steps: stepsSummary(sheet))
        } else {
            snapshot.sheet = nil
        }
        guard snapshot.sheet != previous else { return }
        snapshot.save()
        WidgetCenter.shared.reloadAllTimelines()
    }

    private static func stepsSummary(_ sheet: SheetSnapshot) -> WidgetSnapshot.SheetToday.Steps? {
        let today = max(healthStepsToday ?? 0, Int(sheet.days.first { $0.date == SheetDate.today }?.steps ?? 0))
        let week = sheet.stepsWeek(todaySteps: today)
        guard today > 0 || week.contains(where: { $0 > 0 }) else { return nil }
        return .init(today: today, target: Int(sheet.settings.stepsTarget ?? 8000),
                     streak: sheet.stepStreak(todaySteps: today), week: week)
    }

    private static func splitRows(_ sheet: SheetSnapshot, month: String) -> [WidgetSnapshot.SheetToday.SplitRow]? {
        guard SalarySplit.income(sheet, month: month) > 0 else { return nil }
        return SalarySplit.lines(sheet, month: month).map {
            .init(name: $0.bucket.rawValue, symbol: $0.bucket.symbol, planned: $0.planned, used: $0.used, saving: $0.bucket.isSaving)
        }
    }

    private static func studyToday(_ sheet: SheetSnapshot) -> WidgetSnapshot.SheetToday.Study? {
        let data = sheet.studyData
        guard !data.subjects.isEmpty else { return nil }
        let next = data.currentTopic
        return .init(minutesToday: Int(data.minutes(on: SheetDate.today).rounded()),
                     dailyTarget: Int(sheet.settings.studyDailyTarget ?? 60),
                     streak: data.streak(),
                     due: data.dueTopics().count,
                     next: next?.topic,
                     nextSubject: data.subject(next?.subjectId)?.name)
    }

    private static func shoppingList(_ sheet: SheetSnapshot) -> WidgetSnapshot.SheetToday.Shopping? {
        guard let list = sheet.shopping else { return nil }
        let toBuy = list.toBuy
        let priced = toBuy.compactMap { $0.price ?? list.lastPrice(of: $0.item) }
        return .init(count: toBuy.count, inCart: list.inCart.count,
                     items: toBuy.prefix(6).map { item in item.quantity.map { "\(item.item) · \($0)" } ?? item.item },
                     estimate: priced.isEmpty ? nil : priced.reduce(0, +))
    }
}
