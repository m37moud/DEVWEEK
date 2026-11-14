import Foundation
#if canImport(Daymark)
@testable import Daymark
#endif

// Foundation-only checks reused by XCTest and the small macOS codec harness.
enum WidgetSnapshotFixtures {
    static let arabic = """
    {"schemaVersion":1,"trackerId":"f8428c3a-1347-40dc-9667-af38e69c6801","generatedAtEpochMillis":1793091720000,"attendanceDate":"2026-10-27","effectiveLocale":"ar","displayStatus":"حاضر","statusCode":"PRESENT","placeLabel":"المكتب / Office","deadlineDisplay":"٠٩:٠٥","deadlineEpochMillis":1793091900000,"zoneId":"UTC","quickCheckRoute":"daymark://quick-check?v=1&trackerId=f8428c3a-1347-40dc-9667-af38e69c6801","arrivalEpochMillis":1793091600000,"themePreference":"DARK","isTracked":true}
    """
    static func roundTrips() throws {
        for locale in ["en", "ar"] {
            let raw = arabic.replacingOccurrences(of: "\"effectiveLocale\":\"ar\"", with: "\"effectiveLocale\":\"\(locale)\"")
            let snapshot = NativeWidgetSnapshot.decode(Data(raw.utf8))!
            precondition(snapshot.attendanceDate == "2026-10-27")
            precondition(snapshot.placeLabel == "المكتب / Office")
            precondition(snapshot.effectiveLocale == locale)
            let encoded = try JSONEncoder().encode(snapshot)
            let decoded = NativeWidgetSnapshot.decode(encoded)!
            precondition(decoded.attendanceDate == snapshot.attendanceDate)
            precondition(decoded.themePreference == "DARK")
            precondition(decoded.deadlineDisplay == "٠٩:٠٥")
        }
    }
    static func rejectsUntrustedPayloads() {
        precondition(NativeWidgetSnapshot.decode(Data("{}".utf8)) == nil)
        for (from, to) in [("\"schemaVersion\":1", "\"schemaVersion\":2"), ("2026-10-27", "not-a-date"),
                           ("\"statusCode\":\"PRESENT\"", "\"statusCode\":\"MAGIC\""),
                           ("daymark://quick-check?v=1", "daymark://quick-check?v=2")] {
            precondition(NativeWidgetSnapshot.decode(Data(arabic.replacingOccurrences(of: from, with: to).utf8)) == nil)
        }
    }
    static func staleSnapshotIsNotTodaysConfirmation() {
        let snapshot = NativeWidgetSnapshot.decode(Data(arabic.utf8))!
        let now = ISO8601DateFormatter().date(from: "2026-10-28T09:18:00Z")!
        precondition(snapshot.resolvedStatus(at: now) == nil)
    }
    static func actionBoundaries() {
        let formatter = ISO8601DateFormatter()
        let raw = arabic.replacingOccurrences(of: "\"statusCode\":\"PRESENT\"", with: "\"statusCode\":\"SCHEDULED\"")
        let snapshot = NativeWidgetSnapshot.decode(Data(raw.utf8))!
        precondition(snapshot.action(at: formatter.date(from: "2026-10-27T08:59:59Z")!) == .disabled)
        precondition(snapshot.action(at: formatter.date(from: "2026-10-27T09:00:00Z")!) == .checkIn)
        precondition(snapshot.resolvedStatus(at: formatter.date(from: "2026-10-27T09:05:00Z")!) == "AWAITING_CHECK")
        precondition(snapshot.resolvedStatus(at: formatter.date(from: "2026-10-27T09:05:01Z")!) == "ABSENT")
        precondition(snapshot.action(at: formatter.date(from: "2026-10-28T09:00:00Z")!) == .openApp)
        let untracked = NativeWidgetSnapshot.decode(Data(raw.replacingOccurrences(of: "\"isTracked\":true", with: "\"isTracked\":false").utf8))!
        precondition(untracked.action(at: formatter.date(from: "2026-10-27T09:10:00Z")!) == .openApp)
    }

}
