#if DEBUG
import SwiftUI
import DaymarkShared

struct NativeProbeView: View {
    let googleAuth: NativeGoogleAuth
    @State private var message = "No reminder scheduled"
    @State private var scheduling = false
    @State private var googleBusy = false
    @State private var googleMessage = "Google probe needs local OAuth configuration; reports are retained."

    var body: some View {
        VStack {
            ContentView()
            Button("Schedule one reminder in 60 seconds") {
                scheduling = true
                Task { @MainActor in
                    message = await ReminderProbe.schedule()
                    scheduling = false
                }
            }
            .disabled(scheduling)
            Text(message).font(.caption)
            Button("Connect Google and create/reuse developer test report") {
                googleBusy = true
                GoogleConnectivityProbe(native: googleAuth).run { outcome, _ in
                    Task { @MainActor in
                        googleMessage = "Google developer probe: \(outcome?.name ?? "UNAVAILABLE")"
                        googleBusy = false
                    }
                }
            }
            .font(.caption)
            .disabled(googleBusy || !googleConfigured)
            Text(googleMessage).font(.caption)
        }
        .onOpenURL { url in
            guard url.scheme == "daymark", url.host == "native-probe" else { return }
            message = "Widget opened the foreground probe. Choose Check once to request location."
        }
    }

    private var googleConfigured: Bool {
        guard let client = Bundle.main.object(forInfoDictionaryKey: "DAYMARK_GOOGLE_IOS_CLIENT_ID") as? String else { return false }
        return client.hasSuffix(".apps.googleusercontent.com") && !client.contains("REPLACE")
    }
}
#endif
