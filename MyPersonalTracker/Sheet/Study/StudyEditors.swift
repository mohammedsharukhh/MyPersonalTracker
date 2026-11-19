//
//  StudyEditors.swift
//  MyPersonalTracker
//
//  One subject in full, and the forms: subject, concept, session, goal, a
//  quick revision check, study targets, starting the timer, and bringing in
//  the old on-phone Learning journal.
//

import SwiftData
import SwiftUI

// MARK: - Subject detail

struct SubjectDetail: View {
    let store: SheetStore
    let subjectId: String
    @Environment(\.dismiss) private var dismiss
    @State private var open: StudySheet?
    @State private var showMastered = false

    var body: some View {
        let data = store.snapshot?.studyData ?? StudyData()
        NavigationStack {
            if let subject = data.subject(subjectId) {
                content(subject, data)
                    .navigationTitle(subject.name)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("Edit") { open = .subject(subject, isNew: false) } }
                        ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                    }
            } else {
                ContentUnavailableView("Subject deleted", systemImage: "books.vertical").toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                }
            }
        }
        .tint(SheetTheme.navy)
        .sheet(item: $open) { StudySheetContent(store: store, sheet: $0) }
    }

    @ViewBuilder
    private func content(_ subject: StudySubject, _ data: StudyData) -> some View {
        let topics = data.topics(of: subject.id)
        let sessions = data.sessions(of: subject.id)
        let total = sessions.reduce(0) { $0 + $1.minutes }
        let order: [StudyTopic.Stage] = [.learning, .revising, .toLearn, .mastered]
        List {
            Section {
                HStack(spacing: 16) {
                    ZStack {
                        ScoreRing(fraction: data.progress(of: subject.id), lineWidth: 8, tint: subject.tint, track: subject.tint.opacity(0.15))
                        Text(SheetFormat.percent(data.progress(of: subject.id), 0)).font(.headline.monospacedDigit())
                    }
                    .frame(width: 70, height: 70)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(subject.status == .active ? "Studying" : subject.status.rawValue).font(.caption.weight(.bold)).foregroundStyle(subject.tint)
                        Text("\(topics.filter { $0.stage == .mastered }.count) of \(topics.count) concepts mastered").font(.subheadline.weight(.semibold))
                        Text("\(StudyFormat.minutes(total)) in total · \(StudyFormat.minutes(data.weekMinutes(subjectId: subject.id))) this week")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
                if let why = subject.why { LabeledContent("Why", value: why) }
                if let resource = subject.resource {
                    if let url = URL(string: resource), url.scheme?.hasPrefix("http") == true {
                        Link(destination: url) { Label(resource, systemImage: "link").lineLimit(1) }
                    } else {
                        LabeledContent("Using", value: resource)
                    }
                }
                if let hours = subject.weeklyHours, hours > 0 {
                    let week = data.weekMinutes(subjectId: subject.id)
                    ProgressRow(title: "This week", detail: "\(StudyFormat.minutes(week)) of \(SheetFormat.number(hours))h",
                                fraction: week / (hours * 60), tint: subject.tint)
                }
                if let target = subject.targetDate, let days = StudyData.daysUntil(target) {
                    LabeledContent("Target date", value: "\(SheetDate.short(target)) · \(StudyFormat.countdown(days))")
                }
                if let pace = data.pace(of: subject) { PaceRow(pace: pace) }
                HStack(spacing: 10) {
                    Button { StudyTimer.shared.start(subject: subject, topic: topics.first { $0.stage == .learning }); dismiss() } label: {
                        Label("Study now", systemImage: "play.fill").lineLimit(1).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).disabled(StudyTimer.shared.isRunning)
                    Button { open = .session(StudyEntry(subjectId: subject.id, subject: subject.name), isNew: true) } label: {
                        Label("Log time", systemImage: "plus").lineLimit(1).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .listRowSeparator(.hidden)
            }

            Section {
                Button { open = .topic(StudyTopic(subjectId: subject.id), isNew: true) } label: {
                    Label("Add a concept", systemImage: "plus.circle.fill")
                }
                Button { open = .syllabus(subject.id) } label: {
                    Label("Paste a syllabus (many at once)", systemImage: "list.bullet.clipboard")
                }
                if topics.isEmpty {
                    Text("List the concepts or chapters in \(subject.name) — e.g. “Closures”, “Binary search”, “Chapter 3: Tenses”.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                ForEach(order, id: \.self) { stage in
                    let list = topics.filter { $0.stage == stage }
                    if stage != .mastered || showMastered {
                        ForEach(list) { topic in ConceptRow(store: store, topic: topic) { open = .topic(topic, isNew: false) } }
                            .onDelete { offsets in offsets.forEach { store.deleteStudyTopic(id: list[$0].id) } }
                    }
                }
                let mastered = topics.filter { $0.stage == .mastered }.count
                if mastered > 0 {
                    Button(showMastered ? "Hide mastered" : "Show \(mastered) mastered") { withAnimation { showMastered.toggle() } }
                        .font(.subheadline)
                }
            } header: {
                Text("Concepts")
            } footer: {
                Text("Tap the circle to move a concept on: To learn → Learning → Revising → Mastered.")
            }

            let goals = data.goals.filter { $0.subjectId == subject.id }
            Section("Goals") {
                ForEach(goals) { g in GoalRow(goal: g, data: data) { open = .goal(g, isNew: false) } }
                Button { open = .goal(StudyGoal(subjectId: subject.id), isNew: true) } label: { Label("Add a goal", systemImage: "flag") }
            }

            let tests = data.testScores(of: subject.id)
            if !tests.isEmpty {
                Section("Test scores") { TestScoresView(tests: tests) }
            }

            if !sessions.isEmpty {
                Section("Sessions") {
                    ForEach(sessions.prefix(15)) { e in SessionRow(entry: e, data: data, showDate: true) { open = .session(e, isNew: false) } }
                }
            }
        }
    }
}

private struct ConceptRow: View {
    let store: SheetStore
    let topic: StudyTopic
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button {
                var t = topic
                t.stage = topic.stage.next
                if t.stage == .revising && t.nextReview == nil, let today = SheetDate.date(SheetDate.today) {
                    t.nextReview = SheetDate.string(StudySchedule.nextReview(after: today, confidence: t.confidence ?? 3, reviews: max(t.reviews, 1)))
                }
                store.saveStudyTopic(t)
            } label: {
                Image(systemName: topic.stage.symbol).font(.title2).foregroundStyle(topic.stage.tint)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("\(topic.stage.rawValue). Move to \(topic.stage.next.rawValue)")
            Button(action: onTap) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(topic.topic).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                        Text(detail).font(.caption).foregroundStyle(topic.isDue() ? SheetTheme.warn : Color(.secondaryLabel)).lineLimit(1)
                    }
                    Spacer()
                    if let c = topic.confidence { ConfidenceDots(value: c) }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private var detail: String {
        var parts = [topic.stage.rawValue]
        if topic.isDue() { parts.append("revise now") } else if let next = topic.nextReview { parts.append("revise \(SheetDate.short(next))") }
        if let notes = topic.notes { parts.append(notes) }
        return parts.joined(separator: " · ")
    }
}

struct ConfidenceDots: View {
    let value: Int
    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { i in
                Circle().fill(i <= value ? Confidence.tint(value) : Color(.tertiarySystemFill)).frame(width: 6, height: 6)
            }
        }
        .accessibilityLabel("Confidence \(value) of 5")
    }
}

enum Confidence {
    static let labels = ["Forgot", "Shaky", "Okay", "Good", "Solid"]
    static func tint(_ value: Int) -> Color {
        switch value {
        case ...2: SheetTheme.bad
        case 3: SheetTheme.warn
        default: SheetTheme.good
        }
    }
}

/// Five buttons: Forgot · Shaky · Okay · Good · Solid.
struct ConfidencePicker: View {
    @Binding var value: Int?

    var body: some View {
        HStack(spacing: 6) {
            ForEach(1...5, id: \.self) { i in
                Button { value = value == i ? nil : i } label: {
                    VStack(spacing: 3) {
                        Text("\(i)").font(.headline)
                        Text(Confidence.labels[i - 1]).font(.caption2)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                    .background(value == i ? Confidence.tint(i) : SheetTheme.input, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .foregroundStyle(value == i ? .white : .primary)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Subject editor

struct SubjectEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var subject: StudySubject
    @State private var hasTarget: Bool
    @State private var targetDate: Date
    @State private var confirmDelete = false

    init(store: SheetStore, subject: StudySubject, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _subject = State(initialValue: subject)
        _hasTarget = State(initialValue: subject.targetDate != nil)
        _targetDate = State(initialValue: SheetDate.date(subject.targetDate) ?? Calendar.current.date(byAdding: .month, value: 3, to: .now) ?? .now)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Subject or course, e.g. Swift, IELTS, DSA", text: $subject.name).textInputAutocapitalization(.words)
                    TextField("Why are you learning it? (optional)", text: optional(\.why), axis: .vertical)
                    TextField("Book, course or link (optional)", text: optional(\.resource)).textInputAutocapitalization(.never)
                } header: { Text("What you're studying") }

                Section {
                    HStack {
                        Text("Weekly target")
                        Spacer()
                        TextField("hours", value: $subject.weeklyHours, format: .number).keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing).frame(width: 80)
                        Text("h/week").foregroundStyle(.secondary)
                    }
                    Toggle("Exam or deadline", isOn: $hasTarget.animation())
                    if hasTarget { DatePicker("Target date", selection: $targetDate, displayedComponents: .date) }
                } header: { Text("Plan") } footer: { Text("Shows how this week's study compares, and counts down to the date.") }

                Section("Status & colour") {
                    Picker("Status", selection: $subject.status) {
                        ForEach(StudySubject.Status.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    HStack {
                        ForEach(StudyPalette.colors.indices, id: \.self) { i in
                            Button { subject.color = i } label: {
                                Circle().fill(StudyPalette.colors[i]).frame(width: 28, height: 28)
                                    .overlay { if (subject.color ?? 0) % StudyPalette.colors.count == i { Image(systemName: "checkmark").font(.caption.bold()).foregroundStyle(.white) } }
                            }
                            .buttonStyle(.plain)
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                if !isNew {
                    Section {
                        Button("Delete subject", role: .destructive) { confirmDelete = true }
                    } footer: { Text("Its concepts and goals are deleted too. Study time you logged stays in your history.") }
                }
            }
            .navigationTitle(isNew ? "New subject" : "Edit subject")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = subject
                        saved.name = subject.name.trimmingCharacters(in: .whitespaces)
                        saved.targetDate = hasTarget ? SheetDate.string(targetDate) : nil
                        store.saveStudySubject(saved)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(subject.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete \(subject.name)?", message: "Its concepts and goals are deleted too. Your logged study time stays.",
                                                        confirmTitle: "Delete subject") { store.deleteStudySubject(id: subject.id); dismiss() })
        }
        .tint(SheetTheme.navy)
    }

    private func optional(_ key: WritableKeyPath<StudySubject, String?>) -> Binding<String> {
        Binding(get: { subject[keyPath: key] ?? "" }, set: { subject[keyPath: key] = $0.isEmpty ? nil : $0 })
    }
}

// MARK: - Concept editor

struct ConceptEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var topic: StudyTopic
    @State private var hasReview: Bool
    @State private var review: Date
    @State private var open: StudySheet?
    @State private var addAnother = false

    init(store: SheetStore, topic: StudyTopic, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _topic = State(initialValue: topic)
        _hasReview = State(initialValue: topic.nextReview != nil)
        _review = State(initialValue: SheetDate.date(topic.nextReview) ?? Calendar.current.date(byAdding: .day, value: 2, to: .now) ?? .now)
    }

    var body: some View {
        let data = store.snapshot?.studyData ?? StudyData()
        NavigationStack {
            Form {
                Section {
                    TextField("Concept, e.g. Closures, Binary search", text: $topic.topic, axis: .vertical)
                    Picker("Subject", selection: $topic.subjectId) {
                        ForEach(data.subjects) { Text($0.name).tag($0.id) }
                    }
                }
                Section("Where you are with it") {
                    ForEach(StudyTopic.Stage.allCases) { stage in
                        Button { topic.stage = stage } label: {
                            HStack(spacing: 12) {
                                Image(systemName: stage.symbol).foregroundStyle(stage.tint).font(.title3).frame(width: 28)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(stage.rawValue).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                                    Text(stage.explanation).font(.caption).foregroundStyle(Color(.secondaryLabel))
                                }
                                Spacer()
                                if topic.stage == stage { Image(systemName: "checkmark").foregroundStyle(SheetTheme.navy).fontWeight(.bold) }
                            }
                        }
                    }
                }
                Section {
                    ConfidencePicker(value: $topic.confidence).listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                } header: { Text("How well do you know it?") }
                Section {
                    TextField("Key points, formulas, examples, what confused you…", text: optional(\.notes), axis: .vertical)
                        .lineLimit(4...12)
                    TextField("Link (video, article, docs)", text: optional(\.link)).textInputAutocapitalization(.never).keyboardType(.URL)
                    if let link = topic.link, let url = URL(string: link), url.scheme?.hasPrefix("http") == true {
                        Link(destination: url) { Label("Open link", systemImage: "safari") }
                    }
                } header: { Text("Notes") }
                Section {
                    Toggle("Remind me to revise", isOn: $hasReview.animation())
                    if hasReview { DatePicker("Next revision", selection: $review, displayedComponents: .date) }
                } footer: {
                    Text(topic.lastStudied.map { "Last studied \(SheetDate.short($0)) · revised \(topic.reviews) time\(topic.reviews == 1 ? "" : "s"). Logging a session on it sets the next date for you." }
                         ?? "Logging a session on it sets the next revision date for you, sooner if you're shaky on it.")
                }
                if !isNew {
                    let sessions = data.sessions.filter { $0.topicId == topic.id }.sorted { $0.date > $1.date }
                    Section("Study on this concept · \(StudyFormat.minutes(sessions.reduce(0) { $0 + $1.minutes }))") {
                        Button { open = .session(StudyEntry(subjectId: topic.subjectId, topicId: topic.id, topic: topic.topic), isNew: true) } label: {
                            Label("Log time on it", systemImage: "plus")
                        }
                        ForEach(sessions.prefix(10)) { e in SessionRow(entry: e, data: data, showDate: true) { open = .session(e, isNew: false) } }
                    }
                    Section { Button("Delete concept", role: .destructive) { store.deleteStudyTopic(id: topic.id); dismiss() } }
                } else {
                    Section { Toggle("Add another after saving", isOn: $addAnother) }
                }
            }
            .navigationTitle(isNew ? "New concept" : "Concept")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .fontWeight(.semibold)
                        .disabled(topic.topic.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || topic.subjectId.isEmpty)
                }
            }
            .sheet(item: $open) { StudySheetContent(store: store, sheet: $0) }
        }
        .tint(SheetTheme.navy)
    }

    private func save() {
        var saved = topic
        saved.topic = topic.topic.trimmingCharacters(in: .whitespacesAndNewlines)
        saved.nextReview = hasReview ? SheetDate.string(review) : nil
        store.saveStudyTopic(saved)
        if addAnother {
            topic = StudyTopic(subjectId: topic.subjectId)
            hasReview = false
        } else {
            dismiss()
        }
    }

    private func optional(_ key: WritableKeyPath<StudyTopic, String?>) -> Binding<String> {
        Binding(get: { topic[keyPath: key] ?? "" }, set: { topic[keyPath: key] = $0.isEmpty ? nil : $0 })
    }
}

// MARK: - Session editor

struct StudySessionEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var entry: StudyEntry
    @State private var date: Date
    @State private var newTopic = ""
    @State private var confidence: Int?
    @State private var stage: StudyTopic.Stage?

    init(store: SheetStore, entry: StudyEntry, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _entry = State(initialValue: entry)
        _date = State(initialValue: SheetDate.date(entry.date) ?? .now)
        let topic = store.snapshot?.studyData.topic(entry.topicId)
        _confidence = State(initialValue: topic?.confidence)
        _stage = State(initialValue: topic?.stage)
    }

    private let quick: [Double] = [15, 25, 30, 45, 60, 90, 120]

    var body: some View {
        let data = store.snapshot?.studyData ?? StudyData()
        let topics = data.topics(of: entry.subjectId).filter { $0.stage != .mastered || $0.id == entry.topicId }
        let topic = data.topic(entry.topicId)
        NavigationStack {
            Form {
                Section("What did you study?") {
                    Picker("Subject", selection: $entry.subjectId) {
                        if entry.subjectId.isEmpty { Text("Choose…").tag("") }
                        ForEach(data.subjects) { Text($0.name).tag($0.id) }
                    }
                    .onChange(of: entry.subjectId) { if data.topic(entry.topicId)?.subjectId != entry.subjectId { entry.topicId = nil } }
                    Picker("Concept", selection: Binding(get: { entry.topicId ?? "" }, set: { id in
                        entry.topicId = id.isEmpty ? nil : id
                        let t = data.topic(entry.topicId)
                        confidence = t?.confidence
                        stage = t?.stage
                    })) {
                        Text("General / none").tag("")
                        ForEach(topics) { Text($0.topic).tag($0.id) }
                    }
                    if entry.topicId == nil && !entry.subjectId.isEmpty {
                        TextField("Or a new concept…", text: $newTopic)
                    }
                    Picker("Kind", selection: $entry.kind) {
                        ForEach(StudyEntry.Kind.allCases) { Label($0.rawValue, systemImage: $0.symbol).tag($0) }
                    }
                }

                Section {
                    HStack {
                        TextField("Minutes", value: $entry.minutes, format: .number).keyboardType(.numberPad)
                            .font(.system(.title2, design: .rounded, weight: .bold))
                        Text("min · \(StudyFormat.minutes(entry.minutes))").foregroundStyle(.secondary)
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(quick, id: \.self) { m in
                                Button(StudyFormat.minutes(m)) { entry.minutes = m }
                                    .font(.caption.weight(.semibold)).padding(.horizontal, 12).padding(.vertical, 7)
                                    .background(entry.minutes == m ? SheetTheme.navy : SheetTheme.input, in: Capsule())
                                    .foregroundStyle(entry.minutes == m ? .white : .primary)
                                    .buttonStyle(.plain)
                            }
                        }
                    }
                    DatePicker("Date", selection: $date, in: ...Date.now, displayedComponents: .date)
                } header: { Text("How long") }

                Section {
                    HStack {
                        Text("Focus")
                        Spacer()
                        ForEach(1...5, id: \.self) { i in
                            Button { entry.focus = entry.focus == i ? nil : i } label: {
                                Image(systemName: i <= (entry.focus ?? 0) ? "star.fill" : "star").foregroundStyle(SheetTheme.warn)
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                    if entry.kind == .test {
                        HStack {
                            Text("Score")
                            Spacer()
                            TextField("%", value: $entry.score, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 70)
                            Text("%").foregroundStyle(.secondary)
                        }
                    }
                    TextField("What did you cover? What's still unclear?", text: Binding(get: { entry.notes ?? "" }, set: { entry.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                        .lineLimit(2...8)
                } header: { Text("How it went") }

                if entry.topicId != nil || !newTopic.trimmingCharacters(in: .whitespaces).isEmpty {
                    Section {
                        ConfidencePicker(value: $confidence).listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                        Picker("Stage", selection: Binding(get: { stage ?? .learning }, set: { stage = $0 })) {
                            ForEach(StudyTopic.Stage.allCases) { Text($0.rawValue).tag($0) }
                        }
                    } header: {
                        Text("How well do you know it now?")
                    } footer: {
                        let reviews = (topic?.reviews ?? 0) + (isNew ? 1 : 0)
                        Text("Next revision \(StudyFormat.countdown(StudySchedule.days(confidence: confidence ?? 3, reviews: max(reviews, 1)))) — sooner when you're shaky, further apart as it sticks.")
                    }
                }

                if !isNew {
                    Section { Button("Delete session", role: .destructive) { store.deleteStudySession(id: entry.id); dismiss() } }
                }
            }
            .navigationTitle(isNew ? "Log study" : "Session")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save(data) }.fontWeight(.semibold).disabled(entry.subjectId.isEmpty || entry.minutes <= 0)
                }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func save(_ data: StudyData) {
        var saved = entry
        saved.date = SheetDate.string(date)
        saved.minutes = entry.minutes.rounded()
        saved.subject = data.subject(entry.subjectId)?.name
        var topic = data.topic(entry.topicId)
        let typed = newTopic.trimmingCharacters(in: .whitespaces)
        if topic == nil, !typed.isEmpty {
            topic = StudyTopic(subjectId: entry.subjectId, topic: typed, stage: .learning)
            saved.topicId = topic?.id
        }
        saved.topic = topic?.topic
        var updated: StudyTopic?
        if let topic {
            // A new session counts as a review and reschedules; an edit only updates what you changed.
            var t = isNew ? topic.studied(on: saved.date, confidence: confidence) : topic
            if !isNew, let confidence { t.confidence = confidence }
            if let stage { t.stage = isNew && stage == .toLearn ? .learning : stage }
            if t != topic { updated = t }
        }
        store.saveStudySession(saved, topic: updated)
        dismiss()
    }
}

// MARK: - Goal editor

struct StudyGoalEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var goal: StudyGoal
    @State private var hasDue: Bool
    @State private var due: Date
    @State private var start: Date

    init(store: SheetStore, goal: StudyGoal, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _goal = State(initialValue: goal)
        _hasDue = State(initialValue: goal.due != nil)
        _due = State(initialValue: SheetDate.date(goal.due) ?? Calendar.current.date(byAdding: .month, value: 1, to: .now) ?? .now)
        _start = State(initialValue: SheetDate.date(goal.start) ?? .now)
    }

    var body: some View {
        let data = store.snapshot?.studyData ?? StudyData()
        NavigationStack {
            Form {
                Section {
                    ForEach(StudyGoal.Kind.allCases) { kind in
                        Button { goal.kind = kind } label: {
                            HStack(spacing: 12) {
                                Image(systemName: kind.symbol).foregroundStyle(SheetTheme.indigo).font(.title3).frame(width: 28)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(kind.title).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                                    Text(kind.explanation).font(.caption).foregroundStyle(Color(.secondaryLabel))
                                }
                                Spacer()
                                if goal.kind == kind { Image(systemName: "checkmark").foregroundStyle(SheetTheme.navy).fontWeight(.bold) }
                            }
                        }
                    }
                } header: { Text("Kind of goal") }

                Section {
                    TextField(placeholder, text: $goal.title, axis: .vertical)
                    Picker("Subject", selection: Binding(get: { goal.subjectId ?? "" }, set: { goal.subjectId = $0.isEmpty ? nil : $0 })) {
                        Text("All subjects").tag("")
                        ForEach(data.subjects) { Text($0.name).tag($0.id) }
                    }
                    if goal.kind != .milestone {
                        HStack {
                            Text(goal.kind == .hours ? "Hours" : "Concepts mastered")
                            Spacer()
                            TextField("Target", value: $goal.target, format: .number).keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing).frame(width: 90)
                        }
                    }
                } header: { Text("Goal") }

                Section {
                    if goal.kind == .hours { DatePicker("Counting from", selection: $start, displayedComponents: .date) }
                    Toggle("Due date", isOn: $hasDue.animation())
                    if hasDue { DatePicker("Due", selection: $due, displayedComponents: .date) }
                    Toggle("Achieved", isOn: $goal.done)
                    TextField("Notes (optional)", text: Binding(get: { goal.notes ?? "" }, set: { goal.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                } footer: {
                    if goal.kind != .milestone, !isNew {
                        Text("Now: \(data.progress(of: goal).detail). It fills in from your sessions and concepts.")
                    }
                }
                if !isNew {
                    Section { Button("Delete goal", role: .destructive) { store.deleteStudyGoal(id: goal.id); dismiss() } }
                }
            }
            .navigationTitle(isNew ? "New goal" : "Goal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = goal
                        saved.title = goal.title.trimmingCharacters(in: .whitespacesAndNewlines)
                        saved.start = SheetDate.string(start)
                        saved.due = hasDue ? SheetDate.string(due) : nil
                        if goal.kind == .milestone { saved.target = nil }
                        if saved.done && saved.doneOn == nil { saved.doneOn = SheetDate.today }
                        if !saved.done { saved.doneOn = nil }
                        store.saveStudyGoal(saved)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(goal.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (goal.kind != .milestone && (goal.target ?? 0) <= 0))
                }
            }
        }
        .tint(SheetTheme.navy)
    }

    private var placeholder: String {
        switch goal.kind {
        case .hours: "e.g. 40 hours of DSA"
        case .topics: "e.g. Master SwiftUI basics"
        case .milestone: "e.g. Pass AWS exam"
        }
    }
}

// MARK: - Quick revision

/// A due concept: read your notes, say how well you remembered, done.
struct QuickReviewSheet: View {
    let store: SheetStore
    let topic: StudyTopic
    @Environment(\.dismiss) private var dismiss
    @State private var confidence: Int?
    @State private var minutes: Double = 10
    @State private var logSession = true
    @State private var showNotes = false

    var body: some View {
        let subject = store.snapshot?.studyData.subject(topic.subjectId)
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(subject?.name ?? "").font(.caption.weight(.bold)).foregroundStyle(subject?.tint ?? .secondary)
                        Text(topic.topic).font(.title3.weight(.bold))
                        Text("Try to recall it before you look at your notes.").font(.caption).foregroundStyle(.secondary)
                    }
                    if let notes = topic.notes {
                        DisclosureGroup("Your notes", isExpanded: $showNotes) { Text(notes).font(.subheadline) }
                    }
                    if let link = topic.link, let url = URL(string: link), url.scheme?.hasPrefix("http") == true {
                        Link(destination: url) { Label("Open link", systemImage: "safari") }
                    }
                }
                Section {
                    ConfidencePicker(value: $confidence).listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                } header: { Text("How well did you remember it?") } footer: {
                    if let confidence {
                        Text("Next revision \(StudyFormat.countdown(StudySchedule.days(confidence: confidence, reviews: topic.reviews + 1))).")
                    }
                }
                Section {
                    Toggle("Count it as study time", isOn: $logSession)
                    if logSession {
                        Stepper("\(StudyFormat.minutes(minutes))", value: $minutes, in: 5...180, step: 5)
                    }
                }
            }
            .navigationTitle("Revise")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        guard let confidence else { return }
                        var t = topic.studied(on: SheetDate.today, confidence: confidence)
                        if confidence >= 4 && t.stage == .learning { t.stage = .revising }
                        if logSession {
                            store.saveStudySession(StudyEntry(subjectId: topic.subjectId, subject: subject?.name, topicId: topic.id, topic: topic.topic,
                                                              minutes: minutes, kind: .revise), topic: t)
                        } else {
                            store.saveStudyTopic(t)
                        }
                        dismiss()
                    }
                    .fontWeight(.semibold).disabled(confidence == nil)
                }
            }
        }
        .tint(SheetTheme.navy)
        .presentationDetents([.large])
    }
}

