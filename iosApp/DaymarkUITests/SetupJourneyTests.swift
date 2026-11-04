import XCTest

final class SetupJourneyTests: XCTestCase {
    private var app: XCUIApplication!
    private var completedOnboarding = false

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
    }

    override func tearDownWithError() throws {
        if completedOnboarding {
            app.launch()
            if node("nav-settings").waitForExistence(timeout: 10) {
                node("nav-settings").tap()
                scrollTo("delete-local-data").tap()
                XCTAssertTrue(node("report-confirm-action").waitForExistence(timeout: 10))
                node("report-confirm-action").tap()
                XCTAssertTrue(node("setup-next").waitForExistence(timeout: 20))
            }
        }
        app.terminate()
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

    private func openProfile() {
        app.launch()
        XCTAssertTrue(node("setup-next").waitForExistence(timeout: 20))
        for _ in 0..<4 {
            if !node("setup-back").exists { break }
            node("setup-back").tap()
            XCTAssertTrue(node("setup-next").waitForExistence(timeout: 10))
        }
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
        app.launchArguments = ["-AppleLanguages", "(ar)", "-AppleLocale", "ar_EG"]
        openProfile()
        XCTAssertFalse(node("language-ar").exists)
        XCTAssertFalse(node("theme-dark").exists)
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

    func testExplicitThemeAndLanguageCanReturnToSystemInSettings() throws {
        openProfile()
        replaceText(scrollTo("setup-name"), with: "Local test")
        node("setup-next").tap()
        XCTAssertTrue(node("setup-place-name").waitForExistence(timeout: 10))
        replaceText(scrollTo("setup-place-name"), with: "Test place")
        addUIInterruptionMonitor(withDescription: "Location permission") { alert in
            let allow = alert.buttons["Allow While Using App"]
            if allow.exists { allow.tap(); return true }
            return false
        }
        scrollTo("setup-register-place").tap()
        app.tap()
        XCTAssertTrue(node("setup-place-ready").waitForExistence(timeout: 40),
                      "Configure a simulator location before running this native journey")
        node("setup-next").tap()
        XCTAssertTrue(node("setup-grace").waitForExistence(timeout: 10))
        node("setup-next").tap()
        XCTAssertTrue(node("setup-customize").waitForExistence(timeout: 10))
        completedOnboarding = true
        node("setup-customize").tap()
        XCTAssertTrue(node("settings-root").waitForExistence(timeout: 20))
        scrollTo("settings-theme-dark").tap()
        scrollTo("settings-theme-light").tap()
        scrollTo("settings-language-ar").tap()
        XCTAssertTrue(node("settings-language-ar").isSelected)
        scrollTo("settings-language-system").tap()
        scrollTo("settings-theme-system").tap()
        XCTAssertTrue(node("settings-language-system").isSelected)
        XCTAssertTrue(node("settings-theme-system").isSelected)
        app.terminate()
        app.launch()
        XCTAssertTrue(node("today-root").waitForExistence(timeout: 20))
        node("nav-settings").tap()
        XCTAssertTrue(node("settings-theme-system").waitForExistence(timeout: 10))
        XCTAssertTrue(node("settings-language-system").isSelected)
        XCTAssertTrue(node("settings-theme-system").isSelected)
    }

    func testWelcomeLanguageThemeMatrixAtLargeText() throws {
        for language in ["en", "ar"] {
            for theme in ["Light", "Dark"] {
                app.terminate()
                app.launchArguments = [
                    "-AppleLanguages", "(\(language))", "-AppleLocale", language == "ar" ? "ar_EG" : "en_US",
                    "-AppleInterfaceStyle", theme,
                    "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"
                ]
                openProfile()
                node("setup-back").tap()
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
