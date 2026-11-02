import SwiftUI
import WidgetKit

private struct ProbeEntry: TimelineEntry { let date: Date }

private struct ProbeProvider: TimelineProvider {
    func placeholder(in context: Context) -> ProbeEntry { ProbeEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (ProbeEntry) -> Void) {
        completion(ProbeEntry(date: .now))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<ProbeEntry>) -> Void) {
        completion(Timeline(entries: [ProbeEntry(date: .now)], policy: .never))
    }
}

@main
struct DaymarkProbeWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "DaymarkNativeProbe", provider: ProbeProvider()) { _ in
            if #available(iOS 17.0, *) {
                ProbeWidgetContent().containerBackground(.background, for: .widget)
            } else {
                ProbeWidgetContent().padding().background(Color.white)
            }
        }
        .configurationDisplayName("Daymark development probe")
        .description("Opens the app for a user-initiated location check.")
        .supportedFamilies([.systemSmall])
    }
}

private struct ProbeWidgetContent: View {
    var body: some View {
        VStack(alignment: .leading) {
            Text("Daymark probe").font(.headline)
            Text("Open location check")
            Text("No attendance recorded").font(.caption)
        }
        .widgetURL(URL(string: "daymark://native-probe"))
    }
}
