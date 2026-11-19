//
//  LensScanner.swift
//  MyPersonalTracker
//
//  The QR scanner. Calm until it matters:
//   • Opening — the camera fades in and the four corners draw themselves.
//   • Looking — aurora-lit corners breathing gently, a soft band of light drifting
//     through the frame, the picture's edges softly darkened.
//   • A code in view — the corners glide onto it, wherever and however tilted, and follow it.
//   • A UPI code — they lock on in white and green, a crystal chime and a buzz, and it goes
//     straight into Pay with who you're paying (and the amount, if the QR has one) filled in.
//  Also: scan from Photos (a QR someone sent), pinch or double-tap to zoom, the torch.
//  Reduce Motion: no breathing or drifting light; changes fade. Mirrors LensScanner.kt.
//

import AVFoundation
import PhotosUI
import SwiftUI
import Vision

struct LensScanner: View {
    let onFound: (UpiScan) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The scanner counts as unavailable until camera access is granted: ask first.
    @State private var access = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var appeared = false
    /// The code in view, in screen points — the corners follow it.
    @State private var tracked: Quad?
    @State private var result: LensResult?
    @State private var countdown: CGFloat = 0
    @State private var torchOn = false
    @State private var zoom: CGFloat = 1
    @State private var zoomAtPinch: CGFloat = 1
    @State private var zoomShownAt: Date?
    @State private var photo: PhotosPickerItem?
    @State private var control = ScannerControl()
    /// A non-UPI code just read: not again for a moment.
    @State private var ignored: (text: String, at: Date)?

    private var cameraReady: Bool { access == .authorized && control.device != nil }

