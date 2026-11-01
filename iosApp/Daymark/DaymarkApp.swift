import SwiftUI
import UserNotifications
import DaymarkShared

@main
@MainActor
struct DaymarkApp: App {
    private let reminders: IosReminderAdapter
    private let notificationDelegate: DaymarkNotificationDelegate
    private let googleDriver: AppAuthDriver
    private let googleAuth: IosGoogleAuthAdapter

    init() {
        let driver = AppAuthDriver()
        googleDriver = driver
        googleAuth = IosGoogleAuthAdapter(driver: driver)
        reminders = IosReminderAdapter()
        let delegate = DaymarkNotificationDelegate { QuickCheckIntents.shared.request() }
        notificationDelegate = delegate
        UNUserNotificationCenter.current().delegate = delegate
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["daymark-native-reminder-probe"])
    }
    var body: some Scene {
        WindowGroup {
            Group {
#if DEBUG
            NativeProbeView(googleAuth: googleAuth, reminders: reminders)
#else
            ContentView()
#endif
            }
            .onOpenURL { url in
                if url.scheme == "daymark", url.host == "check-in" { QuickCheckIntents.shared.request() }
                else { _ = googleDriver.resume(url) }
            }
        }
    }
}
