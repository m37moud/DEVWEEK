import Foundation

/// Display-only data shared through the App Group. No database or authentication access.
struct NativeWidgetSnapshot: Codable {
    let schemaVersion: Int
    let trackerId: String
    let generatedAtEpochMillis: Int64
    let attendanceDate: String
    let effectiveLocale: String
    let displayStatus: String
    let statusCode: String
    let placeLabel: String
    let deadlineDisplay: String?
    let deadlineEpochMillis: Int64?
    let zoneId: String
    let quickCheckRoute: String
    let arrivalEpochMillis: Int64?
    let themePreference: String
    let isTracked: Bool

    enum CodingKeys: String, CodingKey {
        case schemaVersion, trackerId, generatedAtEpochMillis, attendanceDate, effectiveLocale, displayStatus, statusCode
        case placeLabel, deadlineDisplay, deadlineEpochMillis, zoneId, quickCheckRoute, arrivalEpochMillis, themePreference, isTracked
    }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try values.decode(Int.self, forKey: .schemaVersion)
        trackerId = try values.decode(String.self, forKey: .trackerId)
        generatedAtEpochMillis = try values.decode(Int64.self, forKey: .generatedAtEpochMillis)
        attendanceDate = try values.decode(String.self, forKey: .attendanceDate)
        effectiveLocale = try values.decode(String.self, forKey: .effectiveLocale)
        displayStatus = try values.decode(String.self, forKey: .displayStatus)
        statusCode = try values.decode(String.self, forKey: .statusCode)
        placeLabel = try values.decode(String.self, forKey: .placeLabel)
        deadlineDisplay = try values.decodeIfPresent(String.self, forKey: .deadlineDisplay)
        deadlineEpochMillis = try values.decodeIfPresent(Int64.self, forKey: .deadlineEpochMillis)
        zoneId = try values.decode(String.self, forKey: .zoneId)
        quickCheckRoute = try values.decode(String.self, forKey: .quickCheckRoute)
        arrivalEpochMillis = try values.decodeIfPresent(Int64.self, forKey: .arrivalEpochMillis)
        themePreference = try values.decodeIfPresent(String.self, forKey: .themePreference) ?? "SYSTEM"
        isTracked = try values.decodeIfPresent(Bool.self, forKey: .isTracked) ?? true
    }
    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encode(schemaVersion, forKey: .schemaVersion)
        try values.encode(trackerId, forKey: .trackerId)
        try values.encode(generatedAtEpochMillis, forKey: .generatedAtEpochMillis)
        try values.encode(attendanceDate, forKey: .attendanceDate)
        try values.encode(effectiveLocale, forKey: .effectiveLocale)
        try values.encode(displayStatus, forKey: .displayStatus)
        try values.encode(statusCode, forKey: .statusCode)
        try values.encode(placeLabel, forKey: .placeLabel)
        try values.encode(deadlineDisplay, forKey: .deadlineDisplay)
        try values.encode(deadlineEpochMillis, forKey: .deadlineEpochMillis)
        try values.encode(zoneId, forKey: .zoneId)
        try values.encode(quickCheckRoute, forKey: .quickCheckRoute)
        try values.encode(arrivalEpochMillis, forKey: .arrivalEpochMillis)
        try values.encode(themePreference, forKey: .themePreference)
        try values.encode(isTracked, forKey: .isTracked)
    }
    static func decode(_ data: Data) -> NativeWidgetSnapshot? {
        guard data.count <= 262_144, let value = try? JSONDecoder().decode(Self.self, from: data), value.isValid else { return nil }
        return value
    }
    private var isValid: Bool {
        guard schemaVersion == 1, let tracker = UUID(uuidString: trackerId), generatedAtEpochMillis >= 0,
              ["en", "ar"].contains(effectiveLocale), !displayStatus.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              ["SYSTEM", "LIGHT", "DARK"].contains(themePreference),
              ["SCHEDULED", "AWAITING_CHECK", "PRESENT", "LATE", "ABSENT", "DAY_OFF", "HOLIDAY", "VACATION"].contains(statusCode),
              TimeZone(identifier: zoneId) != nil, Self.isCanonicalDate(attendanceDate),
              quickCheckRoute == "daymark://quick-check?v=1&trackerId=\(tracker.uuidString.lowercased())",
              (deadlineDisplay == nil) == (deadlineEpochMillis == nil) else { return false }
        if let deadlineEpochMillis, deadlineEpochMillis < 0 { return false }
        if let arrivalEpochMillis, arrivalEpochMillis < 0 { return false }
        if let arrivalEpochMillis, let deadlineEpochMillis, arrivalEpochMillis > deadlineEpochMillis { return false }
        return true
    }
    private static func isCanonicalDate(_ value: String) -> Bool {
        guard value.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil else { return false }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        guard let date = formatter.date(from: value) else { return false }
        return formatter.string(from: date) == value
    }
    /// Nil means the cache is for another local date. It must not appear as today's confirmation.
    func resolvedStatus(at date: Date) -> String? {
        guard let zone = TimeZone(identifier: zoneId) else { return nil }
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = zone
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        let currentDate = String(format: "%04d-%02d-%02d", parts.year!, parts.month!, parts.day!)
        guard currentDate == attendanceDate else { return nil }
        guard isTracked, ["SCHEDULED", "AWAITING_CHECK"].contains(statusCode) else { return statusCode }
        let now = date.timeIntervalSince1970 * 1000
        if let deadlineEpochMillis, now > Double(deadlineEpochMillis) { return "ABSENT" }
        if let arrivalEpochMillis, now >= Double(arrivalEpochMillis) { return "AWAITING_CHECK" }
        return statusCode
    }
}
