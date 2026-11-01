import XCTest

// Run only against a dedicated QA profile. Native execution is currently deferred.
final class PlanJourneyTests: XCTestCase {
    func testTimeOffPreviewCanBeCancelledWithoutSaving() throws {
        let app = XCUIApplication(); app.launch()
        func node(_ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }
        guard node("nav-settings").waitForExistence(timeout: 20) else { throw XCTSkip("Requires a configured QA tracker.") }
        node("nav-settings").tap()
        node("settings-language-en").tap()
        node("nav-plan").tap()
        let add = node("plan-add-time-off")
        for _ in 0..<20 { if add.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(add.isHittable); add.tap()
        node("time-off-preview").tap()
        XCTAssertTrue(app.staticTexts["Review before saving"].waitForExistence(timeout: 10))
        XCTAssertTrue(node("preview-confirm").exists)
        app.buttons["Cancel"].firstMatch.tap()
        XCTAssertFalse(node("preview-confirm").exists)
    }
}
