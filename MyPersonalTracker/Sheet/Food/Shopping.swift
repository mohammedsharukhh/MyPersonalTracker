//
//  Shopping.swift
//  MyPersonalTracker
//
//  Food ▸ Shopping: the grocery list (by aisle), the cart while you shop,
//  finishing a trip (one Groceries expense in Finance), and the history of
//  what you bought and what it cost. Kept in the sheet, so everyone using it
//  sees the same list.
//

import SwiftUI

enum ShoppingActions {
    /// Adds the week's planned foods, added up, that aren't on the list yet. Returns how many were added.
    @MainActor @discardableResult
    static func addPlan(_ plan: [PlanItem], to store: SheetStore) -> Int {
        let onList = Set((store.snapshot?.shoppingData.items ?? []).filter { $0.status != .bought }.map { $0.item.lowercased() })
        let groups = Dictionary(grouping: plan) { "\($0.food.lowercased())|\($0.unit ?? "")" }
        var added = 0
        for group in groups.values.sorted(by: { $0[0].food < $1[0].food }) {
            let food = group[0].food
            guard !onList.contains(food.lowercased()) else { continue }
            let amount = group.reduce(0) { $0 + ($1.amount ?? 0) }
            let quantity = amount > 0 ? "\(SheetFormat.number(amount, digits: 2)) \(group[0].unit ?? "")".trimmingCharacters(in: .whitespaces) : nil
            store.saveShoppingItem(ShoppingItem(item: food, quantity: quantity, fromPlan: true))
            added += 1
        }
        return added
    }
}

