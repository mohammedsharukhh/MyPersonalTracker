//
//  ReceiptScanner.swift
//  MyPersonalTracker
//
//  Reads a receipt photo on the device (Vision text recognition, nothing is
//  uploaded) and pulls out the total, the date and the shop's name to fill in
//  Add expense. The person checks the result before saving.
//

import PhotosUI
import SwiftUI
import Vision
import VisionKit

nonisolated enum ReceiptReader {
    struct Result: Equatable, Sendable {
        var amount: Double?
        var date: Date?
        var merchant: String?
    }

    @MainActor
    static func read(_ image: UIImage) async -> Result {
        guard let cgImage = image.cgImage else { return Result() }
        let orientation = CGImagePropertyOrientation(image.imageOrientation)
        let lines = await Task.detached(priority: .userInitiated) { recognize(cgImage, orientation: orientation) }.value
        return parse(lines: lines)
    }

    /// Text lines, top to bottom as printed. Runs synchronously — call off the main thread.
    static func recognize(_ image: CGImage, orientation: CGImagePropertyOrientation) -> [String] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        do {
            try VNImageRequestHandler(cgImage: image, orientation: orientation).perform([request])
        } catch {
            return []
        }
        return (request.results ?? [])
            .sorted { $0.boundingBox.minY > $1.boundingBox.minY }
            .compactMap { $0.topCandidates(1).first?.string }
    }

    private static let totalWords = ["grand total", "total due", "amount due", "total amount", "net total", "balance due",
                                     "total", "net amount", "amount", "إجمالي", "المجموع"]
    private static let skipWords = ["subtotal", "sub total", "sub-total", "vat", "tax", "change", "cash", "tendered", "discount", "saving"]

    /// Amounts with two decimals: "1,234.50", "12.00", "45,50".
    private static let money = try! NSRegularExpression(pattern: #"(?<![\d.,])(\d{1,3}(?:[,\s]\d{3})+|\d+)[.,](\d{2})(?![\d])"#)

    static func amounts(in line: String) -> [Double] {
        let range = NSRange(line.startIndex..., in: line)
        return money.matches(in: line, range: range).compactMap { match in
            guard let whole = Range(match.range(at: 1), in: line), let cents = Range(match.range(at: 2), in: line) else { return nil }
            let digits = line[whole].replacingOccurrences(of: ",", with: "").replacingOccurrences(of: " ", with: "")
            return Double("\(digits).\(line[cents])")
        }
    }

    static func parse(lines: [String], now: Date = .now) -> Result {
        var result = Result()

        // 1. A "total" line (skipping subtotal/VAT/change), its amount or the next line's.
        outer: for keyword in totalWords {
            for (i, line) in lines.enumerated() {
                let lower = line.lowercased()
                guard lower.contains(keyword), !skipWords.contains(where: { lower.contains($0) }) else { continue }
                if let value = amounts(in: line).last ?? (i + 1 < lines.count ? amounts(in: lines[i + 1]).first : nil), value > 0 {
                    result.amount = value
                    break outer
                }
            }
        }
        // 2. Otherwise the largest amount on the receipt.
        if result.amount == nil {
            result.amount = lines.flatMap { amounts(in: $0) }.filter { $0 > 0 && $0 < 1_000_000 }.max()
        }

        // Date: the first one in the last 90 days (receipts also print expiry and phone numbers).
        if let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.date.rawValue) {
            let earliest = Calendar.current.date(byAdding: .day, value: -90, to: now) ?? now
            let latest = Calendar.current.date(byAdding: .day, value: 1, to: now) ?? now
            for line in lines {
                let matches = detector.matches(in: line, range: NSRange(line.startIndex..., in: line))
                if let date = matches.compactMap(\.date).first(where: { $0 >= earliest && $0 <= latest }) {
                    result.date = date
                    break
                }
            }
        }

        // Shop: the first line near the top that reads like a name.
        result.merchant = lines.prefix(6).first { line in
            let letters = line.filter(\.isLetter).count
            let lower = line.lowercased()
            return letters >= 3 && letters * 2 >= line.count && line.count <= 32
                && !["receipt", "invoice", "tax", "welcome", "tel", "phone", "vat"].contains { lower.contains($0) }
        }?.trimmingCharacters(in: .whitespaces).capitalized
        return result
    }
}

extension CGImagePropertyOrientation {
    nonisolated init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up: self = .up
        case .down: self = .down
        case .left: self = .left
        case .right: self = .right
        case .upMirrored: self = .upMirrored
        case .downMirrored: self = .downMirrored
        case .leftMirrored: self = .leftMirrored
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}

/// "Scan receipt" for Add expense: camera (document scanner) or a photo.
struct ReceiptScanButton: View {
    let onRead: (ReceiptReader.Result) -> Void

    @State private var scanning = false
    @State private var photo: PhotosPickerItem?
    @State private var choosingPhoto = false
    @State private var reading = false
    @State private var message: String?

    var body: some View {
        HStack(spacing: 8) {
            Menu {
                if VNDocumentCameraViewController.isSupported {
                    Button("Scan with camera", systemImage: "doc.viewfinder") { scanning = true }
                }
                Button("Choose a photo", systemImage: "photo") { choosingPhoto = true }
            } label: {
                Label(reading ? "Reading receipt…" : "Scan receipt", systemImage: "doc.text.viewfinder")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(SheetTheme.navy.opacity(0.1), in: Capsule())
                    .foregroundStyle(SheetTheme.navy)
            }
            .disabled(reading)
            if reading { ProgressView().controlSize(.small) }
            if let message { Text(message).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
        }
        .photosPicker(isPresented: $choosingPhoto, selection: $photo, matching: .images)
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    await read(image)
                }
                photo = nil
            }
        }
        .fullScreenCover(isPresented: $scanning) {
            ReceiptCameraScanner { image in
                scanning = false
                if let image { Task { await read(image) } }
            }
            .ignoresSafeArea()
        }
    }

    private func read(_ image: UIImage) async {
        reading = true
        let result = await ReceiptReader.read(image)
        reading = false
        if result.amount == nil {
            message = "Couldn't find a total — type it in."
        } else {
            message = "Filled from the receipt — check it."
        }
        onRead(result)
    }
}

private struct ReceiptCameraScanner: UIViewControllerRepresentable {
    let onFinish: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let controller = VNDocumentCameraViewController()
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: VNDocumentCameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let onFinish: (UIImage?) -> Void
        init(onFinish: @escaping (UIImage?) -> Void) { self.onFinish = onFinish }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            onFinish(scan.pageCount > 0 ? scan.imageOfPage(at: 0) : nil)
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) { onFinish(nil) }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) { onFinish(nil) }
    }
}
