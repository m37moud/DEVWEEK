import XCTest
@testable import Daymark

final class WidgetSnapshotTests: XCTestCase {
    func testEnglishAndArabicRoundTrips() throws { try WidgetSnapshotFixtures.roundTrips() }
    func testUnknownAndInvalidPayloadsAreRejected() { WidgetSnapshotFixtures.rejectsUntrustedPayloads() }
    func testWidgetActions() { WidgetSnapshotFixtures.actionBoundaries() }
    func testYesterdayIsNotTodaysConfirmation() { WidgetSnapshotFixtures.staleSnapshotIsNotTodaysConfirmation() }
}
