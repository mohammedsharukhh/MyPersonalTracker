//
//  StudyModels.swift
//  MyPersonalTracker
//
//  Study as the sheet keeps it (18_Study.gs): subjects you're studying, the
//  concepts in each, every session you put in, and your goals. The figures the
//  Study tab shows — today's minutes, streak, progress, what's due for
//  revision, goal progress — are worked out here from those rows.
//

import SwiftUI

struct StudyData: Codable {
    var subjects: [StudySubject] = []
    var topics: [StudyTopic] = []
    var sessions: [StudyEntry] = []
    var goals: [StudyGoal] = []
}

extension SheetSnapshot {
    var studyData: StudyData { study ?? StudyData() }
}

// MARK: - Rows

/// A subject or course: "Swift", "IELTS", "AWS Solutions Architect".
struct StudySubject: Codable, Hashable, Identifiable {
    enum Status: String, Codable, CaseIterable, Identifiable {
        case active = "Active", paused = "Paused", completed = "Completed"
        var id: String { rawValue }
    }

    var id: String
    var name: String
    /// Why you're learning it, or what for ("Switch to iOS job", "Exam in March").
    var why: String?
    /// Book, course or link you're following.
    var resource: String?
    var weeklyHours: Double?
    /// Exam or deadline, "yyyy-MM-dd".
    var targetDate: String?
    var status: Status
    /// Index into `StudyPalette`.
    var color: Int?
    var started: String?

    init(id: String = UUID().uuidString, name: String = "", why: String? = nil, resource: String? = nil, weeklyHours: Double? = nil,
         targetDate: String? = nil, status: Status = .active, color: Int? = nil, started: String? = SheetDate.today) {
        self.id = id; self.name = name; self.why = why; self.resource = resource; self.weeklyHours = weeklyHours
        self.targetDate = targetDate; self.status = status; self.color = color; self.started = started
    }

    enum CodingKeys: String, CodingKey { case id, name, why, resource, weeklyHours, targetDate, status, color, started }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        name = c.flexString(.name) ?? ""
        why = c.flexString(.why)
        resource = c.flexString(.resource)
        weeklyHours = c.flexDouble(.weeklyHours)
        targetDate = c.flexString(.targetDate)
        status = c.flexString(.status).flatMap(Status.init(rawValue:)) ?? .active
        color = c.flexDouble(.color).map { Int($0) }
        started = c.flexString(.started)
    }

    var tint: Color { StudyPalette.color(color) }
}

/// One concept inside a subject, with how well you know it and when to revise it.
struct StudyTopic: Codable, Hashable, Identifiable {
    enum Stage: String, Codable, CaseIterable, Identifiable {
        case toLearn = "To learn", learning = "Learning", revising = "Revising", mastered = "Mastered"
        var id: String { rawValue }

        var symbol: String {
            switch self {
            case .toLearn: "circle"
            case .learning: "circle.lefthalf.filled"
            case .revising: "arrow.triangle.2.circlepath.circle.fill"
            case .mastered: "checkmark.seal.fill"
            }
        }

        var tint: Color {
            switch self {
            case .toLearn: .secondary
            case .learning: SheetTheme.sky
            case .revising: SheetTheme.warn
            case .mastered: SheetTheme.good
            }
        }

        var explanation: String {
            switch self {
            case .toLearn: "Not started yet"
            case .learning: "Working through it now"
            case .revising: "Learnt it — revising so it sticks"
            case .mastered: "You know it well"
            }
        }

        /// How far along it counts toward the subject's progress.
        var weight: Double {
            switch self {
            case .toLearn: 0
            case .learning: 0.35
            case .revising: 0.7
            case .mastered: 1
            }
        }

        var next: Stage {
            switch self {
            case .toLearn: .learning
            case .learning: .revising
            case .revising: .mastered
            case .mastered: .toLearn
            }
        }
    }

    var id: String
    var subjectId: String
    var topic: String
    var stage: Stage
    /// 1 (shaky) … 5 (solid).
    var confidence: Int?
    var lastStudied: String?
    var nextReview: String?
    var reviews: Int
    var notes: String?
    var link: String?
    var added: String?

