import XCTest
import UserNotifications
import DaymarkShared
@testable import Daymark

@MainActor
final class ReminderAdapterTests: XCTestCase {
    private let tracker = "f8428c3a-1347-40dc-9667-af38e69c6801"
    private let fire = ISO8601DateFormatter().date(from: "2030-11-02T09:00:00Z")!
    private final class Center: ReminderNotificationCenter {
        var granted = true
        var requests: [String: UNNotificationRequest] = [:]
        var pendingHook: (() async -> Void)?
        var failAdd = false
        func requestPermission() async throws -> Bool { granted }
        func pendingRequests() async -> [UNNotificationRequest] { await pendingHook?(); return Array(requests.values) }
        func add(_ request: UNNotificationRequest) async throws {
            if failAdd { throw NSError(domain: "unit-test", code: 1) }
            requests[request.identifier] = request
        }
        func remove(_ identifiers: [String]) { identifiers.forEach { requests.removeValue(forKey: $0) } }
    }
    private func payload(title: String = "Arrival", empty: Bool = false) -> String {
        let reminders: [[String: Any]] = empty ? [] : [["id": "daymark.reminder:\(tracker):2030-11-02", "trackerId": tracker,
            "attendanceDate": "2030-11-02", "fireAtEpochMillis": Int64(fire.timeIntervalSince1970 * 1000),
            "title": title, "body": "Open to check in", "localeTag": title == "Arrival" ? "en" : "ar"]]
        return String(data: try! JSONSerialization.data(withJSONObject: ["trackerId": tracker, "reminders": reminders]), encoding: .utf8)!
    }
    private func replace(_ adapter: IosReminderAdapter, _ json: String) async -> Bool {
        await withCheckedContinuation { continuation in adapter.replace(remindersJson: json) { continuation.resume(returning: $0.boolValue) } }
    }
    func testReplacementOwnsOnlyItsTrackerAndLanguageChangeKeepsIdentity() async {
        let center = Center()
        let unrelated = UNNotificationRequest(identifier: "other-feature", content: UNMutableNotificationContent(), trigger: nil)
        let otherTracker = UNNotificationRequest(identifier: "daymark.reminder:other:2030-11-02", content: UNMutableNotificationContent(), trigger: nil)
        center.requests[unrelated.identifier] = unrelated
        center.requests[otherTracker.identifier] = otherTracker
        let adapter = IosReminderAdapter(center: center, now: { Date(timeIntervalSince1970: 0) })
        let result1 = await replace(adapter, payload())
        XCTAssertTrue(result1)
        let result2 = await replace(adapter, payload(title: "حان وقت الحضور"))
        XCTAssertTrue(result2)
        XCTAssertEqual(center.requests.count, 3)
        XCTAssertEqual(center.requests["daymark.reminder:\(tracker):2030-11-02"]?.content.title, "حان وقت الحضور")
        let result3 = await replace(adapter, payload(empty: true))
        XCTAssertTrue(result3)
        XCTAssertEqual(Set(center.requests.keys), [unrelated.identifier, otherTracker.identifier])
    }
    func testTriggerUsesAbsoluteGregorianUTCComponentsWithoutRepeating() async throws {
        let center = Center()
        let adapter = IosReminderAdapter(center: center, now: { Date(timeIntervalSince1970: 0) })
        let result4 = await replace(adapter, payload())
        XCTAssertTrue(result4)
        let request = try XCTUnwrap(center.requests.values.first)
        let trigger = try XCTUnwrap(request.trigger as? UNCalendarNotificationTrigger)
        XCTAssertFalse(trigger.repeats)
        XCTAssertEqual(trigger.dateComponents.timeZone?.secondsFromGMT(), 0)
        XCTAssertEqual(trigger.dateComponents.calendar?.identifier, .gregorian)
        XCTAssertEqual(trigger.dateComponents.year, 2030)
        XCTAssertEqual(trigger.dateComponents.month, 11)
        XCTAssertEqual(trigger.dateComponents.day, 2)
        XCTAssertEqual(trigger.dateComponents.hour, 9)
        XCTAssertEqual(trigger.nextTriggerDate(), fire)
        XCTAssertEqual(request.content.userInfo["intent"] as? String, "quick-check")
    }
    func testDeniedPermissionDoesNotScheduleOrRemoveAnything() async {
        let center = Center(); center.granted = false
        let adapter = IosReminderAdapter(center: center)
        let allowed = await withCheckedContinuation { continuation in
            adapter.requestPermission { continuation.resume(returning: $0.boolValue) }
        }
        XCTAssertFalse(allowed)
        XCTAssertTrue(center.requests.isEmpty)
    }
    func testMalformedOrCrossTrackerPayloadDoesNotRemovePendingRequests() async {
        let center = Center()
        let adapter = IosReminderAdapter(center: center, now: { Date(timeIntervalSince1970: 0) })
        let result5 = await replace(adapter, payload())
        XCTAssertTrue(result5)
        let result6 = await replace(adapter, "invalid-json")
        XCTAssertFalse(result6)
        let result7 = await replace(adapter, payload().replacingOccurrences(of: "\"trackerId\":\"\(tracker)\"", with: "\"trackerId\":\"another\""))
        XCTAssertFalse(result7)
        XCTAssertEqual(center.requests.count, 1)
    }
    func testCancelFencesAReplaceWaitingForTheNativePendingQueue() async {
        let center = Center()
        let adapter = IosReminderAdapter(center: center, now: { Date(timeIntervalSince1970: 0) })
        let started = expectation(description: "pending native read")
        let replaced = expectation(description: "obsolete replacement rejected")
        let cancelled = expectation(description: "cancel completed")
        var resume: CheckedContinuation<Void, Never>?
        center.pendingHook = {
            center.pendingHook = nil
            await withCheckedContinuation { continuation in resume = continuation; started.fulfill() }
        }
        adapter.replace(remindersJson: payload()) { success in XCTAssertFalse(success.boolValue); replaced.fulfill() }
        await fulfillment(of: [started], timeout: 2)
        adapter.cancelTracker(trackerId: tracker) { success in XCTAssertTrue(success.boolValue); cancelled.fulfill() }
        // Give the cancellation request's main-actor task a chance to invalidate the pending operation.
        await Task.yield()
        resume?.resume()
        await fulfillment(of: [replaced, cancelled], timeout: 2)
        XCTAssertTrue(center.requests.isEmpty)
    }
}
