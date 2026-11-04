import XCTest

final class SetupJourneyTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
    }

    private func node(_ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    private func scrollTo(_ id: String) -> XCUIElement {
        let element = node(id)
        for _ in 0..<12 {
            if element.isHittable { break }
            app.swipeUp()
        }
        return element
    }

    private func preferencesAtTop() {
        for _ in 0..<12 {
            if node("language-en").isHittable { break }
            app.swipeDown()
        }
    }

    private func openProfile() {
        app.launch()
        XCTAssertTrue(node("setup-next").waitForExistence(timeout: 20))
        for _ in 0..<4 {
            if !node("setup-back").exists { break }
            node("setup-back").tap()
            XCTAssertTrue(node("setup-next").waitForExistence(timeout: 10))
        }
        preferencesAtTop()
        node("language-en").tap()
        node("setup-next").tap()
        XCTAssertTrue(node("setup-name").waitForExistence(timeout: 10))
    }

    private func replaceText(_ field: XCUIElement, with text: String) {
        field.tap()
        if let current = field.value as? String, current != field.label {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        field.typeText(text)
    }

    func testArabicDraftSurvivesRelaunchWithoutInventingAPlace() throws {
        openProfile()
        preferencesAtTop()
        node("language-ar").tap()
        XCTAssertGreaterThan(node("language-system").frame.midX, node("language-ar").frame.midX)
        replaceText(scrollTo("setup-name"), with: "أحمد / Ahmed")
        node("setup-next").tap()
        XCTAssertTrue(node("setup-place-name").waitForExistence(timeout: 10))
        replaceText(scrollTo("setup-place-name"), with: "المكتب")
        XCTAssertTrue(node("setup-draft-saved").waitForExistence(timeout: 10))
        app.terminate()
        app.launch()
        XCTAssertTrue(node("setup-place-name").waitForExistence(timeout: 20))
        XCTAssertEqual(node("setup-place-name").value as? String, "المكتب")
        XCTAssertFalse(node("setup-place-ready").exists)
        XCTAssertFalse(node("today-root").exists)
        node("setup-back").tap()
        XCTAssertTrue(node("setup-name").waitForExistence(timeout: 10))
        XCTAssertEqual(node("setup-name").value as? String, "أحمد / Ahmed")
    }

    func testExplicitThemeAndLanguageCanReturnToSystem() throws {
        openProfile()
        preferencesAtTop()
        node("theme-dark").tap()
        XCTAssertEqual(node("setup-heading").label, "Your profile")
        node("theme-light").tap()
        node("language-system").tap()
        node("theme-system").tap()
        XCTAssertTrue(node("setup-name").exists)
        XCTAssertFalse(node("today-root").exists)
    }
    func testWelcomeLanguageThemeMatrixAtLargeText() throws {
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        openProfile()
        node("setup-back").tap()
        XCTAssertTrue(node("setup-heading").waitForExistence(timeout: 10))
        for language in ["en", "ar"] {
            preferencesAtTop()
            node("language-" + language).tap()
            for theme in ["light", "dark"] {
                preferencesAtTop()
                node("theme-" + theme).tap()
                let heading = scrollTo("setup-heading")
                XCTAssertEqual(heading.label, language == "en" ? "Welcome to Daymark" : "مرحبًا بك في دايمارك")
                XCTAssertTrue(node("setup-next").isHittable)
                let evidence = XCTAttachment(screenshot: app.screenshot())
                evidence.name = "onboarding-ios-" + language + "-" + theme
                evidence.lifetime = .keepAlways
                add(evidence)
            }
        }
    }

}
