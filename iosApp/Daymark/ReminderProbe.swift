#if DEBUG
import UserNotifications

enum ReminderProbe {
    static func schedule() async -> String {
        let center = UNUserNotificationCenter.current()
        do {
            guard try await center.requestAuthorization(options: [.alert, .sound]) else {
                return "Notification permission denied; no reminder scheduled"
            }
            let content = UNMutableNotificationContent()
            content.title = "Daymark development reminder"
            content.body = "Open Daymark and choose Check once. This reminder does not check location."
            content.sound = .default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 60, repeats: false)
            try await center.add(UNNotificationRequest(
                identifier: "daymark-native-reminder-probe", content: content, trigger: trigger
            ))
            return "One reminder scheduled; background the app to observe delivery"
        } catch {
            return "Reminder scheduling failed; no location requested"
        }
    }
}
#endif
