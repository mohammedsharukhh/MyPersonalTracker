//
//  PayView.swift
//  MyPersonalTracker
//
//  Pay with UPI: who (scan their QR, a recent payee or a UPI ID), how much,
//  which app — then Pay opens that app. Back here, it asks whether it went
//  through; a paid one is added to Finance. Mirrors PayScreen.kt.
//

import SwiftUI
import AVFoundation
import Vision
import VisionKit

/// Who to pay, filled in when Pay opens (e.g. "Send money" to Mom from the Parents tab).
struct PayPrefill: Equatable {
    var payee: UpiPayee
    var category: String
    var note: String
}

struct PayView: View {
    let store: SheetStore
    /// Opened from the Pay widget: the camera first.
    var startWithScan = false
    var payTo: PayPrefill? = nil
    @Environment(\.dismiss) private var dismiss
    @State private var payments = UpiPayments.shared

    @State private var vpa = ""
    @State private var name = ""
    /// The name a QR code filled in (anyone can print one): said so under it until it's changed.
    @State private var qrName: String?
    @State private var mcc: String?
    @State private var amountText = ""
    @State private var note = ""
    @State private var category = ""
    @State private var app: UpiApp?
    @State private var scanning = false
    @State private var errorText: String?
    /// The app that didn't open: offered in the App Store.
    @State private var notOpened: UpiApp?
    /// The apps this iPhone says it has (checked on appear and on coming back, e.g. from the App Store).
    @State private var found: Set<UpiApp> = []
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @FocusState private var amountFocused: Bool

