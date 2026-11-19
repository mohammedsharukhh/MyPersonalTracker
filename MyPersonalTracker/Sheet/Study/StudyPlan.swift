//
//  StudyPlan.swift
//  MyPersonalTracker
//
//  What a learner actually asks the app: "what do I do today?" (a short plan
//  with one button per step), "help me revise" (all due concepts, one tap
//  each), "don't make me type my syllabus" (paste it), "will I finish in
//  time?" (pacing) and "am I improving?" (test scores).
//

import SwiftUI

// MARK: - Today's plan

/// ① Revise what's due ② Learn the next concept ③ Reach today's target — each ticks itself off.
struct TodayPlanCard: View {
    let data: StudyData
    let settings: SheetSettings
    @Binding var open: StudySheet?
    @AppStorage(ReminderSettings.studyEnabledKey) private var reminding = false

    var body: some View {
        let today = SheetDate.today
        let due = data.dueTopics(today: today)
        let revisedToday = data.sessions.filter { $0.date == today && $0.kind == .revise }.count
        let current = data.currentTopic
        let learntToday = current.map { c in data.sessions.contains { $0.date == today && $0.topicId == c.id } } ?? false
        let minutes = data.minutes(on: today)
        let target = max(settings.studyDailyTarget ?? 60, 1)

        SheetCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Today's plan").font(.headline)
                    Spacer()
                    let done = [due.isEmpty, learntToday || current == nil, minutes >= target].filter { $0 }.count
                    Text("\(done) of 3 done").font(.caption.weight(.semibold)).foregroundStyle(done == 3 ? SheetTheme.good : .secondary)
                }

                // ① Revision.
                PlanStep(number: 1, done: due.isEmpty,
                         title: due.isEmpty ? (revisedToday > 0 ? "Revision done for today" : "Nothing due for revision") : "Revise \(due.count) concept\(due.count == 1 ? "" : "s")",
                         detail: due.isEmpty ? "Concepts come back here when they're due." : due.prefix(3).map(\.topic).joined(separator: ", ") + (due.count > 3 ? "…" : ""),
                         action: due.isEmpty ? nil : ("Start", { open = .reviewRun(due.map(\.id)) }))

                // ② Learning.
                if let current, let subject = data.subject(current.subjectId) {
                    PlanStep(number: 2, done: learntToday, title: learntToday ? "Studied \(current.topic) today" : "Learn: \(current.topic)",
                             detail: subject.name + (current.stage == .toLearn ? " · next up" : " · in progress"),
                             action: learntToday ? nil : ("Study", { open = .startTimer }))
                } else {
                    PlanStep(number: 2, done: false, title: "Pick what to learn next",
                             detail: data.subjects.isEmpty ? "Add a subject and its concepts." : "Add concepts to an active subject.",
                             action: ("Add", { open = data.subjects.isEmpty ? .subject(StudySubject(), isNew: true) : .subjectDetail((data.activeSubjects.first ?? data.subjects[0]).id) }))
                }

                // ③ Time.
                PlanStep(number: 3, done: minutes >= target,
                         title: minutes >= target ? "Today's target reached" : "\(StudyFormat.minutes(target - minutes)) left to today's target",
                         detail: "\(StudyFormat.minutes(minutes)) of \(StudyFormat.minutes(target)) studied",
                         action: minutes >= target ? nil : ("Log", { open = .session(StudyEntry(subjectId: current?.subjectId ?? data.activeSubjects.first?.id ?? data.subjects.first?.id ?? "", topicId: current?.id), isNew: true) }))
                if !reminding {
                    Button { open = .targets } label: {
                        Label("Get a daily study reminder", systemImage: "bell.badge").font(.caption.weight(.semibold))
                    }
                }
            }
        }
    }
}

private struct PlanStep: View {
    let number: Int
    let done: Bool
    let title: String
    let detail: String
    let action: (String, () -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(done ? SheetTheme.good : SheetTheme.navy.opacity(0.12)).frame(width: 30, height: 30)
                if done { Image(systemName: "checkmark").font(.caption.weight(.bold)).foregroundStyle(.white) }
                else { Text("\(number)").font(.subheadline.weight(.bold)).foregroundStyle(SheetTheme.navy) }
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.subheadline.weight(.semibold)).strikethrough(done, color: .secondary)
                    .foregroundStyle(done ? .secondary : .primary).lineLimit(2)
                Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 0)
            if let action {
                Button(action.0, action: action.1)
                    .buttonStyle(.borderedProminent).tint(SheetTheme.navy).font(.subheadline.weight(.semibold))
            }
        }
    }
}

// MARK: - Revise all

/// Every due concept, one after another: recall it, peek at your notes, tap how well you knew it.
struct ReviewRunSheet: View {
    let store: SheetStore
    let topicIds: [String]
    @Environment(\.dismiss) private var dismiss
    @State private var index = 0
    @State private var showNotes = false
    @State private var rated: [(StudyTopic, Int)] = []
    @State private var minutesEach = 3.0
    @State private var logTime = true

