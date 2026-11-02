import SwiftUI
import WidgetKit

private struct AttendanceEntry: TimelineEntry {
    let date: Date
    let snapshot: NativeWidgetSnapshot?
}
private struct AttendanceProvider: TimelineProvider {
    func placeholder(in context: Context) -> AttendanceEntry { AttendanceEntry(date: .now, snapshot: nil) }
    func getSnapshot(in context: Context, completion: @escaping (AttendanceEntry) -> Void) {
        completion(AttendanceEntry(date: .now, snapshot: readSnapshot()))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<AttendanceEntry>) -> Void) {
        let snapshot = readSnapshot(); let now = Date()
        var dates = [now]
        if let snapshot {
            for millis in [snapshot.arrivalEpochMillis, snapshot.deadlineEpochMillis.map { $0 + 1 }].compactMap({ $0 }) {
                let boundary = Date(timeIntervalSince1970: Double(millis) / 1000)
                if boundary > now { dates.append(boundary) }
            }
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: snapshot.zoneId) ?? .gmt
            if let midnight = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) { dates.append(midnight) }
        }
        completion(Timeline(entries: dates.sorted().map { AttendanceEntry(date: $0, snapshot: snapshot) }, policy: snapshot == nil ? .never : .atEnd))
    }
    private func readSnapshot() -> NativeWidgetSnapshot? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "DAYMARK_APP_GROUP_IDENTIFIER") as? String,
              !group.isEmpty, !group.contains("$("),
              let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group),
              let data = try? Data(contentsOf: folder.appendingPathComponent("DaymarkWidget", isDirectory: true).appendingPathComponent("daymark-widget.json")) else { return nil }
        return NativeWidgetSnapshot.decode(data)
    }
}

@main
struct DaymarkWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaymarkAttendance", provider: AttendanceProvider()) { entry in
            if #available(iOS 17.0, *) {
                AttendanceWidgetView(entry: entry).containerBackground(for: .widget) { widgetBackground(entry.snapshot) }
            } else {
                AttendanceWidgetView(entry: entry).padding().background(widgetBackground(entry.snapshot))
            }
        }
        .configurationDisplayName("widget_name")
        .description("widget_description")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
private func widgetBackground(_ snapshot: NativeWidgetSnapshot?) -> Color {
    switch snapshot?.themePreference {
    case "DARK": return Color(red: 0.07, green: 0.11, blue: 0.09)
    case "LIGHT": return Color(red: 0.97, green: 0.98, blue: 0.96)
    default: return Color(uiColor: .systemBackground)
    }
}
private struct AttendanceWidgetView: View {
    @Environment(\.colorScheme) private var systemScheme
    let entry: AttendanceEntry
    private var language: String {
        entry.snapshot?.effectiveLocale ?? (Locale.preferredLanguages.first(where: { $0.hasPrefix("ar") || $0.hasPrefix("en") })?.hasPrefix("ar") == true ? "ar" : "en")
    }
    private var bundle: Bundle {
        Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
    }
    private func text(_ key: String) -> String { bundle.localizedString(forKey: key, value: nil, table: nil) }
    private var route: URL { URL(string: entry.snapshot?.quickCheckRoute ?? "daymark://check-in")! }
    private var scheme: ColorScheme? {
        switch entry.snapshot?.themePreference { case "LIGHT": return .light; case "DARK": return .dark; default: return nil }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Label("Daymark", systemImage: "checkmark.circle.fill").font(.headline)
            if let snapshot = entry.snapshot {
                let status = snapshot.resolvedStatus(at: entry.date)
                Text(status == nil ? text("widget_stale") : status == snapshot.statusCode ? snapshot.displayStatus : text("widget_status_\(status!.lowercased())"))
                    .font(.headline).lineLimit(2)
                Text(date(snapshot)).font(.caption).lineLimit(1)
                Text("\u{2068}\(snapshot.placeLabel)\u{2069}").font(.caption).lineLimit(1)
                if let deadline = snapshot.deadlineDisplay {
                    Text(String(format: text("widget_deadline"), "\u{2068}\(deadline)\u{2069}")).font(.caption2).lineLimit(1)
                }
                Text(String(format: text("widget_updated"), "\u{2068}\(updated(snapshot))\u{2069}")).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            } else { Text(text("widget_open")).font(.subheadline) }
            Spacer(minLength: 0)
            Link(destination: route) { Text(text(entry.snapshot == nil ? "widget_open" : "widget_check")).font(.caption.bold()) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .tint((scheme ?? systemScheme) == .dark ? Color(red: 0.76, green: 0.87, blue: 0.62) : Color(red: 0.16, green: 0.40, blue: 0.30))
        .environment(\.locale, Locale(identifier: language))
        .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
        .environment(\.colorScheme, scheme ?? systemScheme)
        .accessibilityIdentifier("daymark-attendance-widget")
        .privacySensitive()
        .widgetURL(route)
    }
    private func date(_ snapshot: NativeWidgetSnapshot) -> String {
        let parts = snapshot.attendanceDate.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return snapshot.attendanceDate }
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = .gmt
        guard let day = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2], hour: 12)) else { return snapshot.attendanceDate }
        let value = makeFormatter(snapshot); value.timeZone = .gmt; value.dateStyle = .medium; value.timeStyle = .none
        return value.string(from: day)
    }
    private func updated(_ snapshot: NativeWidgetSnapshot) -> String {
        let value = makeFormatter(snapshot); value.dateStyle = .none; value.timeStyle = .short
        return value.string(from: Date(timeIntervalSince1970: Double(snapshot.generatedAtEpochMillis) / 1000))
    }
    private func makeFormatter(_ snapshot: NativeWidgetSnapshot) -> DateFormatter {
        let value = DateFormatter(); value.calendar = Calendar(identifier: .gregorian)
        value.locale = Locale(identifier: language == "ar" ? "ar_EG@numbers=arab" : "en_US_POSIX")
        value.timeZone = TimeZone(identifier: snapshot.zoneId) ?? .gmt
        return value
    }
}