    /// Every app, always: the ones found on this iPhone first, then the rest, then "Other UPI app".
    /// The rest can still be picked — an app can be there without the iPhone being able to tell.
    private var listed: [UpiApp] { UpiApp.listing(found: found) }
    private var amount: Double? { Double(amountText.replacingOccurrences(of: ",", with: "")).flatMap { $0 > 0 ? $0 : nil } }
    private var payee: UpiPayee? {
        let id = vpa.trimmingCharacters(in: .whitespaces)
        return UpiPayee.isValidVPA(id) ? UpiPayee(vpa: id, name: name.trimmingCharacters(in: .whitespaces), mcc: mcc) : nil
    }
    private var categories: [String] { store.snapshot?.lists.categories ?? [] }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button { scanning = true } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "qrcode.viewfinder").font(.title3.weight(.semibold))
                            Text("Scan their QR code").font(.headline)
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 13)
                        .background(SheetTheme.navy, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                    .listRowSeparator(.hidden)
                    if !payments.recentPayees.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(payments.recentPayees, id: \.vpa) { p in
                                    FilterChip(title: p.label, symbol: "person.crop.circle", tint: SheetTheme.navy, selected: p.vpa == vpa) { choose(p) }
                                }
                            }
                        }
                    }
                    TextField("UPI ID, e.g. shop@okicici", text: $vpa)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.emailAddress)
                    TextField("Name (optional)", text: $name)
                } header: { Text("Pay to") } footer: {
                    if !vpa.isEmpty, payee == nil { Text("That doesn't look like a UPI ID (name@bank).").foregroundStyle(SheetTheme.bad) }
                    else if let qrName, !qrName.isEmpty, name == qrName { Text(UpiSafety.qrName) }
                }

                Section("Amount") {
                    HStack {
                        Text("₹").font(.system(size: 30, weight: .bold, design: .rounded)).foregroundStyle(.secondary)
                        TextField("0", text: $amountText)
                            .keyboardType(.decimalPad).focused($amountFocused)
                            .font(.system(size: 34, weight: .bold, design: .rounded))
                    }
                    if let amount, Money.main != "INR" {
                        if let converted = CurrencyBook.shared.convert(amount, from: "INR", to: Money.main) {
                            Text("Goes into Finance as \(SheetFormat.qar(converted)) \(Money.main)").font(.footnote).foregroundStyle(.secondary)
                        } else {
                            Text("No INR rate yet — set one in the currency converter so it can go into Finance.")
                                .font(.footnote).foregroundStyle(SheetTheme.warn)
                        }
                    }
                    TextField("What for? (optional)", text: $note)
                    if !categories.isEmpty {
                        Picker("Category", selection: $category) {
                            ForEach(categories, id: \.self) { Text($0).tag($0) }
                        }
                    }
                }

                Section {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                        ForEach(listed) { a in
                            let missing = a != .anyApp && !found.contains(a)
                            Button { app = a } label: {
                                VStack(spacing: 6) {
                                    UpiAppLogo(app: a).opacity(missing ? 0.45 : 1)
                                    Text(a.title).font(.caption.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.8)
                                        .foregroundStyle(Color(missing ? .secondaryLabel : .label))
                                    if missing { Text("Not found").font(.caption2).foregroundStyle(.secondary) }
                                }
                                .frame(maxWidth: .infinity).padding(.vertical, 10)
                                .background(app == a ? SheetTheme.navy.opacity(0.12) : Color(.tertiarySystemFill),
                                            in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(app == a ? SheetTheme.navy : .clear, lineWidth: 2))
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(app == a ? .isSelected : [])
                            .accessibilityValue(missing ? "Not found on this iPhone" : "")
                        }
                    }
                    .padding(.vertical, 4)
                } header: { Text("Pay with") } footer: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Your UPI app opens with everything filled in — check it, then enter your PIN there. Then come back: a paid one is added to Finance. “Not found” means this iPhone doesn't seem to have it; you can still try.")
                        Text("\(UpiSafety.pin) \(UpiSafety.iPhoneLinks)").fontWeight(.semibold)
                    }
                }

                Section {
                    Button { Task { await pay() } } label: {
                        Text(amount.map { "Pay ₹\(SheetFormat.number($0, digits: $0.rounded() == $0 ? 0 : 2))" } ?? "Pay")
                            .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent).tint(SheetTheme.good)
                    .disabled(payee == nil || amount == nil || app == nil)
                    .listRowInsets(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                }

                if !payments.payments.isEmpty {
                    Section("Recent payments") {
                        ForEach(payments.payments.prefix(15)) { p in PaymentRow(payment: p) }
                    }
                }
            }
            .navigationTitle("Pay with UPI")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
            .fullScreenCover(isPresented: $scanning) {
                LensScanner { scan in
                    // Straight into Pay, no slide-down.
                    var quiet = Transaction(); quiet.disablesAnimations = true
                    withTransaction(quiet) { scanning = false }
                    choose(scan.payee)
                    qrName = scan.payee.name
                    if let a = scan.amount { amountText = SheetFormat.number(a, digits: a.rounded() == a ? 0 : 2).replacingOccurrences(of: ",", with: "") }
                    if let n = scan.note, note.isEmpty { note = n }
                    if scan.amount == nil { amountFocused = true }
                }
            }
            .popup(isPresented: Binding(get: { errorText != nil }, set: { if !$0 { errorText = nil } }), .error("Couldn't open the app", message: errorText))
            .popup(isPresented: Binding(get: { notOpened != nil }, set: { if !$0 { notOpened = nil } }),
                   Popup(title: "\(notOpened?.title ?? "The app") didn't open",
                         message: "It doesn't seem to be on this iPhone. Get it from the App Store, or pick another app.",
                         symbol: "arrow.down.app", confirmTitle: "App Store", cancelTitle: "Not now",
                         onConfirm: { if let a = notOpened { openAppStore(for: a) } }))
            .onChange(of: scenePhase) { _, phase in if phase == .active { checkInstalled() } }
            .task {
                // From the widget: the camera, once this screen has finished sliding in.
                guard startWithScan, vpa.isEmpty else { return }
                try? await Task.sleep(for: .milliseconds(400))
                scanning = true
            }
            .onAppear {
                checkInstalled()
                if app == nil { app = listed.first { found.contains($0) } ?? .anyApp }
                if category.isEmpty { category = categories.first { $0 == "Other" } ?? categories.last ?? "" }
                if let payTo, vpa.isEmpty {
                    vpa = payTo.payee.vpa
                    name = payTo.payee.name
                    if categories.contains(payTo.category) { category = payTo.category }
                    note = payTo.note
                    amountFocused = true
                }
            }
        }
        .tint(SheetTheme.navy)
    }

    private func choose(_ p: UpiPayee) {
        qrName = nil
        vpa = p.vpa
        name = p.name
        mcc = p.mcc
        if let last = payments.lastCategory(for: p.vpa) { category = last }
        else if let guess = CategoryGuess.guess(p.name, among: categories) { category = guess }
        else if let mcc = p.mcc, let byCode = Self.category(forMCC: mcc, among: categories) { category = byCode }
    }

    private func pay() async {
        guard let payee, let amount, let app else { return }
        let request = UpiRequest(payee: payee, amount: (amount * 100).rounded() / 100, note: note.trimmingCharacters(in: .whitespaces),
                                 reference: "MPT" + String(Int(Date.now.timeIntervalSince1970 * 1000)))
        if await payments.pay(request, category: category, with: app) {
            amountText = ""; note = ""
        } else if app == .anyApp {
            errorText = "No UPI app answered. Install PhonePe, Google Pay, Paytm or another UPI app, then try again."
        } else {
            notOpened = app
        }
    }

    private func checkInstalled() {
        found = Set(UpiApp.allCases.filter { $0 != .anyApp && $0.isInstalled })
    }

    /// The App Store, searching for the app by name.
    private func openAppStore(for app: UpiApp) {
        let term = app.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        guard let url = URL(string: "itms-apps://search.itunes.apple.com/WebObjects/MZSearch.woa/wa/search?media=software&term=\(term)") else { return }
        openURL(url)
    }

    /// A few common merchant codes on shop QRs.
    static func category(forMCC mcc: String, among categories: [String]) -> String? {
        let wanted: String? = switch mcc {
        case "5411", "5422", "5499": "Groceries"
        case "5812", "5813", "5814": "Food & Dining"
        case "5541", "5542", "4121", "4111": "Transport"
        case "5912": "Health & Pharmacy"
        case "4814", "4899": "Mobile & Internet"
        case "5651", "5691", "5699": "Clothing"
        default: nil
        }
        return wanted.flatMap { w in categories.first { $0 == w } }
    }
}

