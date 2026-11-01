import XCTest

final class SetupJourneyTests: XCTestCase {
    private var app: XCUIApplication!
    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
    }
    private func node(_ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }
    private func scrollTo(_ id: String) -> XCUIElement {
        let element = node(id)
        for _ in 0..<8 { if element.isHittable { break }; app.swipeUp() }
        return element
    }
    func testArabicDraftSurvivesRelaunchWithoutInventingAPlace() throws {
        app.launch()
        XCTAssertTrue(node("setup-name").waitForExistence(timeout: 20))
        node("language-ar").tap()
        XCTAssertTrue(app.staticTexts["خطوتك الأولى نحو حضور منتظم"].waitForExistence(timeout: 10))
        XCTAssertGreaterThan(node("language-system").frame.midX, node("language-ar").frame.midX)
        let name = scrollTo("setup-name")
        name.tap(); name.typeText("أحمد / Ahmed")
        let place = scrollTo("setup-place-name")
        place.tap(); place.typeText("المكتب")
        _ = scrollTo("setup-draft-saved")
        XCTAssertTrue(node("setup-draft-saved").waitForExistence(timeout: 10))
        app.terminate(); app.launch()
        XCTAssertTrue(node("setup-name").waitForExistence(timeout: 20))
        XCTAssertEqual(node("setup-name").value as? String, "أحمد / Ahmed")
        XCTAssertFalse(node("setup-place-ready").exists)
        XCTAssertFalse(node("today-root").exists)
        XCTAssertTrue(app.staticTexts["خطوتك الأولى نحو حضور منتظم"].exists)
    }
    func testExplicitThemeAndLanguageCanReturnToSystem() throws {
        app.launch()
        XCTAssertTrue(node("language-en").waitForExistence(timeout: 20))
        node("language-en").tap()
        node("theme-dark").tap()
        XCTAssertTrue(app.staticTexts["Make attendance part of your routine"].waitForExistence(timeout: 10))
        node("theme-light").tap()
        node("language-system").tap()
        node("theme-system").tap()
        XCTAssertTrue(node("setup-name").exists)
    }
}
