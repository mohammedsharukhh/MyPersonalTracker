//
//  Reminders.swift
//  MyPersonalTracker
//

import Foundation
import SwiftData
import UserNotifications

/// User-facing reminder preferences, stored in UserDefaults (shared keys with `@AppStorage`).
enum ReminderSettings {
    static let enabledKey = "remindersEnabled"
    static let eveningMinutesKey = "eveningReminderMinutes"
    static let morningEnabledKey = "morningReminderEnabled"
    static let morningMinutesKey = "morningReminderMinutes"
    static let smileCheckInEnabledKey = "smileCheckInEnabled"
    static let studyEnabledKey = "studyReminderEnabled"
    static let studyMinutesKey = "studyReminderMinutes"
    static let defaultStudyMinutes = 19 * 60

    /// Sheet-driven notifications, each on by default (they need notification permission).
    static let briefingEnabledKey = "morningBriefingEnabled"
    static let billRemindersKey = "billRemindersEnabled"
    static let renewalRemindersKey = "renewalRemindersEnabled"
    static let weeklyReviewKey = "weeklyReviewEnabled"
    static let parentRemindersKey = "parentRemindersEnabled"

    static func isOn(_ key: String) -> Bool { UserDefaults.standard.object(forKey: key) as? Bool ?? true }

    static let defaultEveningMinutes = 21 * 60
    static let defaultMorningMinutes = 8 * 60
}

