//
//  SmileCheckInView.swift
//  MyPersonalTracker
//

import AVFoundation
import SwiftUI
import UIKit

/// Daily selfie: guides the user to center their face and smile, then captures automatically.
/// Calls `onFinish` with the photo, or nil if the user skipped.
struct SmileCheckInView: View {
    let onFinish: (Data?) -> Void

    @State private var model = SmileCameraModel()
    @State private var flash = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            switch model.phase {
            case .starting:
                ProgressView().tint(.white)
            case .unavailable:
                message(
                    symbol: "camera.fill",
                    title: "Camera not available",
                    detail: "The smile check-in needs a front camera. You can still use the rest of the app."
                )
            case .denied:
                message(
                    symbol: "lock.fill",
                    title: "Camera access is off",
                    detail: "Allow camera access in Settings to take your daily smile.",
                    showSettings: true
                )
            case .live:
                live
            case .captured(let data):
                captured(data)
            }

            if flash {
                Color.white.ignoresSafeArea().transition(.opacity)
            }
        }
        .preferredColorScheme(.dark)
        .statusBarHidden()
        .task { await model.start() }
        .onDisappear { model.stop() }
        .onChange(of: model.phase) { _, phase in
            if case .captured = phase {
                withAnimation(.easeOut(duration: 0.08)) { flash = true }
                withAnimation(.easeIn(duration: 0.35).delay(0.08)) { flash = false }
            }
        }
        .sensoryFeedback(.success, trigger: model.phase) { _, new in
            if case .captured = new { true } else { false }
        }
        .sensoryFeedback(.selection, trigger: model.guidance.isAligned) { _, new in new }
    }

    // MARK: Live camera

    private var live: some View {
        ZStack {
            CameraPreview(session: model.camera.session)
                .ignoresSafeArea()

            FaceOvalOverlay(guidance: model.guidance, progress: model.progress)
                .ignoresSafeArea()

            VStack {
                VStack(spacing: 8) {
                    Text("Daily smile check-in")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.7))
                    Text(model.guidance.emoji)
                        .font(.system(size: 44))
                    Text(model.guidance.message)
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)
                        .contentTransition(.opacity)
                        .animation(.snappy, value: model.guidance)
                }
                .foregroundStyle(.white)
                .padding(.top, 24)
                .padding(.horizontal)

                Spacer()

                HStack {
                    Button("Skip today") { onFinish(nil) }
                        .font(.body.weight(.medium))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Button {
                        model.shutter()
                    } label: {
                        Circle()
                            .fill(.white)
                            .frame(width: 64, height: 64)
                            .overlay(Circle().stroke(.white.opacity(0.5), lineWidth: 4).padding(-7))
                    }
                    .accessibilityLabel("Take photo")

                    Text("Auto")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.7))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .accessibilityHidden(true)
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 24)
            }
        }
    }

    // MARK: Review

    private func captured(_ data: Data) -> some View {
        VStack(spacing: 24) {
            Text("Nice smile! 😄")
                .font(.title.bold())
                .foregroundStyle(.white)
                .padding(.top, 32)

            if let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .clipShape(.rect(cornerRadius: 24))
                    .padding(.horizontal, 24)
                    .frame(maxHeight: .infinity)
            }

            HStack(spacing: 16) {
                Button("Retake") { model.retake() }
                    .buttonStyle(.bordered)
                    .tint(.white)
                Button("Save") { onFinish(data) }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)
            }
            .controlSize(.large)
            .padding(.bottom, 32)
        }
    }

    private func message(symbol: String, title: String, detail: String, showSettings: Bool = false) -> some View {
        VStack(spacing: 16) {
            Image(systemName: symbol)
                .font(.system(size: 44))
            Text(title)
                .font(.title2.bold())
            Text(detail)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white.opacity(0.7))
            if showSettings, let url = URL(string: UIApplication.openSettingsURLString) {
                Link("Open Settings", destination: url)
                    .buttonStyle(.borderedProminent)
            }
            Button("Continue") { onFinish(nil) }
                .buttonStyle(.bordered)
                .tint(.white)
                .padding(.top, 8)
        }
        .foregroundStyle(.white)
        .padding(32)
    }
}

// MARK: - Model

@Observable
final class SmileCameraModel {
    enum Phase: Equatable {
        case starting, unavailable, denied, live
        case captured(Data)
    }

    var phase: Phase = .starting
    var guidance: FaceGuidance = .noFace
    var progress: Double = 0
    let camera = SelfieCamera()

    func start() async {
        guard SelfieCamera.isAvailable else {
            phase = .unavailable
            return
        }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            break
        case .notDetermined:
            guard await AVCaptureDevice.requestAccess(for: .video) else {
                phase = .denied
                return
            }
        default:
            phase = .denied
            return
        }

        camera.onUpdate = { [weak self] guidance, progress in
            guard let self, phase == .live else { return }
            self.guidance = guidance
            self.progress = progress
        }
        camera.onPhoto = { [weak self] data in
            guard let self else { return }
            if let data, let stored = UIImage(data: data)?.jpegForStorage(maxDimension: 1200) {
                phase = .captured(stored)
                camera.stop()
            } else {
                camera.reset()
            }
        }
        camera.start()
        phase = .live
    }

    func shutter() { camera.capturePhoto() }

    func retake() {
        guidance = .noFace
        progress = 0
        camera.reset()
        camera.start()
        phase = .live
    }

    func stop() { camera.stop() }
}

// MARK: - Pieces

/// Darkens everything except a face-shaped oval; the oval's outline shows alignment and fills up while holding.
private struct FaceOvalOverlay: View {
    let guidance: FaceGuidance
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width * 0.72
            let size = CGSize(width: width, height: width * 1.32)

            ZStack {
                Rectangle()
                    .fill(.black.opacity(0.55))
                    .overlay {
                        Ellipse()
                            .frame(width: size.width, height: size.height)
                            .blendMode(.destinationOut)
                    }
                    .compositingGroup()

                Ellipse()
                    .stroke(guidance.isAligned ? Color.yellow : .white.opacity(0.8), lineWidth: 4)
                    .frame(width: size.width, height: size.height)
                    .animation(.snappy, value: guidance.isAligned)

                Ellipse()
                    .trim(from: 0, to: progress)
                    .stroke(.green, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: size.width, height: size.height)
                    .animation(.linear(duration: 0.1), value: progress)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {
        if let connection = view.previewLayer.connection, connection.isVideoRotationAngleSupported(90) {
            connection.videoRotationAngle = 90
        }
    }
}