    init(id: String = UUID().uuidString, subjectId: String, topic: String = "", stage: Stage = .toLearn, confidence: Int? = nil,
         lastStudied: String? = nil, nextReview: String? = nil, reviews: Int = 0, notes: String? = nil, link: String? = nil,
         added: String? = SheetDate.today) {
        self.id = id; self.subjectId = subjectId; self.topic = topic; self.stage = stage; self.confidence = confidence
        self.lastStudied = lastStudied; self.nextReview = nextReview; self.reviews = reviews; self.notes = notes
        self.link = link; self.added = added
    }

    enum CodingKeys: String, CodingKey { case id, subjectId, topic, stage, confidence, lastStudied, nextReview, reviews, notes, link, added }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        subjectId = c.flexString(.subjectId) ?? ""
        topic = c.flexString(.topic) ?? ""
        stage = c.flexString(.stage).flatMap(Stage.init(rawValue:)) ?? .toLearn
        confidence = c.flexDouble(.confidence).map { Int($0) }
        lastStudied = c.flexString(.lastStudied)
        nextReview = c.flexString(.nextReview)
        reviews = c.flexDouble(.reviews).map { Int($0) } ?? 0
        notes = c.flexString(.notes)
        link = c.flexString(.link)
        added = c.flexString(.added)
    }

    func isDue(on today: String = SheetDate.today) -> Bool {
        guard let nextReview else { return false }
        return nextReview <= today
    }

    /// After studying it on `date`: moves it on from "To learn", counts the
    /// review and schedules the next one from how confident you feel.
    func studied(on date: String, confidence: Int?) -> StudyTopic {
        var t = self
        if t.lastStudied.map({ date >= $0 }) ?? true { t.lastStudied = date }
        if let confidence { t.confidence = confidence }
        if t.stage == .toLearn { t.stage = .learning }
        t.reviews += 1
        if let base = SheetDate.date(date) {
            t.nextReview = SheetDate.string(StudySchedule.nextReview(after: base, confidence: t.confidence ?? 3, reviews: t.reviews))
        }
        return t
    }
}

/// When to revise next: sooner when it's shaky, further apart each time you
/// come back to it (a simple spaced-repetition schedule).
enum StudySchedule {
    /// Days until the next review for confidence 1…5, before spacing out.
    static let baseDays = [1, 2, 4, 7, 14]

    static func days(confidence: Int, reviews: Int) -> Int {
        let c = min(max(confidence, 1), 5)
        let base = baseDays[c - 1]
        // Forgot it or shaky: start the spacing over, so it comes back soon.
        guard c > 2 else { return base }
        let spacing = 1 + Double(max(reviews - 1, 0)) * 0.5
        return min(90, Int((Double(base) * spacing).rounded()))
    }

    static func nextReview(after date: Date, confidence: Int, reviews: Int) -> Date {
        Calendar.current.date(byAdding: .day, value: days(confidence: confidence, reviews: reviews), to: date) ?? date
    }
}

/// One study session.
struct StudyEntry: Codable, Hashable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case learn = "Learn", practice = "Practice", revise = "Revise", test = "Test", project = "Project"
        var id: String { rawValue }

        var symbol: String {
            switch self {
            case .learn: "book.fill"
            case .practice: "pencil.and.list.clipboard"
            case .revise: "arrow.triangle.2.circlepath"
            case .test: "checkmark.rectangle.stack.fill"
            case .project: "hammer.fill"
            }
        }
    }

    var id: String
    var date: String
    var subjectId: String
    /// The subject's name as the sheet copied it (kept after the subject is deleted).
    var subject: String?
    var topicId: String?
    var topic: String?
    var minutes: Double
    var kind: Kind
    /// 1 (distracted) … 5 (deep focus).
    var focus: Int?
    var notes: String?
    /// A test or quiz result in %.
    var score: Double?
    var pending = false

    init(id: String = UUID().uuidString, date: String = SheetDate.today, subjectId: String, subject: String? = nil,
         topicId: String? = nil, topic: String? = nil, minutes: Double = 30, kind: Kind = .learn, focus: Int? = nil,
         notes: String? = nil, score: Double? = nil, pending: Bool = false) {
        self.id = id; self.date = date; self.subjectId = subjectId; self.subject = subject; self.topicId = topicId
        self.topic = topic; self.minutes = minutes; self.kind = kind; self.focus = focus; self.notes = notes
        self.score = score; self.pending = pending
    }

    enum CodingKeys: String, CodingKey { case id, date, subjectId, subject, topicId, topic, minutes, kind, focus, notes, score }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        date = c.flexString(.date) ?? ""
        subjectId = c.flexString(.subjectId) ?? ""
        subject = c.flexString(.subject)
        topicId = c.flexString(.topicId)
        topic = c.flexString(.topic)
        minutes = c.flexDouble(.minutes) ?? 0
        kind = c.flexString(.kind).flatMap(Kind.init(rawValue:)) ?? .learn
        focus = c.flexDouble(.focus).map { Int($0) }
        notes = c.flexString(.notes)
        score = c.flexDouble(.score)
    }
}

