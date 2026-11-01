import Foundation
import UserNotifications
import DaymarkShared

@MainActor
protocol ReminderNotificationCenter: AnyObject {
    func requestPermission() async throws -> Bool
    func pendingRequests() async -> [UNNotificationRequest]
    func add(_ request: UNNotificationRequest) async throws
    func remove(_ identifiers: [String])
}

@MainActor
private final class SystemReminderNotificationCenter: ReminderNotificationCenter {
    private let center = UNUserNotificationCenter.current()
    func requestPermission() async throws -> Bool { try await center.requestAuthorization(options: [.alert, .sound]) }
    func pendingRequests() async -> [UNNotificationRequest] { await center.pendingNotificationRequests() }
    func add(_ request: UNNotificationRequest) async throws {
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional || status == .ephemeral else {
            throw NSError(domain: "Daymark.Reminders", code: 1)
        }
        try await center.add(request)
    }
    func remove(_ identifiers: [String]) { center.removePendingNotificationRequests(withIdentifiers: identifiers) }
}

private struct NativeReminder: Decodable {
    let id: String
    let trackerId: String
    let attendanceDate: String
    let fireAtEpochMillis: Int64
    let title: String
    let body: String
    let localeTag: String
}
private struct NativeReminderEnvelope: Decodable {
    let trackerId: String
    let reminders: [NativeReminder]
}

@MainActor
final class IosReminderAdapter: NSObject, ReminderPort {
    private let center: ReminderNotificationCenter
    private let now: () -> Date
    private var generation = 0
    private var tail: Task<Void, Never>?
    init(center: ReminderNotificationCenter? = nil, now: @escaping () -> Date = Date.init) {
        self.center = center ?? SystemReminderNotificationCenter()
        self.now = now
        super.init()
    }
    nonisolated func requestPermission(completion: @escaping (KotlinBoolean) -> Void) {
        Task { @MainActor in
            let allowed = (try? await center.requestPermission()) ?? false
            completion(KotlinBoolean(bool: allowed))
        }
    }
    nonisolated func replace(remindersJson: String, completion: @escaping (KotlinBoolean) -> Void) {
        Task { @MainActor in
            guard let payload = decode(remindersJson) else { completion(KotlinBoolean(bool: false)); return }
            enqueue(completion: completion) { ticket in
                let existing = await self.center.pendingRequests()
                guard ticket == self.generation else { return false }
                let prefix = "daymark.reminder:\(payload.trackerId):"
                self.center.remove(existing.filter { $0.identifier.hasPrefix(prefix) }.map(\.identifier))
                var added: [String] = []
                for spec in payload.reminders {
                    guard ticket == self.generation else { self.center.remove(added); return false }
                    let fire = Date(timeIntervalSince1970: Double(spec.fireAtEpochMillis) / 1000)
                    // The caller can become stale while native scheduling is suspended; never turn a past arrival into an immediate alert.
                    if fire < self.now() { continue }
                    let request = self.request(spec, fire: fire)
                    do { try await self.center.add(request); added.append(spec.id) }
                    catch { self.center.remove(added + [spec.id]); return false }
                }
                guard ticket == self.generation else { self.center.remove(added); return false }
                return true
            }
        }
    }
    nonisolated func cancelTracker(trackerId: String, completion: @escaping (KotlinBoolean) -> Void) {
        Task { @MainActor in
            guard UUID(uuidString: trackerId) != nil else { completion(KotlinBoolean(bool: false)); return }
            enqueue(completion: completion) { ticket in
                let requests = await self.center.pendingRequests()
                guard ticket == self.generation else { return false }
                self.center.remove(requests.filter { $0.identifier.hasPrefix("daymark.reminder:\(trackerId):") }.map(\.identifier))
                return true
            }
        }
    }
    private func enqueue(completion: @escaping (KotlinBoolean) -> Void, operation: @escaping (Int) async -> Bool) {
        generation += 1
        let ticket = generation
        let previous = tail
        tail = Task { @MainActor in
            await previous?.value
            guard ticket == generation else { completion(KotlinBoolean(bool: false)); return }
            completion(KotlinBoolean(bool: await operation(ticket)))
        }
    }
    private func decode(_ text: String) -> NativeReminderEnvelope? {
        guard let data = text.data(using: .utf8), let payload = try? JSONDecoder().decode(NativeReminderEnvelope.self, from: data),
              UUID(uuidString: payload.trackerId) != nil, payload.reminders.count <= 30,
              Set(payload.reminders.map(\.id)).count == payload.reminders.count else { return nil }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        for spec in payload.reminders {
            guard spec.trackerId == payload.trackerId, spec.id == "daymark.reminder:\(payload.trackerId):\(spec.attendanceDate)",
                  spec.attendanceDate.range(of: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", options: .regularExpression) != nil,
                  let date = formatter.date(from: spec.attendanceDate), formatter.string(from: date) == spec.attendanceDate,
                  ["en", "ar"].contains(spec.localeTag), spec.fireAtEpochMillis >= 0 else { return nil }
        }
        return payload
    }
    private func request(_ spec: NativeReminder, fire: Date) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = spec.title
        content.body = spec.body
        content.sound = .default
        content.userInfo = ["intent": "quick-check"]
        var calendar = Calendar(identifier: .gregorian)
        let utc = TimeZone(secondsFromGMT: 0)!
        calendar.timeZone = utc
        var components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fire)
        components.calendar = calendar
        components.timeZone = utc
        return UNNotificationRequest(identifier: spec.id, content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
    }
}

@MainActor
final class DaymarkNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    private let openQuickCheck: () -> Void
    init(openQuickCheck: @escaping () -> Void) { self.openQuickCheck = openQuickCheck }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void) {
        let owned = response.notification.request.identifier.hasPrefix("daymark.reminder:")
        let quickCheck = response.notification.request.content.userInfo["intent"] as? String == "quick-check"
        if owned && quickCheck { Task { @MainActor in openQuickCheck() } }
        completionHandler()
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}
