//
//  ParentsView.swift
//  MyPersonalTracker
//
//  Mom & Dad: what they asked you to do, on the Dashboard and as its own tab
//  (the card and a tapped reminder open it). Each request reminds you at its
//  time until it's ticked off, can carry photos (the prescription, the bill)
//  and a cost for Finance, and is a tap away from calling or messaging them.
//

import PhotosUI
import SwiftUI
import UIKit
import UserNotifications

/// On the Dashboard: what's waiting, a tick to mark it done, + to add one.
struct ParentsCard: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    let onOpen: () -> Void
    @State private var adding: ParentAsk?

    var body: some View {
        let open = snapshot.openParentAsks
        let late = open.filter { $0.isLate() }.count
        SheetCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    Button(action: onOpen) {
                        HStack(spacing: 12) {
                            IconBadge(symbol: "figure.2.and.child.holdinghands", tint: late > 0 ? SheetTheme.bad : SheetTheme.purple, size: 44)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Mom & Dad asked").font(.headline).foregroundStyle(Color(.label))
                                Text(open.isEmpty ? "Nothing waiting" : late > 0 ? "\(late) late · \(open.count) to do" : "\(open.count) to do")
                                    .font(.caption).foregroundStyle(late > 0 ? SheetTheme.bad : .secondary)
                            }
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button { adding = ParentAsk() } label: {
                        Image(systemName: "plus.circle.fill").font(.title2).frame(minWidth: 44, minHeight: 44)
                    }
                    .foregroundStyle(SheetTheme.navy)
                    .accessibilityLabel("Add something Mom or Dad asked for")
                }
                if open.isEmpty {
                    Text("When Mom or Dad asks you to do something, add it here. You'll get a reminder until it's done.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                let low = snapshot.medicineList.filter { $0.runsLow() }
                let soon = snapshot.parentDateList.first { ($0.daysUntil() ?? 99) <= 7 }
                if !low.isEmpty || soon != nil {
                    Button(action: onOpen) {
                        // One line each, with a symbol rather than an emoji in front.
                        VStack(alignment: .leading, spacing: 4) {
                            if !low.isEmpty {
                                Label("\(low.count == 1 ? low[0].name : "\(low.count) medicines") to refill", systemImage: "pills.fill")
                            }
                            if let soon {
                                Label("\(soon.title) \(soon.when().lowercased())", systemImage: soon.kind.symbol)
                            }
                        }
                        .font(.caption.weight(.semibold)).foregroundStyle(SheetTheme.warn).multilineTextAlignment(.leading)
                    }
                    .buttonStyle(.plain)
                }
                ForEach(open.prefix(3)) { a in ParentAskRow(store: store, ask: a, onEdit: onOpen) }
                if open.count > 3 {
                    Button("See all \(open.count)", action: onOpen).font(.subheadline.weight(.semibold)).foregroundStyle(SheetTheme.navy)
                }
            }
        }
        .sheet(item: $adding) { ParentAskEditor(store: store, ask: $0, isNew: true) }
    }
}

/// One request: a tick to mark it done, then what, who and when. Touch and hold to call or message them.
struct ParentAskRow: View {
    let store: SheetStore
    let ask: ParentAsk
    var onDone: ((ParentAsk) -> Void)? = nil
    let onEdit: () -> Void
    @State private var ticks = 0

