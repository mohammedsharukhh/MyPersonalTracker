//
//  SplashView.swift
//  MyPersonalTracker
//
//  The opening animation: the app icon draws itself — the pulse line runs
//  out, the lime dot lands on the chime's top note, the name settles in — then
//  it opens into the app. About two seconds; a tap skips it. The sound follows
//  the silent switch and can be turned off in Settings.
//

import AVFoundation
import SwiftUI

enum SplashSettings {
    static let soundKey = "splashSoundEnabled"
}

struct SplashView: View {
    /// Whether the app behind is built yet; the splash leaves only once it is.
    var appReady = true
    /// Called once the animation has played, to build the app behind it.
    var onReadyForApp: () -> Void = {}
    let onFinish: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage(SplashSettings.soundKey) private var soundOn = true
    @State private var line: CGFloat = 0
    @State private var dot = false
    @State private var glow = false
    @State private var title = false
    @State private var leaving = false
    @State private var finished = false
    @State private var played = false
    @State private var mounted = false

    var body: some View {
        ZStack {
            SplashBackground()
            VStack(spacing: 34) {
                SplashMark(line: line, dot: dot, glow: glow)
                    .frame(width: 240, height: 240 * SplashMark.aspect)
                VStack(spacing: 8) {
                    Text("My Personal Tracker")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                    Text("Habits · Money · Study · Food")
                        .font(.system(.subheadline, design: .rounded, weight: .medium))
                        .opacity(0.7)
                }
                .foregroundStyle(.white)
                .opacity(title ? 1 : 0)
                .offset(y: title ? 0 : 14)
                .blur(radius: title ? 0 : 6)
            }
            .offset(y: -20)
        }
        .scaleEffect(leaving ? 1.12 : 1)
        .opacity(leaving ? 0 : 1)
        .contentShape(Rectangle())
        .onTapGesture { finish() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("My Personal Tracker")
        .accessibilityAddTraits(.isImage)
        .task { await run() }
        .onChange(of: appReady, initial: true) { mounted = appReady; leaveIfDone() }
    }

    private func run() async {
        if soundOn { playChime() }
        if reduceMotion {
            // No draw or pop; show the finished mark and fade out.
            line = 1; dot = true; title = true
            try? await Task.sleep(for: .seconds(1.1))
            finish()
            return
        }
        withAnimation(.timingCurve(0.22, 1, 0.36, 1, duration: 0.75)) { line = 1 }
        // The dot lands with the chime's top note (0.74 s in).
        try? await Task.sleep(for: .seconds(0.72))
        withAnimation(.spring(response: 0.32, dampingFraction: 0.55)) { dot = true }
        try? await Task.sleep(for: .seconds(0.2))
        withAnimation(.easeOut(duration: 0.5)) { title = true; glow = true }
        try? await Task.sleep(for: .seconds(0.7))
        finish()
    }

    /// The animation has played (or was tapped away): build the app, and leave once it's there.
    private func finish() {
        played = true
        onReadyForApp()
        leaveIfDone()
    }

    private func leaveIfDone() {
        guard played, mounted, !finished else { return }
        finished = true
        withAnimation(.easeIn(duration: 0.35)) { leaving = true }
        Task {
            try? await Task.sleep(for: .seconds(0.36))
            onFinish()
        }
    }

    private func playChime() { SplashChime.shared.play() }
}

/// Plays the chime off the main thread (audio session calls can block). Ambient:
/// silent with the ring/silent switch, and mixes with music already playing —
/// so the session never needs activating or handing back by hand; playing
/// starts it and nothing else was paused.
private final class SplashChime: @unchecked Sendable {
    static let shared = SplashChime()
    private let queue = DispatchQueue(label: "splash.chime", qos: .userInitiated)
    private var player: AVAudioPlayer?

    func play() {
        queue.async { [self] in
            guard let url = Bundle.main.url(forResource: "splash_chime", withExtension: "m4a") else { return }
            try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
            player = try? AVAudioPlayer(contentsOf: url)
            player?.volume = 0.8
            player?.play()
            // Let it ring out, then let go of the player.
            queue.asyncAfter(deadline: .now() + 3) { [self] in player?.stop(); player = nil }
        }
    }
}

private struct SplashBackground: View {
    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: 0x5156A3), Color(hex: 0x0B1347)], startPoint: .topLeading, endPoint: .bottomTrailing)
            RadialGradient(colors: [Color.white.opacity(0.10), .clear], center: .init(x: 0.5, y: 0.42), startRadius: 10, endRadius: 320)
        }
        .ignoresSafeArea()
    }
}

/// The icon, drawn live: the pulse line runs flat, spikes, then climbs to the lime dot.
struct SplashMark: View {
    var line: CGFloat
    var dot: Bool
    var glow: Bool

    // Traced from AppIcon.png, in its 1024-unit space: the line's corners, its
    // width, and the dot. `bounds` is the drawing including caps and dot.
    static let corners = [(149, 599), (314, 599), (390, 420), (468, 707), (550, 557), (819, 315)].map { CGPoint(x: $0.0, y: $0.1) }
    static let lineWidth: CGFloat = 66, dotRadius: CGFloat = 61
    static let bounds = CGRect(x: 116, y: 254, width: 764, height: 486)
    static var aspect: CGFloat { bounds.height / bounds.width }

    var body: some View {
        GeometryReader { geo in
            let s = geo.size.width / Self.bounds.width
            let lime = Color(hex: 0xBCE754)
            ZStack(alignment: .topLeading) {
                PulseLine()
                    .trim(from: 0, to: line)
                    .stroke(LinearGradient(stops: [
                        .init(color: Color(hex: 0x00DFF4), location: 0.04),
                        .init(color: Color(hex: 0x5AE2C0), location: 0.37),
                        .init(color: Color(hex: 0xA5E673), location: 0.82),
                        .init(color: lime, location: 0.92)
                    ], startPoint: .leading, endPoint: .trailing),
                    style: StrokeStyle(lineWidth: Self.lineWidth * s, lineCap: .round, lineJoin: .round))
                Circle()
                    .fill(lime)
                    .frame(width: Self.dotRadius * 2 * s, height: Self.dotRadius * 2 * s)
                    .shadow(color: lime.opacity(glow ? 0.7 : 0), radius: glow ? 22 : 0)
                    .scaleEffect(dot ? 1 : 0.2)
                    .opacity(dot ? 1 : 0)
                    .position(x: (819 - Self.bounds.minX) * s, y: (315 - Self.bounds.minY) * s)
            }
        }
    }
}

/// The pulse line through `SplashMark.corners`, scaled to fill its rect.
private struct PulseLine: Shape {
    func path(in rect: CGRect) -> Path {
        let b = SplashMark.bounds, s = rect.width / b.width
        let points = SplashMark.corners.map { CGPoint(x: rect.minX + ($0.x - b.minX) * s, y: rect.minY + ($0.y - b.minY) * s) }
        return Path { $0.addLines(points) }
    }
}

#Preview {
    SplashView {}
}