// MARK: - Targets, timer, import

struct StudyTargetsEditor: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var daily: Double?
    @State private var weekly: Double?
    @State private var saving = false
    @AppStorage(ReminderSettings.studyEnabledKey) private var remind = false
    @AppStorage(ReminderSettings.studyMinutesKey) private var remindAt = ReminderSettings.defaultStudyMinutes

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Text("Every day")
                        Spacer()
                        TextField("60", value: $daily, format: .number).keyboardType(.numberPad).multilineTextAlignment(.trailing).frame(width: 80)
                        Text("min").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("Every week")
                        Spacer()
                        TextField("7", value: $weekly, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 80)
                        Text("hours").foregroundStyle(.secondary)
                    }
                } header: { Text("Study targets") } footer: {
                    Text("Saved to Settings ▸ Study in your sheet. Each subject can have its own weekly target too.")
                }
                Section {
                    Toggle("Remind me to study", isOn: $remind)
                        .onChange(of: remind) { _, on in
                            Task {
                                if on { _ = await ReminderScheduler.requestAuthorization() }
                                await ReminderScheduler.refresh(context: context)
                            }
                        }
                    if remind {
                        DatePicker("Every day at", selection: Binding(
                            get: { Calendar.current.date(byAdding: .minute, value: remindAt, to: Calendar.current.startOfDay(for: .now)) ?? .now },
                            set: { remindAt = Calendar.current.component(.hour, from: $0) * 60 + Calendar.current.component(.minute, from: $0)
                                   Task { await ReminderScheduler.refresh(context: context) } }),
                                   displayedComponents: .hourAndMinute)
                    }
                } footer: {
                    Text("Tells you what's due for revision and what's next. Skipped on days you've already reached your target.")
                }
                if let error = store.errorMessage, !saving { Text(error).font(.caption).foregroundStyle(SheetTheme.bad) }
            }
            .navigationTitle("Targets")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                daily = store.snapshot?.settings.studyDailyTarget
                weekly = store.snapshot?.settings.studyWeeklyTarget
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        saving = true
                        Task {
                            let ok = await store.saveSettings(["STUDY_DAILY_TARGET": daily ?? 60, "STUDY_WEEKLY_TARGET": weekly ?? 7])
                            saving = false
                            if ok { dismiss() }
                        }
                    } label: { if saving { ProgressView() } else { Text("Save").fontWeight(.semibold) } }
                    .disabled((daily ?? 0) <= 0 || (weekly ?? 0) <= 0 || saving)
                }
            }
        }
        .tint(SheetTheme.navy)
        .presentationDetents([.medium, .large])
    }
}

