//
//  WelcomeView.swift
//  MyPersonalTracker
//
//  First launch with no sheet connected: what the app is, and Sign in with
//  Google, which makes (or finds) the tracker sheet in the person's own Drive.
//  The same button sits on the "Connect" card and in Settings ▸ Your sheet.
//

import SwiftUI

/// Sign in with Google, then find or make the sheet and load it — with progress and errors shown under it.
struct ConnectGoogleButton: View {
    let store: SheetStore
    var onConnected: () -> Void = {}
    @State private var working = false
    @State private var step: String?
    @State private var error: String?

    var body: some View {
        VStack(spacing: 8) {
            Button {
                connect()
            } label: {
                HStack(spacing: 10) {
                    if working { ProgressView().tint(.white) } else { Image(systemName: "person.crop.circle.badge.checkmark") }
                    Text(working ? "Setting up your sheet…" : "Sign in with Google").font(.headline)
                }
                .frame(maxWidth: .infinity, minHeight: 30)
                .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .tint(SheetTheme.navy)
            .buttonBorderShape(.roundedRectangle(radius: 14))
            .disabled(working || !GoogleAuth.shared.isConfigured)
            if working, store.isLoading {
                SheetLoadingView(progress: store.loadProgress).padding(.top, 4)
            } else if working, let step {
                Text(step).font(.footnote).foregroundStyle(.secondary)
            }
            if let error { Text(error).font(.footnote).foregroundStyle(SheetTheme.bad).multilineTextAlignment(.center) }
        }
    }

    private func connect() {
        working = true
        error = nil
        Task {
            do {
                try await store.connectGoogle { step = $0 }
                onConnected()
            } catch GoogleError.cancelled {
                // Closed the sign-in: nothing to say.
            } catch {
                self.error = error.localizedDescription
            }
            working = false
            step = nil
        }
    }
}

/// Shown once at first launch while no sheet is connected.
struct WelcomeView: View {
    let store: SheetStore
    let onNotNow: () -> Void
    let onWebApp: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                VStack(spacing: 14) {
                    Image(systemName: "tablecells.fill")
                        .font(.system(size: 44, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 88, height: 88)
                        .background(SheetTheme.navy, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .accessibilityHidden(true)
                    Text("Your life, in one sheet").font(.largeTitle.weight(.bold)).multilineTextAlignment(.center)
                    Text("Habits, money, food, study and Mom & Dad — tracked in the app, kept in a Google Sheet that's yours.")
                        .font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .padding(.top, 40)

                VStack(alignment: .leading, spacing: 18) {
                    point("lock.shield", "In your own Google Drive", "The app makes “\(TrackerWorkbook.title)” for you. It can only see the files it makes — nothing else in your Drive.")
                    point("square.grid.2x2", "Ready in seconds", "Every tab, formula and chart is set up. No scripts, links or keys.")
                    point("arrow.triangle.2.circlepath", "Both ways", "What you add here goes to the sheet; what you change in the sheet shows here.")
                    point("iphone.and.arrow.forward", "On every phone", "Sign in on another phone and your sheet is there.")
                }

                VStack(spacing: 14) {
                    ConnectGoogleButton(store: store)
                    Button("Not now", action: onNotNow).font(.body.weight(.semibold))
                    Button("I set up my sheet's web app myself", action: onWebApp)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .background(SheetTheme.page)
        .tint(SheetTheme.navy)
    }

    private func point(_ symbol: String, _ title: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            IconBadge(symbol: symbol, tint: SheetTheme.navy, size: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline)
                Text(text).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