    var body: some View {
        let data = store.snapshot?.studyData ?? StudyData()
        let topics = topicIds.compactMap { id in data.topic(id) }
        NavigationStack {
            Group {
                if index < topics.count {
                    card(topics[index], data, position: index + 1, of: topics.count)
                } else {
                    summary
                }
            }
            .navigationTitle(index < topics.count ? "Revise · \(index + 1) of \(topics.count)" : "Revision done")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(index < topics.count ? "Stop" : "Close") { finish(data) }
                }
            }
        }
        .tint(SheetTheme.navy)
        .interactiveDismissDisabled(!rated.isEmpty)
    }

    private func card(_ topic: StudyTopic, _ data: StudyData, position: Int, of total: Int) -> some View {
        let subject = data.subject(topic.subjectId)
        return ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ProgressView(value: Double(position - 1), total: Double(total)).tint(SheetTheme.navy)
                VStack(alignment: .leading, spacing: 6) {
                    Text(subject?.name ?? "").font(.caption.weight(.bold)).foregroundStyle(subject?.tint ?? .secondary)
                    Text(topic.topic).font(.title2.weight(.bold))
                    Text("Say or write down what you remember about it, then check your notes.").font(.subheadline).foregroundStyle(.secondary)
                }
                if let notes = topic.notes {
                    SheetCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Button { withAnimation { showNotes.toggle() } } label: {
                                Label(showNotes ? "Hide your notes" : "Show your notes", systemImage: showNotes ? "eye.slash" : "eye")
                                    .font(.subheadline.weight(.semibold))
                            }
                            if showNotes { Text(notes).font(.body) }
                        }
                    }
                }
                if let link = topic.link, let url = URL(string: link), url.scheme?.hasPrefix("http") == true {
                    Link(destination: url) { Label("Open link", systemImage: "safari") }
                }
                Text("How well did you remember it?").font(.headline)
                VStack(spacing: 8) {
                    ForEach((1...5).reversed(), id: \.self) { level in
                        Button { rate(topic, level) } label: {
                            HStack {
                                Text(Confidence.labels[level - 1]).font(.headline)
                                Spacer()
                                Text("next in \(StudySchedule.days(confidence: level, reviews: topic.reviews + 1)) day\(StudySchedule.days(confidence: level, reviews: topic.reviews + 1) == 1 ? "" : "s")")
                                    .font(.caption).opacity(0.8)
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity)
                            .background(Confidence.tint(level).opacity(0.15), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .foregroundStyle(Confidence.tint(level))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
        .background(SheetTheme.page)
    }

    private var summary: some View {
        Form {
            Section {
                Label("You revised \(rated.count) concept\(rated.count == 1 ? "" : "s")", systemImage: "checkmark.seal.fill")
                    .font(.headline).foregroundStyle(SheetTheme.good)
                let shaky = rated.filter { $0.1 <= 2 }.map(\.0.topic)
                if !shaky.isEmpty { Text("Coming back soon: \(shaky.joined(separator: ", "))").font(.subheadline).foregroundStyle(.secondary) }
            }
            Section {
                Toggle("Count it as study time", isOn: $logTime)
                if logTime {
                    Stepper("About \(Int(minutesEach)) min each · \(StudyFormat.minutes(minutesEach * Double(rated.count)))", value: $minutesEach, in: 1...20)
                }
            }
            Section {
                Button { finish(store.snapshot?.studyData ?? StudyData()) } label: {
                    Text("Done").font(.headline).frame(maxWidth: .infinity)
                }
            }
        }
    }

    private func rate(_ topic: StudyTopic, _ level: Int) {
        var t = topic.studied(on: SheetDate.today, confidence: level)
        if level >= 4 && t.stage == .learning { t.stage = .revising }
        store.saveStudyTopic(t)
        rated.append((topic, level))
        showNotes = false
        withAnimation(.snappy) { index += 1 }
    }

    /// One revision session per subject for the concepts rated.
    private func finish(_ data: StudyData) {
        if logTime, !rated.isEmpty {
            for (subjectId, items) in Dictionary(grouping: rated, by: { $0.0.subjectId }) {
                store.saveStudySession(StudyEntry(subjectId: subjectId, subject: data.subject(subjectId)?.name,
                                                  topicId: items.count == 1 ? items[0].0.id : nil, topic: items.count == 1 ? items[0].0.topic : nil,
                                                  minutes: minutesEach * Double(items.count), kind: .revise,
                                                  notes: items.count == 1 ? nil : "Revised " + items.map(\.0.topic).joined(separator: ", ")))
            }
        }
        dismiss()
    }
}

// MARK: - Paste a syllabus

enum SyllabusParse {
    /// One concept per line, without bullets or numbering ("1.", "a)", "-", "•").
    static func lines(_ text: String) -> [String] {
        let marker = try? NSRegularExpression(pattern: #"^\s*(?:[-•*·▪◦–]+|\(?\d{1,3}[.)]|\(?[a-zA-Z][.)])\s+"#)
        var seen = Set<String>()
        return text.split(whereSeparator: \.isNewline).compactMap { raw in
            var line = String(raw)
            if let marker { line = marker.stringByReplacingMatches(in: line, range: NSRange(line.startIndex..., in: line), withTemplate: "") }
            line = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty, seen.insert(line.lowercased()).inserted else { return nil }
            return String(line.prefix(200))
        }
    }
}

struct SyllabusSheet: View {
    let store: SheetStore
    let subjectId: String
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""

    var body: some View {
        let data = store.snapshot?.studyData ?? StudyData()
        let existing = Set(data.topics(of: subjectId).map { $0.topic.lowercased() })
        let lines = SyllabusParse.lines(text).filter { !existing.contains($0.lowercased()) }
        NavigationStack {
            Form {
                Section {
                    TextEditor(text: $text).frame(minHeight: 200).font(.body)
                        .overlay(alignment: .topLeading) {
                            if text.isEmpty {
                                Text("For example:\n1. Arrays and strings\n2. Two pointers\n3. Sliding window\n4. Binary search")
                                    .foregroundStyle(.tertiary).padding(.top, 8).padding(.leading, 5).allowsHitTesting(false)
                            }
                        }
                } header: {
                    Text("Paste or type one concept per line")
                } footer: {
                    Text("Copy the contents page of your book or course. Bullets and numbering are removed; concepts already in \(data.subject(subjectId)?.name ?? "this subject") are skipped.")
                }
                if !lines.isEmpty {
                    Section("\(lines.count) concept\(lines.count == 1 ? "" : "s") to add") {
                        ForEach(Array(lines.enumerated()), id: \.offset) { _, line in Label(line, systemImage: "circle") }
                    }
                }
            }
            .navigationTitle("Add your syllabus")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add \(lines.count)") {
                        for line in lines { store.saveStudyTopic(StudyTopic(subjectId: subjectId, topic: line)) }
                        dismiss()
                    }
                    .fontWeight(.semibold).disabled(lines.isEmpty)
                }
            }
        }
        .tint(SheetTheme.navy)
    }
}

