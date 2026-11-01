import XCTest
@testable import Daymark

final class WidgetSnapshotTests: XCTestCase {
    func testEnglishAndArabicRoundTrips() throws { try WidgetSnapshotFixtures.roundTrips() }
    func testUnknownAndInvalidPayloadsAreRejected() { WidgetSnapshotFixtures.rejectsUntrustedPayloads() }
    func testYesterdayIsNotTodaysConfirmation() { WidgetSnapshotFixtures.staleSnapshotIsNotTodaysConfirmation() }
}
