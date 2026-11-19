//
//  GoogleRetryTests.swift
//  MyPersonalTrackerTests
//
//  "Too many requests" (429) from Google: wait and try again, as Google
//  advises, instead of failing a long run of changes halfway every time.
//  Mirrors GoogleRetryTest.kt.
//

import Foundation
import Testing
@testable import MyPersonalTracker

/// Answers 429 a set number of times, then 200.
final class BusyGoogle: URLProtocol {
    nonisolated(unsafe) static var busyAnswers = 0
    nonisolated(unsafe) static var requests = 0

    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "busy-google.test" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.requests += 1
        let busy = Self.busyAnswers > 0
        if busy { Self.busyAnswers -= 1 }
        let body = busy ? #"{"error":{"message":"Quota exceeded"}}"# : #"{"ok":true}"#
        client?.urlProtocol(self, didReceive: HTTPURLResponse(url: request.url!, statusCode: busy ? 429 : 200, httpVersion: nil, headerFields: nil)!,
                            cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

@Suite(.serialized)
struct GoogleRetryTests {
    @Test func waitsLongerEachTimeThenGivesUp() {
        #expect(GoogleRetry.wait(status: 429, attempt: 0, retryAfter: nil) == 2)
        #expect(GoogleRetry.wait(status: 429, attempt: 1, retryAfter: nil) == 4)
        #expect(GoogleRetry.wait(status: 503, attempt: 4, retryAfter: nil) == 32)
        #expect(GoogleRetry.wait(status: 429, attempt: 5, retryAfter: nil) == nil)
        // Google's own "try again in N seconds" wins, within reason.
        #expect(GoogleRetry.wait(status: 429, attempt: 0, retryAfter: "7") == 7)
        #expect(GoogleRetry.wait(status: 429, attempt: 0, retryAfter: "600") == 60)
        // A refusal isn't retried: it would only be refused again.
        #expect(GoogleRetry.wait(status: 400, attempt: 0, retryAfter: nil) == nil)
        #expect(GoogleRetry.wait(status: 403, attempt: 0, retryAfter: nil) == nil)
    }

    @Test func aBusyGoogleIsAskedAgain() async throws {
        URLProtocol.registerClass(BusyGoogle.self)
        defer { URLProtocol.unregisterClass(BusyGoogle.self) }
        let request = URLRequest(url: URL(string: "https://busy-google.test/v4/spreadsheets/x:batchUpdate")!)

        BusyGoogle.busyAnswers = 2; BusyGoogle.requests = 0
        let answer = try await GoogleHTTP.json(request, waits: [0, 0, 0])
        #expect(answer["ok"] as? Bool == true)
        #expect(BusyGoogle.requests == 3)

        BusyGoogle.busyAnswers = 10; BusyGoogle.requests = 0
        await #expect(throws: GoogleError.self) { try await GoogleHTTP.json(request, waits: [0, 0]) }
        #expect(BusyGoogle.requests == 3, "asked once, then once per wait")
    }

    @Test func aBusyGoogleSaysHowLongItWaits() async throws {
        URLProtocol.registerClass(BusyGoogle.self)
        defer { URLProtocol.unregisterClass(BusyGoogle.self) }
        BusyGoogle.busyAnswers = 1; BusyGoogle.requests = 0
        let heard = Heard()
        _ = try await GoogleRetry.$onWait.withValue({ heard.add($0) }) {
            try await GoogleHTTP.json(URLRequest(url: URL(string: "https://busy-google.test/x")!), waits: [0])
        }
        #expect(heard.waits == [0])
    }
}

private final class Heard: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [TimeInterval] = []
    var waits: [TimeInterval] { lock.withLock { values } }
    func add(_ value: TimeInterval) { lock.withLock { values.append(value) } }
}
