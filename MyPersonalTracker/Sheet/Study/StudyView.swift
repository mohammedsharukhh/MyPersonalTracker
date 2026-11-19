//
//  StudyView.swift
//  MyPersonalTracker
//
//  The Study tab. Overview: today against your target, streak, what you're on
//  now (with a timer), concepts due for revision, goals and subjects at a
//  glance. Then every subject, every goal, and the log of sessions.
//

import Charts
import SwiftData
import SwiftUI

/// Every editor the Study screens open, as one sheet item.
enum StudySheet: Identifiable {
    case subject(StudySubject, isNew: Bool)
    case subjectDetail(String)
    case topic(StudyTopic, isNew: Bool)
    case session(StudyEntry, isNew: Bool)
    case goal(StudyGoal, isNew: Bool)
    case review(StudyTopic)
    case reviewRun([String])
    case syllabus(String)
    case targets
    case startTimer
    case importJournal

    var id: String {
        switch self {
        case .subject(let s, _): "subject-\(s.id)"
        case .subjectDetail(let id): "detail-\(id)"
        case .topic(let t, _): "topic-\(t.id)"
        case .session(let e, _): "session-\(e.id)"
        case .goal(let g, _): "goal-\(g.id)"
        case .review(let t): "review-\(t.id)"
        case .reviewRun(let ids): "run-\(ids.joined(separator: ","))"
        case .syllabus(let id): "syllabus-\(id)"
        case .targets: "targets"
        case .startTimer: "timer"
        case .importJournal: "import"
        }
    }
}

struct StudyView: View {
    let store: SheetStore

    enum Section: String, CaseIterable { case overview = "Overview", subjects = "Subjects", goals = "Goals", log = "Log" }
    @State private var section: Section = .overview
    @State private var open: StudySheet?

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    if let error = store.errorMessage, store.isConfigured {
                        SheetErrorBanner(message: error) { Task { await store.refresh() } }
                    }
                    SyncStatusBar(store: store)
                    Picker("View", selection: $section) {
                        ForEach(Section.allCases, id: \.self) { Text($0.rawValue) }
                    }
                    .pickerStyle(.segmented)

                    if let snapshot = store.snapshot {
                        let data = snapshot.studyData
                        switch section {
                        case .overview: StudyOverview(data: data, settings: snapshot.settings, open: $open, section: $section)
                        case .subjects: StudySubjectsList(data: data, open: $open)
                        case .goals: StudyGoalsList(data: data, open: $open)
                        case .log: StudyLogList(data: data, open: $open)
                        }
                    } else if store.needsConnection {
                        NotConnectedHint()
                    } else {
                        SheetLoadingView(progress: store.loadProgress).padding(.top, 60)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .containerBackground(SheetTheme.pageWash, for: .navigation)
            .navigationTitle("Study")
            .refreshable { await store.refresh() }
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) { MoreButton(); SettingsButton() }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        let data = store.snapshot?.studyData ?? StudyData()
                        Button("Log a study session", systemImage: "clock.badge.checkmark") {
                            open = .session(StudyEntry(subjectId: data.currentTopic?.subjectId ?? data.activeSubjects.first?.id ?? ""), isNew: true)
                        }
                        .disabled(data.subjects.isEmpty)
                        Button("Add a concept", systemImage: "lightbulb") {
                            open = .topic(StudyTopic(subjectId: data.activeSubjects.first?.id ?? data.subjects.first?.id ?? ""), isNew: true)
                        }
                        .disabled(data.subjects.isEmpty)
                        Button("Add a subject", systemImage: "books.vertical") {
                            open = .subject(StudySubject(color: data.subjects.count), isNew: true)
                        }
                        Button("Add a goal", systemImage: "flag") { open = .goal(StudyGoal(), isNew: true) }
                    } label: {
                        Image(systemName: "plus.circle.fill").font(.title3)
                    }
                    .disabled(store.snapshot == nil)
                    .accessibilityLabel("Add")
                }
            }
            .sheet(item: $open) { StudySheetContent(store: store, sheet: $0) }
            #if DEBUG
            .task {
                // `-studySection subjects|goals|log` and `-sheetOpen session|subject|goal|review` for screenshots.
                try? await Task.sleep(for: .seconds(1))
                if let raw = UserDefaults.standard.string(forKey: "studySection"), let s = Section(rawValue: raw.capitalized) { section = s }
                let data = store.snapshot?.studyData ?? StudyData()
                switch UserDefaults.standard.string(forKey: "sheetOpen") {
                case "session": open = .session(StudyEntry(subjectId: data.currentTopic?.subjectId ?? "", topicId: data.currentTopic?.id), isNew: true)
                case "subject": if let s = data.subjects.first { open = .subjectDetail(s.id) }
                case "goal": open = .goal(StudyGoal(), isNew: true)
                case "review": if let t = data.dueTopics().first { open = .review(t) }
                case "revise-all": open = .reviewRun(data.dueTopics().map(\.id))
                case "syllabus": if let s = data.subjects.first { open = .syllabus(s.id) }
                default: break
                }
            }
            #endif
        }
    }
}

