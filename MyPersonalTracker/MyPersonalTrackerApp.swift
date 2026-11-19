//
//  MyPersonalTrackerApp.swift
//  MyPersonalTracker
//
//  Created by GLB-BLR-M3 on 25/09/26.
//

import SwiftData
import SwiftUI
import UserNotifications

@main
struct MyPersonalTrackerApp: App {
    init() {
        UNUserNotificationCenter.current().delegate = NotificationPresenter.shared
        UpiPayments.registerCategory()
        ParentReminders.registerCategory()
        ReminderSettings.separateMorning()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .modelContainer(for: [DailyLog.self, TodoItem.self, StudySession.self, LearningSection.self, LearningTopic.self, Expense.self])
    }
}
