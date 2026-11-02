import XCTest
import DaymarkShared
@testable import Daymark

@MainActor
final class GoogleAuthBridgeTests: XCTestCase {
    private final class Driver: IosAuthorizationDriver {
        var requestedScopes: [String] = []
        var presentations = 0
        var callback: ((NativeAuthReply) -> Void)?
        func authorize(scopes: [String], interactive: Bool, completion: @escaping (NativeAuthReply) -> Void) {
            requestedScopes = scopes
            if interactive { presentations += 1 }
            callback = completion
        }
        func forget(completion: @escaping (Bool) -> Void) { completion(true) }
    }

    func testSilentAuthorizationRequestsOnlyDriveFileAndDoesNotPresent() async {
        let driver = Driver()
        let adapter = IosGoogleAuthAdapter(driver: driver)
        let done = expectation(description: "silent reply")
        adapter.authorize(interactive: false) { reply in
            XCTAssertEqual(reply.status, NativeAuthStatus.needsInteraction)
            done.fulfill()
        }
        await Task.yield()
        XCTAssertEqual(driver.presentations, 0)
        XCTAssertEqual(driver.requestedScopes, ["https://www.googleapis.com/auth/drive.file"])
        driver.callback?(NativeAuthReply(status: .needsInteraction, accessToken: nil, errorKey: nil))
        await fulfillment(of: [done], timeout: 2)
    }

    func testDuplicateCallbacksCompleteOnceAndForgetFencesOlderReply() async {
        let driver = Driver()
        let adapter = IosGoogleAuthAdapter(driver: driver)
        var statuses: [NativeAuthStatus] = []
        adapter.authorize(interactive: true) { reply in statuses.append(reply.status) }
        await Task.yield()
        adapter.forget { _ in }
        await Task.yield()
        driver.callback?(NativeAuthReply(status: .granted, accessToken: "obsolete-test-token", errorKey: nil))
        driver.callback?(NativeAuthReply(status: .granted, accessToken: "duplicate-test-token", errorKey: nil))
        XCTAssertEqual(statuses, [.needsInteraction])
    }

    func testActualOAuthRequestUsesOnlyDriveFileWithPKCEAndNoSecret() {
        let request = AppAuthDriver.request(
            clientID: "unit-test.apps.googleusercontent.com",
            redirectURL: URL(string: "com.googleusercontent.apps.unit-test:/oauthredirect")!,
            scopes: IosGoogleAuthAdapter.scopes)
        XCTAssertEqual(request.scope, "https://www.googleapis.com/auth/drive.file")
        XCTAssertEqual(request.codeChallengeMethod, "S256")
        XCTAssertNotNil(request.codeVerifier)
        XCTAssertNil(request.clientSecret)
        XCTAssertEqual(request.responseType, "code")
    }
}