/// Schedules one-off local notifications for the next week, skipping today's when it's already done.
/// Re-run whenever the app becomes active or goes to the background.
enum ReminderScheduler {
    private static let idPrefix = "reminder."
    private static let daysAhead = 7

    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func refresh(context: ModelContext, now: Date = .now) async {
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(idPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)

        let defaults = UserDefaults.standard
        let authorized = await center.notificationSettings().authorizationStatus == .authorized
        // The study reminder has its own switch (Study ▸ targets), separate from the daily-log ones.
        if authorized, defaults.bool(forKey: ReminderSettings.studyEnabledKey) {
            await scheduleStudy(now: now)
        }
        await scheduleSheetAlerts(now: now)
        guard authorized else { return }
        // The evening and morning reminders have a switch each (Settings ▸ Notifications).
        let eveningOn = defaults.bool(forKey: ReminderSettings.enabledKey)
        let morningOn = defaults.object(forKey: ReminderSettings.morningEnabledKey) as? Bool ?? true

        let eveningMinutes = defaults.object(forKey: ReminderSettings.eveningMinutesKey) as? Int ?? ReminderSettings.defaultEveningMinutes
        let morningMinutes = defaults.object(forKey: ReminderSettings.morningMinutesKey) as? Int ?? ReminderSettings.defaultMorningMinutes

        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let logs = (try? context.fetch(FetchDescriptor<DailyLog>())) ?? []
        let todayLog = logs.first { $0.date == today }
        // With the Google Sheet connected, "logged today" and the streak come from it.
        let sheet = SheetStore.shared.snapshot
        let sheetDay = sheet?.days.first { $0.date == SheetDate.today }
        let loggedToday = sheet != nil ? (sheetDay?.isLogged ?? false) : (todayLog?.showedUp == true)
        let streak = sheet != nil
            ? Int(sheet?.days.last { $0.date < SheetDate.today && $0.isLogged }?.streak ?? 0)
            : ProgressStats(logs: logs, expenses: [], today: now).currentStreak

        for offset in 0..<daysAhead {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today) else { continue }

            if eveningOn, let fire = calendar.date(byAdding: .minute, value: eveningMinutes, to: day), fire > now,
               !(offset == 0 && loggedToday) {
                let content = UNMutableNotificationContent()
                if offset == 0, streak > 0 {
                    content.title = "Don't break your \(streak)-day streak 🔥"
                    content.body = sheet != nil ? "You haven't logged today's habits yet — it takes a few taps."
                                                : "You haven't logged today yet. What did you do, what did you learn?"
                } else {
                    content.title = "Time to log your day ✍️"
                    content.body = sheet != nil ? "Log today's habits and anything you spent."
                                                : "What did you do, what did you learn, and how long did you study?"
                }
                content.sound = .default
                add(content, at: fire, id: "\(idPrefix)evening.\(offset)", calendar: calendar)
            }

            // With the sheet connected, the morning slot is the briefing (or nothing, if that's off).
            if MorningSlot.of(sheetConnected: sheet != nil, briefingOn: ReminderSettings.isOn(ReminderSettings.briefingEnabledKey), morningOn: morningOn) == .smile,
               let fire = calendar.date(byAdding: .minute, value: morningMinutes, to: day), fire > now,
               !(offset == 0 && todayLog?.selfieData != nil) {
                let content = UNMutableNotificationContent()
                content.title = "Good morning! 😁"
                content.body = "Take your daily smile and write today's to-dos on your sticky note."
                content.sound = .default
                add(content, at: fire, id: "\(idPrefix)morning.\(offset)", calendar: calendar)
            }
        }
    }

    /// "Time to study": what's due for revision that day, what's next, how much is left of today's target.
    /// Skipped on a day the target is already reached.
    @MainActor
    private static func scheduleStudy(now: Date) {
        guard let snapshot = SheetStore.shared.snapshot else { return }
        let data = snapshot.studyData
        guard !data.activeSubjects.isEmpty else { return }
        let minutesOfDay = UserDefaults.standard.object(forKey: ReminderSettings.studyMinutesKey) as? Int ?? ReminderSettings.defaultStudyMinutes
        let target = max(snapshot.settings.studyDailyTarget ?? 60, 1)
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        for offset in 0..<daysAhead {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today),
                  let fire = calendar.date(byAdding: .minute, value: minutesOfDay, to: day), fire > now else { continue }
            let key = SheetDate.string(day)
            let studied = offset == 0 ? data.minutes(on: key) : 0
            if studied >= target { continue }
            let due = data.dueTopics(today: key).count
            var parts: [String] = []
            if due > 0 { parts.append("\(due) concept\(due == 1 ? "" : "s") to revise") }
            if let next = data.currentTopic { parts.append("next: \(next.topic)") }
            parts.append(studied > 0 ? "\(StudyFormat.minutes(target - studied)) to go today" : "\(StudyFormat.minutes(target)) today")
            let content = UNMutableNotificationContent()
            let streak = offset == 0 ? data.streak(today: now) : 0
            content.title = streak > 0 ? "Keep your \(streak)-day study streak 📚" : "Time to study 📚"
            content.body = parts.joined(separator: " · ").prefix(1).uppercased() + parts.joined(separator: " · ").dropFirst()
            content.sound = .default
            add(content, at: fire, id: "\(idPrefix)study.\(offset)", calendar: calendar)
        }
    }

    private static func add(_ content: UNNotificationContent, at date: Date, id: String, calendar: Calendar) {
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    // MARK: - From the sheet

    private static let sheetPrefix = "sheet."

    /// The morning briefing, bills due tomorrow, renewal reminders, what Mom and
    /// Dad asked for and the Sunday week-in-review — all from the latest snapshot,
    /// rescheduled each time the app opens or one of them changes. Each has its
    /// own switch in Settings.
    @MainActor
    static func scheduleSheetAlerts(now: Date = .now) async {
        let center = UNUserNotificationCenter.current()
        let old = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(sheetPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: old)
        guard await center.notificationSettings().authorizationStatus == .authorized,
              let s = SheetStore.shared.snapshot, !SheetStore.shared.isDemo else { return }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let morning = UserDefaults.standard.object(forKey: ReminderSettings.morningMinutesKey) as? Int ?? ReminderSettings.defaultMorningMinutes

        func at(_ day: Date, minutes: Int) -> Date? { calendar.date(byAdding: .minute, value: minutes, to: calendar.startOfDay(for: day)) }
        func post(_ id: String, _ title: String, _ body: String, at date: Date, open: String? = nil, parentAsk: String? = nil) {
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = .default
            if let open { content.userInfo = ["open": open] }
            // One request: a Done button that marks it done without opening the app.
            if let parentAsk {
                content.userInfo["parentAsk"] = parentAsk
                content.categoryIdentifier = ParentReminders.categoryId
            }
            add(content, at: date, id: sheetPrefix + id, calendar: calendar)
        }

        for offset in 0..<daysAhead {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today), let fire = at(day, minutes: morning), fire > now else { continue }
            if ReminderSettings.isOn(ReminderSettings.briefingEnabledKey) {
                let b = Briefing.text(s, on: day, includeLive: offset <= 1)
                post("brief.\(offset)", b.title, b.body, at: fire, open: "dashboard")
            }
            // Bills due tomorrow, the morning before.
            if ReminderSettings.isOn(ReminderSettings.billRemindersKey), let tomorrow = calendar.date(byAdding: .day, value: 1, to: day) {
                let bills = Bills.due(s, on: tomorrow)
                if !bills.isEmpty {
                    let total = bills.reduce(0) { $0 + ($1.amount ?? 0) }
                    post("bill.\(offset)", bills.count == 1 ? "Due tomorrow: \(bills[0].description ?? "a bill")" : "\(bills.count) bills due tomorrow",
                         bills.map { "\($0.description ?? "Bill") · \(SheetFormat.qar($0.amount)) \(Money.main)" }.joined(separator: "\n")
                            + (bills.count > 1 ? "\nTotal \(SheetFormat.qar(total)) \(Money.main)" : ""),
                         at: fire.addingTimeInterval(60), open: "finance")
                }
            }
        }

        // Renewals: each reminder day within the next two months, soonest first (iOS keeps 64 pending in all).
        if ReminderSettings.isOn(ReminderSettings.renewalRemindersKey) {
            var alerts: [(Date, Renewal, Int)] = []
            for r in s.renewalList {
                guard let expires = SheetDate.date(r.expires) else { continue }
                for d in r.remind {
                    guard let day = calendar.date(byAdding: .day, value: -d, to: expires), let fire = at(day, minutes: morning),
                          fire > now, fire < now.addingTimeInterval(60 * 86400) else { continue }
                    alerts.append((fire, r, d))
                }
            }
            for (fire, r, d) in alerts.sorted(by: { $0.0 < $1.0 }).prefix(15) {
                post("renewal.\(r.id).\(d)", d == 0 ? "\(r.item) expires today" : "\(r.item) expires in \(d) day\(d == 1 ? "" : "s")",
                     "On \(SheetDate.short(r.expires)). Renew it, then mark it renewed in the app.", at: fire.addingTimeInterval(120), open: "renewals")
            }
        }

        // What Mom and Dad asked for, at each one's own time, until it's done (10 at most, soonest first).
        if ReminderSettings.isOn(ReminderSettings.parentRemindersKey) {
            for (fire, asks) in ParentReminders.slots(s.openParentAsks, from: now, snoozed: ParentSnooze.all).prefix(10) {
                let m = ParentReminders.message(asks, on: fire)
                post("parents.\(Int(fire.timeIntervalSince1970))", m.title, m.body, at: fire, open: "parents",
                     parentAsk: asks.count == 1 ? asks[0].id : nil)
            }
            // Once a day, only when there's something: medicines running low, their days coming up, a call that's due.
            let minutes = UserDefaults.standard.object(forKey: ParentDigest.minutesKey) as? Int ?? ParentDigest.defaultMinutes
            let last = [ParentAsk.Who.mom: ParentTalk.last(.mom), .dad: ParentTalk.last(.dad)].compactMapValues { $0 }
            let every = [ParentAsk.Who.mom: ParentTalk.every(.mom), .dad: ParentTalk.every(.dad)]
            for offset in 0..<daysAhead {
                guard let day = calendar.date(byAdding: .day, value: offset, to: today), let fire = at(day, minutes: minutes), fire > now else { continue }
                let lines = ParentDigest.lines(s, on: day, last: last, every: every)
                guard !lines.isEmpty else { continue }
                let m = ParentDigest.message(lines)
                post("parentDigest.\(offset)", m.title, m.body, at: fire, open: "parents")
            }
        }

        // Sunday evening: the week in review.
        if ReminderSettings.isOn(ReminderSettings.weeklyReviewKey) {
            let sunday = calendar.nextDate(after: calendar.date(byAdding: .second, value: -1, to: today) ?? today,
                                           matching: DateComponents(weekday: 1), matchingPolicy: .nextTime) ?? today
            if let fire = at(sunday, minutes: 19 * 60), fire > now {
                let review = WeeklyReview(s, weekOf: FoodView.monday(of: sunday))
                post("weekly", "Your week in review 📊", review.summary, at: fire, open: "weeklyReview")
            }
        }
    }
}
