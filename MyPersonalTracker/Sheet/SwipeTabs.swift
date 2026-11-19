//
//  SwipeTabs.swift
//  MyPersonalTracker
//
//  The tabs as pages you can swipe between (Study → swipe left → Food),
//  with a glass tab bar that follows along. Tapping a tab still jumps to it.
//  A sideways swipe on something that scrolls sideways itself (chips, the
//  week strip) scrolls that first. A raised button sits in the bar between
//  the tabs (Pay with UPI) without being a tab itself.
//

import SwiftData
import SwiftUI

struct SwipeTabItem: Identifiable {
    let tag: Int
    let title: String
    let symbol: String
    let world: TabWorld
    let content: AnyView
    var id: Int { tag }

    init(_ title: String, systemImage: String, tag: Int, world: TabWorld = .neutral, @ViewBuilder content: () -> some View) {
        self.tag = tag
        self.title = title
        self.symbol = systemImage
        self.world = world
        self.content = AnyView(content().environment(\.tabWorld, world))
    }
}

struct SwipeTabs<Center: View>: View {
    @Binding var selection: Int
    let items: [SwipeTabItem]
    /// The raised button sits in the middle, after this many tabs.
    var centerAfter = 3
    @ViewBuilder var center: Center
    @Environment(\.modelContext) private var modelContext
    @State private var barHeight: CGFloat = 64

    var body: some View {
        // Each tab in its own view controller (UIPageViewController), so every
        // navigation bar keeps its system margins and each tab keeps its state.
        TabPageController(selection: $selection, pages: items.map { item in
            AnyView(item.content.environment(\.modelContext, modelContext).tint(SheetTheme.navy))
        }, bottomInset: barHeight)
        .ignoresSafeArea()
        .overlay(alignment: .bottom) {
            SwipeTabBar(selection: $selection, items: items, centerAfter: centerAfter, center: center)
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { barHeight = $0 }
        }
        .sensoryFeedback(.selection, trigger: selection)
    }
}

private struct TabPageController: UIViewControllerRepresentable {
    @Binding var selection: Int
    let pages: [AnyView]
    /// Room for the floating tab bar, so the end of each page can scroll clear of it.
    let bottomInset: CGFloat

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIPageViewController {
        let pager = UIPageViewController(transitionStyle: .scroll, navigationOrientation: .horizontal)
        pager.dataSource = context.coordinator
        pager.delegate = context.coordinator
        pager.view.backgroundColor = .clear
        context.coordinator.controllers = pages.map { page in
            let host = UIHostingController(rootView: page)
            host.view.backgroundColor = .clear
            host.additionalSafeAreaInsets.bottom = bottomInset
            return host
        }
        let start = min(max(selection, 0), pages.count - 1)
        pager.setViewControllers([context.coordinator.controllers[start]], direction: .forward, animated: false)
        context.coordinator.shown = start
        return pager
    }

    func updateUIViewController(_ pager: UIPageViewController, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        for host in coordinator.controllers where host.additionalSafeAreaInsets.bottom != bottomInset {
            host.additionalSafeAreaInsets.bottom = bottomInset
        }
        // A tab tapped (or opened from elsewhere): slide to it.
        let target = min(max(selection, 0), coordinator.controllers.count - 1)
        guard target != coordinator.shown, !coordinator.isSwiping else { return }
        let direction: UIPageViewController.NavigationDirection = target > coordinator.shown ? .forward : .reverse
        coordinator.shown = target
        pager.setViewControllers([coordinator.controllers[target]], direction: direction, animated: true)
    }

    final class Coordinator: NSObject, UIPageViewControllerDataSource, UIPageViewControllerDelegate {
        var parent: TabPageController
        var controllers: [UIViewController] = []
        var shown = 0
        var isSwiping = false

        init(_ parent: TabPageController) { self.parent = parent }

        func pageViewController(_ pager: UIPageViewController, viewControllerBefore vc: UIViewController) -> UIViewController? {
            guard let i = controllers.firstIndex(of: vc), i > 0 else { return nil }
            return controllers[i - 1]
        }

        func pageViewController(_ pager: UIPageViewController, viewControllerAfter vc: UIViewController) -> UIViewController? {
            guard let i = controllers.firstIndex(of: vc), i < controllers.count - 1 else { return nil }
            return controllers[i + 1]
        }

        func pageViewController(_ pager: UIPageViewController, willTransitionTo pending: [UIViewController]) {
            isSwiping = true
        }

        func pageViewController(_ pager: UIPageViewController, didFinishAnimating finished: Bool,
                                previousViewControllers: [UIViewController], transitionCompleted completed: Bool) {
            isSwiping = false
            guard completed, let current = pager.viewControllers?.first, let i = controllers.firstIndex(of: current) else { return }
            shown = i
            if parent.selection != i { parent.selection = i }
        }
    }
}

/// A floating glass bar in the style of the system one.
private struct SwipeTabBar<Center: View>: View {
    @Binding var selection: Int
    let items: [SwipeTabItem]
    let centerAfter: Int
    let center: Center
    @Namespace private var highlight

    var body: some View {
        // The tabs either side each take half the bar, so the Pay button sits dead centre.
        HStack(spacing: 0) {
            HStack(spacing: 0) { ForEach(items.prefix(centerAfter)) { tab($0) } }
                .frame(maxWidth: .infinity)
            Color.clear.frame(width: 60, height: 1)
            HStack(spacing: 0) { ForEach(items.dropFirst(centerAfter)) { tab($0) } }
                .frame(maxWidth: .infinity)
        }
        .padding(5)
        .glassEffect(.regular.interactive(), in: .capsule)
        // On top of the glass (inside it, the glass draws over it), level with the tabs.
        .overlay { center }
        .padding(.horizontal, 20)
        .padding(.bottom, 2)
        .animation(.snappy, value: selection)
    }

    private func tab(_ item: SwipeTabItem) -> some View {
        let selected = item.tag == selection
        return Button {
            withAnimation(.snappy) { selection = item.tag }
        } label: {
            VStack(spacing: 3) {
                Image(systemName: item.symbol).font(.system(size: 19, weight: .semibold)).frame(height: 24)
                Text(item.title).font(.system(size: 10, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(selected ? item.world.accent : Color(.label))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 7)
            .background {
                if selected {
                    Capsule().fill(item.world.accent.opacity(0.15))
                        .matchedGeometryEffect(id: "selected", in: highlight)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.title)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}