    var body: some View {
        let late = ask.isLate()
        HStack(spacing: 8) {
            Button {
                ticks += 1
                Task {
                    if ask.isOpen {
                        await ParentActions.complete(ask, store: store)
                        onDone?(ask.markedDone())
                    } else {
                        await ParentActions.reopen(ask, store: store)
                    }
                }
            } label: {
                Image(systemName: ask.isOpen ? "circle" : "checkmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(ask.isOpen ? (late ? SheetTheme.bad : SheetTheme.navy) : SheetTheme.good)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .sensoryFeedback(.success, trigger: ticks)
            .accessibilityLabel(ask.isOpen ? "Mark done" : "Mark not done")
            .accessibilityValue(ask.task)

            Button(action: onEdit) {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ask.task).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                            .strikethrough(!ask.isOpen).multilineTextAlignment(.leading)
                        Text(ParentText.detail(ask)).font(.caption).foregroundStyle(late ? SheetTheme.bad : .secondary)
                    }
                    Spacer(minLength: 0)
                    if !ask.photos.isEmpty {
                        Label("\(ask.photos.count)", systemImage: "photo").font(.caption2).foregroundStyle(.secondary)
                            .accessibilityLabel("\(ask.photos.count) photo\(ask.photos.count == 1 ? "" : "s")")
                    }
                    if let cost = ask.cost, cost > 0 {
                        Text(SheetFormat.qar(cost)).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    }
                    if ask.pending {
                        Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky)
                            .accessibilityLabel("Not synced yet")
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .contextMenu { ParentContactButtons(ask: ask) }
    }
}

/// Call, WhatsApp or text whoever asked — about it, or to say it's done.
struct ParentContactButtons: View {
    let ask: ParentAsk

    var body: some View {
        let people = ParentContacts.people(for: ask.who).filter { ParentContacts.phone($0) != nil }
        if people.isEmpty {
            Text("Add Mom's and Dad's numbers in the Parents tab (phone button) to call or message them from here.")
        }
        ForEach(people) { who in
            if let phone = ParentContacts.phone(who) {
                let text = ParentContacts.message(ask, to: who)
                Section(who.rawValue) {
                    Button("Call \(who.rawValue)", systemImage: "phone") { ParentLinks.shared.call(who, phone) }
                    Button("WhatsApp \(who.rawValue)", systemImage: "message.badge") { ParentLinks.shared.whatsApp(who, phone, text: text) }
                    Button("Text \(who.rawValue)", systemImage: "message") { ParentLinks.shared.text(phone, text: text) }
                }
            }
        }
    }
}

/// Opens a call, WhatsApp or a text — and says so when this phone can't (no SIM, the
/// simulator, WhatsApp not installed) instead of doing nothing.
@MainActor @Observable
final class ParentLinks {
    static let shared = ParentLinks()
    /// Shown as an alert by the Parents tab and the editor.
    var failure: String?
    /// "Send" before their UPI ID is saved: the Parents tab asks for it, then opens Pay.
    var askingUpi: ParentAsk.Who?

    /// A call or WhatsApp from here counts as talking to them (for the "call them" nudge).
    func call(_ who: ParentAsk.Who, _ phone: String) {
        open(ParentContacts.callURL(phone), "This phone can't make calls. Try it on your iPhone with a SIM.") { ParentTalk.talked(who) }
    }
    func whatsApp(_ who: ParentAsk.Who, _ phone: String, text: String) {
        open(ParentContacts.whatsAppURL(phone, text: text), "Couldn't open WhatsApp.") { ParentTalk.talked(who) }
    }
    func text(_ phone: String, text: String) {
        open(ParentContacts.smsURL(phone, text: text), "This phone can't send texts. Try it on your iPhone.")
    }

    private func open(_ url: URL?, _ otherwise: String, then opened: @escaping @MainActor () -> Void = {}) {
        guard let url else { failure = "That number doesn't look right — check it with the phone button in the Parents tab."; return }
        UIApplication.shared.open(url) { [weak self] ok in
            Task { @MainActor in
                if ok { opened(); await ReminderScheduler.scheduleSheetAlerts() } else { self?.failure = otherwise }
            }
        }
    }
}

/// Mom or Dad at the top of the Parents tab: who, when you last talked, and round
/// Call · WhatsApp · Send ₹ buttons. Without a number, Call and WhatsApp ask for it.
struct ParentPersonRow: View {
    let who: ParentAsk.Who
    let onAddNumber: () -> Void

    var body: some View {
        let phone = ParentContacts.phone(who)
        let last = ParentTalk.last(who)
        let since = ParentTalk.daysSince(last, on: .now)
        let due = ParentTalk.every(who) > 0 && (since ?? .max) >= ParentTalk.every(who)
        HStack(alignment: .center, spacing: 10) {
            Text(String(who.rawValue.prefix(1)))
                .font(.headline).foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(who == .mom ? SheetTheme.purple : SheetTheme.indigo, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(who.rawValue).font(.headline)
                Text(since == 0 ? "Talked today ✓" : (ParentTalk.text(last) ?? "Not noted yet"))
                    .font(.caption).foregroundStyle(due ? SheetTheme.warn : .secondary)
                    .lineLimit(1).minimumScaleFactor(0.85)
                if since != 0 {
                    Button("Mark as talked") {
                        ParentTalk.talked(who)
                        Task { await ReminderScheduler.scheduleSheetAlerts() }
                    }
                    .font(.caption.weight(.semibold))
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Mark talked to \(who.rawValue) today")
                }
            }
            .layoutPriority(1)
            Spacer(minLength: 0)
            round("phone.fill", SheetTheme.good, "Call \(who.rawValue)") {
                if let phone { ParentLinks.shared.call(who, phone) } else { onAddNumber() }
            }
            round("message.fill", Color(red: 0.15, green: 0.68, blue: 0.38), "WhatsApp \(who.rawValue)") {
                if let phone { ParentLinks.shared.whatsApp(who, phone, text: "Hi \(who.rawValue), ") } else { onAddNumber() }
            }
            round("indianrupeesign", SheetTheme.teal, "Send money to \(who.rawValue)") { ParentActions.sendMoney(to: who) }
        }
        .padding(.vertical, 2)
    }

    private func round(_ symbol: String, _ tint: Color, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 16, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 40, height: 40)
                .background(tint.opacity(0.15), in: Circle())
                .frame(width: 42, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(label)
    }
}

/// Mom or Dad with Call · WhatsApp · Text (a request's editor, after ticking one off).
struct ParentContactStrip: View {
    let who: ParentAsk.Who
    let phone: String
    /// What WhatsApp and Text start with.
    let text: String

    var body: some View {
        HStack(spacing: 8) {
            Text(who.rawValue).font(.subheadline.weight(.semibold)).frame(minWidth: 36, alignment: .leading)
            Spacer(minLength: 4)
            pills
        }
    }

    private var pills: some View {
        HStack(spacing: 8) {
            pill("Call", "phone.fill", SheetTheme.good) { ParentLinks.shared.call(who, phone) }
                .accessibilityLabel("Call \(who.rawValue)")
            pill("WhatsApp", "message.fill", Color(red: 0.15, green: 0.68, blue: 0.38)) { ParentLinks.shared.whatsApp(who, phone, text: text) }
                .accessibilityLabel("WhatsApp \(who.rawValue)")
            pill("Text", "bubble.left.fill", SheetTheme.sky) { ParentLinks.shared.text(phone, text: text) }
                .accessibilityLabel("Text \(who.rawValue)")
        }
    }

    private func pill(_ title: String, _ symbol: String, _ tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: symbol).font(.caption)
                Text(title).font(.caption.weight(.semibold)).lineLimit(1).fixedSize()
            }
                .padding(.horizontal, 10).frame(minHeight: 36)
                .foregroundStyle(tint)
                .background(tint.opacity(0.13), in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.borderless)
    }
}

