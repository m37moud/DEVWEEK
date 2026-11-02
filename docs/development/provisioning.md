# Optional Google report provisioning

Local attendance requires no Google account. Live Google testing has not run: no project OAuth client configuration or user consent has been supplied. Compiling mock-based tests does not qualify a live connection.

The only requested scope is `https://www.googleapis.com/auth/drive.file`. Enable Google Drive API and Google Sheets API in the user's own Google Cloud project; configure the consent screen/test users and register native Android and iOS clients for the final application identifiers. This task does not create accounts, enroll an Apple developer team, enable billing, publish or request a broader scope.

Android currently uses provisional ID `dev.daymark.app`. Register the signing certificate fingerprint for the actual build being tested. An iOS public client ID must match the bundle identifier and the provider-approved reversed-client redirect scheme. These are public native client identifiers; never place a client secret, service-account key, access token or refresh token in the repository or Room.

## iOS dependency ruling

The planned Google Sign-In 10.1.0 SDK cannot honor the approved scope limit: its [internal options initializer](https://github.com/google/GoogleSignIn-iOS/blob/10.1.0/GoogleSignIn/Sources/GIDSignInInternalOptions.m) unconditionally calls [a helper adding email and profile](https://github.com/google/GoogleSignIn-iOS/blob/10.1.0/GoogleSignIn/Sources/GIDScopes.m). The privacy requirement takes precedence over that SDK choice.

Use exact-pinned AppAuth 3.0.0 for iOS OAuth authorization-code + PKCE through the system browser with `drive.file` alone. Its [package manifest](https://github.com/openid/AppAuth-iOS/blob/3.0.0/Package.swift) permits iOS 15+, compatible with Daymark's iOS 16 floor, and has no package dependencies. SPM resolved revision `a972daac82d449d58ab119e91c68153e29ddac33`; the updated native host builds and all three Swift bridge/request tests pass. The complete Kotlin suite has 59 passing tests, including 11 Android host permission/authorization tests. Android debug packaging and release Kotlin compilation also pass. Live-provider verification remains pending. The cost of this substitution is additional integration and provider validation, not permission expansion.

The iOS host reads `Configuration.defaults.xcconfig`, which optionally includes the ignored real configuration. Copy `Configuration.example.xcconfig` to `Configuration.xcconfig`, fill the public client ID and exact provider-approved reversed-client scheme, and rebuild. The redirect URI is formed as `<scheme>:/oauthredirect`. Default builds have no usable OAuth client ID and cannot start a real iOS connection. The AppAuth-owned session archive lives only in a device-local, unlocked-only keychain item; shared app storage receives no refresh token. Disconnect invalidates callbacks before removing that native session. Android uses AuthorizationClient 22.0.0 and Credential Manager/provider 1.6.0 for the documented app account-session reset, without requesting identity/profile scopes.

## Live developer probe contract

After native adapters and configuration are ready, the user explicitly connects on each platform. Create a clearly named **Daymark developer test** spreadsheet tagged with an opaque tracker ID in Drive `appProperties.daymarkTrackerId`. Write and read a literal test row containing `=1+2` and Arabic text using Sheets `valueInputOption=RAW`; verify the formula-like string remains literal. Confirm cancel/reconnect and distinguish two consenting test accounts using Drive `about.get(fields=user(permissionId))`, without adding profile scopes.

Retain the developer test report for the user to remove. Never silently delete reports. If creation may have succeeded despite an error or interrupted response, discover the tagged report before considering another create. Multiple matches require selection, not an arbitrary first match. Shared transport has no automatic HTTP retries; later publishing owns durable uncertainty/reconciliation.

Keep real local iOS configuration in ignored `iosApp/Daymark/Configuration.xcconfig`. Do not retain live token, HTTP body or personal note dumps as evidence. Record only the gate outcome, platform/version, scope set and sanitized error category. A report created by one native OAuth client must be verified under the same application project configuration used by the other client before promising cross-platform report access.

## Checkpoint acceptance

Task 3 is partially verified. Callback/transport tests and native builds pass; no live Google connect/create/write/read, cancel/reconnect or account-switch result is claimed on either platform. Native public client configuration and explicit user consent remain required. The Android adapter is application-owned and can request silent authorization after Activity destruction; UI resolution requires a bound foreground Activity. Failed native session clearing surfaces a fixed error key rather than claiming success. Native SDK behavior still requires the live gate.