/// Something you're aiming for: hours to put in, concepts to master, or a milestone.
struct StudyGoal: Codable, Hashable, Identifiable {
    enum Kind: String, Codable, CaseIterable, Identifiable {
        case hours = "Hours", topics = "Topics", milestone = "Milestone"
        var id: String { rawValue }

        var title: String {
            switch self {
            case .hours: "Study hours"
            case .topics: "Master concepts"
            case .milestone: "Milestone"
            }
        }

        var explanation: String {
            switch self {
            case .hours: "e.g. 40 hours of DSA by December"
            case .topics: "e.g. master 15 SwiftUI concepts"
            case .milestone: "e.g. pass the AWS exam, finish the course"
            }
        }

        var symbol: String {
            switch self {
            case .hours: "clock.fill"
            case .topics: "checkmark.seal.fill"
            case .milestone: "flag.checkered"
            }
        }
    }

    var id: String
    var title: String
    var kind: Kind
    /// Empty: counts every subject.
    var subjectId: String?
    var target: Double?
    var start: String?
    var due: String?
    var done: Bool
    var doneOn: String?
    var notes: String?

    init(id: String = UUID().uuidString, title: String = "", kind: Kind = .hours, subjectId: String? = nil, target: Double? = nil,
         start: String? = SheetDate.today, due: String? = nil, done: Bool = false, doneOn: String? = nil, notes: String? = nil) {
        self.id = id; self.title = title; self.kind = kind; self.subjectId = subjectId; self.target = target
        self.start = start; self.due = due; self.done = done; self.doneOn = doneOn; self.notes = notes
    }

    enum CodingKeys: String, CodingKey { case id, title, kind, subjectId, target, start, due, done, doneOn, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.flexString(.id) ?? UUID().uuidString
        title = c.flexString(.title) ?? ""
        kind = c.flexString(.kind).flatMap(Kind.init(rawValue:)) ?? .milestone
        subjectId = c.flexString(.subjectId)
        target = c.flexDouble(.target)
        start = c.flexString(.start)
        due = c.flexString(.due)
        done = (try? c.decodeIfPresent(Bool.self, forKey: .done)) ?? (c.flexString(.done) == "Y")
        doneOn = c.flexString(.doneOn)
        notes = c.flexString(.notes)
    }
}

enum StudyPalette {
    static let colors: [Color] = [SheetTheme.indigo, SheetTheme.teal, SheetTheme.purple, SheetTheme.warn,
                                  SheetTheme.sky, SheetTheme.good, SheetTheme.bad, SheetTheme.navy]
    static func color(_ index: Int?) -> Color { colors[abs(index ?? 0) % colors.count] }
}

// MARK: - Figures

struct StudyPace {
    enum State { case onTrack, behind, done, passed }
    let left: Int
    let total: Int
    let daysLeft: Int
    let perWeekNeeded: Double
    let perWeekRecent: Double
    let state: State

    var summary: String {
        switch state {
        case .done: return "Every concept mastered"
        case .passed: return "Target date passed · \(left) left"
        case .onTrack, .behind:
            let need = perWeekNeeded < 1 ? "under 1" : "about \(Int(perWeekNeeded.rounded(.up)))"
            return "\(left) left · \(StudyFormat.countdown(daysLeft)) · \(need) a week"
        }
    }
}

