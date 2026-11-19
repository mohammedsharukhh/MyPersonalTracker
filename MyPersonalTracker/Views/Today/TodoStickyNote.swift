//
//  TodoStickyNote.swift
//  MyPersonalTracker
//

import PhotosUI
import SwiftData
import SwiftUI

/// A paper sticky note with today's to-dos. Type a line and press return to add it;
/// the camera button attaches a photo to the next to-do, or long-press a to-do to attach one later.
struct TodoStickyNote: View {
    let day: Date

    @Environment(\.modelContext) private var context
    @Query(sort: \TodoItem.createdAt) private var todos: [TodoItem]
    @State private var text = ""
    @FocusState private var focused: Bool

    /// Photo waiting to be attached to the next to-do you add.
    @State private var stagedPhoto: Data?
    /// Existing to-do a photo is being picked for; nil means the photo goes to `stagedPhoto`.
    @State private var photoTarget: TodoItem?
    @State private var isShowingCamera = false
    @State private var isShowingLibrary = false
    @State private var libraryItem: PhotosPickerItem?
    @State private var viewing: TodoItem?

    private static let paper = Color(red: 1.0, green: 0.92, blue: 0.52)
    private static let ink = Color(red: 0.22, green: 0.18, blue: 0.06)
    private static let fold: CGFloat = 22

    /// Open items first, then the ones ticked today.
    private var visible: [TodoItem] {
        let items = todos.filter { $0.isVisible(on: day) }
        return items.filter { !$0.isDone } + items.filter(\.isDone)
    }

    var body: some View {
        let visible = visible
        let doneCount = visible.count(where: \.isDone)

        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("To-do")
                    .font(.custom("Noteworthy-Bold", size: 26, relativeTo: .title2))
                if !visible.isEmpty {
                    Text("\(doneCount)/\(visible.count)")
                        .font(.custom("Noteworthy-Light", size: 16, relativeTo: .subheadline))
                        .opacity(0.7)
                }
                Spacer()
                if doneCount > 0 {
                    Button("Clear done") {
                        withAnimation { visible.filter(\.isDone).forEach(context.delete) }
                    }
                    .font(.caption.weight(.semibold))
                    .buttonStyle(.borderless)
                    .tint(Self.ink.opacity(0.7))
                }
            }

            ForEach(visible) { item in
                TodoLine(item: item, ink: Self.ink) { viewing = item }
                    .contextMenu {
                        if CameraPicker.isAvailable {
                            Button(item.photoData == nil ? "Take Photo" : "Retake Photo", systemImage: "camera") {
                                photoTarget = item
                                isShowingCamera = true
                            }
                        }
                        Button(item.photoData == nil ? "Attach Photo" : "Replace Photo", systemImage: "photo.on.rectangle") {
                            photoTarget = item
                            isShowingLibrary = true
                        }
                        if item.photoData != nil {
                            Button("Remove Photo", systemImage: "photo.badge.minus") {
                                withAnimation { item.photoData = nil }
                            }
                        }
                        Divider()
                        Button("Delete", systemImage: "trash", role: .destructive) {
                            withAnimation { context.delete(item) }
                        }
                    }
            }

