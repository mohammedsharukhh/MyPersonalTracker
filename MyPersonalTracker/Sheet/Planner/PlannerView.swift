//
//  PlannerView.swift
//  MyPersonalTracker
//
//  Daily ▸ Planner: each day of the week with its plans and what you'll wear.
//  Daily ▸ Wardrobe: the clothes outfits are picked from.
//

import SwiftUI

// MARK: - Week

/// The week, a day at a time: plans (with times) and the day's outfit.
struct PlannerContent: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var weekStart = FoodView.monday(of: .now)
    @State private var editingPlan: DayPlan?
    @State private var pickingOutfit: String?

    private var days: [String] {
        (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: weekStart) }.map(SheetDate.string)
    }

    var body: some View {
        let planner = snapshot.plannerData
        let thisWeek = weekStart == FoodView.monday(of: .now)
        HStack {
            Button { shiftWeek(-7) } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 36)
            }
            .accessibilityLabel("Previous week")
            Spacer()
            VStack(spacing: 1) {
                Text(thisWeek ? "This week" : "Week of \(weekStart.formatted(.dateTime.day().month()))").font(.headline)
                Text("\(SheetDate.short(days.first ?? "")) – \(SheetDate.short(days.last ?? ""))").font(.caption).foregroundStyle(.secondary)
            }
            .onTapGesture { withAnimation(.snappy) { weekStart = FoodView.monday(of: .now) } }
            Spacer()
            Button { shiftWeek(7) } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 36)
            }
            .accessibilityLabel("Next week")
        }
        .foregroundStyle(SheetTheme.navy)
        .onAppear {
            // "Plan next week" from the week in review.
            if let week = AppNavigation.shared.plannerWeek { weekStart = week; AppNavigation.shared.plannerWeek = nil }
        }

        let weekPlans = days.reduce(0) { $0 + planner.plans(on: $1).count }
        let dressed = days.filter { planner.outfit(on: $0) != nil }.count
        if weekPlans == 0 && dressed == 0 {
            SheetCard {
                VStack(alignment: .leading, spacing: 6) {
                    Label("Plan your week", systemImage: "calendar.badge.plus").font(.headline)
                    Text("Add what's on each day — a dinner, the gym, a trip on the weekend — and pick what to wear. Add your clothes in Wardrobe once and outfits are a few taps.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            }
        }

        ForEach(days, id: \.self) { date in
            // Past days of this week stay visible but quieter.
            DayCard(date: date, planner: planner, isPast: date < SheetDate.today,
                    onAdd: { editingPlan = DayPlan(date: date) },
                    onOpen: { editingPlan = $0 },
                    onToggle: { plan in var p = plan; p.done.toggle(); store.savePlan(p) },
                    onOutfit: { pickingOutfit = date })
        }
        Color.clear.frame(height: 0)
            .sheet(item: $editingPlan) { plan in
                DayPlanEditor(store: store, plan: plan, isNew: !planner.plans.contains { $0.id == plan.id })
            }
            .sheet(item: Binding(get: { pickingOutfit.map(OutfitDay.init) }, set: { pickingOutfit = $0?.date })) { day in
                OutfitPicker(store: store, date: day.date)
            }
    }

    private func shiftWeek(_ days: Int) {
        withAnimation(.snappy) { weekStart = Calendar.current.date(byAdding: .day, value: days, to: weekStart) ?? weekStart }
    }
}

private struct OutfitDay: Identifiable {
    let date: String
    var id: String { date }
}

private struct DayCard: View {
    let date: String
    let planner: PlannerData
    let isPast: Bool
    let onAdd: () -> Void
    let onOpen: (DayPlan) -> Void
    let onToggle: (DayPlan) -> Void
    let onOutfit: () -> Void

    var body: some View {
        let plans = planner.plans(on: date)
        let outfit = planner.outfit(on: date)
        let day = SheetDate.date(date)
        let isToday = date == SheetDate.today
        let isWeekend = day.map { Calendar.current.isDateInWeekend($0) } ?? false
        SheetCard(padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(day?.formatted(.dateTime.weekday(.wide).day().month()) ?? date).font(.headline)
                    if isToday { StatusPill(text: "Today", tint: SheetTheme.navy) }
                    else if date == SheetDate.string(Calendar.current.date(byAdding: .day, value: 1, to: .now) ?? .now) {
                        StatusPill(text: "Tomorrow", tint: SheetTheme.teal)
                    } else if isWeekend { StatusPill(text: "Weekend", tint: SheetTheme.purple) }
                    Spacer()
                    Button(action: onAdd) { Image(systemName: "plus.circle.fill").font(.title3) }
                        .foregroundStyle(SheetTheme.navy)
                        .accessibilityLabel("Add a plan")
                }
                .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 8)

                if plans.isEmpty {
                    Button(action: onAdd) {
                        Text(isPast ? "Nothing planned" : "Nothing planned yet — add something")
                            .font(.subheadline).foregroundStyle(.tertiary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16).padding(.bottom, 10)
                }
                ForEach(plans) { plan in
                    PlanRow(plan: plan, onToggle: { onToggle(plan) }, onOpen: { onOpen(plan) })
                }

                Divider().padding(.leading, 16)
                Button(action: onOutfit) { OutfitRow(outfit: outfit, planner: planner) }
                    .buttonStyle(.plain)
            }
        }
        .opacity(isPast && !isToday ? 0.7 : 1)
    }
}

