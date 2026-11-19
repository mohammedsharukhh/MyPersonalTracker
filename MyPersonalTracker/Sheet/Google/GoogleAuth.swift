//
//  GoogleAuth.swift
//  MyPersonalTracker
//
//  Sign in with Google in the system's web sheet (OAuth 2.0 with PKCE, no
//  Google SDK). The app asks only for `drive.file`: the files it creates or
//  that you open with it — nothing else in your Drive. The refresh token stays
//  in the Keychain; access tokens live in memory for their hour.
//
//  Needs `GoogleOAuthClientID` in Info.plist: the iOS OAuth client from
//  Google Cloud ▸ Google Auth Platform ▸ Clients (it isn't a secret).
//

import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

@Observable
final class GoogleAuth {
    static let shared = GoogleAuth()

    static let scopes = ["openid", "email", "https://www.googleapis.com/auth/drive.file"]
    private static let authorizeURL = URL(string: "https://accounts.google.com/o/oauth2/v2/auth")!
    private static let tokenURL = URL(string: "https://oauth2.googleapis.com/token")!
    private static let revokeURL = URL(string: "https://oauth2.googleapis.com/revoke")!
    private static let refreshAccount = "googleRefreshToken"
    private static let emailKey = "googleEmail"

    let clientID: String
    /// Which Google account, for showing; nil when signed out.
    private(set) var email: String?
    private(set) var isSignedIn: Bool
    private var accessToken: String?
    private var expiresAt = Date.distantPast
    private let presenter = WindowPresenter()

    init(clientID: String = Bundle.main.object(forInfoDictionaryKey: "GoogleOAuthClientID") as? String ?? "") {
        self.clientID = clientID.trimmingCharacters(in: .whitespaces)
        isSignedIn = Keychain.get(Self.refreshAccount) != nil
        email = isSignedIn ? UserDefaults.standard.string(forKey: Self.emailKey) : nil
    }

    var isConfigured: Bool { clientID.hasSuffix(".apps.googleusercontent.com") }

    /// `com.googleusercontent.apps.<id>` — Google's redirect scheme for an iOS client.
    var redirectScheme: String { clientID.split(separator: ".").reversed().joined(separator: ".") }
    var redirectURI: String { "\(redirectScheme):/oauth2redirect" }

    func signIn() async throws {
        guard isConfigured else { throw GoogleError.notConfigured }
        let verifier = PKCE.verifier()
        let state = PKCE.verifier()
        let callback = try await presenter.authenticate(url: authorizationURL(challenge: PKCE.challenge(for: verifier), state: state),
                                                        scheme: redirectScheme)
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        if let error = items.first(where: { $0.name == "error" })?.value { throw GoogleError.denied(error) }
        guard items.first(where: { $0.name == "state" })?.value == state,
              let code = items.first(where: { $0.name == "code" })?.value else { throw GoogleError.badResponse("The sign-in didn't come back as expected.") }

        let tokens = try await post(Self.tokenURL, form: ["code": code, "client_id": clientID, "redirect_uri": redirectURI,
                                                          "grant_type": "authorization_code", "code_verifier": verifier])
        guard let refresh = tokens["refresh_token"] as? String else { throw GoogleError.badResponse("Google didn't return a refresh token.") }
        guard Keychain.set(refresh, for: Self.refreshAccount) else { throw GoogleError.badResponse("Couldn't save the sign-in to the Keychain.") }
        keep(tokens)
        email = (tokens["id_token"] as? String).flatMap(Self.email(fromIDToken:))
        UserDefaults.standard.set(email, forKey: Self.emailKey)
        isSignedIn = true
    }

    /// Forgets the sign-in here and revokes it at Google.
    func signOut() async {
        let token = Keychain.get(Self.refreshAccount)
        Keychain.set("", for: Self.refreshAccount)
        UserDefaults.standard.removeObject(forKey: Self.emailKey)
        accessToken = nil
        expiresAt = .distantPast
        email = nil
        isSignedIn = false
        if let token { _ = try? await post(Self.revokeURL, form: ["token": token]) }
    }

    /// A token good for at least another minute, refreshed when needed.
    func validAccessToken() async throws -> String {
        if let accessToken, expiresAt.timeIntervalSinceNow > 60 { return accessToken }
        guard let refresh = Keychain.get(Self.refreshAccount) else { throw GoogleError.signedOut }
        do {
            let tokens = try await post(Self.tokenURL, form: ["client_id": clientID, "refresh_token": refresh, "grant_type": "refresh_token"])
            keep(tokens)
        } catch GoogleError.http(400, let message) where message.contains("invalid_grant") {
            // Revoked in the Google account, or expired (7 days while the consent screen is in Testing).
            await signOut()
            throw GoogleError.signedOut
        }
        guard let accessToken else { throw GoogleError.signedOut }
        return accessToken
    }