/// A goal's progress, worked out from the log and the topics.
struct GoalProgress {
    enum State { case done, onTrack, behind, overdue, open }
    let done: Double
    let target: Double
    let fraction: Double
    let state: State
    let detail: String
}

extension StudyData {
    func subject(_ id: String?) -> StudySubject? { subjects.first { $0.id == id } }
    func topic(_ id: String?) -> StudyTopic? { topics.first { $0.id == id } }

    var activeSubjects: [StudySubject] { subjects.filter { $0.status == .active } }

    /// A subject's concepts in the order they were added (a pasted syllabus keeps its order).
    func topics(of subjectId: String) -> [StudyTopic] {
        topics.enumerated().filter { $0.element.subjectId == subjectId }
            .sorted { ($0.element.added ?? "", $0.offset) < ($1.element.added ?? "", $1.offset) }
            .map(\.element)
    }

    /// Mock tests and quizzes with a score, oldest first.
    func testScores(of subjectId: String) -> [StudyEntry] {
        sessions.filter { $0.subjectId == subjectId && $0.score != nil }.sorted { $0.date < $1.date }
    }

    /// How a subject with a target date is paced: what's left, and what it takes to finish.
    func pace(of subject: StudySubject, today: Date = .now) -> StudyPace? {
        guard subject.status == .active, let days = Self.daysUntil(subject.targetDate, from: today) else { return nil }
        let list = topics.filter { $0.subjectId == subject.id }
        guard !list.isEmpty else { return nil }
        let left = list.filter { $0.stage != .mastered }.count
        // Concepts you've got through lately (moved past "Learning" and touched in the last 2 weeks).
        let since = SheetDate.string(Calendar.current.date(byAdding: .day, value: -14, to: today) ?? today)
        let recent = Double(list.filter { ($0.stage == .revising || $0.stage == .mastered) && ($0.lastStudied ?? "") >= since }.count) / 2
        let weeks = max(Double(days) / 7, 1.0 / 7)
        let needed = Double(left) / weeks
        let state: StudyPace.State = left == 0 ? .done : (days < 0 ? .passed : (recent + 0.25 >= needed ? .onTrack : .behind))
        return StudyPace(left: left, total: list.count, daysLeft: days, perWeekNeeded: needed, perWeekRecent: recent, state: state)
    }

    func sessions(of subjectId: String) -> [StudyEntry] {
        sessions.filter { $0.subjectId == subjectId }.sorted { $0.date > $1.date }
    }

    /// Minutes studied between two dates (inclusive), optionally for one subject.
    func minutes(from: String, to: String, subjectId: String? = nil) -> Double {
        sessions.filter { $0.date >= from && $0.date <= to && (subjectId == nil || $0.subjectId == subjectId) }
            .reduce(0) { $0 + $1.minutes }
    }

    func minutes(on date: String) -> Double { minutes(from: date, to: date) }

    /// Days in a row with some study, up to today (or yesterday, if today has none yet).
    func streak(today: Date = .now) -> Int {
        let days = Set(sessions.filter { $0.minutes > 0 }.map(\.date))
        let cal = Calendar.current
        var day = cal.startOfDay(for: today)
        if !days.contains(SheetDate.string(day)) { day = cal.date(byAdding: .day, value: -1, to: day) ?? day }
        var count = 0
        while days.contains(SheetDate.string(day)) {
            count += 1
            day = cal.date(byAdding: .day, value: -1, to: day) ?? day
        }
        return count
    }

    static func weekStart(_ date: Date = .now) -> Date {
        let cal = Calendar.current
        let weekday = (cal.component(.weekday, from: date) + 5) % 7   // Monday = 0
        return cal.date(byAdding: .day, value: -weekday, to: cal.startOfDay(for: date)) ?? date
    }

    func weekMinutes(_ date: Date = .now, subjectId: String? = nil) -> Double {
        let start = Self.weekStart(date)
        let end = Calendar.current.date(byAdding: .day, value: 6, to: start) ?? start
        return minutes(from: SheetDate.string(start), to: SheetDate.string(end), subjectId: subjectId)
    }

    /// 0…1: how far through its concepts a subject is (stages weighted).
    func progress(of subjectId: String) -> Double {
        let list = topics.filter { $0.subjectId == subjectId }
        guard !list.isEmpty else { return 0 }
        return list.reduce(0) { $0 + $1.stage.weight } / Double(list.count)
    }

