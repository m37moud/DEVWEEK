import SwiftUI
import WidgetKit

private struct AttendanceEntry: TimelineEntry {
    let date: Date
    let snapshot: NativeWidgetSnapshot?
}
private struct AttendanceProvider: TimelineProvider {
    func placeholder(in context: Context) -> AttendanceEntry { previewEntry("AWAITING_CHECK") }
    func getSnapshot(in context: Context, completion: @escaping (AttendanceEntry) -> Void) {
        completion(context.isPreview ? previewEntry("AWAITING_CHECK") : AttendanceEntry(date: .now, snapshot: readSnapshot()))
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
        StaticConfiguration(kind: NativeWidgetSnapshot.widgetKind, provider: AttendanceProvider()) { entry in
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
private func widgetBackground(_ snapshot: NativeWidgetSnapshot?) -> some View {
    let fill: Color
    switch snapshot?.themePreference {
    case "DARK": fill = Color(red: 0.07, green: 0.11, blue: 0.09)
    case "LIGHT": fill = Color(red: 0.97, green: 0.98, blue: 0.96)
    default:
        fill = Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(red: 0.07, green: 0.11, blue: 0.09, alpha: 1)
                : UIColor(red: 0.97, green: 0.98, blue: 0.96, alpha: 1)
        })
    }
    return fill.overlay(ContainerRelativeShape().stroke(Color.gray.opacity(0.3), lineWidth: 1))
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
    private var route: URL { URL(string: entry.snapshot?.quickCheckRoute ?? "daymark://quick-check")! }
    private var scheme: ColorScheme? {
        switch entry.snapshot?.themePreference { case "LIGHT": return .light; case "DARK": return .dark; default: return nil }
    }

    private var action: NativeWidgetSnapshot.Action { entry.snapshot?.action(at: entry.date) ?? .openApp }
    private var launchURL: URL? {
        switch action {
        case .checkIn: return route
        case .openApp: return URL(string: "daymark://quick-check")
        case .disabled: return nil
        }
    }
    private var dark: Bool { (scheme ?? systemScheme) == .dark }
    private var foreground: Color { dark ? Color(red: 0.93, green: 0.95, blue: 0.91) : Color(red: 0.13, green: 0.22, blue: 0.18) }
    private var primary: Color { dark ? Color(red: 0.76, green: 0.87, blue: 0.62) : Color(red: 0.16, green: 0.40, blue: 0.30) }
    private var statusLabel: String {
        guard let snapshot = entry.snapshot else { return text("widget_open") }
        guard let status = snapshot.resolvedStatus(at: entry.date) else { return text("widget_refresh") }
        if !snapshot.isTracked { return text("widget_not_started_short") }
        return text("widget_status_\(status.lowercased())")
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(text("widget_today")).font(.caption2).foregroundStyle(foreground.opacity(0.75))
            Text(statusLabel)
                .font(.headline.weight(.bold)).foregroundStyle(foreground)
                .fixedSize(horizontal: false, vertical: true)
            if let snapshot = entry.snapshot {
                Text(String(format: text("widget_updated"), "\u{2068}\(updated(snapshot))\u{2069}"))
                    .font(.caption2).foregroundStyle(foreground.opacity(0.75))
            }
            Spacer(minLength: 0)
            if let launchURL {
                Link(destination: launchURL) { actionLabel }
            } else {
                actionLabel.accessibilityLabel(text("widget_check_disabled"))
                    .disabled(true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .environment(\.locale, Locale(identifier: language))
        .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
        .environment(\.colorScheme, scheme ?? systemScheme)
        .accessibilityIdentifier("daymark-attendance-widget")
        .privacySensitive()
        .widgetURL(launchURL)
    }
    private var actionForeground: Color {
        switch action {
        case .checkIn:
            return dark ? Color(red: 0.13, green: 0.21, blue: 0.12) : Color(red: 0.97, green: 0.98, blue: 0.94)
        case .openApp: return primary
        case .disabled: return foreground
        }
    }
    private var actionLabel: some View {
        Text(text(action == .openApp ? "widget_open" : "widget_check"))
            .font(.subheadline.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(actionForeground)
            .background(action == .checkIn ? primary : foreground.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .opacity(action == .disabled ? 0.5 : 1)
    }
    private func updated(_ snapshot: NativeWidgetSnapshot) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: language == "ar" ? "ar_EG@numbers=arab" : "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: snapshot.zoneId) ?? .gmt
        formatter.dateStyle = .none
        formatter.timeStyle = .short
        return formatter.string(from: Date(timeIntervalSince1970: Double(snapshot.generatedAtEpochMillis) / 1000))
    }
}

struct AttendanceWidgetPreviews: PreviewProvider {
    static var previews: some View {
        ForEach(["AWAITING_CHECK", "SCHEDULED", "PRESENT", "DAY_OFF", "STALE"], id: \.self) { status in
            AttendanceWidgetView(entry: previewEntry(status))
                .padding().background(widgetBackground(nil))
                .previewContext(WidgetPreviewContext(family: .systemSmall))
                .previewDisplayName(status)
        }
    }
}

private func previewEntry(_ status: String) -> AttendanceEntry {
        let now = Date()
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = .gmt
        let id = "f8428c3a-1347-40dc-9667-af38e69c6801"
        let json: [String: Any] = [
            "schemaVersion": 1, "trackerId": id,
            "generatedAtEpochMillis": Int64(now.timeIntervalSince1970 * 1000),
            "attendanceDate": status == "STALE" ? "2020-01-01" : formatter.string(from: now),
            "effectiveLocale": Locale.preferredLanguages.first(where: { $0.hasPrefix("ar") || $0.hasPrefix("en") })?.hasPrefix("ar") == true ? "ar" : "en", "displayStatus": status,
            "statusCode": status == "STALE" ? "PRESENT" : status,
            "placeLabel": "", "zoneId": "UTC",
            "arrivalEpochMillis": Int64(now.addingTimeInterval(status == "SCHEDULED" ? 3600 : -3600).timeIntervalSince1970 * 1000),
            "quickCheckRoute": "daymark://quick-check?v=1&trackerId=\(id)",
            "themePreference": "SYSTEM", "isTracked": true
        ]
        return AttendanceEntry(date: now, snapshot: (try? JSONSerialization.data(withJSONObject: json)).flatMap(NativeWidgetSnapshot.decode))
    }
