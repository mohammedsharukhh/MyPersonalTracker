# MyPersonalTracker

A personal life-and-money tracker for iPhone, built in SwiftUI, with a companion Android app in Jetpack Compose. Your data lives in your own Google Sheet, so you can open, chart and export it like any spreadsheet.

**~30k lines of Swift · ~26k lines of Kotlin · 190 iOS tests (Swift Testing) · 128 Android tests (JUnit)**

## What it does

**Today**
- Daily log of habits, water and sleep, with a score and streak
- Focus timer that shows as a Live Activity on the Lock Screen and in the Dynamic Island
- To-dos, plus an optional daily smile check-in selfie

**Money**
- Expense ledger with budgets, and notifications when a budget bucket reaches 80%
- Receipt scanning on the device with Vision text recognition (nothing is uploaded)
- Pay with UPI: scan a shop's QR code, pay in PhonePe, Google Pay, Paytm or CRED, and the payment is logged
- Salary split, savings goals, loans and EMIs, renewals (passport, visa, licence), money lent to people, and a currency converter

**Life**
- Study planner and timer, meal and shopping logs, day planner, and life goals
- Parents' requests and medicines, with reminders

**Across the system**
- Home Screen widgets: streak, to-dos, spending and Pay
- Siri, Spotlight, Shortcuts and the Action button through App Intents: add an expense by voice, or ask what you've spent today
- Steps, sleep, weight and workouts read from Apple Health
- Notifications: morning briefing, bills due tomorrow, renewals, and a weekly review

## How it works

- **The data lives in a Google Sheet.** The user's own Google Sheet is the source of truth. There are two ways to connect it:
  1. **Sign in with Google** (OAuth 2.0 with PKCE). The app copies the bundled workbook (`Resources/Blueprint.xlsx`) into the user's Drive. It then runs the sheet's Apps Script logic inside the app with JavaScriptCore, so there's no server to deploy.
  2. **Your own Apps Script web app.** Deploy the scripts in `MyPersonalTracker/Resources/Backend` and paste the web app URL and key into Settings.
- **It works offline.** Writes are queued on the device and synced with retries. The last snapshot is cached so screens open instantly.
- **Secrets stay protected.** The web app API key is stored in the Keychain on iOS and encrypted with an Android Keystore AES-GCM key on Android. It's sent in the request body, never in a URL, and payment history is kept out of backups. Tests check both on iOS and Android.
- **Architecture (iOS):** SwiftUI views over `@Observable` state, with a `@MainActor` `SheetStore` as the single source of truth for sheet data. On-device models use SwiftData. Networking uses async/await with `URLSession`.

## Tech

| | |
|---|---|
| **iOS** | Swift, SwiftUI, Observation, SwiftData, Swift Charts, WidgetKit, ActivityKit (Live Activities), App Intents, HealthKit, Vision / VisionKit, AVFoundation, UserNotifications, AuthenticationServices, Keychain, JavaScriptCore |
| **Android** | Kotlin, Jetpack Compose (Material 3), Room, Glance widgets, Android Keystore |
| **Backend** | Google Sheets + Google Apps Script, either run on the device or deployed as a web app |
| **Tests** | Swift Testing (iOS), JUnit (Android) |

## Project layout

```
MyPersonalTracker/        iOS app
  Sheet/                  Google Sheet features (money, study, food, parents, goals…) and SheetStore
  Sheet/Google/           Google sign-in, Sheets/Drive API, in-app Apps Script runtime
  Models/                 SwiftData models
  Support/                Health, reminders, focus timer, widget sync
  Views/                  Today, history, check-in, splash
  Resources/Backend/      The Apps Script backend (JavaScript)
TrackerWidgets/           Widgets and the focus Live Activity
Shared/                   Types shared by the app and the widgets
MyPersonalTrackerTests/   190 Swift Testing tests (sync loop, offline queue, auth retry, security, features)
android/                  Android app (Kotlin, Compose)
```

## Run it

**iOS** (Xcode 26, iOS 26.5+)
1. Open `MyPersonalTracker.xcodeproj`.
2. Choose your team for the app and `TrackerWidgets` targets. Change the bundle IDs and the App Group (`group.mohammedsharukms.MyPersonalTracker`) to ones your team owns.
3. Run. To see every screen with sample data and no sheet, add the launch argument `-sheetDemo` (Debug builds only).
4. To connect a real sheet, do one of these:
   - Create an iOS OAuth client in Google Cloud and paste its ID into `GoogleOAuthClientID` in `Config/MyPersonalTracker-Info.plist`.
   - Deploy `Resources/Backend` as an Apps Script web app, run its setup to get the API key, and enter the URL and key in Settings.

Run the tests with **Product ▸ Test** (⌘U).

**Android**: open `android/` in Android Studio, or run `./gradlew testDebugUnitTest` for the unit tests.

## Author

Mohammed Sharuk M S · iOS Developer · [github.com/mohammedsharukhh](https://github.com/mohammedsharukhh)