    func mastered(of subjectId: String?) -> Int {
        topics.filter { $0.stage == .mastered && (subjectId == nil || $0.subjectId == subjectId) }.count
    }

    /// Concepts due for revision today or earlier, most overdue first (paused subjects left out).
    func dueTopics(today: String = SheetDate.today) -> [StudyTopic] {
        let paused = Set(subjects.filter { $0.status != .active }.map(\.id))
        return topics.filter { $0.isDue(on: today) && !paused.contains($0.subjectId) }
            .sorted { ($0.nextReview ?? "") < ($1.nextReview ?? "") }
    }

    /// What you're on right now: the concept you're learning that you touched last,
    /// else the next one to learn in an active subject.
    var currentTopic: StudyTopic? {
        let active = Set(activeSubjects.map(\.id))
        let learning = topics.filter { $0.stage == .learning && active.contains($0.subjectId) }
            .sorted { ($0.lastStudied ?? "") > ($1.lastStudied ?? "") }
        if let first = learning.first { return first }
        return topics.filter { $0.stage == .toLearn && active.contains($0.subjectId) }
            .sorted { ($0.added ?? "") < ($1.added ?? "") }.first
    }

    func progress(of goal: StudyGoal, today: Date = .now) -> GoalProgress {
        let todayText = SheetDate.string(today)
        let target = max(goal.target ?? 1, 0.0001)
        let done: Double
        let detail: String
        switch goal.kind {
        case .hours:
            let end = goal.doneOn ?? todayText
            done = minutes(from: goal.start ?? "0000-00-00", to: end, subjectId: goal.subjectId) / 60
            detail = "\(SheetFormat.number(done)) of \(SheetFormat.number(goal.target, digits: 0)) h"
        case .topics:
            done = Double(mastered(of: goal.subjectId))
            detail = "\(Int(done)) of \(Int(goal.target ?? 0)) mastered"
        case .milestone:
            done = goal.done ? 1 : 0
            detail = goal.done ? "Done" : "Not yet"
        }
        let fraction = goal.kind == .milestone ? done : min(done / target, 1)
        let state: GoalProgress.State
        if goal.done || (goal.kind != .milestone && fraction >= 1) {
            state = .done
        } else if let due = goal.due, due < todayText {
            state = .overdue
        } else if let due = SheetDate.date(goal.due), let start = SheetDate.date(goal.start), due > start, goal.kind != .milestone {
            let elapsed = today.timeIntervalSince(start) / due.timeIntervalSince(start)
            state = fraction + 0.05 >= min(max(elapsed, 0), 1) ? .onTrack : .behind
        } else {
            state = .open
        }
        return GoalProgress(done: done, target: goal.target ?? 1, fraction: fraction, state: state, detail: detail)
    }

    /// Days until a date ("yyyy-MM-dd"), negative when past.
    static func daysUntil(_ date: String?, from today: Date = .now) -> Int? {
        guard let target = SheetDate.date(date) else { return nil }
        return Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: today), to: target).day
    }
}

extension GoalProgress.State {
    var label: String {
        switch self {
        case .done: "Done"
        case .onTrack: "On track"
        case .behind: "Behind"
        case .overdue: "Overdue"
        case .open: "In progress"
        }
    }

    var tint: Color {
        switch self {
        case .done: SheetTheme.good
        case .onTrack: SheetTheme.teal
        case .behind: SheetTheme.warn
        case .overdue: SheetTheme.bad
        case .open: SheetTheme.sky
        }
    }
}

enum StudyFormat {
    /// "1h 25m", "40m", "0m".
    static func minutes(_ value: Double) -> String {
        let total = Int(value.rounded())
        let h = total / 60, m = total % 60
        if h == 0 { return "\(m)m" }
        return m == 0 ? "\(h)h" : "\(h)h \(m)m"
    }

    /// "in 12 days", "tomorrow", "today", "3 days ago".
    static func countdown(_ days: Int) -> String {
        switch days {
        case 0: "today"
        case 1: "tomorrow"
        case -1: "yesterday"
        case let d where d > 1: "in \(d) days"
        default: "\(-days) days ago"
        }
    }
}
