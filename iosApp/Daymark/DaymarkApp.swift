import SwiftUI
import UserNotifications
import DaymarkShared

@main
@MainActor
struct DaymarkApp: App {
    @State private var showDeveloperProbe = false
    private let widget: WidgetSink
    private let reminders: IosReminderAdapter
    private let notificationDelegate: DaymarkNotificationDelegate
    private let googleDriver: AppAuthDriver
    private let googleAuth: IosGoogleAuthAdapter

    init() {
        let driver = AppAuthDriver()
        googleDriver = driver
        googleAuth = IosGoogleAuthAdapter(driver: driver)
        reminders = IosReminderAdapter()
        widget = DevelopmentWidgetSink()
        let delegate = DaymarkNotificationDelegate { QuickCheckIntents.shared.request() }
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
                    ContentView(auth: googleAuth, reminders: reminders, widget: widget).ignoresSafeArea()
                }
#else
                ContentView(auth: googleAuth, reminders: reminders, widget: widget).ignoresSafeArea()
#endif
            }
            .onOpenURL { url in
                if url.scheme == "daymark", url.host == "check-in" { QuickCheckIntents.shared.request(); return }
#if DEBUG
                if url.scheme == "daymark", ["developer-probe", "native-probe"].contains(url.host ?? "") { showDeveloperProbe = true; return }
#endif
                _ = googleDriver.resume(url)
            }
        }
    }
}
