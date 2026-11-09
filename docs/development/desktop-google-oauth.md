# Desktop Google connection

The desktop development host uses Google's installed-app authorization flow: the system browser, a random loopback port on `127.0.0.1`, a random state value, and PKCE with SHA-256. Only `drive.file` is requested. No client secret is embedded or sent. Attendance remains available without Google.

## Create the desktop client

1. Open [Google Auth Platform](https://console.cloud.google.com/auth/overview) and select your Google Cloud project.
2. Configure **Branding** and **Audience**. For an External app in Testing, add your Google account under **Test users**.
3. Under **Data Access**, configure `https://www.googleapis.com/auth/drive.file`.
4. Enable the **Google Drive API** and **Google Sheets API** in **APIs & Services → Library**.
5. Open **Clients → Create client**, choose **Desktop app**, and name it **Daymark Desktop**. Use a separate client from Android and iOS.
6. Download the client JSON and save it as `~/.daymark/google-oauth-client.json`. Keep the download outside the repository. The file must have the Google-generated `installed.client_id` field; a Web application client is rejected. Other fields, including any downloaded client secret, are ignored.
7. Restrict the file to your account: `chmod 600 ~/.daymark/google-oauth-client.json`.
8. Run `./gradlew :desktopApp:run`, open Settings, and click **Connect Google**. Finish consent in the browser. If you close the browser without responding, the attempt expires after three minutes.

Google documents the client secret as optional for this flow. If your client rejects a token exchange, verify its type and configuration; do not embed a secret to work around it. See [Google's desktop OAuth documentation](https://developers.google.com/identity/protocols/oauth2/native-app).

## Session and platform behavior

On macOS, only the refresh session is retained in the native Keychain through the bundled `DaymarkGoogleSession` helper. Tokens never enter Room, preferences, local JSON, command arguments, or logs. Keychain access can prompt or fail; failure is reported instead of claiming a connection succeeded.

Windows and Linux can use the browser flow, but keep the session only in memory until the app exits. They require interactive connection again on the next launch; persistent native credential vaults are not implemented there. These platforms have not been verified on physical hosts.

Disconnect cancels pending native authorization, fences old callbacks, and clears the local session. It preserves the remote spreadsheet. Google authorization configuration, real-account consent, API access, Keychain prompts, and packaged app behavior require verification on the intended host before desktop delivery.

## Verification

`./gradlew :desktopApp:test` covers PKCE, callback validation, the loopback browser handoff, session refresh, configuration errors, and disconnect behavior using synthetic credentials. These tests do not contact Google or write to your Keychain.
