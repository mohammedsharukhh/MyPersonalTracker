//
//  SettingsViews.swift
//  MyPersonalTracker
//
//  Settings is one screen: your sheet at the top, then "Targets" (pages that
//  save to the sheet, each with its own Save), then "This phone" (applies right
//  away), then Your data (export, reset) last. Opens from More and from Today's gear. Mirrors SettingsScreens.kt;
//  the pages and keys are in SettingsModels.swift.
//

import SwiftUI
import UserNotifications

struct SheetSettingsView: View {
    let store: SheetStore
    /// Opens on Your sheet with the web app fields showing (Welcome ▸ "I have a web app").
    var opensSheetPage = false
    @State private var sheetPageShown = false
    @State private var openedSheetPage = false
    @Environment(\.dismiss) private var dismiss
    @AppStorage(SplashSettings.soundKey) private var splashSound = true
    @State private var healthSteps = HealthSteps.shared
    @State private var auth = GoogleAuth.shared
    @State private var customizingHabits = false
    @State private var dataTools = false
    @State private var notifications = NotificationSummary.count(sheetConnected: false)

    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink { SheetConnectionPage(store: store) } label: { sheetRow }
                } footer: {
                    if plan == .hidden { Text("Connect your sheet to set your money, health and study targets.") }
                }

                if plan != .hidden {
                    Section {
                        if let settings = store.snapshot?.settings {
                            ForEach(SettingsPage.allCases) { page in
                                NavigationLink { PlanPage(store: store, page: page) } label: { row(page) }
                            }
                            Button { customizingHabits = true } label: {
                                SettingsRow(symbol: "slider.horizontal.3", tint: SheetTheme.navy, title: "Habits", subtitle: "Names, icons, which ones you track", opens: true)
                            }
                            NavigationLink { PeriodPage(store: store) } label: {
                                SettingsRow(symbol: "calendar", tint: SheetTheme.navy, title: "Period",
                                            subtitle: "\(SheetDate.short(settings.startDate)) – \(SheetDate.short(settings.endDate))")
                            }
                        } else if case .failed(let message) = plan {
                            Button { Task { await store.refresh() } } label: {
                                SettingsRow(symbol: "arrow.clockwise", tint: SheetTheme.bad, title: "Couldn't load your plan", subtitle: "\(message) · tap to try again")
                            }
                        } else {
                            SheetLoadingView(progress: store.loadProgress)
                                .padding(.vertical, 8)
                            .task { if !store.isLoading { await store.refresh() } }
                        }
                    } header: {
                        Text("Targets")
                    } footer: {
                        if plan == .ready { Text("Saved to your sheet when you tap Save on a page.") }
                    }
                }

                Section {
                    NavigationLink {
                        NotificationsPage(store: store) { notifications = NotificationSummary.count(sheetConnected: store.isConfigured) }
                    } label: {
                        SettingsRow(symbol: "bell.badge.fill", tint: SheetTheme.navy, title: "Notifications",
                                    subtitle: NotificationSummary.text(on: notifications.on, total: notifications.total))
                    }
                    if healthSteps.isAvailable {
                        Toggle(isOn: Binding(get: { healthSteps.isEnabled }, set: { on in
                            if on { Task { await healthSteps.connect(store: store) } } else { healthSteps.disconnect() }
                        })) {
                            SettingsRow(symbol: "heart.fill", tint: SheetTheme.navy, title: "Apple Health",
                                        subtitle: healthSteps.today.map { "\($0.formatted()) steps today" } ?? "Fills in steps, sleep, weight and workouts")
                        }
                    }
                    Toggle(isOn: $splashSound) {
                        SettingsRow(symbol: "speaker.wave.2.fill", tint: SheetTheme.navy, title: "Sound on opening", subtitle: "A short chime, quiet on silent")
                    }
                } header: {
                    Text("This phone")
                } footer: {
                    Text(healthSteps.isEnabled
                         ? "Only on this phone. Changes apply right away. To stop Apple Health reading, also turn it off in Settings ▸ Health ▸ Data Access."
                         : "Only on this phone. Changes apply right away.")
                }

                // Last and on its own: it holds Reset.
                Section("Your data") {
                    Button { dataTools = true } label: {
                        SettingsRow(symbol: "externaldrive.fill", tint: SheetTheme.navy, title: "Export or reset", subtitle: "Export everything, or reset and start over", opens: true)
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .sheet(isPresented: $customizingHabits) { HabitsEditor(store: store) }
            .sheet(isPresented: $dataTools) { DataToolsView(store: store) }
            .navigationDestination(isPresented: $sheetPageShown) { SheetConnectionPage(store: store, showWebApp: true) }
            .onAppear {
                notifications = NotificationSummary.count(sheetConnected: store.isConfigured)
                if opensSheetPage, !openedSheetPage { openedSheetPage = true; sheetPageShown = true }
            }
        }
        .tint(SheetTheme.navy)
    }

    private var plan: PlanState {
        .of(connected: store.isConfigured, loaded: store.snapshot?.settings != nil, loading: store.isLoading, error: store.errorMessage)
    }

    private var sheetRow: some View {
        HStack(spacing: 14) {
            IconBadge(symbol: "tablecells.fill", tint: store.isConfigured ? SheetTheme.good : SheetTheme.warn, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text("Your sheet").font(.headline).foregroundStyle(Color(.label))
                Text(SheetStatus.subtitle(connected: store.isConfigured, account: store.usesGoogleSheet ? auth.email : nil, synced: store.lastSynced))
                    .font(.caption)
                    .foregroundStyle(store.isConfigured ? Color(.secondaryLabel) : SheetTheme.warn)
            }
        }
        .padding(.vertical, 4)
    }

    private func row(_ page: SettingsPage) -> some View {
        let symbol = switch page {
        case .money: "banknote.fill"
        case .healthFood: "heart.text.square.fill"
        case .study: "book.fill"
        }
        return SettingsRow(symbol: symbol, tint: SheetTheme.navy, title: page.title, subtitle: page.subtitle)
    }
}

