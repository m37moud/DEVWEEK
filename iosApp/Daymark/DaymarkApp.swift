import SwiftUI
import UserNotifications
import DaymarkShared

@main
@MainActor
struct DaymarkApp: App {
    @State private var showDeveloperProbe = false
    private let widget: WidgetSink
    private let widgetSetup = IosWidgetSetupPort()
    private let reminders: IosReminderAdapter
    private let notificationDelegate: DaymarkNotificationDelegate
    private let googleDriver: AppAuthDriver
    private let googleAuth: IosGoogleAuthAdapter

    init() {
        let driver = AppAuthDriver()
        googleDriver = driver
        googleAuth = IosGoogleAuthAdapter(driver: driver)
        reminders = IosReminderAdapter()
        widget = WidgetSnapshotWriter()
        let delegate = DaymarkNotificationDelegate { route in QuickCheckIntents.shared.requestRoute(raw: route) }
        notificationDelegate = delegate
        UNUserNotificationCenter.current().delegate = delegate
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["daymark-native-reminder-probe"])
    }
    var body: some Scene {
        WindowGroup {
            Group {
#if DEBUG
                if showDeveloperProbe {
                    NativeProbeView(googleAuth: googleAuth, reminders: reminders, onClose: { showDeveloperProbe = false })
                } else {
                    ContentView(auth: googleAuth, reminders: reminders, widget: widget, widgetSetup: widgetSetup).ignoresSafeArea()
                }
#else
                ContentView(auth: googleAuth, reminders: reminders, widget: widget, widgetSetup: widgetSetup).ignoresSafeArea()
#endif
            }
            .onOpenURL { url in
                if url.scheme == "daymark", ["quick-check", "check-in"].contains(url.host ?? "") { QuickCheckIntents.shared.requestRoute(raw: url.absoluteString); return }
#if DEBUG
                if url.scheme == "daymark", ["developer-probe", "native-probe"].contains(url.host ?? "") { showDeveloperProbe = true; return }
#endif
                _ = googleDriver.resume(url)
            }
        }
    }
}
