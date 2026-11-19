//
//  SheetLoadProgress.swift
//  MyPersonalTracker
//
//  What a sheet load is doing, in words, for the loading screens: the first load
//  after connecting can take a minute while the sheet is set up. Mirrors SheetLoadProgress.kt.
//

import SwiftUI

struct SheetLoadProgress: Equatable, Sendable {
    let title: String
    /// 0…1 while the sheet is being set up; nil while reading (a spinner only).
    var fraction: Double?
    var note: String?

    static let reading = SheetLoadProgress(title: "Reading your sheet…")
    static let checking = SheetLoadProgress(title: "Checking what needs setting up…")
    static let finishing = SheetLoadProgress(title: "Almost done…")

    /// Writing the changes the sheet needs, named by the part of the sheet they're in.
    static func writing(tab: String?, done: Int, total: Int) -> SheetLoadProgress {
        let fraction = total > 0 ? min(1, Double(done) / Double(total)) : 0
        return SheetLoadProgress(title: area(of: tab), fraction: fraction,
                                 note: "\(Int((fraction * 100).rounded()))% done · keep the app open")
    }

    /// Google asked for a pause ("too many requests"): what was happening carries on after it.
    static func waiting(seconds: Int, during step: SheetLoadProgress?) -> SheetLoadProgress {
        let step = step ?? reading
        return SheetLoadProgress(title: step.title, fraction: step.fraction,
                                 note: "Google asked us to slow down · carrying on in \(seconds)s")
    }

    private static func area(of tab: String?) -> String {
        guard let tab else { return "Getting your sheet ready…" }
        let areas: [(String, [String])] = [
            ("Building your habits…", ["Daily Tracker", "Dashboard", "Weekly Summary", "Monthly Summary", "My Inputs", "Input Log"]),
            ("Setting up your money…", ["Finance Tracker", "Salary & Savings", "Set Aside", "Salary Split", "Savings Goals", "Recurring", "Loans", "Shopping", "Shopping Trips"]),
            ("Setting up your meals…", ["Food Log", "Meal Plan", "My Foods"]),
            ("Setting up your study plan…", ["Study Subjects", "Study Topics", "Study Log", "Study Goals"]),
            ("Setting up Mom & Dad…", ["Parents", "Parent Medicines", "Parent Dates", "Parent Health", "Take Home", "People"]),
            ("Setting up your planner…", ["Planner", "Outfits", "Wardrobe"]),
            ("Setting up your goals…", ["Life Goals", "Goal Steps"]),
            ("Setting up your renewals…", ["Renewals"]),
        ]
        return areas.first { $0.1.contains(tab) }?.0 ?? "Getting your sheet ready…"
    }
}

/// The loading state of a screen with nothing to show yet: what's happening, and how far along.
struct SheetLoadingView: View {
    let progress: SheetLoadProgress?

    var body: some View {
        let step = progress ?? .reading
        VStack(spacing: 12) {
            if let fraction = step.fraction {
                ProgressView(value: fraction).frame(maxWidth: 260)
            } else {
                ProgressView()
            }
            Text(step.title).font(.headline)
            if let note = step.note {
                Text(note).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .animation(.default, value: step)
    }
}