private struct SettingsRow: View {
    let symbol: String
    let tint: Color
    let title: String
    let subtitle: String
    /// Opens over Settings (a button), so it draws the chevron a pushed row gets from its NavigationLink.
    var opens = false

    var body: some View {
        HStack(spacing: 12) {
            IconBadge(symbol: symbol, tint: tint, size: 32)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.body).foregroundStyle(Color(.label))
                Text(subtitle).font(.caption).foregroundStyle(Color(.secondaryLabel))
            }
            if opens {
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Color(.tertiaryLabel))
                    .accessibilityHidden(true)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Sign in with Google, or (advanced) your own Apps Script web app.
private struct SheetConnectionPage: View {
    let store: SheetStore
    var showWebApp = false
    @State private var url = ""
    @State private var key = ""
    @State private var ownWebApp = false
    @State private var savedMessage: String?

    var body: some View {
        Form {
            GoogleSheetSection(store: store)
            if !store.usesGoogleSheet {
                Section {
                    Toggle(isOn: $ownWebApp.animation()) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Use my own Apps Script web app")
                            Text("Only if you set up the sheet yourself").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    if ownWebApp {
                        TextField("Web app URL", text: $url, axis: .vertical)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.footnote.monospaced())
                        SecureField("API key", text: $key)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Button {
                            store.useWebApp(url: url.trimmingCharacters(in: .whitespacesAndNewlines),
                                            key: key.trimmingCharacters(in: .whitespacesAndNewlines))
                            Task {
                                await store.refresh()
                                savedMessage = store.errorMessage == nil ? "Connected ✓" : nil
                            }
                        } label: {
                            HStack {
                                Text("Connect & sync")
                                if store.isLoading { Spacer(); ProgressView() }
                            }
                        }
                        .disabled(url.isEmpty || key.isEmpty)
                        if let savedMessage { Text(savedMessage).foregroundStyle(SheetTheme.good) }
                        if let error = store.errorMessage { Text(error).font(.caption).foregroundStyle(SheetTheme.bad) }
                    }
                } header: {
                    Text("Advanced")
                } footer: {
                    if ownWebApp {
                        Text("For a sheet you set up yourself: in the sheet, Extensions ▸ Apps Script ▸ run setupNewWorkbook once (or createApiKey for a new key) and copy the key it shows.")
                    }
                }
            }
        }
        .navigationTitle("Your sheet")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            url = store.webAppURL
            key = store.apiKey
            ownWebApp = showWebApp || !store.webAppURL.isEmpty
        }
    }
}

/// Money, Health & food or Study: targets saved to the sheet with this page's Save.
private struct PlanPage: View {
    let store: SheetStore
    let page: SettingsPage
    @Environment(\.dismiss) private var dismiss
    @State private var draft: SettingsDraft
    @State private var confirmDiscard = false
    @State private var saveError: String?
    @State private var bodyTargets = false
    /// Bumped when the fields are filled again (after a currency switch or weight targets), so they show it.
    @State private var generation = 0

    init(store: SheetStore, page: SettingsPage) {
        self.store = store
        self.page = page
        let draft = SettingsDraft(page: page)
        draft.seed(store.snapshot?.settings)
        _draft = State(initialValue: draft)
    }

