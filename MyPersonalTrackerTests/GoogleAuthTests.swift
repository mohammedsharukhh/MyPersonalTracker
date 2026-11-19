//
//  GoogleAuthTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import Testing
@testable import MyPersonalTracker

@Suite("Sign in with Google")
struct GoogleAuthTests {
    private let auth = GoogleAuth(clientID: "123-abc.apps.googleusercontent.com")

    @Test func pkceMatchesTheRFCExample() {
        // RFC 7636, appendix B.
        #expect(PKCE.challenge(for: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        let verifier = PKCE.verifier()
        #expect(verifier.count == 43)
        #expect(verifier.range(of: #"^[A-Za-z0-9_-]+$"#, options: .regularExpression) != nil)
    }

    @Test func redirectsToTheReversedClientID() {
        #expect(auth.isConfigured)
        #expect(auth.redirectScheme == "com.googleusercontent.apps.123-abc")
        #expect(auth.redirectURI == "com.googleusercontent.apps.123-abc:/oauth2redirect")
        #expect(!GoogleAuth(clientID: "").isConfigured)
    }

    @Test func asksOnlyForFilesTheAppMakes() throws {
        let url = auth.authorizationURL(challenge: "CH", state: "ST")
        let items = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems)
        func value(_ name: String) -> String? { items.first { $0.name == name }?.value }
        #expect(value("scope") == "openid email https://www.googleapis.com/auth/drive.file")
        #expect(value("code_challenge_method") == "S256")
        #expect(value("code_challenge") == "CH")
        #expect(value("state") == "ST")
        #expect(value("response_type") == "code")
        #expect(value("access_type") == "offline")
        #expect(value("redirect_uri") == auth.redirectURI)
    }

    @Test func readsTheEmailFromTheIDToken() {
        // Header and signature aren't checked; the payload is {"email":"a.b@gmail.com","sub":"1"}.
        let payload = Data(#"{"email":"a.b@gmail.com","sub":"1"}"#.utf8).base64EncodedString()
            .replacingOccurrences(of: "=", with: "")
        #expect(GoogleAuth.email(fromIDToken: "eyJhbGciOiJSUzI1NiJ9.\(payload).sig") == "a.b@gmail.com")
        #expect(GoogleAuth.email(fromIDToken: "not-a-token") == nil)
    }

    @Test func findsTheFileIDInALink() {
        let id = "1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-xyz"
        #expect(GoogleSheetsAPI.fileID(from: "https://docs.google.com/spreadsheets/d/\(id)/edit#gid=0") == id)
        #expect(GoogleSheetsAPI.fileID(from: "https://drive.google.com/open?id=\(id)") == id)
        #expect(GoogleSheetsAPI.fileID(from: "  \(id) ") == id)
        #expect(GoogleSheetsAPI.fileID(from: "hello") == nil)
    }

    @Test func quotesTabNamesThatNeedIt() {
        #expect(GoogleSheetsAPI.a1("Ledger", "A1:C4") == "Ledger!A1:C4")
        #expect(GoogleSheetsAPI.a1("Mom & Dad", "A1") == "'Mom & Dad'!A1")
        #expect(GoogleSheetsAPI.a1("Dad's list", "A1") == "'Dad''s list'!A1")
    }

    @Test func readsGoogleErrorBodies() {
        #expect(GoogleHTTP.message(from: ["error": ["message": "File not found: 1abc."]], data: Data()) == "File not found: 1abc.")
        #expect(GoogleHTTP.message(from: ["error": "invalid_grant", "error_description": "Token has been expired or revoked."], data: Data())
                == "invalid_grant: Token has been expired or revoked.")
    }

    @Test func workbookPeriodIsThirtyDaysFromStartOfDay() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "Asia/Kolkata"))
        let day = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 19, hour: 22, minute: 45)))
        let period = TrackerWorkbook.period(startingOn: day, calendar: calendar)
        #expect(period.start == "2026-10-19")
        #expect(period.end == "2026-11-17")
    }

    @Test func habitScoreChartReadsDateAndScoreColumns() throws {
        let request = TrackerWorkbook.habitScoreChart(dashboard: 7, daily: 3)
        let spec = try #require(((request["addChart"] as? [String: Any])?["chart"] as? [String: Any])?["spec"] as? [String: Any])
        let basic = try #require(spec["basicChart"] as? [String: Any])
        #expect(basic["chartType"] as? String == "LINE")
        func source(_ value: Any?) -> [String: Any]? {
            (((value as? [String: Any])?["sourceRange"] as? [String: Any])?["sources"] as? [[String: Any]])?.first
        }
        let domain = try #require(source(((basic["domains"] as? [[String: Any]])?.first)?["domain"]))
        let series = try #require(source(((basic["series"] as? [[String: Any]])?.first)?["series"]))
        #expect(domain["startColumnIndex"] as? Int == 0)    // A: date
        #expect(series["startColumnIndex"] as? Int == 14)   // O: score
        #expect(series["startRowIndex"] as? Int == 4 && series["endRowIndex"] as? Int == 34)   // rows 5…34
        #expect(series["sheetId"] as? Int == 3)
    }

    @Test func blueprintIsBundled() throws {
        let url = try #require(Bundle.main.url(forResource: "Blueprint", withExtension: "xlsx"))
        let data = try Data(contentsOf: url)
        #expect(data.count > 100_000)
        #expect(data.prefix(2) == Data("PK".utf8))   // an .xlsx is a zip
    }
}
