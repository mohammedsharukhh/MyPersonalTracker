//
//  Popup.swift
//  MyPersonalTracker
//
//  The app's own pop-up, used for every confirmation and message instead of
//  the system alert: an icon, a title, a line or two of explanation, and the
//  buttons — the main one red when it deletes something. It opens in its own
//  window above everything (sheets included), dims what's behind, and a tap
//  outside cancels.
//
//  Use it like an alert:
//      .popup(isPresented: $confirmDelete, Popup(title: "Delete this expense?", ...) { delete() })
//

import SwiftUI
import UIKit

struct Popup {
    enum Style { case destructive, standard, info }

    var title: String
    var message: String?
    var symbol: String?
    var style: Style = .standard
    var confirmTitle = "OK"
    var cancelTitle: String? = "Cancel"
    /// A text field (e.g. a name), passed to `onConfirmText`.
    var field: (placeholder: String, initial: String)?
    var onConfirm: () -> Void = {}
    var onConfirmText: (String) -> Void = { _ in }
    /// The second button only (not a tap outside).
    var onCancel: () -> Void = {}

    init(title: String, message: String? = nil, symbol: String? = nil, style: Style = .standard,
         confirmTitle: String = "OK", cancelTitle: String? = "Cancel", onConfirm: @escaping () -> Void = {}, onCancel: @escaping () -> Void = {}) {
        self.title = title; self.message = message; self.symbol = symbol; self.style = style
        self.confirmTitle = confirmTitle; self.cancelTitle = cancelTitle; self.onConfirm = onConfirm; self.onCancel = onCancel
    }

    /// "Delete …?" with a red button.
    static func delete(_ title: String, message: String? = nil, confirmTitle: String = "Delete", onConfirm: @escaping () -> Void) -> Popup {
        Popup(title: title, message: message, symbol: "trash.fill", style: .destructive, confirmTitle: confirmTitle, onConfirm: onConfirm)
    }

    /// Something went wrong — one button.
    static func error(_ title: String, message: String?) -> Popup {
        Popup(title: title, message: message, symbol: "exclamationmark.triangle.fill", style: .info, confirmTitle: "OK", cancelTitle: nil)
    }

    /// Asks for a line of text.
    static func text(_ title: String, message: String? = nil, placeholder: String, initial: String = "", confirmTitle: String = "Save",
                     onConfirm: @escaping (String) -> Void) -> Popup {
        var p = Popup(title: title, message: message, symbol: "pencil", style: .standard, confirmTitle: confirmTitle)
        p.field = (placeholder, initial)
        p.onConfirmText = onConfirm
        return p
    }
}

extension View {
    /// Shows `popup` while `isPresented` is true; any button or a tap outside sets it back to false.
    func popup(isPresented: Binding<Bool>, _ popup: @autoclosure @escaping () -> Popup) -> some View {
        onChange(of: isPresented.wrappedValue) { _, shown in
            if shown { PopupCenter.shared.show(popup()) { isPresented.wrappedValue = false } } else { PopupCenter.shared.hide() }
        }
    }
}

/// Puts the pop-up in a window above the app's own, so it covers sheets too.
@MainActor
final class PopupCenter {
    static let shared = PopupCenter()
    private var window: UIWindow?
    private var onClose: (() -> Void)?
    private let model = PopupModel()

    func show(_ popup: Popup, onClose: @escaping () -> Void) {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive })
            ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else { onClose(); return }
        // The same pop-up asked for again while it's up: keep it. (A `.popup` on a Form
        // Section is copied onto every row, so one tap can ask several times — closing the
        // first would set its binding back to false and hide it at once.)
        if model.visible, let current = model.popup, current.title == popup.title, current.message == popup.message {
            let earlier = self.onClose
            self.onClose = { earlier?(); onClose() }
            return
        }
        self.onClose?()
        self.onClose = onClose
        model.popup = popup
        model.visible = false
        if window == nil {
            let w = UIWindow(windowScene: scene)
            w.windowLevel = .alert + 1
            w.backgroundColor = .clear
            let host = UIHostingController(rootView: PopupLayer(model: model) { [weak self] in self?.hide() })
            host.view.backgroundColor = .clear
            w.rootViewController = host
            window = w
        }
        window?.makeKeyAndVisible()
        UIImpactFeedbackGenerator(style: popup.style == .destructive ? .medium : .light).impactOccurred()
        withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) { model.visible = true }
    }

    func hide() {
        guard window != nil, model.visible else { return }
        withAnimation(.easeIn(duration: 0.16)) { model.visible = false }
        let close = onClose
        onClose = nil
        close?()
        Task {
            try? await Task.sleep(for: .seconds(0.18))
            guard !self.model.visible else { return }
            self.window?.isHidden = true
            self.window = nil
        }
    }
}

@Observable
final class PopupModel {
    var popup: Popup?
    var visible = false
}

private struct PopupLayer: View {
    let model: PopupModel
    let close: () -> Void

    var body: some View {
        ZStack {
            if model.visible, let popup = model.popup {
                Color.black.opacity(0.38)
                    .ignoresSafeArea()
                    .onTapGesture { close() }
                    .transition(.opacity)
                PopupCard(popup: popup, close: close)
                    .padding(.horizontal, 28)
                    .transition(.scale(scale: 0.88).combined(with: .opacity))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityAddTraits(.isModal)
    }
}

private struct PopupCard: View {
    let popup: Popup
    let close: () -> Void
    @State private var text = ""
    @FocusState private var focused: Bool
    @Environment(\.colorScheme) private var scheme

    private var tint: Color {
        switch popup.style {
        case .destructive: SheetTheme.bad
        case .info: SheetTheme.warn
        case .standard: SheetTheme.navy
        }
    }

    var body: some View {
        VStack(spacing: 16) {
            if let symbol = popup.symbol {
                Image(systemName: symbol)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 56, height: 56)
                    .background(tint.opacity(0.14), in: Circle())
            }
            VStack(spacing: 6) {
                Text(popup.title).font(.title3.weight(.bold)).multilineTextAlignment(.center)
                if let message = popup.message, !message.isEmpty {
                    Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            if let field = popup.field {
                TextField(field.placeholder, text: $text)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit(confirm)
                    .padding(12)
                    .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .onAppear { text = field.initial; focused = true }
            }
            VStack(spacing: 8) {
                Button(action: confirm) {
                    Text(popup.confirmTitle).font(.headline).frame(maxWidth: .infinity).padding(.vertical, 13)
                        .background(popup.style == .destructive ? Color(hex: 0xDC2626) : Color(light: 0x1F3864, dark: 0x3B5BA9),
                                    in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
                .disabled(popup.field != nil && text.trimmingCharacters(in: .whitespaces).isEmpty)
                if let cancel = popup.cancelTitle {
                    Button { popup.onCancel(); close() } label: {
                        Text(cancel).font(.headline).frame(maxWidth: .infinity).padding(.vertical, 12)
                            .foregroundStyle(Color(.label))
                            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(22)
        .frame(maxWidth: 360)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(.white.opacity(scheme == .dark ? 0.08 : 0), lineWidth: 1))
        .shadow(color: .black.opacity(scheme == .dark ? 0.5 : 0.18), radius: 30, y: 12)
    }

    private func confirm() {
        if popup.field != nil {
            let value = text.trimmingCharacters(in: .whitespaces)
            guard !value.isEmpty else { return }
            popup.onConfirmText(value)
        } else {
            popup.onConfirm()
        }
        close()
    }
}
