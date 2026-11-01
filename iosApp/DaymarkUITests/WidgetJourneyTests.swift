import XCTest

// Requires a provisioned App Group, a dedicated QA tracker, and a pinned production widget.
// Physical iPhone and iOS UI execution remain open gates, not skipped verification successes.
final class WidgetJourneyTests: XCTestCase {
    func testPinnedWidgetOpensTheForegroundAttendanceScreen() throws {
        let app = XCUIApplication(); app.launch()
        let today = app.descendants(matching: .any).matching(identifier: "today-root").firstMatch
        guard today.waitForExistence(timeout: 20) else { throw XCTSkip("Configure a dedicated QA tracker before this device journey.") }
        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let widget = springboard.descendants(matching: .any).matching(identifier: "daymark-attendance-widget").firstMatch
        guard widget.waitForExistence(timeout: 10) else { throw XCTSkip("Pin the provisioned production Daymark widget on the visible home screen.") }
        widget.tap()
        XCTAssertTrue(today.waitForExistence(timeout: 20))
    }
}