    var body: some View {
        GeometryReader { geo in
            let home = Quad.square(in: geo.size)
            ZStack {
                Color.black
                if cameraReady {
                    QRScanner(control: control, onItems: { items in see(items, size: geo.size) })
                        .opacity(appeared ? 1 : 0)
                        .scaleEffect(appeared || reduceMotion ? 1 : 1.06)
                        .gesture(pinch)
                        .onTapGesture(count: 2) { setZoom(zoom < 1.8 ? 2 : 1) }
                }
                // The edges of the picture, softly darkened around the frame.
                RadialGradient(colors: [.clear, .black.opacity(0.62)], center: UnitPoint(x: 0.5, y: Quad.centerY),
                               startRadius: home.width * 0.62, endRadius: max(geo.size.width, geo.size.height) * 0.78)
                    .allowsHitTesting(false)
                Finder(quad: tracked ?? home, home: home, state: finderState, appeared: appeared, reduceMotion: reduceMotion)
                    .allowsHitTesting(false)
                if let zoomShownAt, Date.now.timeIntervalSince(zoomShownAt) < 1.4 {
                    Text(String(format: "%.1f×", zoom))
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .glassEffect(.regular, in: Capsule())
                        .position(x: geo.size.width / 2, y: home.bottomLeft.y + 34)
                        .transition(.opacity)
                }
                chrome(insets: geo.safeAreaInsets)
                    .opacity(appeared ? 1 : 0)
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .statusBarHidden()
        .sensoryFeedback(trigger: result) { _, new in
            switch new {
            case .upi: .success
            case .other: .warning
            case nil: nil
            }
        }
        .task {
            if access == .notDetermined {
                _ = await AVCaptureDevice.requestAccess(for: .video)
                access = AVCaptureDevice.authorizationStatus(for: .video)
            }
            withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .easeOut(duration: 0.7)) { appeared = true }
            #if DEBUG
            // `-lensDemo`: a code already read, for screenshots (the simulator has no camera).
            if ProcessInfo.processInfo.arguments.contains("-lensDemo") {
                try? await Task.sleep(for: .seconds(1))
                withAnimation { result = .upi(UpiScan(payee: UpiPayee(vpa: "freshmart@okicici", name: "FreshMart Supermarket"), amount: 250)) }
                withAnimation(.linear(duration: 1.8)) { countdown = 0.6 }
            }
            #endif
        }
        .onChange(of: photo) { _, item in if let item { Task { await read(item) } } }
        .onDisappear { control.stop() }
    }

    private var finderState: Finder.State {
        switch result {
        case .upi: .locked
        case .other: .wrong
        case nil: tracked == nil ? .looking : .tracking
        }
    }

    // MARK: Chrome: top buttons, the hint or the result card, bottom tools

    private func chrome(insets: EdgeInsets) -> some View {
        VStack(spacing: 0) {
            HStack {
                glassButton("xmark", label: "Close") { dismiss() }
                Spacer()
                Text("Scan & Pay").font(.headline).foregroundStyle(.white)
                Spacer()
                if cameraReady, control.hasTorch {
                    glassButton(torchOn ? "flashlight.on.fill" : "flashlight.off.fill", label: torchOn ? "Torch off" : "Torch on", lit: torchOn) {
                        torchOn.toggle()
                        control.setTorch(torchOn)
                    }
                } else {
                    Color.clear.frame(width: 48, height: 48)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, insets.top + 4)

            Spacer()

            if let result {
                ResultCard(result: result, countdown: countdown, onPay: { finish() }, onAgain: { reset() })
                    .padding(.horizontal, 16)
                    .transition(reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
            } else {
                VStack(spacing: 18) {
                    Text(cameraReady || access == .notDetermined ? "Point at any UPI QR" : "Camera not available")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .glassEffect(.regular, in: Capsule())
                    HStack(spacing: 14) {
                        PhotosPicker(selection: $photo, matching: .images) {
                            Label("From Photos", systemImage: "photo.on.rectangle.angled")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 18).frame(height: 48)
                                .glassEffect(.regular.interactive(), in: Capsule())
                        }
                        .accessibilityHint("Scan a QR code from a screenshot or photo")
                        if cameraReady {
                            Button { setZoom(zoom < 1.8 ? 2 : 1) } label: {
                                Text(zoom < 1.8 ? "2×" : "1×")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundStyle(.white)
                                    .frame(width: 48, height: 48)
                                    .glassEffect(.regular.interactive(), in: Circle())
                            }
                            .accessibilityLabel(zoom < 1.8 ? "Zoom in" : "Zoom out")
                        }
                    }
                }
                .transition(.opacity)
            }
        }
        .padding(.bottom, insets.bottom + 16)
        .animation(reduceMotion ? .easeInOut(duration: 0.2) : .spring(response: 0.45, dampingFraction: 0.86), value: result)
    }

    private func glassButton(_ symbol: String, label: String, lit: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(lit ? .yellow : .white)
                .frame(width: 48, height: 48)
                .glassEffect(.regular.interactive(), in: Circle())
        }
        .accessibilityLabel(label)
    }

    private var pinch: some Gesture {
        MagnifyGesture()
            .onChanged { value in setZoom(zoomAtPinch * value.magnification, animated: false) }
            .onEnded { _ in zoomAtPinch = zoom }
    }

    private func setZoom(_ value: CGFloat, animated: Bool = true) {
        zoom = control.setZoom(value)
        zoomAtPinch = zoom
        withAnimation(.easeOut(duration: 0.2)) { zoomShownAt = .now }
        Task {
            try? await Task.sleep(for: .seconds(1.5))
            withAnimation(.easeOut(duration: 0.3)) { if let at = zoomShownAt, Date.now.timeIntervalSince(at) >= 1.4 { zoomShownAt = nil } }
        }
    }

    // MARK: Reading

    /// What the camera sees: follow the first code; read it once.
    private func see(_ items: [(text: String, quad: Quad)], size: CGSize) {
        guard result == nil || result?.isUPI == false else { return }
        guard let first = items.first else {
            withAnimation(follow) { tracked = nil }
            return
        }
        withAnimation(follow) { tracked = first.quad.padded(14) }
        guard result == nil else { return }
        if let scan = UpiScan.parse(first.text) {
            lock(scan)
        } else if ignored?.text != first.text || Date.now.timeIntervalSince(ignored?.at ?? .distantPast) > 4 {
            ignored = (first.text, .now)
            result = .other(first.text)
            Task {
                try? await Task.sleep(for: .seconds(1.8))
                if case .other = result { withAnimation { result = nil } }
            }
        }
    }

    private var follow: Animation { reduceMotion ? .easeOut(duration: 0.15) : .spring(response: 0.32, dampingFraction: 0.82) }

    private func lock(_ scan: UpiScan) {
        control.pause()
        result = .upi(scan)
        LensSound.shared.play()
        // Straight into Pay with it filled in: no card to tap, nothing timing out. Pay's own Scan button rescans.
        finish()
    }

    private func finish() {
        guard case .upi(let scan) = result else { return }
        if torchOn { control.setTorch(false) }
        onFound(scan)
    }

    private func reset() {
        withAnimation { result = nil; tracked = nil }
        countdown = 0
        control.resume()
    }

    /// A QR from Photos (a screenshot someone sent).
    private func read(_ item: PhotosPickerItem) async {
        photo = nil
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }
        let text = await Task.detached { QRFromImage.read(data) }.value
        guard let text else {
            result = .other("")
            try? await Task.sleep(for: .seconds(1.8))
            if case .other = result { withAnimation { result = nil } }
            return
        }
        if let scan = UpiScan.parse(text) { lock(scan) } else { result = .other(text) }
    }
}