private struct PlanRow: View {
    let plan: DayPlan
    let onToggle: () -> Void
    let onOpen: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button(action: onToggle) {
                Image(systemName: plan.done ? "checkmark.circle.fill" : "circle")
                    .font(.title3).foregroundStyle(plan.done ? SheetTheme.good : Color(.tertiaryLabel))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(plan.done ? "Done — mark not done" : "Mark done")
            Button(action: onOpen) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(plan.title).font(.subheadline.weight(.semibold))
                            .strikethrough(plan.done).foregroundStyle(plan.done ? .secondary : .primary)
                        let detail = [plan.place.map { "📍 \($0)" }, plan.notes].compactMap { $0 }.joined(separator: " · ")
                        if !detail.isEmpty { Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                    }
                    Spacer(minLength: 8)
                    Text(PlanTime.display(plan.time) ?? "Any time").font(.caption.weight(.semibold).monospacedDigit())
                        .foregroundStyle(plan.time == nil ? Color.secondary : SheetTheme.navy)
                    if plan.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
    }
}

private struct OutfitRow: View {
    let outfit: Outfit?
    let planner: PlannerData

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(outfit.flatMap { $0.items.first }.flatMap { planner.clothing($0)?.type.emoji } ?? "👔")
                .font(.title3).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                if let outfit {
                    let pieces = planner.pieces(of: outfit)
                    Text("Wear").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text(pieces.isEmpty ? (outfit.note ?? "Outfit") : pieces.joined(separator: " + "))
                        .font(.subheadline.weight(.medium)).foregroundStyle(Color(.label))
                    if !pieces.isEmpty, let note = outfit.note { Text(note).font(.caption).foregroundStyle(.secondary) }
                    ForEach(planner.recentRepeats(in: outfit), id: \.0.id) { piece, gap in
                        Label("\(piece.label) worn \(gap == 1 ? "the day before" : "\(gap) days before")", systemImage: "arrow.triangle.2.circlepath")
                            .font(.caption2.weight(.semibold)).foregroundStyle(SheetTheme.warn)
                    }
                } else {
                    Text("What will you wear?").font(.subheadline).foregroundStyle(SheetTheme.navy)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

// MARK: - Editors

struct DayPlanEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var plan: DayPlan
    @State private var day: Date
    @State private var hasTime: Bool
    @State private var time: Date
    @State private var confirmDelete = false

    init(store: SheetStore, plan: DayPlan, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _plan = State(initialValue: plan)
        let date = SheetDate.date(plan.date) ?? .now
        _day = State(initialValue: date)
        _hasTime = State(initialValue: plan.time != nil)
        _time = State(initialValue: PlanTime.date(plan.time, on: date) ?? Calendar.current.date(bySettingHour: 18, minute: 0, second: 0, of: date) ?? date)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("What's the plan? e.g. Dinner with Arun", text: $plan.title, axis: .vertical)
                        .textInputAutocapitalization(.sentences)
                    DatePicker("Day", selection: $day, displayedComponents: .date)
                    Toggle("At a time", isOn: $hasTime.animation())
                    if hasTime { DatePicker("Time", selection: $time, displayedComponents: .hourAndMinute) }
                    TextField("Where (optional)", text: Binding(get: { plan.place ?? "" }, set: { plan.place = $0.isEmpty ? nil : $0 }))
                    TextField("Notes (optional)", text: Binding(get: { plan.notes ?? "" }, set: { plan.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                if !isNew {
                    Section {
                        Toggle("Done", isOn: $plan.done)
                    }
                    Section {
                        Button("Delete plan", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(isNew ? "New plan" : "Plan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = plan
                        saved.title = plan.title.trimmingCharacters(in: .whitespacesAndNewlines)
                        saved.date = SheetDate.string(day)
                        saved.time = hasTime ? PlanTime.text(time) : nil
                        store.savePlan(saved)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(plan.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .popup(isPresented: $confirmDelete, .delete("Delete this plan?", message: plan.title) { store.deletePlan(id: plan.id); dismiss() })
        .tint(SheetTheme.navy)
    }
}

/// Picks a day's outfit from the wardrobe, with when each piece was last worn.
struct OutfitPicker: View {
    let store: SheetStore
    let date: String
    @Environment(\.dismiss) private var dismiss
    @State private var selected: [String] = []
    @State private var note = ""
    @State private var loaded = false
    @State private var newName = ""
    @State private var newType: ClothingType = .top

    var body: some View {
        let planner = store.snapshot?.plannerData ?? PlannerData()
        let existing = planner.outfit(on: date)
        NavigationStack {
            List {
                Section {
                    if selected.isEmpty {
                        Text("Tap clothes below to put the outfit together.").font(.subheadline).foregroundStyle(.secondary)
                    } else {
                        Text(selected.compactMap { planner.clothing($0)?.label }.joined(separator: " + "))
                            .font(.subheadline.weight(.semibold))
                    }
                    TextField("Note (optional), e.g. iron the shirt", text: $note)
                } header: {
                    Text("Wear on \(SheetDate.date(date)?.formatted(.dateTime.weekday(.wide).day().month()) ?? date)")
                }
                if let last = lastOutfit(planner), selected.isEmpty {
                    Section {
                        Button {
                            selected = last.items.filter { planner.clothing($0) != nil }
                        } label: {
                            Label("Same as \(SheetDate.short(last.date)): \(planner.pieces(of: last).joined(separator: " + "))", systemImage: "arrow.uturn.backward")
                                .font(.subheadline).lineLimit(2)
                        }
                    }
                }
                ForEach(ClothingType.allCases) { type in
                    let pieces = planner.wardrobe.filter { $0.type == type }.sorted { $0.label < $1.label }
                    if !pieces.isEmpty {
                        Section("\(type.emoji) \(type.rawValue)") {
                            ForEach(pieces) { piece in
                                Button { toggle(piece.id) } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(piece.label).foregroundStyle(Color(.label))
                                            Text(wornText(planner, piece)).font(.caption).foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        Image(systemName: selected.contains(piece.id) ? "checkmark.circle.fill" : "circle")
                                            .font(.title3)
                                            .foregroundStyle(selected.contains(piece.id) ? SheetTheme.navy : Color(.tertiaryLabel))
                                    }
                                    .contentShape(Rectangle())
                                }
                            }
                        }
                    }
                }
                Section {
                    HStack {
                        TextField("e.g. White linen shirt", text: $newName).textInputAutocapitalization(.sentences)
                        Picker("", selection: $newType) {
                            ForEach(ClothingType.allCases) { Text("\($0.emoji) \($0.rawValue)").tag($0) }
                        }
                        .labelsHidden().fixedSize()
                    }
                    Button("Add and wear it") {
                        let item = Clothing(name: newName.trimmingCharacters(in: .whitespaces), type: newType)
                        store.saveClothing(item)
                        selected.append(item.id)
                        newName = ""
                    }
                    .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
                } header: {
                    Text(planner.wardrobe.isEmpty ? "Add your first clothes" : "Not in your wardrobe yet?")
                } footer: {
                    Text("It's added to your Wardrobe too, for next time.")
                }
                if existing != nil {
                    Section {
                        Button("Clear outfit", role: .destructive) { store.deleteOutfit(id: Outfit.id(for: date)); dismiss() }
                    }
                }
            }
            .navigationTitle("What to wear")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let trimmed = note.trimmingCharacters(in: .whitespaces)
                        store.saveOutfit(Outfit(date: date, items: selected, note: trimmed.isEmpty ? nil : trimmed))
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(selected.isEmpty && note.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                selected = existing?.items ?? []
                note = existing?.note ?? ""
            }
        }
        .tint(SheetTheme.navy)
    }

    private func toggle(_ id: String) {
        if let i = selected.firstIndex(of: id) { selected.remove(at: i) } else { selected.append(id) }
    }

    private func lastOutfit(_ planner: PlannerData) -> Outfit? {
        planner.outfits.filter { $0.date < date && !$0.items.isEmpty }.max { $0.date < $1.date }
    }

    private func wornText(_ planner: PlannerData, _ piece: Clothing) -> String {
        guard let last = planner.lastWorn(piece.id, before: date) else { return "Not worn yet" }
        let days = SheetDate.date(last).flatMap { a in SheetDate.date(date).map { Calendar.current.dateComponents([.day], from: a, to: $0).day ?? 0 } } ?? 0
        return days <= 1 ? "Worn the day before" : "Last worn \(days) days before · \(SheetDate.short(last))"
    }
}

// MARK: - Wardrobe

struct WardrobeContent: View {
    let store: SheetStore
    let snapshot: SheetSnapshot
    @State private var editing: Clothing?

    var body: some View {
        let planner = snapshot.plannerData
        HStack(alignment: .bottom) {
            SectionTitle(title: "Wardrobe", subtitle: planner.wardrobe.isEmpty ? nil : "\(planner.wardrobe.count) pieces")
            Button { editing = Clothing() } label: { Label("Add clothes", systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold)) }
                .foregroundStyle(SheetTheme.navy)
        }
        if planner.wardrobe.isEmpty {
            SheetCard {
                Text("Add the clothes you have — shirts, trousers, shoes, a kurta for occasions. Then picking a day's outfit in Planner is a few taps, and you'll see when you last wore each piece.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        // Pieces not worn for a month (or never) — easy to forget you have them.
        let idle = planner.wardrobe.filter { piece in
            if planner.nextPlanned(piece.id) != nil { return false }
            guard let last = planner.lastWorn(piece.id, before: SheetDate.string(Calendar.current.date(byAdding: .day, value: 1, to: .now) ?? .now)) else { return true }
            return (SheetDate.date(last).map { Calendar.current.dateComponents([.day], from: $0, to: .now).day ?? 0 } ?? 0) >= 30
        }
        if !planner.outfits.isEmpty, !idle.isEmpty, idle.count < planner.wardrobe.count {
            SheetCard {
                VStack(alignment: .leading, spacing: 4) {
                    Label("Not worn in a month", systemImage: "hanger").font(.subheadline.weight(.semibold))
                    Text(idle.prefix(6).map(\.label).joined(separator: ", ") + (idle.count > 6 ? " and \(idle.count - 6) more" : ""))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        ForEach(ClothingType.allCases) { type in
            let pieces = planner.wardrobe.filter { $0.type == type }.sorted { $0.label < $1.label }
            if !pieces.isEmpty {
                SheetCard(padding: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("\(type.emoji)  \(type.rawValue) · \(pieces.count)").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                            .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 4)
                        ForEach(pieces) { piece in
                            Button { editing = piece } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(piece.label).font(.subheadline.weight(.medium)).foregroundStyle(Color(.label))
                                        Text(wornSummary(planner, piece)).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if piece.pending { Image(systemName: "clock.arrow.circlepath").font(.caption2).foregroundStyle(SheetTheme.sky) }
                                    Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 9)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.bottom, 6)
                }
            }
        }
        Color.clear.frame(height: 0)
            .sheet(item: $editing) { piece in
                ClothingEditor(store: store, item: piece, isNew: !planner.wardrobe.contains { $0.id == piece.id })
            }
    }
}

/// "Worn 3× · last 2 Oct · next Sat" — up to today, then what's planned.
private func wornSummary(_ planner: PlannerData, _ piece: Clothing) -> String {
    let tomorrow = SheetDate.string(Calendar.current.date(byAdding: .day, value: 1, to: .now) ?? .now)
    let worn = planner.timesWorn(piece.id)
    var parts = [worn == 0 ? "Not worn yet" : "Worn \(worn)×"]
    if let last = planner.lastWorn(piece.id, before: tomorrow) { parts.append(last == SheetDate.today ? "today" : "last \(SheetDate.short(last))") }
    if let next = planner.nextPlanned(piece.id), next != SheetDate.today, let d = SheetDate.date(next) {
        parts.append("next \(d.formatted(.dateTime.weekday(.abbreviated).day()))")
    }
    return parts.joined(separator: " · ")
}

struct ClothingEditor: View {
    let store: SheetStore
    let isNew: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var item: Clothing
    @State private var confirmDelete = false

    init(store: SheetStore, item: Clothing, isNew: Bool) {
        self.store = store
        self.isNew = isNew
        _item = State(initialValue: item)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("e.g. Oxford shirt, chinos, white sneakers", text: $item.name).textInputAutocapitalization(.sentences)
                    Picker("Type", selection: $item.type) {
                        ForEach(ClothingType.allCases) { Text("\($0.emoji) \($0.rawValue)").tag($0) }
                    }
                    TextField("Colour (optional), e.g. Navy", text: Binding(get: { item.colour ?? "" }, set: { item.colour = $0.isEmpty ? nil : $0 }))
                        .textInputAutocapitalization(.words)
                    TextField("Notes (optional), e.g. dry clean only", text: Binding(get: { item.notes ?? "" }, set: { item.notes = $0.isEmpty ? nil : $0 }), axis: .vertical)
                }
                if !isNew {
                    Section {
                        Button("Remove from wardrobe", role: .destructive) { confirmDelete = true }
                    } footer: { Text("Past outfits keep it.") }
                }
            }
            .navigationTitle(isNew ? "Add clothes" : "Edit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        var saved = item
                        saved.name = item.name.trimmingCharacters(in: .whitespaces)
                        saved.colour = item.colour?.trimmingCharacters(in: .whitespaces)
                        store.saveClothing(saved)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(item.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .popup(isPresented: $confirmDelete, .delete("Remove \(item.label)?", message: "Past outfits keep it.", confirmTitle: "Remove") {
                store.deleteClothing(id: item.id); dismiss()
            })
        }
        .tint(SheetTheme.navy)
    }
}

// MARK: - Dashboard

/// Today's plans and outfit, on the Dashboard.
struct PlannerTodayCard: View {
    let snapshot: SheetSnapshot
    let onOpen: () -> Void

    var body: some View {
        let planner = snapshot.plannerData
        let today = SheetDate.today
        let plans = planner.plans(on: today)
        let outfit = planner.outfit(on: today)
        if !plans.isEmpty || outfit != nil {
            Button(action: onOpen) {
                SheetCard {
                    HStack(alignment: .top, spacing: 12) {
                        IconBadge(symbol: "calendar", tint: SheetTheme.purple, size: 44)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Today's plan").font(.headline).foregroundStyle(Color(.label))
                            ForEach(plans.prefix(3)) { plan in
                                HStack(spacing: 6) {
                                    Text(PlanTime.display(plan.time) ?? "Any time").font(.caption.weight(.semibold).monospacedDigit())
                                        .foregroundStyle(SheetTheme.navy).frame(minWidth: 58, alignment: .leading)
                                    Text(plan.title).font(.subheadline).strikethrough(plan.done)
                                        .foregroundStyle(plan.done ? Color.secondary : Color(.label)).lineLimit(1)
                                }
                            }
                            if plans.count > 3 { Text("+\(plans.count - 3) more").font(.caption).foregroundStyle(.secondary) }
                            if let outfit {
                                Text("👕 \(planner.pieces(of: outfit).joined(separator: " + "))\(outfit.note.map { " · \($0)" } ?? "")")
                                    .font(.caption.weight(.medium)).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                    }
                }
            }
            .buttonStyle(.plain)
        }
    }
}