    var body: some View {
        Form {
            if let settings = store.snapshot?.settings {
                switch page {
                case .money: money(settings)
                case .healthFood: healthFood(settings)
                case .study: Section("Study goals") { fields(SettingsPage.studyGoals) }.id(generation)
                }
            } else {
                NotConnectedHint()
            }
        }
        .navigationTitle(page.title)
        .navigationBarTitleDisplayMode(.inline)
        // With unsaved changes, leaving asks first.
        .navigationBarBackButtonHidden(draft.hasChanges)
        .interactiveDismissDisabled(draft.hasChanges)
        .toolbar {
            if draft.hasChanges {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { confirmDiscard = true } }
            }
            ToolbarItem(placement: .confirmationAction) {
                if store.isSaving {
                    ProgressView()
                } else {
                    Button("Save") { Task { await save() } }.fontWeight(.semibold).disabled(!draft.hasChanges)
                }
            }
        }
        .popup(isPresented: $confirmDiscard, Popup(title: "Discard your changes?", message: "What you changed on \(page.title) isn't saved to your sheet.",
                                                   symbol: "arrow.uturn.backward", style: .destructive, confirmTitle: "Discard", onConfirm: { dismiss() }))
        .popup(isPresented: Binding(get: { saveError != nil }, set: { if !$0 { saveError = nil } }), .error("Couldn't save", message: saveError))
        .sheet(isPresented: $bodyTargets, onDismiss: reseed) { BodyTargetsView(store: store) }
    }

    @ViewBuilder
    private func money(_ settings: SheetSettings) -> some View {
        CurrencySection(store: store, settings: settings) { reseed() }
        Section {
            fields(SettingsPage.spending(single: Money.single))
            Toggle(isOn: Binding(get: { limitFollowsSplit }, set: { draft.values["LIMIT_FROM_SPLIT"] = $0 ? 1 : 0 })) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Monthly limit from salary split")
                    Text("This month's income × (Essentials + Lifestyle)").font(.caption).foregroundStyle(.secondary)
                }
            }
            if !limitFollowsSplit { fields(Array(SettingsPage.monthlyLimit.dropFirst())) }
            LabeledContent("This month's limit", value: "\(SheetFormat.qar(settings.monthlyLimit)) \(Money.main)")
        } header: { Text("Spending") } footer: {
            Text(limitFollowsSplit ? "Until this month's salary is in, your own monthly limit is used." : "Your own monthly limit is used every month.")
        }
        .id(generation)
        Section {
            fields(SettingsPage.split)
            let total = SplitBucket.allCases.reduce(0.0) { $0 + ((draft.values[$1.key] ?? nil) ?? 0) }
            LabeledContent("Total", value: abs(total - 1) < 0.001 ? "✓ 100%" : "\(SheetFormat.percent(total, 0)) — should be 100%")
                .foregroundStyle(abs(total - 1) < 0.001 ? SheetTheme.good : SheetTheme.warn)
        } header: { Text("Salary split") } footer: {
            Text("Each month's income is shared out like this. Essentials and Lifestyle fill from your expenses, Family support from money sent home, and the emergency fund and Savings / SIP from what you put aside.")
        }
        .id(generation)
        if let lists = store.snapshot?.lists {
            Section {
                Text(lists.categories.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
            } header: { Text("Expense categories") } footer: { Text("To change these, edit the Lists tab in your sheet.") }
        }
    }

    @ViewBuilder
    private func healthFood(_ settings: SheetSettings) -> some View {
        Section("Health targets") { fields(SettingsPage.health) }.id(generation)
        Section {
            Button { bodyTargets = true } label: {
                Label(settings.followWeight == true ? "Targets follow your weight — change" : "Work out targets from my weight", systemImage: "scalemass")
            }
            fields(SettingsPage.nutrition)
        } header: { Text("Food & nutrition") } footer: {
            if settings.followWeight == true { Text("Typing a calorie or macro target here stops them following your weight.") }
        }
        .id(generation)
    }

    private func fields(_ list: [SettingsField]) -> some View {
        ForEach(list) { field in
            NumberRow(title: field.title, unit: field.unit, value: draft.binding(field.key, percent: field.percent), hint: nil)
        }
    }

    private var limitFollowsSplit: Bool { ((draft.values["LIMIT_FROM_SPLIT"] ?? nil) ?? 1) != 0 }

    private func reseed() {
        draft.seed(store.snapshot?.settings)
        generation += 1
    }

    private func save() async {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        if await store.saveSettings(draft.changes) {
            dismiss()
        } else {
            saveError = store.errorMessage ?? "The sheet didn't answer. Try again in a minute."
        }
    }
}