/// Ticking off and putting back, from a row, the editor or a reminder's Done button.
enum ParentActions {
    /// Opens Pay with their UPI ID filled in; a paid one goes to Finance as Remittance (so it counts as sent home).
    @MainActor
    static func sendMoney(to who: ParentAsk.Who) {
        guard let upi = ParentContacts.upi(who) else { ParentLinks.shared.askingUpi = who; return }
        let nav = AppNavigation.shared
        nav.payWithScan = false
        nav.payTo = PayPrefill(payee: UpiPayee(vpa: upi, name: who.rawValue, mcc: nil), category: "Remittance", note: "For \(who.rawValue)")
        nav.showPay = true
    }

    /// Done: a repeating one moves to its next date; its cost goes to Finance if asked.
    @MainActor
    static func complete(_ a: ParentAsk, store: SheetStore) async {
        store.saveParentAsk(a.markedDone())
        if a.finance, let cost = a.cost, cost > 0 {
            let lists = store.snapshot?.lists
            let category = (lists?.categories ?? []).contains("Gifts & Family") ? "Gifts & Family" : (lists?.categories.last ?? "Other")
            _ = await store.saveExpense(row: nil, date: SheetDate.today, description: "\(a.who.label): \(a.task)", category: category,
                                        method: lists?.methods.first ?? "Cash", qar: cost, notes: ParentReminders.financeNote)
        }
        await ReminderScheduler.scheduleSheetAlerts()
    }

    @MainActor
    static func reopen(_ a: ParentAsk, store: SheetStore) async {
        var copy = a
        copy.done = nil
        store.saveParentAsk(copy)
        await ReminderScheduler.scheduleSheetAlerts()
    }
}

/// The Parents tab: everything they asked for — late, today, coming up, then what's done.
struct ParentsView: View {
    let store: SheetStore
    private enum Page: String, CaseIterable {
        case requests = "To do", medicines = "Medicines", dates = "Dates", trip = "Trip home", health = "Health"
        var symbol: String {
            switch self {
            case .requests: "checklist"
            case .medicines: "pills.fill"
            case .dates: "gift.fill"
            case .trip: "airplane"
            case .health: "heart.text.square.fill"
            }
        }
    }
    @State private var page = Page.requests
    @State private var editing: ParentAsk?
    @State private var editingMedicine: ParentMedicine?
    @State private var refilling: ParentMedicine?
    @State private var editingDate: ParentDate?
    @State private var editingTakeHome: TakeHomeItem?
    @State private var editingReading: ParentReading?
    @State private var who: ParentAsk.Who?
    @State private var editingContacts = false
    /// Just ticked off: offer to tell them.
    @State private var justDone: ParentAsk?
    @State private var links = ParentLinks.shared
    @AppStorage(ParentContacts.momKey) private var momPhone = ""
    @AppStorage(ParentContacts.dadKey) private var dadPhone = ""
    @AppStorage(ParentTalk.lastKey(.mom)) private var momTalk = ""
    @AppStorage(ParentTalk.lastKey(.dad)) private var dadTalk = ""
    @AppStorage(ParentContacts.momUpiKey) private var momUpi = ""
    @AppStorage(ParentContacts.dadUpiKey) private var dadUpi = ""