    func authorizationURL(challenge: String, state: String) -> URL {
        var components = URLComponents(url: Self.authorizeURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: Self.scopes.joined(separator: " ")),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "access_type", value: "offline"),
            // Always show the account picker, so a refresh token comes back.
            URLQueryItem(name: "prompt", value: "select_account consent"),
        ]
        return components.url!
    }

    /// The `email` claim of an ID token (shown only; it came straight from Google over TLS).
    static func email(fromIDToken token: String) -> String? {
        let parts = token.split(separator: ".")
        guard parts.count == 3 else { return nil }
        var payload = parts[1].replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
        guard let data = Data(base64Encoded: payload),
              let claims = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return claims["email"] as? String
    }

    private func keep(_ tokens: [String: Any]) {
        accessToken = tokens["access_token"] as? String
        expiresAt = Date.now.addingTimeInterval((tokens["expires_in"] as? Double) ?? 3600)
    }

    private func post(_ url: URL, form: [String: String]) async throws -> [String: Any] {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var body = URLComponents()
        body.queryItems = form.map { URLQueryItem(name: $0.key, value: $0.value) }
        // `+` would read as a space in a form body.
        request.httpBody = body.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B").data(using: .utf8)
        return try await GoogleHTTP.json(request)
    }
}

enum GoogleError: LocalizedError {
    case notConfigured, signedOut, cancelled
    case denied(String)
    case badResponse(String)
    case http(Int, String)

    var errorDescription: String? {
        switch self {
        case .notConfigured: "Add your Google OAuth client ID (GoogleOAuthClientID in Info.plist) first."
        case .signedOut: "Sign in with Google again."
        case .cancelled: "Sign-in was cancelled."
        case .denied(let reason): "Google didn't allow it (\(reason))."
        case .badResponse(let text): text
        case .http(let status, let message): "Google answered \(status): \(message)"
        }
    }
}

/// Sends a request and reads a JSON object back, turning Google's error bodies into `GoogleError.http`.
/// A busy Google ("too many requests", 429, or 503) answers the same request
/// later: wait and ask again, longer each time, as Google advises. A 429 means
/// nothing was done, so asking again never makes a change twice.
enum GoogleRetry {
    /// About a minute in all: the window Sheets counts its per-minute quota over.
    static let waits: [TimeInterval] = [2, 4, 8, 16, 32]
    /// Told before each pause (the loading screen says "carrying on in 8s").
    @TaskLocal static var onWait: (@Sendable (TimeInterval) -> Void)?

    static func wait(status: Int, attempt: Int, retryAfter: String?, waits: [TimeInterval] = waits) -> TimeInterval? {
        guard status == 429 || status == 503, attempt < waits.count else { return nil }
        if let seconds = retryAfter.flatMap(TimeInterval.init), seconds >= 0 { return min(seconds, 60) }
        return waits[attempt]
    }
}

enum GoogleHTTP {
    static func json(_ request: URLRequest, waits: [TimeInterval] = GoogleRetry.waits) async throws -> [String: Any] {
        var attempt = 0
        while true {
            let (data, response) = try await URLSession.shared.data(for: request)
            let http = response as? HTTPURLResponse
            let status = http?.statusCode ?? 0
            let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
            if (200..<300).contains(status) { return object }
            guard let wait = GoogleRetry.wait(status: status, attempt: attempt, retryAfter: http?.value(forHTTPHeaderField: "Retry-After"), waits: waits) else {
                throw GoogleError.http(status, message(from: object, data: data))
            }
            SheetStore.log("  Google busy (\(status)), asking again in \(Int(wait))s")
            GoogleRetry.onWait?(wait)
            try await Task.sleep(for: .seconds(wait))
            attempt += 1
        }
    }

    /// `{"error": {"message": …}}` (APIs) or `{"error": "invalid_grant", "error_description": …}` (OAuth).
    static func message(from object: [String: Any], data: Data) -> String {
        if let error = object["error"] as? [String: Any], let message = error["message"] as? String { return message }
        if let error = object["error"] as? String {
            return [error, object["error_description"] as? String].compactMap { $0 }.joined(separator: ": ")
        }
        return String(data: data.prefix(200), encoding: .utf8) ?? "no details"
    }
}

/// RFC 7636 code verifier and S256 challenge.
enum PKCE {
    static func verifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return base64URL(Data(bytes))
    }

    static func challenge(for verifier: String) -> String {
        base64URL(Data(SHA256.hash(data: Data(verifier.utf8))))
    }

    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

/// Shows ASWebAuthenticationSession over the app's key window.
private final class WindowPresenter: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    func authenticate(url: URL, scheme: String) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(url: url, callback: .customScheme(scheme)) { callback, error in
                if let callback { continuation.resume(returning: callback) }
                else if (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin { continuation.resume(throwing: GoogleError.cancelled) }
                else { continuation.resume(throwing: error ?? GoogleError.cancelled) }
            }
            session.presentationContextProvider = self
            // Google's own account cookies make the account picker one tap.
            session.prefersEphemeralWebBrowserSession = false
            self.session = session
            if !session.start() { continuation.resume(throwing: GoogleError.badResponse("Couldn't open the sign-in sheet.")) }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        if let key = scenes.flatMap(\.windows).first(where: \.isKeyWindow) { return key }
        guard let scene = scenes.first else { preconditionFailure("No window to show the sign-in over.") }
        return ASPresentationAnchor(windowScene: scene)
    }
}