/// What was read.
enum LensResult: Equatable {
    case upi(UpiScan)
    case other(String)

    var isUPI: Bool { if case .upi = self { true } else { false } }
}

// MARK: - The finder

/// Four corners: aurora-lit while looking, white as they follow a code, green once it's a UPI code.
private struct Finder: View {
    enum State { case looking, tracking, locked, wrong }
    let quad: Quad
    let home: Quad
    let state: State
    let appeared: Bool
    let reduceMotion: Bool

    static let aurora: [Color] = [Color(red: 0.25, green: 0.95, blue: 0.70), Color(red: 0.30, green: 0.80, blue: 1.0),
                                  Color(red: 0.62, green: 0.55, blue: 1.0), Color(red: 0.25, green: 0.95, blue: 0.70)]
    static let green = Color(red: 0.22, green: 0.90, blue: 0.55)

    var body: some View {
        TimelineView(.animation(paused: reduceMotion || state == .locked)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let breathe = state == .looking && !reduceMotion ? 1 + 0.018 * sin(t * 2.2) : 1
            ZStack {
                // A soft band of light drifting through the empty frame.
                if state == .looking && !reduceMotion {
                    let band = home.height * 0.45
                    let y = home.center.y + (home.height / 2 - band / 3) * sin(t * 1.1)
                    LinearGradient(colors: [.clear, Self.aurora[1].opacity(0.16), Self.aurora[0].opacity(0.22), .clear],
                                   startPoint: .top, endPoint: .bottom)
                        .frame(width: home.width - 8, height: band)
                        .position(x: home.center.x, y: y)
                        .mask(RoundedRectangle(cornerRadius: 30, style: .continuous).frame(width: home.width - 8, height: home.height - 8)
                            .position(home.center))
                        .blendMode(.plusLighter)
                        .transition(.opacity)
                }
                // Locked: a glow over the code.
                if state == .locked {
                    QuadShape(quad: quad).fill(Self.green.opacity(0.09))
                        .blur(radius: 10)
                        .transition(.opacity)
                }
                let corners = CornerShape(quad: quad)
                    .trim(from: 0, to: appeared ? 1 : 0)
                corners
                    .stroke(style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
                    .foregroundStyle(stroke(t))
                    .shadow(color: glow.opacity(0.75), radius: state == .locked ? 16 : 9)
                    .scaleEffect(breathe, anchor: UnitPoint(x: 0.5, y: Quad.centerY))
            }
            .animation(.easeInOut(duration: 0.35), value: state)
        }
    }

    private var glow: Color {
        switch state {
        case .locked: Self.green
        case .wrong: .orange
        case .tracking: .white
        case .looking: Self.aurora[1]
        }
    }

    private func stroke(_ t: Double) -> AnyShapeStyle {
        switch state {
        case .locked: AnyShapeStyle(LinearGradient(colors: [.white, Self.green], startPoint: .topLeading, endPoint: .bottomTrailing))
        case .wrong: AnyShapeStyle(Color.orange)
        case .tracking: AnyShapeStyle(Color.white)
        case .looking: AnyShapeStyle(AngularGradient(colors: Self.aurora, center: .center, angle: .degrees(reduceMotion ? 0 : t * 40)))
        }
    }
}

// MARK: - The result card

private struct ResultCard: View {
    let result: LensResult
    let countdown: CGFloat
    let onPay: () -> Void
    let onAgain: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            switch result {
            case .upi(let scan):
                HStack(spacing: 14) {
                    Text(initials(scan.payee.label))
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(width: 52, height: 52)
                        .background(LinearGradient(colors: [Color(red: 0.18, green: 0.62, blue: 0.42), Color(red: 0.12, green: 0.30, blue: 0.62)],
                                                   startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(scan.payee.label).font(.title3.weight(.bold)).lineLimit(2).minimumScaleFactor(0.85)
                            Image(systemName: "checkmark.seal.fill").foregroundStyle(Finder.green).font(.subheadline)
                                .accessibilityLabel("UPI code")
                        }
                        Text(scan.payee.vpa).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    if let amount = scan.amount {
                        Text("₹" + SheetFormat.number(amount, digits: amount.rounded() == amount ? 0 : 2))
                            .font(.title3.weight(.bold).monospacedDigit())
                    }
                }
                HStack(spacing: 10) {
                    Button(action: onAgain) {
                        Text("Scan again").font(.headline).frame(maxWidth: .infinity).frame(height: 50)
                    }
                    .buttonStyle(.plain)
                    .glassEffect(.regular.interactive(), in: Capsule())
                    Button(action: onPay) {
                        HStack(spacing: 10) {
                            Text("Pay").font(.headline)
                            ZStack {
                                Circle().stroke(.white.opacity(0.3), lineWidth: 2.5)
                                Circle().trim(from: 0, to: countdown).stroke(.white, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                                    .rotationEffect(.degrees(-90))
                            }
                            .frame(width: 18, height: 18)
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity).frame(height: 50)
                        .background(Finder.green.gradient, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Opens the payment, filled in")
                }
            case .other(let text):
                HStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill").font(.title2).foregroundStyle(.orange)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(text.isEmpty ? "No QR code in that photo" : "Not a UPI payment code").font(.headline)
                        Text(text.isEmpty ? "Try a clearer screenshot." : (text.hasPrefix("http") ? "It's a website link." : "Point at a shop's or a person's UPI QR."))
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .foregroundStyle(.white)
        .padding(18)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
    }

    private func initials(_ name: String) -> String {
        let words = name.split(whereSeparator: { !$0.isLetter && !$0.isNumber }).prefix(2)
        let letters = words.compactMap(\.first).map(String.init).joined().uppercased()
        return letters.isEmpty ? "₹" : letters
    }
}

// MARK: - Geometry

/// Four points of a code (or the frame) on screen.
struct Quad: Equatable {
    var topLeft: CGPoint, topRight: CGPoint, bottomRight: CGPoint, bottomLeft: CGPoint

    /// Where the frame sits: a little above the middle.
    static let centerY: CGFloat = 0.42

    static func square(in size: CGSize) -> Quad {
        let side = min(size.width * 0.70, 290)
        let c = CGPoint(x: size.width / 2, y: size.height * centerY)
        return Quad(topLeft: CGPoint(x: c.x - side / 2, y: c.y - side / 2), topRight: CGPoint(x: c.x + side / 2, y: c.y - side / 2),
                    bottomRight: CGPoint(x: c.x + side / 2, y: c.y + side / 2), bottomLeft: CGPoint(x: c.x - side / 2, y: c.y + side / 2))
    }

    var points: [CGPoint] { [topLeft, topRight, bottomRight, bottomLeft] }
    var center: CGPoint { CGPoint(x: points.map(\.x).reduce(0, +) / 4, y: points.map(\.y).reduce(0, +) / 4) }
    var width: CGFloat { hypot(topRight.x - topLeft.x, topRight.y - topLeft.y) }
    var height: CGFloat { hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y) }
    var size: CGSize { CGSize(width: width, height: height) }

    /// Grown outward from the centre, so the corners sit just outside the code.
    func padded(_ by: CGFloat) -> Quad {
        let c = center
        func out(_ p: CGPoint) -> CGPoint {
            let d = hypot(p.x - c.x, p.y - c.y)
            guard d > 0 else { return p }
            return CGPoint(x: p.x + (p.x - c.x) / d * by * 1.41, y: p.y + (p.y - c.y) / d * by * 1.41)
        }
        return Quad(topLeft: out(topLeft), topRight: out(topRight), bottomRight: out(bottomRight), bottomLeft: out(bottomLeft))
    }
}

extension Quad: VectorArithmetic {
    static var zero: Quad { Quad(topLeft: .zero, topRight: .zero, bottomRight: .zero, bottomLeft: .zero) }

    private static func map(_ a: Quad, _ b: Quad, _ f: (CGFloat, CGFloat) -> CGFloat) -> Quad {
        func p(_ x: CGPoint, _ y: CGPoint) -> CGPoint { CGPoint(x: f(x.x, y.x), y: f(x.y, y.y)) }
        return Quad(topLeft: p(a.topLeft, b.topLeft), topRight: p(a.topRight, b.topRight),
                    bottomRight: p(a.bottomRight, b.bottomRight), bottomLeft: p(a.bottomLeft, b.bottomLeft))
    }

    static func + (a: Quad, b: Quad) -> Quad { map(a, b, +) }
    static func - (a: Quad, b: Quad) -> Quad { map(a, b, -) }
    mutating func scale(by rhs: Double) { self = Quad.map(self, self) { x, _ in x * rhs } }
    var magnitudeSquared: Double { points.reduce(0) { $0 + $1.x * $1.x + $1.y * $1.y } }
}

/// The four corners as rounded L's along the quad's edges (animatable, so they glide between shapes).
private struct CornerShape: Shape {
    var quad: Quad
    var animatableData: Quad {
        get { quad }
        set { quad = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let pts = quad.points
        for i in 0..<4 {
            let corner = pts[i], prev = pts[(i + 3) % 4], next = pts[(i + 1) % 4]
            func toward(_ q: CGPoint) -> (CGPoint, CGFloat) {
                let d = hypot(q.x - corner.x, q.y - corner.y)
                let l = min(d * 0.26, 46)
                return (CGPoint(x: corner.x + (q.x - corner.x) / max(d, 1) * l, y: corner.y + (q.y - corner.y) / max(d, 1) * l), l)
            }
            let (a, la) = toward(prev), (b, lb) = toward(next)
            p.move(to: a)
            p.addArc(tangent1End: corner, tangent2End: b, radius: min(22, min(la, lb) * 0.7))
            p.addLine(to: b)
        }
        return p
    }
}

private struct QuadShape: Shape {
    var quad: Quad
    var animatableData: Quad {
        get { quad }
        set { quad = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.addLines(quad.points)
        p.closeSubpath()
        return p
    }
}

// MARK: - Camera, photos, sound, torch

/// The camera, run by the app itself: AVFoundation's own QR detector, which also says
/// where the code's corners are. The torch, zoom and focus are on the same camera the
/// picture comes from, so turning the torch on can't stall the picture.
@MainActor final class ScannerControl: NSObject, AVCaptureMetadataOutputObjectsDelegate {
    let session = AVCaptureSession()
    private(set) var device: AVCaptureDevice?
    /// The preview, to turn detected corners into screen points.
    weak var preview: AVCaptureVideoPreviewLayer?
    var onItems: ([(text: String, quad: Quad)]) -> Void = { _ in }
    private let queue = DispatchQueue(label: "lens.camera", qos: .userInitiated)
    private var configured = false
    private var paused = false
    private var clearTask: Task<Void, Never>?

    override init() {
        device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
        super.init()
    }

    var hasTorch: Bool { device?.hasTorch ?? false }

    func start() {
        guard let device else { return }
        let session = session
        let needsSetup = !configured
        configured = true
        let output = AVCaptureMetadataOutput()
        if needsSetup {
            session.beginConfiguration()
            session.sessionPreset = .high
            if let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) { session.addInput(input) }
            if session.canAddOutput(output) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: .main)
                if output.availableMetadataObjectTypes.contains(.qr) { output.metadataObjectTypes = [.qr] }
            }
            session.commitConfiguration()
            Self.configureFocus(device)
        }
        queue.async { if !session.isRunning { session.startRunning() } }
    }

    func stop() {
        let session = session
        setTorch(false)
        queue.async { if session.isRunning { session.stopRunning() } }
    }

    func pause() { paused = true }
    func resume() { paused = false }

    func setTorch(_ on: Bool) {
        guard let device, device.hasTorch else { return }
        queue.async {
            guard (try? device.lockForConfiguration()) != nil else { return }
            if on, device.isTorchModeSupported(.on) { try? device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel) } else { device.torchMode = .off }
            device.unlockForConfiguration()
        }
    }

    @discardableResult
    func setZoom(_ value: CGFloat) -> CGFloat {
        guard let device else { return 1 }
        let z = min(max(value, 1), min(8, device.activeFormat.videoMaxZoomFactor))
        queue.async {
            guard (try? device.lockForConfiguration()) != nil else { return }
            device.ramp(toVideoZoomFactor: z, withRate: 12)
            device.unlockForConfiguration()
        }
        return z
    }

    /// Continuous focus, favouring things close up — QR codes are usually a hand's length away.
    private nonisolated static func configureFocus(_ device: AVCaptureDevice) {
        guard (try? device.lockForConfiguration()) != nil else { return }
        if device.isFocusModeSupported(.continuousAutoFocus) { device.focusMode = .continuousAutoFocus }
        if device.isAutoFocusRangeRestrictionSupported { device.autoFocusRangeRestriction = .near }
        if device.isExposureModeSupported(.continuousAutoExposure) { device.exposureMode = .continuousAutoExposure }
        device.unlockForConfiguration()
    }

    nonisolated func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
        MainActor.assumeIsolated {
            guard !paused, let preview else { return }
            let items: [(text: String, quad: Quad)] = objects.compactMap { object in
                guard let code = preview.transformedMetadataObject(for: object) as? AVMetadataMachineReadableCodeObject,
                      let text = code.stringValue, code.corners.count == 4 else { return nil }
                let c = code.corners
                return (text, Quad(topLeft: c[0], topRight: c[1], bottomRight: c[2], bottomLeft: c[3]))
            }
            onItems(items)
            // No more reports means the code has gone: let the corners go home.
            clearTask?.cancel()
            clearTask = Task { [weak self] in
                try? await Task.sleep(for: .milliseconds(350))
                guard !Task.isCancelled, let self, !self.paused else { return }
                self.onItems([])
            }
        }
    }
}

/// The camera picture, filling the screen.
struct QRScanner: UIViewRepresentable {
    let control: ScannerControl
    let onItems: ([(text: String, quad: Quad)]) -> Void

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.preview.session = control.session
        view.preview.videoGravity = .resizeAspectFill
        control.preview = view.preview
        control.onItems = onItems
        control.start()
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {
        control.onItems = onItems
    }

    static func dismantleUIView(_ view: PreviewView, coordinator: ()) {
        (view.preview.session).map { session in DispatchQueue.global(qos: .userInitiated).async { session.stopRunning() } }
    }
}

/// The first QR code in a picture.
enum QRFromImage {
    nonisolated static func read(_ data: Data) -> String? {
        guard let image = UIImage(data: data), let cg = image.cgImage else { return nil }
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]
        let handler = VNImageRequestHandler(cgImage: cg, orientation: CGImagePropertyOrientation(image.imageOrientation))
        try? handler.perform([request])
        return request.results?.compactMap(\.payloadStringValue).first
    }
}

/// The lock-on chime, quiet and mixed with whatever's playing; silent on silent.
final class LensSound: @unchecked Sendable {
    static let shared = LensSound()
    private let queue = DispatchQueue(label: "lens.sound", qos: .userInitiated)
    private var player: AVAudioPlayer?

    func play() {
        queue.async { [self] in
            guard let url = Bundle.main.url(forResource: "portal_lock", withExtension: "m4a") else { return }
            try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
            if player == nil { player = try? AVAudioPlayer(contentsOf: url) }
            player?.volume = 0.7
            player?.currentTime = 0
            player?.play()
        }
    }
}