    var body: some View {
        NavigationStack {
            List {
                if let s = store.snapshot {
                    content(s)
                } else if store.needsConnection {
                    NotConnectedHint()
                } else {
                    SheetLoadingView(progress: store.loadProgress).padding(.vertical, 40).listRowBackground(Color.clear)
                }
            }
            .scrollContentBackground(.hidden)
            .containerBackground(SheetTheme.pageWash, for: .navigation)
            .navigationTitle("Mom & Dad")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) { MoreButton(); SettingsButton() }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editingContacts = true } label: { Image(systemName: "phone") }
                        .accessibilityLabel("Mom's and Dad's numbers")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if let s = store.snapshot {
                        ShareLink(item: ParentShare.text(s, who: who)) { Image(systemName: "square.and.arrow.up") }
                            .accessibilityLabel("Share what's still to do")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { add() } label: { Image(systemName: "plus") }
                        .accessibilityLabel(page == .requests ? "Add something they asked for" : page == .medicines ? "Add a medicine"
                                            : page == .dates ? "Add a date" : page == .trip ? "Add something to take home" : "Add a reading")
                        .disabled(store.snapshot == nil)
                }
            }
            .sheet(item: $editing) { a in
                ParentAskEditor(store: store, ask: a, isNew: !(store.snapshot?.parentAsks ?? []).contains { $0.id == a.id })
            }
            .sheet(item: $editingMedicine) { m in
                ParentMedicineEditor(store: store, medicine: m, isNew: !(store.snapshot?.parentMedicines ?? []).contains { $0.id == m.id })
            }
            .sheet(item: $refilling) { ParentRefillSheet(store: store, medicine: $0) }
            .sheet(item: $editingDate) { d in
                ParentDateEditor(store: store, date: d, isNew: !(store.snapshot?.parentDates ?? []).contains { $0.id == d.id })
            }
            .sheet(item: $editingTakeHome) { TakeHomeItemEditor(store: store, item: $0) }
            .sheet(item: $editingReading) { ParentReadingEditor(store: store, reading: $0, isNew: true) }
            .sheet(isPresented: $editingContacts) { ParentContactsEditor() }
            .safeAreaInset(edge: .bottom) {
                if let done = justDone { tellBanner(done).transition(.move(edge: .bottom).combined(with: .opacity)) }
            }
            .animation(.snappy, value: justDone?.id)
            .task(id: justDone?.id) {
                guard justDone != nil else { return }
                try? await Task.sleep(for: .seconds(10))
                justDone = nil
            }
            .alert("Couldn't open that", isPresented: Binding(get: { links.failure != nil }, set: { if !$0 { links.failure = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(links.failure ?? "") }
            .sheet(item: Binding(get: { links.askingUpi.map(UpiAsk.init) }, set: { links.askingUpi = $0?.who })) { ask in
                ParentUpiPrompt(who: ask.who)
            }
            #if DEBUG
            // `-parentEdit p4` opens that request's editor (screenshots).
            .task {
                if let id = UserDefaults.standard.string(forKey: "parentEdit") { editing = store.snapshot?.parentAsks.first { $0.id == id } }
                if let raw = UserDefaults.standard.string(forKey: "parentPage"), let p = Page(rawValue: raw) { page = p }
            }
            #endif
        }
        .tint(SheetTheme.navy)
    }

    private func add() {
        switch page {
        case .requests: editing = ParentAsk(who: who ?? .mom)
        case .medicines: editingMedicine = ParentMedicine(who: who ?? .dad)
        case .dates: editingDate = ParentDate(who: who ?? .mom)
        case .trip: editingTakeHome = TakeHomeItem(who: who ?? .both)
        case .health: editingReading = ParentReading(who: healthWho)
        }
    }

    /// Readings are one person's: Mom when filtered to Mom, else Dad.
    private var healthWho: ParentAsk.Who { who == .mom ? .mom : .dad }

    @ViewBuilder
    private func content(_ s: SheetSnapshot) -> some View {
        if let error = s.food?.photoError, s.parentAsks.contains(where: { !$0.photos.isEmpty }) {
            Section { Label(error, systemImage: "exclamationmark.triangle").font(.footnote).foregroundStyle(SheetTheme.warn) }
        }
        contactsSection
        let money = s.parentMoney()
        if money.total > 0 {
            Section {
                ParentMoneyCard(money: money)
            } footer: {
                Text("Send money with ₹ next to Mom or Dad — it's logged as Remittance, so it counts as sent home.")
            }
        }
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Page.allCases, id: \.self) { p in
                        FilterChip(title: p.rawValue, symbol: p.symbol, tint: SheetTheme.navy, selected: page == p) {
                            withAnimation(.snappy) { page = p }
                        }
                    }
                }
            }
            Picker("For", selection: $who) {
                Text("Everyone").tag(ParentAsk.Who?.none)
                Text("Mom").tag(ParentAsk.Who?.some(.mom))
                Text("Dad").tag(ParentAsk.Who?.some(.dad))
            }
            .pickerStyle(.segmented)
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
        .listRowSeparator(.hidden)
        switch page {
        case .requests: requests(s)
        case .medicines: medicines(s)
        case .dates: dates(s)
        case .trip: TakeHomeSections(store: store, snapshot: s, who: who)
        case .health: ParentHealthSections(store: store, snapshot: s, who: healthWho)
        }
    }

    private func forWho(_ w: ParentAsk.Who) -> Bool { who == nil || w == who || w == .both }

    @ViewBuilder
    private func medicines(_ s: SheetSnapshot) -> some View {
        let list = s.medicineList.filter { forWho($0.who) }
        if list.isEmpty {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Never let their tablets run out").font(.headline)
                    Text("Add what Mom and Dad take, how many a day and how many are left. It counts down by itself and the daily Mom & Dad notification warns you a week before they run out.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Button { add() } label: { Label("Add a medicine", systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold)) }
                        .padding(.top, 4)
                }
                .padding(.vertical, 4)
            }
        } else {
            let low = list.filter { $0.runsLow() }
            if !low.isEmpty {
                Section("Refill soon") { ForEach(low) { medicineRow($0) } }
            }
            let rest = list.filter { !$0.runsLow() }
            if !rest.isEmpty {
                Section(low.isEmpty ? "Medicines" : "Others") { ForEach(rest) { medicineRow($0) } }
            }
        }
    }

    private func medicineRow(_ m: ParentMedicine) -> some View {
        ParentMedicineRow(medicine: m, onEdit: { editingMedicine = m }, onRefill: { refilling = m })
    }

    @ViewBuilder
    private func dates(_ s: SheetSnapshot) -> some View {
        let list = s.parentDateList.filter { forWho($0.who) }
        if list.isEmpty {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Their days, every year").font(.headline)
                    Text("Birthdays, their anniversary, a grandparent's remembrance day. You're reminded a week and a day before, and on the day.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Button { add() } label: { Label("Add a date", systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold)) }
                        .padding(.top, 4)
                }
                .padding(.vertical, 4)
            }
        } else {
            let soon = list.filter { ($0.daysUntil() ?? 999) <= 30 }
            if !soon.isEmpty { Section("Next 30 days") { ForEach(soon) { d in ParentDateRow(date: d) { editingDate = d } } } }
            let later = list.filter { ($0.daysUntil() ?? 999) > 30 }
            if !later.isEmpty { Section("Later") { ForEach(later) { d in ParentDateRow(date: d) { editingDate = d } } } }
        }
    }

    @ViewBuilder
    private func requests(_ s: SheetSnapshot) -> some View {
        let match = { (a: ParentAsk) in forWho(a.who) }
        let open = s.openParentAsks.filter(match)
        let today = SheetDate.today
        if s.parentAsks.isEmpty {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Never forget what they asked").font(.headline)
                    Text("Book Dad's eye check-up, send Mom the photos, refill the tablets every month… Add it when they ask, pick a day and a time, and you'll get a reminder every day until you tick it off. Snap the prescription or the bill with it.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Button { editing = ParentAsk() } label: { Label("Add the first one", systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold)) }
                        .padding(.top, 4)
                }
                .padding(.vertical, 4)
            }
        }
        group("Late", open.filter { $0.date < today })
        group("Today", open.filter { $0.date == today })
        group("Coming up", open.filter { $0.date > today })
        let done = s.doneParentAsks.filter(match)
        if !done.isEmpty {
            Section {
                ForEach(done.prefix(30)) { row($0) }
            } header: {
                Text("Done")
            } footer: {
                Text("What you've already sorted for them. Tick one again to put it back on the list; touch and hold one to tell them it's done.")
            }
        }
    }

    @ViewBuilder
    private func group(_ title: String, _ asks: [ParentAsk]) -> some View {
        if !asks.isEmpty {
            Section(title) { ForEach(asks) { row($0) } }
        }
    }

    private func row(_ a: ParentAsk) -> some View {
        ParentAskRow(store: store, ask: a, onDone: { done in
            if ParentContacts.people(for: done.who).contains(where: { ParentContacts.phone($0) != nil }) { justDone = done }
        }) { editing = a }
            .listRowInsets(EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 16))
    }

    /// Mom and Dad, a tap from a call or a message — or a prompt to add their numbers.
    @ViewBuilder
    private var contactsSection: some View {
        let _ = (momPhone, dadPhone, momTalk, dadTalk, momUpi, dadUpi)   // redraw when a number, UPI ID or talk changes
        Section {
            ForEach([ParentAsk.Who.mom, .dad], id: \.self) { who in
                ParentPersonRow(who: who) { editingContacts = true }
            }
        }
    }

    /// "Done ✓ — tell Dad?" for a few seconds after ticking one off.
    private func tellBanner(_ done: ParentAsk) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("Done — tell \(done.who.label)?", systemImage: "checkmark.circle.fill")
                    .font(.subheadline.weight(.semibold)).foregroundStyle(SheetTheme.good)
                Spacer()
                Button { justDone = nil } label: { Image(systemName: "xmark").font(.caption.weight(.bold)).frame(width: 44, height: 32) }
                    .buttonStyle(.plain).foregroundStyle(.secondary).accessibilityLabel("Close")
            }
            ForEach(ParentContacts.people(for: done.who), id: \.self) { w in
                if let phone = ParentContacts.phone(w) {
                    ParentContactStrip(who: w, phone: phone, text: ParentContacts.message(done, to: w))
                }
            }
        }
        .padding(14)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

