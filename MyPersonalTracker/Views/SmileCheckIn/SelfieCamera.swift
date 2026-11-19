//
//  SelfieCamera.swift
//  MyPersonalTracker
//

import AVFoundation
import CoreImage
import QuartzCore

/// What the user should do next to get a good, smiling selfie.
nonisolated enum FaceGuidance: Equatable, Sendable {
    case noFace, tooManyFaces, moveCloser, moveBack, center, straighten, openEyes, smile, hold

    var message: String {
        switch self {
        case .noFace: "Look at the camera"
        case .tooManyFaces: "Just you, please"
        case .moveCloser: "Come a little closer"
        case .moveBack: "Move back a bit"
        case .center: "Center your face in the oval"
        case .straighten: "Hold your head straight"
        case .openEyes: "Eyes open!"
        case .smile: "Now give us a big smile!"
        case .hold: "Perfect! Hold it…"
        }
    }

    var emoji: String {
        switch self {
        case .noFace: "👀"
        case .tooManyFaces: "🙋"
        case .moveCloser, .moveBack, .center: "🎯"
        case .straighten: "📐"
        case .openEyes: "👁️"
        case .smile: "😁"
        case .hold: "📸"
        }
    }

    /// Face is positioned well; only the smile (or holding still) is missing.
    var isAligned: Bool { self == .smile || self == .hold }
}

/// Front camera session that watches for a centered, smiling face and captures automatically.
/// Runs on its own queues; results are delivered on the main actor.
nonisolated final class SelfieCamera: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate,
    AVCapturePhotoCaptureDelegate, @unchecked Sendable {

    static var isAvailable: Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) != nil
    }

    /// How long the face must stay aligned and smiling before the photo is taken.
    static let holdDuration: TimeInterval = 0.8

    let session = AVCaptureSession()

    /// Set before calling `start()`.
    var onUpdate: (@MainActor @Sendable (FaceGuidance, Double) -> Void)?
    var onPhoto: (@MainActor @Sendable (Data?) -> Void)?

    private let sessionQueue = DispatchQueue(label: "SelfieCamera.session")
    private let videoQueue = DispatchQueue(label: "SelfieCamera.video")
    private let photoOutput = AVCapturePhotoOutput()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let detector = CIDetector(
        ofType: CIDetectorTypeFace,
        context: nil,
        options: [CIDetectorAccuracy: CIDetectorAccuracyLow, CIDetectorTracking: true]
    )

    // Only touched on videoQueue.
    private var lastAnalysis: CFTimeInterval = 0
    private var readySince: CFTimeInterval?
    private var isCapturing = false

    func start() {
        sessionQueue.async { [self] in
            if session.inputs.isEmpty { configure() }
            if !session.isRunning { session.startRunning() }
        }
    }

    func stop() {
        sessionQueue.async { [self] in
            if session.isRunning { session.stopRunning() }
        }
    }

    /// Manual shutter.
    func capturePhoto() {
        videoQueue.async { [self] in
            guard !isCapturing else { return }
            isCapturing = true
            capture()
        }
    }

    /// Re-arms auto capture after a retake.
    func reset() {
        videoQueue.async { [self] in
            isCapturing = false
            readySince = nil
        }
    }

    private func configure() {
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .photo

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return }
        session.addInput(input)

        if session.canAddOutput(photoOutput) { session.addOutput(photoOutput) }

        videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(self, queue: videoQueue)
        if session.canAddOutput(videoOutput) { session.addOutput(videoOutput) }

        // Upright, mirrored frames: analysis sees what the user sees, and the saved photo matches the preview.
        for output in [photoOutput, videoOutput] as [AVCaptureOutput] {
            guard let connection = output.connection(with: .video) else { continue }
            if connection.isVideoRotationAngleSupported(90) { connection.videoRotationAngle = 90 }
            if connection.isVideoMirroringSupported {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = true
            }
        }
    }

    private func capture() {
        sessionQueue.async { [self] in
            guard photoOutput.connection(with: .video) != nil else {
                deliver(photo: nil)
                return
            }
            photoOutput.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }
    }

    private func deliver(photo data: Data?) {
        let onPhoto = onPhoto
        Task { @MainActor in onPhoto?(data) }
    }

    // MARK: - AVCaptureVideoDataOutputSampleBufferDelegate

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = CACurrentMediaTime()
        // ~10 checks per second is plenty and keeps the phone cool.
        guard !isCapturing, now - lastAnalysis > 0.1,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer),
              let detector else { return }
        lastAnalysis = now

        let image = CIImage(cvPixelBuffer: pixelBuffer)
        let faces = detector
            .features(in: image, options: [CIDetectorSmile: true, CIDetectorEyeBlink: true])
            .compactMap { $0 as? CIFaceFeature }
        let guidance = Self.guidance(for: faces, in: image.extent)

        var progress = 0.0
        if guidance == .hold {
            let since = readySince ?? now
            readySince = since
            progress = min((now - since) / Self.holdDuration, 1)
            if progress >= 1 {
                isCapturing = true
                capture()
            }
        } else {
            readySince = nil
        }

        let onUpdate = onUpdate
        let reportedProgress = progress
        Task { @MainActor in onUpdate?(guidance, reportedProgress) }
    }

    static func guidance(for faces: [CIFaceFeature], in extent: CGRect) -> FaceGuidance {
        guard let face = faces.first else { return .noFace }
        guard faces.count == 1 else { return .tooManyFaces }

        let width = face.bounds.width / extent.width
        if width < 0.3 { return .moveCloser }
        if width > 0.8 { return .moveBack }

        let centerX = (face.bounds.midX - extent.minX) / extent.width
        let centerY = (face.bounds.midY - extent.minY) / extent.height
        if abs(centerX - 0.5) > 0.12 || abs(centerY - 0.5) > 0.14 { return .center }

        if face.hasFaceAngle, abs(face.faceAngle) > 12 { return .straighten }
        if face.leftEyeClosed && face.rightEyeClosed { return .openEyes }
        if !face.hasSmile { return .smile }
        return .hold
    }

    // MARK: - AVCapturePhotoCaptureDelegate

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        deliver(photo: error == nil ? photo.fileDataRepresentation() : nil)
    }
}
