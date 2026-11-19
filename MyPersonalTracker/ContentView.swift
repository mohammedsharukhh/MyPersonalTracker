//
//  ContentView.swift
//  MyPersonalTracker
//
//  Created by GLB-BLR-M3 on 25/09/26.
//

import SwiftData
import SwiftUI

struct ContentView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    /// The opening animation, once per launch (not when coming back from the background).
    @State private var showingSplash = !ProcessInfo.processInfo.arguments.contains("-noSplash")
    /// The tabs are built behind the splash once it's playing, so it starts at once.
    @State private var appReady = ProcessInfo.processInfo.arguments.contains("-noSplash")
    /// The Google Sheet ("Life & Finance Command Center") behind every tab.
    @State private var sheet = SheetStore.shared
    @State private var settingsOpensSheet = false
    /// Selected tab; `-sheetTab N` on launch opens tab N (used for screenshots).
    /// Selected tab (and Siri's "Open Add Expense"); `-sheetTab N` opens tab N.
    @State private var nav = AppNavigation.shared
    /// "Not now" on the welcome screen: don't ask again at launch (Settings and the Connect card still offer it).
    @AppStorage("welcome.dismissed") private var welcomeDismissed = false

    var body: some View {
        ZStack {
            if appReady { app }
            if showingSplash {
                SplashView(appReady: appReady, onReadyForApp: { appReady = true }) { showingSplash = false }
                    .zIndex(1)
            }
        }
    }

    private var app: some View {
        tabs
            .overlay(alignment: .bottom) {
                UndoToast(store: sheet).padding(.bottom, 70)
                    .animation(.snappy, value: sheet.undoMessage)
            }
            .sheet(isPresented: $nav.showSettings, onDismiss: { settingsOpensSheet = false }) {
                SheetSettingsView(store: sheet, opensSheetPage: settingsOpensSheet)
            }
            // First launch with no sheet: sign in with Google (or not now).
            .fullScreenCover(isPresented: Binding(
                get: { !showingSplash && !welcomeDismissed && !sheet.isDemo && !sheet.isConfigured },
                set: { if !$0 { welcomeDismissed = true } })) {
                WelcomeView(store: sheet, onNotNow: { welcomeDismissed = true }, onWebApp: {
                    welcomeDismissed = true
                    settingsOpensSheet = true
                    Task { try? await Task.sleep(for: .milliseconds(400)); nav.showSettings = true }
                })
            }
            .sheet(isPresented: $nav.showMore) {
                SheetMoreView(store: sheet)
            }
            .sheet(isPresented: $nav.showAddExpense) {
                SheetExpenseEditor(store: sheet, expense: nil)
            }
            .sheet(isPresented: $nav.showRenewals) {
                NavigationStack {
                    RenewalsScreen(store: sheet)
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Done")
                            { nav.showRenewals = false } } }
                }
                .tint(SheetTheme.navy)
            }
            .sheet(isPresented: $nav.showCards) {
                NavigationStack {
                    CardsScreen()
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Done")
                            { nav.showCards = false } } }
                }
                .tint(SheetTheme.navy)
            }
            .sheet(isPresented: $nav.showWeeklyReview) { WeeklyReviewView(store: sheet) }
            .sheet(isPresented: $nav.showYearReview) { YearReviewView(store: sheet) }
            .sheet(isPresented: $nav.showHabits) { HabitsEditor(store: sheet) }
            .sheet(isPresented: $nav.showPay, onDismiss: { nav.payWithScan = false; nav.payTo = nil }) {
                PayView(store: sheet, startWithScan: nav.payWithScan, payTo: nav.payTo)
            }
            .modifier(PaymentQuestion())
            .onOpenURL { url in if url.scheme == "mypersonaltracker" { nav.open(url.host) } }
            .sheet(isPresented: $nav.showDayLog) {
                if let today = sheet.snapshot?.days.first(where: { $0.date == SheetDate.today }) { DayLogSheet(store: sheet, day: today) }
            }
            .task {
                #if DEBUG
                // `-openSheet weeklyReview|renewals|cards|parents|yearReview` (screenshots).
                if ProcessInfo.processInfo.arguments.contains("-popupDemo") {
                    try? await Task.sleep(for: .seconds(1))
                    PopupCenter.shared.show(.delete("Delete this expense?", message: "It's removed from the sheet. You can undo for a few seconds.") {}) {}
                }
                if let target = UserDefaults.standard.string(forKey: "openSheet") {
                    if target == "yearReview" { nav.showYearReview = true }
                    else if target == "settings" { nav.showSettings = true }
                    else { nav.open(target) }
                }
                #endif
                if SheetStore.isUnitTesting { return }
                syncExternalState()
                await sheet.refresh()
                await HealthSteps.shared.sync(store: sheet)
                await ReminderScheduler.scheduleSheetAlerts()
            }
            // Today logged (or not) changes tonight's reminder and the widgets.
            .onChange(of: sheet.isTodayLogged) { syncExternalState() }
            .onChange(of: scenePhase) {
                switch scenePhase {
                case .active:
                    // Back from a UPI app: did the payment go through?
                    UpiPayments.shared.askIfWaiting()
                    syncExternalState()
                    FocusTimer.shared.syncLiveActivity()
                    // Pick up edits made in the sheet itself since last time, then today's steps.
                    if !SheetStore.isUnitTesting {
                        Task { await sheet.refresh(); await HealthSteps.shared.sync(store: sheet); await ReminderScheduler.scheduleSheetAlerts() }
                    }
                case .background:
                    // Pick up whatever was logged this session (e.g. skip tonight's reminder).
                    syncExternalState()
                default:
                    break
                }
            }
    }

    /// Keeps things outside the app (notifications, widgets) in step with the data.
    private func syncExternalState() {
        WidgetSync.update(context: context)
        Task { await ReminderScheduler.refresh(context: context) }
    }

    /// The areas of the workbook (and what Mom & Dad asked for), as pages you
    /// can swipe between, with Pay with UPI raised in the middle of the tab bar. More (summaries, tools, settings, the
    /// journal) opens from the ⋯ button on each tab.
    private var tabs: some View {
        SwipeTabs(selection: $nav.tab, items: [
            SwipeTabItem("Dashboard", systemImage: "square.grid.2x2.fill", tag: AppTab.dashboard, world: .dashboard) {
                CommandDashboardView(store: sheet) { nav.showSettings = true }
            },
            SwipeTabItem("Daily", systemImage: "checklist", tag: AppTab.daily, world: .daily) { DailyTrackerView(store: sheet) },
            SwipeTabItem("Parents", systemImage: "figure.2.and.child.holdinghands", tag: AppTab.parents, world: .parents) { ParentsView(store: sheet) },
            SwipeTabItem("Study", systemImage: "graduationcap.fill", tag: AppTab.study, world: .study) { StudyView(store: sheet) },
            SwipeTabItem("Food", systemImage: "fork.knife", tag: AppTab.food, world: .food) { FoodView(store: sheet) },
            SwipeTabItem("Finance", systemImage: "banknote.fill", tag: AppTab.finance, world: .finance) { FinanceTrackerView(store: sheet) },
        ]) {
            UpiTabButton()
        }
        .tint(SheetTheme.navy)
    }
}

#Preview {
    ContentView()
        .modelContainer(for: [DailyLog.self, TodoItem.self, StudySession.self, LearningSection.self, LearningTopic.self, Expense.self], inMemory: true)
}