private struct UpiAsk: Identifiable {
    let who: ParentAsk.Who
    var id: String { who.rawValue }
}

/// "Send" before their UPI ID is saved: ask for it once, then straight on to Pay.
struct ParentUpiPrompt: View {
    let who: ParentAsk.Who
    @Environment(\.dismiss) private var dismiss
    @State private var id = ""

    private var valid: Bool { UpiPayee.isValidVPA(id.trimmingCharacters(in: .whitespaces)) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("\(who.rawValue)'s UPI ID, e.g. name@okicici", text: $id)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.emailAddress)
                    if !id.isEmpty, !valid { Text("That doesn't look like a UPI ID (name@bank).").font(.caption).foregroundStyle(SheetTheme.bad) }
                } footer: {
                    Text("Ask \(who.rawValue), or find it in their UPI app (PhonePe, Google Pay, Paytm…). It's saved on this phone, so next time Send goes straight to Pay. Change it with the phone button.")
                }
            }
            .navigationTitle("Send money to \(who.rawValue)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Next") {
                        UserDefaults.standard.set(id.trimmingCharacters(in: .whitespaces), forKey: who == .dad ? ParentContacts.dadUpiKey : ParentContacts.momUpiKey)
                        dismiss()
                        Task { try? await Task.sleep(for: .milliseconds(450)); ParentActions.sendMoney(to: who) }
                    }
                    .fontWeight(.semibold)
                    .disabled(!valid)
                }
            }
        }
        .presentationDetents([.medium])
        .tint(SheetTheme.navy)
    }
}

