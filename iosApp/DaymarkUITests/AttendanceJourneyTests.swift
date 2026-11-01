import XCTest

// Requires a preconfigured tracker and a simulator/device location inside its saved place.
// Native execution is deferred by the user while both installed runtimes are retained.
final class AttendanceJourneyTests: XCTestCase {
    func testForegroundCheckShowsSavedSourceAndHistory() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let today = app.descendants(matching: .any).matching(identifier: "today-root").firstMatch
        guard today.waitForExistence(timeout: 20) else {
            throw XCTSkip("Complete a tracker and register the test location before this device journey.")
        }
        let check = app.buttons.matching(identifier: "today-check").firstMatch
        guard check.isEnabled else { throw XCTSkip("Run during an eligible attendance window with no saved confirmation.") }
        check.tap()
        let source = app.staticTexts["Location checked manually"]
        XCTAssertTrue(source.waitForExistence(timeout: 40))
        let history = app.buttons.matching(identifier: "today-history").firstMatch
        for _ in 0..<5 { if history.isHittable { break }; app.swipeUp() }
        history.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "history-root").firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Location checked manually"].exists)
    }
}
