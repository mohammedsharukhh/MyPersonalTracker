//
//  ParentTests.swift
//  MyPersonalTrackerTests
//

import Foundation
import Testing
@testable import MyPersonalTracker

@Suite("Mom & Dad asked")
struct ParentAskTests {
    private func d(_ text: String) throws -> Date { try #require(SheetDate.date(text)) }

    @Test func keepsRemindingEveryDayUntilDone() throws {
        let a = ParentAsk(task: "Book eye check-up", who: .dad, date: "2026-10-10", time: "09:00", nag: true)
        #expect(!a.reminds(on: try d("2026-10-09")))           // not before its day
        #expect(a.reminds(on: try d("2026-10-10")))
        #expect(a.reminds(on: try d("2026-10-14")))            // late: still every day
        let done = a.markedDone(on: try d("2026-10-14"))
        #expect(!done.isOpen)
        #expect(!done.reminds(on: try d("2026-10-15")))
    }

    @Test func withoutNaggingOnlyOnItsDates() throws {
        let once = ParentAsk(task: "Send photos", date: "2026-10-10", nag: false)
        #expect(once.reminds(on: try d("2026-10-10")))
        #expect(!once.reminds(on: try d("2026-10-11")))
        let weekly = ParentAsk(task: "Call Grandma", date: "2026-10-11", repeats: .weekly, nag: false)
        #expect(weekly.reminds(on: try d("2026-10-18")))
        #expect(!weekly.reminds(on: try d("2026-10-17")))
        var silent = once
        silent.time = nil
        #expect(!silent.reminds(on: try d("2026-10-10")))      // on the list, no notification
    }

    @Test func repeatingMovesToItsNextDate() throws {
        let weekly = ParentAsk(task: "Call Grandma", date: "2026-10-11", repeats: .weekly)
        #expect(weekly.markedDone(on: try d("2026-10-11")).date == "2026-10-18")
        #expect(weekly.markedDone(on: try d("2026-10-10")).date == "2026-10-18")   // done early skips the coming one
        #expect(weekly.markedDone(on: try d("2026-10-27")).date == "2026-11-01")   // missed a few
        #expect(weekly.markedDone(on: try d("2026-10-11")).isOpen)                 // repeating ones stay on the list
        let monthly = ParentAsk(task: "Tablets", date: "2026-01-31", repeats: .monthly)
        #expect(monthly.markedDone(on: try d("2026-01-31")).date == "2026-02-28")
        #expect(monthly.falls(on: try d("2026-02-28")))                            // a 31st falls on a short month's last day
        #expect(!monthly.falls(on: try d("2026-02-27")))
        #expect(ParentAsk(task: "x", date: "2026-02-28", repeats: .monthly).falls(on: try d("2026-03-28")))
    }

    @Test func requestsAtTheSameTimeShareOneNotification() throws {
        let now = try d("2026-10-17").addingTimeInterval(8 * 3600)   // 08:00
        let asks = [
            ParentAsk(id: "a", task: "Pay electricity bill", who: .mom, date: "2026-10-17", time: "09:00", nag: false),
            ParentAsk(id: "b", task: "Buy BP tablets", who: .dad, date: "2026-10-17", time: "09:00", nag: false),
            ParentAsk(id: "c", task: "Send photos", who: .mom, date: "2026-10-17", time: "07:00", nag: false),   // already passed
        ]
        let slots = ParentReminders.slots(asks, from: now)
        #expect(slots.count == 1)
        #expect(slots[0].asks.map(\.id) == ["a", "b"])
        let m = ParentReminders.message(slots[0].asks, on: slots[0].fire)
        #expect(m.title == "2 things Mom & Dad asked for")
        #expect(m.body.contains("Dad: Buy BP tablets"))
        let one = ParentReminders.message([asks[0]], on: slots[0].fire)
        #expect(one.title == "Mom asked: Pay electricity bill")
    }

    @Test func lateOnesAreListedFirstAndDoneOnesApart() throws {
        let json = """
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"expenses":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "parents":[{"id":"1","task":"Later","who":"Mom","date":"2026-10-20","time":"09:00","repeats":"Once","nag":true},
                    {"id":"2","task":"Late","who":"Dad","date":"2026-10-12","time":null,"repeats":"Once","nag":"Y"},
                    {"id":"3","task":"Done","who":"Both","date":"2026-10-01","repeats":"Once","nag":false,"done":"2026-10-02"}]}
        """
        let s = try JSONDecoder().decode(SheetSnapshot.self, from: Data(json.utf8))
        #expect(s.openParentAsks.map(\.task) == ["Late", "Later"])
        #expect(s.doneParentAsks.map(\.task) == ["Done"])
        #expect(s.parentAsks.first { $0.id == "2" }?.nag == true)          // the sheet's "Y"
        #expect(s.parentAsksDue(by: "2026-10-17").map(\.task) == ["Late"])
        #expect(Briefing.text(s, on: try d("2026-10-17"), includeLive: false).body.contains("👪 Dad: Late"))
    }

    @Test func aQueuedSaveShowsAtOnceAndDeleteRemovesIt() throws {
        var s = try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"expenses":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}}
        """.utf8))
        let ask = ParentAsk(id: "x", task: "Fix the fan", who: .dad, date: "2026-10-18")
        let payload = try #require(try JSONSerialization.jsonObject(with: JSONEncoder().encode(ask)) as? [String: Any])
        SheetStore.applyLocally(PendingWrite(id: "1", action: "saveParentAsk", payload: payload, summary: "", createdAt: .now, notBefore: nil, tempRow: nil), to: &s)
        #expect(s.parentAsks.first?.task == "Fix the fan")
        #expect(s.parentAsks.first?.pending == true)
        #expect(payload["repeats"] as? String == "Once")      // what 31_Parents.gs reads
        SheetStore.applyLocally(PendingWrite(id: "2", action: "deleteParentAsk", payload: ["id": "x"], summary: "", createdAt: .now, notBefore: nil, tempRow: nil), to: &s)
        #expect(s.parentAsks.isEmpty)
    }
}

@Suite("Mom & Dad: photos, contacts, snooze, cost")
struct ParentExtrasTests {
    private func d(_ text: String) throws -> Date { try #require(SheetDate.date(text)) }

    @Test func readsPhotosAndCostFromTheSheet() throws {
        let fromSheet = try JSONDecoder().decode(ParentAsk.self, from: Data(#"{"id":"1","task":"Tablets","date":"2026-10-17","photos":["a1","b2"],"cost":"85","finance":"Y"}"#.utf8))
        #expect(fromSheet.photos == ["a1", "b2"])
        #expect(fromSheet.cost == 85)
        #expect(fromSheet.finance)
        let typed = try JSONDecoder().decode(ParentAsk.self, from: Data(#"{"id":"1","task":"x","date":"2026-10-17","photos":"a1, b2"}"#.utf8))
        #expect(typed.photos == ["a1", "b2"])                     // as typed into the cell
        let old = try JSONDecoder().decode(ParentAsk.self, from: Data(#"{"id":"1","task":"x","date":"2026-10-17"}"#.utf8))
        #expect(old.photos.isEmpty && old.cost == nil && !old.finance)
        #expect(ParentPhotos.localKey("local:abc") == "abc")
        #expect(ParentPhotos.localKey("1AbCdriveId") == nil)
    }

    @Test func tomorrowSkipsOneDayOnly() throws {
        let now = try d("2026-10-17").addingTimeInterval(8 * 3600)
        let a = ParentAsk(id: "a", task: "Call the plumber", date: "2026-10-15", time: "09:00", nag: true)
        let slots = ParentReminders.slots([a], from: now, days: 3, snoozed: ["a": "2026-10-18"])
        #expect(slots.map { SheetDate.string($0.fire) } == ["2026-10-18", "2026-10-19"])
        #expect(ParentReminders.slots([a], from: now, days: 3).count == 3)
    }

    @Test func messagesAndLinks() throws {
        let open = ParentAsk(task: "Book eye check-up", who: .dad, date: "2026-10-17")
        #expect(ParentContacts.message(open, to: .dad, today: "2026-10-17").hasPrefix("Hi Dad, about"))
        let done = open.markedDone(on: try d("2026-10-17"))
        #expect(ParentContacts.message(done, to: .dad, today: "2026-10-17") == "Hi Dad, done ✅ Book eye check-up")
        #expect(ParentContacts.digits("+91 98765-43210") == "919876543210")
        #expect(ParentContacts.whatsAppURL("+91 98765 43210", text: "Hi Dad")?.absoluteString == "https://wa.me/919876543210?text=Hi%20Dad")
        #expect(ParentContacts.callURL("+91 98765 43210")?.absoluteString == "tel:+919876543210")
        #expect(ParentContacts.people(for: .both) == [.mom, .dad])
    }

    @Test func spendForThemThisMonth() throws {
        let s = try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]},
         "expenses":[{"row":5,"date":"2026-10-03","category":"Gifts & Family","qar":85,"notes":"Mom & Dad"},
                     {"row":6,"date":"2026-10-04","category":"Gifts & Family","qar":40,"notes":"Birthday"},
                     {"row":7,"date":"2026-09-30","category":"Gifts & Family","qar":60,"notes":"Mom & Dad"}]}
        """.utf8))
        #expect(s.parentSpend(month: "2026-10") == 85)
    }
}