// MARK: - Pacing and tests (subject detail)

struct PaceRow: View {
    let pace: StudyPace

    var body: some View {
        let tint: Color = switch pace.state {
        case .done: SheetTheme.good
        case .onTrack: SheetTheme.teal
        case .behind: SheetTheme.warn
        case .passed: SheetTheme.bad
        }
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Label(pace.state == .behind ? "Behind pace" : (pace.state == .onTrack ? "On pace" : (pace.state == .done ? "Finished" : "Past the date")),
                      systemImage: "speedometer").font(.subheadline.weight(.semibold)).foregroundStyle(tint)
                Spacer()
                Text("\(pace.total - pace.left) of \(pace.total) mastered").font(.caption).foregroundStyle(.secondary)
            }
            Text(pace.summary).font(.subheadline)
            if pace.state == .onTrack || pace.state == .behind {
                Text("Lately you've got through about \(SheetFormat.number(pace.perWeekRecent)) a week.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

struct TestScoresView: View {
    let tests: [StudyEntry]

    var body: some View {
        let scores = tests.compactMap(\.score)
        let average = scores.isEmpty ? 0 : scores.reduce(0, +) / Double(scores.count)
        let trend = scores.count >= 2 ? scores[scores.count - 1] - scores[scores.count - 2] : 0
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Latest \(SheetFormat.number(scores.last, digits: 0))%").font(.headline)
                    Text("Average \(SheetFormat.number(average, digits: 0))% over \(scores.count) test\(scores.count == 1 ? "" : "s")").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if scores.count >= 2 {
                    Label("\(trend >= 0 ? "+" : "")\(SheetFormat.number(trend, digits: 0))", systemImage: trend >= 0 ? "arrow.up.right" : "arrow.down.right")
                        .font(.subheadline.weight(.bold)).foregroundStyle(trend >= 0 ? SheetTheme.good : SheetTheme.bad)
                }
            }
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(Array(tests.suffix(8).enumerated()), id: \.offset) { _, test in
                    VStack(spacing: 3) {
                        Text(SheetFormat.number(test.score, digits: 0)).font(.caption2.weight(.semibold))
                        RoundedRectangle(cornerRadius: 4).fill(Confidence.tint((test.score ?? 0) >= 75 ? 5 : ((test.score ?? 0) >= 50 ? 3 : 1)))
                            .frame(height: max(6, CGFloat(test.score ?? 0) * 0.7))
                        Text(SheetDate.date(test.date)?.formatted(.dateTime.day().month(.abbreviated)) ?? "").font(.system(size: 9)).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 100, alignment: .bottom)
        }
        .padding(.vertical, 4)
    }
}