/// Pick the subject (and concept) the timer is for.
struct StartStudySheet: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var subjectId = ""
    @State private var topicId = ""

    var body: some View {
        let data = store.snapshot?.studyData ?? StudyData()
        let subjects = data.subjects.filter { $0.status != .completed }
        NavigationStack {
            Form {
                Section {
                    Picker("Subject", selection: $subjectId) {
                        ForEach(subjects) { Text($0.name).tag($0.id) }
                    }
                    Picker("Concept", selection: $topicId) {
                        Text("General / none").tag("")
                        ForEach(data.topics(of: subjectId).filter { $0.stage != .mastered }) { Text($0.topic).tag($0.id) }
                    }
                } footer: {
                    Text("The timer keeps running on your Lock Screen. Stop it here to save the session.")
                }
            }
            .navigationTitle("Start studying")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                let current = data.currentTopic
                subjectId = current?.subjectId ?? subjects.first?.id ?? ""
                topicId = current?.id ?? ""
            }
            .onChange(of: subjectId) { if data.topic(topicId)?.subjectId != subjectId { topicId = "" } }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Start") {
                        guard let subject = data.subject(subjectId) else { return }
                        StudyTimer.shared.start(subject: subject, topic: data.topic(topicId))
                        dismiss()
                    }
                    .fontWeight(.semibold).disabled(data.subject(subjectId) == nil)
                }
            }
        }
        .tint(SheetTheme.navy)
        .presentationDetents([.medium])
    }
}

