//
//  PhotoSupport.swift
//  MyPersonalTracker
//

import SwiftUI
import UIKit
import VisionKit

extension UIImage {
    /// Downscales to fit `maxDimension` and encodes as JPEG, keeping attachments small.
    func jpegForStorage(maxDimension: CGFloat = 1600, quality: CGFloat = 0.8) -> Data? {
        let longest = max(size.width, size.height)
        guard longest > maxDimension else { return jpegData(compressionQuality: quality) }
        let scale = maxDimension / longest
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format)
            .image { _ in draw(in: CGRect(origin: .zero, size: target)) }
            .jpegData(compressionQuality: quality)
    }
}

extension UIImage {
    /// Stacks pages top to bottom at a common width, e.g. a multi-page scanned bill.
    static func stackedVertically(_ pages: [UIImage], width: CGFloat = 1200) -> UIImage? {
        guard !pages.isEmpty else { return nil }
        let heights = pages.map { width * $0.size.height / max($0.size.width, 1) }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: heights.reduce(0, +)), format: format).image { _ in
            var y: CGFloat = 0
            for (page, height) in zip(pages, heights) {
                page.draw(in: CGRect(x: 0, y: y, width: width, height: height))
                y += height
            }
        }
    }
}

/// The system document scanner (auto-crops and flattens paper). Multi-page scans are stacked into one image.
struct DocumentScanner: UIViewControllerRepresentable {
    static var isAvailable: Bool { VNDocumentCameraViewController.isSupported }

    let onScan: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let scanner = VNDocumentCameraViewController()
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let parent: DocumentScanner

        init(_ parent: DocumentScanner) { self.parent = parent }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController,
                                          didFinishWith scan: VNDocumentCameraScan) {
            let pages = (0..<scan.pageCount).map(scan.imageOfPage(at:))
            if let image = UIImage.stackedVertically(pages) { parent.onScan(image) }
            parent.dismiss()
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            parent.dismiss()
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            parent.dismiss()
        }
    }
}

/// The system camera, wrapped for SwiftUI. Check `isAvailable` first; the simulator has no camera.
struct CameraPicker: UIViewControllerRepresentable {
    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    let onCapture: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker

        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onCapture(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

/// A square, rounded thumbnail for stored photo data.
struct PhotoThumbnail: View {
    let data: Data
    var size: CGFloat = 40

    var body: some View {
        Group {
            if let image = UIImage(data: data) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "photo").foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(.rect(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(.black.opacity(0.1)))
    }
}

/// A Polaroid-style print: photo in a white frame with a deeper bottom edge, slightly tilted.
struct PolaroidPhoto: View {
    let data: Data
    var width: CGFloat = 56
    var tilt: Double = 4

    var body: some View {
        let border = max(3, width * 0.07)
        Group {
            if let image = UIImage(data: data) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "photo").foregroundStyle(.secondary)
            }
        }
        .frame(width: width - border * 2, height: width - border * 2)
        .clipped()
        .padding([.top, .horizontal], border)
        .padding(.bottom, border * 3.5)
        .background(Color(white: 0.98))
        .clipShape(.rect(cornerRadius: 1.5))
        .shadow(color: .black.opacity(0.25), radius: 2, x: 1, y: 2)
        .rotationEffect(.degrees(tilt))
    }
}

/// A round selfie thumbnail.
struct SelfieAvatar: View {
    let data: Data
    var size: CGFloat = 40

    var body: some View {
        Group {
            if let image = UIImage(data: data) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "face.smiling").foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(.circle)
        .overlay(Circle().strokeBorder(.white.opacity(0.8), lineWidth: 1.5))
        .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
    }
}

/// Full-screen photo with pinch to zoom and a share button.
struct PhotoViewer: View {
    let data: Data
    var title = ""

    @Environment(\.dismiss) private var dismiss
    @State private var zoom: CGFloat = 1
    @GestureState private var pinch: CGFloat = 1

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                if let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .scaleEffect(zoom * pinch)
                        .gesture(
                            MagnifyGesture()
                                .updating($pinch) { value, state, _ in state = value.magnification }
                                .onEnded { value in zoom = min(max(zoom * value.magnification, 1), 5) }
                        )
                        .onTapGesture(count: 2) { withAnimation { zoom = zoom > 1 ? 1 : 2.5 } }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                if let image = UIImage(data: data) {
                    ToolbarItem(placement: .primaryAction) {
                        ShareLink(item: Image(uiImage: image), preview: SharePreview(title.isEmpty ? "Photo" : title, image: Image(uiImage: image)))
                    }
                }
            }
        }
    }
}