/// A payment in the list: status, amount, and for one still waiting, Paid / Failed.
struct PaymentRow: View {
    let payment: UpiPayment

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: icon).font(.title3).foregroundStyle(tint)
                VStack(alignment: .leading, spacing: 1) {
                    Text(payment.payee.label).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text("\(payment.date.formatted(date: .abbreviated, time: .shortened)) · \(payment.app.title) · \(statusText)")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Text("₹" + SheetFormat.number(payment.amount, digits: payment.amount.rounded() == payment.amount ? 0 : 2))
                    .font(.subheadline.weight(.bold).monospacedDigit())
            }
            if payment.status == .waiting {
                HStack(spacing: 8) {
                    Button { UpiPayments.shared.markPaid(payment.id) } label: { Label("Paid", systemImage: "checkmark").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).tint(SheetTheme.good)
                    Button { UpiPayments.shared.markFailed(payment.id) } label: { Label("Failed", systemImage: "xmark").frame(maxWidth: .infinity) }
                        .buttonStyle(.bordered).tint(SheetTheme.bad)
                }
                .font(.subheadline.weight(.semibold))
            }
        }
        .swipeActions { Button("Remove", systemImage: "trash", role: .destructive) { UpiPayments.shared.delete(payment.id) } }
    }

    private var icon: String {
        switch payment.status {
        case .paid: "checkmark.circle.fill"
        case .failed: "xmark.circle.fill"
        case .waiting: "clock.fill"
        }
    }

    private var tint: Color {
        switch payment.status {
        case .paid: SheetTheme.good
        case .failed: SheetTheme.bad
        case .waiting: SheetTheme.warn
        }
    }

    private var statusText: String {
        switch payment.status {
        case .paid: "Paid"
        case .failed: "Failed"
        case .waiting: "Did it go through?"
        }
    }
}

/// Back from the UPI app: "Did you pay?"
struct PaymentQuestion: ViewModifier {
    @State private var payments = UpiPayments.shared

    func body(content: Content) -> some View {
        content.popup(isPresented: Binding(get: { payments.asking != nil }, set: { if !$0 { payments.asking = nil } }),
                      Popup(title: "Did the payment go through?",
                            message: payments.asking.map { "₹\(SheetFormat.number($0.amount, digits: $0.amount.rounded() == $0.amount ? 0 : 2)) to \($0.payee.label) with \($0.app.title)." },
                            symbol: "indianrupeesign.circle.fill", style: .standard, confirmTitle: "Yes, paid", cancelTitle: "No, it failed",
                            onConfirm: { if let id = payments.asking?.id { payments.markPaid(id) } },
                            onCancel: { if let id = payments.asking?.id { payments.markFailed(id) } }))
    }
}

/// The app's tile from Assets ▸ UPI ("upi-phonePe", "upi-googlePay"…: its colour and initials, made by the
/// project; swap in a company's own logo from its brand kit under the same name), else one drawn here.
struct UpiAppLogo: View {
    let app: UpiApp

    var body: some View {
        Group {
            if let logo = UIImage(named: "upi-" + app.rawValue) {
                Image(uiImage: logo)
                    .resizable().scaledToFit()
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            } else {
                Text(app.monogram).font(.system(size: app == .anyApp ? 13 : 18, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(app.tint, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .accessibilityHidden(true)   // The app's name is right under it.
    }
}

/// Pay with UPI, raised in the middle of the tab bar: tap to scan a QR code,
/// touch and hold to type a UPI ID. A badge counts payments still to confirm.
struct UpiTabButton: View {
    @State private var payments = UpiPayments.shared
    @State private var taps = 0

    var body: some View {
        let waiting = payments.waiting.count
        Button { open(scan: true) } label: {
            // Sits in the bar like a tab, in the app's action colour: the one thing in the bar that does rather than goes.
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 21, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(SheetTheme.navy, in: Circle())
            .overlay(alignment: .topTrailing) {
                if waiting > 0 {
                    Text("\(waiting)").font(.caption2.weight(.bold)).monospacedDigit()
                        .foregroundStyle(.black)
                        .frame(minWidth: 18, minHeight: 18)
                        .background(Color(red: 1, green: 0.78, blue: 0.35), in: Capsule())
                        .offset(x: 2, y: -2)
                }
            }
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.impact(weight: .medium), trigger: taps)
        .contextMenu {
            Button("Scan a QR code", systemImage: "qrcode.viewfinder") { open(scan: true) }
            Button("Pay to a UPI ID", systemImage: "at") { open(scan: false) }
        }
        .accessibilityLabel("Pay with UPI")
        .accessibilityValue(waiting > 0 ? "\(waiting) payment\(waiting == 1 ? "" : "s") to confirm" : "")
        .accessibilityHint("Opens the QR scanner. Touch and hold to type a UPI ID instead.")
    }

    private func open(scan: Bool) {
        taps += 1
        AppNavigation.shared.payWithScan = scan
        AppNavigation.shared.showPay = true
    }
}