/// Copies the Learning journal into the sheet: sections become subjects, topics become concepts.
struct JournalImportSheet: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \LearningSection.createdAt) private var sections: [LearningSection]
    @Query(sort: \StudySession.createdAt) private var sessions: [StudySession]

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(sections) { section in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(section.name).font(.subheadline.weight(.semibold))
                            Text("\(section.topics.count) topics · \(section.doneCount) done").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                } footer: {
                    Text("Each section becomes a subject and its topics become concepts (Done → Mastered). Timed sessions whose name matches a section come too. Your journal on this phone stays as it is.")
                }
            }
            .navigationTitle("Bring in your journal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Import") { importAll(); dismiss() }.fontWeight(.semibold) }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func importAll() {
        let existing = Set((store.snapshot?.studyData.subjects ?? []).map { $0.name.lowercased() })
        var byName: [String: StudySubject] = [:]
        for (i, section) in sections.enumerated() where !existing.contains(section.name.lowercased()) {
            let subject = StudySubject(name: section.name, color: i, started: SheetDate.string(section.createdAt))
            store.saveStudySubject(subject)
            byName[section.name.lowercased()] = subject
            for topic in section.sortedTopics {
                let stage: StudyTopic.Stage = switch topic.status {
                case .notStarted: .toLearn
                case .learning: .learning
                case .done: .mastered
                }
                store.saveStudyTopic(StudyTopic(subjectId: subject.id, topic: topic.title, stage: stage,
                                                notes: topic.notes.isEmpty ? nil : topic.notes, added: SheetDate.string(topic.createdAt)))
            }
        }
        for session in sessions {
            guard let subject = byName[session.title.lowercased()] else { continue }
            store.saveStudySession(StudyEntry(date: SheetDate.string(session.log?.date ?? session.createdAt), subjectId: subject.id,
                                              subject: subject.name, minutes: Double(session.minutes)))
        }
    }
}