/// Routes a `StudySheet` to its screen.
struct StudySheetContent: View {
    let store: SheetStore
    let sheet: StudySheet

    var body: some View {
        switch sheet {
        case .subject(let s, let isNew): SubjectEditor(store: store, subject: s, isNew: isNew)
        case .subjectDetail(let id): SubjectDetail(store: store, subjectId: id)
        case .topic(let t, let isNew): ConceptEditor(store: store, topic: t, isNew: isNew)
        case .session(let e, let isNew): StudySessionEditor(store: store, entry: e, isNew: isNew)
        case .goal(let g, let isNew): StudyGoalEditor(store: store, goal: g, isNew: isNew)
        case .review(let t): QuickReviewSheet(store: store, topic: t)
        case .reviewRun(let ids): ReviewRunSheet(store: store, topicIds: ids)
        case .syllabus(let id): SyllabusSheet(store: store, subjectId: id)
        case .targets: StudyTargetsEditor(store: store)
        case .startTimer: StartStudySheet(store: store)
        case .importJournal: JournalImportSheet(store: store)
        }
    }
}

// MARK: - Overview

private struct StudyOverview: View {
    let data: StudyData
    let settings: SheetSettings
    @Binding var open: StudySheet?
    @Binding var section: StudyView.Section

    var body: some View {
        StudyHero(data: data, settings: settings, open: $open)

        if data.subjects.isEmpty {
            StudyEmptyCard(open: $open)
        } else {
            TodayPlanCard(data: data, settings: settings, open: $open)

            WeekChartCard(data: data)

            let goals = data.goals.filter { !$0.done }.sorted { ($0.due ?? "9999") < ($1.due ?? "9999") }
            if !goals.isEmpty {
                SheetCard {
                    VStack(alignment: .leading, spacing: 12) {
                        CardHeader(title: "Goals", action: "See all") { section = .goals }
                        ForEach(goals.prefix(3)) { goal in
                            GoalRow(goal: goal, data: data) { open = .goal(goal, isNew: false) }
                        }
                    }
                }
            }

            SheetCard {
                VStack(alignment: .leading, spacing: 12) {
                    CardHeader(title: "What you're studying", action: "All subjects") { section = .subjects }
                    ForEach(data.activeSubjects) { subject in
                        SubjectRow(subject: subject, data: data) { open = .subjectDetail(subject.id) }
                    }
                    if data.activeSubjects.isEmpty {
                        Text("No active subjects. Resume one from Subjects, or add a new one.").font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }

            let recent = data.sessions.sorted { $0.date > $1.date }.prefix(4)
            if !recent.isEmpty {
                SheetCard {
                    VStack(alignment: .leading, spacing: 10) {
                        CardHeader(title: "Recent sessions", action: "Full log") { section = .log }
                        ForEach(Array(recent)) { entry in
                            SessionRow(entry: entry, data: data, showDate: true) { open = .session(entry, isNew: false) }
                        }
                    }
                }
            }
        }
    }
}

/// Today's minutes against the target, the streak, the week, and what you're on now.
private struct StudyHero: View {
    let data: StudyData
    let settings: SheetSettings
    @Binding var open: StudySheet?
    @State private var timer = StudyTimer.shared

    var body: some View {
        let today = data.minutes(on: SheetDate.today)
        let target = max(settings.studyDailyTarget ?? 60, 1)
        let week = data.weekMinutes()
        let weekTarget = (settings.studyWeeklyTarget ?? 7) * 60
        let current = data.currentTopic

        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Studied today").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                    Text(StudyFormat.minutes(today)).font(.system(.largeTitle, design: .rounded, weight: .bold)).foregroundStyle(.white)
                    Text("Target \(StudyFormat.minutes(target)) a day").font(.caption).foregroundStyle(.white.opacity(0.75))
                }
                Spacer()
                Button { open = .targets } label: {
                    ZStack {
                        ScoreRing(fraction: today / target, lineWidth: 9)
                        VStack(spacing: 0) {
                            Text(SheetFormat.percent(min(today / target, 9.99), 0)).font(.system(.headline, design: .rounded, weight: .bold))
                            Text("of goal").font(.caption2).opacity(0.75)
                        }
                        .foregroundStyle(.white)
                    }
                    .frame(width: 84, height: 84)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Study targets")
            }
            HStack(spacing: 8) {
                HeroChip(symbol: "flame.fill", text: "\(data.streak()) day streak")
                HeroChip(symbol: "calendar", text: "\(StudyFormat.minutes(week)) / \(StudyFormat.minutes(weekTarget)) this week")
            }

            if timer.isRunning, let started = timer.startedAt {
                let subject = data.subject(timer.subjectId)
                HStack(spacing: 12) {
                    Image(systemName: "timer").font(.title2).symbolEffect(.pulse, options: .repeating)
                    VStack(alignment: .leading, spacing: 2) {
                        Text([subject?.name, data.topic(timer.topicId)?.topic].compactMap { $0 }.joined(separator: " · "))
                            .font(.subheadline.weight(.semibold)).lineLimit(1)
                        Text(started, style: .timer).font(.title2.bold()).monospacedDigit()
                    }
                    Spacer()
                    Button { timer.cancel() } label: { Image(systemName: "xmark.circle.fill").font(.title2).opacity(0.7) }
                        .accessibilityLabel("Cancel timer")
                    Button("Stop") {
                        if let entry = timer.stop(data: data) { open = .session(entry, isNew: true) }
                    }
                    .buttonStyle(.borderedProminent).tint(.white).foregroundStyle(SheetTheme.navy).fontWeight(.bold)
                }
                .foregroundStyle(.white)
                .padding(12)
                .background(.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else if !data.subjects.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    if let current, let subject = data.subject(current.subjectId) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Now studying").font(.caption.weight(.semibold)).foregroundStyle(.white.opacity(0.75))
                            Text(current.topic).font(.headline).foregroundStyle(.white).lineLimit(2)
                            Text(subject.name).font(.caption).foregroundStyle(.white.opacity(0.75))
                        }
                    }
                    HStack(spacing: 10) {
                        Button { open = .startTimer } label: {
                            Label("Start studying", systemImage: "play.fill").font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .background(.white, in: Capsule()).foregroundStyle(SheetTheme.navy)
                        }
                        Button {
                            open = .session(StudyEntry(subjectId: current?.subjectId ?? data.activeSubjects.first?.id ?? data.subjects[0].id,
                                                       topicId: current?.id), isNew: true)
                        } label: {
                            Label("Log time", systemImage: "plus").font(.subheadline.weight(.bold))
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .background(.white.opacity(0.18), in: Capsule()).foregroundStyle(.white)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(20)
        .background(SheetTheme.heroGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(.top, 4)
    }
}

private struct StudyEmptyCard: View {
    @Binding var open: StudySheet?
    /// The app's earlier, on-phone Learning journal — offered for import.
    @Query private var journal: [LearningSection]

    var body: some View {
        SheetCard {
            VStack(alignment: .leading, spacing: 12) {
                Label("What are you studying?", systemImage: "books.vertical.fill").font(.headline)
                Text("Add each subject or course you're learning. Inside it, list the concepts — then log your study time, mark how well you know each one, and the app tells you when to revise it.")
                    .font(.subheadline).foregroundStyle(.secondary)
                Button { open = .subject(StudySubject(), isNew: true) } label: {
                    Label("Add your first subject", systemImage: "plus.circle.fill").font(.headline)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(SheetTheme.navy, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
                if !journal.isEmpty {
                    Button { open = .importJournal } label: {
                        Label("Bring in your Learning journal (\(journal.count) sections, \(journal.reduce(0) { $0 + $1.topics.count }) topics)", systemImage: "square.and.arrow.down")
                            .font(.subheadline.weight(.semibold))
                    }
                }
            }
        }
    }
}

/// Minutes per day for the last 7 days, coloured by subject.
private struct WeekChartCard: View {
    let data: StudyData

    var body: some View {
        let cal = Calendar.current
        let days = (0..<7).reversed().compactMap { cal.date(byAdding: .day, value: -$0, to: cal.startOfDay(for: .now)) }
        let first = SheetDate.string(days.first ?? .now)
        let sessions = data.sessions.filter { $0.date >= first }
        let total = sessions.reduce(0) { $0 + $1.minutes }
        SheetCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Last 7 days").font(.headline)
                        Text("\(StudyFormat.minutes(total)) · avg \(StudyFormat.minutes(total / 7)) a day").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                }
                if total == 0 {
                    Text("Log a session to see your week.").font(.footnote).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, minHeight: 120)
                } else {
                    Chart {
                        ForEach(days, id: \.self) { day in
                            let key = SheetDate.string(day)
                            ForEach(sessions.filter { $0.date == key }) { entry in
                                BarMark(x: .value("Day", day, unit: .day), y: .value("Minutes", entry.minutes))
                                    .foregroundStyle(data.subject(entry.subjectId)?.tint ?? .gray)
                            }
                        }
                    }
                    .chartXAxis { AxisMarks(values: .stride(by: .day)) { _ in AxisValueLabel(format: .dateTime.weekday(.narrow)) } }
                    .frame(height: 150)
                    let legend = data.subjects.filter { s in sessions.contains { $0.subjectId == s.id } }
                    HStack(spacing: 12) {
                        ForEach(legend) { s in
                            HStack(spacing: 4) {
                                Circle().fill(s.tint).frame(width: 8, height: 8)
                                Text(s.name).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct CardHeader: View {
    let title: String
    let action: String
    let onTap: () -> Void

    var body: some View {
        HStack {
            Text(title).font(.headline)
            Spacer()
            Button(action, action: onTap).font(.subheadline.weight(.semibold))
        }
    }
}

// MARK: - Rows

struct SubjectRow: View {
    let subject: StudySubject
    let data: StudyData
    let onTap: () -> Void

    var body: some View {
        let topics = data.topics.filter { $0.subjectId == subject.id }
        let week = data.weekMinutes(subjectId: subject.id)
        let days = StudyData.daysUntil(subject.targetDate)
        Button(action: onTap) {
            HStack(spacing: 12) {
                ZStack {
                    ScoreRing(fraction: data.progress(of: subject.id), lineWidth: 5, tint: subject.tint, track: subject.tint.opacity(0.15))
                    Text(String(subject.name.prefix(1)).uppercased()).font(.headline).foregroundStyle(subject.tint)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 3) {
                    Text(subject.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label)).lineLimit(1)
                    Text(topics.isEmpty ? "No concepts yet" : "\(topics.filter { $0.stage == .mastered }.count) of \(topics.count) concepts mastered")
                        .font(.caption).foregroundStyle(Color(.secondaryLabel))
                    HStack(spacing: 8) {
                        if let hours = subject.weeklyHours, hours > 0 {
                            Text("\(StudyFormat.minutes(week)) / \(SheetFormat.number(hours))h this week")
                                .foregroundStyle(week >= hours * 60 ? SheetTheme.good : Color(.secondaryLabel))
                        } else if week > 0 {
                            Text("\(StudyFormat.minutes(week)) this week")
                        }
                        if let days, subject.status == .active {
                            Text(days < 0 ? "Target passed" : "Target \(StudyFormat.countdown(days))")
                                .foregroundStyle(days < 0 ? SheetTheme.bad : (days <= 14 ? SheetTheme.warn : Color(.secondaryLabel)))
                        }
                    }
                    .font(.caption2.weight(.semibold)).foregroundStyle(Color(.secondaryLabel))
                }
                Spacer()
                Text(SheetFormat.percent(data.progress(of: subject.id), 0)).font(.subheadline.weight(.bold).monospacedDigit()).foregroundStyle(subject.tint)
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct GoalRow: View {
    let goal: StudyGoal
    let data: StudyData
    let onTap: () -> Void

    var body: some View {
        let progress = data.progress(of: goal)
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline) {
                    Image(systemName: goal.kind.symbol).foregroundStyle(progress.state.tint)
                    Text(goal.title).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label)).lineLimit(2)
                    Spacer()
                    StatusPill(text: progress.state.label, tint: progress.state.tint)
                }
                if goal.kind != .milestone {
                    ProgressView(value: progress.fraction).tint(progress.state.tint)
                }
                Text([progress.detail, data.subject(goal.subjectId)?.name,
                      goal.due.flatMap { due in StudyData.daysUntil(due).map { "due \(StudyFormat.countdown($0))" } }]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(Color(.secondaryLabel))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct SessionRow: View {
    let entry: StudyEntry
    let data: StudyData
    var showDate = false
    let onTap: () -> Void

    var body: some View {
        let subject = data.subject(entry.subjectId)
        Button(action: onTap) {
            HStack(spacing: 12) {
                IconBadge(symbol: entry.kind.symbol, tint: subject?.tint ?? .gray, size: 34)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(entry.topic ?? subject?.name ?? entry.subject ?? "Study").font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color(.label)).lineLimit(1)
                        if entry.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                    }
                    Text([showDate ? SheetDate.weekdayDay(entry.date) : nil, entry.topic == nil ? nil : (subject?.name ?? entry.subject),
                          entry.kind.rawValue, entry.score.map { "\(SheetFormat.number($0, digits: 0))%" }, entry.notes]
                        .compactMap { $0 }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(Color(.secondaryLabel)).lineLimit(1)
                }
                Spacer()
                Text(StudyFormat.minutes(entry.minutes)).font(.subheadline.weight(.bold).monospacedDigit()).foregroundStyle(Color(.label))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Subjects, goals, log

private struct StudySubjectsList: View {
    let data: StudyData
    @Binding var open: StudySheet?

    var body: some View {
        AddButton(title: "Add a subject") { open = .subject(StudySubject(color: data.subjects.count), isNew: true) }
        if data.subjects.isEmpty { StudyEmptyCard(open: $open) }
        ForEach(StudySubject.Status.allCases) { status in
            let list = data.subjects.filter { $0.status == status }
            if !list.isEmpty {
                SectionTitle(title: status == .active ? "Studying now" : status.rawValue)
                SheetCard {
                    VStack(spacing: 14) {
                        ForEach(list) { s in SubjectRow(subject: s, data: data) { open = .subjectDetail(s.id) } }
                    }
                }
            }
        }
    }
}

private struct StudyGoalsList: View {
    let data: StudyData
    @Binding var open: StudySheet?

    var body: some View {
        AddButton(title: "Add a goal") { open = .goal(StudyGoal(), isNew: true) }
        let active = data.goals.filter { !$0.done }.sorted { ($0.due ?? "9999") < ($1.due ?? "9999") }
        let done = data.goals.filter(\.done).sorted { ($0.doneOn ?? "") > ($1.doneOn ?? "") }
        if data.goals.isEmpty {
            SheetCard {
                Text("Set what you're aiming for: hours to put in (40 hours of DSA by December), concepts to master (15 SwiftUI topics), or a milestone like passing an exam. Progress fills in from your sessions and concepts.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        if !active.isEmpty {
            SectionTitle(title: "In progress")
            SheetCard {
                VStack(spacing: 16) { ForEach(active) { g in GoalRow(goal: g, data: data) { open = .goal(g, isNew: false) } } }
            }
        }
        if !done.isEmpty {
            SectionTitle(title: "Achieved")
            SheetCard {
                VStack(spacing: 16) { ForEach(done) { g in GoalRow(goal: g, data: data) { open = .goal(g, isNew: false) } } }
            }
        }
    }
}

private struct StudyLogList: View {
    let data: StudyData
    @Binding var open: StudySheet?

    var body: some View {
        AddButton(title: "Log a study session") {
            open = .session(StudyEntry(subjectId: data.currentTopic?.subjectId ?? data.activeSubjects.first?.id ?? data.subjects.first?.id ?? ""), isNew: true)
        }
        .disabled(data.subjects.isEmpty)
        let byDay = Dictionary(grouping: data.sessions, by: \.date)
        let days = byDay.keys.sorted(by: >)
        if days.isEmpty {
            SheetCard {
                Text(data.subjects.isEmpty ? "Add a subject first, then log the time you study." : "No sessions yet. Use Start studying for a timer, or log time you've already done.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        ForEach(days.prefix(60), id: \.self) { day in
            let list = byDay[day] ?? []
            SheetCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(SheetDate.weekdayDay(day)).font(.subheadline.weight(.bold))
                        Spacer()
                        Text(StudyFormat.minutes(list.reduce(0) { $0 + $1.minutes })).font(.subheadline.weight(.bold)).foregroundStyle(SheetTheme.navy)
                    }
                    ForEach(list) { e in SessionRow(entry: e, data: data) { open = .session(e, isNew: false) } }
                }
            }
        }
    }
}

struct AddButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: "plus.circle.fill")
                .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 13)
                .background(SheetTheme.navy, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .foregroundStyle(.white)
        }
        .buttonStyle(.plain)
    }
}

/// Small Dashboard card: today's study against the target, streak, what's due.
struct StudyTodayCard: View {
    let snapshot: SheetSnapshot

    var body: some View {
        let data = snapshot.studyData
        let today = data.minutes(on: SheetDate.today)
        let target = max(snapshot.settings.studyDailyTarget ?? 60, 1)
        let due = data.dueTopics().count
        Button { AppNavigation.shared.tab = AppTab.study } label: {
            HStack(spacing: 14) {
                IconBadge(symbol: "graduationcap.fill", tint: SheetTheme.indigo, size: 40)
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Study today").font(.subheadline.weight(.semibold))
                        Spacer()
                        Text("\(data.streak()) day streak").font(.caption.weight(.semibold)).foregroundStyle(SheetTheme.warn)
                    }
                    ProgressRow(title: data.currentTopic?.topic ?? (data.subjects.isEmpty ? "Add what you're studying" : "Studied"),
                                detail: "\(StudyFormat.minutes(today)) / \(StudyFormat.minutes(target))",
                                fraction: today / target, tint: SheetTheme.indigo)
                    if due > 0 {
                        Text("\(due) concept\(due == 1 ? "" : "s") due for revision").font(.caption.weight(.semibold)).foregroundStyle(SheetTheme.warn)
                    }
                }
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(14)
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