/// Mom's and Dad's numbers, kept on this phone only.
struct ParentContactsEditor: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage(ParentContacts.momKey) private var mom = ""
    @AppStorage(ParentContacts.dadKey) private var dad = ""
    @AppStorage(ParentContacts.momUpiKey) private var momUpi = ""
    @AppStorage(ParentContacts.dadUpiKey) private var dadUpi = ""
    @AppStorage(ParentTalk.everyKey(.mom)) private var momEvery = 0
    @AppStorage(ParentTalk.everyKey(.dad)) private var dadEvery = 0
    @AppStorage(ParentDigest.minutesKey) private var minutes = ParentDigest.defaultMinutes

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Mom's number", text: $mom).keyboardType(.phonePad).textContentType(.telephoneNumber)
                    TextField("Dad's number", text: $dad).keyboardType(.phonePad).textContentType(.telephoneNumber)
                } header: { Text("Numbers") } footer: {
                    Text("With the country code, e.g. +91 98765 43210. They stay on this phone — they aren't added to your sheet.")
                }
                Section {
                    upiField("Mom's UPI ID", $momUpi)
                    upiField("Dad's UPI ID", $dadUpi)
                } header: { Text("To send them money") } footer: {
                    Text("e.g. name@okicici — ask them, or find it in their UPI app. Then Send in the Parents tab opens Pay with UPI with it filled in.")
                }
                Section {
                    every("Call Mom", $momEvery)
                    every("Call Dad", $dadEvery)
                } header: { Text("Remind me to call") } footer: {
                    Text("A call or WhatsApp from the Parents tab counts, and so does Talked ✓ for calls made from elsewhere.")
                }
                Section {
                    DatePicker("Daily Mom & Dad reminder", selection: Binding(
                        get: { Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: .now) ?? .now },
                        set: { let c = Calendar.current.dateComponents([.hour, .minute], from: $0); minutes = (c.hour ?? 18) * 60 + (c.minute ?? 0) }),
                               displayedComponents: .hourAndMinute)
                } footer: {
                    Text("One notification a day when there's something: medicines running low, their birthday or anniversary coming up, a call that's due.")
                }
            }
            .navigationTitle("Calls & reminders")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .onDisappear { Task { await ReminderScheduler.scheduleSheetAlerts() } }
        }
        .tint(SheetTheme.navy)
    }

    private func upiField(_ title: String, _ value: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            TextField(title, text: value).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.emailAddress)
            let text = value.wrappedValue.trimmingCharacters(in: .whitespaces)
            if !text.isEmpty, !UpiPayee.isValidVPA(text) {
                Text("That doesn't look like a UPI ID (name@bank).").font(.caption).foregroundStyle(SheetTheme.bad)
            }
        }
    }

    private func every(_ title: String, _ value: Binding<Int>) -> some View {
        Picker(title, selection: value) {
            Text("Off").tag(0)
            Text("Every day").tag(1)
            ForEach([2, 3, 5, 7, 14], id: \.self) { Text("Every \($0) days").tag($0) }
        }
    }
}

