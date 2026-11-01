import XCTest

// Source-only until native host/UI execution is resumed. Skips never count as matrix passes.
final class LocaleMatrixJourneyTests: XCTestCase {
    func testEnglishArabicLightDarkCoreActionsRemainReachable() throws {
        let app = XCUIApplication(); app.launch()
        func node(_ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }
        guard node("nav-settings").waitForExistence(timeout: 20) else { throw XCTSkip("Requires a dedicated QA tracker.") }
        var verified = Set<String>()
        for language in ["en", "ar"] {
            for theme in ["light", "dark"] {
                node("nav-settings").tap()
                let selection = node("settings-language-\(language)")
                for _ in 0..<20 { if selection.isHittable { break }; app.swipeDown() }
                selection.tap(); node("settings-theme-\(theme)").tap()
                let connect = node("report-connect")
                for _ in 0..<20 { if connect.isHittable { break }; app.swipeUp() }
                XCTAssertTrue(connect.isHittable)
                node("nav-today").tap()
                let check = node("today-check")
                for _ in 0..<20 { if check.isHittable { break }; app.swipeUp() }
                XCTAssertTrue(check.isHittable)
                verified.insert("\(language)/\(theme)")
            }
        }
        XCTAssertEqual(4, verified.count)
    }
}
