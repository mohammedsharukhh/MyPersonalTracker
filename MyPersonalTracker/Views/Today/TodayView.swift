//
//  TodayView.swift
//  MyPersonalTracker
//

import SwiftData
import SwiftUI

struct TodayView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: \DailyLog.date, order: .reverse) private var logs: [DailyLog]
    @Query private var todos: [TodoItem]
    @State private var today = Calendar.current.startOfDay(for: .now)
    @State private var isTakingSelfie = false
    @State private var isViewingSelfie = false
    @State private var isShowingSettings = false

    private var yesterday: Date {
        Calendar.current.date(byAdding: .day, value: -1, to: today).map(Calendar.current.startOfDay) ?? today
    }
    private var todayLog: DailyLog? { logs.first { $0.date == today } }
    private var yesterdayLog: DailyLog? { logs.first { $0.date == yesterday } }

    var body: some View {
        NavigationStack {
            Group {
                if let todayLog {
                    DailyLogForm(log: todayLog) {
                        Section {
                            TodoStickyNote(day: today)
                                .padding(.vertical, 14)
                                .listRowBackground(Color.clear)
                                .listRowInsets(EdgeInsets(top: 0, leading: 4, bottom: 0, trailing: 4))
                        }

                        FocusTimerSection()
                    } footer: {
                        Section {
                            DayRecap(
                                day: yesterday,
                                log: yesterdayLog,
                                todos: todos.groupedByDay()[yesterday] ?? [],
                                emptyMessage: "Nothing logged yesterday. Today's a fresh start."
                            )
                            if let yesterdayLog {
                                NavigationLink("Edit yesterday") { PastDayView(log: yesterdayLog) }
                                    .font(.subheadline)
                            }
                        } header: {
                            HStack {
                                Text("Yesterday")
                                Spacer()
                                Text(yesterday.formatted(.dateTime.weekday(.abbreviated).day().month()))
                            }
                        } footer: {
                            Text("See every day in the History tab.")
                        }
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Today")
            .toolbarTitleDisplayMode(.inlineLarge)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Settings", systemImage: "gearshape") { isShowingSettings = true }
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        if todayLog?.selfieData != nil {
                            Button("View Today's Smile", systemImage: "photo") { isViewingSelfie = true }
                            Button("Retake", systemImage: "camera") { isTakingSelfie = true }
                        } else {
                            Button("Take Today's Smile", systemImage: "camera") { isTakingSelfie = true }
                        }
                    } label: {
                        if let data = todayLog?.selfieData {
                            SelfieAvatar(data: data, size: 34)
                        } else {
                            Image(systemName: "face.smiling")
                        }
                    }
                    .accessibilityLabel("Today's smile")
                }
            }
            .sheet(isPresented: $isShowingSettings) {
                SheetSettingsView(store: .shared)
            }
            .fullScreenCover(isPresented: $isTakingSelfie) {
                SmileCheckInView { data in
                    if let data { todayLog?.selfieData = data }
                    isTakingSelfie = false
                }
            }
            .fullScreenCover(isPresented: $isViewingSelfie) {
                if let data = todayLog?.selfieData {
                    PhotoViewer(data: data, title: "Today's smile")
                }
            }
        }
        .task(id: today) { ensureTodayLog() }
        .onChange(of: scenePhase) {
            // Roll over to a new log if the app was left open past midnight.
            if scenePhase == .active {
                today = Calendar.current.startOfDay(for: .now)
            }
        }
    }

    private func ensureTodayLog() {
        #if DEBUG
        SampleData.seedIfRequested(context)
        #endif
        guard todayLog == nil else { return }
        _ = DailyLog.forDay(today, in: context)
    }
}

#Preview {
    TodayView()
        .modelContainer(for: [DailyLog.self, TodoItem.self, StudySession.self, LearningSection.self], inMemory: true)
}
