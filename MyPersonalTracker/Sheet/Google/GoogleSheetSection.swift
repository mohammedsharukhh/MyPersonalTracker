//
//  GoogleSheetSection.swift
//  MyPersonalTracker
//
//  Settings ▸ Your sheet: sign in with Google and the tracker sheet is made in
//  (or found again in) the user's own Drive. No Apps Script, link or key.
//

import SwiftUI

struct GoogleSheetSection: View {
    let store: SheetStore
    @State private var auth = GoogleAuth.shared
    @State private var working = false
    @State private var step: String?
    @State private var error: String?
    @State private var confirmSignOut = false
    @State private var confirmNewSheet = false
    @State private var backedUp: String?
    @Environment(\.openURL) private var openURL

    private var connected: Bool { store.usesGoogleSheet && auth.isSignedIn }

    var body: some View {
        Section {
            if connected {
                LabeledContent {
                    Text(auth.email ?? "Google account").lineLimit(1).truncationMode(.middle)
                } label: {
                    Label("Connected", systemImage: "checkmark.circle.fill").foregroundStyle(SheetTheme.good)
                }
                if let url = store.googleSheetURL {
                    Button("Open in Google Sheets", systemImage: "tablecells") { openURL(url) }
                }
                Button {
                    confirmNewSheet = true
                } label: {
                    HStack {
                        Label("Make a new sheet", systemImage: "doc.badge.plus")
                        if working { Spacer(); ProgressView() }
                    }
                }
                .disabled(working)
                Button("Back up to Google Drive", systemImage: "externaldrive.badge.checkmark") { backUp() }
                    .disabled(working)
                if working, let step { Text(step).font(.caption).foregroundStyle(.secondary) }
                if let backedUp { Text("Backed up: “\(backedUp)” and an Excel copy in “Life & Finance · Backups”.").font(.caption).foregroundStyle(SheetTheme.good) }
                Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) { confirmSignOut = true }
                if let problem = store.errorMessage { Text(problem).font(.caption).foregroundStyle(SheetTheme.bad) }
            } else {
                Button {
                    connect()
                } label: {
                    HStack {
                        Label("Sign in with Google", systemImage: "person.crop.circle.badge.checkmark")
                        if working { Spacer(); ProgressView() }
                    }
                }
                .disabled(working || !auth.isConfigured)
                if working, let step { Text(step).font(.caption).foregroundStyle(.secondary) }
            }
            if let error { Text(error).font(.caption).foregroundStyle(SheetTheme.bad) }
        } header: {
            Text("Your sheet")
        } footer: {
            Text(connected
                 ? "“\(TrackerWorkbook.title)” in your Google Drive. The app can only see the files it makes — nothing else in your Drive."
                 : "Sign in and the app makes your tracker sheet in your own Google Drive. Nothing to set up. Signed in before? It finds your sheet again.")
        }
        .popup(isPresented: $confirmSignOut, Popup(title: "Sign out of Google?", message: "Your sheet stays in your Google Drive. Sign in again to see it here.",
                                                    symbol: "rectangle.portrait.and.arrow.right", confirmTitle: "Sign out",
                                                    onConfirm: { Task { await store.disconnectGoogle() } }))
        .popup(isPresented: $confirmNewSheet, Popup(title: "Make a new, empty sheet?",
                                                     message: "The app starts using a fresh sheet with a period from today. Your current sheet stays in Google Drive, renamed “(old)”.",
                                                     symbol: "doc.badge.plus", confirmTitle: "Make it",
                                                     onConfirm: { startNewSheet() }))
    }

    private func backUp() {
        working = true
        error = nil
        backedUp = nil
        step = "Copying your sheet…"
        Task {
            do {
                backedUp = try await store.backUpGoogleSheet()
            } catch {
                self.error = error.localizedDescription
            }
            working = false
            step = nil
        }
    }

    private func startNewSheet() {
        working = true
        error = nil
        Task {
            do {
                try await store.startNewGoogleSheet { step = $0 }
            } catch {
                self.error = error.localizedDescription
            }
            working = false
            step = nil
        }
    }

    private func connect() {
        working = true
        error = nil
        Task {
            do {
                try await store.connectGoogle { step = $0 }
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