/// The Shopping section of the Food tab.
struct ShoppingSection: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    let weekPlan: [PlanItem]

    @State private var newItem = ""
    @State private var newPrice = ""
    @State private var editing: ShoppingItem?
    @State private var checkingOut = false
    @State private var trip: ShoppingTrip?
    @State private var planNote: String?
    @FocusState private var focus: ShopField?

    private var data: ShoppingData { snapshot.shoppingData }

    var body: some View {
        let toBuy = data.toBuy
        let cart = data.inCart
        let month = data.spent(month: String(SheetDate.today.prefix(7)))

        // Quick add.
        HStack(spacing: 10) {
            Image(systemName: "cart.badge.plus").foregroundStyle(SheetTheme.navy)
            TextField("Add an item, e.g. milk 2L, rice 5 kg", text: $newItem)
                .focused($focus, equals: .name)
                .submitLabel(.next)
                .onSubmit { focus = .newPrice }
            TextField("Price", text: $newPrice)
                .keyboardType(.decimalPad)
                .focused($focus, equals: .newPrice)
                .multilineTextAlignment(.trailing)
                .frame(width: 70)
                .padding(.vertical, 6).padding(.horizontal, 8)
                .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .accessibilityLabel("Price in \(Money.main), optional")
            if !newItem.trimmingCharacters(in: .whitespaces).isEmpty {
                Button("Add", action: add).fontWeight(.semibold)
            }
        }
        .padding(14)
        .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                if focus == .newPrice || focus == .name {
                    Button("Add") { add() }.fontWeight(.semibold).disabled(newItem.trimmingCharacters(in: .whitespaces).isEmpty)
                } else {
                    Button("Done") { focus = nil }.fontWeight(.semibold)
                }
            }
        }

        let again = data.buyAgain
        if !again.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("Buy again").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(again, id: \.self) { name in
                            Button {
                                store.saveShoppingItem(ShoppingItem(item: name, quantity: data.lastQuantity(of: name), price: data.lastPrice(of: name)))
                            } label: {
                                Label(name, systemImage: "plus").font(.caption.weight(.semibold))
                                    .padding(.horizontal, 12).padding(.vertical, 7)
                                    .background(SheetTheme.card, in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }

        if !weekPlan.isEmpty {
            Button {
                let added = ShoppingActions.addPlan(weekPlan, to: store)
                planNote = added == 0 ? "Everything planned this week is already on the list." : "Added \(added) item\(added == 1 ? "" : "s") from this week's plan."
            } label: {
                Label("Add this week's meal plan", systemImage: "calendar.badge.plus")
                    .font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity).padding(.vertical, 10)
            }
            .buttonStyle(.bordered)
            if let planNote { Text(planNote).font(.caption).foregroundStyle(.secondary) }
        }

        // To buy, by aisle.
        if toBuy.isEmpty && cart.isEmpty {
            SheetCard {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Your list is empty", systemImage: "checklist").font(.headline)
                    Text("Add what you need above. At the shop, tap each item as it goes in the cart, then Finish shopping to record what you paid — it's added to Finance as Groceries.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            }
        } else if !toBuy.isEmpty {
            HStack(alignment: .bottom) {
                let estimate = toBuy.compactMap(\.price).reduce(0, +)
                SectionTitle(title: "To buy", subtitle: "\(toBuy.count) item\(toBuy.count == 1 ? "" : "s")"
                             + (estimate > 0 ? " · about \(SheetFormat.qar(estimate)) \(Money.main)" : "") + " · tap ○ as it goes in the cart")
                ShareLink(item: shareText(toBuy)) { Label("Share", systemImage: "square.and.arrow.up").font(.subheadline.weight(.semibold)) }
            }
            ForEach(ShoppingAisle.all, id: \.self) { aisle in
                let list = toBuy.filter { $0.category == aisle }.sorted { $0.item < $1.item }
                if !list.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        Label(aisle, systemImage: ShoppingAisle.symbol(aisle))
                            .font(.caption.weight(.bold)).foregroundStyle(.secondary)
                            .padding(.horizontal, 14).padding(.top, 10).padding(.bottom, 4)
                        ForEach(list) { item in
                            Divider().padding(.leading, 14)
                            ItemRow(item: item, focus: $focus, onTick: { move(item, to: .inCart) }, onOpen: { editing = item }, onPrice: { setPrice(item, $0) })
                        }
                    }
                    .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
        }

        // In the cart.
        if !cart.isEmpty {
            let priced = cart.compactMap(\.price).reduce(0, +)
            SectionTitle(title: "In the cart", subtitle: priced > 0 ? "\(SheetFormat.qar(priced)) \(Money.main) so far" : "Tap Price to type what each cost — or just enter the total at the end")
            VStack(spacing: 0) {
                ForEach(cart) { item in
                    if item.id != cart.first?.id { Divider().padding(.leading, 14) }
                    ItemRow(item: item, focus: $focus, onTick: { move(item, to: .need) }, onOpen: { editing = item }, onPrice: { setPrice(item, $0) })
                }
            }
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            Button { checkingOut = true } label: {
                Label("Finish shopping · \(cart.count) item\(cart.count == 1 ? "" : "s")", systemImage: "checkmark.circle.fill")
                    .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(SheetTheme.good, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }

        // History.
        let trips = data.trips.sorted { $0.date > $1.date }
        if !trips.isEmpty {
            SectionTitle(title: "What you bought",
                         subtitle: month.trips > 0 ? "This month: \(month.trips) trip\(month.trips == 1 ? "" : "s") · \(SheetFormat.qar(month.total)) \(Money.main)" : nil)
            VStack(spacing: 0) {
                ForEach(trips.prefix(20)) { t in
                    if t.id != trips.first?.id { Divider().padding(.leading, 14) }
                    Button { trip = t } label: {
                        HStack(spacing: 12) {
                            IconBadge(symbol: "bag.fill", tint: SheetTheme.categoryColor("Groceries"), size: 34)
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 4) {
                                    Text(t.store ?? "Shopping").font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                                    if t.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                                }
                                Text("\(SheetDate.weekdayDay(t.date)) · \(t.items) item\(t.items == 1 ? "" : "s")").font(.caption).foregroundStyle(Color(.secondaryLabel))
                            }
                            Spacer()
                            if let total = t.total, total > 0 {
                                Text(SheetFormat.qar(total)).font(.subheadline.weight(.bold).monospacedDigit()).foregroundStyle(Color(.label))
                            }
                            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(SheetTheme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }

        Color.clear.frame(height: 0)
            .sheet(item: $editing) { ShoppingItemEditor(store: store, item: $0) }
            .sheet(isPresented: $checkingOut) { CheckoutSheet(store: store, cart: cart) }
            .sheet(item: $trip) { TripDetail(store: store, trip: $0) }
    }

    private func add() {
        let parsed = ShoppingParse.split(newItem)
        guard !parsed.item.isEmpty else { return }
        let price = Double(newPrice.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil }
        // Already on the list: update it rather than add it twice.
        if var existing = data.items.first(where: { $0.status != .bought && $0.item.lowercased() == parsed.item.lowercased() }) {
            existing.quantity = parsed.quantity ?? existing.quantity
            existing.price = price ?? existing.price
            store.saveShoppingItem(existing)
        } else {
            store.saveShoppingItem(ShoppingItem(item: parsed.item, quantity: parsed.quantity, price: price ?? data.lastPrice(of: parsed.item)))
        }
        newItem = ""
        newPrice = ""
        focus = .name
    }

    private func setPrice(_ item: ShoppingItem, _ price: Double?) {
        guard price != item.price else { return }
        var changed = item
        changed.price = price
        store.saveShoppingItem(changed)
    }

    private func move(_ item: ShoppingItem, to status: ShoppingItem.Status) {
        var changed = item
        changed.status = status
        withAnimation(.snappy) { store.saveShoppingItem(changed) }
    }

    /// The list as text, by aisle — for WhatsApp or Notes.
    private func shareText(_ items: [ShoppingItem]) -> String {
        var lines = ["Shopping list"]
        for aisle in ShoppingAisle.all {
            let list = items.filter { $0.category == aisle }
            guard !list.isEmpty else { continue }
            lines.append("\n\(aisle)")
            lines += list.map { "☐ \($0.label)" }
        }
        return lines.joined(separator: "\n")
    }
}

/// Which box on the Shopping screen has the keyboard.
enum ShopField: Hashable { case name, newPrice, price(String) }

private struct ItemRow: View {
    let item: ShoppingItem
    var focus: FocusState<ShopField?>.Binding
    let onTick: () -> Void
    let onOpen: () -> Void
    let onPrice: (Double?) -> Void

    var body: some View {
        let inCart = item.status == .inCart
        HStack(spacing: 12) {
            Button(action: onTick) {
                Image(systemName: inCart ? "checkmark.circle.fill" : "circle")
                    .font(.title3).foregroundStyle(inCart ? SheetTheme.good : .secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(inCart ? "Take \(item.item) out of the cart" : "Put \(item.item) in the cart")
            Button(action: onOpen) {
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 4) {
                            Text(item.item).font(.subheadline.weight(.semibold)).foregroundStyle(Color(.label))
                                .strikethrough(inCart, color: .secondary)
                            if item.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                        }
                        if let detail = [item.quantity, item.notes, item.fromPlan ? "from plan" : nil].compactMap({ $0 }).joined(separator: " · ").nilIfEmpty {
                            Text(detail).font(.caption).foregroundStyle(Color(.secondaryLabel)).lineLimit(1)
                        }
                    }
                    Spacer()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            PriceField(id: item.id, price: item.price, focus: focus, onCommit: onPrice)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
    }
}

/// The price on a row: tap and type. Saved a moment after you stop typing, and when you leave the box.
private struct PriceField: View {
    let id: String
    let price: Double?
    var focus: FocusState<ShopField?>.Binding
    let onCommit: (Double?) -> Void
    @State private var text = ""

    var body: some View {
        TextField("Price", text: $text)
            .keyboardType(.decimalPad)
            .focused(focus, equals: .price(id))
            .multilineTextAlignment(.trailing)
            .font(.subheadline.monospacedDigit().weight(.semibold))
            .frame(width: 70)
            .padding(.vertical, 6).padding(.horizontal, 8)
            .background(SheetTheme.input, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .accessibilityLabel("Price in \(Money.main)")
            .onAppear { text = Self.format(price) }
            .onChange(of: price) { _, new in if focus.wrappedValue != .price(id) { text = Self.format(new) } }
            .onChange(of: focus.wrappedValue) { old, new in
                if old == .price(id), new != .price(id) { commit() }
            }
            .task(id: text) {
                guard focus.wrappedValue == .price(id) else { return }
                try? await Task.sleep(for: .milliseconds(800))
                if !Task.isCancelled { commit() }
            }
    }

    private func commit() {
        onCommit(Double(text.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil })
    }

    static func format(_ value: Double?) -> String {
        value.map { $0.formatted(.number.precision(.fractionLength(0...2)).grouping(.never)) } ?? ""
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

/// Edit one item: name, quantity, aisle, price, note.
private struct ShoppingItemEditor: View {
    let store: SheetStore
    @Environment(\.dismiss) private var dismiss
    @State private var item: ShoppingItem

    init(store: SheetStore, item: ShoppingItem) {
        self.store = store
        _item = State(initialValue: item)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Item", text: $item.item)
                    TextField("Quantity, e.g. 2 L, 5 kg, 30", text: Binding(get: { item.quantity ?? "" }, set: { item.quantity = $0.isEmpty ? nil : $0 }))
                    Picker("Aisle", selection: $item.category) {
                        ForEach(ShoppingAisle.all, id: \.self) { Label($0, systemImage: ShoppingAisle.symbol($0)).tag($0) }
                    }
                }
                Section {
                    HStack {
                        Text("Price")
                        Spacer()
                        TextField("optional", value: $item.price, format: .number).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                        Text(Money.main).foregroundStyle(.secondary)
                    }
                    TextField("Note, e.g. brand, low fat", text: Binding(get: { item.notes ?? "" }, set: { item.notes = $0.isEmpty ? nil : $0 }))
                }
                Section {
                    Button("Remove from list", role: .destructive) { store.deleteShoppingItem(id: item.id); dismiss() }
                }
            }
            .navigationTitle(item.status == .inCart ? "In the cart" : "To buy")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        item.item = item.item.trimmingCharacters(in: .whitespaces)
                        store.saveShoppingItem(item)
                        dismiss()
                    }
                    .fontWeight(.semibold).disabled(item.item.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .tint(SheetTheme.navy)
        .presentationDetents([.medium, .large])
    }
}

/// Finish the trip: where, how much in total, paid how — and into Finance.
private struct CheckoutSheet: View {
    let store: SheetStore
    let cart: [ShoppingItem]
    @Environment(\.dismiss) private var dismiss
    @State private var storeName = ""
    @State private var totalText = ""
    @State private var date = Date.now
    @State private var method = ""
    @State private var addToFinance = true
    @State private var notes = ""

    private var total: Double? { Double(totalText.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil } }
    private var stores: [String] {
        Array(Set((store.snapshot?.shoppingData.trips ?? []).compactMap(\.store))).sorted()
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Shop, e.g. Lulu, DMart, Carrefour", text: $storeName).textInputAutocapitalization(.words)
                    if !stores.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(stores, id: \.self) { name in
                                    Button(name) { storeName = name }
                                        .font(.caption.weight(.semibold)).padding(.horizontal, 12).padding(.vertical, 7)
                                        .background(storeName == name ? SheetTheme.navy : SheetTheme.input, in: Capsule())
                                        .foregroundStyle(storeName == name ? .white : .primary)
                                        .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    DatePicker("Date", selection: $date, in: ...Date.now, displayedComponents: .date)
                } header: { Text("Where") }

                Section {
                    HStack {
                        CurrencyPill(code: Money.main)
                        TextField("0.00", text: $totalText).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                            .font(.system(.title2, design: .rounded, weight: .bold))
                    }
                    Picker("Paid with", selection: $method) {
                        ForEach(store.snapshot?.lists.methods ?? ["Cash"], id: \.self) { Text($0) }
                    }
                    Toggle("Add to Finance as Groceries", isOn: $addToFinance)
                } header: { Text("Total paid") } footer: {
                    Text(addToFinance ? "One Groceries expense for the whole trip. Deleting the trip later takes it out of Finance too." : "Recorded here only.")
                }

                Section("\(cart.count) item\(cart.count == 1 ? "" : "s")") {
                    ForEach(cart) { item in
                        HStack {
                            Text(item.label)
                            Spacer()
                            if let price = item.price { Text(SheetFormat.qar(price)).foregroundStyle(.secondary).monospacedDigit() }
                        }
                    }
                    TextField("Note (optional)", text: $notes)
                }
            }
            .navigationTitle("Finish shopping")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                method = store.snapshot?.lists.methods.first ?? "Cash"
                let priced = cart.compactMap(\.price).reduce(0, +)
                if priced > 0 { totalText = SheetFormat.number(priced, digits: 2).replacingOccurrences(of: ",", with: "") }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        store.checkoutShopping(tripId: UUID().uuidString, date: SheetDate.string(date),
                                               store: storeName.trimmingCharacters(in: .whitespaces), total: total, method: method,
                                               addToFinance: addToFinance && total != nil, notes: notes,
                                               items: cart.map { ($0.id, $0.price) })
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .tint(SheetTheme.navy)
    }
}

/// One past trip: what you bought and what it cost.
private struct TripDetail: View {
    let store: SheetStore
    let trip: ShoppingTrip
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false

    var body: some View {
        let data = store.snapshot?.shoppingData ?? ShoppingData()
        let items = data.bought(on: trip.id).sorted { $0.item < $1.item }
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(SheetDate.short(trip.date)).font(.subheadline).foregroundStyle(.secondary)
                        Text(trip.total.map { "\(SheetFormat.qar($0)) \(Money.main)" } ?? "No total").font(.system(.largeTitle, design: .rounded, weight: .bold))
                        Text([trip.method, "\(trip.items) items", trip.notes].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
                Section("Bought") {
                    ForEach(items) { item in
                        HStack {
                            Label(item.label, systemImage: ShoppingAisle.symbol(item.category))
                            Spacer()
                            if let price = item.price { Text(SheetFormat.qar(price)).foregroundStyle(.secondary).monospacedDigit() }
                        }
                    }
                    Button("Put these on the list again", systemImage: "arrow.uturn.backward") {
                        for item in items { store.saveShoppingItem(ShoppingItem(item: item.item, quantity: item.quantity, category: item.category)) }
                        dismiss()
                    }
                }
                Section { Button("Delete this trip", role: .destructive) { confirmDelete = true } }
            }
            .navigationTitle(trip.store ?? "Shopping trip")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .popup(isPresented: $confirmDelete, .delete("Delete this trip?", message: "Its items leave your history, and its Groceries expense is taken out of Finance.",
                                                        confirmTitle: "Delete trip") { store.deleteShoppingTrip(id: trip.id); dismiss() })
        }
        .tint(SheetTheme.navy)
    }
}
