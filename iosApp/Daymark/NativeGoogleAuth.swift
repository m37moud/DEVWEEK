import AppAuth
import DaymarkShared
import Security
import UIKit

@MainActor
protocol IosAuthorizationDriver: AnyObject {
    func authorize(scopes: [String], interactive: Bool, completion: @escaping (NativeAuthReply) -> Void)
    func forget(completion: @escaping (Bool) -> Void)
}

@MainActor
final class IosGoogleAuthAdapter: NSObject, NativeGoogleAuth {
    static let scopes = ["https://www.googleapis.com/auth/drive.file"]
    private let driver: IosAuthorizationDriver
    private var generation = 0

    init(driver: IosAuthorizationDriver) { self.driver = driver }
    override init() { self.driver = AppAuthDriver(); super.init() }

    nonisolated func authorize(interactive: Bool, completion: @escaping (NativeAuthReply) -> Void) {
        Task { @MainActor in
            let requestedGeneration = generation
            var completed = false
            driver.authorize(scopes: Self.scopes, interactive: interactive) { [weak self] reply in
                guard !completed else { return }
                completed = true
                guard let self, self.generation == requestedGeneration else {
                    completion(NativeAuthReply(status: .needsInteraction, accessToken: nil, errorKey: nil))
                    return
                }
                completion(reply)
            }
        }
    }

    nonisolated func forget(completion: @escaping (KotlinBoolean) -> Void) {
        Task { @MainActor in
            generation += 1
            var completed = false
            driver.forget { success in
                guard !completed else { return }
                completed = true
                completion(KotlinBoolean(bool: success))
            }
        }
    }
}

@MainActor
final class AppAuthDriver: IosAuthorizationDriver {
    private var state: OIDAuthState? = NativeGoogleSession.load()
    private var flow: OIDExternalUserAgentSession?
    private var generation = 0

    static func request(clientID: String, redirectURL: URL, scopes: [String]) -> OIDAuthorizationRequest {
        OIDAuthorizationRequest(
            configuration: OIDServiceConfiguration(
                authorizationEndpoint: URL(string: "https://accounts.google.com/o/oauth2/v2/auth")!,
                tokenEndpoint: URL(string: "https://oauth2.googleapis.com/token")!),
            clientId: clientID, clientSecret: nil, scopes: scopes, redirectURL: redirectURL,
            responseType: OIDResponseTypeCode,
            additionalParameters: ["access_type": "offline", "prompt": "select_account"])
    }

    func authorize(scopes: [String], interactive: Bool, completion: @escaping (NativeAuthReply) -> Void) {
        guard scopes == IosGoogleAuthAdapter.scopes else {
            completion(self.reply(.failed, error: "authorization_configuration")); return
        }
        let requestedGeneration = generation
        if !interactive {
            guard let state, state.isAuthorized else { completion(self.reply(.needsInteraction)); return }
            state.performAction { [weak self] token, _, error in
                Task { @MainActor in
                    guard let self, self.generation == requestedGeneration else {
                        completion(NativeAuthReply(status: .needsInteraction, accessToken: nil, errorKey: nil)); return
                    }
                    guard error == nil, let token, !token.isEmpty else {
                        completion(self.reply(.needsInteraction)); return
                    }
                    guard NativeGoogleSession.save(state) else {
                        completion(self.reply(.failed, error: "session_unavailable")); return
                    }
                    completion(self.reply(.granted, token: token))
                }
            }
            return
        }
        guard flow == nil else { completion(self.reply(.failed, error: "authorization_busy")); return }
        guard let clientID = Bundle.main.object(forInfoDictionaryKey: "DAYMARK_GOOGLE_IOS_CLIENT_ID") as? String,
              clientID.hasSuffix(".apps.googleusercontent.com"),
              let redirect = Bundle.main.object(forInfoDictionaryKey: "DAYMARK_GOOGLE_REDIRECT_URI") as? String,
              let redirectURL = URL(string: redirect), let scheme = redirectURL.scheme,
              scheme.hasPrefix("com.googleusercontent.apps."),
              let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
              var presenter = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
            completion(self.reply(.failed, error: "authorization_configuration")); return
        }
        while let presented = presenter.presentedViewController { presenter = presented }
        let request = Self.request(clientID: clientID, redirectURL: redirectURL, scopes: scopes)
        flow = OIDAuthState.authState(byPresenting: request, presenting: presenter) { [weak self] next, error in
            Task { @MainActor in
                guard let self, self.generation == requestedGeneration else {
                    completion(NativeAuthReply(status: .needsInteraction, accessToken: nil, errorKey: nil)); return
                }
                self.flow = nil
                if let error {
                    let native = error as NSError
                    completion(self.reply(native.domain == OIDGeneralErrorDomain && native.code == OIDErrorCode.userCanceledAuthorizationFlow.rawValue ? .cancelled : .failed,
                        error: "authorization_failed"))
                    return
                }
                guard let next, let token = next.lastTokenResponse?.accessToken, !token.isEmpty,
                      NativeGoogleSession.save(next) else {
                    completion(self.reply(.failed, error: "session_unavailable")); return
                }
                self.state = next
                completion(self.reply(.granted, token: token))
            }
        }
    }

    func resume(_ url: URL) -> Bool {
        guard let flow else { return false }
        do { try flow.resumeExternalUserAgentFlow(url); return true }
        catch { return false }
    }

    func forget(completion: @escaping (Bool) -> Void) {
        generation += 1
        flow?.cancel(); flow = nil; state = nil
        completion(NativeGoogleSession.remove())
    }

    private func reply(_ status: NativeAuthStatus, token: String? = nil, error: String? = nil) -> NativeAuthReply {
        NativeAuthReply(status: status, accessToken: token, errorKey: error)
    }
}

private enum NativeGoogleSession {
    private static var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "dev.daymark.oauth", kSecAttrAccount as String: "native-google-session"]
    }
    static func load() -> OIDAuthState? {
        var query = query
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return try? NSKeyedUnarchiver.unarchivedObject(ofClass: OIDAuthState.self, from: data)
    }
    static func save(_ state: OIDAuthState) -> Bool {
        guard let data = try? NSKeyedArchiver.archivedData(withRootObject: state, requiringSecureCoding: true) else { return false }
        let attributes: [String: Any] = [kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let updated = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updated == errSecSuccess { return true }
        guard updated == errSecItemNotFound else { return false }
        return SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) == errSecSuccess
    }
    static func remove() -> Bool {
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}
