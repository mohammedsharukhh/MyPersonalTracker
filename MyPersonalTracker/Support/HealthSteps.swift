//
//  HealthSteps.swift
//  MyPersonalTracker
//
//  Steps, sleep, weight and workouts from Apple Health into the Daily Tracker —
//  today and the last few weeks, whenever the app opens or refreshes. Read only;
//  nothing is written to Health.
//
//  Steps only ever go up (a bigger number you typed stays); sleep, weight and
//  Workout only fill a day that's empty, so anything you typed wins. None of it
//  on its own makes a day "logged" (see 03_DailyTracker.gs).
//

import Foundation
import HealthKit
import Observation

/// One day from Health.
nonisolated struct HealthDay: Equatable, Sendable {
    var steps: Int?
    /// Hours asleep the night before (ending that morning).
    var sleep: Double?
    var weight: Double?
    /// 20 minutes or more of workouts.
    var workout = false
}

@MainActor
@Observable
final class HealthSteps {
    static let shared = HealthSteps()
    static let enabledKey = "healthStepsEnabled"
    /// How far back each sync looks (missed days fill in when you next open the app).
    static let daysBack = 30
    static let workoutMinutes = 20.0

    private let health = HKHealthStore()
    private var readTypes: Set<HKObjectType> {
        [HKQuantityType(.stepCount), HKCategoryType(.sleepAnalysis), HKQuantityType(.bodyMass), HKObjectType.workoutType()]
    }

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }
    var isEnabled: Bool {
        get { access(keyPath: \.isEnabled); return UserDefaults.standard.bool(forKey: Self.enabledKey) }
        set { withMutation(keyPath: \.isEnabled) { UserDefaults.standard.set(newValue, forKey: Self.enabledKey) } }
    }
    /// Today's steps from Health, as of the last sync.
    private(set) var today: Int?
    private(set) var lastSync: Date?
    private var syncing = false

    /// Asks for read access, then syncs. Health never says whether reading was
    /// refused (that's private), so a "no" just reads as nothing to fill in.
    func connect(store: SheetStore) async {
        guard isAvailable else { return }
        do {
            try await health.requestAuthorization(toShare: [], read: readTypes)
            isEnabled = true
            await sync(store: store)
        } catch {
            isEnabled = false
        }
    }

    func disconnect() {
        isEnabled = false
        today = nil
    }

    /// Sends what Health has that the sheet doesn't.
    func sync(store: SheetStore) async {
        guard isEnabled, isAvailable, !syncing, let snapshot = store.snapshot, !store.isDemo else { return }
        syncing = true
        defer { syncing = false }
        // Connected before sleep, weight and workouts were read: ask once for those too.
        if (try? await health.statusForAuthorizationRequest(toShare: [], read: readTypes)) == .shouldRequest {
            try? await health.requestAuthorization(toShare: [], read: readTypes)
        }
        let calendar = Calendar.current
        let end = Date.now
        let start = calendar.date(byAdding: .day, value: -(Self.daysBack - 1), to: calendar.startOfDay(for: end)) ?? end
        var days: [String: HealthDay] = [:]
        if let steps = try? await dailySteps(from: start, to: end) { for (d, n) in steps { days[d, default: HealthDay()].steps = n } }
        if let sleep = try? await nightlySleep(from: start, to: end) { for (d, h) in sleep { days[d, default: HealthDay()].sleep = h } }
        if let weight = try? await dailyWeight(from: start, to: end) { for (d, kg) in weight { days[d, default: HealthDay()].weight = kg } }
        if let workouts = try? await workoutDays(from: start, to: end) { for d in workouts { days[d, default: HealthDay()].workout = true } }
        today = days[SheetDate.today]?.steps ?? 0
        lastSync = .now
        let changes = Self.changes(days, against: snapshot)
        if !changes.isEmpty { store.saveHealth(changes) }
        WidgetSync.updateSheet(store.snapshot, stepsToday: today)
    }

    /// For each day of the sheet's period, only what Health would add: steps higher
    /// than the sheet's, and sleep, weight or a workout where the sheet has none.
    nonisolated static func changes(_ health: [String: HealthDay], against snapshot: SheetSnapshot) -> [String: HealthDay] {
        var out: [String: HealthDay] = [:]
        let today = SheetDate.today
        // Never a future day, whatever the sheet's rows say.
        for day in snapshot.days where day.date <= today {
            guard let h = health[day.date] else { continue }
            // Only what you track: trackers you turned off, and a 5th habit that isn't a workout, are left alone.
            let set = snapshot.settings
            var add = HealthDay()
            if Tracker.steps.isOn(set), let s = h.steps, s > 0, s > Int(day.steps ?? 0) { add.steps = s }
            if Tracker.sleep.isOn(set), let s = h.sleep, s > 0, day.sleep == nil { add.sleep = (s * 10).rounded() / 10 }
            if Tracker.weight.isOn(set), let w = h.weight, w > 0, day.weight == nil { add.weight = (w * 10).rounded() / 10 }
            if Habit.healthFillsWorkout(set), h.workout, day.workout == nil { add.workout = true }
            if add != HealthDay() { out[day.date] = add }
        }
        return out
    }

    // MARK: - Reading Health

    /// Steps per day ("yyyy-MM-dd" → count), as Health adds them up (no double counting phone + watch).
    private func dailySteps(from start: Date, to end: Date) async throws -> [String: Int] {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end)
        let query = HKStatisticsCollectionQueryDescriptor(
            predicate: HKSamplePredicate.quantitySample(type: HKQuantityType(.stepCount), predicate: predicate),
            options: .cumulativeSum, anchorDate: Calendar.current.startOfDay(for: start), intervalComponents: DateComponents(day: 1))
        let collection = try await query.result(for: health)
        var out: [String: Int] = [:]
        collection.enumerateStatistics(from: start, to: end) { stats, _ in
            if let sum = stats.sumQuantity()?.doubleValue(for: .count()) { out[SheetDate.string(stats.startDate)] = Int(sum.rounded()) }
        }
        return out
    }

    /// Hours asleep each night, filed under the morning it ended (6 pm the day before to 2 pm).
    /// Overlapping records (phone and watch) are merged so nothing counts twice.
    private func nightlySleep(from start: Date, to end: Date) async throws -> [String: Double] {
        let calendar = Calendar.current
        let from = calendar.date(byAdding: .hour, value: -6, to: start) ?? start
        let asleep = HKCategoryValueSleepAnalysis.allAsleepValues.map(\.rawValue)
        let query = HKSampleQueryDescriptor(
            predicates: [.categorySample(type: HKCategoryType(.sleepAnalysis), predicate: HKQuery.predicateForSamples(withStart: from, end: end))],
            sortDescriptors: [SortDescriptor(\.startDate)])
        let samples = try await query.result(for: health).filter { asleep.contains($0.value) }
        var byNight: [String: [(Date, Date)]] = [:]
        for s in samples {
            // The night belongs to the day it ends on, if it ends before 2 pm; a nap after that counts for that day too.
            let key = SheetDate.string(s.endDate)
            byNight[key, default: []].append((s.startDate, s.endDate))
        }
        return byNight.compactMapValues { intervals in
            let merged = intervals.sorted { $0.0 < $1.0 }.reduce(into: [(Date, Date)]()) { acc, next in
                if let last = acc.last, next.0 <= last.1 { acc[acc.count - 1].1 = max(last.1, next.1) } else { acc.append(next) }
            }
            let hours = merged.reduce(0) { $0 + $1.1.timeIntervalSince($1.0) } / 3600
            return hours >= 0.5 ? hours : nil
        }
    }

    /// The last weight recorded each day, in kg.
    private func dailyWeight(from start: Date, to end: Date) async throws -> [String: Double] {
        let query = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: HKQuantityType(.bodyMass), predicate: HKQuery.predicateForSamples(withStart: start, end: end))],
            sortDescriptors: [SortDescriptor(\.startDate)])
        var out: [String: Double] = [:]
        for s in try await query.result(for: health) {
            out[SheetDate.string(s.startDate)] = s.quantity.doubleValue(for: .gramUnit(with: .kilo))
        }
        return out
    }

    /// Days with at least `workoutMinutes` of workouts.
    private func workoutDays(from start: Date, to end: Date) async throws -> Set<String> {
        let query = HKSampleQueryDescriptor(
            predicates: [.workout(HKQuery.predicateForSamples(withStart: start, end: end))],
            sortDescriptors: [SortDescriptor(\.startDate)])
        var minutes: [String: Double] = [:]
        for w in try await query.result(for: health) { minutes[SheetDate.string(w.startDate), default: 0] += w.duration / 60 }
        return Set(minutes.filter { $0.value >= Self.workoutMinutes }.keys)
    }
}
