//
//  BudgetAlerts.swift
//  MyPersonalTracker
//
//  A notification when a spending bucket of the salary split (Essentials,
//  Family support, Lifestyle) reaches 80% of this month's share, and again
//  when it goes over — once per bucket per level per month. Checked every
//  time the sheet data changes, so it covers expenses added here, bills the
//  sheet posts by itself, and changes made on other devices.
//

import Foundation
import UserNotifications

enum BudgetAlerts {
    static let enabledKey = "budgetAlertsEnabled"
    private static let sentKey = "budgetAlertsSent"

    struct Alert: Equatable {
        enum Level: Int { case nearly = 80, over = 100 }
        let bucket: SplitBucket
        let level: Level
        let used: Double
        let planned: Double
        let month: String
        var key: String { "\(month)|\(bucket.rawValue)|\(level.rawValue)" }

        var title: String {
            switch level {
            case .nearly: "\(bucket.rawValue): \(SheetFormat.percent(used / planned, 0)) used"
            case .over: "\(bucket.rawValue) is over budget"
            }
        }

        var body: String {
            switch level {
            case .nearly: "\(SheetFormat.qar(planned - used)) \(Money.main) left of \(SheetFormat.qar(planned)) this month."
            case .over: "\(SheetFormat.qar(used - planned)) \(Money.main) over this month's \(SheetFormat.qar(planned)) — it comes out of what's left over."
            }
        }
    }

    /// The alerts `month` has earned that haven't been sent. Only the highest
    /// level per bucket: going straight past 100% doesn't also send the 80% one.
    static func due(_ s: SheetSnapshot, month: String, sent: Set<String>) -> [Alert] {
        SalarySplit.lines(s, month: month).compactMap { line in
            guard !line.bucket.isSaving, line.planned > 0 else { return nil }
            let level: Alert.Level? = line.used > line.planned + 0.005 ? .over : line.fraction >= 0.8 ? .nearly : nil
            guard let level else { return nil }
            let alert = Alert(bucket: line.bucket, level: level, used: line.used, planned: line.planned, month: month)
            return sent.contains(alert.key) ? nil : alert
        }
    }

    static var isEnabled: Bool { UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true }

    /// Sends this month's new alerts. Call with the latest snapshot.
    @MainActor
    static func check(_ s: SheetSnapshot, now: Date = .now) {
        guard isEnabled else { return }
        let month = String(SheetDate.string(now).prefix(7))
        let defaults = UserDefaults.standard
        // Only this month's keys are kept.
        var sent = Set((defaults.stringArray(forKey: sentKey) ?? []).filter { $0.hasPrefix(month) })
        let alerts = due(s, month: month, sent: sent)
        guard !alerts.isEmpty else { return }
        for alert in alerts {
            sent.insert(alert.key)
            // Over implies nearly: don't send the 80% one later if spending is undone and redone.
            if alert.level == .over { sent.insert(Alert(bucket: alert.bucket, level: .nearly, used: 0, planned: 0, month: month).key) }
        }
        defaults.set(Array(sent), forKey: sentKey)
        Task {
            let center = UNUserNotificationCenter.current()
            // Asked the first time there's something to say, if never asked before.
            var status = await center.notificationSettings().authorizationStatus
            if status == .notDetermined, await ReminderScheduler.requestAuthorization() { status = .authorized }
            guard status == .authorized || status == .provisional else { return }
            for alert in alerts {
                let content = UNMutableNotificationContent()
                content.title = alert.title
                content.body = alert.body
                content.sound = .default
                try? await center.add(UNNotificationRequest(identifier: "budget.\(alert.key)", content: content, trigger: nil))
            }
        }
    }
}

/// Shows the app's notifications as banners while it's open too (a budget alert
/// usually fires right after you add an expense).
final class NotificationPresenter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationPresenter()

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    /// A tapped notification opens what it's about (the week in review, renewals…).
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        // "Did your payment go through?" — Paid / It failed, or a tap to answer in the app.
        if let payment = response.notification.request.content.userInfo["upiPayment"] as? String {
            await MainActor.run {
                switch response.actionIdentifier {
                case UpiPayments.paidAction: UpiPayments.shared.markPaid(payment)
                case UpiPayments.failedAction: UpiPayments.shared.markFailed(payment)
                default: UpiPayments.shared.askIfWaiting()
                }
            }
            return
        }
        // "Mom asked: …" — Done marks it done (a repeating one moves to its next date); Tomorrow gives a day.
        if let id = response.notification.request.content.userInfo["parentAsk"] as? String {
            switch response.actionIdentifier {
            case ParentReminders.doneAction: await ParentReminders.markDone(id: id); return
            case ParentReminders.tomorrowAction: await ParentReminders.snooze(id: id); return
            default: break
            }
        }
        let target = response.notification.request.content.userInfo["open"] as? String
        await MainActor.run { AppNavigation.shared.open(target) }
    }
}
