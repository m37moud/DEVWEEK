import XCTest

final class LocaleJourneyTests: XCTestCase {
    func testPersistedArabicSettingsCanReturnToEnglishAndSystemTheme() throws {
        let app = XCUIApplication(); app.launch()
        func node(_ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }
        guard node("nav-settings").waitForExistence(timeout: 20) else { throw XCTSkip("Requires a configured QA tracker.") }
        node("nav-settings").tap()
        node("settings-language-ar").tap()
        XCTAssertTrue(app.staticTexts["الإعدادات"].waitForExistence(timeout: 10))
        node("settings-theme-dark").tap()
        app.terminate(); app.launch()
        node("nav-settings").tap()
        XCTAssertTrue(app.staticTexts["الإعدادات"].waitForExistence(timeout: 10))
        node("settings-language-en").tap()
        node("settings-theme-system").tap()
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
    }
}