private struct PeriodPage: View {
    let store: SheetStore
    @State private var starting = false

    var body: some View {
        Form {
            if let settings = store.snapshot?.settings {
                Section {
                    LabeledContent("Starts", value: SheetDate.short(settings.startDate))
                    LabeledContent("Ends", value: SheetDate.short(settings.endDate))
                    Button("Start the next period…") { starting = true }
                } footer: {
                    Text("Starting a new period copies this one to “Archive” tabs in your sheet and starts fresh with the new dates.")
                }
            } else {
                NotConnectedHint()
            }
        }
        .navigationTitle("Period")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $starting) {
            if let settings = store.snapshot?.settings { NewPeriodSheet(store: store, settings: settings) }
        }
    }
}

/// Every reminder in one place. They apply right away.
private struct NotificationsPage: View {
    let store: SheetStore
    var onChange: () -> Void = {}
    @Environment(\.modelContext) private var context
    @Environment(\.openURL) private var openURL
    @AppStorage(ReminderSettings.enabledKey) private var eveningOn = false
    @AppStorage(ReminderSettings.eveningMinutesKey) private var eveningMinutes = ReminderSettings.defaultEveningMinutes
    @AppStorage(ReminderSettings.morningEnabledKey) private var morningOn = true
    @AppStorage(ReminderSettings.morningMinutesKey) private var morningMinutes = ReminderSettings.defaultMorningMinutes
    @AppStorage(ReminderSettings.briefingEnabledKey) private var briefingOn = true
    @AppStorage(ReminderSettings.billRemindersKey) private var billsOn = true
    @AppStorage(ReminderSettings.renewalRemindersKey) private var renewalsOn = true
    @AppStorage(ReminderSettings.parentRemindersKey) private var parentsOn = true
    @AppStorage(ReminderSettings.weeklyReviewKey) private var weeklyOn = true
    @AppStorage(BudgetAlerts.enabledKey) private var budgetOn = true
    @State private var denied = false

    private var connected: Bool { store.isConfigured }

    var body: some View {
        Form {
            if denied {
                Section {
                    Button("Notifications are off for this app, so none of these can come. Open Settings to allow them.") {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(SheetTheme.bad)
                }
            }
            Section("Every day") {
                if connected {
                    toggle("Morning briefing", "Today's plans, bills, money left, what to revise", $briefingOn)
                } else {
                    toggle("Morning reminder", "Take your smile and write today's to-dos", $morningOn)
                }
                DatePicker("Morning at", selection: timeBinding($morningMinutes), displayedComponents: .hourAndMinute)
                toggle("Evening: log my day", "Skipped once you've logged · warns you when your streak is at risk", $eveningOn)
                DatePicker("Evening at", selection: timeBinding($eveningMinutes), displayedComponents: .hourAndMinute)
            }
            if connected {
                Section("When something's due") {
                    toggle("Bills due tomorrow", nil, $billsOn)
                    toggle("Renewals", "QID, passport, insurance — before they expire", $renewalsOn)
                    toggle("What Mom & Dad asked for", "At the time each one is due", $parentsOn)
                    toggle("Budget alerts", "At 80% of a bucket and when it goes over", $budgetOn)
                }
                Section {
                    toggle("Week in review", "Sunday evening", $weeklyOn)
                } header: { Text("Every week") } footer: { Text("Changes apply right away.") }
            } else {
                Section {} footer: { Text("Connect your sheet for bill, renewal and budget reminders. Changes apply right away.") }
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: [morningMinutes, eveningMinutes]) { reschedule() }
        .task { denied = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied }
    }

    /// A switch that asks for permission when turned on, then reschedules.
    private func toggle(_ title: String, _ subtitle: String?, _ value: Binding<Bool>) -> some View {
        Toggle(isOn: Binding(get: { value.wrappedValue }, set: { on in
            value.wrappedValue = on
            onChange()
            Task {
                if on, !(await ReminderScheduler.requestAuthorization()) { denied = true }
                await ReminderScheduler.refresh(context: context)
            }
        })) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                if let subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
            }
        }
    }

    private func reschedule() { Task { await ReminderScheduler.refresh(context: context) } }

    /// Minutes-since-midnight ⇄ Date for time pickers.
    private func timeBinding(_ minutes: Binding<Int>) -> Binding<Date> {
        Binding {
            Calendar.current.date(byAdding: .minute, value: minutes.wrappedValue, to: Calendar.current.startOfDay(for: .now)) ?? .now
        } set: { date in
            let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
            minutes.wrappedValue = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        }
    }
}