@Suite("Mom & Dad: medicines, dates, calls, sharing")
struct ParentCareTests {
    private func d(_ text: String) throws -> Date { try #require(SheetDate.date(text)) }

    private func snapshot(_ extra: String) throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"expenses":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}\(extra)}
        """.utf8))
    }

    @Test func medicinesCountDownAndRefill() throws {
        let m = ParentMedicine(who: .dad, name: "Telmisartan", perDay: 2, left: 30, counted: "2026-10-10", warn: 7, pack: 30)
        #expect(m.leftOn(try d("2026-10-15")) == 20)
        #expect(m.daysLeft(on: try d("2026-10-15")) == 10)
        #expect(!m.runsLow(on: try d("2026-10-15")))
        #expect(m.runsLow(on: try d("2026-10-18")))                 // 14 left = 7 days
        #expect(m.leftOn(try d("2026-11-30")) == 0)                 // never below zero
        #expect(m.status(on: try d("2026-11-30")) == "Ran out")
        let refilled = m.refilled(with: 30, on: try d("2026-10-18"))
        #expect(refilled.left == 44 && refilled.counted == "2026-10-18")
        var stopped = m; stopped.stopped = true
        let later = try d("2026-10-18")
        #expect(stopped.daysLeft(on: later) == nil && !stopped.runsLow(on: later))
        let fromSheet = try JSONDecoder().decode(ParentMedicine.self, from: Data(#"{"id":"1","who":"Mom","name":"D3","perDay":"0.5","left":10,"counted":"2026-10-01","warn":5,"finance":"Y"}"#.utf8))
        #expect(fromSheet.perDay == 0.5 && fromSheet.warn == 5 && fromSheet.finance)
    }

    @Test func datesComeRoundEveryYear() throws {
        let b = ParentDate(title: "Mom's birthday", who: .mom, kind: .birthday, day: "10-24", year: 1966)
        #expect(b.daysUntil(from: try d("2026-10-17")) == 7)
        #expect(b.headline(from: try d("2026-10-17")) == "Mom's birthday — turns 60")
        #expect(b.daysUntil(from: try d("2026-10-25")) == 364)        // next year's
        let leap = ParentDate(title: "x", day: "02-29")
        #expect(leap.next(from: try d("2027-01-10")).map(SheetDate.string) == "2027-02-28")
        let a = ParentDate(title: "Anniversary", who: .both, kind: .anniversary, day: "11-02", year: 1991)
        #expect(a.headline(from: try d("2026-10-17")).hasSuffix("35th"))
        let sheet = try JSONDecoder().decode(ParentDate.self, from: Data(#"{"id":"1","title":"x","who":"Dad","kind":"Remembrance","day":"08-03","year":"","remind":"1,0"}"#.utf8))
        #expect(sheet.year == nil && sheet.remind == [1, 0] && sheet.kind == .remembrance)
    }

    @Test func theDailyNotificationSaysWhatsDue() throws {
        let s = try snapshot("""
        ,"parentMedicines":[{"id":"m","who":"Dad","name":"Telmisartan","perDay":1,"left":5,"counted":"2026-10-17","warn":7}],
         "parentDates":[{"id":"d","title":"Mom's birthday","who":"Mom","kind":"Birthday","day":"10-24","year":1966,"remind":"7,1,0"}]
        """)
        let day = try d("2026-10-17")
        let lines = ParentDigest.lines(s, on: day, last: [.mom: "2026-10-12"], every: [.mom: 3, .dad: 0])
        #expect(lines.contains { $0.hasPrefix("💊 Dad's Telmisartan") && $0.contains("5 days") })
        #expect(lines.contains("🎂 Mom's birthday — turns 60 · in 7 days"))
        #expect(lines.contains("📞 You haven't talked to Mom in 5 days"))
        #expect(ParentDigest.lines(s, on: try d("2026-10-18"), last: [:], every: [:]).count == 1)   // birthday not a reminder day
        #expect(ParentDigest.message(lines).title == "Mom & Dad · 3 things")
        #expect(!ParentDigest.lines(s, on: day, last: [.mom: "2026-10-16"], every: [.mom: 3]).contains { $0.hasPrefix("📞") })
    }

    @Test func shareSaysWhatsStillToDo() throws {
        let s = try snapshot("""
        ,"parents":[{"id":"1","task":"Book eye check-up","who":"Dad","date":"2026-10-15","repeats":"Once"},
                    {"id":"2","task":"Send photos","who":"Mom","date":"2026-10-17","repeats":"Once","done":"2026-10-17"}]
        """)
        let text = ParentShare.text(s, today: try d("2026-10-17"))
        #expect(text.hasPrefix("Still to do for Mom & Dad:"))
        #expect(text.contains("• Book eye check-up — Dad, 2 days late (late)"))
        #expect(!text.contains("Send photos"))
    }
}

@Suite("Mom & Dad: trip home, health, money")
struct ParentHomeTests {
    private func d(_ text: String) throws -> Date { try #require(SheetDate.date(text)) }

    private func snapshot(_ extra: String) throws -> SheetSnapshot {
        try JSONDecoder().decode(SheetSnapshot.self, from: Data("""
        {"settings":{},"lists":{"categories":[],"methods":[]},"dashboard":[],"dashboardTables":[],
         "budget":{"headers":[],"rows":[]},"days":[],"salary":[],
         "weekly":{"headers":[],"rows":[]},"monthly":{"headers":[],"rows":[]}\(extra)}
        """.utf8))
    }

    @Test func tripCountsDownAndNudges() throws {
        let s = try snapshot("""
        ,"expenses":[],"takeHome":{"trip":"2026-10-24","items":[{"id":"1","item":"Dates","who":"Mom","got":false},
                                                              {"id":"2","item":"Perfume","who":"Mom","got":"Y"}]}
        """)
        let home = s.takeHomeData
        #expect(home.daysToTrip(from: try d("2026-10-17")) == 7)
        #expect(home.countdown(from: try d("2026-10-23")) == "Trip home tomorrow")
        #expect(home.daysToTrip(from: try d("2026-10-25")) == nil)          // passed
        #expect(home.toGet == 1 && home.sorted.first?.item == "Dates")
        let lines = ParentDigest.lines(s, on: try d("2026-10-17"), last: [:], every: [:])
        #expect(lines == ["🧳 Trip home in 7 days — 1 thing still to get"])
        #expect(ParentDigest.lines(s, on: try d("2026-10-18"), last: [:], every: [:]).isEmpty)   // 6 days: not a nudge day
    }

    @Test func readingsChartAndDoctorSummary() throws {
        let s = try snapshot("""
        ,"expenses":[],"parentHealth":[{"id":"1","date":"2026-10-10","who":"Dad","kind":"Blood pressure","value":140,"value2":90},
                                       {"id":"2","date":"2026-10-16","time":"08:30","who":"Dad","kind":"Blood pressure","value":130,"value2":84},
                                       {"id":"3","date":"2026-10-16","who":"Mom","kind":"Sugar (fasting)","value":"110"}]
        """)
        #expect(s.readings(.bloodPressure, who: .dad).map(\.value) == [140, 130])     // oldest first, for the chart
        #expect(s.readingList.first?.display == "130/84 mmHg")
        let text = ParentHealthShare.text(s, who: .dad, today: try d("2026-10-17"))
        #expect(text.hasPrefix("Dad's readings — last 30 days"))
        #expect(text.contains("Average: 135/87 over 2 readings"))
        #expect(!text.contains("Sugar"))                                               // Mom's isn't in Dad's
    }

    @Test func moneyForThemThisMonth() throws {
        let s = try snapshot("""
        ,"expenses":[{"row":5,"date":"2026-10-02","category":"Remittance","qar":1500,"inr":39150},
                     {"row":6,"date":"2026-10-03","category":"Gifts & Family","qar":85,"notes":"Mom & Dad"},
                     {"row":7,"date":"2026-09-28","category":"Remittance","qar":1000,"inr":26100}]
        """)
        let m = s.parentMoney(month: "2026-10")
        #expect(m == ParentMoney(sent: 1500, sentHome: 39150, spent: 85))
        #expect(m.total == 1585)
    }
}
