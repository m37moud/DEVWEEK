import XCTest

// Requires a dedicated QA profile. Native execution and live Google consent remain open gates.
final class GoogleConnectionJourneyTests: XCTestCase {
    func testDeletionRequiresConfirmationInEnglishAndArabic() throws {
        let app = XCUIApplication(); app.launch()
        func node(_ id: String) -> XCUIElement { app.descendants(matching: .any).matching(identifier: id).firstMatch }
        guard node("nav-settings").waitForExistence(timeout: 20) else {
            throw XCTSkip("Requires a configured QA tracker; a skip is not acceptance evidence.")
        }
        node("nav-settings").tap()
        for language in ["en", "ar"] {
            let choice = node("settings-language-\(language)")
            for _ in 0..<20 { if choice.isHittable { break }; app.swipeDown() }
            choice.tap()
            let deletion = node("delete-local-data")
            for _ in 0..<20 { if deletion.isHittable { break }; app.swipeUp() }
            XCTAssertTrue(deletion.isHittable); deletion.tap()
            XCTAssertTrue(node("report-confirm-action").waitForExistence(timeout: 10))
            app.buttons[language == "ar" ? "إلغاء" : "Cancel"].firstMatch.tap()
            XCTAssertTrue(node("nav-settings").exists)
            XCTAssertFalse(node("report-confirm-action").exists)
        }
    }
}