            if let stagedPhoto {
                HStack(spacing: 8) {
                    PolaroidPhoto(data: stagedPhoto, width: 56, tilt: -3)
                        .padding(.vertical, 4)
                    Text("Photo will be attached to your next to-do")
                        .font(.caption)
                        .opacity(0.7)
                    Spacer(minLength: 0)
                    Button("Remove photo", systemImage: "xmark.circle.fill") {
                        withAnimation { self.stagedPhoto = nil }
                    }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.borderless)
                }
                .padding(.leading, 34)
                .transition(.opacity.combined(with: .scale(scale: 0.9, anchor: .leading)))
            }

            HStack(spacing: 10) {
                Image(systemName: "plus")
                    .fontWeight(.semibold)
                    .frame(width: 24)
                TextField(
                    "New to-do",
                    text: $text,
                    prompt: Text("Type a to-do and press return").foregroundStyle(Self.ink.opacity(0.45))
                )
                .font(.custom("Noteworthy-Light", size: 18, relativeTo: .body))
                .focused($focused)
                .submitLabel(.done)
                .onSubmit(add)

                Menu {
                    if CameraPicker.isAvailable {
                        Button("Take Photo", systemImage: "camera") {
                            photoTarget = nil
                            isShowingCamera = true
                        }
                    }
                    Button("Choose from Library", systemImage: "photo.on.rectangle") {
                        photoTarget = nil
                        isShowingLibrary = true
                    }
                } label: {
                    Image(systemName: stagedPhoto == nil ? "camera" : "camera.fill")
                        .font(.title3)
                        .accessibilityLabel("Attach photo")
                }
                .buttonStyle(.borderless)
            }
            .opacity(focused || visible.isEmpty ? 1 : 0.8)
        }
        .foregroundStyle(Self.ink)
        .tint(Self.ink)
        .padding(.horizontal, 20)
        .padding(.top, 26)
        .padding(.bottom, 20 + Self.fold / 2)
        .background {
            FoldedCorner(fold: Self.fold)
                .fill(Self.paper.gradient)
                .overlay { FoldFlap(fold: Self.fold).fill(Self.paper.mix(with: .brown, by: 0.25)) }
                .shadow(color: .black.opacity(0.18), radius: 6, x: 2, y: 5)
        }
        .overlay(alignment: .top) {
            // A strip of tape holding the note down.
            Rectangle()
                .fill(Color.white.opacity(0.55))
                .frame(width: 90, height: 24)
                .rotationEffect(.degrees(-4))
                .offset(y: -10)
                .shadow(color: .black.opacity(0.08), radius: 1, y: 1)
        }
        .rotationEffect(.degrees(-1))
        .animation(.snappy, value: visible.map(\.persistentModelID))
        .animation(.snappy, value: visible.map(\.isDone))
        .animation(.snappy, value: stagedPhoto)
        .fullScreenCover(isPresented: $isShowingCamera) {
            CameraPicker { attach($0) }
                .ignoresSafeArea()
        }
        .photosPicker(isPresented: $isShowingLibrary, selection: $libraryItem, matching: .images)
        .onChange(of: libraryItem) {
            guard let libraryItem else { return }
            Task {
                if let data = try? await libraryItem.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    attach(image)
                }
                self.libraryItem = nil
            }
        }
        .fullScreenCover(item: $viewing) { item in
            if let data = item.photoData {
                PhotoViewer(data: data, title: item.title)
            }
        }
    }

    private func attach(_ image: UIImage) {
        guard let data = image.jpegForStorage() else { return }
        if let photoTarget {
            photoTarget.photoData = data
            self.photoTarget = nil
        } else {
            stagedPhoto = data
            focused = true
        }
    }

    private func add() {
        var title = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.isEmpty, stagedPhoto != nil { title = "Photo" }
        guard !title.isEmpty else { return }
        let item = TodoItem(title: title)
        item.photoData = stagedPhoto
        context.insert(item)
        text = ""
        stagedPhoto = nil
        focused = true
    }
}

private struct TodoLine: View {
    let item: TodoItem
    let ink: Color
    let onShowPhoto: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Button {
                item.toggle()
            } label: {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Image(systemName: item.isDone ? "checkmark.square.fill" : "square")
                        .font(.title3)
                        .frame(width: 24)
                        .contentTransition(.symbolEffect(.replace))
                    Text(item.title)
                        .font(.custom("Noteworthy-Light", size: 18, relativeTo: .body))
                        .strikethrough(item.isDone, color: ink)
                        .opacity(item.isDone ? 0.5 : 1)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(ink)
            .sensoryFeedback(.success, trigger: item.isDone) { _, new in new }
            .accessibilityValue(item.isDone ? "Done" : "Not done")

            if let data = item.photoData {
                Button(action: onShowPhoto) {
                    // Alternate the tilt per item so a stack of prints looks hand-placed.
                    PolaroidPhoto(data: data, width: 52, tilt: [4, -3, 2][Int(item.createdAt.timeIntervalSince1970 * 1000) % 3])
                        .padding(.vertical, 2)
                        .opacity(item.isDone ? 0.6 : 1)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("View photo")
            }
        }
    }
}

/// Note outline with the bottom-right corner cut off for the fold.
private struct FoldedCorner: Shape {
    let fold: CGFloat

    func path(in rect: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: rect.minX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - fold))
            p.addLine(to: CGPoint(x: rect.maxX - fold, y: rect.maxY))
            p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
            p.closeSubpath()
        }
    }
}

/// The curled-up flap that fills the cut corner.
private struct FoldFlap: Shape {
    let fold: CGFloat

    func path(in rect: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: rect.maxX, y: rect.maxY - fold))
            p.addLine(to: CGPoint(x: rect.maxX - fold, y: rect.maxY - fold))
            p.addLine(to: CGPoint(x: rect.maxX - fold, y: rect.maxY))
            p.closeSubpath()
        }
    }
}

#Preview {
    TodoStickyNote(day: .now)
        .padding(30)
        .modelContainer(for: TodoItem.self, inMemory: true)
}