struct ParentAskEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var a: ParentAsk
    @State private var day: Date
    @State private var remind: Bool
    @State private var time: Date
    @State private var confirmDelete = false
    @State private var notificationsOff = false
    @State private var takingPhoto = false
    @State private var scanning = false
    @State private var picked: [PhotosPickerItem] = []
    @State private var viewing: String?
    @State private var links = ParentLinks.shared
    @FocusState private var typing: Bool

    init(store: SheetStore, ask: ParentAsk, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _a = State(initialValue: ask)
        _day = State(initialValue: SheetDate.date(ask.date) ?? .now)
        _remind = State(initialValue: ask.time != nil)
        _time = State(initialValue: PlanTime.date(ask.time ?? ParentAsk.defaultTime) ?? .now)
    }

    private var trimmed: String { a.task.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var roomForPhotos: Int { ParentAsk.maxPhotos - a.photos.count }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Who asked", selection: $a.who) {
                        ForEach(ParentAsk.Who.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    TextField("What did they ask for?", text: $a.task, axis: .vertical)
                        .lineLimit(1...4)
                        .focused($typing)
                } footer: {
                    if isNew {
                        Text("e.g. Book Dad's eye check-up, send Mom the wedding photos, pay the electricity bill.")
                    } else if let asked = a.asked {
                        Text("Asked on \(SheetDate.short(asked)).")
                    }
                }
                if !isNew { letThemKnow }
                photosSection
                Section {
                    DatePicker(a.repeats == .once ? "Do it on" : "Next on", selection: $day, displayedComponents: .date)
                    Picker("Repeat", selection: $a.repeats) {
                        ForEach(ParentAsk.Repeat.allCases) { Text($0.rawValue).tag($0) }
                    }
                } footer: {
                    if a.repeats != .once { Text("When you tick it off, it moves to the next date.") }
                }
                Section {
                    Toggle("Remind me", isOn: $remind)
                    if remind {
                        DatePicker("At", selection: $time, displayedComponents: .hourAndMinute)
                        Toggle("Keep reminding until it's done", isOn: $a.nag)
                    }
                } footer: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(!remind ? "It stays on your list without a notification."
                             : a.nag ? "A notification at this time from that day on, every day, until you tick it off. “Tomorrow” on the notification gives you a day."
                             : "A notification at this time on the day\(a.repeats == .once ? "" : " each time it comes round").")
                        if remind, notificationsOff {
                            Button("Notifications are off for this app — turn them on in Settings") {
                                if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
                            }
                            .font(.footnote)
                        }
                    }
                }
                Section {
                    HStack {
                        Text("It costs")
                        Spacer()
                        TextField("Optional", value: $a.cost, format: .number)
                            .keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 110)
                        Text(Money.main).foregroundStyle(.secondary)
                    }
                    if (a.cost ?? 0) > 0 { Toggle("Add to Finance when done", isOn: $a.finance) }
                } footer: {
                    if (a.cost ?? 0) > 0, a.finance {
                        Text("Logged as Gifts & Family each time you tick it off\(a.repeats == .once ? "" : " (every time it comes round)"), so it counts toward Family support.")
                    }
                }
                Section {
                    TextField("Notes (optional)", text: Binding(get: { a.notes ?? "" }, set: { a.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                if !isNew {
                    Section {
                        if a.isOpen {
                            Button(a.repeats == .once ? "Mark as done" : "Done — move to the next date") {
                                let done = form(a)
                                Task { await ParentActions.complete(done, store: store) }
                                dismiss()
                            }
                        } else {
                            Button("Not done yet") {
                                let undone = form(a)
                                Task { await ParentActions.reopen(undone, store: store) }
                                dismiss()
                            }
                        }
                        Button("Delete", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(isNew ? "Mom & Dad asked" : a.who.label + " asked")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .fontWeight(.semibold)
                        .disabled(trimmed.isEmpty)
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Delete this request?", message: "Its reminders and photos go too.") {
                store.deleteParentAsk(id: a.id)
                Task { await ReminderScheduler.scheduleSheetAlerts() }
                dismiss()
            })
            .fullScreenCover(isPresented: $takingPhoto) {
                MealCameraPicker { image in
                    takingPhoto = false
                    if let image { attach(image) }
                }
                .ignoresSafeArea()
            }
            .fullScreenCover(isPresented: $scanning) {
                DocumentScanner { attach($0) }.ignoresSafeArea()
            }
            .onChange(of: picked) { _, items in
                guard !items.isEmpty else { return }
                Task {
                    for item in items {
                        if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) { attach(image) }
                    }
                    picked = []
                }
            }
            .sheet(item: Binding(get: { viewing.map(PhotoRef.init) }, set: { viewing = $0?.ref })) { ref in
                ParentPhotoViewer(refs: a.photos, start: ref.ref)
            }
            .alert("Couldn't open that", isPresented: Binding(get: { links.failure != nil }, set: { if !$0 { links.failure = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(links.failure ?? "") }
            .task {
                if isNew { typing = true }
                notificationsOff = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
            }
        }
        .tint(SheetTheme.navy)
    }

    /// Call or message whoever asked, about it or to say it's done.
    @ViewBuilder
    private var letThemKnow: some View {
        let people = ParentContacts.people(for: a.who).compactMap { w in ParentContacts.phone(w).map { (w, $0) } }
        Section {
            if people.isEmpty {
                Text("Add \(a.who == .both ? "their numbers" : a.who.rawValue + "'s number") with the phone button in the Parents tab to call or message from here.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            ForEach(people, id: \.0) { who, phone in
                ParentContactStrip(who: who, phone: phone, text: ParentContacts.message(a, to: who))
            }
        } header: {
            Text("Let them know")
        }
    }

    /// Up to three: the prescription, the product, the bill.
    private var photosSection: some View {
        Section {
            if !a.photos.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(a.photos, id: \.self) { ref in
                            Button { viewing = ref } label: { ParentPhotoThumb(ref: ref, size: 84) }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Photo")
                                .overlay(alignment: .topTrailing) {
                                    Button { a.photos.removeAll { $0 == ref } } label: {
                                        Image(systemName: "xmark.circle.fill").font(.title3)
                                            .symbolRenderingMode(.palette).foregroundStyle(.white, .black.opacity(0.6))
                                            .frame(width: 44, height: 44, alignment: .topTrailing)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Remove photo")
                                }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            if roomForPhotos > 0 {
                HStack(spacing: 10) {
                    photoButton("Camera", "camera") { takingPhoto = true }
                    PhotosPicker(selection: $picked, maxSelectionCount: roomForPhotos, matching: .images) {
                        photoLabel("Photos", "photo.on.rectangle")
                    }
                    .buttonStyle(.plain)
                    if DocumentScanner.isAvailable { photoButton("Scan", "doc.viewfinder") { scanning = true } }
                }
            }
        } header: {
            Text("Photos")
        } footer: {
            Text(a.photos.isEmpty ? "Optional — the prescription, the product they want, the bill. Up to \(ParentAsk.maxPhotos)."
                 : "Tap one to see it full size and share it.")
        }
    }

    private func photoButton(_ title: String, _ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { photoLabel(title, symbol) }.buttonStyle(.plain)
    }

    private func photoLabel(_ title: String, _ symbol: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: symbol).font(.title3)
            Text(title).font(.caption.weight(.semibold))
        }
        .foregroundStyle(SheetTheme.navy)
        .frame(maxWidth: .infinity, minHeight: 56)
        .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func attach(_ image: UIImage) {
        guard a.photos.count < ParentAsk.maxPhotos, let jpeg = MealPhotos.prepare(image) else { return }
        a.photos.append(ParentPhotos.add(jpeg))
    }

    /// The request with what's in the form (the date picker and reminder time).
    private func form(_ ask: ParentAsk) -> ParentAsk {
        var saved = ask
        saved.task = trimmed.isEmpty ? a.task : trimmed
        saved.date = SheetDate.string(day)
        saved.time = remind ? PlanTime.text(time) : nil
        saved.notes = saved.notes?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        if (saved.cost ?? 0) <= 0 { saved.cost = nil; saved.finance = false }
        return saved
    }

    private func save() {
        let saved = form(a)
        store.saveParentAsk(saved)
        Task {
            // Asked here, when it's first needed.
            if saved.time != nil { _ = await ReminderScheduler.requestAuthorization() }
            await ReminderScheduler.scheduleSheetAlerts()
        }
        dismiss()
    }
}

private struct PhotoRef: Identifiable {
    let ref: String
    var id: String { ref }
}

/// One photo, square: on this phone, cached, or downloaded from Drive.
struct ParentPhotoThumb: View {
    let ref: String
    let size: CGFloat
    @State private var image: UIImage?

    var body: some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(SheetTheme.input)
            .frame(width: size, height: size)
            .overlay {
                if let image { Image(uiImage: image).resizable().scaledToFill() } else { ProgressView() }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .task(id: ref) { image = await ParentPhotos.image(ref, thumb: true) }
    }
}

/// Full size, swipe between them, and share (to the pharmacy, a sibling…).
struct ParentPhotoViewer: View {
    let refs: [String]
    let start: String
    @Environment(\.dismiss) private var dismiss
    @State private var shown: String
    @State private var images: [String: UIImage] = [:]

    init(refs: [String], start: String) {
        self.refs = refs
        self.start = start
        _shown = State(initialValue: start)
    }

    var body: some View {
        NavigationStack {
            TabView(selection: $shown) {
                ForEach(refs, id: \.self) { ref in
                    Group {
                        if let image = images[ref] { Image(uiImage: image).resizable().scaledToFit() } else { ProgressView() }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .tag(ref)
                    .task { if images[ref] == nil { images[ref] = await ParentPhotos.image(ref, thumb: false) } }
                }
            }
            .tabViewStyle(.page(indexDisplayMode: refs.count > 1 ? .always : .never))
            .background(Color.black)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    if let image = images[shown] {
                        ShareLink(item: Image(uiImage: image), preview: SharePreview("Photo", image: Image(uiImage: image)))
                    }
                }
            }
            .toolbarBackground(.visible, for: .navigationBar)
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

extension ParentPhotos {
    /// The photo for `ref`: on this phone, cached, or downloaded from Drive (then cached).
    static func image(_ ref: String, thumb: Bool) async -> UIImage? {
        if let key = localKey(ref) { return UIImage(contentsOfFile: localURL(key: key).path) }
        let cached = cacheURL(id: ref, thumb: thumb)
        if let image = UIImage(contentsOfFile: cached.path) { return image }
        guard let data = await SheetStore.shared.photo(id: ref, thumb: thumb), let image = UIImage(data: data) else { return nil }
        try? data.write(to: cached, options: .atomic)
        return image
    }
}

// MARK: - The reminder's buttons

extension ParentReminders {
    static func registerCategory() {
        let done = UNNotificationAction(identifier: doneAction, title: "Done ✓", options: [])
        let tomorrow = UNNotificationAction(identifier: tomorrowAction, title: "Tomorrow", options: [])
        let category = UNNotificationCategory(identifier: categoryId, actions: [done, tomorrow], intentIdentifiers: [])
        let center = UNUserNotificationCenter.current()
        center.getNotificationCategories { existing in
            center.setNotificationCategories(existing.filter { $0.identifier != categoryId }.union([category]))
        }
    }

    /// From the notification: ticks it off, moves the reminders on and gives the save a moment to reach the sheet.
    @MainActor
    static func markDone(id: String) async {
        let store = SheetStore.shared
        guard let ask = store.snapshot?.parentAsks.first(where: { $0.id == id }), ask.isOpen else { return }
        await ParentActions.complete(ask, store: store)
        await store.waitForSync(seconds: 15)
    }

    /// From the notification: quiet until tomorrow (the request's date stays as it is).
    @MainActor
    static func snooze(id: String) async {
        ParentSnooze.untilTomorrow(id)
        await ReminderScheduler.scheduleSheetAlerts()
    }
}
