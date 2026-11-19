//
//  CardsView.swift
//  MyPersonalTracker
//
//  More ▸ Cards: the list (locked until Face ID or the passcode), one card's
//  details with show and copy, and the editor. See CardModels.swift.
//

import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct CardsScreen: View {
    @State private var vault = CardVault.shared
    @Environment(\.scenePhase) private var scenePhase
    @State private var adding: SavedCard?
    @State private var showing: SavedCard?

    var body: some View {
        Group {
            if vault.isUnlocked { list } else { locked }
        }
        .background(SheetTheme.page)
        .navigationTitle("Cards")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if vault.isUnlocked {
                ToolbarItem(placement: .primaryAction) {
                    Button("Add card", systemImage: "plus") { adding = SavedCard() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Lock", systemImage: "lock.fill") { vault.lock() }
                }
            }
        }
        .modifier(CardPrivacy())
        .task { await vault.unlock() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { vault.didEnterBackground() }
            if phase == .active { vault.didBecomeActive() }
        }
        .onChange(of: vault.isUnlocked) { _, unlocked in
            if !unlocked { adding = nil; showing = nil }
        }
        // Leaving the screen locks them at once.
        .onDisappear { vault.lock() }
        .sheet(item: $adding) { CardEditor(card: $0, isNew: true) }
        .sheet(item: $showing) { CardDetailView(id: $0.id) }
    }

    private var locked: some View {
        ContentUnavailableView {
            Label("Cards are locked", systemImage: "lock.fill")
        } description: {
            Text("Your cards are kept on this iPhone only. Unlock with Face ID or your passcode to see them.")
            if let error = vault.errorMessage { Text(error).foregroundStyle(SheetTheme.bad) }
        } actions: {
            Button { Task { await vault.unlock() } } label: {
                Label("Unlock", systemImage: vault.unlockSymbol)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }

    private var list: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                if vault.cards.isEmpty {
                    SheetCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Keep your cards handy").font(.headline)
                            Text("Save the number, expiry, CVV and name of each debit and credit card, so they're here when you pay online. Tap + to add one.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                }
                ForEach(vault.cards) { card in
                    Button { showing = card } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            CardFace(card: card)
                            switch card.expiryState() {
                            case .expired: StatusPill(text: "Expired — add the new card", tint: SheetTheme.bad)
                            case .soon: StatusPill(text: "Expires soon", tint: SheetTheme.warn)
                            case .fine: EmptyView()
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Shows the full details")
                }
                Label("Saved only on this iPhone, in the Keychain, where only Face ID or your passcode opens them. Never sent to your Google Sheet, not part of Export everything, and not moved to a new phone — your bank can always re-issue the details.",
                      systemImage: "lock.shield")
                    .font(.caption).foregroundStyle(.secondary)
                    .padding(.top, 4)
            }
            .padding(16)
        }
    }
}

/// The card as a card: bank, number, name, expiry and network on its colour.
struct CardFace: View {
    let card: SavedCard
    var revealed = false
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let (from, to) = card.style.hex
        // At accessibility text sizes the rows stack instead of squeezing.
        let row = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6)) : AnyLayout(HStackLayout(alignment: .bottom, spacing: 12))
        VStack(alignment: .leading, spacing: 14) {
            row {
                Text(card.title).font(.headline).lineLimit(3)
                Spacer(minLength: 0)
                Text(card.kind.rawValue.uppercased()).font(.caption2.weight(.bold)).opacity(0.8)
            }
            Spacer(minLength: 8)
            Text(revealed ? card.groupedNumber : card.maskedNumber)
                .font(.title3.monospaced().weight(.semibold))
                .minimumScaleFactor(0.5)
                .lineLimit(revealed ? 2 : 1)
            row {
                VStack(alignment: .leading, spacing: 2) {
                    if !card.holder.isEmpty { Text(card.holder.uppercased()).font(.caption.weight(.semibold)).lineLimit(2) }
                    if !card.expiryText.isEmpty { Text("VALID THRU \(card.expiryText)").font(.caption2.monospaced()).opacity(0.85) }
                }
                Spacer(minLength: 0)
                Text(card.brand.mark).font(.headline.weight(.heavy)).italic()
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, minHeight: 190, alignment: .topLeading)
        .foregroundStyle(.white)
        .background(LinearGradient(colors: [Color(hex: from), Color(hex: to)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .privacySensitive()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(card.spokenSummary)
    }
}

/// One card: show or copy each detail. Copies stay on this iPhone and clear after a minute.
struct CardDetailView: View {
    let id: SavedCard.ID
    @State private var vault = CardVault.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showNumber = false
    @State private var showCVV = false
    @State private var editing = false
    @State private var copied: String?

    var body: some View {
        NavigationStack {
            if let card = vault.cards.first(where: { $0.id == id }) {
                List {
                    Section {
                        CardFace(card: card, revealed: showNumber)
                            .listRowInsets(EdgeInsets())
                            .listRowBackground(Color.clear)
                    }
                    Section {
                        DetailRow(title: "Card number", value: card.groupedNumber, hidden: card.maskedNumber,
                                  revealed: card.number.isEmpty ? nil : $showNumber) { copy(card.number, "Card number") }
                        if !card.expiryText.isEmpty {
                            DetailRow(title: "Expiry", value: card.expiryText) { copy(card.expiryText, "Expiry") }
                        }
                        if !card.cvv.isEmpty {
                            DetailRow(title: "CVV", value: card.cvv, hidden: "•••", revealed: $showCVV) { copy(card.cvv, "CVV") }
                        }
                        if !card.holder.isEmpty {
                            DetailRow(title: "Name on card", value: card.holder) { copy(card.holder, "Name") }
                        }
                    } footer: {
                        Text("Copied details clear from the clipboard after a minute and don't go to your other devices.")
                    }
                    Section {
                        if !card.bank.isEmpty { LabeledContent("Bank", value: card.bank) }
                        LabeledContent("Type", value: card.kind.rawValue)
                        if card.brand != .unknown { LabeledContent("Network", value: card.brand.name) }
                    }
                    if !card.notes.isEmpty {
                        Section("Notes") { Text(card.notes).textSelection(.enabled) }
                    }
                }
                .navigationTitle(card.title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
                    ToolbarItem(placement: .primaryAction) { Button("Edit") { editing = true } }
                }
                .sheet(isPresented: $editing) { CardEditor(card: card, isNew: false) }
            } else {
                // Deleted from the editor, or locked.
                Color.clear.onAppear { dismiss() }
            }
        }
        .overlay(alignment: .bottom) {
            if let copied {
                Label("\(copied) copied", systemImage: "checkmark.circle.fill")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: copied)
        .sensoryFeedback(.success, trigger: copied) { _, new in new != nil }
        .task(id: copied) {
            guard copied != nil else { return }
            try? await Task.sleep(for: .seconds(2))
            copied = nil
        }
        .modifier(CardPrivacy())
        .tint(SheetTheme.navy)
    }

    private func copy(_ text: String, _ what: String) {
        UIPasteboard.general.setItems([[UTType.plainText.identifier: text]],
                                      options: [.localOnly: true, .expirationDate: Date.now.addingTimeInterval(60)])
        copied = what
        UIAccessibility.post(notification: .announcement, argument: "\(what) copied")
    }
}

/// Title over a value, with show/hide (when it can be hidden) and copy.
private struct DetailRow: View {
    let title: String
    let value: String
    var hidden: String?
    var revealed: Binding<Bool>?
    let onCopy: () -> Void

    var body: some View {
        let showing = revealed?.wrappedValue ?? true
        HStack(spacing: 4) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.caption).foregroundStyle(.secondary)
                Text(showing ? value : (hidden ?? value))
                    .font(.body.monospaced())
                    .privacySensitive()
            }
            .accessibilityElement(children: .combine)
            Spacer()
            if let revealed {
                Button(showing ? "Hide \(title)" : "Show \(title)", systemImage: showing ? "eye.slash" : "eye") {
                    revealed.wrappedValue.toggle()
                }
                .labelStyle(.iconOnly)
                .frame(minWidth: 44, minHeight: 44)
            }
            Button("Copy \(title)", systemImage: "doc.on.doc", action: onCopy)
                .labelStyle(.iconOnly)
                .frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(.borderless)
    }
}

struct CardEditor: View {
    let isNew: Bool
    @State private var vault = CardVault.shared
    @Environment(\.dismiss) private var dismiss
    @State private var card: SavedCard
    /// Up to four digits, MMYY.
    @State private var expiry: String
    @State private var showCVV = false
    @State private var confirmDelete = false

    init(card: SavedCard, isNew: Bool) {
        self.isNew = isNew
        _card = State(initialValue: card)
        _expiry = State(initialValue: card.expiryText.filter(\.isNumber))
    }

    private var number: Binding<String> {
        Binding(get: { CardFormat.grouped(card.number) }, set: { text in
            let digits = text.filter(\.isNumber)
            card.number = String(digits.prefix(CardBrand(number: digits).maxLength))
        })
    }
    private var expiryField: Binding<String> {
        Binding(get: { CardFormat.expiryField(expiry) }, set: { expiry = String($0.filter(\.isNumber).prefix(4)) })
    }
    private var cvv: Binding<String> {
        Binding(get: { card.cvv }, set: { card.cvv = String($0.filter(\.isNumber).prefix(4)) })
    }

    private var expiryValid: Bool { expiry.isEmpty || CardFormat.expiry(expiry) != nil }
    private var looksMistyped: Bool { card.number.count >= 12 && !CardFormat.passesLuhn(card.number) }
    private var canSave: Bool {
        expiryValid && (!card.nickname.trimmingCharacters(in: .whitespaces).isEmpty || card.number.count >= 4)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nickname, e.g. HDFC Millennia", text: $card.nickname)
                    TextField("Bank", text: $card.bank)
                    Picker("Type", selection: $card.kind) {
                        ForEach(SavedCard.Kind.allCases) { Text($0.rawValue).tag($0) }
                    }
                }
                Section {
                    TextField("Card number", text: number)
                        .keyboardType(.numberPad)
                        .textContentType(.creditCardNumber)
                        .font(.body.monospaced())
                    TextField("Name on card", text: $card.holder)
                        .textContentType(.creditCardName)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    HStack {
                        TextField("Expiry MM/YY", text: expiryField)
                            .keyboardType(.numberPad)
                            .textContentType(.creditCardExpiration)
                            .font(.body.monospaced())
                        Divider()
                        Group {
                            if showCVV { TextField("CVV", text: cvv) } else { SecureField("CVV", text: cvv) }
                        }
                        .keyboardType(.numberPad)
                        .textContentType(.creditCardSecurityCode)
                        .font(.body.monospaced())
                        Button(showCVV ? "Hide CVV" : "Show CVV", systemImage: showCVV ? "eye.slash" : "eye") { showCVV.toggle() }
                            .labelStyle(.iconOnly)
                            .frame(minWidth: 44, minHeight: 44)
                            .buttonStyle(.borderless)
                    }
                } header: {
                    Text("On the card")
                } footer: {
                    VStack(alignment: .leading, spacing: 4) {
                        if looksMistyped {
                            Label("This number doesn't look right — check for a typo.", systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(SheetTheme.warn)
                        } else if card.brand != .unknown, !card.number.isEmpty {
                            Text(card.brand.name)
                        }
                        if !expiryValid { Text("Expiry is the month (01–12) and year, e.g. 08/29.").foregroundStyle(SheetTheme.bad) }
                    }
                }
                Section("Colour") {
                    HStack(spacing: 6) {
                        ForEach(SavedCard.Style.allCases) { style in
                            let (from, to) = style.hex
                            Button {
                                card.style = style
                            } label: {
                                Circle()
                                    .fill(LinearGradient(colors: [Color(hex: from), Color(hex: to)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 32, height: 32)
                                    .overlay { if card.style == style { Image(systemName: "checkmark").font(.caption.weight(.bold)).foregroundStyle(.white) } }
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(style.rawValue.capitalized)
                            .accessibilityAddTraits(card.style == style ? .isSelected : [])
                        }
                    }
                }
                Section("Notes") {
                    TextField("Bill date, limit, customer care number…", text: $card.notes, axis: .vertical)
                }
                if !isNew {
                    Section { Button("Delete card", role: .destructive) { confirmDelete = true } }
                }
                if let error = vault.errorMessage {
                    Section { Text(error).foregroundStyle(SheetTheme.bad) }
                }
            }
            .navigationTitle(isNew ? "New card" : card.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.fontWeight(.semibold).disabled(!canSave)
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete \(card.title)?", message: "It's removed from this iPhone. This can't be undone.") {
                vault.delete(id: card.id)
                dismiss()
            })
        }
        .modifier(CardPrivacy())
        .tint(SheetTheme.navy)
        .onAppear { vault.errorMessage = nil }
    }

    private func save() {
        var saved = card
        saved.nickname = card.nickname.trimmingCharacters(in: .whitespaces)
        saved.bank = card.bank.trimmingCharacters(in: .whitespaces)
        saved.holder = card.holder.trimmingCharacters(in: .whitespaces)
        saved.notes = card.notes.trimmingCharacters(in: .whitespacesAndNewlines)
        let parsed = CardFormat.expiry(expiry)
        saved.expiryMonth = parsed?.month
        saved.expiryYear = parsed?.year
        if vault.save(saved) { dismiss() }
    }
}

/// Covers the cards whenever the app isn't in front (the app switcher, the
/// Face ID prompt, Control Center), so they never show in a snapshot.
private struct CardPrivacy: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content.overlay {
            if scenePhase != .active {
                ZStack {
                    SheetTheme.page
                    Image(systemName: "lock.fill").font(.largeTitle).foregroundStyle(.secondary)
                }
                .ignoresSafeArea()
                .accessibilityHidden(true)
            }
        }
    }
}
