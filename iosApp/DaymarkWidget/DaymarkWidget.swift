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
    private var route: URL { URL(string: entry.snapshot?.quickCheckRoute ?? "daymark://quick-check")! }
    private var scheme: ColorScheme? {
        switch entry.snapshot?.themePreference { case "LIGHT": return .light; case "DARK": return .dark; default: return nil }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("Daymark", systemImage: "checkmark.circle.fill")
                .font(.headline)

            if let snapshot = entry.snapshot {
                let status = snapshot.resolvedStatus(at: entry.date)
                statusBadge(status: status, snapshot: snapshot)
                snapshotDetails(snapshot)
            } else {
                Text(text("widget_open")).font(.subheadline)
            }

            Spacer(minLength: 0)

            actionButton
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

    private func snapshotDetails(_ snapshot: NativeWidgetSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\u{2068}\(snapshot.placeLabel)\u{2069}")
                .font(.caption).lineLimit(1)
            if let deadline = snapshot.deadlineDisplay {
                Text(String(format: text("widget_deadline"), "\u{2068}\(deadline)\u{2069}"))
                    .font(.caption2).lineLimit(1)
            }
            Text(String(format: text("widget_updated"), "\u{2068}\(updated(snapshot))\u{2069}"))
                .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
        }
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

    private func statusBadge(status: String?, snapshot: NativeWidgetSnapshot) -> some View {
        let isPresent = status == "PRESENT"
        let isLate = status == "LATE"
        let isOff = status == "DAY_OFF" || status == "HOLIDAY" || status == "VACATION"
        let labelText: String = {
            if status == nil { return text("widget_stale") }
            if status == snapshot.statusCode { return snapshot.displayStatus }
            return text("widget_status_\(status!.lowercased())")
        }()

        let bg: Color = {
            if isPresent { return Color(red: 0.86, green: 0.94, blue: 0.85) }
            if isLate { return Color(red: 0.99, green: 0.91, blue: 0.80) }
            if isOff { return Color(red: 0.89, green: 0.93, blue: 0.90) }
            return Color(red: 0.92, green: 0.95, blue: 0.93)
        }()
        let fg: Color = {
            if isPresent { return Color(red: 0.11, green: 0.30, blue: 0.21) }
            if isLate { return Color(red: 0.48, green: 0.29, blue: 0.00) }
            if isOff { return Color(red: 0.21, green: 0.33, blue: 0.27) }
            return Color(red: 0.13, green: 0.22, blue: 0.18)
        }()

        return HStack(spacing: 6) {
            if isPresent {
                Image(systemName: "checkmark")
                    .font(.caption.bold())
            } else if isLate {
                Image(systemName: "exclamationmark.circle")
                    .font(.caption)
            } else if isOff {
                Image(systemName: "moon.fill")
                    .font(.caption)
            } else {
                Circle()
                    .fill(Color(red: 0.16, green: 0.40, blue: 0.30))
                    .frame(width: 8, height: 8)
            }
            Text(labelText)
                .font(.subheadline.weight(.medium))
                .lineLimit(2)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(bg)
        .foregroundColor(fg)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var actionButton: some View {
        let isConfirmed = entry.snapshot?.resolvedStatus(at: entry.date).map { $0 == "PRESENT" || $0 == "LATE" } ?? false
        let title: String = {
            if entry.snapshot == nil { return text("widget_open") }
            if isConfirmed { return "✓ " + (entry.snapshot?.displayStatus ?? text("widget_open")) }
            return text("widget_check")
        }()

        return Link(destination: route) {
            Text(title)
                .font(.caption.bold())
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
                .background(isConfirmed ? Color(red: 0.89, green: 0.93, blue: 0.90) : ((scheme ?? systemScheme) == .dark ? Color(red: 0.76, green: 0.87, blue: 0.62) : Color(red: 0.16, green: 0.40, blue: 0.30)))
                .foregroundColor(isConfirmed ? Color(red: 0.13, green: 0.22, blue: 0.18) : ((scheme ?? systemScheme) == .dark ? Color(red: 0.13, green: 0.21, blue: 0.12) : Color(red: 0.96, green: 0.98, blue: 0.94)))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}
